## 1. 权限点勾选树默认全部收起

- [x] 1.1 `RoleManagementView.vue`：移除权限点勾选树 `el-tree` 上的 `default-expand-all` 属性
- [x] 1.2 确认未额外绑定 `default-expanded-keys`，树默认全部收起、只展示第一层分组节点
- [x] 1.3 确认编辑已有角色时，弹窗打开后已勾选的权限点仍正确回显勾选状态（不受节点收起影响）

## 2. 用户列表备注列截断 + tooltip

- [x] 2.1 `UserManagementView.vue`：动态列渲染分支中新增 `columnName === 'remark'` 且内容长度大于 6 时的 `el-tooltip` 分支，`content` 传完整备注文本
- [x] 2.2 新增 `.user-cell--truncate` 样式（`max-width: 6em; overflow: hidden; white-space: nowrap; text-overflow: ellipsis;`），应用在截断分支的 `<span>` 上
- [x] 2.3 确认非备注列、以及备注长度不超过 6 个字符时仍走原有纯文本展示分支，不受影响

## 3. 验证

- [x] 3.1 `npx vue-tsc --noEmit -p tsconfig.app.json` 通过，无类型错误
- [x] 3.2 本地启动前端，打开角色管理新增/编辑弹窗，确认权限点勾选树默认全部收起，且编辑已有角色时已勾选权限点正确回显
- [x] 3.3 打开用户管理列表，确认备注超过 6 个字符的行被截断为省略号、不再撑宽表格行，鼠标悬停时 tooltip 展示完整备注内容；备注为空或不超过 6 个字符的行展示不受影响
