## Context

`AppManagementView.vue` 和 `PositionManagementView.vue` 各自维护一份本地
`orgTree`（`ref<OrgTreeNode[]>`），通过组件内 `fetchOrgTree()` 调用
`orgApi.getOrgTree()` 一次性加载全量组织树，供弹窗内 `el-tree-select`（"所属组织"
选择器）使用。两个页面都在 `onMounted` 里无条件调用这个函数，与是否会打开弹窗无关。
这与组织管理、用户管理页面此前的默认实现一致（也是同样的问题），后两者已经通过
`2026-07-15-org-tree-defer-parent-selector-load`、`2026-07-15-user-org-tree-defer-load`
两个 change 改成了"仅在打开弹窗时按需加载"，`UserManagementView.vue` 目前的实现
（`openCreateDialog`/`openEditDialog` 内 `await fetchOrgTree()`）就是现成的参考模式。

## Goals / Non-Goals

**Goals:**
- 应用管理、任职管理两个页面进入时不再触发全量组织树请求，只有真正打开新增/编辑弹窗
  时才请求一次。
- 打开弹窗时看到的仍然是当时最新的全量树数据，不引入过期缓存问题。

**Non-Goals:**
- 不改动任职管理页面左侧导航树的懒加载方式（已经是懒加载，不受影响）。
- 不改动后端任何接口，仍然复用现有的 `GET /api/orgs/tree`。
- 不引入弹窗内的"加载中"骨架屏；两个页面的 `openEditDialog` 本来就已经在 `await`
  详情接口，`openCreateDialog`/`openEditDialog` 改为 `async` 后 `await fetchOrgTree()`
  与既有等待模式一致，不额外画 loading 态。
- 不做进程内缓存/脏标记：每次打开弹窗都重新请求，用"每次都新鲜"替代"缓存 +
  失效"，与 `org-management` 此前的决策保持一致。

## Decisions

### 1. 两个页面各自的 `onMounted` 去掉 `fetchOrgTree()` 调用
- `AppManagementView.vue`：`onMounted` 只保留 `appStore.fetchPage()`。
- `PositionManagementView.vue`：`onMounted` 只保留 `fetchPositionTypeOptions()`（左侧
  导航树的懒加载由 `el-tree` 的 `lazy` 模式在挂载时自动对根节点触发一次 `load`，不需要
  主动调用）。

### 2. 全量树请求移到 `openCreateDialog`/`openEditDialog` 内部，`await` 后再展示弹窗
- 两个页面的 `openCreateDialog` 均改为 `async` 函数，函数体首行 `await fetchOrgTree()`，
  其余重置表单的逻辑不变。
- 两个页面的 `openEditDialog` 已经是 `async` 且已经 `await` 详情接口
  （`appApi.getAppById` / `positionApi.getPositionById`）；新增一行
  `await fetchOrgTree()`，与获取详情的请求顺序不重要（两者互不依赖），沿用
  `UserManagementView.vue` 的顺序 `await` 写法，不做 `Promise.all` 并发优化——弹窗
  打开延迟本身就很短，不值得为此增加代码复杂度。
- 理由：这样每次打开弹窗都能拿到当时最新的全量树，不需要额外的缓存失效机制。

## Risks / Trade-offs

- [Risk] 新增/编辑弹窗打开时会比之前多一次可感知的网络等待（之前全量树早已在页面
  加载时预取好，点开弹窗是瞬时的；改动后点开弹窗才发请求）→ Mitigation：这是本次
  改动明确要接受的权衡，用弹窗打开时的一次性小延迟换取绝大多数不打开弹窗的页面访问
  不再发多余请求；两个页面的 `openEditDialog` 已经有等待详情接口的先例，用户对"点开
  弹窗有短暂等待"并不陌生。
- [Risk] 连续多次打开/关闭弹窗会导致重复请求全量树（每次都重新拉取，不做进程内缓存）
  → Mitigation：组织树规模在当前项目定位下不大，且这是与 `org-management` 一致的
  既有权衡，不引入额外的脏标记状态机。

## Open Questions

无。
