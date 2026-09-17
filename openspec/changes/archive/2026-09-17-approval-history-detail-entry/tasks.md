## 1. 后端：单条申请详情接口

- [x] 1.1 `backend/src/main/java/cn/nihility/rbac/approval/service/ApprovalRequestService.java`：新增接口方法 `ApprovalRequestVO getDetail(Long id, Long viewerId);`，补充方法注释说明参与关系授权口径。
- [x] 1.2 `backend/src/main/java/cn/nihility/rbac/approval/service/impl/ApprovalRequestServiceImpl.java`：
  - 实现 `getDetail(Long id, Long viewerId)`：按 `id` 查 `ApprovalRequestEntity`（不存在抛 `BusinessException`），调用新增的私有方法做参与关系授权校验，通过后用 `userDisplayService.resolveDisplayNames(...)` 解析这一条记录的 `createBy`/`approverId` 展示名，复用既有私有方法 `toVO(entity, displayNames)` 构造返回值。
  - 新增私有方法（如 `requireViewer(ApprovalRequestEntity entity, Long viewerId)`）：
    - `processInstanceId` 非空时，查 `ProcessInstanceEntity` + 该实例的 `ApprovalRecordEntity` 列表 + 当前开放的 `ApprovalTaskEntity` 列表（状态 `PENDING`/`CLAIMED`），按"申请人本人 / 审批轨迹 `operatorId`或`fromUserId` 命中 / 当前开放任务候选人命中（`taskAuthorizationService.isAuthorized`）"三选一放行，判定口径与 `WorkflowTaskServiceImpl.requireViewer()` 一致（可参考该方法实现，但不要跨包调用私有方法，本类内独立实现）。
    - `processInstanceId` 为空时，按"`entity.getCreateBy()` 等于当前查看者，或 `entity.getApproverId()` 等于当前查看者"放行。
    - 都不满足时抛 `BusinessException("无权限查看该申请详情")`。
- [x] 1.3 `backend/src/main/java/cn/nihility/rbac/approval/controller/ApprovalRequestController.java`：新增
  ```java
  @GetMapping("/api/approval-requests/{id}")
  public Result<ApprovalRequestVO> detail(@PathVariable Long id) {
      return Result.success(approvalRequestService.getDetail(id, requireCurrentUserId()));
  }
  ```
  补充 `@Operation`/`@Parameter` springdoc 注解，说明查看权限口径。

## 2. 后端：测试

- [x] 2.1 新增用例：申请人本人调用 `getDetail`/`GET /api/approval-requests/{id}` 能正常查看自己提交的申请详情。
- [x] 2.2 新增用例：历史处理过该申请关联流程实例的审批人（`ApprovalRecordEntity.operatorId` 命中）能正常查看，即使当前不再是任何开放任务的候选人。
- [x] 2.3 新增用例：当前所处审批节点的候选人（尚未处理，任务仍 `PENDING`/`CLAIMED`）能正常查看。
- [x] 2.4 新增用例：与申请无任何参与关系的用户调用接口被拒绝，返回无权限错误。
- [x] 2.5 新增用例：`processInstanceId` 为空的申请，提交人和审批人能查看，其余用户被拒绝。
- [x] 2.6 新增用例：查询一个不存在的 `id` 返回申请不存在的错误，不是无权限错误。
- [x] 2.7 新增用例：返回的 `ApprovalRequestVO` 内容（`requestPayload`/`targetSnapshot`/审批对象名称等）与 `pageMine`/`pagePending` 对同一条记录返回的内容一致（复用同一个 `toVO()`，行为应天然一致，用测试固化这个保证）。

## 3. 前端：API 与页面接入

- [x] 3.1 `frontend/src/api/approval.ts`：新增 `getApprovalRequestDetail(id: number): Promise<ApprovalRequestRow>`，对应 `GET /api/approval-requests/${id}`。
- [x] 3.2 `frontend/src/views/approval/history/ApprovalHistoryView.vue`：
  - 表格新增"操作"列，放"详情"按钮；
  - 新增 `detailVisible`/`detailRow`/`detailLoadingId` 等状态（`detailLoadingId` 记录当前正在拉取详情的那一行 `id`，而不是单一布尔值，使 loading 态精确落在被点击的那一行按钮上），点击"详情"时按 `row.businessId` 调用 `getApprovalRequestDetail`，成功后打开 `ApprovalRequestDetailDialog`；`row.businessId` 为空时提示"该记录暂不支持查看详情"，不发起请求；请求期间对应行按钮 loading，避免重复点击；
  - 引入并挂载 `<approval-request-detail-dialog v-model="detailVisible" :row="detailRow" />`（与"我的申请"/"待我审批"两个页面的既有用法一致）；
  - 更新文件顶部注释，去掉"仅本次不提供'查看详情'操作列（design.md Non-Goals）"这句过时描述。

## 4. 前端：本地验证

- [x] 4.1 `npm run build`（`vue-tsc` 类型检查 + vite build）确认无类型错误。
- [ ] 4.2 手动验证：处理一条审批申请（同意/拒绝）后，在"审批历史"页面找到该条记录，点击"详情"，核对展示内容与直接从"待我审批"/"我的申请"打开的详情一致，审批流程图正常展示。

## 5. OpenSpec 文档同步（第一批：详情入口）

- [x] 5.1 实现完成后，调用 `openspec-doc-sync` 按真实 diff/测试结果核对并更新本 change 的 `proposal.md`/`design.md`/`tasks.md`。
- [x] 5.2 已执行 `openspec-sync-specs` 把本 change 的 delta spec 应用到 `openspec/specs/master-data-approval-workflow/spec.md`（43 个 spec 全部校验通过）；归档仍待用户手动触发，不自动执行。

## 6. 后端：审批历史列表补充操作类型（追加需求）

- [x] 6.1 `backend/src/main/java/cn/nihility/rbac/workflow/dto/ApprovalTaskVO.java`：新增字段 `private String operationType;`（已办查询专用，"我的待办"查询结果恒为空，参照 `action`/`remark` 两个既有"已办查询专用"字段的注释风格补注释）。
- [x] 6.2 `backend/src/main/resources/mybatis/mapper/ApprovalTaskMapper.xml` 的 `selectDonePage`：新增 `LEFT JOIN tab_approval_request ar ON ar.id = p.business_id AND ar.biz_type = p.business_type`，`SELECT` 列表新增 `ar.operation_type AS operationType`。`selectDonePageCount` 不需要同步加这个 JOIN（只是新增查询列，不引入新的过滤条件，不影响总数）。

## 7. 后端：测试

- [x] 7.1 更新/新增 `WorkflowTaskServiceImplTest`（或相应集成测试）：`findDoneTasks` 返回结果里每条记录的 `operationType` 与其关联 `tab_approval_request.operation_type` 一致。
- [x] 7.2 新增用例：某条已办任务对应的流程实例 `business_id`/`business_type` 在 `tab_approval_request` 里找不到匹配记录时（`LEFT JOIN` 未命中），`operationType` 为空，查询本身不受影响、不报错、不影响该条记录的其它字段。

## 8. 前端：审批历史列表接入操作类型

- [x] 8.1 `frontend/src/types/workflow.ts` 的 `ApprovalTaskVO` 接口：新增 `operationType: string | null` 字段。
- [x] 8.2 `frontend/src/views/approval/history/ApprovalHistoryView.vue`：
  - `<el-table-column prop="nodeName" label="节点名称" ...>` 的 `label` 改为"审批节点"（`prop`/绑定字段不变）；
  - 在"业务对象类型"列之后（或"审批节点"列之前，视觉上贴近"业务对象类型"更合理）新增"操作类型"列，用 `APPROVAL_OPERATION_TYPE_OPTIONS`（从 `@/types/approval` import）按 `row.operationType` 匹配展示文案，匹配不到（含为空）时展示 `-`，写法参照 `MyApprovalRequestView.vue` 里"操作类型"列的既有实现。
- [x] 8.3 `npm run build` 确认无类型错误。

## 9. OpenSpec 文档同步（第二批：操作类型列）

- [x] 9.1 实现完成后，调用 `openspec-doc-sync` 核对并更新本 change 的 `proposal.md`/`design.md`/`tasks.md`。
- [x] 9.2 已执行 `openspec-sync-specs` 把本次追加的 delta spec 变更应用到 `openspec/specs/master-data-approval-workflow/spec.md`（43 个 spec 全部校验通过）；归档仍由用户手动触发。

## 10. 前端：申请人列超长省略（追加需求）

- [x] 10.1 `frontend/src/views/approval/history/ApprovalHistoryView.vue`："申请人"列 `min-width="100"` 改为 `width="120" show-overflow-tooltip`（Element Plus 内置能力：内容超出列宽时省略号截断，鼠标悬停展示完整内容，项目里 `OperationLogManagementView.vue`/`AppConfigView.vue` 等页面已有同款用法，不需要手写字符数截断逻辑）。
- [x] 10.2 `npm run build` 确认无类型错误。

## 11. 前端：已完成审批节点内联展示审批记录（追加需求）

- [x] 11.1 `frontend/src/components/processFlowChart/ProcessFlowChart.vue`：
  - 删除点击弹窗机制：`isClickable()`、`onNodeClick()`、`recordsDialogVisible`/`recordsDialogTitle`/`recordsDialogRecords` 三个 ref、模板里的 `<el-dialog v-model="recordsDialogVisible" ...>` 整块、节点卡片上 `@click="onNodeClick(node)"`、`:class="{ 'is-clickable': isClickable(node) }"`、`<span v-if="isClickable(node)" class="process-flow-chart__node-hint">点击查看审批记录</span>`；
  - 在节点卡片模板里新增 `v-if="node.status === 'COMPLETED'"` 分支（与现有 `v-if="node.status === 'CURRENT'"` 的审批人内联块并列），内联渲染 `node.records ?? []`：每条记录展示处理人（`operatorName || operatorId || '—'`）+ 处理动作标签（复用既有 `actionLabel()`）+ 处理时间，其后有 `remark` 则另起一行展示，有 `fromUserName`/`toUserName` 则另起一行展示"{fromUserName} → {toUserName}"；`records` 为空数组时展示"暂无处理记录"；多条记录纵向堆叠（会签节点场景）。可以直接复用原 `<el-dialog>` 里 `.process-flow-chart__records`/`.process-flow-chart__record*` 那套结构做内联版本，样式按卡片内嵌的尺寸适当收紧（字号/间距可以比原弹窗版本小一号，卡片宽度只有 200-280px）；
  - `isClickable(node)`/`.is-clickable` 样式类删除后，`.process-flow-chart__node` 的 `cursor: pointer`/`hover` 效果一并移除（改回默认 `cursor: default`，`COMPLETED` 节点不再是可点击元素）；
  - 更新文件顶部注释（10-13 行），去掉"COMPLETED...点击可查看该节点关联的审批轨迹"的描述，改为"COMPLETED（已完成）直接内联展示该节点关联的审批轨迹（谁、何时、什么意见），数据同样来自 `ProcessGraphNodeVO.records`，不需要额外请求"。
- [x] 11.2 `npm run build` 确认无类型错误。
- [ ] 11.3 手动验证：打开一条已经过至少一级审批（含至少一条 `remark`）的申请详情，"审批流程"分区里对应的已完成节点直接展示处理人和处理意见，不需要点击；节点不再有 hover 指针/点击态视觉反馈。

## 12. OpenSpec 文档同步（第三批：已完成节点内联展示）

- [x] 12.1 实现由我直接完成（单文件、纯前端、无新接口，未额外委派 doc-sync agent），已核对 `proposal.md`/`design.md`/`tasks.md` 与实际代码一致。
- [x] 12.2 已执行 `openspec-sync-specs` 把本次追加的 delta spec 变更应用到 `openspec/specs/master-data-approval-workflow/spec.md`（43 个 spec 全部校验通过）；归档仍由用户手动触发。

## 13. 前端：申请内容过滤空字段（追加需求）

- [x] 13.1 `frontend/src/components/ApprovalRequestDetailDialog.vue` 的 `fieldRows` computed（约 170-187 行）：在现有 `.map(...)` 之后追加 `.filter((item) => item.newValue !== '-' || (item.oldValue !== null && item.oldValue !== '-'))`，过滤掉新旧值都为空的字段行；不改动 `displayValue()` 本身（其它调用方如 `positionFieldRows()` 仍需要它把空值统一转成 `-`）。
- [x] 13.2 `npm run build` 确认无类型错误。
- [ ] 13.3 手动验证：打开一条只填了部分可选字段的 UPDATE 申请详情，"申请内容"分区里两侧都未填写的字段不再出现，有值的字段正常展示新旧对照；全部字段都为空的极端情况下正确落到既有的"暂无内容"提示。

## 14. OpenSpec 文档同步（第四批：申请内容过滤空字段）

- [x] 14.1 实现由我直接完成（单文件、纯前端过滤逻辑，未额外委派 doc-sync agent），已核对 `proposal.md`/`design.md`/`tasks.md` 与实际代码一致。
- [x] 14.2 本次改动是纯展示层过滤逻辑，不涉及 `openspec/specs/` 里任何已文档化的 Scenario（既有 Scenario 只约束接口返回的数据完整性，不约束前端渲染哪些字段），不需要执行 `openspec-sync-specs`。
