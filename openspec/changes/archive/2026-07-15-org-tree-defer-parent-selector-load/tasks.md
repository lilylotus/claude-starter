## 1. 前端：orgStore 调整全量树加载时机与选中节点状态

- [x] 1.1 `stores/org.ts`：`selectNode(id: number)` 改为 `selectNode(id: number, name: string)`，新增 `selectedName` 状态字段，与 `selectedId` 一起设置；`clearSelection()` 同步清空 `selectedName`
- [x] 1.2 `stores/org.ts`：`refreshAfterMutation()` 去掉 `await fetchTree()` 调用，其余逻辑不变
- [x] 1.3 `stores/org.ts`：`return` 里补充导出新增的 `selectedName`

## 2. 前端：OrgManagementView.vue 调整弹窗打开时机与标题数据源

- [x] 2.1 `onMounted` 去掉 `orgStore.fetchTree()` 调用，只保留 `orgStore.fetchChildren(0)`
- [x] 2.2 `handleNodeClick(node)` 改为调用 `orgStore.selectNode(node.id, node.name)`
- [x] 2.3 `rightPanelTitle` 改为直接读取 `orgStore.selectedName`，不再调用 `findNodeName`；删除 `findNodeName` 函数
- [x] 2.4 `openCreateDialog` 改为 `async` 函数，函数体首行 `await orgStore.fetchTree()`
- [x] 2.5 `openEditDialog` 新增 `await orgStore.fetchTree()`；实现上放在 `await orgApi.getOrgById(row.id)` 之后顺序执行（与 design.md 决策 1 一致，不用 `Promise.all`）

## 3. 验证

- [x] 3.1 `npm run build`（`vue-tsc` + `vite build`）通过，无类型错误
- [x] 3.2 真实浏览器端到端验证：启动 `bootRun`（48080）与 `vite --host 127.0.0.1`（5173），用 Playwright（本地已缓存 chromium，临时装到 scratchpad 目录，未写入项目依赖）登录后捕获 `/api/orgs` 相关请求。结果：①进入组织管理页面（mount）只发出 `/api/orgs/tree/children?parentId=0` 与 `/api/orgs/children?parentId=0&page=1&pageSize=10`，未出现 `GET /api/orgs/tree`；②点击左侧树节点"机构02"，只触发对应的懒加载/分页请求，右侧标题正确显示为"[机构02]下级组织"，同样未触发 `GET /api/orgs/tree`；③点击"新增"后才唯一一次发出 `GET /api/orgs/tree`，且弹窗正常展示。验证完毕后已停止两个临时进程，清理了 scratchpad 里的验证脚本与临时 npm 依赖
