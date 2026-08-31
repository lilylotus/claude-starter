<script setup lang="ts">
// 聊天连接状态提示条：展示 WebSocket 连接中/已连接/已断开重连中/已断开四种状态，
// 呼应 chat-messaging spec"断线重连体验"需求——用户不需要手动刷新页面就能感知到
// 连接状态变化并自动恢复聊天。已连接状态不常驻展示，只在非稳定状态下提示，
// 避免长期占用界面空间。
import { computed } from 'vue'
import type { ChatConnectionState } from '@/utils/chatSocket'

const props = defineProps<{
  state: ChatConnectionState
}>()

const visible = computed(() => props.state !== 'open')

const text = computed(() => {
  switch (props.state) {
    case 'idle':
      return '尚未连接聊天服务'
    case 'connecting':
      return '正在连接聊天服务…'
    case 'reconnecting':
      return '连接已断开，正在自动重连…'
    case 'closed':
      return '连接已断开'
    default:
      return ''
  }
})

const type = computed<'info' | 'warning'>(() => (props.state === 'connecting' ? 'info' : 'warning'))
</script>

<template>
  <el-alert
    v-if="visible"
    class="connection-status-bar"
    :type="type"
    :title="text"
    :closable="false"
    show-icon
  />
</template>

<style scoped lang="scss">
.connection-status-bar {
  margin-bottom: 12px;
}
</style>
