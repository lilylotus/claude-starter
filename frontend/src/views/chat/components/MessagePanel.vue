<script setup lang="ts">
// 消息收发面板：历史消息滚动到顶部触发向前加载更多（beforeSeq 游标分页）、发送消息
// （含"发送中/已发送/失败重发"状态展示）、离线补偿消息按 conversationSeq 顺序展示
// （chat-messaging spec"聊天菜单与会话/消息界面"/"离线消息补偿推送"需求，tasks.md 6.6）。
import { computed, nextTick, ref, watch } from 'vue'
import { useChatStore, type LocalChatMessage } from '@/stores/chat'
import { CONVERSATION_TYPE_GROUP } from '@/types/chat'

const emit = defineEmits<{
  showMembers: []
}>()

const chatStore = useChatStore()

const scrollContainer = ref<HTMLDivElement>()
// 滚动容器顶部这个像素范围内继续上滑视为"触发加载更多"，避免必须精确滚到 0
const LOAD_MORE_THRESHOLD_PX = 48

async function handleScroll() {
  const el = scrollContainer.value
  const conversationId = chatStore.currentConversationId
  if (!el || conversationId === null) return
  if (el.scrollTop > LOAD_MORE_THRESHOLD_PX) return
  if (chatStore.hasMoreByConversation[conversationId] === false) return
  if (chatStore.loadingMoreConversationId !== null) return

  const previousHeight = el.scrollHeight
  await chatStore.loadMoreMessages(conversationId)
  await nextTick()
  // 顶部插入了更早的消息后，把滚动位置补偿回加载前用户所在的相对位置，避免视觉跳动
  el.scrollTop = el.scrollHeight - previousHeight
}

// 切换会话或首次收到消息时，滚动到底部展示最新消息
function scrollToBottom() {
  nextTick(() => {
    const el = scrollContainer.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

watch(
  () => chatStore.currentConversationId,
  () => scrollToBottom(),
)

watch(
  () => chatStore.currentMessages.length,
  (newLength, oldLength) => {
    // 仅在消息数量增加且不是"向历史翻页"导致的增加时才自动滚到底部：
    // loadingMoreConversationId 非空说明这次增加来自 loadMoreMessages，交给上面 handleScroll 自行处理滚动位置
    if (newLength > oldLength && chatStore.loadingMoreConversationId === null) {
      scrollToBottom()
    }
  },
)

const inputContent = ref('')

function isMine(message: LocalChatMessage): boolean {
  return message.senderId === chatStore.currentUserId
}

const isGroup = computed(() => chatStore.currentConversation?.conversationType === CONVERSATION_TYPE_GROUP)

function send() {
  const content = inputContent.value.trim()
  const conversationId = chatStore.currentConversationId
  if (!content || conversationId === null) return
  chatStore.sendToConversation(conversationId, content)
  inputContent.value = ''
}

// el-input 的 keydown 事件类型声明为 Event | KeyboardEvent（textarea 原生事件类型不够精确），
// 这里收窄为 KeyboardEvent 后再判断按键，避免直接以 KeyboardEvent 声明形参类型报错
function handleKeydown(event: Event | KeyboardEvent) {
  const keyboardEvent = event as KeyboardEvent
  if (keyboardEvent.key === 'Enter' && !keyboardEvent.shiftKey) {
    keyboardEvent.preventDefault()
    send()
  }
}

function retry(message: LocalChatMessage) {
  if (chatStore.currentConversationId === null) return
  chatStore.retrySend(chatStore.currentConversationId, message.msgId)
}
</script>

<template>
  <section class="message-panel">
    <template v-if="chatStore.currentConversation">
      <header class="message-panel__header">
        <h2 class="message-panel__title">{{ chatStore.currentConversation.name }}</h2>
        <el-button v-if="isGroup" size="small" @click="emit('showMembers')">群成员</el-button>
      </header>

      <div ref="scrollContainer" class="message-panel__body" @scroll="handleScroll">
        <div v-if="chatStore.loadingMoreConversationId === chatStore.currentConversationId" class="message-panel__loading-more">
          正在加载更早的消息…
        </div>
        <div
          v-for="message in chatStore.currentMessages"
          :key="message.msgId"
          class="message-bubble-row"
          :class="{ 'message-bubble-row--mine': isMine(message) }"
        >
          <div class="message-bubble">
            <div v-if="isGroup && !isMine(message)" class="message-bubble__sender">{{ message.senderName }}</div>
            <div class="message-bubble__content">{{ message.content }}</div>
            <div class="message-bubble__meta">
              <span>{{ message.sendTime }}</span>
              <span v-if="isMine(message) && message.status === 'sending'" class="message-bubble__status">发送中…</span>
              <span v-else-if="isMine(message) && message.status === 'failed'" class="message-bubble__status message-bubble__status--failed">
                发送失败
                <el-link type="danger" :underline="false" @click="retry(message)">重发</el-link>
              </span>
            </div>
          </div>
        </div>
        <el-empty v-if="chatStore.currentMessages.length === 0" description="暂无消息，说点什么吧" />
      </div>

      <footer class="message-panel__footer">
        <el-input
          v-model="inputContent"
          type="textarea"
          :rows="3"
          placeholder="按 Enter 发送，Shift+Enter 换行"
          @keydown="handleKeydown"
        />
        <el-button type="primary" class="message-panel__send" @click="send">发送</el-button>
      </footer>
    </template>
    <el-empty v-else class="message-panel__placeholder" description="请选择左侧会话开始聊天" />
  </section>
</template>

<style scoped lang="scss">
.message-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-width: 0;
}

.message-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.message-panel__title {
  font-size: 15px;
  color: var(--color-ink);
  margin: 0;
}

.message-panel__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 4px 4px 4px 0;
}

.message-panel__loading-more {
  text-align: center;
  font-size: 12px;
  color: var(--color-text-tertiary);
  padding: 4px 0 12px;
}

.message-panel__placeholder {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.message-bubble-row {
  display: flex;
  margin-bottom: 12px;

  &--mine {
    justify-content: flex-end;
  }
}

.message-bubble {
  max-width: 60%;
  background: var(--color-surface-raised);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 8px 12px;

  .message-bubble-row--mine & {
    background: var(--color-primary-soft);
    border-color: var(--color-primary-soft);
  }
}

.message-bubble__sender {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-bottom: 2px;
}

.message-bubble__content {
  font-size: 14px;
  color: var(--color-text);
  white-space: pre-wrap;
  word-break: break-word;
}

.message-bubble__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  font-size: 11px;
  color: var(--color-text-tertiary);
}

.message-bubble__status--failed {
  color: var(--color-danger);
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.message-panel__footer {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
}

.message-panel__send {
  align-self: stretch;
}
</style>
