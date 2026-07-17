## Why

RBAC 权限管理系统目前的"权限管理"分组下只有角色管理、权限点管理，缺少"管理员"这一层
概念——即"哪个已存在的用户被授予了管理员身份、拥有哪些角色、能管辖哪些组织范围"。
这次改动新增"管理员管理"页面，把用户（`tab_user`）、角色（`tab_role`）、组织
（`tab_org`）三者通过一个新的管理员主数据表关联起来，支撑后续鉴权/数据权限收窄
（按管辖组织范围过滤数据）打基础。

## What Changes

- 新增 `tab_admin` 主数据表：管理员名称、管理员编码、关联用户 id、显示序号、备注、
  状态，编码在未删除范围内唯一，一个用户在未删除范围内最多关联一个管理员。
- 新增 `tab_admin_role` 管理员-角色关联表（多对多）：一个管理员可关联多个角色。
- 新增 `tab_admin_org_scope` 管理员组织管辖范围表：一个管理员可管辖多个组织，每个
  组织独立标记"是否包含递归子组织"。
- 新增管理员管理后端接口：分页查询（按显示序号降序）、详情（含关联用户名、角色
  列表、组织管辖范围列表）、新增、编辑（角色/组织管辖范围随主表一并整体同步）、
  启用、停用、逻辑删除。
- 角色模块新增一个不分页的"角色选项"接口（`GET /api/roles/options`），供管理员
  表单的角色多选下拉使用（现有 `GET /api/roles` 只支持分页，不适合下拉框场景，
  与 `GET /api/orgs/tree`、字典项 `options` 接口是同类角色）。
- 新增管理员管理前端页面（路径 `/permission/admins`，挂在"权限管理"分组下），
  交互参照角色管理（分页表格 + 新增/编辑/详情/启用/停用/删除弹窗）：新增/编辑弹窗
  内"关联用户"为远程搜索单选（复用用户管理已有的按姓名/手机号搜索接口）、"管理员
  角色"为多选下拉（数据源为新增的角色选项接口，页面挂载时一次性加载）、"管辖组织
  范围"为动态多行子表单（每行一个组织树单选 + "含子组织"复选框，交互参照用户管理
  弹窗内任职信息子表单的加行/删行模式），组织树数据延迟到打开新增/编辑弹窗时才请求
  （遵循本仓库既有约定，见 `CLAUDE.md`）。

## Capabilities

### Added Capabilities
- `admin-management`：管理员主数据的增删改查、启停用、以及管理员与角色、管理员与
  组织管辖范围的多对多关联维护。

### Modified Capabilities
- `role-management`：新增一个不分页的角色选项查询接口，供其他模块的角色选择器复用；
  不影响角色管理已有的分页查询、维护、启停用、删除行为。

## Impact

- 数据库：新增 `tab_admin`、`tab_admin_role`、`tab_admin_org_scope` 三张表
  （Flyway `V12__init_tab_admin.sql`）。
- 后端：新增 `cn.nihility.rbac.admin` 包（entity/mapper/mybatis xml/dto/mapstruct/
  service/controller/constant/exception），`role` 模块新增 `GET /api/roles/options`
  接口及对应 Service 方法。
- 前端：新增 `views/permission/admin/AdminManagementView.vue`、
  `api/admin.ts`、`types/admin.ts`；`role.ts`（api/types）新增角色选项查询封装；
  `router/menu.ts`、`router/index.ts` 挂载新页面。
- 完成后需要同步更新仓库根目录 `权限资源.txt`（新增 `AdminManagement:admin:*` 系列
  编码）并追加一份 `tab_menu` 种子数据迁移，做法参照已归档的
  `2026-07-17-seed-menu-resource-data`。
