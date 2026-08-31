<script setup lang="ts">
// 聊天页面（/chat）：会话列表 + 消息收发 + 断线重连状态提示，组合 4 个子组件与
// 2 个弹窗，进入页面时建立 WebSocket 连接、离开页面时断开（chat-messaging spec
// "聊天菜单与会话/消息界面"需求，tasks.md 6.1/6.5/6.6/6.8）。
import { onMounted, onUnmounted, ref } from 'vue'
import { useChatStore } from '@/stores/chat'
import ConnectionStatusBar from './components/ConnectionStatusBar.vue'
import ConversationList from './components/ConversationList.vue'
import MessagePanel from './components/MessagePanel.vue'
import GroupMembersDialog from './components/GroupMembersDialog.vue'
import CreateGroupDialog from './components/CreateGroupDialog.vue'
import StartSingleChatDialog from './components/StartSingleChatDialog.vue'

const chatStore = useChatStore()

onMounted(async () => {
  chatStore.connect()
  await chatStore.loadConversations()
})

onUnmounted(() => {
  chatStore.disconnect()
})

const startSingleChatVisible = ref(false)
const createGroupVisible = ref(false)
const membersVisible = ref(false)
</script>

<template>
  <div class="chat-view">
    <connection-status-bar :state="chatStore.connectionState" />

    <div class="chat-view__body">
      <section class="chat-view__panel chat-view__panel--list">
        <conversation-list
          @start-single-chat="startSingleChatVisible = true"
          @create-group="createGroupVisible = true"
        />
      </section>
      <section class="chat-view__panel chat-view__panel--message">
        <message-panel @show-members="membersVisible = true" />
      </section>
    </div>

    <start-single-chat-dialog v-model="startSingleChatVisible" />
    <create-group-dialog v-model="createGroupVisible" />
    <group-members-dialog v-model="membersVisible" :conversation-id="chatStore.currentConversationId" />
  </div>
</template>

<style scoped lang="scss">
.chat-view {
  display: flex;
  flex-direction: column;
  height: calc(100vh - var(--layout-header-height) - 48px);
}

.chat-view__body {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 16px;
  flex: 1;
  min-height: 0;

  @media (max-width: 960px) {
    grid-template-columns: 1fr;
  }
}

.chat-view__panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 16px;
  box-shadow: var(--shadow-sm);
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
</style>
