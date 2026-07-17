## Why

应用管理页面（`views/application/app/AppManagementView.vue`）和任职管理页面
（`views/identity/position/PositionManagementView.vue`）目前都在 `onMounted`
里无条件调用 `fetchOrgTree()`，请求全量组织树接口 `GET /api/orgs/tree`，用于填充
新增/编辑弹窗内"所属组织"选择器；但这份全量树数据唯一的消费者是这两个弹窗，绝大多数
只浏览应用列表/任职列表、从不新增或编辑的页面访问会因此白白触发一次这个请求。组织
管理页面和用户管理页面此前已经各自完成过同样的懒加载改造（见已归档的
`2026-07-15-org-tree-defer-parent-selector-load`、`2026-07-15-user-org-tree-defer-load`），
本次是把同一约定补齐到剩下的两个消费全量组织树的页面。

## What Changes

- 应用管理页面进入时不再预先请求全量组织树；改为用户实际打开新增/编辑弹窗时才请求，
  请求完成后再展示弹窗。
- 任职管理页面进入时不再预先请求全量组织树；改为用户实际打开新增/编辑弹窗时才请求，
  请求完成后再展示弹窗（左侧导航树本身已经是懒加载，不受影响）。

## Capabilities

### Modified Capabilities
- `application-management`: 新增/编辑弹窗"所属组织"全量树的加载时机从"页面进入即
  加载"改为"仅在打开新增/编辑弹窗时按需加载"。不涉及后端接口变化。
- `position-management`: 新增/编辑弹窗"所属组织"全量树的加载时机从"页面进入即加载"
  改为"仅在打开新增/编辑弹窗时按需加载"；左侧导航树的懒加载方式不变。不涉及后端接口
  变化。

## Impact

- 前端：`views/application/app/AppManagementView.vue`（`onMounted` 去掉
  `fetchOrgTree()` 调用；`openCreateDialog` 改为 `async` 并在函数体首行
  `await fetchOrgTree()`；`openEditDialog` 内新增 `await fetchOrgTree()`）、
  `views/identity/position/PositionManagementView.vue`（同样调整：`onMounted` 去掉
  `fetchOrgTree()`，只保留 `fetchPositionTypeOptions()`；`openCreateDialog` 改为
  `async` 并 `await fetchOrgTree()`；`openEditDialog` 内新增 `await fetchOrgTree()`）。
- 后端：无变化，复用既有的 `GET /api/orgs/tree`。
- 数据库：无变化。
