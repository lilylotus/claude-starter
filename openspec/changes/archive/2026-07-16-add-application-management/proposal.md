## Why

应用管理菜单（`/application/list`）目前只是一个占位页面，没有真正的应用主数据管理能力。需要落地"应用管理"功能，让管理员能维护接入本系统的应用清单：新增应用、指定应用编码、负责人和所属组织、按显示序号排序浏览、启停用和逻辑删除。

## What Changes

- 新增身份管理体系下的"应用管理"独立能力（挂在已有菜单组"应用管理"下的子菜单，路径 `/application/list`；该子菜单文案原为"应用列表"，实现完成后按用户要求改为"应用管理"，与一级菜单组同名），提供应用的分页查询、详情查询、创建、更新、启用、停用、逻辑删除接口。
- 应用管理页面：
  - 顶部无搜索栏，按 `showOrder` 降序（相同时按 `id` 升序）分页展示（每页 10 条）。
  - 列表列：应用名称、应用编码、负责人、所属组织、显示序号、状态、操作。
  - 新增/编辑弹窗字段：应用名称（必填）、应用编码（必填，在未删除应用范围内唯一，语义和校验方式对齐组织管理的组织编码）、负责人（必填，远程搜索选择，支持按姓名或手机号搜索已存在用户，复用 `GET /api/users?name=`/`?mobile=`）、所属组织（必填，组织树单选，复用 `GET /api/orgs/tree`）、显示序号（默认 `0`）、备注（可选）。
  - 启用/停用/删除/详情交互模式与组织管理、用户管理、任职管理一致（行内操作按钮 + 删除二次确认；详情为只读弹窗）。
- 新增应用记录不涉及应用密钥（appKey/appSecret）等字段——"应用密钥"是同一菜单组下的另一个独立菜单项（`/application/secret`），本次不实现，也不在应用主数据里预留相关字段。

## Capabilities

### New Capabilities

- `application-management`：应用主数据的分页查询、详情查询、创建、更新、启用、停用、逻辑删除能力，及配套的"应用管理"管理界面。依赖 `org-management` 提供组织树数据、`user-management` 提供负责人的按姓名/手机号搜索。

## Impact

- 数据库：新增 Flyway 迁移脚本（`V7__init_tab_app.sql`），新建 `tab_app` 表，含 `code`（应用编码）字段。
- 后端：新增 `cn.nihility.rbac.app` 包，包含 `AppController`、`AppService`/`AppServiceImpl`、`AppCreateRequest`/`AppUpdateRequest`/`AppVO`、`AppConvert`（MapStruct）、`AppStatus` 常量类、`AppEntity`、`AppMapper`；`AppServiceImpl` 新增 `checkCodeUnique` 校验（对齐 `OrgServiceImpl`）；并按项目既有惯例（参照 `PositionServiceImplTest`）新增 `AppServiceImplTest` 单元测试，含编码唯一性校验的覆盖用例。
- 前端：新增 `frontend/src/views/application/app/AppManagementView.vue`、`frontend/src/api/app.ts`、`frontend/src/types/app.ts`、`frontend/src/stores/app.ts`；调整 `frontend/src/router/index.ts` 的 `implementedComponents`，把 `/application/list` 指向新页面（替换 `PlaceholderView`）；`frontend/src/router/menu.ts` 把该子菜单文案由"应用列表"改为"应用管理"（用户要求）。
- 不涉及 `org-management`、`user-management` 现有接口/规格的变更，均只是只读复用其既有查询接口。
