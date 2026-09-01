## Why

两个独立但都已在现网复现的小缺陷：

1. **发起单聊报错后前端卡死**：`StartSingleChatDialog.vue` 的 `submit()` 调用
   `chatStore.startNewSingleChat(...)`，该方法返回一个只在收到对应 `msgId` 的 ACK
   帧（`chat.ts` 的 `handleAck`）或判定发送失败（`handleSendFailed`，对应 ACK 超时
   重发耗尽）时才会 settle 的 Promise。但服务端对"发起单聊"这类业务校验失败（如
   `ChatBusinessHandler` 校验不通过）回复的是携带同一 `msgId` 的 **ERROR 帧**，
   `chat.ts` 的 `handleError` 目前只弹出一次 `ElMessage.error` 提示，完全没有查找
   `pendingSingleCreations` 并 reject 对应 Promise——`startNewSingleChat` 返回的
   Promise 因此永远不会 settle，`submit()` 里 `await` 之后的 `finally { submitting.value
   = false }` 永远不会执行，弹窗的"发送"按钮从此卡在 loading 转圈状态，且用户此时也
   无法再关闭弹窗重新发起（`close()`/`reset()` 不会重置 `submitting`），聊天入口单聊
   功能实质上被锁死，需要刷新整个页面才能恢复。
2. **权限点模块名英文回退**：权限点管理页面（`PermissionManagementView.vue`）与角色
   新增/编辑弹窗的权限点勾选树（`RoleManagementView.vue`）已经按 `permission-management`
   / `role-management` 两份 spec 的既有约束，通过共享工具 `buildPermissionTree` +
   `resolvePermissionModuleLabel`（`src/utils/permissionTree.ts`）把权限编码第一段
   模块名解析为中文分组名展示；但该工具内的 `PERMISSION_MODULE_LABELS` 映射表还没有
   补充 `Chat`（聊天模块，见 `V2__create_chat_tables.sql`/`权限资源.txt`）与
   `SensitiveWordManagement`（敏感词管理模块）两个模块的中文名，命中不了映射表的
   "未登记模块兜底展示原始编码前缀"分支，导致这两个模块在权限点管理页面、角色新增/
   编辑弹窗里都直接显示英文原文。另外，角色详情页（`RoleDetailView.vue`）"已分配权限
   点"分组展示压根没有复用这份共享工具，而是自己内联了一份不查中文名映射表、只按
   `code.split(':')[0]` 分组的重复逻辑——这意味着即使把映射表补全，角色详情页依然会
   对**所有**模块（不只是 Chat/SensitiveWordManagement）一直显示英文模块名，是本次
   要求"以后英文都需要用中文展示"这条约束实际落地时必须堵上的一个实现缺口。

## What Changes

- `frontend/src/stores/chat.ts`：`handleError` 增加按 `body.msgId` 查找
  `pendingSingleCreations`，命中则 `reject` 该 Promise（错误信息取 `body.message`，
  找不到则退化为通用提示），并从 Map 中移除；使 `StartSingleChatDialog.vue` 的
  `submit()` 无论服务端以 ACK 超时（`handleSendFailed`）还是业务 ERROR 帧
  （`handleError`）判定发起单聊失败，都能在 `finally` 里正确复位 `submitting`，
  弹窗恢复可用（可重新填写、重新提交或关闭）。不改变 `handleError` 现有的全局错误
  提示行为，只是补上遗漏的 Promise 结果分支。
- `frontend/src/utils/permissionTree.ts`：`PERMISSION_MODULE_LABELS` 补充
  `Chat: '聊天'`、`SensitiveWordManagement: '敏感词管理'` 两条映射，与
  `权限资源.txt` 登记的模块中文名保持一致。
- `frontend/src/views/permission/role/RoleDetailView.vue`：`groupedPermissions`
  改为直接调用共享的 `buildPermissionTree(detailData.value?.permissions ?? [],
  resolvePermissionModuleLabel)`，删除自己内联的重复分组逻辑；模板改用
  `PermissionTreeNode` 返回的 `label`/`children`/`raw` 字段渲染（分组名、叶子权限点
  名称）。这样角色详情页与权限点管理页面、角色新增/编辑弹窗共用同一份分组算法与同一份
  中文名映射表，新增模块只需要维护 `PERMISSION_MODULE_LABELS` 一处，三个位置自动保持
  一致——即是本次要新增的约束的落地方式。
- `openspec/specs/chat-messaging/spec.md`：新增一条 Requirement，明确"客户端发起单聊
  等待结果的 Promise/加载态必须在收到 ACK、ACK 超时判定失败、或收到匹配 `msgId` 的
  ERROR 帧三种终态路径下都被正确 settle/复位"，防止未来再次出现只处理其中一两种终态
  导致 UI 卡死的回归。
- `openspec/specs/role-management/spec.md`：把现有"角色权限点勾选树的模块标签展示"
  Requirement 的适用范围从"角色新增/编辑弹窗"扩展到"角色详情页已分配权限点展示"，
  三处（权限点管理页面、角色新增/编辑弹窗、角色详情页）统一要求复用同一份共享分组
  工具与中文名映射表，不允许各自实现一份不查表的分组逻辑。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `chat-messaging`：新增"发起单聊等待结果的客户端状态复位"Requirement。
- `role-management`：扩展"角色权限点勾选树的模块标签展示"Requirement 的适用范围至
  角色详情页。

## Impact

- **前端**：`stores/chat.ts`、`utils/permissionTree.ts`、
  `views/permission/role/RoleDetailView.vue` 三个文件的小范围改动，不涉及路由、
  接口契约或数据库变更。
- **后端**：无改动。
- **兼容性**：纯前端行为修正与文案展示修正，不影响任何已保存的数据或已有权限编码；
  `权限资源.txt` 无需修改（模块中文名已在其中登记为"聊天""敏感词管理"，本次只是让
  前端映射表与它保持一致）。
