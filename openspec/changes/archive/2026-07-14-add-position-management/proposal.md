## Why

身份管理目前只有"组织管理"和"用户管理"两个入口：组织的层级结构在组织管理里维护，用户的任职记录只能在用户管理的新增/编辑弹窗里以内嵌子表单的形式逐条维护，无法脱离"编辑某个用户"这个入口去"按组织查看/维护挂在该组织下的所有任职人员"。这在组织人数较多、需要按部门盘点任职情况时很不方便。需要新增一个以组织为导航维度的"任职管理"页面，并让任职记录具备独立的启用/停用/删除能力（目前任职记录没有独立状态列，只能随用户整体物理删除）。

## What Changes

- 新增身份管理下的"任职管理"菜单（路径 `/identity/positions`），左侧组织树（默认全部收起、懒加载展开，复用组织管理左侧树的交互模式），右侧展示当前选中组织下的任职人员分页列表；未选中组织节点时右侧为空并提示先选择组织。
- 任职管理页面支持新增、编辑、启用、停用、删除、详情：
  - 新增：选择一个已存在的用户（远程搜索），所属组织默认预填为左侧当前选中节点（可改），填写任职类型、任职地址、任职电话、显示序号、备注。
  - 编辑：可修改所属组织及任职类型/地址/电话/显示序号/备注；所属用户不可修改。
  - 启用/停用：切换任职记录状态，与组织/用户模块语义一致。
  - 删除：逻辑删除（`status = -1000`）。
  - 详情：只读展示任职记录完整信息（含所属用户、所属组织、审计字段）。
- **BREAKING（内部数据模型）**：`tab_user_position` 新增 `status` 列（`2000`=启用，`3000`=停用，`-1000`=已删除），删除语义从"随用户整体物理删除"变为"逻辑删除"；已有数据通过 Flyway 迁移回填为 `2000`。
- 用户管理模块中内嵌的任职子表单行为保持不变（仍是"整体 diff、请求中未出现的既有记录物理删除"），但新增记录时显式写入 `status = 2000`，且查询任职记录（用户详情回显、diff 时的既有记录基准）时排除 `status = -1000` 的记录，避免任职管理页面里已逻辑删除的记录在用户编辑页面"复活"。

## Capabilities

### New Capabilities

- `position-management`：任职管理页面（组织树导航 + 任职人员分页列表）及配套的任职记录独立查询、创建、编辑、启用、停用、逻辑删除接口。

### Modified Capabilities

- `user-management`：任职记录不再是"无独立状态、随用户物理删除"，而是拥有 `2000`/`3000`/`-1000` 状态语义；用户详情、内嵌任职子表单的既有记录查询需要排除已逻辑删除的任职记录，新增任职记录需要显式置为启用状态。

## Impact

- 数据库：新增 Flyway 迁移脚本（`V6__add_status_to_tab_user_position.sql`），为 `tab_user_position` 增加 `status` 列并回填。
- 后端：`cn.nihility.rbac.user` 包下新增 `PositionController`、`PositionService`/`PositionServiceImpl`、`PositionCreateRequest`/`PositionUpdateRequest`/`PositionVO`、`PositionConvert`（MapStruct）、`PositionStatus` 常量类；复用既有 `UserPositionEntity`/`UserPositionMapper`；调整 `UserServiceImpl` 中任职记录相关的查询与新增逻辑。
- 前端：新增 `frontend/src/views/identity/position/PositionManagementView.vue`、`frontend/src/api/position.ts`、`frontend/src/types/position.ts`、`frontend/src/stores/position.ts`；调整 `frontend/src/router/menu.ts`（新增菜单项）、`frontend/src/router/index.ts`（登记新路由组件）。
- 不涉及组织管理模块（`org-management`）本身的接口/规格变更，左侧组织树复用其现有只读查询接口。
