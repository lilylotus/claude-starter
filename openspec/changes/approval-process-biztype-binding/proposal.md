## Why

`workflow-approval-engine` change 落地了一个通用的、可为任意业务绘制/发布多套流程的 Flowable 工作流引擎（`WorkflowService`/流程设计器），但组织（ORG）/用户（USER）/任职（POSITION）/应用（APP）四类业务实际提交审批申请时，`ApprovalProcessServiceImpl` 把流程编码写死为常量
`WorkflowConstants.MASTER_DATA_APPROVAL_PROCESS_CODE`，完全没有使用调用方传入的 `bizType` 做区分。结果是：无论在流程设计器里为四类业务分别新建、绘制、发布了多少个流程模型，实际发起审批时永远只会走同一个预置流程，新建的流程模型没有任何实际生效入口；配套的"审批设置"（`tab_approval_switch`）也只是一个纯布尔开关，没有"该 bizType 绑定哪个已发布流程模型"这一环，管理员在界面上根本无法把已发布的流程指定给某类业务。这条断链使得"分业务类型设计审批流程"这个诉求在当前系统里名不副实，必须补齐。

## What Changes

- `tab_approval_switch` 表新增 `process_code` 列（可空），记录该 `bizType` 当前绑定的流程模型编码（对应 `tab_wf_process_model.process_code`）。
- 新增/修改"审批设置"查询与修改接口：查询时返回每个 `bizType` 当前绑定的 `processCode`（连同已有的 `enabled` 状态）；修改接口在开启审批开关或更换绑定流程时，校验目标 `processCode` 对应的流程模型必须存在且处于 `PUBLISHED` 状态，未绑定或绑定流程已下线时按明确的兜底规则处理（见 design.md）。
- `ApprovalProcessServiceImpl.start()` 改为按提交申请的 `bizType` 动态解析出该业务类型当前绑定的 `processCode` 后再调用 `WorkflowService.start()`，移除写死的 `PROCESS_CODE` 常量。
- 前端"审批设置"页面（`ApprovalSettingsView.vue`）在每个 `bizType` 的开关旁新增"绑定流程模型"下拉选择，数据源为 `GET /api/workflow/process-models` 中 `status=PUBLISHED` 的流程模型列表；未绑定流程时的开关行为与提示在 UI 上明确区分于"已绑定但流程被下线"的场景。
- 数据库迁移：为 ORG/USER/POSITION/APP 四条既有 `tab_approval_switch` 记录设置初始 `process_code`（沿用现有预置流程 `MASTER_DATA_APPROVAL`，保持迁移后行为与迁移前一致，避免存量环境因本次改动导致审批全部失效）。

## Capabilities

### New Capabilities

（无新增能力，本次是对既有能力的行为修改）

### Modified Capabilities

- `master-data-approval-workflow`：「审批开关」需求从"仅 `bizType`+`enabled` 布尔开关"扩展为"`bizType`+`enabled`+绑定的流程模型 `processCode`"；「提交时按开关状态分流为审批或直接生效」需求中"启动 Flowable 流程实例"的流程编码来源从固定常量改为按 `bizType` 查询到的绑定值。

## Impact

- 后端：`cn.nihility.rbac.approval`（`ApprovalSwitchEntity`/`ApprovalSwitchServiceImpl`/`ApprovalProcessServiceImpl`/对应 Controller、DTO）、`db/migration` 新增一个 `V11__*.sql`（表结构变更 + 存量数据回填）。
- 前端：`views/approval/ApprovalSettingsView.vue`、`api/approval.ts`（或对应模块）、`types/` 下审批设置相关类型。
- 依赖：读取流程模型列表复用已有的 `GET /api/workflow/process-models` 接口（`workflow-approval-engine` change 已落地），不新增工作流引擎侧改动。
- 兼容性：非破坏性——迁移后默认绑定保持原有唯一流程，现有"待审批"申请与已发布流程不受影响；仅当管理员后续主动更换绑定或新建其他流程时才会走到新流程。
