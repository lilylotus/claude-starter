## Context

"审批历史"页面（`ApprovalHistoryView.vue`）数据来自通用审批引擎的已办任务查询 `GET /api/v1/workflow/tasks/done`，每行是一条 `ApprovalTaskVO`（工作流任务维度的记录，携带 `businessType`/`businessId`/`processInstanceId`/`nodeName`/`action`/`remark`/`finishedTime` 等），不是 `tab_approval_request` 表本身的行。`ApprovalTaskVO.businessId` 对主数据审批场景而言就是 `tab_approval_request.id`（`ProcessInstanceEntity.businessId` 与 `tab_approval_request.id` 的映射关系在 `master-data-approval-workflow` 能力里已确立），`processInstanceId` 就是 `tab_wf_process_instance.id`。

"我的申请"/"待我审批"两个页面已经有完整的申请详情弹窗 `ApprovalRequestDetailDialog.vue`，接收一个 `ApprovalRequestRow`（对应后端 `ApprovalRequestVO`）作为 prop，内部：
- 基本信息/申请内容两个分区直接读 prop 上的字段（`bizType`/`operationType`/`requestPayload`/`targetSnapshot`/`status`/`createByName`/...）；
- "审批流程"分区在 `processInstanceId` 非空时，自行调用 `GET /api/v1/workflow/process-instances/{processInstanceId}`（`workflowApi.getProcessInstanceDetail`）拉取流程图与审批轨迹。

"我的申请"/"待我审批"两个页面能直接把列表行传给这个弹窗，是因为它们的列表查询接口 `pageMine`/`pagePending` 返回的就是完整的 `ApprovalRequestVO`。"审批历史"没有这样一个接口——它的列表行是工作流任务视角的 `ApprovalTaskVO`，缺少 `requestPayload`/`targetSnapshot`/`operationType` 等详情弹窗渲染"基本信息"/"申请内容"两个分区所需的字段。

`GET /api/v1/workflow/process-instances/{processInstanceId}`（流程实例详情，驱动"审批流程"分区）已有明确的参与关系授权规则，实现在 `WorkflowTaskServiceImpl.requireViewer()`（私有方法）：申请人本人、审批轨迹中出现过的操作人/转办来源人（历史参与）、或当前任一开放任务的指定处理人/候选人之一，三者之一即可查看，否则拒绝。

## Goals / Non-Goals

**Goals:**
- "审批历史"页面新增"详情"入口，点击后展示与"我的申请"/"待我审批"完全一致的申请详情弹窗（含审批流程图）。
- 新增的单条详情查询接口，对"历史处理过该申请关联流程实例的人"这一类查看者（审批历史列表里的当前用户必然属于这一类）放行，不需要额外的业务管理权限点。
- 不重复实现详情弹窗——前端 100% 复用 `ApprovalRequestDetailDialog.vue`。

**Non-Goals:**
- 不改变"审批历史"列表本身的查询接口、字段、过滤方式（`GET /api/v1/workflow/tasks/done` 不变）。
- 不把"我的申请"/"待我审批"现有的列表查询接口改造成来源统一的单一详情接口——本次只新增一个单条查询接口，`pageMine`/`pagePending` 仍各自返回完整列表数据，不改动。
- 不在 `WorkflowTaskServiceImpl` 之外抽一个跨模块共享的"参与关系授权"组件——参与关系判定逻辑在新接口里按需复刻（见 Decision 2），不做更大范围的重构。

## Decisions

### Decision 1：新增 `GET /api/approval-requests/{id}`，复用既有 `toVO()` 转换

`ApprovalRequestController` 新增：
```java
@GetMapping("/api/approval-requests/{id}")
public Result<ApprovalRequestVO> detail(@PathVariable Long id) {
    return Result.success(approvalRequestService.getDetail(id, requireCurrentUserId()));
}
```
`ApprovalRequestService` 接口新增 `ApprovalRequestVO getDetail(Long id, Long viewerId);`，`ApprovalRequestServiceImpl` 实现：按 `id` 查 `ApprovalRequestEntity`（不存在则 404/`BusinessException`），做参与关系授权校验（见 Decision 2），通过后复用已有的私有 `toVO(entity, displayNames)` 方法构造返回值——`displayNames` 只需为这一条记录的 `createBy`/`approverId` 调用 `userDisplayService.resolveDisplayNames(...)`，不需要像分页查询那样批量收集一页的 id。

实现时发现 `ApprovalRequestController` 此前没有读取当前登录用户 id 的辅助方法（`pageMine`/`pagePending` 等既有接口的当前用户 id 是在别处解析的），因此新增了一个私有 `requireCurrentUserId()`（未登录时抛 `BusinessException("当前用户未登录")`），写法仿照 `WorkflowTaskController` 里的同名方法。

理由：`toVO()` 已经包含本次及此前几个 change 逐步补全的全部展示逻辑（`removeHiddenFields`、`enrichPositionOrgNames`、`targetSnapshot` 填充范围），单条查询复用它，保证"审批历史"点进去看到的详情内容和"我的申请"/"待我审批"里看到的完全一致，不会出现两套逻辑逐渐漂移。

### Decision 2：参与关系授权在 `ApprovalRequestServiceImpl` 内按需复刻，不抽共享组件

`WorkflowTaskServiceImpl.requireViewer()` 是 `workflow` 模块内的私有方法，无法跨包直接复用。新接口所在的 `ApprovalRequestServiceImpl`（`approval` 模块）恰好已经注入了同样的三个数据访问依赖（`processInstanceMapper`、`approvalRecordMapper`、`approvalTaskMapper`）与 `taskAuthorizationService`，因此按相同的判定口径在本类内新增一个私有方法（如 `requireViewer(ApprovalRequestEntity entity, Long viewerId)`）复刻同一套三段判定：

1. `processInstanceId` 非空：查 `ProcessInstanceEntity`，按"申请人（`applicantId`）本人 / 该实例审批轨迹中出现过的 `operatorId` 或 `fromUserId` / 当前开放任务（`ApprovalTaskEntity` 状态为 `PENDING`/`CLAIMED`）的指定处理人或候选人"三选一放行，逻辑与 `WorkflowTaskServiceImpl.requireViewer()` 一致。
2. `processInstanceId` 为空（未走 Flowable 的简单审批场景）：退化为"`entity.getCreateBy()` 等于当前查看者，或 `entity.getApproverId()` 等于当前查看者"放行，其余拒绝——没有流程实例可供参与关系判定时的最小兜底规则，覆盖"审批历史"触发不到、但接口本身仍需自洽的边界情况。
3. 都不满足时抛 `BusinessException("无权限查看该申请详情")`。

理由：这与项目里已有的先例一致（`WorkflowModelCompilerImplTest`/`ProcessModelDslValidator` 相关代码注释里也提到过"该方法为包内可见无法跨包直接复用，此处按相同约定复刻"）——两处判定逻辑都不大（二十行左右），跨模块抽一个共享服务换来的收益（避免一次小复制）不足以抵消额外的抽象/依赖成本；两处各自独立演进也更安全，不会因为一处改动意外影响另一处的语义。

理由补充："审批历史"页面能触达的每一条记录，当前登录用户必然在该记录关联流程实例的审批轨迹里留下过 `operatorId`（历史处理过），天然满足第 1 类判定——因此从"审批历史"点进来的详情查看，实际上不会撞见"无权限"的情况；新增的授权判定主要是保证这个新接口本身作为一个通用的"按 id 查详情"接口是自洽、不越权的，为将来其它入口（如果有）复用打基础。

### Decision 3：前端按需拉取，不在列表行里预取详情

`ApprovalHistoryView.vue` 新增"操作"列，"详情"按钮点击时才调用 `approvalApi.getApprovalRequestDetail(row.businessId)`，拉取成功后把结果赋给一个 `detailRow` ref 并打开 `ApprovalRequestDetailDialog`（与"我的申请"/"待我审批"两个页面已有的 `openDetail()` 模式一致，只是多一步异步请求）；按钮增加 loading 态，避免用户重复点击触发多次请求。`row.businessId` 为空（理论上不应发生，防御性处理）时提示"该记录暂不支持查看详情"，不发起请求。

理由：审批历史列表条数可能较多，逐条预取详情既浪费请求也没有必要——用户通常只会点开少数几条自己关心的记录。

### Decision 4："审批历史"列表补充"操作类型"列，"节点名称"改名"审批节点"

`ApprovalTaskVO`（"我的待办/已办"查询结果，`cn.nihility.rbac.workflow.dto`）目前没有操作类型字段——它是工作流引擎的任务视角对象，不知道业务侧"这是一次新增/编辑/启用/停用/删除操作"。`ApprovalTaskMapper.xml` 的 `selectDonePage` 已经 `INNER JOIN tab_wf_process_instance p ON p.id = t.process_instance_id`，新增一个 `LEFT JOIN tab_approval_request ar ON ar.id = p.business_id AND ar.biz_type = p.business_type`（用 `LEFT JOIN` 而不是 `INNER JOIN`：万一将来出现不对应 `tab_approval_request` 记录的其它工作流业务对象类型，不应该让这类记录直接从已办列表里消失），`SELECT` 列表加一列 `ar.operation_type AS operationType`，`ApprovalTaskVO` 加一个同名字段。`selectDonePageCount` 不需要同步加这个 JOIN——它只影响 `SELECT` 列表新增一列，不引入新的过滤条件，不会改变命中行数，`selectDonePageCount` 现有注释里强调的"与 selectDonePage 保持完全一致的 JOIN/WHERE 结构"是为了防止过滤条件写岔导致 total 数对不上，这里没有新增过滤条件，不适用那条顾虑。

`selectTodoPage`（"我的待办"，`resultType` 是 `ApprovalTaskEntity` 而不是 `ApprovalTaskVO`）不涉及本次改动——待办查询结果类型本身没有 `operationType` 位置可放，且当前没有任何前端页面消费待办查询接口，不需要为它同步改造。

实现时发现：`WorkflowConvert.toTaskVO(ApprovalTaskEntity)`（"我的待办"专用的 MapStruct 转换方法，把 `ApprovalTaskEntity` 转成 `ApprovalTaskVO`）需要同步补一条 `@Mapping(target = "operationType", ignore = true)`——`ApprovalTaskVO` 新增字段后，`ApprovalTaskEntity` 上没有同名/可映射的源字段，MapStruct 在严格模式下会因为目标字段找不到映射来源而编译报错，写法与该接口里既有的 `action`/`remark` 两个"已办查询专用"字段的 `ignore = true` 处理方式一致（这两个字段同样只在 `selectDonePage` 的手写 SQL 结果里被填充，`toTaskVO` 转换出来的"我的待办"结果本就恒为空）。

前端 `ApprovalTaskVO`（`frontend/src/types/workflow.ts`）同步加 `operationType: string | null`；`ApprovalHistoryView.vue` 表格"节点名称"列标签改为"审批节点"（不改绑定字段，`nodeName` 字段本身不变），在它后面新增"操作类型"列，复用 `frontend/src/types/approval.ts` 里已有的 `APPROVAL_OPERATION_TYPE_OPTIONS` 做取值到展示文案的映射（与"我的申请"/"待我审批"两个列表的操作类型列用同一套映射，保持全站措辞一致）。

### Decision 5：已完成节点的审批记录改为内联展示，去掉点击弹窗交互

`ProcessFlowChart.vue`（"审批流程"分区）目前对 `status === 'COMPLETED'` 的节点采用"点击节点 → 弹出 `el-dialog` 展示 `node.records`（处理人/处理动作/处理时间/意见/转办信息）"的交互（`isClickable()`/`onNodeClick()`/`recordsDialogVisible` 等），节点卡片上只有一行"点击查看审批记录"提示文字。改为：去掉这一整套点击+弹窗机制，`COMPLETED` 节点的卡片上直接内联渲染 `node.records` 列表（与 `CURRENT` 节点已有的"内联展示处理人/候选审批人信息"是同一种交互模式，两种状态现在视觉上更一致）——每条记录展示处理人、处理动作（复用既有 `actionLabel()`/`APPROVAL_RECORD_ACTION_LABEL`）、处理时间、意见（有则展示）、转办来源→去向（有则展示），会签节点存在多条记录时逐条纵向列出。

数据来源不变：`ProcessGraphNodeVO.records` 已经随 `GET /api/v1/workflow/process-instances/{id}` 一次性返回，组件顶部注释里早就写明"数据已经在 `ProcessGraphNodeVO.records` 里，不需要额外请求"——这次只是把消费方式从"点击后按需展示"改成"直接展示"，不涉及接口改动。

理由：用户反馈点击查看这一步是多余的操作成本，已完成节点的处理人和意见是查看审批历史时最常需要立即看到的信息，不应该需要额外一次点击才能看到；节点卡片纵向排列、高度本来就会随内容自适应，内联展示不会破坏现有布局（`process-flow-chart__nodes` 用 `flex-wrap`，卡片本就各自独立伸缩高度）。

同步更新 `openspec/specs/master-data-approval-workflow/spec.md`"管理页面的审批入口"Requirement 里"详情展示当前节点与完整流程图"这条 Scenario 的措辞：把"已经过的节点标注为已完成并**可查看**对应的处理人、处理时间与意见"改为"已经过的节点标注为已完成并**直接展示**对应的处理人、处理时间与意见"，如实反映交互方式的变化。

### Decision 6："申请内容"过滤掉新旧值都为空的字段

`ApprovalRequestDetailDialog.vue` 的 `fieldRows` computed（约 170-187 行）目前对 `payload`/`snapshot` 里出现过的每一个 key 都生成一行，`displayValue()` 把 `undefined`/`null`/`''` 统一转成展示用的 `'-'`——但即使某个字段两侧都是 `'-'`（这条记录从未填写过这个可选字段），现在仍然会渲染成一行"字段名：- → -"，对用户没有信息量，纯粹占位。

在 `.map(...)` 之后追加一步 `.filter(...)`：`newValue !== '-' || (oldValue !== null && oldValue !== '-')`——CREATE 申请（`oldValue` 恒为 `null`）只看 `newValue` 是否为空；UPDATE 申请两侧只要有一侧非空就保留这一行。不改 `displayValue()` 本身（它的"空值统一转 `-`"职责对其它调用方——如任职记录的 `positionFieldRows()`——仍然适用，不能动），只在 `fieldRows` 这一层过滤已经生成的行。

理由：这是纯展示层的信息密度问题，不涉及数据语义——`requestPayload`/`targetSnapshot` 原样返回，只是详情弹窗不再为"两侧都没有值"的字段单独占一行。过滤后 `fieldRows.length === 0` 时既有的"暂无内容"兜底提示（296 行附近 `v-if="fieldRows.length === 0 && !userPositions"`）天然复用，不需要额外处理"全部字段都被过滤掉"这种边界情况。

## Risks / Trade-offs

- **[风险] 新接口成为除"审批历史"外的另一个"按 id 查任意申请详情"入口，如果将来被误用于越权场景** → 已有的三段参与关系判定与 `WorkflowTaskServiceImpl.requireViewer()` 同口径，且比 `pageMine`/`pagePending` 的既有访问控制更严格（那两个接口是"能看到列表里哪些行"，这个接口是"能不能看某一条的完整内容"），不新增攻击面。
- **[权衡] 参与关系判定逻辑在两个模块里各存一份** → 见 Decision 2 的理由；如果未来判定规则变化，需要记得同步改两处（已在两处的代码注释里互相标注一下，方便以后同步维护）。
- **[风险] `processInstanceId` 为空的历史遗留简单审批记录，`审批历史`列表本身其实并不会展示这类记录**（因为它们没有走 Flowable 引擎、不会出现在 `tab_wf_approval_task` 的已办任务查询里）**，Decision 2 里的兜底分支实际上是死代码路径** → 保留是为了让新接口本身在被其它入口复用时依然安全自洽，不依赖调用方只会传入"一定有流程实例"的 id；覆盖测试会包含这条兜底分支，明确其行为。

## Migration Plan

- 无数据库结构变更，无需 Flyway 迁移脚本。
- 纯新增接口 + 前端新增入口，不改变任何既有接口的请求/响应结构，不影响已有功能。

## Open Questions

（无）
