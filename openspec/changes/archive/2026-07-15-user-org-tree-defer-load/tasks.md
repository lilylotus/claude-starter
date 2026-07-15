## 1. 前端：UserManagementView.vue 调整组织树加载时机

- [x] 1.1 `onMounted` 去掉 `fetchOrgTree()` 调用，只保留 `userStore.fetchPage()` 与 `fetchPositionTypeOptions()`
- [x] 1.2 `openCreateDialog` 改为 `async` 函数，函数体首行 `await fetchOrgTree()`
- [x] 1.3 `openEditDialog` 新增 `await fetchOrgTree()`，放在 `await userApi.getUserById(row.id)` 之后顺序执行

## 2. 验证

- [x] 2.1 `npm run build`（`vue-tsc` + `vite build`）通过，无类型错误
- [x] 2.2 真实浏览器端到端验证（Playwright，启动 `bootRun` 48080 + `vite --host 127.0.0.1` 5173）：①进入用户管理页面（mount）只发出 `/api/users?page=1&pageSize=10`，未出现 `GET /api/orgs/tree`；②打开"详情"弹窗只发出 `/api/users/{id}`，同样未触发该接口；③点击"新增"后才唯一一次发出 `GET /api/orgs/tree`，弹窗正常展示；④弹窗内添加一条任职记录，点开"所属组织"下拉树，截图确认默认收起、仅展示顶层节点（机构01/02/03）。验证完毕后已停止两个临时进程，清理了 scratchpad 里的验证脚本、临时 npm 依赖与截图
