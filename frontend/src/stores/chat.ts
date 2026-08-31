import { defineStore } from 'pinia'
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as chatApi from '@/api/chat'
import { useAuthStore } from '@/stores/auth'
import { ChatSocketClient, type ChatConnectionState } from '@/utils/chatSocket'
import { resolveChatGatewayUrl } from '@/config/chatGateway'
import {
  CONVERSATION_TYPE_SINGLE,
  type AckFrameBody,
  type ChatMessageVO,
  type ConversationMemberVO,
  type ConversationVO,
  type ErrorFrameBody,
  type LoginAckFrameBody,
  type MessagePushFrameBody,
} from '@/types/chat'

// 本地消息发送状态：sending=已发出等待 ACK，sent=已收到 ACK 确认，failed=重发达到上限仍失败
export type LocalMessageStatus = 'sending' | 'sent' | 'failed'

export interface LocalChatMessage extends ChatMessageVO {
  status: LocalMessageStatus
}

const MESSAGE_PAGE_SIZE = 20

// 聊天 store：会话列表、当前会话消息列表（按 conversationSeq 排序）、WebSocket 连接状态、
// 未读数（chat-gateway-core change tasks.md 6.3）。WebSocket 协议编解码/重连细节封装在
// utils/chatSocket.ts，本 store 只负责把回调结果接入响应式状态并驱动 REST 调用。
export const useChatStore = defineStore('chat', () => {
  // ---- 连接状态 ----
  const connectionState = ref<ChatConnectionState>('idle')
  // 当前登录用户 id：WebSocket 认证成功（LOGIN_ACK）后由服务端回填，REST 接口登录态里
  // 不带这个字段，聊天模块是目前唯一需要它的地方，因此就地维护，不污染 auth store
  const currentUserId = ref<number | null>(null)

  let socket: ChatSocketClient | null = null
  // 通过"发起单聊"入口发送的第一条消息：ACK 到达前会话尚不存在，用 msgId 关联
  // 待 resolve 的 Promise，由 handleAck/handleSendFailed 决出结果
  const pendingSingleCreations = new Map<
    string,
    { resolve: (conversationId: number) => void; reject: (reason: Error) => void }
  >()

  // ---- 会话列表 ----
  const conversations = ref<ConversationVO[]>([])
  const conversationsLoading = ref(false)
  const currentConversationId = ref<number | null>(null)
  // 会话 id -> 未读消息数；当前选中的会话恒为 0
  const unreadCounts = reactive<Record<number, number>>({})

  const currentConversation = computed(
    () => conversations.value.find((item) => item.id === currentConversationId.value) ?? null,
  )

  // ---- 消息 ----
  // 会话 id -> 消息列表，按 conversationSeq 升序（最旧在前），供消息面板从上到下渲染
  const messagesByConversation = reactive<Record<number, LocalChatMessage[]>>({})
  // 会话 id -> 是否还有更早的历史消息可加载
  const hasMoreByConversation = reactive<Record<number, boolean>>({})
  const initialMessagesLoading = ref(false)
  // 当前正在"加载更多"的会话 id，同一时刻只允许一个会话在翻页，避免游标错乱
  const loadingMoreConversationId = ref<number | null>(null)

  const currentMessages = computed(() =>
    currentConversationId.value !== null ? (messagesByConversation[currentConversationId.value] ?? []) : [],
  )

  // ---- 会话成员（单聊用于解析对方 userId 供 WebSocket 发送，群聊用于成员管理面板）----
  const membersByConversation = reactive<Record<number, ConversationMemberVO[]>>({})

  const currentMembers = computed(() =>
    currentConversationId.value !== null ? (membersByConversation[currentConversationId.value] ?? []) : [],
  )

  // ---- WebSocket 连接管理 ----

  function ensureSocket(): ChatSocketClient {
    if (socket) return socket
    const authStore = useAuthStore()
    socket = new ChatSocketClient({
      url: resolveChatGatewayUrl(),
      getAccessKey: () => authStore.accessKey,
      onStateChange: (state) => {
        connectionState.value = state
      },
      onLoginAck: handleLoginAck,
      onMessagePush: handleMessagePush,
      onAck: handleAck,
      onError: handleError,
      onSendFailed: handleSendFailed,
    })
    return socket
  }

  function connect(): void {
    ensureSocket().connect()
  }

  function disconnect(): void {
    socket?.disconnect()
    connectionState.value = 'idle'
  }

  function handleLoginAck(body: LoginAckFrameBody): void {
    if (body.success && body.userId) {
      currentUserId.value = body.userId
    } else {
      ElMessage.error(body.message || '聊天连接认证失败')
    }
  }

  function handleError(body: ErrorFrameBody): void {
    ElMessage.error(body.message || '聊天服务出现异常')
  }

  // 收到服务端推送（实时投递或离线补偿）：插入本地消息列表并更新会话摘要
  function handleMessagePush(body: MessagePushFrameBody): void {
    const localMessage: LocalChatMessage = {
      id: 0,
      msgId: body.msgId,
      conversationId: body.conversationId,
      conversationSeq: body.conversationSeq,
      senderId: body.senderId,
      senderName: '',
      msgType: body.msgType,
      content: body.content,
      filtered: false,
      sendTime: body.sendTime,
      status: 'sent',
    }
    appendOrReplaceMessage(body.conversationId, localMessage)
    updateConversationSummaryFromPush(body)

    if (body.conversationId !== currentConversationId.value && body.senderId !== currentUserId.value) {
      unreadCounts[body.conversationId] = (unreadCounts[body.conversationId] ?? 0) + 1
    }
  }

  // 收到服务端对自己发送消息的 ACK：把本地"发送中"状态更新为"已发送"并回填真实序号
  function handleAck(body: AckFrameBody): void {
    const creation = pendingSingleCreations.get(body.msgId)
    if (creation) {
      pendingSingleCreations.delete(body.msgId)
      creation.resolve(body.conversationId)
    }

    const list = messagesByConversation[body.conversationId]
    if (list) {
      const target = list.find((message) => message.msgId === body.msgId)
      if (target) {
        target.status = 'sent'
        target.conversationSeq = body.conversationSeq
        target.sendTime = body.sendTime
        list.sort((a, b) => a.conversationSeq - b.conversationSeq)
      }
    }

    const conversation = conversations.value.find((item) => item.id === body.conversationId)
    if (conversation) {
      conversation.lastMessageSendTime = body.sendTime
    } else {
      // 服务端确认了一个当前会话列表里还没有的会话（典型场景：发起新单聊的首条消息），
      // 静默刷新一次会话列表拿到完整信息
      void loadConversations()
    }
  }

  function handleSendFailed(msgId: string): void {
    const creation = pendingSingleCreations.get(msgId)
    if (creation) {
      pendingSingleCreations.delete(msgId)
      creation.reject(new Error('消息发送失败，请重试'))
    }
    for (const list of Object.values(messagesByConversation)) {
      const target = list.find((message) => message.msgId === msgId)
      if (target) {
        target.status = 'failed'
        break
      }
    }
  }

  function appendOrReplaceMessage(conversationId: number, message: LocalChatMessage): void {
    const list = messagesByConversation[conversationId] ?? (messagesByConversation[conversationId] = [])
    const existingIndex = list.findIndex((item) => item.msgId === message.msgId)
    if (existingIndex >= 0) {
      list[existingIndex] = { ...list[existingIndex], ...message }
    } else {
      list.push(message)
    }
    list.sort((a, b) => a.conversationSeq - b.conversationSeq)
  }

  function updateConversationSummaryFromPush(body: MessagePushFrameBody): void {
    const conversation = conversations.value.find((item) => item.id === body.conversationId)
    if (!conversation) {
      // 收到一条不在当前会话列表中的消息推送（如全新单聊的第一条消息），静默刷新会话列表
      void loadConversations()
      return
    }
    conversation.lastMessageContent = body.content
    conversation.lastMessageSenderId = body.senderId
    conversation.lastMessageSendTime = body.sendTime
    conversations.value.sort((a, b) => compareByRecency(a, b))
  }

  function compareByRecency(a: ConversationVO, b: ConversationVO): number {
    const aTime = a.lastMessageSendTime ? new Date(a.lastMessageSendTime).getTime() : 0
    const bTime = b.lastMessageSendTime ? new Date(b.lastMessageSendTime).getTime() : 0
    return bTime - aTime
  }

  // ---- 会话列表/成员：REST ----

  async function loadConversations(): Promise<void> {
    conversationsLoading.value = true
    try {
      conversations.value = await chatApi.getConversations()
    } finally {
      conversationsLoading.value = false
    }
  }

  async function ensureMembers(conversationId: number): Promise<ConversationMemberVO[]> {
    if (!membersByConversation[conversationId]) {
      membersByConversation[conversationId] = await chatApi.getConversationMembers(conversationId)
    }
    return membersByConversation[conversationId]
  }

  async function refreshMembers(conversationId: number): Promise<void> {
    membersByConversation[conversationId] = await chatApi.getConversationMembers(conversationId)
  }

  // 单聊会话对方的 userId：从已缓存的成员列表里找出不是自己的那个成员
  function counterpartUserId(conversationId: number): number | null {
    const members = membersByConversation[conversationId]
    if (!members || currentUserId.value === null) return null
    return members.find((member) => member.userId !== currentUserId.value)?.userId ?? null
  }

  // 选中一个会话：加载成员（供单聊解析对方 userId/群聊成员面板）与首屏历史消息，
  // 并清空该会话的未读计数
  async function selectConversation(conversationId: number): Promise<void> {
    currentConversationId.value = conversationId
    unreadCounts[conversationId] = 0
    await ensureMembers(conversationId)
    if (!messagesByConversation[conversationId]) {
      await loadInitialMessages(conversationId)
    }
  }

  async function loadInitialMessages(conversationId: number): Promise<void> {
    initialMessagesLoading.value = true
    try {
      const page = await chatApi.getMessages(conversationId, undefined, MESSAGE_PAGE_SIZE)
      // 后端按 conversationSeq 降序返回（最新在前），反转为时间正序供消息面板展示
      const ordered = [...page.records].reverse().map((message) => toLocalMessage(message, 'sent'))
      messagesByConversation[conversationId] = ordered
      hasMoreByConversation[conversationId] = page.records.length >= MESSAGE_PAGE_SIZE
    } finally {
      initialMessagesLoading.value = false
    }
  }

  // 向历史翻页加载更早的消息：以当前已加载的最早一条消息的 conversationSeq 作为游标
  async function loadMoreMessages(conversationId: number): Promise<void> {
    const list = messagesByConversation[conversationId]
    if (!list || list.length === 0) return
    if (hasMoreByConversation[conversationId] === false) return
    if (loadingMoreConversationId.value !== null) return

    loadingMoreConversationId.value = conversationId
    try {
      const beforeSeq = list[0].conversationSeq
      const page = await chatApi.getMessages(conversationId, beforeSeq, MESSAGE_PAGE_SIZE)
      const older = [...page.records].reverse().map((message) => toLocalMessage(message, 'sent'))
      messagesByConversation[conversationId] = [...older, ...list]
      hasMoreByConversation[conversationId] = page.records.length >= MESSAGE_PAGE_SIZE
    } finally {
      loadingMoreConversationId.value = null
    }
  }

  function toLocalMessage(message: ChatMessageVO, status: LocalMessageStatus): LocalChatMessage {
    return { ...message, status }
  }

  async function createGroup(name: string, memberUserIds: number[]): Promise<ConversationVO> {
    const created = await chatApi.createGroupConversation({ name, memberUserIds })
    await loadConversations()
    return created
  }

  async function addMembers(conversationId: number, userIds: number[]): Promise<void> {
    await chatApi.addConversationMembers(conversationId, userIds)
    await refreshMembers(conversationId)
    await loadConversations()
  }

  async function removeMember(conversationId: number, targetUserId: number): Promise<void> {
    await chatApi.removeConversationMember(conversationId, targetUserId)
    if (targetUserId === currentUserId.value) {
      // 主动退出群聊：本地移除该会话及其缓存的消息/成员数据
      conversations.value = conversations.value.filter((item) => item.id !== conversationId)
      delete messagesByConversation[conversationId]
      delete membersByConversation[conversationId]
      delete hasMoreByConversation[conversationId]
      if (currentConversationId.value === conversationId) {
        currentConversationId.value = null
      }
    } else {
      await refreshMembers(conversationId)
      await loadConversations()
    }
  }

  // ---- 发送消息 ----

  // 在本地消息列表里插入一条"发送中"占位消息；conversationSeq 暂用 Number.MAX_SAFE_INTEGER
  // 保证排在末尾，收到 ACK 后会被回填真实序号并重新排序
  function pushLocalPendingMessage(conversationId: number, msgId: string, content: string, msgType: number): void {
    if (currentUserId.value === null) return
    appendOrReplaceMessage(conversationId, {
      id: 0,
      msgId,
      conversationId,
      conversationSeq: Number.MAX_SAFE_INTEGER,
      senderId: currentUserId.value,
      senderName: '',
      msgType,
      content,
      filtered: false,
      sendTime: new Date().toISOString(),
      status: 'sending',
    })
  }

  // 向一个已存在的会话（单聊或群聊）发送消息
  function sendToConversation(conversationId: number, content: string, msgType = 1): void {
    const conversation = conversations.value.find((item) => item.id === conversationId)
    if (!conversation) return
    if (conversation.conversationType === CONVERSATION_TYPE_SINGLE) {
      const toUserId = counterpartUserId(conversationId)
      if (toUserId === null) {
        ElMessage.error('无法确定对方用户，请重新进入该会话后再试')
        return
      }
      const msgId = ensureSocket().sendSingle(toUserId, content, msgType)
      pushLocalPendingMessage(conversationId, msgId, content, msgType)
    } else {
      const msgId = ensureSocket().sendGroup(conversationId, content, msgType)
      pushLocalPendingMessage(conversationId, msgId, content, msgType)
    }
  }

  // 发起一条全新单聊消息（对方此前没有会话记录，服务端首次发送时自动创建），
  // 返回创建后的会话 id，调用方（发起单聊弹窗）据此调用 selectConversation 切换过去
  function startNewSingleChat(toUserId: number, content: string, msgType = 1): Promise<number> {
    return new Promise((resolve, reject) => {
      const msgId = ensureSocket().sendSingle(toUserId, content, msgType)
      pendingSingleCreations.set(msgId, { resolve, reject })
    })
  }

  // 手动重发一条状态为"失败"的消息：复用同一个 msgId（服务端幂等处理，不会重复入库/投递）
  function retrySend(conversationId: number, msgId: string): void {
    const list = messagesByConversation[conversationId]
    const target = list?.find((message) => message.msgId === msgId)
    const conversation = conversations.value.find((item) => item.id === conversationId)
    if (!target || !conversation) return

    target.status = 'sending'
    if (conversation.conversationType === CONVERSATION_TYPE_SINGLE) {
      const toUserId = counterpartUserId(conversationId)
      if (toUserId === null) {
        target.status = 'failed'
        ElMessage.error('无法确定对方用户，请重新进入该会话后再试')
        return
      }
      ensureSocket().sendSingle(toUserId, target.content, target.msgType, msgId)
    } else {
      ensureSocket().sendGroup(conversationId, target.content, target.msgType, msgId)
    }
  }

  // 退出登录/离开应用时调用：断开连接并清空全部会话状态
  function reset(): void {
    disconnect()
    conversations.value = []
    currentConversationId.value = null
    currentUserId.value = null
    Object.keys(messagesByConversation).forEach((key) => delete messagesByConversation[Number(key)])
    Object.keys(membersByConversation).forEach((key) => delete membersByConversation[Number(key)])
    Object.keys(hasMoreByConversation).forEach((key) => delete hasMoreByConversation[Number(key)])
    Object.keys(unreadCounts).forEach((key) => delete unreadCounts[Number(key)])
  }

  return {
    connectionState,
    currentUserId,
    conversations,
    conversationsLoading,
    currentConversationId,
    currentConversation,
    unreadCounts,
    messagesByConversation,
    hasMoreByConversation,
    initialMessagesLoading,
    loadingMoreConversationId,
    currentMessages,
    membersByConversation,
    currentMembers,
    connect,
    disconnect,
    loadConversations,
    ensureMembers,
    refreshMembers,
    selectConversation,
    loadMoreMessages,
    createGroup,
    addMembers,
    removeMember,
    sendToConversation,
    startNewSingleChat,
    retrySend,
    reset,
  }
})
