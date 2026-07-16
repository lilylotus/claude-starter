## Why

"系统管理"菜单组下的"菜单管理"（`/system/menus`）目前只是一个占位页面。系统需要一份可维护的资源主数据——RBAC 鉴权链路里角色勾选的"资源"（菜单/按钮/API）目前完全没有落地，管理员无法维护侧边栏菜单结构、按钮级权限点或 API 资源的树形归属关系。本次落地这份资源树主数据的增删改查、启停用、逻辑删除能力。

## What Changes

- 新增"菜单管理"独立能力（挂在已有菜单"系统管理 → 菜单管理"，路径 `/system/menus`；菜单文案沿用现有的"菜单管理"，不主动改名），提供资源树查询、树懒加载子节点查询、直属子资源分页查询、详情查询、创建、更新、启用、停用、逻辑删除接口。
- 资源实体字段：资源名称、资源编码、上级资源（`parentId`，树形结构，`0` 表示顶级）、资源类型（固定三选一：菜单/按钮/API，非可扩展字典）、显示序号（`showOrder`）、备注、状态。
- 菜单管理页面：左侧展示资源树（懒加载，默认全部收起，与 `org-management` 左侧组织树交互模式一致），右侧以分页表格展示选中节点的直属下级资源（未选中时默认展示顶级资源第一页），按 `showOrder` 降序（相同时按 `id` 升序）分页展示（每页 10 条）。
  - 列表列：资源名称、资源编码、资源类型、显示序号、状态、操作。
  - 新增/编辑弹窗字段：资源名称（必填）、资源编码（必填，在未删除资源范围内唯一）、上级资源（树选择器，复用 `org-management` 的"虚拟顶级根节点 + 防环"模式）、资源类型（必填，单选：菜单/按钮/API）、显示序号（默认 `0`）、备注（可选）。
  - 启用/停用/删除/详情交互模式与组织管理、角色管理一致（行内操作按钮 + 删除二次确认；详情为只读弹窗）；存在未删除下级资源时拒绝删除，与组织管理的树形删除约束一致。
- 不包含"资源"与角色/权限点的关联勾选（例如角色绑定可见菜单、按钮级权限点与 API 资源的鉴权校验）——本次只落地资源树自身的主数据管理，与 `role-management`/`permission-management` 当初的范围收敛方式一致。
- 不把"资源类型"接入通用字典管理模块（`dict-management`）：菜单/按钮/API 是固定的三种结构性类型而非可由管理员自由增减的业务枚举，采用与状态码同样的"固定常量类"方案。

## Capabilities

### New Capabilities

- `menu-management`：资源（菜单/按钮/API）树形主数据的树查询、懒加载子节点查询、分页查询、详情查询、创建、更新、启用、停用、逻辑删除能力，及配套的左树右表主从式管理界面。树形交互与删除约束对齐 `org-management`；扁平字段（名称+编码+显示序号+备注+状态）与交互模式对齐 `role-management`/`permission-management`。

## Impact

- 数据库：新增 Flyway 迁移脚本（`V10__init_tab_menu.sql`），新建 `tab_menu` 表。
- 后端：新增 `cn.nihility.rbac.menu` 包，包含 `MenuController`、`MenuService`/`MenuServiceImpl`、`MenuCreateRequest`/`MenuUpdateRequest`/`MenuVO`/`MenuTreeNodeVO`、`MenuConvert`（MapStruct）、`MenuStatus`/`MenuResourceType` 常量类、`MenuEntity`、`MenuMapper`；新增 `MenuServiceImplTest` 单元测试。
- 前端：新增 `frontend/src/views/system/menu/MenuManagementView.vue`、`frontend/src/api/menu.ts`、`frontend/src/types/menu.ts`、`frontend/src/stores/menu.ts`；调整 `frontend/src/router/index.ts` 的 `implementedComponents`，把 `/system/menus` 指向新页面（替换 `PlaceholderView`），并从 `stubDescriptions` 移除对应条目；`frontend/src/router/menu.ts` 不需要改动（菜单项已存在）。
- 不涉及其他已有能力（`org-management`/`user-management`/`dict-management`/`application-management`/`role-management`/`permission-management`）的接口/规格变更。
