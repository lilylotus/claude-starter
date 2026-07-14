## 1. 上级组织选择器默认展开逻辑

- [x] 1.1 在 `OrgManagementView.vue` 中新增 `findAncestorPath(nodes, targetId)` 递归函数，基于 `editableTreeSelectData` 查找目标节点从根到自身的祖先路径
- [x] 1.2 新增 `treeSelectExpandedKeys` computed：新增模式固定返回 `[0]`；编辑模式返回 `findAncestorPath` 结果去掉最后一个元素（即当前上级组织的祖先路径，不含其自身），找不到时兜底返回 `[0]`
- [x] 1.3 新增 `treeSelectRenderKey` ref，在 `openCreateDialog` 与 `openEditDialog` 中各自递增一次
- [x] 1.4 模板中移除 `el-tree-select` 的 `default-expand-all`，改为绑定 `:key="treeSelectRenderKey"` 和 `:default-expanded-keys="treeSelectExpandedKeys"`

## 2. 验证

- [ ] 2.1 手动验证：新增组织时上级组织选择器只展开顶级组织，顶级组织节点本身不展开（当前环境无浏览器自动化工具可用，未实际操作验证，仅代码走查确认逻辑符合预期）
- [ ] 2.2 手动验证：编辑一个多层级组织时，上级组织选择器展开到当前上级组织所在路径，当前上级组织节点本身不展开（同上，未实际操作验证）
- [ ] 2.3 手动验证：连续编辑上级组织不同的两个组织，展开范围随之更新，不残留上一次的展开状态（同上，未实际操作验证）
- [x] 2.4 `npm run build`（`frontend/` 目录）确认类型检查通过
