## Context

项目里 13 个主列表分页分成两种既有实现模式：

1. **Pinia store 承载分页状态**（`org`/`user`/`position`/`app`/`menu`/`role`/`admin`/`permission`/`dict` 共 9 个 store，`dict` 内部有两套独立状态）：`page`/`pageSize`/`total` 是 store 内的 `ref`，视图通过 `orgStore.page` 等只读展示，翻页由 `@current-change` 调用 store 暴露的 `changePage(targetPage)` action，action 内部把新页码写回 `page`、调用对应的 `fetchXxx()`，并在结果返回后用后端回传的 `page`/`pageSize` 回写状态。
2. **组件本地状态承载分页状态**（`FormFieldDefinitionPanel.vue`/`OperationLogManagementView.vue`/`ImportFieldConfigPanel.vue`/`MetadataFieldListView.vue` 共 4 个）：`page`/`pageSize` 是 `<script setup>` 里的本地 `ref`，翻页由 `@current-change` 调用本地函数 `handlePageChange(targetPage)`，内部同样是"写回页码 + 调用本地 `fetchXxx()`"。

两种模式都已经有"筛选/搜索后重置到第一页再查询"的先例（如 `org.ts` 的 `selectNode` 调 `fetchChildren(id, 1)`；`OperationLogManagementView.vue` 的 `handleSearch` 先 `page.value = 1` 再 `fetchList()`），新增的"切换每页条数"直接复用同一模式即可，不需要额外设计。

## Goals / Non-Goals

**Goals:**
- 13 处主列表分页均获得一个可用、行为一致的每页条数下拉选择器，默认 10，可选 10/20/50/100。
- 选项列表只在一处维护（`frontend/src/constants/pagination.ts`），后续如需调整可选值只改一个文件。

**Non-Goals:**
- 不改动 `OperationHistoryPanel.vue` 固定 5 条/页的嵌入式操作历史面板——它是详情页里的辅助信息面板，不是主列表，规格已单独约束为固定值，改动需要另开 change 单独评估。
- 不在 `layout` 中加 `jumper`（跳转到第几页的输入框）——用户只要求加每页条数选择器，不引入未被要求的额外控件。
- 不改动后端分页默认值或新增 `pageSize` 上限校验——现状（`defaultValue = "10"`，无上限）已经能满足 10/20/50/100 全部可选值，没有必要为此新增后端改动。

## Decisions

- **共享常量模块而非各处硬编码**：13 处分页如果各自写一份 `[10, 20, 50, 100]` 字面量，后续调整选项（比如想加一个 200）要改 13 处、还容易漏改导致选项不一致；抽成 `frontend/src/constants/pagination.ts` 一次导出，13 处统一 `import`，且用 `DEFAULT_PAGE_SIZE` 替换掉原本分散在 9 个 store + 4 个组件里的 `ref(10)` 字面量，"默认每页 10 条"这件事以后也只需要改一个常量。
  - 备选方案（未采用）：不抽公共常量，每处保留自己的字面量数组——13 处高度重复，维护成本明显更高，否决。
- **`layout` 采用 `"sizes, prev, pager, next, total"`**：把 `sizes`（每页条数选择器）放在 `prev`（上一页）之前，对应用户"在分页前添加分页数据大小"的原话；`total` 保留在原有的最后位置不变，是本次改动对现有视觉顺序的最小调整——只在最前面插入 `sizes`，不挪动其余 token。
- **各处沿用既有的 action/函数命名习惯**：store 版本新增 `changePageSize(newSize)`（与既有 `changePage(targetPage)` 对称命名），本地状态版本新增 `handleSizeChange(newSize)`（与既有 `handlePageChange(targetPage)` 对称命名）；`dict.ts` 因为左右两侧是独立的分页状态（`typesPage`/`itemsPage`），对应拆成 `changeTypesPageSize`/`changeItemsPageSize` 两个 action，而不是一个接受"侧别"参数的通用 action——与 store 里已有的 `typesPage`/`itemsPage` 两两独立成对的命名风格一致，不引入新的参数化模式。
- **切换每页条数的行为**：设置新的 `pageSize`、把 `page` 重置为 `1`、用新的 `page`/`pageSize` 重新发起查询；不保留切换前的页码语义（比如原本第 3 页、每页 10 条，切到每页 20 条后不去换算"原来第 21~30 条现在在第几页"，直接回到第一页）——与项目里"筛选后回到第一页"的既有约定保持一致，也是分页组件切换每页条数的通用做法。

## Risks / Trade-offs

- [风险] 13 处近乎相同的重复编辑，逐处操作容易漏改某一处的 `layout`/`page-sizes`/`size-change` 三者之一，导致选择器不出现或点了没反应——缓解：tasks.md 把每处拆成独立的可勾选子任务，逐一核对三者都改到；最终验证阶段额外要求至少手动打开 2~3 个代表性页面（一个 store 版本、一个本地状态版本、`dict` 双分页）实测点击有效且能正确回到第一页。
- [权衡] `dict.ts` 两个独立 action 而不是一个参数化 action，牺牲了一点点代码复用换取和现有代码风格的一致性；如果后续还有第三个"多分页"场景，可以再考虑要不要抽象。
