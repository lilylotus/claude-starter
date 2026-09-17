## Why

用户提出两个"待我审批"/"我的申请"页面的体验诉求：

1. 批准时无法填写审批意见/备注——只有拒绝有。
2. 审批详情看不到"当前流程走到哪一步、还剩哪些步骤、完整的审批流程图"。

已通过只读代码走查（不含代码修改）确认：

- **需求 1 后端已就绪，仅缺前端**：`/api/approval-requests/{id}/approve` 对应的
  `ApprovalOpinionRequest.opinion` 字段本来就是可选参数，一路贯通落库到
  `tab_approval_request.opinion` 与引擎侧 `tab_wf_approval_record.remark`
  （`ApprovalRequestServiceImpl.approve()`/`finalizeApproval()`）。前端
  `PendingApprovalRequestView.vue` 的 `handleApprove` 只弹了一个 `ElMessageBox.confirm`
  确认框，从未把意见传给 API；同一文件里"拒绝"按钮已经有一个带 `el-input type="textarea"`
  的意见弹窗（`rejectDialogVisible`/`rejectForm.opinion`），批准只需要照着做一个类似的、
  不强制必填的弹窗即可，接口不用改。
- **需求 2 数据大部分已存在，但从未接到这两个前端页面上**：
  - `GET /api/v1/workflow/process-instances/{id}` 已经返回完整审批轨迹
    `records`（`ApprovalRecordVO`：谁在哪个节点、什么时间、什么意见）与当前开放节点集合
    `openNodes`，但这个接口从未被 `frontend/src/api/`（`approval.ts`/`workflow.ts`）调用过，
    `ApprovalRequestVO`（审批申请视图对象）也从没把 `processInstanceId` 暴露给前端，前端
    根本不知道该拿哪个流程实例 id 去查。
  - 完整节点/连线图（"完整的审批流程图"这个诉求需要的数据）目前只存在于
    `ProcessDefinitionEntity.modelJsonSnapshot`（DSL 快照），唯一暴露它的接口
    `GET /api/workflow/process-models/{id}/versions` 挂在 `WorkflowDesign:model:view`
    这个**流程设计器管理员权限点**下，普通申请人/审批人没有这个权限，也用不了这个以
    `processModelId` 为 key 的接口（申请人/审批人手上只有 `processInstanceId`）。
  - 前端已经把 `@vue-flow/core`/`background`/`controls` 作为依赖引入，`views/workflow/
    designer/` 下已有可复用的节点渲染组件（`StartNode.vue`/`ApprovalNode.vue`/
    `ConditionNode.vue`/`EndNode.vue`），画流程图不需要引入新的图表库或从零写渲染逻辑。
- **额外发现一个需要在本次一并修复的授权缺口**：`GET /api/v1/workflow/process-instances/{id}`
  当前**完全没有做"当前用户是否有权查看这个流程实例"的校验**——`WorkflowTaskServiceImpl
  .getProcessDetail()` 直接按 id 查询并返回完整审批轨迹（含所有审批人的意见文本），
  `IdentityAuthFilter` 的固定权限映射表里也没有这条路径，落到"未命中映射表按 menu
  头原值校验"的兜底逻辑，也就是**任何登录用户只要构造出一个存在的 `processInstanceId`
  就能看到别人提交的申请全部审批意见**，与 `approval-runtime-safety` 能力已经写明的
  "详情与抄送数据 SHALL 按参与关系及字段权限返回"（`操作授权和访问控制` 需求）直接矛盾。
  这个接口目前之所以没暴露出风险，是因为前端从未调用它；本次要把它接到"待我审批"/
  "我的申请"详情弹窗上，必须先把这个校验补上，否则等于把一个潜在漏洞变成前端触手可及的
  真实漏洞。

## What Changes

### 需求 1：批准时可填写审批意见

- `PendingApprovalRequestView.vue` 新增批准确认弹窗（结构参照现有拒绝弹窗），意见为可选
  填写（不强制必填，与后端 `ApprovalOpinionRequest` 现有校验规则一致），提交时把意见传给
  `approvalApi.approveApprovalRequest(id, opinion)`（该函数已支持该参数）。
- 不改动后端。

### 需求 2：审批详情展示当前流程位置、剩余步骤与完整流程图

- **后端**：
  - `ApprovalRequestVO` 新增 `processInstanceId` 字段（`ApprovalRequestServiceImpl.toVO`
    补充赋值），供前端据此查询流程实例详情。
  - `WorkflowTaskServiceImpl.getProcessDetail(processInstanceId)` 补上参与关系校验：仅
    申请人本人、该实例审批轨迹中出现过的操作人/转办来源人、当前任一开放任务的指定处理人或
    候选人（复用既有 `TaskAuthorizationService.isAuthorized` 逐个开放任务校验），三者之一
    满足才允许查看；均不满足时拒绝并返回无权限错误。落实
    `approval-runtime-safety` 能力"操作授权和访问控制"需求里"详情...数据 SHALL 按参与
    关系...返回"这句既有约束。
  - `ProcessInstanceDetailVO` 新增只读的节点/连线图字段（新增
    `ProcessGraphNodeVO`/`ProcessGraphEdgeVO`，或等价结构）：按流程实例关联的
    `processDefinitionId` 读取 `ProcessDefinitionEntity.modelJsonSnapshot`（按
    `schemaVersion` 区分 v1/v2 反序列化，复用既有 DSL DTO，不新写解析器），转换为精简的
    只读节点列表（id/类型/名称/坐标）与连线列表（id/source/target/条件分支说明），每个
    节点附带服务端算好的状态（已完成/进行中/未到达）——已完成节点关联对应的
    `ApprovalRecordVO`（审批人、时间、意见），进行中节点对应 `openNodes`。
  - **范围收敛（design.md 有详细取舍）**：对于流程图上位于当前节点之后、经过条件分支的
    未到达部分，本次只按流程设计的完整拓扑展示"可能路径"，不去精确预测条件分支最终走哪
    一条（这需要重放流程发起时冻结的路由变量，属于更大的工作量，design.md 记录为后续增强
    方向，不在本次范围）；已经过的节点（含会签/并行的历史结果）展示是精确的，因为那部分
    数据直接来自已经发生的 `records`。
- **前端**：
  - 新增/扩展 API 封装（`frontend/src/api/workflow.ts` 或新文件）与类型
    （`frontend/src/types/`）承接上面两个后端改动。
  - `ApprovalRequestDetailDialog.vue` 新增"审批流程"区块：一段当前节点位置的文字摘要 +
    用 Vue Flow 只读渲染完整节点/连线图（复用设计器已有节点组件，按服务端返回的状态着色，
    已完成节点悬浮/点击可看到审批人与意见）。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `master-data-approval-workflow`：`审批申请查询` 需求补充 `processInstanceId`
  随查询结果返回；`管理页面的审批入口` 需求补充"批准操作可填写审批意见"与"审批详情展示
  当前流程节点、完整流程图与已发生的审批轨迹"。
- `approval-runtime-safety`：`操作授权和访问控制` 需求补充针对
  `GET /api/v1/workflow/process-instances/{id}` 的具体落地场景（参与关系校验），修复现状
  与既有需求文字不一致的问题。
- `workflow-approval-engine`：新增"流程实例详情查询含完整节点图"需求，明确
  `ProcessInstanceDetailVO` 携带只读节点/连线图及每个节点的状态。

## Impact

- **后端**：`cn.nihility.rbac.approval.dto.ApprovalRequestVO`、
  `cn.nihility.rbac.approval.service.impl.ApprovalRequestServiceImpl`（`toVO`）、
  `cn.nihility.rbac.workflow.service.impl.WorkflowTaskServiceImpl`（`getProcessDetail`
  新增授权校验与图数据组装）、`cn.nihility.rbac.workflow.dto.ProcessInstanceDetailVO`
  （新增字段）、新增 1-2 个只读图节点/连线 DTO。不新增数据库表/字段，不改 Flyway 迁移
  （`modelJsonSnapshot` 已经落库，只是新增一条读取+转换路径）。
- **前端**：`views/approval/pending/PendingApprovalRequestView.vue`（新增批准弹窗）、
  `components/ApprovalRequestDetailDialog.vue`（新增流程图区块）、`api/`、`types/` 新增
  少量文件/字段；不改现有页面已有功能的行为。
- **权限**：不新增 RBAC 权限点——两个页面本身已经分别受 `ApprovalManagement:request:view`/
  `ApprovalManagement:request:approve` 门控（见 `权限资源.txt`），新增的是"数据行级别的
  参与关系校验"，与页面级 RBAC 权限点是两回事，不需要在 `权限资源.txt` 登记新条目。
- **兼容性/安全**：`GET /api/v1/workflow/process-instances/{id}` 补上参与关系校验后，
  任何此前依赖"无校验、任意 id 可查"这一（未文档化、属于缺陷的）行为的调用方会开始收到
  无权限错误——目前确认没有任何前端代码调用这个接口，属于安全加固而非破坏性变更。
- 不涉及 `production-approval-lifecycle`/`fix-approval-zero-task-process-completion` 两个
  change 的范围，互不冲突。
