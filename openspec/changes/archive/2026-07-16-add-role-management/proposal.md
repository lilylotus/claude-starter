## Why

权限管理菜单（`/permission/roles`）目前只是一个占位页面，没有真正的角色主数据管理能力。需要落地"角色管理"功能，让管理员能维护系统角色清单：新增角色、指定角色编码、按显示序号排序浏览、启停用和逻辑删除。角色-权限点勾选、用户-角色绑定等能力依赖尚未实现的"权限点管理"（`/permission/points`，目前仍是占位页），本次不实现，留待权限点管理落地后再单独规划。

## What Changes

- 新增权限管理体系下的"角色管理"独立能力（挂在已有菜单"权限管理 → 角色管理"，路径 `/permission/roles`），提供角色的分页查询、详情查询、创建、更新、启用、停用、逻辑删除接口。
- 角色管理页面：
  - 顶部无搜索栏，按 `showOrder` 降序（相同时按 `id` 升序）分页展示（每页 10 条）。
  - 列表列：角色名称、角色编码、备注、状态、显示序号、操作。
  - 新增/编辑弹窗字段：角色名称（必填）、角色编码（必填，在未删除角色范围内唯一）、显示序号（默认 `0`）、备注（可选）。
  - 启用/停用/删除/详情交互模式与组织管理、字典类型管理一致（行内操作按钮 + 删除二次确认；详情为只读弹窗）。
- 不包含权限点勾选、用户-角色绑定等能力（与用户确认过范围）。

## Capabilities

### New Capabilities

- `role-management`：角色主数据的分页查询、详情查询、创建、更新、启用、停用、逻辑删除能力，及配套的"角色管理"管理界面。字段与交互模式对齐 `dict-management` 中字典类型管理（同为"名称+编码+显示序号+备注+状态"的扁平实体，无外键关联）。

## Impact

- 数据库：新增 Flyway 迁移脚本（`V8__init_tab_role.sql`），新建 `tab_role` 表。
- 后端：新增 `cn.nihility.rbac.role` 包，包含 `RoleController`、`RoleService`/`RoleServiceImpl`、`RoleCreateRequest`/`RoleUpdateRequest`/`RoleVO`、`RoleConvert`（MapStruct）、`RoleStatus` 常量类、`RoleEntity`、`RoleMapper`；按项目既有惯例新增 `RoleServiceImplTest` 单元测试。
- 前端：新增 `frontend/src/views/permission/role/RoleManagementView.vue`、`frontend/src/api/role.ts`、`frontend/src/types/role.ts`、`frontend/src/stores/role.ts`；调整 `frontend/src/router/index.ts` 的 `implementedComponents`，把 `/permission/roles` 指向新页面（替换 `PlaceholderView`），并从 `stubDescriptions` 移除对应条目；`frontend/src/router/menu.ts` 不需要改动（菜单项已存在）。
- 不涉及其他已有能力（`org-management`/`user-management`/`dict-management`/`application-management`）的接口/规格变更。
