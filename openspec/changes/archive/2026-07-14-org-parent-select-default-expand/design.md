## Context

`OrgManagementView.vue` 的新增/编辑弹窗里，"上级组织"用 `el-tree-select` 展示，数据源是 `editableTreeSelectData`（在 `orgStore.tree` 全量树前拼接一个虚拟顶级根节点 `{ id: 0, name: '顶级组织' }`；编辑时额外 `pruneSubtree` 掉自身及子孙）。当前该组件带 `default-expand-all`，无论新增还是编辑，打开弹窗即把整棵树全部展开。

## Goals / Non-Goals

**Goals:**
- 新增弹窗打开时，只展开虚拟根节点，仅显示顶级组织，顶级组织节点本身不展开。
- 编辑弹窗打开时，只展开从根到当前上级组织（`parentId` 对应节点）的祖先路径，该节点本身以及路径之外的节点不展开。
- 每次打开弹窗都按当前场景（新增 / 编辑哪个组织）重新计算展开范围。

**Non-Goals:**
- 不改变可选中哪些节点的规则（仍是一次性全量加载 + `check-strictly` + 编辑时 prune 自身/子孙）。
- 不引入懒加载；`el-tree-select` 的数据源继续保持一次性全量加载。

## Decisions

- **展开范围的计算方式**：新增一个 `findAncestorPath(nodes, targetId)` 递归函数，在 `editableTreeSelectData`（含虚拟根）中查找目标节点的祖先路径（含自身，按从根到叶的顺序）。`default-expanded-keys` 取该路径去掉最后一个元素（即只展开祖先，不展开目标节点自身）：
  - 新增模式：目标固定为虚拟根 `id = 0`，路径就是 `[0]`，去掉最后一个得到 `[]`？不对——虚拟根本身也需要展开才能看到顶级组织。因此新增模式直接特判为 `[0]`（展开虚拟根本身），不复用"去掉最后一个元素"这条规则；编辑模式复用祖先路径规则（`path.slice(0, -1)`），因为编辑时目标是"当前上级组织"节点，需要展开到能看见它、但不展开它自己。
  - 之所以两种模式规则不完全统一，是因为"新增"没有一个已存在的"目标节点"，语义上是"只看顶级"，等价于把虚拟根当成需要展开、但本身没有更上层祖先的特例。
- **强制重新应用展开状态**：Element Plus 的 `el-tree`（`el-tree-select` 内部）只在初始挂载时读取 `default-expanded-keys`，此后是非响应式的。弹窗组件本身不会因为切换新增/编辑而重新挂载（`el-dialog` 内容常驻），因此用一个每次打开弹窗自增的 `treeSelectRenderKey` 绑定到 `el-tree-select` 的 `:key`，强制组件在每次打开时重新挂载，从而让新的 `default-expanded-keys` 生效。
  - 备选方案：调用 `treeRef.value?.store` 内部 API 手动展开/收起节点。放弃，因为需要维护的展开状态与收起状态更多、更容易和 Element Plus 内部实现细节耦合；`:key` remount 更简单可靠。
- **展开路径基于 `editableTreeSelectData`**：而不是原始 `orgStore.tree`，因为编辑模式下选择器实际渲染的数据已经被 `pruneSubtree` 处理过（去掉了自身和子孙），路径计算要在同一份渲染数据上进行，否则算出的 key 在树里找不到对应节点。

## Risks / Trade-offs

- [新增模式与编辑模式的展开规则不完全对称（新增是 `[0]`，编辑是祖先路径去掉自身）] → 这是虚拟根节点本身没有"更上一级祖先"导致的，属于预期内的特例，已在 Decisions 中说明，不做进一步统一。
- [`:key` remount 方式在弹窗打开的瞬间会重新创建 `el-tree-select` DOM] → 影响可忽略：弹窗刚打开、组件尚未有可感知的过渡动画依赖，且已有 `check-strictly`、`node-key` 等 props 数据驱动，remount 不影响选中值（`v-model="form.parentId"` 已经在 remount 前设置好）。
