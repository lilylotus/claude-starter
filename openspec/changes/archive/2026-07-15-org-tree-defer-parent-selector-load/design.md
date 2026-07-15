## Context

`OrgManagementView.vue` + `stores/org.ts` 里目前有三处围绕全量树（`orgStore.tree`，来自 `GET /api/orgs/tree`）的调用：
1. `onMounted` 里无条件调用 `orgStore.fetchTree()`。
2. `rightPanelTitle` 计算属性里用 `findNodeName(orgStore.tree, orgStore.selectedId)` 递归查找选中节点的名称。
3. `refreshAfterMutation()` 里增删改成功后无条件重新调用 `fetchTree()`。

这三处调用都是为了保证新增/编辑弹窗的"上级组织" `el-tree-select`（`treeSelectData`）能拿到最新的全量树数据，但弹窗未必会被打开——多数用户只是浏览左侧树、翻右侧表格分页，从不新增/编辑。

## Goals / Non-Goals

**Goals:**
- 全量组织树接口只在用户实际打开新增/编辑弹窗时才请求一次，页面加载和增删改操作本身不再触发它。
- 打开弹窗时看到的仍然是当时最新的全量树数据（不引入过期缓存问题）。
- 右侧面板标题不再依赖全量树数据即可正确展示。

**Non-Goals:**
- 不改动左侧导航树的懒加载方式（已经是懒加载，不受影响）。
- 不改动后端任何接口，仍然复用现有的 `GET /api/orgs/tree`。
- 不引入弹窗内的"加载中"骨架屏之类的额外 UI 状态；`openCreateDialog`/`openEditDialog` 改为 `async` 函数，`await` 全量树请求完成后再把 `dialogVisible` 置为 `true`，用户体验上等价于弹窗出现前有一个短暂等待（复用 `openEditDialog` 现有的等待 `getOrgById` 的既有模式），不额外画 loading 态。

## Decisions

### 1. 全量树请求移到 `openCreateDialog`/`openEditDialog` 内部，`await` 后再展示弹窗
- `openCreateDialog` 改为 `async`，函数体首行 `await orgStore.fetchTree()`，其余逻辑不变。
- `openEditDialog` 已经是 `async` 且已经 `await orgApi.getOrgById(row.id)`；新增一行 `await orgStore.fetchTree()`，与获取详情的请求顺序不重要（两者互不依赖），为减少改动直接顺序 `await`，不做 `Promise.all` 并发优化——弹窗打开延迟本身就很短，不值得为此增加代码复杂度。
- 理由：这样每次打开弹窗都能拿到当时最新的全量树，天然替代了原来"增删改后主动刷新缓存"的作用，不需要额外的缓存失效/标记脏机制。

### 2. `refreshAfterMutation()` 去掉 `await fetchTree()`
- 增删改成功后不再主动刷新 `orgStore.tree`；下次任意一次打开弹窗时（决策 1）会重新请求，天然是最新数据，不存在拿到过期全量树的风险。
- 理由：`refreshAfterMutation()` 本来就是为了保证"下一次会用到这份数据的地方看到的是最新的"，既然消费点已经改成"用之前必刷新"，这里的主动刷新就是多余的重复请求。

### 3. 右侧面板标题改用左侧树点击事件自带的节点名称，不再查全量树
- `orgStore.selectNode(id)` 签名改为 `selectNode(id, name)`，新增一个 `selectedName` 状态字段，与 `selectedId` 一起在 `selectNode` 里设置；`clearSelection()` 同时清空两者。
- `OrgManagementView.vue` 的 `handleNodeClick(node)` 改为传入 `orgStore.selectNode(node.id, node.name)`（左侧懒加载树的节点数据本来就包含 `name` 字段，无需额外请求）。
- `rightPanelTitle` 改为 `orgStore.selectedId === null ? '' : (orgStore.selectedName ? \`[${orgStore.selectedName}]下级组织\` : '')`，不再调用 `findNodeName`；`findNodeName` 函数本身连同其递归实现一并删除（不再有调用方）。
- 理由：标题只需要"当前选中节点的名字"，点击事件本身已经带着这个信息，没必要为了一个字段去依赖整棵全量树。

## Risks / Trade-offs

- [Risk] 新增/编辑弹窗打开时会比之前多一次可感知的网络等待（之前全量树早已在页面加载时预取好，点开弹窗是瞬时的；改动后点开弹窗才发请求）→ Mitigation：这是本次改动明确要接受的权衡——用弹窗打开时的一次性小延迟，换取绝大多数不打开弹窗的页面访问不再发多余请求；`openEditDialog` 已经有等待 `getOrgById` 的先例，用户对"点开弹窗有短暂等待"并不陌生。
- [Risk] 连续多次打开/关闭弹窗会导致重复请求全量树（每次都重新拉取，不做进程内缓存）→ Mitigation：组织树规模在当前项目定位下不大，且这本来就是"决策 2"里选择的简化方案（用"每次都新鲜"替代"缓存 + 失效"），不引入额外的脏标记状态机。

## Open Questions

无。
