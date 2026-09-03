## Why

现有 `master-data-approval-workflow` 能力只有一个写死的单节点 Flowable 流程
（`masterDataApprovalProcess`：提交 → 一个不限定候选人的 `userTask` → 按
`approved` 变量分流），审批人完全靠 `ApprovalManagement:request:approve`
权限点校验，不支持多级审批、会签、转办、委派、加签、退回，也没有可复用的
审批人解析能力。4A 场景下组织/人员/岗位/角色/应用变更通常需要"部门负责人
→ 安全管理员"这类多级审批，且未来会有更多业务类型接入审批，业务层不应该
反复重写一遍流程编排代码，也不应该直接依赖 Flowable 的 `ACT_*` 表和
`RuntimeService`/`TaskService` 等引擎 API。需要一个通用的、以自有抽象层
包装 Flowable 的多级审批引擎，把流程状态机、审批人解析、待办/轨迹查询、
幂等与权限校验固化为可复用能力，四个现有业务模块作为第一个接入方完成
从单节点到多级审批的演进。

## What Changes

- 新增 `WorkflowService` 抽象接口（`start`/`approve`/`reject`/
  `returnTask`/`withdraw`/`transfer`/`delegate`/`addSign`/`findTasks`/
  `getProcessDetail`），业务层只依赖这层接口；底层由
  `FlowableWorkflowService` 适配 Flowable 的 `RuntimeService`/
  `TaskService`/`RepositoryService`/`HistoryService`，业务代码和
  Controller 不再直接注入这些 Flowable Service。
- 新增审批引擎自己的业务表（`tab_` 前缀，遵循项目表命名规范）：
  流程定义表、流程实例表、审批任务表（服务"我的待办/已办"查询，不直接
  查询 `ACT_RU_TASK`）、审批记录/轨迹表（含 `SUBMIT`/`APPROVE`/`REJECT`/
  `RETURN`/`TRANSFER`/`DELEGATE`/`ADD_SIGN`/`WITHDRAW`/`TERMINATE` 动作）、
  操作幂等表（基于请求方传入的幂等键去重审批类写操作）。
- 新增 `AssigneeResolver` 审批人解析体系，至少支持：指定人员、指定角色、
  指定岗位、指定组织负责人、发起人部门负责人、发起人部门上级负责人、
  流程发起人、上一节点审批人；并对"解析结果为空""审批人为申请人本人"
  等边界场景提供可配置策略（转流程管理员 / 自动跳过 / 允许自审）。
- 基于 Flowable 原生 Multi-Instance 能力支持会签，提供 `AND`（全部通过）/
  `OR`（任一通过）/`PERCENT`（比例通过）三种会签模式，不在业务层自行
  重新实现会签状态机。
- 明确区分 `Reject`（排他网关直接终止流程）与 `Return`（退回历史节点，
  通过 `returnTask(taskId, targetNodeId)` 封装，不直接暴露 Flowable 的
  `ChangeActivityStateBuilder`）；`Withdraw` 走可配置的 `WithdrawPolicy`
  （如仅第一个审批人处理前允许撤回）。
- **BREAKING**：把现状的 `masterDataApprovalProcess` 单节点 BPMN
  （`backend/src/main/resources/processes/approval-process.bpmn20.xml`）
  与 `cn.nihility.rbac.approval` 包下的 `ApprovalProcessService` 等代码
  重构为新引擎下的一个具体流程定义接入示例：四个现有业务模块
  （ORG/USER/POSITION/APP）对外的提交/审批/拒绝/撤回接口签名和响应结构
  基本不变，但内部改为经由 `WorkflowService` 驱动，且审批人从"不限定候选
  人，仅靠权限点校验"演进为可配置的多级审批（默认提供"部门负责人 →
  安全管理员"两级流程），因此已开启审批的 `bizType` 在本次变更后审批人
  解析行为发生变化，属于对现有能力的破坏性调整。
- 审批类写操作（`approve`/`reject`/`returnTask`/`withdraw`/`transfer`/
  `delegate`/`addSign`）加入幂等保护与三维度（`assignee`/
  `candidateUser`/`candidateGroup`）越权校验；启动流程与业务落库保持在
  同一本地事务内。
- 新增前端可视化流程设计器：基于 Vue Flow 画布，节点只暴露业务语言
  （开始 / 审批 / 条件 / 结束），不直接暴露 BPMN 概念；画布保存为自定义
  Workflow JSON DSL，审批节点的属性面板（角色/人员/组织负责人等审批人
  来源、会签模式、空审批人策略、是否允许自审/转办/委派/加签/退回）直接
  对应 `workflow-approval-engine` 能力里的节点审批人规则字段。
- 新增后端 `WorkflowModelCompiler`：把 DSL 编译为 Flowable `BpmnModel`
  并在编译期做结构校验（孤立节点、条件分支未覆盖、审批节点必填属性
  缺失等），校验通过后才允许发布部署。
- 新增流程模型的完整生命周期管理：`DRAFT`（可反复编辑）→
  `PUBLISHED`（编译部署为一个不可变的 Flowable 新版本，旧版本运行中的
  实例不受影响，Flowable 按 key 自动新增 version）→
  `DISABLED`（挂起该版本，不再接受新发起，运行中实例不受影响，
  Flowable 自动回退到前一个未挂起版本承接新发起请求，实现"回滚"）；
  提供按流程编码查看版本历史列表（发布人、发布时间、DSL 快照、状态）
  的页面。

明确不在本次范围（留给后续 change）：Outbox + MQ 事件通知、审批超时
升级与催办、流程监控运维、跨流程调用（Call Activity）、信号/消息事件；
设计器 DSL 节点类型本次只做 `开始`/`审批`/`条件`/`结束` 四种，`抄送`
（无网关意义的知会节点，依赖通知基础设施）与`并行网关`（区别于会签，
指同时激活多条不同节点的分支）留待后续 change 补充。

## Capabilities

### New Capabilities
- `workflow-approval-engine`: 通用多级审批引擎——`WorkflowService` 抽象
  层、流程定义/实例/任务/轨迹/幂等业务表、`AssigneeResolver` 审批人解析
  体系、会签（Multi-Instance）、Reject/Return/Withdraw/Transfer/
  Delegate/AddSign 操作语义、待办与已办查询、越权校验。
- `workflow-process-designer`: 前端 Vue Flow 可视化流程设计器 + Workflow
  JSON DSL + 后端 `WorkflowModelCompiler`（DSL 编译为 BPMN 并校验）+
  流程模型草稿/发布/下线三态生命周期管理与版本历史查看。

### Modified Capabilities
- `master-data-approval-workflow`: 审批通过/拒绝/撤回的底层驱动方式从
  直接调用 Flowable `RuntimeService`/`TaskService` 改为经由
  `WorkflowService`；单节点、不限定候选人的审批流程升级为可配置的多级
  审批（默认"部门负责人 → 安全管理员"两级），审批人解析、越权校验、
  幂等保护规则相应变化；"我的申请"/"待我审批"查询改为读取新引擎的审批
  任务表而非直接查询 Flowable 运行时表。

## Impact

- 受影响代码：`backend/src/main/java/cn/nihility/rbac/approval/**`
  （`ApprovalProcessService`/`ApprovalProcessServiceImpl`/
  `ApprovalRequestService` 及其实现）、
  `backend/src/main/resources/processes/approval-process.bpmn20.xml`。
- 新增后端代码：`cn.nihility.rbac.workflow` 包（`WorkflowService` 抽象、
  `FlowableWorkflowService` 适配、`AssigneeResolver` 及其实现、审批业务
  表对应的 entity/mapper/service、`WorkflowModelCompiler`、流程模型
  草稿/发布/下线的 Controller/Service）。
- 新增前端代码：`frontend/src/views/workflow/**`（流程模型列表、版本
  历史、Vue Flow 设计器画布与节点属性面板）、对应 `api/`/`stores/`/
  `types/`。
- 数据库：新增 Flyway 迁移脚本（流程模型、流程定义版本、流程实例、
  审批任务/候选人明细、审批记录、操作幂等共 8 张表），可能需要为
  `tab_approval_request` 补充关联新引擎流程实例的字段。
- 依赖：后端复用已引入的 `flowable-spring-boot-starter-process`
  （7.2.0），不新增 Java 依赖；前端新增 `@vue-flow/core`
  （及按需的 `@vue-flow/controls`/`@vue-flow/background`）第三方 npm
  依赖，属于新增第三方依赖，需要在落地实现前与用户确认。
- 权限资源：新增 `WorkflowDesign` 模块的页面/按钮权限点（流程设计器
  访问、编辑草稿、发布、下线），需要同步更新 `权限资源.txt`。
- 兼容性：四个业务模块调用方（Controller 层对外契约）尽量保持不变，
  但已开启审批开关的 `bizType` 审批人解析行为和审批级数发生变化，
  需要在 `master-data-approval-workflow` 的 delta spec 中明确写清楚
  BREAKING 的具体范围。
