## Context

`RoleManagementView.vue` 新增/编辑角色弹窗内的权限点勾选树是一个 `el-tree`（`show-checkbox` + `node-key="id"`），此前带有 `default-expand-all`，弹窗一打开就会把全部分组及权限点节点展开。权限点数量随模块增多而增长后，弹窗内会出现一长串展开节点，用户需要滚动很久才能找到目标权限点，与权限点管理页面自身的权限点树（未设置 `default-expand-all`）的默认行为也不一致。

`UserManagementView.vue` 的用户列表表格里，除固定的"状态"列和"操作"列外，其余列（含"备注"）由 `userFields.listColumns` 动态渲染，每列直接把 `row[col.columnName]` 原样输出为文本，没有任何长度限制。备注字段（`tab_user.remark`，`VARCHAR(255)`）允许填入较长文本，一旦某一行备注较长，`el-table` 该列会被撑宽，进而挤压其他列的可视宽度，破坏整个列表的布局。

## Goals / Non-Goals

**Goals:**
- 权限点勾选树弹窗打开时默认全部收起（不展开任何层级），已勾选的权限点节点即使处于收起的父节点下也要正确保持勾选状态（`el-tree` 的 `checked-keys`/`setCheckedKeys` 行为与节点是否展开无关，天然满足）。
- 用户列表"备注"列文本超过 6 个字符时截断并追加省略号，不撑宽表格行；悬停时可通过 tooltip 查看完整内容。

**Non-Goals:**
- 不改动其余动态列（姓名、编号、性别等）的展示逻辑，只针对 `columnName === 'remark'` 特判。
- 不改动角色详情页、权限点管理页面权限点树的展开行为（后者本来就没有 `default-expand-all`，无需改动）。
- 不引入"记住上次展开状态"之类的持久化交互，弹窗每次打开都是全部收起的初始状态。

## Decisions

- **权限点树默认收起的实现方式**：直接删除 `el-tree` 上的 `default-expand-all` 属性，不额外传 `default-expanded-keys`。`el-tree` 在两者都缺省时默认所有节点收起，只展示第一层分组节点；不需要新增任何 `computed`/响应式状态。
  - 备选方案（未采用）：只默认展开第一层分组节点——用户诉求是"默认收起所有权限点列表"，第一层默认展开不满足这个诉求，故不采用。

- **备注列截断 + tooltip 的实现方式**：在动态列的 `#default` 插槽里，针对 `col.columnName === 'remark'` 且当前值长度大于 6 的情况，用 `el-tooltip`（`content` 传完整文本，`placement="top"`）包裹一个 `<span class="user-cell--truncate">`，该 class 用 CSS 做 `max-width: 6em; overflow: hidden; white-space: nowrap; text-overflow: ellipsis;` 定宽截断；未超过 6 个字符或非备注列时仍走原来的纯文本 `<span>` 分支，不引入 tooltip 开销。
  - 备选方案（未采用）：用 JS 字符串截断（如 `slice(0, 6) + '...'`）替换单元格内容——这样悬停时看不到完整原文，且截断逻辑和展示逻辑耦合在一起，不如 CSS `text-overflow: ellipsis` + `el-tooltip` 展示原文清晰；后者是 Element Plus 项目里处理超长表格单元格的惯用做法。
  - 备选方案（未采用）：给动态列统一加 `show-overflow-tooltip`（`el-table-column` 内置属性）——该属性是按渲染后的像素宽度触发省略号，不是"字符数大于 6"这个精确规则，且会对所有动态列生效而不仅是备注列，与用户提出的"字段大于 6 个字符后面隐藏"要求（按字符数而非像素宽度）不完全一致，因此改为手写判断只针对备注列生效。

## Risks / Trade-offs

- [权衡] "大于 6 个字符截断"是按字符长度（`String(...).length`）判断，中英文字符宽度不同，中文备注在视觉上可能比英文备注截断得更早看到省略号——这是用户明确要求的"大于 6 个字符"规则本身的取舍，不做中英文差异化处理。
- [权衡] 该截断规则通过 `columnName === 'remark'` 硬编码，不是动态列的通用能力；如果后续别的动态字段也需要类似截断，需要在配置里显式声明哪些列需要截断，而不是复用本次实现。
