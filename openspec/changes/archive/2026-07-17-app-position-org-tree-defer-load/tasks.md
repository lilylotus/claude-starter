## 1. 前端：应用管理页面延迟加载全量组织树

- [x] 1.1 `views/application/app/AppManagementView.vue`：`onMounted` 去掉
  `fetchOrgTree()` 调用，只保留 `appStore.fetchPage()`
- [x] 1.2 `openCreateDialog` 改为 `async` 函数，函数体首行 `await fetchOrgTree()`
- [x] 1.3 `openEditDialog` 新增 `await fetchOrgTree()`（放在 `await
  appApi.getAppById(row.id)` 之后顺序执行）

## 2. 前端：任职管理页面延迟加载全量组织树

- [x] 2.1 `views/identity/position/PositionManagementView.vue`：`onMounted` 去掉
  `fetchOrgTree()` 调用，只保留 `fetchPositionTypeOptions()`
- [x] 2.2 `openCreateDialog` 改为 `async` 函数，函数体首行 `await fetchOrgTree()`
- [x] 2.3 `openEditDialog` 新增 `await fetchOrgTree()`（放在 `await
  positionApi.getPositionById(row.id)` 之后顺序执行）

## 3. 验证

- [x] 3.1 `npx vue-tsc --noEmit` 通过，无类型错误
- [ ] 3.2 真实浏览器端到端验证（启动 `bootRun` + `vite`，捕获网络请求确认页面加载
  阶段不再发出 `GET /api/orgs/tree`，仅在打开新增/编辑弹窗时发出一次）——尚未执行，
  后续需要时可参照 `2026-07-15-org-tree-defer-parent-selector-load` 的验证方式补做
