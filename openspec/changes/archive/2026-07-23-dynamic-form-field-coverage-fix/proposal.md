## Why

"表单字段定义"（动态自定义字段，`ext1`~`ext10`）能力已经落地到组织/用户/任职/应用四个模块各自的列表与新增/编辑表单，但三处相邻界面被遗漏，仍停留在旧的硬编码字段集合上：新增用户时内嵌的"任职信息"子表单不支持自定义字段；组织/用户/任职/应用的详情页面不展示自定义字段（尽管后端详情接口已经返回）；四个模块的操作历史也不记录自定义字段的变更。这导致管理员通过"表单管理"新增的自定义字段在这三处要么无法填写、要么填了看不见、要么改了留不下痕迹，功能不完整。

## What Changes

- 用户管理内嵌的任职子表单（新增/编辑用户弹窗中的"任职信息"）新增对 `bizType=POSITION` 动态字段的支持：请求/返回结构（`UserPositionRequest`/`UserPositionVO`）补齐 `ext1`~`ext10`，`UserConvert` 不再忽略这些字段，前端子表单接入 `useDynamicFormFields('POSITION')` 动态渲染。
- 组织、用户、任职、应用四个详情页面（`OrgDetailView.vue`/`UserDetailView.vue`/`PositionDetailView.vue`/`AppDetailView.vue`）接入对应 `bizType` 的动态字段渲染元数据，只读展示启用状态的自定义字段（含用户详情页内嵌的任职记录列表）。
- 组织、用户、任职、应用四个模块记录操作日志时使用的字段快照（各自的 `toLogSnapshot`）补充 `ext1`~`ext10`，使自定义字段的新增/编辑变更能出现在操作历史的字段级变更详情中；快照中自定义字段的展示名取自该 `bizType` 当前启用的表单字段定义（而非写死 `ext1` 这类技术字段名）。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `user-management`：新增用户/更新用户内嵌任职子表单支持 `POSITION` 动态字段的读写；用户详情查询与详情页面展示用户自身及其任职记录的 `ext1`~`ext10`；用户详情操作历史的字段级变更详情包含 `ext1`~`ext10`。
- `position-management`：任职记录的 `UserPositionRequest`/`UserPositionVO`（用户管理内嵌入口所用结构）纳入 `ext1`~`ext10`；任职记录详情操作历史的字段级变更详情包含 `ext1`~`ext10`。
- `org-management`：组织详情页面展示 `ext1`~`ext10`；组织详情操作历史的字段级变更详情包含 `ext1`~`ext10`。
- `application-management`：应用详情页面展示 `ext1`~`ext10`；应用详情操作历史的字段级变更详情包含 `ext1`~`ext10`。

## Impact

- 后端：`user/dto/UserPositionRequest.java`、`user/dto/UserPositionVO.java`、`user/mapstruct/UserConvert.java`、`user/service/impl/UserServiceImpl.java`（`toLogSnapshot`）、`org/service/impl/OrgServiceImpl.java`（`toLogSnapshot`）、`user/service/impl/PositionServiceImpl.java`（`toLogSnapshot`）、`app/service/impl/AppServiceImpl.java`（`toLogSnapshot`）。
- 前端：`views/identity/user/UserManagementView.vue`（任职子表单）、`views/identity/org/OrgDetailView.vue`、`views/identity/user/UserDetailView.vue`、`views/identity/position/PositionDetailView.vue`、`views/application/app/AppDetailView.vue`。
- 不涉及数据库迁移（`ext1`~`ext10` 列已存在），不新增依赖。
