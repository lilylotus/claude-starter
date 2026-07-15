## Why

用户管理页面（`views/identity/user/UserManagementView.vue`）目前一进入页面就无条件请求全量组织树接口 `GET /api/orgs/tree`（`onMounted` 里调用 `fetchOrgTree()`）。这份数据（`orgTree`）唯一的消费者是新增/编辑用户弹窗里"任职信息"子表单的"所属组织" `el-tree-select`；用户列表、搜索、分页、详情弹窗（任职记录里的组织名走的是后端已解析好的 `orgName` 字符串字段，不依赖 `orgTree`）都不需要它。绝大多数只是浏览/搜索用户列表的页面访问，会因此白白多发一次全量组织树请求，和此前组织管理页面同类问题（[[org-tree-defer-parent-selector-load]]，已归档）是一样的模式。

## What Changes

- 用户管理页面进入时不再预先请求全量组织树接口；改为在用户实际打开新增/编辑弹窗时才请求，请求完成后再展示弹窗。

## Capabilities

### Modified Capabilities
- `user-management`: 前端用户管理页面里"所属组织"选择器用的全量组织树，加载时机从"页面进入即加载"改为"仅在打开新增/编辑弹窗时按需加载"。不涉及后端接口变化。

## Impact

- 前端：`views/identity/user/UserManagementView.vue`（`onMounted` 去掉 `fetchOrgTree()`、`openCreateDialog` 改为 `async` 并 `await fetchOrgTree()`、`openEditDialog` 新增 `await fetchOrgTree()`）。
- 后端：无变化，复用既有的 `GET /api/orgs/tree`。
- 数据库：无变化。
