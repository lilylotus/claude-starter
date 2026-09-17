## Why

"我的申请"、"待我审批"列表与申请详情弹窗目前把"目标记录 id"（`targetId`，一个数据库自增主键）直接展示给用户，用户看到的是一个没有业务含义的数字，无法直观判断这条申请到底是针对哪个用户/组织/应用。系统里其实已经具备把这个 id 翻译成人类可读名称所需的信息（用户姓名、组织名称、应用名称），只是没有在这三处 UI 上使用。此外，当前"目标记录当前值"（`targetSnapshot`）只在 `operationType=UPDATE` 时由后端返回，ENABLE/DISABLE/DELETE 类型的申请完全没有名称信息可用，需要一并补齐。

同样的问题也出现在申请详情里嵌套展示的"任职信息"上：用户新增（或编辑时新增）任职记录随 `requestPayload.positions` 一起提交，其中"所属组织"只有 `orgId`，没有组织名称（`UserPositionRequest` 本身不携带 `orgName`），前端 `ApprovalRequestDetailDialog.vue` 目前只能在拿不到 `orgName` 时退化展示组织 id 原始数字，同样不友好，需要一并解决。

## What Changes

- "我的申请"列表、"待我审批"列表、申请详情弹窗三处 UI 的"目标记录 ID"列/字段统一改名为"审批对象"，展示人类可读的名称而非原始 id：
  - `bizType=USER` → 用户姓名
  - `bizType=ORG` → 组织名称
  - `bizType=APP` → 应用名称
  - `bizType=POSITION`（三处 UI 现有的业务对象类型之一，本次一并覆盖）→ "用户姓名 - 组织名称"
- CREATE 类型申请（`targetId` 为空）展示 `requestPayload` 中提交的名称；UPDATE/ENABLE/DISABLE/DELETE 类型展示目标记录当前的名称。
- 后端 `ApprovalRequestServiceImpl.toVO()` 补齐 ENABLE/DISABLE/DELETE 操作类型下的 `targetSnapshot` 填充（目前只有 UPDATE 会填充），使这三类操作也具备可供展示名称的数据来源。
- 后端 `ApprovalRequestServiceImpl.toVO()` 在返回 `bizType=USER` 的 `requestPayload` 前，为其中 `positions` 数组里每条新增/变更任职记录按 `orgId` 批量查出组织名称并注入 `orgName` 字段（只读时计算注入，不修改数据库里存储的原始 `requestPayload` JSON），前端沿用既有的"优先展示 `orgName`，取不到才退化为 `orgId`"逻辑即可自动生效，不需要改前端渲染代码。
- 不新增数据库字段、不改变 `targetId` 本身的存储与返回（仍然原样返回，只是前端不再把它当作主展示信息渲染），前端展示逻辑按 `bizType` 从 `requestPayload`/`targetSnapshot` 里取对应名称字段。
- 申请详情弹窗移除"生效记录ID"（`resultTargetId`）展示项：有了"审批对象"这一更友好的展示，`resultTargetId` 这个数据库自增 id 对用户不再有查看价值，直接去掉，不保留占位。仅移除前端展示，不改变 `resultTargetId` 字段本身的存储、返回与业务语义（创建类审批通过后回填新记录 id 的逻辑不受影响）。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `master-data-approval-workflow`：
  - Requirement "审批申请查询"：从"仅 UPDATE 类型申请提供目标记录当前值"改为"UPDATE/ENABLE/DISABLE/DELETE 四类涉及已存在目标记录的操作均提供目标记录当前值，供前端解析出用于展示的审批对象名称"；并新增"USER 类型申请的 `requestPayload.positions` 中每条任职记录 SHALL 携带可展示的组织名称"这一点。

## Impact

- 前端：`frontend/src/views/approval/mine/MyApprovalRequestView.vue`、`frontend/src/views/approval/pending/PendingApprovalRequestView.vue`、`frontend/src/components/ApprovalRequestDetailDialog.vue`；新增一个按 `bizType` 从行数据解析"审批对象"展示文案的小工具函数（放在合适的 utils 或就地实现，三处复用同一份逻辑，避免重复）；`positions.orgName` 走既有渲染逻辑自动生效，只需更新相关注释。
- 后端：`backend/src/main/java/cn/nihility/rbac/approval/service/impl/ApprovalRequestServiceImpl.java` 的 `toVO()` 方法；新增 `OrgMapper` 依赖用于批量查询组织名称（沿用 `UserServiceImpl` 已有的同类批量查询模式，不新增第三方依赖）。
- 不影响：`targetId`/`resultTargetId` 字段本身的语义与既有依赖它们做业务判断的逻辑（撤回、创建结果关联等）。
