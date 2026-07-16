## Why

权限管理菜单下的"权限点管理"（`/permission/points`）目前只是一个占位页面，没有真正的权限点主数据管理能力。需要落地这项功能，让管理员能维护最细粒度的权限点清单（如 `identity:user:edit`）：新增权限点、指定权限编码、按显示序号排序浏览、启停用和逻辑删除。这是 RBAC 鉴权链路的基础数据——角色勾选权限点、接口鉴权引用权限点，都要依赖这份主数据先存在。

## What Changes

- 新增权限管理体系下的"权限管理"独立能力（挂在已有菜单"权限管理 → 权限点管理"，路径 `/permission/points`；菜单文案沿用现有的"权限点管理"，不主动改名——与 `application-management` 落地时的处理方式一致：先按现有文案实现，是否改名等用户另行明确要求），提供权限点的分页查询、详情查询、创建、更新、启用、停用、逻辑删除接口。
- 权限管理页面：
  - 顶部无搜索栏，按 `showOrder` 降序（相同时按 `id` 升序）分页展示（每页 10 条）。
  - 列表列：权限名称、权限编码、备注、状态、显示序号、操作。
  - 新增/编辑弹窗字段：权限名称（必填）、权限编码（必填，在未删除权限范围内唯一）、显示序号（默认 `0`）、备注（可选）。
  - 启用/停用/删除/详情交互模式与角色管理、字典类型管理一致（行内操作按钮 + 删除二次确认；详情为只读弹窗）。
- 不包含角色-权限点勾选、接口鉴权引用权限点校验等能力——本次只落地权限点自身的主数据管理，与 `role-management` 当初的范围收敛方式一致（角色管理落地时也明确排除了权限点勾选）。

## Capabilities

### New Capabilities

- `permission-management`：权限点主数据的分页查询、详情查询、创建、更新、启用、停用、逻辑删除能力，及配套的管理界面。字段与交互模式对齐 `role-management`/`dict-management`（同为"名称+编码+显示序号+备注+状态"的扁平实体，无外键关联）。

## Impact

- 数据库：新增 Flyway 迁移脚本（`V9__init_tab_permission.sql`），新建 `tab_permission` 表。
- 后端：新增 `cn.nihility.rbac.permission` 包，包含 `PermissionController`、`PermissionService`/`PermissionServiceImpl`、`PermissionCreateRequest`/`PermissionUpdateRequest`/`PermissionVO`、`PermissionConvert`（MapStruct）、`PermissionStatus` 常量类、`PermissionEntity`、`PermissionMapper`；按项目既有惯例新增 `PermissionServiceImplTest` 单元测试。
- 前端：新增 `frontend/src/views/permission/permission/PermissionManagementView.vue`、`frontend/src/api/permission.ts`、`frontend/src/types/permission.ts`、`frontend/src/stores/permission.ts`；调整 `frontend/src/router/index.ts` 的 `implementedComponents`，把 `/permission/points` 指向新页面（替换 `PlaceholderView`），并从 `stubDescriptions` 移除对应条目；`frontend/src/router/menu.ts` 不需要改动（菜单项已存在）。
- 不涉及其他已有能力（`org-management`/`user-management`/`dict-management`/`application-management`/`role-management`）的接口/规格变更。
