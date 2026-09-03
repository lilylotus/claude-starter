## Context

现状（`master-data-approval-workflow` + `flowable-database-bootstrap` 两个已归档能力）：

- Flowable 7.2.0 以 `flowable-spring-boot-starter-process` 内嵌方式运行在同一
  Spring Boot 应用、同一 MySQL 实例中；`ACT_*` 表由
  `db/flowable/flowable.mysql.create.7.2.0.sql` 一次性初始化，不纳入 Flyway。
- 唯一的流程定义 `masterDataApprovalProcess`（
  `backend/src/main/resources/processes/approval-process.bpmn20.xml`）只有
  一个不设 `candidateGroups`/`candidateUsers` 的 `userTask`，谁能处理完全靠
  `ApprovalManagement:request:approve` 权限点，不关心具体是谁。
- `ApprovalProcessServiceImpl` 直接注入 `RuntimeService`/`TaskService`，
  `ApprovalRequestServiceImpl`（四个业务模块共用）据此驱动
  `tab_approval_request`/`tab_approval_switch` 两张表的状态机；"我的申请"/
  "待我审批" 直接查询 `tab_approval_request.status`，因为一条申请只对应
  一个用户任务。
- 4A 现有主数据模型里没有独立的"岗位"模块，但已有可复用的管理员抽象：
  `tab_admin`（管理员主数据）+ `tab_admin_role`（管理员持有的角色，多对多）
  + `tab_admin_org_scope`（管理员的组织管辖范围，含 `includeChildren`
  递归标记）。`tab_app.owner_id` 记录应用负责人。这两个抽象是当前唯一能
  落地"组织负责人""安全管理员""应用管理员"这类审批人规则的数据来源。
- 参考文档（仓库根目录两份 4A + Flowable 方案）给出的架构原则：Flowable
  只做状态机、业务层不直接依赖 `ACT_*`/引擎 Service、审批人解析走独立
  `AssigneeResolver`、会签用 Flowable 原生 Multi-Instance、Reject 与
  Return 语义分离、操作幂等、待办查询走自己的业务表而非直接查
  `ACT_RU_TASK`。

约束：

- MySQL 5.7 兼容（禁止窗口函数/CTE/`JSON_TABLE`），所有新 SQL 遵循此限制。
- 表名统一 `tab_` 前缀；新表在 `tab_` 之后用 `wf_` 区分领域
  （如 `tab_wf_process_instance`），不引入第二套前缀规则。
- MapStruct 不注册为 Spring bean，接口内声明 `INSTANCE` 静态单例。
- `@Transactional` 必须显式声明 `rollbackFor` 和 `propagation`。
- 前端技术栈为 Vue 3 + TypeScript + Vite + Element Plus + Pinia，
  Composition API + `<script setup>`；新增可视化设计器需要引入新的第三方
  npm 依赖（Vue Flow），落地前需与用户确认。
- 本次不引入 MQ/Outbox；流程模型的"发布"动作是同步编译 + 部署，不做
  异步任务队列。

## Goals / Non-Goals

**Goals:**

- 提供 `WorkflowService` 抽象层，业务代码与 Controller 不再直接注入
  Flowable 的 `RuntimeService`/`TaskService`/`RepositoryService`/
  `HistoryService`。
- 支持基于配置（而非硬编码 Java 分支）的多级审批人解析：指定人员、指定
  角色、指定岗位（预留，无数据源时按"未配置"处理）、发起人部门负责人、
  发起人部门上级负责人、指定组织负责人、流程发起人、上一节点审批人。
- 支持会签（`AND`/`OR`/`PERCENT`）、Reject/Return/Withdraw/Transfer/
  Delegate/AddSign 语义化操作，操作幂等，越权校验。
- 把 `masterDataApprovalProcess` 从单节点升级为默认两级
  （部门负责人 → 安全管理员）审批，四个业务模块的对外契约基本不变。
- "我的待办/已办"查询改为读取自有的审批任务/记录表。
- 提供前端 Vue Flow 可视化流程设计器：节点只暴露"开始/审批/条件/结束"
  业务语言，画布保存为自定义 Workflow JSON DSL，不直接暴露 BPMN 概念。
- 提供后端 `WorkflowModelCompiler`：DSL → Flowable `BpmnModel`，编译期
  做结构校验（孤立节点、条件分支未覆盖、审批节点必填属性缺失）。
- 提供流程模型 `DRAFT`/`PUBLISHED`/`DISABLED` 三态生命周期与按流程编码
  查看版本历史（发布人/发布时间/DSL 快照/状态）的能力。

**Non-Goals:**

- 不做 Outbox + MQ 事件通知、审批超时升级/催办、流程监控运维大盘。
- 不做跨流程调用（Call Activity）、信号/消息事件。
- 不实现独立的"岗位"主数据模块——`POSITION` 类型的 `AssigneeResolver`
  本次只做接口占位（解析结果为空，按"审批人为空"策略处理），待岗位模块
  落地后再补齐真实实现。
- DSL 节点类型本次只做 `START`/`APPROVAL`/`CONDITION`/`END` 四种，不做
  `抄送`（依赖通知基础设施，Non-Goal）与`并行网关`（区别于会签，本次
  会签已能覆盖"多人处理同一节点"的场景，"同时激活多个不同节点"的并行
  网关场景留待后续按需再加）。
- 不做"回滚到任意历史版本再次成为最新版本"——回滚统一通过挂起当前
  发布版本、Flowable 自动回退到上一个未挂起版本承接新发起请求实现，
  不支持挑选任意历史版本重新设为"最新"。

## Decisions

### 1. 包结构与分层

新增 `cn.nihility.rbac.workflow` 顶层包，遵循项目既有分层约定：

```text
cn.nihility.rbac.workflow
├── constant/          ApprovalAction、AssigneeType、ApprovalMode、
│                      EmptyAssigneeStrategy、WithdrawPolicyType 等枚举
├── controller/        WorkflowTaskController（待办/已办查询 + 通用任务操作）
├── dto/               命令对象与 VO（ApproveCommand、ReturnCommand 等）
├── entity/            5 张新表对应的实体
├── mapper/            对应 BaseMapper
├── mapstruct/         entity <-> VO 转换（静态 INSTANCE 单例）
├── service/ + impl/   WorkflowTaskService（待办/已办/轨迹查询）、
│                      IdempotencyService（幂等表读写）
├── assignee/          AssigneeResolver 接口 + 各实现
│                      （RoleAssigneeResolver、PositionAssigneeResolver、
│                      OrgLeaderAssigneeResolver、ApplicantDeptLeaderResolver、
│                      ApplicantDeptParentLeaderResolver、
│                      InitiatorAssigneeResolver、
│                      PreviousApproverAssigneeResolver、
│                      UserAssigneeResolver）
├── engine/            WorkflowService 接口
│   └── flowable/      FlowableWorkflowService（实现类）、
│                      WorkflowAssigneeTaskListener（TaskListener）、
│                      WorkflowMultiInstanceExecutionListener
│                      （ExecutionListener，会签候选人集合准备）
├── policy/            WithdrawPolicy 接口 + 默认实现
│                      （BeforeFirstApprovalWithdrawPolicy）
├── designer/          流程模型草稿/发布/下线/版本历史
│   ├── controller/    WorkflowProcessModelController
│   ├── dto/           ProcessModelDsl（DSL 反序列化模型）、
│   │                  ProcessModelCreateRequest、PublishResult、
│   │                  ProcessDefinitionVersionVO 等
│   ├── entity/mapper/ tab_wf_process_model 对应 entity/mapper
│   ├── service/+impl/ WorkflowProcessModelService（草稿保存/发布/
│   │                  下线/版本历史查询）
│   └── compiler/      WorkflowModelCompiler（DSL 校验 + 编译为
│                      BpmnModel + 派生 tab_wf_node_assignee_rule 行）
└── exception/         WorkflowException + Controller/Advice 复用全局处理器
```

前端新增 `frontend/src/views/workflow/`：

```text
views/workflow/
├── process-model/       流程模型列表页 + 版本历史弹窗
│   ├── ProcessModelListView.vue
│   └── VersionHistoryDialog.vue
└── designer/             Vue Flow 画布
    ├── ProcessDesignerView.vue
    ├── nodes/             StartNode.vue、ApprovalNode.vue、
    │                      ConditionNode.vue、EndNode.vue
    └── panels/            NodePropertyPanel.vue（按节点类型切换表单，
                           审批节点表单字段对应 assignee_type/
                           approval_mode/empty_assignee_strategy/
                           allow_* 等）
api/workflow.ts            流程模型/版本/任务相关请求封装
stores/workflowDesigner.ts 画布编辑态（当前 DSL、选中节点、脏标记）
types/workflow.ts          DSL 节点/边类型定义，与后端 DTO 字段对齐
```

`cn.nihility.rbac.approval` 包（业务层）保留，`ApprovalProcessService`
改为内部委托 `WorkflowService`，`ApprovalRequestService` 不感知 Flowable
API，只感知 `WorkflowService` 的命令对象。

**备选方案**：把审批人解析、多级流程也塞进 `approval` 包内，不新开
`workflow` 包。放弃原因——`approval` 包语义上是"主数据变更审批"这一具体
业务，未来接入的其他审批类型（如权限申请、应用接入）不应该反过来依赖
`approval` 包；独立 `workflow` 包才能被多个业务方复用，符合参考文档
"Flowable 封装层与具体审批业务解耦"的核心原则。

### 2. WorkflowService 接口与命令对象

```java
public interface WorkflowService {
    WorkflowInstanceResult start(StartProcessCommand command);
    void approve(ApproveCommand command);
    void reject(RejectCommand command);
    void returnTask(ReturnTaskCommand command);
    void withdraw(WithdrawCommand command);
    void transfer(TransferCommand command);
    void delegate(DelegateCommand command);
    void addSign(AddSignCommand command);
    List<ApprovalTaskVO> findTodoTasks(Long userId, TaskQuery query);
    List<ApprovalTaskVO> findDoneTasks(Long userId, TaskQuery query);
    ProcessInstanceDetailVO getProcessDetail(String processInstanceId);
}
```

命令对象是内部服务层参数，不是 HTTP 请求体，不加 `jakarta.validation`
注解（校验在 Controller 的 Request DTO 上做，转换为命令对象前已校验）；
均携带 `idempotencyKey`（可空，来自 `X-Request-Id` 请求头）、
`operatorId`、必要的业务标识（`processInstanceId`/`taskId`）。

### 3. 表设计（均 `tab_` 前缀，均含创建人/创建时间/更新人/更新时间四字段）

- **`tab_wf_process_model`**：流程模型主数据（一个流程的"身份"，可反复
  编辑）。字段：`process_code`（业务侧流程编码，如
  `MASTER_DATA_APPROVAL`，唯一）、`process_name`、`model_json`（当前
  草稿 DSL，`status=DRAFT`/`PUBLISHED` 时均可继续编辑覆盖）、`status`
  （`DRAFT`/`PUBLISHED`/`DISABLED`）、`current_definition_id`（指向
  `tab_wf_process_definition.id`，当前生效的已发布版本，`DRAFT` 状态
  下为空或指向上一个仍在生效的版本）。
- **`tab_wf_process_definition`**：流程模型的一次不可变发布快照
  （"版本历史列表"的数据来源）。字段：`process_model_id`（关联上表）、
  `process_code` 冗余、`version`（同一 `process_model_id` 下自增）、
  `flowable_definition_key`、`flowable_definition_id`、
  `model_json_snapshot`（发布时刻的 DSL 快照，只读）、`status`
  （`PUBLISHED`/`DISABLED`，`DISABLED` 对应挂起该
  `flowable_definition_id`，不删除、不影响运行中实例）、
  `published_by`、`published_time`。**关键修正**：初版设计曾把
  `tab_wf_node_assignee_rule` 挂在可变的 `flowable_definition_key`
  下，一旦同一流程发布新版本会连带改写旧版本的审批人规则，违反"旧版本
  运行中实例不受影响"的前提；改为下面的规则表挂在不可变的
  `process_definition_id` 上，每次发布都是"新插入一批规则行"而非
  "原地更新"。
- **`tab_wf_node_assignee_rule`**：`process_definition_id`
  （关联 `tab_wf_process_definition.id`，不再用可变的
  `flowable_definition_key`）、`node_id`（BPMN `userTask` 的 `id`）、
  `node_name`、`node_order`（用于展示"第几级审批"）、`assignee_type`
  （`AssigneeType` 枚举）、`assignee_value`（角色 code / 用户 id 等，
  按类型解释，指定组织负责人/发起人部门负责人类型时存放"要求的管理员
  角色 code"）、`approval_mode`（`SINGLE`/`AND`/`OR`/`PERCENT`）、
  `approval_percent`（仅 `PERCENT` 使用）、`empty_assignee_strategy`
  （`TO_WORKFLOW_ADMIN`/`AUTO_SKIP`/`REJECT`）、`allow_self_approval`、
  `allow_transfer`/`allow_delegate`/`allow_add_sign`/`allow_return`。
  这是"可配置多级审批"的核心表——BPMN 只声明节点顺序和网关，节点的
  审批人规则、会签模式、允许的操作都在这张表里配置，避免为每个新业务
  类型重新写 Java 分支；发布新版本时由 `WorkflowModelCompiler` 从 DSL
  重新生成整套规则行，插入时关联新的 `process_definition_id`，旧版本
  的规则行原样保留不动。
- **`tab_wf_process_instance`**：`flowable_instance_id`（唯一）、
  `process_definition_id`（关联 `tab_wf_process_definition.id`，记录
  本实例具体跑在哪个不可变版本上）、`business_type`、`business_id`
  （如 `tab_approval_request.id`）、`title`、`applicant_id`、
  `applicant_org_id`（发起时快照，见 Decision 6）、`status`
  （`RUNNING`/`APPROVED`/`REJECTED`/`WITHDRAWN`/`TERMINATED`）、
  `current_node_id`、`started_time`、`finished_time`。
- **`tab_wf_approval_task`**：`flowable_task_id`（唯一）、
  `process_instance_id`（关联上表主键）、`node_id`、`node_name`、
  `assignee_id`（单人）、`candidate_type`（`USER`/`ROLE`，会签/候选组
  场景为空则查 `tab_wf_approval_task_candidate` 明细）、`status`
  （`PENDING`/`CLAIMED`/`COMPLETED`/`TRANSFERRED`/`RETURNED`）、
  `created_time`、`finished_time`。"我的待办"查询 = 本表
  `assignee_id = 当前用户` 或候选人明细命中，`status=PENDING`；不直接
  查 `ACT_RU_TASK`。
- **`tab_wf_approval_task_candidate`**：`task_id`、`candidate_type`
  （`USER`/`ROLE`）、`candidate_value`，会签/候选组节点每个候选人一行，
  供"待我审批"按角色/按人聚合查询，避免解析 Flowable `IdentityLink`。
- **`tab_wf_approval_record`**：`process_instance_id`、`task_id`
  （可空，`SUBMIT`/`TERMINATE` 无关联任务）、`node_id`、`node_name`、
  `operator_id`、`action`（`SUBMIT`/`APPROVE`/`REJECT`/`RETURN`/
  `TRANSFER`/`DELEGATE`/`ADD_SIGN`/`WITHDRAW`/`TERMINATE`）、`comment`、
  `from_user_id`（转办/委派场景记录原处理人）、`created_time`——完整审批
  轨迹，`Withdraw` 判断"是否已有人审批过"也查这张表而非
  `ACT_HI_TASKINST`。
- **`tab_wf_operation_request`**：`request_key`（唯一，取
  `X-Request-Id`；为空时退化为不做幂等保护）、`task_id`、`operator_id`、
  `operation`、`status`（`SUCCESS`/`FAILED`）、`created_time`。

### 4. 审批人解析：TaskListener + ExecutionListener 组合

- 单人/候选组节点（`approval_mode=SINGLE`）：BPMN `userTask` 上挂
  `flowable:taskListener event="create"
  class="...WorkflowAssigneeTaskListener"`；监听器先由
  Flowable 的 `flowable_definition_id` 反查
  `tab_wf_process_definition.id`，再按
  `(process_definition_id, taskDefinitionKey)` 查
  `tab_wf_node_assignee_rule`，调用对应 `AssigneeResolver.resolve(...)`
  得到用户 id 集合，单一结果 `task.setAssignee(...)`，多个结果写
  `task.addCandidateUsers(...)` 并落 `tab_wf_approval_task_candidate`。
- 会签节点（`approval_mode` 为 `AND`/`OR`/`PERCENT`）：Multi-Instance
  的候选人集合必须在节点创建**之前**确定，`TaskListener(create)` 时机
  太晚。改在该节点挂
  `flowable:executionListener event="start"
  class="...WorkflowMultiInstanceExecutionListener"`，解析规则后把
  用户 id 列表写入一个按节点命名的流程变量（如 `approvers_securityAdmin`），
  BPMN 的 `multiInstanceLoopCharacteristics` 用
  `flowable:collection="approvers_securityAdmin"` 引用该变量，
  `completionCondition` 按 `approval_mode` 生成：
  - `AND` → 不写 `completionCondition`（默认要求全部完成）；
  - `OR` → `${nrOfCompletedInstances >= 1}`；
  - `PERCENT` → `${nrOfCompletedInstances/nrOfInstances >=
    <approval_percent/100>}`。
  会签的通过/驳回判定复用同一个变量名约定：单个实例把 `approved` 写入
  自己的本地作用域，网关判断走会签公共出口变量（Flowable
  Multi-Instance 结束后可通过聚合变量或后置 `ExecutionListener` 归并，
  实现细节见 tasks.md）。
- 空审批人：`empty_assignee_strategy=TO_WORKFLOW_ADMIN` 时转配置的
  "流程管理员"角色（复用 `tab_admin`/`tab_admin_role`）；`AUTO_SKIP`
  自动完成该节点（记一条 `APPROVE` 轨迹，`comment` 注明"无审批人自动通过"）；
  `REJECT` 直接终止流程并记录失败原因。
- 审批人是发起人本人：`allow_self_approval=false`（默认）时按空审批人
  策略处理；为 `true` 时保留候选人不变。

**备选方案**：把审批人解析逻辑直接写死在每个 `bizType` 对应的 Java
`TaskListener` 子类里（文档 2 里 `DeptLeaderTaskListener` 的写法）。
放弃原因——4A 已经有 4 个业务模块，未来还会增加，逐个写监听器子类会
导致规则改动要改代码 + 重新发布，`tab_wf_node_assignee_rule` 配置化
方案让"调整审批级数/换审批角色"变成一次数据变更，同时仍然遵循文档
"复杂查询逻辑写进 TaskListener 而不是 BPMN 表达式字符串"的安全建议
（未引入用户可控的 UEL 表达式，无表达式注入风险）。

### 5. 组织负责人 / 部门负责人解析的数据来源

复用现有 `tab_admin` + `tab_admin_role` + `tab_admin_org_scope`：
`OrgLeaderAssigneeResolver`（及 `ApplicantDeptLeaderAssigneeResolver`/
`ApplicantDeptParentLeaderAssigneeResolver`）查询：管辖范围覆盖目标组织
（`org_id` 命中，或 `include_children=1` 且目标组织在其子树）、且持有
`assignee_value` 指定角色 code（如 `DEPT_LEADER`）、状态启用的管理员，
取其关联的 `tab_admin.user_id`（需确认 `AdminEntity` 是否已有到用户的
关联字段，实现阶段核对）。上级部门负责人 = 从申请人所在组织沿
`org_parent_path` 向上找第一个能解析出负责人的组织，找不到则按空审批
人策略处理。`PositionAssigneeResolver` 本次仅返回空集合并记录
"岗位模块未接入"日志，触发空审批人策略。

### 6. 组织快照与幂等/越权

- `start` 时把 `applicantId`/`applicantOrgId` 写入
  `tab_wf_process_instance` 快照字段，全部 `AssigneeResolver` 只读这个
  快照，不实时重查申请人当前组织，避免审批中途申请人调岗导致路由突变
  （文档"边界场景"一节的建议）。
- 幂等：`approve`/`reject`/`returnTask`/`withdraw`/`transfer`/
  `delegate`/`addSign` 六个写操作统一经过
  `IdempotencyService.executeOnce(requestKey, operation, supplier)`：
  `requestKey` 非空时先查 `tab_wf_operation_request`，命中直接返回
  历史结果，未命中执行并落记录（`INSERT` 唯一键冲突时回退为查询已有
  结果，覆盖并发重复提交）。
- 越权校验：完成任务前必须满足以下任一——`assignee_id` 等于当前用户；
  当前用户在 `tab_wf_approval_task_candidate` 中以 `USER` 类型命中；
  当前用户持有的角色命中 `ROLE` 类型候选。候选组任务 `complete` 前若
  未 `claim`，服务层自动先 `claim` 再 `complete`（文档建议，减少前端
  复杂度）。

### 7. Reject / Return / Withdraw / Transfer / Delegate / AddSign

- **Reject**：沿用现有排他网关模式，`taskService.complete(taskId,
  Map.of("approved", false))`，流程直接走向拒绝结束事件。
- **Return**：`WorkflowService.returnTask(taskId, targetNodeId)` 内部
  使用 `runtimeService.createChangeActivityStateBuilder()` 把 token
  从当前活动移动到 `targetNodeId`，仅 `tab_wf_node_assignee_rule.
  allow_return=true` 的节点允许发起；目标节点重新触发
  `WorkflowAssigneeTaskListener` 重新解析审批人（不复用退回前的审批人
  快照）。
- **Withdraw**：`WithdrawPolicy.canWithdraw(instance, operator)`，默认
  实现 `BeforeFirstApprovalWithdrawPolicy` 查
  `tab_wf_approval_record` 是否已存在该实例的 `APPROVE`/`REJECT` 记录，
  存在则拒绝撤回；允许时 `runtimeService.deleteProcessInstance(...)`
  并把 `tab_wf_process_instance.status` 置为 `WITHDRAWN`。
- **Transfer**：改变当前任务的 `assignee` 为目标用户，记
  `from_user_id`，仅 `allow_transfer=true` 的节点允许。
- **Delegate**：使用 `taskService.delegateTask(taskId, userId)`，受托人
  完成后归还原处理人（Flowable 原生委派语义），仅 `allow_delegate=true`
  的节点允许。
- **AddSign**：`taskService.addUserIdentityLink` +
  `runtimeService.addMultiInstanceExecution`（会签节点）动态加签，
  减签对应 `deleteMultiInstanceExecution`；不手工改
  `nrOfInstances`/`nrOfCompletedInstances` 等内部计数变量。

### 8. `masterDataApprovalProcess` 的升级方式

新 BPMN：`开始 → 部门负责人审批(userTask, node_id=deptLeaderApprove) →
安全管理员审批(userTask, node_id=securityAdminApprove) → 排他网关按
`approved` 变量 → 已通过/已拒绝`；两个节点默认在
`tab_wf_node_assignee_rule` 里配置为 `approval_mode=SINGLE`、
`assignee_type=ORG_LEADER`（部门负责人）与
`assignee_type=ROLE`（安全管理员角色 code，具体值由部署时配置），
`empty_assignee_strategy=TO_WORKFLOW_ADMIN`。四个业务模块
（ORG/USER/POSITION/APP）复用同一个流程定义，`business_type` 用
`bizType` 区分，`tab_approval_request` 新增
`current_node_name` 冗余字段用于列表展示当前在哪一级，其余字段不变。
`ApprovalProcessServiceImpl` 改为薄封装，`start`/`approve`/`reject`/
`withdraw` 全部转调用 `WorkflowService`；`ApprovalRequestServiceImpl`
的"我的申请"/"待我审批"查询改为联查
`tab_wf_approval_task`（当前待办）而不是只看
`tab_approval_request.status`。

**BREAKING 影响**：已开启审批开关的 `bizType`，本次上线后新提交的申请
从"任意有权限的人都能审批"变为"必须是部门负责人或配置角色的人才能在
对应节点审批"；上线前已存在的、仍在"待审批"状态的旧流程实例继续按
Flowable 默认版本行为运行在旧的单节点 BPMN 版本上直到走完，不受影响。

**与设计器的关系**：默认两级流程仍按上述方式随 Flyway 迁移 + 静态 BPMN
资源启动（风险最低，不依赖尚未跑通的编译器链路）。迁移脚本同时插入一条
对应的 `tab_wf_process_model` 行（`status=PUBLISHED`，`model_json` 用
等价的 DSL 描述这两个节点），并把启动时部署产生的 Flowable
`flowable_definition_id` 回填到对应的 `tab_wf_process_definition` 行。
这样默认流程从第一天起就纳入同一套版本模型，后续业务方需要调整这两级
审批规则时可以直接在设计器里编辑并发布新版本，不需要区分"手写的初始
流程"和"设计器创建的流程"两条代码路径。

### 9. Workflow JSON DSL Schema

前端画布只暴露四种节点类型，DSL 大致形状：

```json
{
  "processCode": "MASTER_DATA_APPROVAL",
  "processName": "主数据变更审批流程",
  "nodes": [
    { "id": "start", "type": "START" },
    {
      "id": "deptLeaderApprove",
      "type": "APPROVAL",
      "name": "部门负责人审批",
      "assigneeType": "APPLICANT_DEPT_LEADER",
      "assigneeValue": null,
      "approvalMode": "SINGLE",
      "emptyAssigneeStrategy": "TO_WORKFLOW_ADMIN",
      "allowSelfApproval": false,
      "allowTransfer": true,
      "allowDelegate": true,
      "allowAddSign": false,
      "allowReturn": false
    },
    {
      "id": "securityAdminApprove",
      "type": "APPROVAL",
      "name": "安全管理员审批",
      "assigneeType": "ROLE",
      "assigneeValue": "SECURITY_ADMIN",
      "approvalMode": "OR",
      "emptyAssigneeStrategy": "TO_WORKFLOW_ADMIN",
      "allowSelfApproval": false,
      "allowTransfer": true,
      "allowDelegate": true,
      "allowAddSign": true,
      "allowReturn": true
    },
    { "id": "end", "type": "END" }
  ],
  "edges": [
    { "from": "start", "to": "deptLeaderApprove" },
    { "from": "deptLeaderApprove", "to": "securityAdminApprove" },
    { "from": "securityAdminApprove", "to": "end" }
  ]
}
```

`CONDITION` 节点复用 `edges` 表达分支，每条从条件节点出发的边携带
`condition: { field, operator, value }`（`field` 只能是流程启动变量里
声明过的字段，`operator` 限定为 `EQ`/`NE`/`GT`/`GTE`/`LT`/`LTE`
白名单，`value` 只接受字符串/数字/布尔字面量），编译器据此生成
`exclusiveGateway` 的 `conditionExpression`；**不允许**用户直接输入
UEL 表达式字符串，避免表达式注入（文档 2"安全"一节的建议）。

`APPROVAL` 节点字段与 `tab_wf_node_assignee_rule` 逐字段对应，前端
属性面板即为该表字段的表单化编辑；`approvalMode` 非 `SINGLE` 时额外
展示会签比例（`PERCENT`）输入框。

DSL 结构校验（保存草稿时前端做基础校验，发布时后端 compiler 做权威
校验，两者规则一致）：唯一 `START` 节点、至少一个 `END` 节点、节点 id
在同一模型内唯一、每条边引用的 `from`/`to` 必须是已声明的节点 id、
从 `START` 出发必须存在到达任一 `END` 的路径、`CONDITION` 节点的出边
必须覆盖所有情况（要求配置一条无 `condition` 的默认边作为兜底分支，
否则编译失败）。

### 10. WorkflowModelCompiler

```java
public interface WorkflowModelCompiler {
    CompiledProcess compile(ProcessModelDsl dsl);
}

public record CompiledProcess(
    BpmnModel bpmnModel,
    List<NodeAssigneeRuleDraft> assigneeRules
) {}
```

编译步骤：① 按 Decision 9 的规则做结构校验，失败则抛出携带具体错误
定位（节点 id/边）的 `WorkflowModelValidationException`；② 用 Flowable
`org.flowable.bpmn.model.*` 对象模型 API 逐节点构建
`StartEvent`/`UserTask`/`ExclusiveGateway`/`EndEvent`/`SequenceFlow`，
`APPROVAL` 节点若 `approvalMode` 非 `SINGLE` 则附加
`MultiInstanceLoopCharacteristics`（引用按节点 id 生成的集合变量名，
`completionCondition` 规则见 Decision 4）；每个 `UserTask` 统一挂上
`WorkflowAssigneeTaskListener`（`SINGLE`）或
`WorkflowMultiInstanceExecutionListener`（会签），不依赖 DSL 自身
携带监听器配置；③ 用 Flowable 自带的 `ProcessValidator` 对生成的
`BpmnModel` 做二次校验（捕获对象模型阶段引入的结构问题）；④ 把
`APPROVAL` 节点字段映射为 `NodeAssigneeRuleDraft` 列表返回，由调用方
（发布流程）在同一事务里连同新的 `tab_wf_process_definition` 行一并
落库。编译过程 SHALL NOT 引入用户可控的 UEL 表达式拼接。

### 11. 流程模型生命周期与前端设计器交互

- **保存草稿**：`PUT /api/workflow/process-models/{id}/draft`，仅更新
  `tab_wf_process_model.model_json`，不触碰 Flowable，`status` 若原为
  `PUBLISHED`/`DISABLED` 不变（表示"已发布版本仍在跑，草稿是下一次
  发布的候选内容"）。
- **发布**：`POST /api/workflow/process-models/{id}/publish`，调用
  `WorkflowModelCompiler` 编译当前 `model_json`，编译失败直接返回
  校验错误、不部署；编译成功后 `repositoryService.createDeployment()
  .addBpmnModel(...)` 部署，`version` 在该 `process_model_id` 下自增，
  新增一行 `tab_wf_process_definition`（`status=PUBLISHED`）与对应的
  `tab_wf_node_assignee_rule` 记录，更新
  `tab_wf_process_model.current_definition_id` 与
  `status=PUBLISHED`；此前已发布的旧版本行保持不变，仍支持其运行中的
  流程实例。
- **下线**：`POST /api/workflow/process-models/{id}/disable`，对
  `current_definition_id` 对应的 `flowable_definition_id` 调用
  `repositoryService.suspendProcessDefinitionById(...)`
  （不级联挂起运行中实例），`tab_wf_process_model.status=DISABLED`，
  `tab_wf_process_definition.status=DISABLED`；此后新发起该
  `processCode` 的流程被拒绝，运行中实例不受影响。
- **重新启用**：对最近一个 `DISABLED` 的版本调用
  `activateProcessDefinitionById(...)` 并把两处 `status` 改回
  `PUBLISHED`；不支持跳过版本直接激活更早的历史版本。
- **版本历史列表**：`GET /api/workflow/process-models/{id}/versions`
  返回该 `process_model_id` 下全部 `tab_wf_process_definition` 行
  （倒序），每行可查看只读的 `model_json_snapshot`，不提供"编辑历史
  版本"入口（历史版本永远不可变，需要调整则编辑当前草稿后重新发布）。
- **前端画布**（Vue Flow）：`ProcessDesignerView.vue` 维护
  `nodes`/`edges` 响应式状态，双向映射 Decision 9 的 DSL 结构；节点
  面板 `NodePropertyPanel.vue` 按选中节点类型渲染 Element Plus 表单，
  `APPROVAL` 节点表单字段直接对应 `tab_wf_node_assignee_rule` 各列，
  校验规则与后端保持一致（复用同一份字段级校验描述，避免前后端校验
  规则漂移）；保存/发布按钮分别调用上述两个接口。
- **权限门控**：流程模型列表页访问受 `WorkflowDesign:model:view`
  门控；新增/编辑草稿受 `WorkflowDesign:model:edit`；发布受
  `WorkflowDesign:model:publish`；下线受 `WorkflowDesign:model:disable`。

### 12. 生产配置对齐

`backend/src/main/resources/application.yml` 当前
`flowable.database-schema-update: true`，与其自身注释
"生产环境禁止自动建表/改表"及 `flowable-database-bootstrap` 能力的
初衷矛盾。本次一并改为 `false`（配合已存在的
`flowable.mysql.create.7.2.0.sql` 首次初始化脚本），纳入 tasks.md。

## Risks / Trade-offs

- [审批规则配置表让路由"数据化"，出配置错误（如两个节点配了相同
  `node_id`、`approval_percent` 越界）不会在编译期发现] →
  在 `WorkflowAssigneeTaskListener`/`ExecutionListener` 里对解析结果
  做防御性校验，解析失败或配置非法时按 `TO_WORKFLOW_ADMIN` 兜底并记录
  错误日志，不让流程卡死或抛出未捕获异常导致 Flowable 事务回滚。
- [`tab_admin_org_scope` 目前是为"管理员管辖范围"设计的表，被
  `OrgLeaderAssigneeResolver` 复用为"组织负责人"数据源，语义上是借用
  而非新建专门概念] → 在 Requirement/Scenario 与代码注释中明确这层
  借用关系，待未来真的需要区分"分配的审批权限"与"字面意义组织负责人"
  时，再拆出独立字段，不在本次过度设计。
- [Multi-Instance 会签的"完成条件归并"实现在 Flowable 层有一定复杂度，
  历史上是这类改造最容易出 bug 的地方] → 落地阶段先用 `AND`
  （全部通过）跑通两级默认流程验证主链路，`OR`/`PERCENT` 补充单元测试
  覆盖 `nrOfInstances`/`nrOfCompletedInstances` 边界（1 人、全部通过、
  部分驳回提前终止）。
- [BREAKING 变更影响已在生产环境开启审批开关的业务] → 若目标环境已有
  在跑的审批数据，上线前需要业务侧确认可接受"新流程强制走两级审批"，
  必要时先把默认规则的第二级设置为空审批人 + `AUTO_SKIP`，等真正配置
  好安全管理员角色后再收紧。
- [DSL → BpmnModel 编译器是全新组件，覆盖不到的 DSL 组合可能生成
  Flowable 拒绝部署或运行时行为异常的 BPMN] → 编译产物先过 Flowable
  自带 `ProcessValidator` 二次校验（Decision 10），并在测试环境对
  "单人串行""两级会签混合""条件分支"等典型组合各建一个集成测试用例，
  再开放给业务方自建流程。
- [业务管理员可以自己创建/发布新流程，误配置（如条件分支缺默认兜底、
  会签比例填 0）比过去"只有开发改 BPMN"时风险更高] → 发布前的结构
  校验（Decision 9）设为硬性拦截而非警告，`WorkflowDesign:model:publish`
  单独设权限点，不与"编辑草稿"权限等同，避免普通编辑者误发布。
- [新增前端第三方依赖 Vue Flow，属于本项目当前技术栈之外的新引入] →
  实现阶段落地前与用户确认具体包名与版本（`@vue-flow/core` 等），
  仅用于设计器画布这一个页面，不影响其余页面的技术栈。
- [`FlowableWorkflowService.start()` 内 `runtimeService.startProcessInstanceById(...)`
  会同步执行到第一个用户任务创建，此时 `tab_wf_process_instance.flowable_instance_id`
  要等 `start()` 方法最后一步才回填；Decision 4 的
  `WorkflowAssigneeTaskListener`/`WorkflowMultiInstanceTaskListener`/
  `WorkflowMultiInstanceExecutionListener` 若按该列反查会命中不到，导致
  流程第一个节点的 `tab_wf_approval_task.process_instance_id` 被错误写成
  `NULL`（已发现并修复的生产缺陷，回归测试见
  `FirstNodeInstanceLinkageBugTest`）] → 三个监听器改为优先按 Flowable
  businessKey 反查（`start()` 发起流程时已把 `tab_wf_process_instance.id`
  作为 businessKey 传入，从流程创建之初即可用，不存在该时序问题），查不到
  再回退按 `flowable_instance_id`，兼容非经由 `WorkflowService` 发起的
  历史调用方。
- [`WorkflowSpringContext`（Decision 4 提到的、供反射实例化的
  `TaskListener`/`ExecutionListener` 获取 Spring Bean 的静态桥接类）把
  `ApplicationContext` 缓存进 JVM 级静态字段，只在每次 Spring 容器刷新时
  被覆盖一次；单个 JVM 内先后创建多个不同配置 `ApplicationContext`（例如
  全量测试套件里穿插着不同 `@MockBean`/属性组合的 `@SpringBootTest`）时，
  该静态字段会停留在"最后一次被刷新的容器"，导致监听器经由它取到的
  Mapper Bean 绑定到错误容器自己的数据源连接，看不见同一个 Spring 事务里
  刚插入但尚未提交的数据] → `getBean(Class)` 改为优先通过 Flowable 当前
  命令上下文（`Context.getProcessEngineConfiguration()`）取"当前正在执行
  的这次调用所属"的 `SpringProcessEngineConfiguration.getApplicationContext()`
  （该配置对象随每个 Spring 容器各自创建一份，与容器一一对应），仅命令
  上下文不可用时才回退读静态字段。
- [全量 `./gradlew build`/`clean test` 时偶发
  `SQLSyntaxErrorException: Unknown column 'processInstanceId'`，定位为
  MyBatis-Plus `TableInfoHelper` 的类级别全局静态缓存（`TABLE_INFO_CACHE`
  按 `Class` 而非按 `Configuration`/`ApplicationContext` 缓存 `TableInfo`）
  在"JVM 内多个 Spring 容器共存"场景下的已知通病，与上一条同属一类问题
  但命中的是 MyBatis-Plus 自身内部缓存] → 非本次 change 新增代码引入，
  也非 workflow 模块专属（多次单独运行
  `./gradlew test --tests "cn.nihility.rbac.workflow.*"` 均 100% 稳定
  通过），修复需要动 `build.gradle` 测试任务隔离策略或更大范围的架构
  调整，超出本次 change 范畴，留作后续技术债，不在本次安排修复。

## Migration Plan

1. 新增 Flyway 迁移脚本：8 张新表（`tab_wf_process_model`、
   `tab_wf_process_definition`、`tab_wf_node_assignee_rule` 等，见
   Decision 3）+ `tab_approval_request.current_node_name` 新增字段；
   预置两级默认规则数据（部门负责人 / 安全管理员角色 code，占位角色
   code 需部署前与业务方确认）与对应的 `tab_wf_process_model`
   `PUBLISHED` 行。
2. 替换 `approval-process.bpmn20.xml` 为两节点版本，`process
   definition key` 保持 `masterDataApprovalProcess` 不变（Flowable
   按 key 自动生成新 version，历史运行实例不受影响）；应用启动部署后
   把生成的 `flowable_definition_id` 回填第 1 步预置的
   `tab_wf_process_definition` 行。
3. 新增 `cn.nihility.rbac.workflow` 包代码（含 `designer` 子包与
   `WorkflowModelCompiler`）；`approval` 包内服务改为委托调用，不再
   直接注入 Flowable Service。
4. `application.yml` 关闭 `flowable.database-schema-update`。
5. 新增前端 `frontend/src/views/workflow/**`（流程模型列表、版本历史、
   Vue Flow 设计器画布与节点属性面板）及对应 `api`/`store`/`types`；
   引入 `@vue-flow/core` 前端依赖需先与用户确认。
6. 更新 `权限资源.txt`：新增 `WorkflowDesign` 模块的
   `model:view`/`model:edit`/`model:publish`/`model:disable` 四个
   权限点；若新增操作按钮（转办/委派/加签/退回）复用现有
   `ApprovalManagement:request:approve` 权限点还是新增细分权限点，在
   tasks.md 落地时最终确定并同步此文件。
7. 回滚：保留旧版 BPMN 文件与 `ApprovalProcessServiceImpl` 的实现历史
   （通过 git revert），因为 Flowable 版本化机制，回滚代码不影响已经在
   新版本上产生的运行时数据完整性，但需要业务侧接受"回滚后新发起的
   申请退回单节点审批"这一行为差异；设计器发布产生的版本回滚同样通过
   挂起对应 `flowable_definition_id` 实现，不删除已发布版本数据。

## Open Questions

1. ~~`tab_admin` 到 `tab_user`/登录用户 id 的关联字段名称~~——已确认为
   `AdminEntity.userId`，`AssigneeResolver` 统一返回 `tab_user.id`。
2. 默认两级规则里"安全管理员"对应哪个具体角色 code，需要业务方在部署
   前确认（当前角色管理模块是否已有预置的安全管理员角色）。
3. 转办/委派/加签/退回是否需要独立于 `ApprovalManagement:request:approve`
   的细分权限点，还是沿用同一个权限点由节点级 `allow_*` 字段控制——
   倾向后者（减少权限点数量），tasks.md 阶段最终确认。
4. 会签场景的部分驳回是否要提前终止整个多实例节点（"一票否决"）还是
   等所有分支完成再统一判定——本次默认按"一票否决直接驳回"处理，若
   业务需要"少数服从多数"再用 `PERCENT` 模式配置，具体行为在 spec 中
   需要写清楚。
5. `@vue-flow/core` 的具体版本号与是否需要 `@vue-flow/controls`/
   `@vue-flow/background` 等配套包，落地实现前需要与用户确认后再
   写入 `package.json`。
6. 流程模型是否允许业务方自由创建"新的 `processCode`"（即真正意义上
   接入全新业务类型），还是本次设计器只服务于编辑现有的
   `MASTER_DATA_APPROVAL` 一个流程——倾向前者（否则设计器的通用性
   意义不大），但"新流程如何与具体业务代码的 `WorkflowService.start`
   调用点对应起来"（`processCode` 与业务方 Java 代码里的调用之间没有
   自动发现机制，仍需业务方硬编码调用）需要在 spec 中写清楚这一边界，
   避免误解为"配置了新流程业务就自动接入"。
