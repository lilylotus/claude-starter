<script setup lang="ts">
// 会话列表面板：单聊+群聊混排，显示最近一条消息摘要与时间，按最近消息时间倒序
// （chat-messaging spec"聊天菜单与会话/消息界面"需求，tasks.md 6.5）。
import { computed } from 'vue'
import { useChatStore } from '@/stores/chat'
import { usePermission } from '@/composables/usePermission'
import { CONVERSATION_TYPE_GROUP, type ConversationVO } from '@/types/chat'

const emit = defineEmits<{
  startSingleChat: []
  createGroup: []
}>()

const chatStore = useChatStore()
const { hasPermission } = usePermission()
const canCreateGroup = computed(() => hasPermission('Chat:conversation:create'))

function summaryText(conversation: ConversationVO): string {
  if (!conversation.lastMessageContent) return '暂无消息'
  return conversation.lastMessageContent
}

async function selectConversation(conversation: ConversationVO) {
  await chatStore.selectConversation(conversation.id)
}
</script>

<template>
  <section class="conversation-list">
    <header class="conversation-list__header">
      <h2 class="conversation-list__title">会话列表</h2>
      <div class="conversation-list__actions">
        <el-button size="small" @click="emit('startSingleChat')">发起单聊</el-button>
        <el-button v-if="canCreateGroup" size="small" type="primary" @click="emit('createGroup')">
          发起群聊
        </el-button>
      </div>
    </header>

    <el-scrollbar class="conversation-list__body">
      <el-empty v-if="!chatStore.conversationsLoading && chatStore.conversations.length === 0" description="暂无会话，发起一次聊天试试" />
      <ul v-loading="chatStore.conversationsLoading" class="conversation-list__items">
        <li
          v-for="conversation in chatStore.conversations"
          :key="conversation.id"
          class="conversation-item"
          :class="{ 'conversation-item--active': conversation.id === chatStore.currentConversationId }"
          @click="selectConversation(conversation)"
        >
          <div class="conversation-item__avatar">
            <el-avatar :size="36">{{ conversation.name?.slice(0, 1) || '?' }}</el-avatar>
            <el-tag v-if="conversation.conversationType === CONVERSATION_TYPE_GROUP" size="small" class="conversation-item__type-tag">
              群
            </el-tag>
          </div>
          <div class="conversation-item__main">
            <div class="conversation-item__row">
              <span class="conversation-item__name">{{ conversation.name }}</span>
              <span class="conversation-item__time">{{ conversation.lastMessageSendTime ?? '' }}</span>
            </div>
            <div class="conversation-item__row">
              <span class="conversation-item__summary">{{ summaryText(conversation) }}</span>
              <el-badge
                v-if="chatStore.unreadCounts[conversation.id]"
                :value="chatStore.unreadCounts[conversation.id]"
                :max="99"
              />
            </div>
          </div>
        </li>
      </ul>
    </el-scrollbar>
  </section>
</template>

<style scoped lang="scss">
.conversation-list {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-width: 0;
}

.conversation-list__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  gap: 8px;
}

.conversation-list__title {
  font-size: 15px;
  color: var(--color-ink);
  margin: 0;
  white-space: nowrap;
}

.conversation-list__actions {
  display: flex;
  gap: 8px;
}

.conversation-list__body {
  flex: 1;
  min-height: 0;
}

.conversation-list__items {
  list-style: none;
  margin: 0;
  padding: 0;
}

.conversation-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 8px;
  border-radius: var(--radius-sm);
  cursor: pointer;

  &:hover {
    background: var(--color-primary-softer);
  }

  &--active {
    background: var(--color-primary-soft);
  }
}

.conversation-item__avatar {
  position: relative;
  flex-shrink: 0;
}

.conversation-item__type-tag {
  position: absolute;
  right: -6px;
  bottom: -4px;
  padding: 0 4px;
  height: 16px;
  line-height: 16px;
}

.conversation-item__main {
  flex: 1;
  min-width: 0;
}

.conversation-item__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.conversation-item__name {
  font-size: 14px;
  color: var(--color-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-item__time {
  font-size: 12px;
  color: var(--color-text-tertiary);
  flex-shrink: 0;
}

.conversation-item__summary {
  font-size: 12px;
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
