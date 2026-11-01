## 1. 共享构树工具

- [x] 1.1 改写 `frontend/src/utils/permissionTree.ts` 的 `buildPermissionTree`：以 `MENU_GROUPS` 为数据源构造"一级菜单 → 二级页面 → 权限点"三层树，按完整编码 / 前两段 / 模块段 / "其他"四级规则归属，过滤空页面与空一级菜单，节点带 `level` 与叶子数量 `count`
- [x] 1.2 保留 `PERMISSION_MODULE_LABELS` 与 `resolvePermissionModuleLabel` 导出，用于"其他"分组的模块中文名

## 2. 页面适配

- [x] 2.1 `PermissionManagementView.vue`：调用新签名；一级/二级节点展示名称与数量角标；"全部展开"展开全部虚拟节点
- [x] 2.2 `RoleManagementView.vue`：权限点勾选树改用三层树，确认默认收起、勾选回显与只提交数字 id 的逻辑不变
- [x] 2.3 `RoleDetailView.vue`：已分配权限点按"一级菜单 → 二级页面 → 权限点标签"只读展示，保持链式视觉语言

## 3. 验证

- [x] 3.1 `npm run build`（vue-tsc 类型检查 + vite build）通过
- [x] 3.2 用当前 151 个权限编码核对归属结果：无权限点丢失，"其他"分组只含 `PluginManagement:plugin:view`
- [x] 3.3 启动前后端，在浏览器中检查权限点管理页、角色新增/编辑弹窗、角色详情页的三层展示与勾选提交（勾选联动与回显已验证；未实际点击"确定"提交，避免修改本地角色数据，提交逻辑未改动）

## 4. OpenSpec 收尾

- [x] 4.1 依据实际实现与验证结果更新本 change 的 proposal/design/tasks
- [x] 4.2 确认 `权限资源.txt` 无需修改（不新增/删除菜单与按钮）
