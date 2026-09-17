## Why

复现路径：把某个业务类型（本次报告为 USER，实际对 ORG/POSITION/APP 四类都成立）绑定的审批流程设计为
`开始 → 条件节点（只有一条默认兜底分支，没有配置任何显式条件分支）→ 结束`，即整条路径上不存在任何审批
（用户任务）节点。提交一条新增用户申请时，接口正常返回"已提交，走审批"（`WriteOperationResultVO.pending`），
但实际上：`tab_user` 没有新增记录（业务数据没入库）；`tab_wf_approval_task` 没有任何任务记录（审批中心/
待办列表看不到这条申请，谁也没法审批）；`tab_wf_process_instance.status` 永远停留在 `RUNNING`，
`tab_approval_request.status` 永远停留在"待审批"——申请进入一个既不报错、也永远无法被处理的死角。

根因（已通过只读代码走查确认，见下方 Impact 与 design.md Context）：

1. `WorkflowAssigneeTaskListener` 只挂在编译产物的 `userTask` 元素上；这条流程路径上没有任何审批节点，
   Flowable 因此在 `runtimeService.startProcessInstanceById(...)` 这一次同步调用内就直接跑完整个流程
   （网关的默认分支被正确选中并推进到结束事件），过程中从未创建任何用户任务，该监听器从未被触发。
2. `FlowableWorkflowService.start()`（`backend/src/main/java/cn/nihility/rbac/workflow/engine/flowable/
   FlowableWorkflowService.java`）在 `startProcessInstanceById` 返回后，从未调用同类内已有的
   `finalizeInstanceIfEnded(...)`（`approve()`/`reject()`/会签计票完成路径末尾都会调用这个方法，
   用来在流程实际跑完时把 `tab_wf_process_instance.status` 从 `RUNNING` 收尾为 `APPROVED`/`REJECTED`）。
   结果：一次"发起即跑完"的流程实例被永远遗忘在 `RUNNING` 状态。
3. `ApprovalRequestServiceImpl.submit()`（`backend/src/main/java/cn/nihility/rbac/approval/service/impl/
   ApprovalRequestServiceImpl.java`）在调用 `approvalProcessService.start(...)` 之后，只处理了"流程仍在
   运行、可能已经生成开放任务"这一种结果（`findOpenTask` 查到就记录 `flowableTaskId`，查不到也照常把申请
   存成"待审批"返回）；完全没有处理"流程在 `start()` 内部就已经跑到终态"这种结果。而
   `ApprovalRequestServiceImpl.finalizeApproval()`——仓库里唯一会调用
   `masterDataOperationExecutor.executeWrite(...)`（也就是真正创建 `tab_user` 记录）的地方——只能从
   `approve(Long id, String opinion)` 这条"人工审批"入口触发；没有任何开放任务，`approve()` 永远够不到，
   `finalizeApproval()` 因此永远不会被调用，新用户数据永远停留在 `tab_approval_request.request_payload`
   这个 JSON 快照里，出不来。

`open/api/sync/pull` 等其它模块不受影响；本次问题严格限定在审批提交→流程发起这一段。

## What Changes

- `FlowableWorkflowService.start()` 在流程实例真正启动后，补上一次
  `finalizeInstanceIfEnded(instance.getId())` 调用——与 `approve()`/`reject()`/会签计票完成路径保持一致的
  收尾方式。该方法内部已有"仅当 Flowable 判定该实例已无剩余运行中执行时才收尾"的幂等判断，正常需要等待
  人工审批的流程（还有开放任务）不受影响，只有"整条命中路径上不存在任何等待状态"的流程会被立即收尾为
  `APPROVED`/`REJECTED`。
- `ApprovalRequestServiceImpl.submit()` 在拿到 `approvalProcessService.start(...)` 的结果后，补上对
  "流程已经在 `start()` 内部同步跑到终态"这一分支的处理，与既有 `approve(Long id, String opinion)` 里
  "`RUNNING`/`APPROVED`/`TERMINATED`"三路分支处理的写法保持一致：
  - 终态为 `APPROVED`：复用 `finalizeApproval()` 已有的业务写入逻辑（校验管辖范围、调用该 `bizType`
    既有的创建/更新方法、回填 `resultTargetId`），标记申请为"已通过"，审批人记为空（无人工审批人），
    意见文案固定说明"该流程未配置需要人工处理的审批节点，按默认分支自动通过"，随后释放本次提交刚获取的
    业务活动锁。
  - 终态为 `REJECTED`（例如默认分支直接流转到 `outcome=REJECTED` 的结束节点）：标记申请为"已拒绝"，同样
    固定文案说明原因，不执行任何业务写入，释放业务活动锁。
  - 终态为 `RUNNING`：行为完全不变（沿用现有的开放任务查找逻辑）。
- 修正 `submit()` 最终构造响应 VO 前读取的 `entity` 必须反映上述分支实际写库后的最新状态（现有实现里
  多处终态收尾都是通过 `LambdaUpdateWrapper` 做局部字段更新，不会同步刷新方法里已经在内存中的 `entity`
  对象），避免"数据库里已经是已通过/已拒绝，接口返回的 VO 却还显示待审批"这一新的不一致。
- 不新增数据库表或字段，不改动 Flyway 迁移；纯应用层逻辑修复。
- 不限制"流程发布时必须至少包含一个审批节点"——现状的建模灵活性（允许某些分支完全不需要人工审批，直接
  自动通过/自动拒绝）本身是合理的，问题在于运行时没有正确处理这种"零等待"的完成结果，而不在于允许了这
  种建模方式。design.md 会记录一个被否决的备选方案（改为在发布校验阶段禁止零审批节点流程）供后续参考。

## Capabilities

### Modified Capabilities

- `master-data-approval-workflow`：明确"提交审批申请"需求中，命中路径上不存在任何审批节点、流程在
  `start()` 内部同步跑到终态时的行为——系统 SHALL 立即完成该终态对应的收尾（业务写入/拒绝标记），而不是
  把申请永远留在"待审批"状态且没有任何可操作任务。

## Impact

- **后端**：`cn.nihility.rbac.workflow.engine.flowable.FlowableWorkflowService`（`start` 方法）、
  `cn.nihility.rbac.approval.service.impl.ApprovalRequestServiceImpl`（`submit` 方法，新增一到两个私有
  收尾辅助方法，复用/适配现有 `finalizeApproval`）。影响面覆盖 ORG/USER/POSITION/APP 四类业务的提交入口
  （`ApprovalRequestServiceImpl.submit` 是四类业务共用的同一段代码），不是 USER 专属问题。
- **数据库**：无迁移，不新增表/字段。
- **前端**：无需改动——响应结构（`WriteOperationResultVO`）不变，只是修复后 `approvalRequest.status`
  会正确反映"已通过/已拒绝"而不是永远卡在"待审批"；若前端此前针对"待审批"状态做了轮询或提示，修复后能
  正常收到终态。
- **兼容性**：修复前已经卡死在 `RUNNING`/待审批状态的历史"僵尸"申请不在本次自动修复范围内（它们的
  Flowable 流程实例已经在历史 `startProcessInstanceById` 调用时就跑完并从 Flowable 运行时表消失，
  `finalizeInstanceIfEnded` 依赖查询 Flowable 运行时表判断"是否已无剩余执行"，对这些历史实例同样成立、
  理论上可以用一次性运维脚本补跑收尾，但本次改动范围只覆盖"修复上线后的新提交"，历史数据修复留给用户
  确认是否需要单独处理）。
- 本 change 不涉及 `openspec/changes/archive/2026-09-06-production-approval-lifecycle` 里"已识别、未实施"
  的 `RELIABLE_ASYNC`/POSITION/APP 执行适配器等范围，两者互不冲突。
