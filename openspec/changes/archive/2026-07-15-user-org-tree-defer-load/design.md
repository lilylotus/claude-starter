## Context

`UserManagementView.vue` 的 `onMounted` 无条件调用 `fetchOrgTree()`（`orgApi.getOrgTree()` → `GET /api/orgs/tree`），把结果存进 `orgTree`。全文件搜索确认 `orgTree` 只在模板里被一处引用：新增/编辑弹窗内"任职信息"子表单的"所属组织" `el-tree-select`（`:data="orgTree"`）。列表、搜索、分页、只读详情弹窗（任职记录表格用的是 `detailData.positions[].orgName`，后端已经解析好的字符串）都不读它。这和此前组织管理页面的"上级组织"选择器是同一种模式（见已归档的 [[org-tree-defer-parent-selector-load]]）。

## Goals / Non-Goals

**Goals:**
- 全量组织树接口只在用户打开新增或编辑弹窗时才请求，浏览/搜索用户列表本身不再触发它。
- 打开弹窗时看到的仍然是当时最新的全量树数据。

**Non-Goals:**
- 不改动详情弹窗（不依赖 `orgTree`，本来就不受影响）。
- 不改动 `userStore.refreshAfterMutation()`（它只刷新用户列表分页数据，本来就不涉及 `orgTree`，无需改动）。
- 不涉及组织管理、任职管理页面各自的组织树加载逻辑（各自独立维护，超出本次范围）。

## Decisions

### 1. 全量树请求移到 `openCreateDialog`/`openEditDialog` 内部，`await` 后再展示弹窗
- `openCreateDialog` 改为 `async`，函数体首行 `await fetchOrgTree()`，其余逻辑不变。
- `openEditDialog` 已经是 `async` 且已经 `await userApi.getUserById(row.id)`；新增一行 `await fetchOrgTree()`，顺序执行（与组织管理页面那次改动的决策一致，不用 `Promise.all`，理由相同：弹窗打开延迟本身很短，不值得为此增加代码复杂度）。
- 理由：每次打开弹窗都重新请求，天然拿到最新数据，不需要额外的缓存失效机制。

## Risks / Trade-offs

- [Risk] 新增/编辑弹窗打开时会比之前多一次可感知的网络等待 → Mitigation：与组织管理页面那次改动接受的权衡一致，`openEditDialog` 本来就有等待 `getUserById` 的先例，用户对"点开弹窗有短暂等待"并不陌生。

## Open Questions

无。
