## Why

RBAC 身份体系（组织 → 用户 → 角色 → 权限）中，组织维度已经落地（`org-management`），但用户维度目前完全空白：`/identity/users` 路由渲染的是 `PlaceholderView.vue` 占位页，后端也没有任何用户相关模块。本 change 补齐用户主数据的维护能力，并支持用户与组织之间的任职关系（一个用户可以同时任职于多个组织，如一份主职加若干兼职/挂职），为后续角色赋权（用户 → 角色）打基础。

## What Changes

- 新增后端用户模块 `cn.nihility.rbac.user`：用户主数据（姓名、编号、性别、手机号、身份证、显示序号、备注）的增删改查、启用/停用、逻辑删除；编号、身份证号在未删除用户范围内唯一。
- 新增用户任职（`position`）子数据：一个用户可关联 0 到多条任职记录，每条记录包含所属组织、认证类型（引用 `dict-management` 模块中 `position_type` 字典类型下的项，如主职/兼职/挂职）、任职地址、任职电话、显示序号、备注及各自独立的创建/更新审计字段；任职记录随用户的创建/更新接口以整体列表的形式提交，服务端按 `id` 是否存在做增量更新（有 `id` 的按行更新并保留其原创建审计信息，无 `id` 的新增，请求中未出现的既有记录做物理删除）。
- 新增列表查询：分页展示，支持按姓名、手机号、身份证号模糊搜索（三个条件可分别单独或组合使用，均为可选，组合时为"与"关系）。
- 新增前端用户管理页面 `/identity/users`：分页表格 + 搜索栏，新增/编辑弹窗内嵌任职信息的可增删行内子表单（组织选择器、认证类型下拉框数据来源于 `dict-management` 模块的只读查询接口）。
- `router/menu.ts` 的 `identity` 分组下，"用户管理"菜单项调整到"组织管理"之后（此前顺序相反）。

## Capabilities

### New Capabilities
- `user-management`：用户主数据的维护能力（增删改查、启停用、按姓名/手机号/身份证号模糊搜索分页），以及用户与组织的多对多任职关联维护，及配套的前端列表+表单管理界面。

### Modified Capabilities
（无——本 change 不修改 `org-management`、`dict-management` 已归档/在途 capability 的需求，仅消费 `dict-management` 暴露的只读查询接口。）

## Impact

- **前置依赖**：本 change 依赖 `add-dict-management` change 中预置的 `position_type` 字典类型（`primary`/`part_time`/`temporary`）已存在；`add-dict-management` 需先于本 change 完成实现并通过验证。
- **后端代码**：新增 `backend/src/main/java/cn/nihility/rbac/user/**`（entity/constant/mapper/dto/service/controller/mapstruct）。
- **数据库**：新增 Flyway 迁移脚本，建表 `tab_user`、`tab_user_position`（`tab_user_position.org_id` 关联 `tab_org.id`，不建物理外键，与 `tab_org` 现有约定一致）。
- **前端代码**：新增 `frontend/src/views/identity/user/UserManagementView.vue`、`src/api/user.ts`、`src/stores/user.ts`、`src/types/user.ts`；修改 `router/menu.ts`（菜单顺序）与 `router/index.ts`（路由指向真实组件）。
- **API**：新增 `/api/users`（GET 分页/POST 创建）、`/api/users/{id}`（GET 详情/PUT 更新/DELETE 逻辑删除）、`/api/users/{id}/enable`、`/api/users/{id}/disable`，共 6 个接口，均通过全局响应包装为 `{ code, message, data }`；用户详情与创建/更新请求中内嵌任职记录列表，不单独暴露任职记录的 CRUD 接口。
