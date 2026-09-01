## 1. 修复发起单聊报错后前端卡死

- [x] 1.1 `frontend/src/stores/chat.ts` 的 `handleError` 增加按 `body.msgId` 查找
      `pendingSingleCreations` 并 `reject` 的分支（design.md Decision 1），错误信息
      取 `body.message`，找不到时退化为通用提示；保留现有 `ElMessage.error` 提示逻辑
      不变
- [x] 1.2 本地验证：构造一次会触发服务端 ERROR 帧的发起单聊请求（如目标用户 id 不
      存在），确认 `StartSingleChatDialog.vue` 的"发送"按钮不再无限转圈，`finally`
      正常复位 `submitting`，弹窗可继续编辑重试或点击"取消"关闭
- [x] 1.3 回归验证：正常发起单聊（成功路径）与 ACK 超时失败路径（如断开网络后发起）
      两种既有场景表现不受影响

## 2. 补全权限点模块中文名映射

- [x] 2.1 `frontend/src/utils/permissionTree.ts` 的 `PERMISSION_MODULE_LABELS` 增加
      `Chat: '聊天'`、`SensitiveWordManagement: '敏感词管理'` 两条映射（design.md
      Decision 2）
- [x] 2.2 `frontend/src/views/permission/role/RoleDetailView.vue` 的
      `groupedPermissions` 改为调用共享的 `buildPermissionTree`/
      `resolvePermissionModuleLabel`，删除原先内联的 `Map` 分组逻辑；模板同步改用
      `PermissionTreeNode` 的 `id`/`label`/`children` 字段渲染（design.md Decision 3）
- [x] 2.3 本地验证：分别打开权限点管理页面、角色新增或编辑弹窗的权限点勾选树、角色
      详情页"已分配权限点"（需要一个已授予 Chat/SensitiveWordManagement 模块权限点
      的角色），确认三处均展示"聊天""敏感词管理"中文分组名；同时抽查一个已有模块
      （如 `OrgManagement`）确认三处展示不受影响、无回归
- [x] 2.4 `cd frontend && npm run build`（`vue-tsc` 类型检查 + `vite build`）确认无
      类型错误、构建成功

## 3. OpenSpec 规范同步

- [x] 3.1 `openspec/specs/chat-messaging/spec.md` 新增"发起单聊等待结果的客户端状态
      复位"Requirement 及对应 Scenario（proposal.md Modified Capabilities）
- [x] 3.2 `openspec/specs/role-management/spec.md` 扩展"角色权限点勾选树的模块标签
      展示"Requirement，覆盖角色详情页只读展示场景
- [x] 3.3 实现完成后，基于真实 diff 与手动验证结果核对本 change 的
      `proposal.md`/`design.md`/`tasks.md` 与实际实现是否一致：`chat.ts`/
      `permissionTree.ts`/`RoleDetailView.vue` 的实际改动与 design.md Decision 1-3
      描述一致，手动验证（1.2/1.3/2.3）已由用户完成，proposal.md/design.md 无需
      修改
