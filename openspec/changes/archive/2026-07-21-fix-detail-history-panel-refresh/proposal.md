## Why

用户反馈："编辑后查看详情，操作历史记录没有自动更新，看不到刚刚那次操作的记录"。排查确认后端写日志是同步操作、查询参数与索引均无问题，问题出在前端：承载 `OperationHistoryPanel` 的各业务"详情"弹窗未加 `destroy-on-close`，"打开详情 → 关闭 → 编辑保存 → 再打开同一条记录的详情"这条常见路径下，弹窗组件实例不销毁，`targetId` 数值前后未变，面板内部 `watch(targetId, ..., { immediate: true })` 不会重新触发，历史列表停留在旧数据。需要修复以保证详情弹窗每次重新打开都能看到最新操作历史。

## What Changes

- 给用户、组织、任职、应用、管理员、权限点、角色、菜单资源、字典类型/字典项共 9 个业务管理视图、10 处承载 `OperationHistoryPanel` 的"详情" `el-dialog` 添加 `destroy-on-close` 属性，使弹窗关闭后组件实例销毁，下次打开重新挂载并触发已有的 `watch(() => props.targetId, ..., { immediate: true })` 重新拉取最新历史记录。
- 不涉及后端代码改动，不涉及 `OperationHistoryPanel.vue` 组件自身逻辑改动，不涉及任何"编辑"弹窗。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `user-management`：用户详情弹窗操作历史展示，新增"关闭详情弹窗后重新打开需展示最新操作历史"场景
- `org-management`：组织详情弹窗操作历史展示，新增同上场景
- `position-management`：任职记录详情弹窗操作历史展示，新增同上场景
- `application-management`：应用详情弹窗操作历史展示，新增同上场景
- `admin-management`：管理员详情弹窗操作历史展示，新增同上场景
- `permission-management`：权限点详情弹窗操作历史展示，新增同上场景
- `role-management`：角色详情弹窗操作历史展示，新增同上场景
- `menu-management`：资源详情弹窗操作历史展示，新增同上场景
- `dict-management`：字典类型/字典项详情弹窗操作历史展示，新增同上场景

## Impact

- 前端：以下 9 个文件、10 处详情 `el-dialog` 添加 `destroy-on-close` 属性（均为单行属性改动，未改动其余模板/脚本逻辑）：
  - `frontend/src/views/identity/user/UserManagementView.vue`
  - `frontend/src/views/identity/org/OrgManagementView.vue`
  - `frontend/src/views/identity/position/PositionManagementView.vue`
  - `frontend/src/views/application/app/AppManagementView.vue`
  - `frontend/src/views/permission/admin/AdminManagementView.vue`
  - `frontend/src/views/permission/permission/PermissionManagementView.vue`
  - `frontend/src/views/permission/role/RoleManagementView.vue`
  - `frontend/src/views/system/menu/MenuManagementView.vue`
  - `frontend/src/views/system/dict/DictManagementView.vue`（字典类型详情、字典项详情两处）
- 后端：无改动。
- 共享组件 `frontend/src/components/OperationHistoryPanel.vue`：无改动，仅其宿主容器的挂载/销毁时机发生变化。
- 已通过 `npm run build`（vue-tsc 类型检查 + vite build）验证，无类型错误、构建通过。
