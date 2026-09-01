## Context

### 1. 发起单聊卡死

`frontend/src/stores/chat.ts` 里 `startNewSingleChat` 的实现：

```ts
function startNewSingleChat(toUserId: number, content: string, msgType = 1): Promise<number> {
  return new Promise((resolve, reject) => {
    const msgId = ensureSocket().sendSingle(toUserId, content, msgType)
    pendingSingleCreations.set(msgId, { resolve, reject })
  })
}
```

这个 Promise 只在两处被 settle：

- `handleAck`：收到匹配 `msgId` 的 `ACK` 帧 → `resolve(body.conversationId)`
- `handleSendFailed`：`ChatSocketClient` 内部 ACK 超时重发 `MAX_AUTO_RETRIES`
  次仍未收到 ACK → `reject(new Error('消息发送失败，请重试'))`

但 `ChatSocketClient.handleMessage` 收到 `ChatFrameType.ERROR` 帧时（对应
`ChatBusinessHandler` 各类业务校验失败，如目标用户不存在、内容为空、消息被限流等）
只做了两件事：

```ts
case ChatFrameType.ERROR: {
  const body = frame.body as ErrorFrameBody
  if (body.msgId) this.resolvePending(body.msgId)
  this.options.onError?.(body)
  break
}
```

`resolvePending(body.msgId)` 只是清掉 `ChatSocketClient` 内部的 ACK 超时重发定时器
（避免继续无意义重发一个服务端已经明确拒绝的消息），不涉及 `chat.ts` 里的
`pendingSingleCreations`。`onError` 对应 `chat.ts` 的 `handleError`：

```ts
function handleError(body: ErrorFrameBody): void {
  ElMessage.error(body.message || '聊天服务出现异常')
}
```

只弹提示，没有查 `pendingSingleCreations`。结果：业务 ERROR 路径下
`startNewSingleChat` 的 Promise 永远不 settle，`StartSingleChatDialog.vue` 的
`submit()`：

```ts
async function submit() {
  ...
  submitting.value = true
  try {
    const conversationId = await chatStore.startNewSingleChat(form.value.userId, form.value.content)
    ...
  } finally {
    submitting.value = false
  }
}
```

`await` 永远不返回，`finally` 永远不执行，"发送"按钮的 `:loading="submitting"`
永远为 `true`。

### 2. 权限模块中文名

`src/utils/permissionTree.ts` 的 `PERMISSION_MODULE_LABELS` 是唯一的"模块编码前缀
→ 中文名"映射表，`resolvePermissionModuleLabel` 兜底返回原始编码前缀。`Chat`/
`SensitiveWordManagement` 两个模块（`V2__create_chat_tables.sql` 新增）落地时漏加
了这两条映射。

`RoleDetailView.vue` 现有实现：

```ts
const groupedPermissions = computed(() => {
  const groups = new Map<string, { moduleName: string; items: { id: number; name: string }[] }>()
  for (const permission of detailData.value?.permissions ?? []) {
    const moduleName = permission.code.split(':')[0] || permission.code
    ...
  }
  return Array.from(groups.values())
})
```

直接用 `code.split(':')[0]` 作为展示名，完全没有经过 `resolvePermissionModuleLabel`
——这是三处展示权限点模块分组的位置（权限点管理页面、角色新增/编辑弹窗、角色详情页）
里唯一一处没有复用共享工具 `buildPermissionTree` 的，即使补全映射表，这个页面依旧会
对所有模块显示英文。

## Goals / Non-Goals

**Goals:**
- 发起单聊在服务端返回业务 ERROR 帧时，弹窗的 loading 态与表单状态和"ACK 超时失败"
  路径表现一致：按钮恢复可点击，弹窗可继续编辑重试或关闭。
- 补全 `PERMISSION_MODULE_LABELS`，让 `Chat`/`SensitiveWordManagement` 在已经复用
  共享工具的位置（权限点管理页面、角色新增/编辑弹窗）正确显示中文。
- 角色详情页改为复用共享分组工具，行为与另外两处完全一致；今后任何新增权限模块只需
  要维护 `PERMISSION_MODULE_LABELS` 一处映射，三个展示位置自动同步，不需要分别改
  三份代码。

**Non-Goals:**
- 不改变 `ChatBusinessHandler`/服务端 ERROR 帧的触发条件或消息内容。
- 不改变 `handleSendFailed`（ACK 超时判定失败）现有逻辑与提示文案。
- 不引入"发起单聊失败后自动重试"之类的新交互；用户仍需手动点击"发送"重试或"取消"
  关闭弹窗。
- 不改变 `sendToConversation`/`retrySend`（向已存在会话发送消息）的错误处理——这两个
  路径不经过 `pendingSingleCreations`，本来就不受这个 bug 影响，本次不涉及。
- 不改动 `权限资源.txt`（其中"聊天""敏感词管理"两个模块中文名已经登记正确，问题出在
  前端映射表没有同步，不是登记文件本身有误）。

## Decisions

### 1. `handleError` 增加对 `pendingSingleCreations` 的 reject 分支

在现有 `ElMessage.error` 提示之前或之后，插入与 `handleSendFailed` 对称的查找+
reject 逻辑：

```ts
function handleError(body: ErrorFrameBody): void {
  if (body.msgId) {
    const creation = pendingSingleCreations.get(body.msgId)
    if (creation) {
      pendingSingleCreations.delete(body.msgId)
      creation.reject(new Error(body.message || '发起单聊失败'))
    }
  }
  ElMessage.error(body.message || '聊天服务出现异常')
}
```

**为什么不改用 `Promise.race` 加超时兜底**：ERROR 帧本身就是服务端明确的终态信号
（不像"迟迟收不到 ACK"那样需要靠客户端超时猜测），有明确信号时就地 reject 是最直接
的做法，不需要再引入一个独立的超时定时器和与 `ChatSocketClient` 内部 ACK 超时定时
器重复的机制。

**`StartSingleChatDialog.vue` 不需要改动**：`submit()` 里的 `try/finally`（没有
`catch`）已经是本仓库聊天弹窗与其它表单弹窗的既有写法（`CreateGroupDialog.vue`
同构），reject 后 `finally` 正常执行复位 `submitting`，未处理的 rejection 只在
控制台产生一条不影响功能的警告——这与现有 `chatApi.createGroupConversation` 走
axios 拦截器统一 toast 后 reject 的既有模式一致，不需要额外补 `.catch`。

### 2. `PERMISSION_MODULE_LABELS` 直接补两条映射，不改数据结构

```ts
export const PERMISSION_MODULE_LABELS: Record<string, string> = {
  ...
  Chat: '聊天',
  SensitiveWordManagement: '敏感词管理',
}
```

顺序放在现有列表末尾，与 `权限资源.txt` 里"聊天""敏感词管理"两个模块标题保持字面
一致。

### 3. `RoleDetailView.vue` 改用共享 `buildPermissionTree`

```ts
import { buildPermissionTree, resolvePermissionModuleLabel } from '@/utils/permissionTree'

const groupedPermissions = computed(() =>
  buildPermissionTree(detailData.value?.permissions ?? [], resolvePermissionModuleLabel),
)
```

模板相应改为消费 `PermissionTreeNode`（`id`/`label`/`children`）而不是原来的
`{moduleName, items}`：

```html
<div v-for="group in groupedPermissions" :key="group.id" class="role-permission-group">
  <span class="role-permission-group__name">{{ group.label }}</span>
  <div class="role-permission-group__tags">
    <el-tag v-for="item in group.children" :key="item.id" class="role-permission-tag">
      {{ item.label }}
    </el-tag>
  </div>
</div>
```

`PermissionOption`（`detailData.permissions` 的元素类型：`id`/`name`/`code`）已经
满足 `buildPermissionTree` 要求的 `PermissionTreeSource` 约束，不需要额外类型转换。

**为什么不是给 `RoleDetailView.vue` 自己的 Map 也查一遍
`PERMISSION_MODULE_LABELS`**：那样虽然能解决本次 Chat/SensitiveWordManagement 两个
模块的问题，但依然是第三份重复的分组实现，不满足用户提出的"以后英文都需要用中文
展示"这条约束——约束要落地成"只有一处分组算法+一份映射表"，否则后续新增模块时这
三个位置很容易再次出现新的不一致。

## Risks / Trade-offs

- [`RoleDetailView.vue` 改用 `buildPermissionTree` 后分组顺序变化] →
  `buildPermissionTree` 按传入 `items` 的原始出现顺序分组（不重新排序），
  `detailData.value.permissions` 本身的顺序由后端 `GET /api/roles/{id}` 决定，
  与原实现（同样按 `permissions` 原始顺序 `Map` 分组）顺序一致，不存在实际差异。
- [`handleError` 里 `pendingSingleCreations` 查找增加了状态耦合] →
  与 `handleSendFailed` 完全对称的写法，`chat.ts` 里已有该先例，不引入新概念。
- [ERROR 帧的 `msgId` 为 `null` 的场景（如认证阶段之前的错误）] →
  `if (body.msgId)` 判空后才查找，与 `ChatSocketClient.handleMessage` 里
  `if (body.msgId) this.resolvePending(body.msgId)` 的判空方式一致，不会误查。

## Migration Plan

1. `chat.ts` 的 `handleError` 补上 `pendingSingleCreations` reject 分支。
2. `permissionTree.ts` 补两条模块中文名映射。
3. `RoleDetailView.vue` 改用共享 `buildPermissionTree`/`resolvePermissionModuleLabel`，
   同步调整模板与相关样式类名（如需要）。
4. 前端 `npm run build`（`vue-tsc` 类型检查 + `vite build`）确认无类型错误。
5. 手动验证：
   - 发起单聊时故意触发一次服务端业务 ERROR（如向不存在的用户 id 发送，或复用现有
     校验规则），确认弹窗按钮恢复可点击、可重新填写提交或点击"取消"关闭。
   - 角色管理 → 权限点管理页面 / 角色新增或编辑弹窗 / 角色详情页三处分别查看包含
     Chat、SensitiveWordManagement 模块权限点的分组标签，确认三处均显示"聊天"
     "敏感词管理"而非英文原文。
6. 更新 `openspec/specs/chat-messaging/spec.md`、
   `openspec/specs/role-management/spec.md`（按 proposal.md 的 Capabilities 描述）。
