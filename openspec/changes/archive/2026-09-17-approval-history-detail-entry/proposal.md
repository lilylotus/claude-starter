## Why

"审批历史"页面目前只展示一条已处理记录的摘要（业务对象类型、节点名称、处理结果、处理意见、申请人、处理时间），没有查看完整申请详情或审批流程图的入口——这是 `master-data-approval-workflow` 能力"管理页面的审批入口"Requirement 里明确写下的既有限制（"该页面 SHALL NOT 提供跳转到完整申请详情或流程图的入口...深入查看留待后续 change"）。用户处理完一条申请后，如果想回顾这条申请当时完整的申请内容、新旧字段对照或完整审批轨迹，现在没有任何入口，只能凭列表里的几个摘要字段猜测。本 change 就是那个"后续 change"：给审批历史列表补上"详情"入口，复用"我的申请"/"待我审批"已经做好的申请详情弹窗（含分区展示、审批流程图、条件分支可读文案等能力）。

## What Changes

- "审批历史"列表新增"操作"列，提供"详情"按钮。
- 后端新增单条申请详情接口 `GET /api/approval-requests/{id}`，返回与"我的申请"/"待我审批"列表同构的 `ApprovalRequestVO`（含 `requestPayload`/`targetSnapshot`/审批对象名称等前端详情弹窗渲染所需的全部信息）。
- 该接口的查看权限 SHALL 复用"流程实例详情"接口（`GET /api/v1/workflow/process-instances/{processInstanceId}`）已有的参与关系判定口径：申请人本人、历史处理过该流程实例的人（含转办来源人）、或当前任一开放任务的指定处理人/候选人之一——三者之一即可查看，其余用户拒绝访问。"审批历史"里的每一条记录，当前登录用户必然是"历史处理过该流程实例的人"，天然满足这条权限规则。
- 申请未关联工作流实例（`processInstanceId` 为空，旧的 LEGACY_SYNC 简单审批场景）时，改为按"提交人本人或最终审批人本人"判定查看权限（没有流程实例可供参与关系判定时的兜底规则）。
- 前端"审批历史"页面点击"详情"后，按行数据里的 `businessId`（即 `tab_approval_request.id`）拉取详情，复用既有的 `ApprovalRequestDetailDialog.vue` 组件展示（基本信息/申请内容/审批流程三分区、审批流程图、条件分支可读文案等能力全部直接复用，不需要为审批历史单独做一套）。
- "审批历史"列表"节点名称"列改名为"审批节点"（与"审批流程"分区、其它页面的措辞保持一致，"节点"在这个上下文本身就是"审批节点"，不需要单独强调"名称"）；新增"操作类型"列，展示该记录关联申请的操作类型（新增/编辑/启用/停用/删除，与"我的申请"/"待我审批"列表用的同一套 `APPROVAL_OPERATION_TYPE_OPTIONS` 映射一致）。后端 `ApprovalTaskVO` 新增 `operationType` 字段，"已办"查询（`selectDonePage`）通过关联 `tab_approval_request` 取得。
- "审批历史"列表"申请人"列改为固定宽度并开启 Element Plus 内置的 `show-overflow-tooltip`：内容超出列宽时以省略号截断，鼠标悬停展示完整姓名，避免超长姓名把整行撑宽。纯样式调整，不涉及数据结构变化。
- 申请详情弹窗"审批流程"分区（`ProcessFlowChart.vue`）里已完成的审批节点，不再需要点击节点才弹出"审批记录"对话框查看处理人/处理意见——改为直接在节点卡片上内联展示该节点关联的全部审批记录（处理人、处理动作、处理时间、意见，多人会签时逐条列出）。数据来源不变（`ProcessGraphNodeVO.records`，随流程实例详情接口一次性返回），纯前端交互调整，不新增/修改任何接口。
- 申请详情弹窗"申请内容"分区（`ApprovalRequestDetailDialog.vue` 的 `fieldRows`）：新值与旧值都是空（都展示为 `-`）的字段不再展示，只展示至少一侧有值的字段——避免两侧都从未填写过的可选字段把"申请内容"/"变更内容"列表撑得很长，干扰真正有值、值得关注的字段。纯前端过滤逻辑，不改变后端返回的数据，不影响"两侧都有值但相同"（未实际变更）这类字段的展示——那类字段仍然展示，只过滤"两侧都为空"这一种情况。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `master-data-approval-workflow`：
  - Requirement "管理页面的审批入口"：把"'审批历史'页面 SHALL NOT 提供跳转到完整申请详情或流程图的入口"改为"SHALL 提供查看完整申请详情（含审批流程图）的入口，复用与'我的申请'/'待我审批'相同的详情展示与参与关系权限判定"。

## Impact

- 后端：新增 `GET /api/approval-requests/{id}` 接口（`ApprovalRequestController`/`ApprovalRequestService`/`ApprovalRequestServiceImpl`），复用 `ApprovalRequestServiceImpl` 已有的 `processInstanceMapper`/`approvalRecordMapper`/`approvalTaskMapper`/`taskAuthorizationService`/`userDisplayService` 依赖与既有的 `toVO()` 转换逻辑，不新增依赖；`ApprovalTaskVO`（`cn.nihility.rbac.workflow.dto`）新增 `operationType` 字段，`backend/src/main/resources/mybatis/mapper/ApprovalTaskMapper.xml` 的 `selectDonePage` 新增 `LEFT JOIN tab_approval_request` 取该字段，`WorkflowConvert.toTaskVO()`（"我的待办"专用转换方法）同步补 `@Mapping(target = "operationType", ignore = true)`。
- 前端：`frontend/src/views/approval/history/ApprovalHistoryView.vue`（新增操作列 + 详情弹窗接入 + "审批节点"改名 + "操作类型"列）、`frontend/src/api/approval.ts`（新增 `getApprovalRequestDetail()`）、`frontend/src/types/workflow.ts`（`ApprovalTaskVO` 新增 `operationType` 字段）；复用既有 `ApprovalRequestDetailDialog.vue`，不新建组件。
- 不影响："我的申请"/"待我审批"既有查询接口与数据结构（`ApprovalTaskVO` 是审批历史独立的类型，不影响那两个页面用的 `ApprovalRequestVO`）；"我的待办"（`selectTodoPage`）查询不受影响，`operationType` 只在已办查询里被填充，待办查询结果该字段恒为空（当前没有任何前端页面消费待办查询，属于可接受的不对称）。
