## 1. 数据库迁移

- [x] 1.1 新增 Flyway 迁移脚本，创建 `tab_wf_process_model`、
      `tab_wf_process_definition`、`tab_wf_node_assignee_rule`、
      `tab_wf_process_instance`、`tab_wf_approval_task`、
      `tab_wf_approval_task_candidate`、`tab_wf_approval_record`、
      `tab_wf_operation_request` 八张表（见 design.md Decision 3），
      字段命名遵循驼峰/下划线映射规则，均含创建人/创建时间/更新人/
      更新时间四字段，禁止窗口函数/CTE 等 MySQL 5.7 不兼容写法；
      `tab_wf_node_assignee_rule`/`tab_wf_process_instance` 均以不可变
      的 `process_definition_id` 关联版本，不使用可变的 Flowable key。
- [x] 1.2 同一脚本内为 `tab_approval_request` 新增 `current_node_name`
      字段（可空，流程结束后置空）。
- [x] 1.3 与业务方确认默认两级规则（部门负责人角色 code、安全管理员
      角色 code，design.md Open Question 2），预置对应的
      `tab_wf_process_model`（`status=PUBLISHED`，`model_json` 为
      Decision 9 示例 DSL）、`tab_wf_process_definition`（`version=1`，
      启动时回填 `flowable_definition_id`）与
      `tab_wf_node_assignee_rule` 初始数据。
- [x] 1.4 将 `backend/src/main/resources/application.yml` 的
      `flowable.database-schema-update` 改为 `false`，核对注释与
      `flowable-database-bootstrap` 能力描述一致。

## 2. 默认流程定义

- [x] 2.1 重写 `backend/src/main/resources/processes/approval-process.bpmn20.xml`
      为两节点版本（`deptLeaderApprove` → `securityAdminApprove` →
      排他网关 → 已通过/已拒绝），`process` id 保持
      `masterDataApprovalProcess` 不变；两个 `userTask` 分别挂
      `flowable:taskListener event="create"` 指向
      `WorkflowAssigneeTaskListener`。
- [x] 2.2 更新 XML 头部注释，说明多级审批的节点职责、审批人规则来自
      `tab_wf_node_assignee_rule` 而非硬编码，以及本流程同时是
      `tab_wf_process_model` 里 `MASTER_DATA_APPROVAL` 的初始版本
      （design.md Decision 8"与设计器的关系"）。
- [x] 2.3 应用启动流程完成部署后，补一次数据初始化/校验逻辑，确认
      1.3 预置的 `tab_wf_process_definition.flowable_definition_id`
      与实际部署产生的一致（或改为启动时代码自动探测回填）。

## 3. Workflow 引擎抽象层（`cn.nihility.rbac.workflow`）

- [x] 3.1 新建 `constant` 包：`ApprovalAction`、`AssigneeType`、
      `ApprovalMode`、`EmptyAssigneeStrategy`、`TaskStatus`、
      `ProcessInstanceStatus`、`ProcessModelStatus` 等枚举。
- [x] 3.2 新建 `entity`/`mapper`：对应 1.1 的八张表。
- [x] 3.3 新建 `dto`：`StartProcessCommand`/`ApproveCommand`/
      `RejectCommand`/`ReturnTaskCommand`/`WithdrawCommand`/
      `TransferCommand`/`DelegateCommand`/`AddSignCommand`/
      `TaskQuery`/`ApprovalTaskVO`/`ProcessInstanceDetailVO`。
- [x] 3.4 新建 `engine.WorkflowService` 接口（签名见 design.md
      Decision 2）。
- [x] 3.5 新建 `engine.flowable.FlowableWorkflowService` 实现：
      `start`/`approve`/`reject`/`returnTask`/`withdraw`/`transfer`/
      `delegate`/`addSign`/`findTodoTasks`/`findDoneTasks`/
      `getProcessDetail`；所有写方法使用
      `@Transactional(rollbackFor = Exception.class, propagation =
      Propagation.REQUIRED)`。
- [x] 3.6 新建 `mapstruct`：entity ↔ VO 转换，接口内声明
      `INSTANCE = Mappers.getMapper(...)` 静态单例，不使用
      `componentModel = "spring"`。

## 4. 审批人解析

- [x] 4.1 新建 `assignee.AssigneeResolver` 接口。
- [x] 4.2 实现 `UserAssigneeResolver`（指定人员）、
      `RoleAssigneeResolver`（指定角色）、
      `PositionAssigneeResolver`（占位，恒返回空集合并记录日志）、
      `InitiatorAssigneeResolver`（流程发起人）、
      `PreviousApproverAssigneeResolver`（上一节点审批人）。
- [x] 4.3 实现 `OrgLeaderAssigneeResolver`/
      `ApplicantDeptLeaderAssigneeResolver`/
      `ApplicantDeptParentLeaderAssigneeResolver`：复用
      `tab_admin`+`tab_admin_role`+`tab_admin_org_scope`
      （`AdminEntity.userId` 关联 `tab_user.id`，见 design.md
      Decision 5），按组织路径向上查找负责人。
- [x] 4.4 实现 `WorkflowAssigneeTaskListener`（单人/候选组节点，
      `TaskListener event=create`）：由 Flowable
      `flowable_definition_id` 反查 `tab_wf_process_definition.id`，
      再按 `(process_definition_id, taskDefinitionKey)` 查
      `tab_wf_node_assignee_rule`，调用对应 `AssigneeResolver`，写
      `assignee`/候选人明细，处理空审批人与自审场景（按
      `empty_assignee_strategy`/`allow_self_approval`）。
- [x] 4.5 实现 `WorkflowMultiInstanceExecutionListener`（会签节点，
      `ExecutionListener event=start`）：解析候选人集合写入流程变量，
      按 `approval_mode` 生成/校验 `completionCondition` 语义。

## 5. 会签、Reject/Return/Withdraw/Transfer/Delegate/AddSign

- [x] 5.1 落地 `AND`/`OR`/`PERCENT` 三种会签完成条件的判定与单元测试
      （覆盖 1 人、全部通过、部分驳回提前终止、比例边界）。
- [x] 5.2 实现 `returnTask`：封装
      `runtimeService.createChangeActivityStateBuilder()`，校验目标
      节点 `allow_return`，退回后重新触发审批人解析。
- [x] 5.3 实现 `policy.WithdrawPolicy` 接口与
      `BeforeFirstApprovalWithdrawPolicy` 默认实现（查
      `tab_wf_approval_record` 是否已存在 `APPROVE`/`REJECT` 记录）。
- [x] 5.4 实现 `transfer`/`delegate`：校验节点 `allow_transfer`/
      `allow_delegate`，记录 `from_user_id`。
- [x] 5.5 实现 `addSign`（含会签场景的动态加签）：使用
      `taskService.addUserIdentityLink`/
      `runtimeService.addMultiInstanceExecution`，校验节点
      `allow_add_sign`。

## 6. 幂等与越权校验

- [x] 6.1 新建 `service.IdempotencyService`：基于
      `tab_wf_operation_request` 的 `executeOnce(requestKey,
      operation, supplier)`，唯一键冲突时回退查询已有结果。
- [x] 6.2 在 `approve`/`reject`/`returnTask`/`withdraw`/`transfer`/
      `delegate`/`addSign` 入口接入幂等保护（读取
      `X-Request-Id` 请求头）。
- [x] 6.3 实现任务处理越权校验（assignee/candidateUser/
      candidateGroup 三维度命中判断），候选组任务未认领时自动
      `claim` 再 `complete`。

## 7. 待办/已办查询

- [x] 7.1 新建 `service.WorkflowTaskService`：基于
      `tab_wf_approval_task`/`tab_wf_approval_task_candidate` 的
      "我的待办"查询、基于 `tab_wf_approval_record` 的"我的已办"查询。
- [x] 7.2 补充 springdoc-openapi 注解（`@Tag`/`@Operation`）。

## 8. 现有审批业务模块改造（`cn.nihility.rbac.approval`）

- [x] 8.1 `ApprovalProcessServiceImpl` 改为委托
      `WorkflowService`，移除对 `RuntimeService`/`TaskService` 的
      直接注入。
- [x] 8.2 `ApprovalRequestServiceImpl` 的"我的申请"/"待我审批"查询
      改为联查 `tab_wf_approval_task`（当前节点候选人命中）而非仅看
      `tab_approval_request.status`；查询结果携带 `currentNodeName`。
- [x] 8.3 审批通过逻辑改为：非最终节点仅推进流程并更新
      `currentNodeName`；仅最终节点通过后执行既有创建/更新/状态切换/
      删除方法（按 delta spec `master-data-approval-workflow`
      "审批通过后执行既有业务逻辑"）。
- [x] 8.4 审批拒绝、撤回逻辑对齐 delta spec 对应 Requirement（拒绝
      任一级都直接终止；撤回仅在尚无任何一级审批记录时允许）。
- [x] 8.5 审批通过/拒绝接口在原有 `ApprovalManagement:request:approve`
      权限点校验基础上，追加"当前节点候选人命中"校验（`approve`/
      `reject` 落地为"按 `tab_approval_request.process_instance_id`
      定位当前开放任务，交由 `WorkflowService.approve`/`reject` 内部的
      assignee/candidateUser/candidateGroup 越权校验"，未在 `approval`
      包内重复实现校验逻辑；`pagePending` 复用
      `TaskAuthorizationService.isAuthorized` 做同口径过滤）。

## 9. WorkflowModelCompiler（DSL → BPMN）

- [x] 9.1 新建 `designer.dto.ProcessModelDsl` 及节点/边的子类型
      （`StartNodeDsl`/`ApprovalNodeDsl`/`ConditionNodeDsl`/
      `EndNodeDsl`/`EdgeDsl`），字段对齐 design.md Decision 9 的 DSL
      Schema。
- [x] 9.2 实现 DSL 结构校验：唯一开始节点、至少一个结束节点、节点 id
      唯一、边引用节点存在、开始到结束可达、条件节点存在兜底默认边、
      审批节点必填字段完整；校验失败抛出携带节点/边定位信息的
      `WorkflowModelValidationException`。
- [x] 9.3 实现 `designer.compiler.WorkflowModelCompiler`：用 Flowable
      `org.flowable.bpmn.model.*` 对象模型 API 构建
      `StartEvent`/`UserTask`/`ExclusiveGateway`/`EndEvent`/
      `SequenceFlow`；`APPROVAL` 节点按 `approvalMode` 附加
      `MultiInstanceLoopCharacteristics`；统一挂载
      `WorkflowAssigneeTaskListener`/
      `WorkflowMultiInstanceExecutionListener`；条件表达式只从白名单
      比较符 + 字段/值拼装，不接受自由表达式字符串。
- [x] 9.4 编译产物过 Flowable 自带 `ProcessValidator` 二次校验，并把
      `APPROVAL` 节点字段映射为 `NodeAssigneeRuleDraft` 列表返回。
- [x] 9.5 单元测试：至少覆盖"单人串行两级""两级含一个会签节点"
      "含条件分支"三种典型 DSL 的编译结果与结构校验失败场景。

## 10. 流程模型生命周期后端接口

- [x] 10.1 复用第一批已建好的 `cn.nihility.rbac.workflow.entity.ProcessModelEntity`/
      `mapper.ProcessModelMapper`（对应 `tab_wf_process_model`），不
      在 `designer` 包下重复建一套。
- [x] 10.2 新建 `designer.service.WorkflowProcessModelService`：
      草稿保存、发布（编译 + 部署 + 落库新版本行 + 更新
      `current_definition_id`）、下线（挂起对应
      `flowable_definition_id`）、重新启用、版本历史查询。
- [x] 10.3 新建 `designer.controller.WorkflowProcessModelController`：
      `PUT .../draft`、`POST .../publish`、`POST .../disable`、
      `POST .../enable`、`GET .../versions`，补充
      springdoc-openapi 注解。
- [x] 10.4 接口权限校验：`view`/`edit`/`publish`/`disable` 四个操作
      分别对应 `WorkflowDesign:model:view/edit/publish/disable`
      权限点（design.md Decision 11）；本项目权限门控统一由
      `IdentityAuthFilter` 依据请求头 `menu` 编码校验，Controller 层
      不重复声明权限注解，与既有 `ApprovalRequestController` 等模块
      的约定一致。
- [x] 10.5 集成测试：草稿保存不触发部署；发布生成新版本且不影响旧
      版本运行中实例（数据库/Flowable 部署层面验证）；下线拒绝新
      发起（`disable` 后 `WorkflowService.start` 因
      `ProcessModelStatus.PUBLISHED` 校验不通过而拒绝）但不影响运行
      中实例；仅有编辑权限的用户调用发布接口被拒绝；存在孤立节点/
      条件分支缺默认边时发布被拒绝（复用 9.5 编译器测试覆盖）。

## 11. 前端可视化流程设计器

- [x] 11.1 与用户确认并引入 `@vue-flow/core`（及按需的
      `@vue-flow/controls`/`@vue-flow/background`）npm 依赖
      （design.md Open Question 5）：实际安装
      `@vue-flow/core@1.48.2`、`@vue-flow/controls@1.1.3`、
      `@vue-flow/background@1.3.2`。
- [x] 11.2 新建 `api/workflow.ts`：草稿保存/发布/下线/启用/版本历史
      五个接口按 `WorkflowProcessModelController` 真实路径封装；待办/
      已办查询（`WorkflowTaskController`）未纳入本次范围（未见明确的
      设计器相关待办 UI 需求，按任务说明从简）。列表/详情/新建三个接口
      后端尚未提供（见下方说明与文件内注释），按本项目 REST 约定占位
      声明，调用方均有容错处理。
- [x] 11.3 新建 `types/workflow.ts`：DSL 节点/边 discriminated union 类型，
      字段与后端 `ProcessModelDsl`/`ApprovalNodeDsl`/`EdgeDsl`/
      `EdgeConditionDsl` 逐字段对齐；枚举选项常量对齐
      `AssigneeType`/`ApprovalMode`/`EmptyAssigneeStrategy`。
- [x] 11.4 新建 `stores/workflowDesigner.ts`（Composition API
      `defineStore`）：维护画布 `nodes`/`edges`、选中节点/边、脏标记，
      提供 `toDsl()`/`fromDsl()` 双向转换（`fromDsl` 内含 BFS 分层
      auto-layout，因为 DSL 本身不携带节点坐标）。
- [x] 11.5 新建 `views/workflow/designer/ProcessDesignerView.vue`
      （Vue Flow 画布，含拖拽节点面板与工具栏）与 `nodes/` 下
      `StartNode.vue`/`ApprovalNode.vue`/`ConditionNode.vue`/
      `EndNode.vue` 四个自定义节点组件。
- [x] 11.6 新建 `views/workflow/designer/panels/NodePropertyPanel.vue`：
      按节点类型渲染 Element Plus 表单，`APPROVAL` 节点表单字段对应
      `tab_wf_node_assignee_rule` 各列，`CONDITION` 节点表单支持添加/
      删除分支并标记默认兜底分支；新增 `utils/workflowValidation.ts`
      复刻后端 `ProcessModelDslValidator` 的全部校验规则，保存草稿时
      提示但不阻塞、发布前强制拦截。
- [x] 11.7 新建 `views/workflow/process-model/ProcessModelListView.vue`
      与 `VersionHistoryDialog.vue`：进入设计器编辑、发布/下线/启用
      操作按钮（按 `status` 控制可见性）、版本历史只读查看（JSON 快照）。
      **已知偏离**：后端 `WorkflowProcessModelController` 目前只有
      draft/publish/disable/enable/versions 五个依赖已知 id 的接口，
      没有"分页列出全部流程模型"接口，`listProcessModels()` 会失败；
      列表页对此做了优雅降级（`el-alert` 提示 + 手动输入流程模型 id
      操作的兜底入口），已用真实数据库确认预置的 `MASTER_DATA_APPROVAL`
      模型 id 为 1，可通过该入口完整走一遍设计器/发布/下线/启用/版本
      历史。后端补齐列表接口后，只需让 `listUnavailable` 分支不再触发
      即可，不需要改动其余代码。
- [x] 11.8 路由 meta 与菜单项按 `WorkflowDesign:model:view` 门控
      （`router/menu.ts` 新增"流程设计"一级分组）；设计器页面内"保存
      草稿"/"发布"按钮分别按 `WorkflowDesign:model:edit`/`publish`
      门控，列表页与设计器页的"下线"/"启用"按钮按
      `WorkflowDesign:model:disable` 门控，四个权限点独立生效。
- [ ] 11.9 浏览器实测：**未能完整达成，部分验证**。已完成：①
      `npm run build`（vue-tsc 类型检查 + vite build）全绿；② 启动
      本地后端（`./gradlew bootRun`，连接远程 MySQL 与 Flowable 均正常
      启动，随后已停止该进程）；③ 用只读 SQL 确认预置流程模型
      `MASTER_DATA_APPROVAL` 的真实 id=1、`status=PUBLISHED`、
      `current_definition_id=1`，验证了列表页兜底入口指向的数据确实
      存在；④ 用 curl 复现前端 RSA-OAEP（SHA-256）登录流程确认
      `/api/auth/login` 接口本身工作正常（返回业务错误而非加密/协议
      错误）。**未完成**：没有可用的登录凭据（`admin/admin123` 在这个
      连接的远程数据库里不是有效账号，未继续在共享数据库上试探/重置
      凭据），因此没有拿到 `identity-token` 走通"创建草稿 → 保存 → 发布
      → 下线"的完整鉴权后接口联调；同时本次任务环境不含浏览器自动化
      工具，没有做画布拖拽/属性面板交互的真实浏览器可视化验证，仅通过
      代码走查 + 类型检查确认组件 props/emit 类型正确、Vue Flow 相关
      导入不报错。建议用户在有可用登录凭据的环境下按 `npm run dev` +
      `./gradlew bootRun` 实际跑一遍并反馈问题。

## 12. 权限资源与文档同步

- [x] 12.1 在 `权限资源.txt` 新增 `WorkflowDesign` 模块及
      `model:view`/`model:edit`/`model:publish`/`model:disable`
      四个权限点（随批次 C 一并完成）。**后续补漏**：当时只更新了
      文档，漏了同步登记 `tab_menu`/`tab_permission` 种子数据（对照
      `V2__create_chat_tables.sql` "聊天"模块的登记方式），导致这
      四个权限点在数据库里从未真实存在、任何角色（含 SUPER_ADMIN）
      都无法被授予，前端侧边栏"流程设计"一级菜单因此对所有用户都
      不可见；已用新增的
      `db/migration/V10__add_workflow_design_menu_permissions.sql`
      补齐菜单/权限点登记并为 SUPER_ADMIN 角色补授。
- [x] 12.2 确认转办/委派/加签/退回复用
      `ApprovalManagement:request:approve`（design.md Open
      Question 3 落地决策已生效，越权判定落在
      `WorkflowService`/`TaskAuthorizationService` 内部，未新增细分
      权限点，`权限资源.txt` 无需为此追加内容）。
- [x] 12.3 编码完成后运行 `openspec-doc-sync` 对齐
      `tasks.md`/`design.md`/`proposal.md` 与实际实现结果。

## 13. 测试

- [x] 13.1 单元测试：各 `AssigneeResolver` 实现（含空审批人、自审、
      向上级部门查找场景），随批次 A 完成
      （`NodeAssigneeResolutionServiceTest`/各 `*AssigneeResolverTest`）。
- [x] 13.2 单元测试：`WithdrawPolicy`、幂等服务、越权校验逻辑，随批次 A
      完成（`BeforeFirstApprovalWithdrawPolicyTest`/
      `IdempotencyServiceImplTest`/`TaskAuthorizationServiceTest`）。
- [x] 13.3 集成测试：两级默认流程的提交 → 第一级通过 → 第二级通过 →
      业务记录创建全链路（已随批次 B 的 `ApprovalRequestServiceImplTest`/
      `RbacApplicationTests` 覆盖）；第一级拒绝直接终止（已覆盖）；
      会签 `AND`/`OR`/`PERCENT` 三种模式（含任一驳回立即终止）的端到端
      集成测试见 `MultiInstanceApprovalIntegrationTest`（专用测试 BPMN
      夹具 `test-multi-instance-{and,or,percent}.bpmn20.xml`）；
      转办/委派/加签/退回对着真实 Flowable 引擎的集成测试见
      `TaskOperationsIntegrationTest`（含"目标节点不允许转办/退回时被
      拒绝"的负向场景，转办/委派/退回用 `test-transfer-delegate-return.bpmn20.xml`
      三级串行流程，加签用会签节点 `test-multi-instance-and.bpmn20.xml`）。
      本轮另修复了一处曾导致上述测试需要手工绕开的生产缺陷：
      `FlowableWorkflowService.start()` 内 `startProcessInstanceById(...)`
      同步执行到第一个用户任务创建时，`WorkflowAssigneeTaskListener`/
      `WorkflowMultiInstanceTaskListener`/`WorkflowMultiInstanceExecutionListener`
      原先按 `flowable_instance_id` 反查 `tab_wf_process_instance`（该列要到
      `start()` 方法最后一步才回填，第一个节点创建时仍是 `NULL`，命中不到），
      导致流程**第一个节点**的 `tab_wf_approval_task.process_instance_id`
      被错误写成 `NULL`，对该任务调用 approve/reject/transfer/delegate/
      returnTask/addSign 会抛 `BusinessException("流程实例不存在")`；三个
      监听器已改为优先按 Flowable businessKey（`start()` 发起流程时已把
      `tab_wf_process_instance.id` 作为 businessKey 传入，从一开始就可用）
      反查，查不到再回退按 `flowable_instance_id`，回归测试见
      `FirstNodeInstanceLinkageBugTest`（已移除 `@Disabled`，测试基类
      `AbstractWorkflowEngineIntegrationTest` 中原先用于绕开该缺陷的
      `startAndFixFirstNodeLinkage(...)` 事后打补丁方法已删除，各测试类
      改为直接调用 `workflowService.start(...)`）。修复过程中还额外发现并
      修复了一处配套缺陷：`WorkflowSpringContext`（供反射实例化的
      `TaskListener`/`ExecutionListener` 获取 Spring Bean 的静态桥接类）
      原先把 `ApplicationContext` 缓存进 JVM 级静态字段，只在每次 Spring
      容器刷新时被覆盖一次；全量测试套件里存在多个不同配置的
      `@SpringBootTest` 容器时，该静态字段会停留在"最后一次被刷新的容器"，
      导致监听器经由它取到的 Mapper Bean 绑定到错误容器自己的数据源连接，
      看不见同一个 Spring 事务里刚插入但尚未提交的数据；已改为
      `getBean(Class)` 优先通过 Flowable 当前命令上下文
      （`Context.getProcessEngineConfiguration()`）取"当前正在执行的这次调用
      所属"的 `SpringProcessEngineConfiguration.getApplicationContext()`，
      仅命令上下文不可用时才回退静态字段。以上两处修复已通过
      `./gradlew test --tests "cn.nihility.rbac.workflow.*"` 反复重跑验证
      100% 稳定通过，`./gradlew build`（全量约 1186 个测试）整体
      BUILD SUCCESSFUL；全量测试运行期间还遇到两个**与本次 change 代码
      无关的环境/基础设施问题**（不计入本次未完成项）：①共享开发数据库
      `10.10.88.31/rbac` 的 Flowable 引擎自身 `act_*`/`flw_*` 表一度整体
      缺失（经确认非本次测试导致，征得用户同意后用仓库自带的一次性建表
      脚本 `backend/src/main/resources/db/flowable/flowable.mysql.create.7.2.0.sql`
      恢复）；②全量套件里偶发 `SQLSyntaxErrorException: Unknown column
      'processInstanceId'`，定位为 MyBatis-Plus `TableInfoHelper` 的类级别
      全局静态缓存（`TABLE_INFO_CACHE` 按 `Class` 而非按容器缓存）在"JVM 内
      多个 Spring 容器共存"场景下的已知通病，与①同属该类问题但命中的是
      MyBatis-Plus 自身内部缓存，非本次新增代码引入、也非 workflow 模块
      专属，超出本次 change 范围，留作后续技术债（需要动
      `build.gradle` 测试任务隔离策略或更大范围的架构调整）。
- [x] 13.4 回归测试：确认四个业务模块审批开关关闭时的直接生效路径不受
      影响（`ApprovalSwitchServiceImplTest` 等既有测试未改动且随
      `./gradlew build` 全量通过，行为未受影响）。
- [ ] 13.5 设计器端到端测试：前端设计器（第 11 节）已落地，但见 11.9
      的详细说明——受限于缺少可用登录凭据与浏览器自动化工具，仅完成了
      构建产物校验、后端接口/数据层核实，未完成真正的鉴权后端到端
      操作序列，仍需要在有可用凭据的环境下补做。
