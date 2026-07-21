## 1. 根因排查

- [x] 1.1 排查后端 `OperationLogRecorderImpl.recordUpdate`（及 recordCreate/recordStatusChange/recordDelete）调用链，确认写日志为同步操作，Controller 返回前已写完 `tab_operation_log`，不存在异步延迟
- [x] 1.2 核对 `tab_operation_log` 表的 `resource_type + target_id` 索引与前端查询参数、`create_time DESC, id DESC` 排序，确认查询链路与字段均无问题
- [x] 1.3 排查前端 `frontend/src/components/OperationHistoryPanel.vue` 的 `watch(() => props.targetId, ..., { immediate: true })`，确认 `targetId` 数值不变时不会重新触发；进一步定位到根因是承载它的各"详情" `el-dialog` 未加 `destroy-on-close`，导致弹窗关闭后组件实例不销毁、下次打开不会重新挂载

## 2. 修复

- [x] 2.1 给以下 9 个业务管理视图、共 10 处"详情" `el-dialog` 添加 `destroy-on-close` 属性：
  - `frontend/src/views/identity/user/UserManagementView.vue`（用户详情）
  - `frontend/src/views/identity/org/OrgManagementView.vue`（组织详情）
  - `frontend/src/views/identity/position/PositionManagementView.vue`（任职详情）
  - `frontend/src/views/application/app/AppManagementView.vue`（应用详情）
  - `frontend/src/views/permission/admin/AdminManagementView.vue`（管理员详情）
  - `frontend/src/views/permission/permission/PermissionManagementView.vue`（权限详情）
  - `frontend/src/views/permission/role/RoleManagementView.vue`（角色详情）
  - `frontend/src/views/system/menu/MenuManagementView.vue`（资源详情）
  - `frontend/src/views/system/dict/DictManagementView.vue`（字典类型详情、字典项详情两处）

## 3. 验证

- [x] 3.1 运行 `npm run build`（`vue-tsc` 类型检查 + `vite build`），确认无类型错误、构建通过
