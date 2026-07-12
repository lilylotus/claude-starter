## Context

组织管理页面（`views/identity/org/OrgManagementView.vue`）目前有两处用到组织树数据，都来自同一个 `orgStore.tree`（由 `GET /api/orgs/tree` 一次性返回完整嵌套树）：
1. 左侧导航 `el-tree`，`default-expand-all` 全部展开，点击节点驱动右侧分页表格。
2. 新增/编辑弹窗里的"上级组织" `el-tree-select`（`treeSelectData` 在其外面包一层虚拟"顶级组织"根节点），编辑时用 `pruneSubtree` 排除自身及子孙节点。

本次改动只调整第 1 处（左侧导航树）为懒加载，第 2 处（弹窗选择器）保持一次性全量加载不变——已和用户确认：弹窗需要一次性看到完整层级才能正确做"排除自身及子孙节点"的校验，懒加载会让这个校验在节点未展开时无法可靠判断。

## Goals / Non-Goals

**Goals:**
- 左侧组织树默认全部收起；点击某个节点展开时，才向后端请求该节点的直属子组织并渲染，不再一次性拉取/渲染整棵树。
- 新增一个不分页的直属子组织查询接口，专门给树懒加载使用，和现有"分页版" `GET /api/orgs/children`（供右侧表格用）区分开。
- 任意增删改操作后，左侧树需要正确反映最新数据（名称变化、状态变化、新增/删除子节点），且尽量不丢失用户已经展开的层级状态。
- 操作列按钮间距调小。

**Non-Goals:**
- 不改动新增/编辑弹窗里"上级组织"选择器的加载方式，它继续用 `GET /api/orgs/tree` 全量加载。
- 不在懒加载响应里预先计算"是否有子节点"（`hasChildren`）来避免"展开后发现没有子节点"的空箭头闪烁；本次不引入这个预计算，接受这个次要的 UX 折衷（见 Risks）。
- 不改动右侧表格分页查询接口 `GET /api/orgs/children`（保持不变，服务于表格）。

## Decisions

### 1. 新增专门的树懒加载接口，不复用分页版 `GET /api/orgs/children`
- 新增 `GET /api/orgs/tree/children?parentId={id}`（`parentId` 缺省为 `0`），返回 `List<OrgTreeNodeVO>`（复用已有的 `id`/`name`/`code`/`parentId`/`status`/`showOrder`/`children` 字段结构，`children` 固定返回空数组，由前端后续懒加载时再填充），不分页、不做 `parentName` 等审计字段解析（这些字段树上不需要）。
- `OrgServiceImpl` 内部复用与 `getChildren`/`listAllUndeletedOrdered` 相同的过滤排序条件（排除逻辑删除、`showOrder` 降序 + `id` 升序），只是不做 `Page` 包装、直接返回列表。
- 理由：分页版 `GET /api/orgs/children` 默认每页 10 条，是为右侧表格设计的；如果某个组织的直属子组织超过 10 个，树懒加载复用这个接口会导致树上只显示前 10 个子节点，语义上也容易和"表格分页状态"混淆。新增一个语义清晰、不分页的独立接口更稳妥。

### 2. `orgStore` 拆分两套树状态：`tree`（弹窗全量树，不变）与懒加载的左侧导航树状态
- 保留 `fetchTree()`/`tree` 不变，继续给弹窗的"上级组织"选择器用。
- 新增左侧导航树自己的响应式数据结构和 `loadTreeNode(node, resolve)` 方法：`node.level === 0` 时请求 `parentId = 0`（顶级），否则用 `node.data.id` 作为 `parentId`，调用新增的树懒加载接口，`resolve(children)` 交给 `el-tree`。
- 理由：两棵树的数据形态和用途都不同（一个懒加载、稀疏；一个全量、稠密且需要支持 prune），硬塞进同一个状态字段会让两处消费者的语义互相干扰。

### 3. 增删改后的左侧树刷新：只刷新受影响节点的子节点，而不是整棵树重新加载
- `el-tree` 组件实例上调用 `treeRef.value.updateKeyChildren(parentId, freshChildren)`（Element Plus 官方为懒加载树提供的"局部刷新某个 key 的子节点"方法），`freshChildren` 从树懒加载接口重新拉取受影响的 `parentId`（即 `orgStore.currentParentId`，与右侧表格当前查询的父级一致）得到。
- 理由：如果增删改之后简单粗暴地把整棵左侧树重新渲染（比如给 `el-tree` 换一个新的 `:key` 强制重建），会导致用户已经展开的所有层级全部收起，每次操作后都要重新一层层点开，体验很差；`updateKeyChildren` 是 Element Plus 官方文档里专门为这个场景（懒加载树 + 局部数据变更）提供的 API，只刷新目标节点下一层，不影响其他已展开分支。
- 备选方案：整树强制重建（`:key` 递增）——实现更简单，但代价是每次操作都丢失展开状态，体验倒退，故不采用。

### 4. 不预先计算 `hasChildren`，接受"展开箭头但发现空数据"的次要 UX 折衷
- `el-tree` 在 `lazy` 模式下，如果节点数据没有显式标注是否为叶子（本次不新增这个标注），默认所有非明确叶子节点都会显示展开箭头；用户点开一个实际没有子组织的节点后，箭头会在这次懒加载完成（`resolve([])`）后自动消失。
- 理由：要在懒加载响应之外提前告诉前端"这个节点有没有子节点"，需要后端为每一层批量做一次 `EXISTS`/`COUNT` 子查询，增加接口复杂度和数据库压力；这是一个常见、可接受的懒加载树 UX 折衷（很多中后台系统都是这样处理的），不值得为了消除这个小瑕疵引入额外的后端开销。

### 5. 操作列按钮间距：用 scoped CSS 覆盖 Element Plus 默认的 `.el-button + .el-button` 间距
- 在 `OrgManagementView.vue` 的 `<style scoped>` 里针对操作列按钮组增加一条选择器，把相邻 `el-button`（`link` 类型）之间的 `margin-left` 调小，不引入新的布局容器或组件。

## Risks / Trade-offs

- [Risk] 懒加载树在没有 `hasChildren` 预标注的情况下，用户点开一个实际无子组织的节点会先看到展开箭头、点开后才发现是空的 → Mitigation：这是本次决策 4 里明确接受的次要 UX 折衷，不额外处理。
- [Risk] `updateKeyChildren` 依赖用户已经展开过目标节点（该 key 存在于树的内部 store 里）；如果受影响的 `parentId` 对应的节点从未被展开过（比如用户没点开过它就直接对其子节点做了增删改——这种情况只会发生在通过右侧表格间接操作时，因为右侧表格显示的正是"当前选中/顶级"的直属子组织，其父节点必然已经在左侧树上处于展开或者是顶级根这一层，属于已加载状态）→ 这个场景在当前交互流程下不会出现，暂不做额外兜底。
- [Risk] 新增的树懒加载接口和现有 `GET /api/orgs/tree`、`GET /api/orgs/children` 三个接口并存，未来维护者可能混淆三者用途 → Mitigation：接口路径和 Swagger `@Operation` 描述里明确各自的用途边界（全量树 / 分页表格 / 树懒加载）。

## Open Questions

无。
