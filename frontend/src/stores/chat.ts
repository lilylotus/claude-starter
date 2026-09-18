import { defineStore } from 'pinia'
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as chatApi from '@/api/chat'
import * as chatKeyApi from '@/api/chatKey'
import * as chatCrypto from '@/utils/chatCrypto'
import * as chatKeyStore from '@/utils/chatKeyStore'
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

// 单聊消息本地解密状态：plain=群聊消息/本就是明文，ok=解密成功，failed=解密失败
// （本地缺少正确密钥状态、或密文损坏），展示层据此在气泡里显示"消息无法解密"占位提示
export type DecryptState = 'plain' | 'ok' | 'failed'

export interface LocalChatMessage extends ChatMessageVO {
  status: LocalMessageStatus
  // 展示用明文：群聊消息等于 content；单聊消息是本地解密后的结果（解密失败时为空字符串，
  // 由 decryptState === 'failed' 驱动界面展示占位提示，而不是把空字符串当正文渲染）
  decryptState: DecryptState
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

  // ---- 单聊端到端加密：本地身份密钥/会话密钥状态（chat-end-to-end-encryption change） ----

  // 本地聊天身份密钥是否已解锁（本地存在密钥材料且用当次密码成功解包，或本次是首次生成）
  const keysUnlocked = ref(false)
  // 当前用户身份密钥对，只保存在内存中，不做任何持久化（design.md Decision 2）
  const identityKeyPair = ref<chatCrypto.IdentityKeyPair | null>(null)
  // 对方 userId -> 本地计算出的公钥指纹（分组十六进制），供"查看安全码"展示
  const counterpartFingerprints = reactive<Record<number, string>>({})
  // 对方 userId -> 是否检测到"安全码已变更且用户尚未确认"，驱动聊天界面的告警条
  const trustWarnings = reactive<Record<number, boolean>>({})
  // 对方 userId -> X25519 ECDH 得到的共享密钥 SK，内存缓存，避免重复做椭圆曲线运算；
  // 不需要响应式（只在内部加解密逻辑里读写，不直接驱动模板）
  const sharedSecretCache = new Map<number, Uint8Array>()

  // 当前选中会话（若为单聊）对方的 userId，供"查看安全码"入口/告警条读取
  const currentCounterpartUserId = computed(() =>
    currentConversationId.value !== null ? counterpartUserId(currentConversationId.value) : null,
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
    if (body.msgId) {
      const creation = pendingSingleCreations.get(body.msgId)
      if (creation) {
        pendingSingleCreations.delete(body.msgId)
        creation.reject(new Error(body.message || '发起单聊失败'))
      }
    }
    ElMessage.error(body.message || '聊天服务出现异常')
  }

  // 收到服务端推送（实时投递或离线补偿）：解密（单聊）后插入本地消息列表并更新会话摘要。
  // 是否需要走解密路径按 senderIdentityKeyFingerprint 是否非空判断，而不是依赖本地会话类型
  // 查表——对"本地会话列表还没同步到的全新单聊"这类场景更健壮（此时 conversations 里还
  // 查不到这个会话，无从得知它的 conversationType）。单聊场景下推送消息的发送者必然是
  // 对方（服务端只会把 MESSAGE_PUSH 投递给消息的接收方，不会推回给发送方自己），因此
  // body.senderId 就是做 ECDH/安全码比对要用的对方 userId。
  async function handleMessagePush(body: MessagePushFrameBody): Promise<void> {
    const isEncryptedSingle = Boolean(body.senderIdentityKeyFingerprint)
    let displayContent = body.content
    let decryptState: DecryptState = 'plain'
    if (isEncryptedSingle) {
      const result = await decryptSingleContent(body.content, body.senderId, body.senderId)
      displayContent = result.content
      decryptState = result.state
    }

    const localMessage: LocalChatMessage = {
      id: 0,
      msgId: body.msgId,
      conversationId: body.conversationId,
      conversationSeq: body.conversationSeq,
      senderId: body.senderId,
      senderName: '',
      msgType: body.msgType,
      content: displayContent,
      filtered: false,
      senderIdentityKeyFingerprint: body.senderIdentityKeyFingerprint,
      sendTime: body.sendTime,
      status: 'sent',
      decryptState,
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

  // ---- 单聊端到端加密：密钥生命周期/会话密钥协商/加解密 ----

  // 登录成功后调用：本地存在该账号的密钥材料就用当次密码解锁，不存在就生成新密钥对、
  // 本地包裹存储后注册到服务端（tasks.md 5.1/5.4）。accountCode 作为本地 IndexedDB 的分区键，
  // 见 utils/chatKeyStore.ts 顶部注释里"为什么用账号编码而不是数值 userId"的说明。
  async function unlockOrGenerateIdentityKeys(accountCode: string, password: string): Promise<void> {
    try {
      const existing = await chatKeyStore.loadIdentityKeyMaterial(accountCode)
      if (existing) {
        try {
          const privateKey = await chatCrypto.unwrapPrivateKey(existing, password)
          identityKeyPair.value = { publicKey: chatCrypto.fromBase64(existing.publicKey), privateKey }
          keysUnlocked.value = true
        } catch {
          keysUnlocked.value = false
          ElMessage.error('聊天密钥解锁失败：密码错误，或本地聊天密钥数据已损坏，历史加密消息可能无法解密')
        }
        return
      }

      const pair = await chatCrypto.generateIdentityKeyPair()
      const wrapped = await chatCrypto.wrapPrivateKey(pair.privateKey, password)
      const publicKeyBase64 = chatCrypto.toBase64(pair.publicKey)
      await chatKeyStore.saveIdentityKeyMaterial(accountCode, { ...wrapped, publicKey: publicKeyBase64 })
      identityKeyPair.value = pair
      keysUnlocked.value = true
      ElMessage({
        type: 'warning',
        message:
          '本地未检测到聊天加密密钥（可能是首次使用聊天功能，或浏览器数据已被清空），已为你生成新的密钥。' +
          '请务必牢记登录密码：一旦忘记密码或清空浏览器数据，聊天记录将永久无法解密，且无法找回。',
        duration: 8000,
        showClose: true,
      })
      try {
        await chatKeyApi.registerMyKey({ identityPublicKey: publicKeyBase64 })
      } catch {
        ElMessage.error('聊天加密密钥未能同步到服务器，对方可能暂时无法向你发起加密会话，请重新登录后重试')
      }
    } catch (error) {
      keysUnlocked.value = false
      ElMessage.error('聊天加密密钥初始化失败，单聊消息暂时无法收发')
      // eslint-disable-next-line no-console
      console.error('unlockOrGenerateIdentityKeys failed', error)
    }
  }

  // 确保已缓存对方的身份公钥/指纹/共享密钥，并按 TOFU 策略比对信任状态；
  // 返回 false 表示对方尚未注册聊天身份公钥（无法建立加密会话），调用方据此中止发送/提示
  async function ensureCounterpartKey(counterpartId: number): Promise<boolean> {
    let vo
    try {
      vo = await chatKeyApi.getUserKey(counterpartId)
    } catch {
      // 后端业务错误（如"该用户尚未注册聊天身份公钥"）已由 request.ts 响应拦截器
      // 统一 ElMessage 提示，这里不重复弹提示
      return false
    }

    const fingerprint = await chatCrypto.computeFingerprint(vo.identityPublicKey)
    counterpartFingerprints[counterpartId] = fingerprint

    const accountCode = useAuthStore().accountCode
    const trusted = await chatKeyStore.getTrustedFingerprint(accountCode, counterpartId)
    if (trusted === null) {
      // 首次与该用户建立单聊会话：按 TOFU 策略直接信任当前查询到的指纹，不触发告警
      await chatKeyStore.setTrustedFingerprint(accountCode, counterpartId, fingerprint)
      trustWarnings[counterpartId] = false
    } else {
      trustWarnings[counterpartId] = trusted !== fingerprint
    }

    if (identityKeyPair.value) {
      const sharedSecret = await chatCrypto.computeSharedSecret(
        identityKeyPair.value.privateKey,
        chatCrypto.fromBase64(vo.identityPublicKey),
      )
      sharedSecretCache.set(counterpartId, sharedSecret)
    }
    return true
  }

  // 用户在"安全码已变更"告警条上主动确认后调用：把当前指纹更新为新的信任指纹，
  // 告警随之消失；确认前不阻止用户继续收发消息（design.md Decision 4，仅提示不阻断）
  async function acknowledgeTrustChange(counterpartId: number): Promise<void> {
    const fingerprint = counterpartFingerprints[counterpartId]
    if (!fingerprint) return
    const accountCode = useAuthStore().accountCode
    await chatKeyStore.setTrustedFingerprint(accountCode, counterpartId, fingerprint)
    trustWarnings[counterpartId] = false
  }

  // 计算当前用户自己的身份公钥指纹，供"查看安全码"弹窗展示；身份私钥不出 store，
  // 本函数只使用公钥部分。密钥尚未解锁时返回 null
  async function computeMyFingerprint(): Promise<string | null> {
    if (!identityKeyPair.value) return null
    return chatCrypto.computeFingerprint(chatCrypto.toBase64(identityKeyPair.value.publicKey))
  }

  // 解密单聊消息内容：ciphertextJson 是密文信封 JSON 字符串，messageSenderId 是这条消息
  // 真正的发送者用户 id（用于派生消息密钥，见 chatCrypto.deriveMessageKey 的入参说明），
  // counterpartId 是"会话另一方"的 userId——共享密钥 SK 在两人之间是对称的，与"谁是
  // 发送者"无关，只取决于"这个单聊会话的两个参与者分别是谁"，因此由调用方显式传入，
  // 不在本函数内部猜测：
  // - 实时/离线推送场景（handleMessagePush）：推送只会投给接收方，senderId 必然是对方，
  //   两个参数相同，直接传 body.senderId 两次即可。
  // - REST 历史消息场景（toLocalMessage）：消息可能是我自己发的也可能是对方发的，
  //   messageSenderId 用 message.senderId，counterpartId 统一用
  //   counterpartUserId(conversationId)（该会话里"不是我"的那个成员）。
  async function decryptSingleContent(
    ciphertextJson: string,
    messageSenderId: number,
    counterpartId: number,
  ): Promise<{ content: string; state: DecryptState }> {
    try {
      if (!keysUnlocked.value || !identityKeyPair.value) {
        return { content: '', state: 'failed' }
      }
      if (!sharedSecretCache.has(counterpartId)) {
        const ok = await ensureCounterpartKey(counterpartId)
        if (!ok) return { content: '', state: 'failed' }
      }
      const sharedSecret = sharedSecretCache.get(counterpartId)
      if (!sharedSecret) return { content: '', state: 'failed' }
      const envelope = chatCrypto.deserializeEnvelope(ciphertextJson)
      const messageKey = await chatCrypto.deriveMessageKey(sharedSecret, envelope.ratchetHeader.counter, messageSenderId)
      const content = await chatCrypto.decryptMessage(messageKey, envelope.nonce, envelope.ciphertext)
      return { content, state: 'ok' }
    } catch {
      return { content: '', state: 'failed' }
    }
  }

  // 加密一条要发给 counterpartId 的单聊消息：本地完成 ECDH（缺失则先算好并缓存）+
  // 按持久化的发送计数器派生一次性 messageKey + AEAD 加密，返回可直接塞进
  // ChatSingleFrameBody.content 的密文信封 JSON 字符串，以及本次使用的发送方指纹。
  // 返回 null 表示对方尚未注册聊天身份公钥，无法建立加密会话（调用方据此中止发送）。
  async function encryptForCounterpart(
    counterpartId: number,
    plaintext: string,
  ): Promise<{ envelopeJson: string; fingerprint: string } | null> {
    if (!identityKeyPair.value || currentUserId.value === null) {
      // 本地聊天身份密钥尚未解锁完成（如刚登录、IndexedDB/网络异常导致初始化还没结束），
      // 这里主动提示一次；ensureCounterpartKey 内部失败（对方未注册公钥等）已由响应
      // 拦截器统一提示，不在这个分支重复处理
      ElMessage.error('聊天加密密钥尚未就绪，请稍候重试；如持续出现，请重新登录')
      return null
    }
    if (!sharedSecretCache.has(counterpartId)) {
      const ok = await ensureCounterpartKey(counterpartId)
      if (!ok) return null
    }
    const sharedSecret = sharedSecretCache.get(counterpartId)
    if (!sharedSecret) return null

    const accountCode = useAuthStore().accountCode
    const counter = await chatKeyStore.nextSendCounter(accountCode, counterpartId)
    const messageKey = await chatCrypto.deriveMessageKey(sharedSecret, counter, currentUserId.value)
    const { nonce, ciphertext } = await chatCrypto.encryptMessage(messageKey, plaintext)
    const envelope: chatCrypto.ChatCiphertextEnvelope = { ciphertext, nonce, ratchetHeader: { counter, version: 1 } }
    const fingerprint = await chatCrypto.computeFingerprint(chatCrypto.toBase64(identityKeyPair.value.publicKey))
    return { envelopeJson: chatCrypto.serializeEnvelope(envelope), fingerprint }
  }

  // 选中一个会话：加载成员（供单聊解析对方 userId/群聊成员面板）、单聊场景下提前查询/
  // 缓存对方身份公钥（供发送前直接复用，避免每条消息都等一次 REST 往返）与首屏历史消息，
  // 并清空该会话的未读计数
  async function selectConversation(conversationId: number): Promise<void> {
    currentConversationId.value = conversationId
    unreadCounts[conversationId] = 0
    await ensureMembers(conversationId)

    const conversation = conversations.value.find((item) => item.id === conversationId)
    if (conversation?.conversationType === CONVERSATION_TYPE_SINGLE) {
      const counterpartId = counterpartUserId(conversationId)
      if (counterpartId !== null) {
        await ensureCounterpartKey(counterpartId)
      }
    }

    if (!messagesByConversation[conversationId]) {
      await loadInitialMessages(conversationId)
    }
  }

  async function loadInitialMessages(conversationId: number): Promise<void> {
    initialMessagesLoading.value = true
    try {
      const page = await chatApi.getMessages(conversationId, undefined, MESSAGE_PAGE_SIZE)
      // 后端按 conversationSeq 降序返回（最新在前），反转为时间正序供消息面板展示
      const ordered = await Promise.all(
        [...page.records].reverse().map((message) => toLocalMessage(message, 'sent', conversationId)),
      )
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
      const older = await Promise.all(
        [...page.records].reverse().map((message) => toLocalMessage(message, 'sent', conversationId)),
      )
      messagesByConversation[conversationId] = [...older, ...list]
      hasMoreByConversation[conversationId] = page.records.length >= MESSAGE_PAGE_SIZE
    } finally {
      loadingMoreConversationId.value = null
    }
  }

  // 把服务端返回的历史消息转换为本地展示用结构：单聊消息（按 senderIdentityKeyFingerprint
  // 是否非空判断）在此本地解密，群聊/历史明文消息原样透传
  async function toLocalMessage(
    message: ChatMessageVO,
    status: LocalMessageStatus,
    conversationId: number,
  ): Promise<LocalChatMessage> {
    if (!message.senderIdentityKeyFingerprint) {
      return { ...message, status, decryptState: 'plain' }
    }
    const counterpartId = counterpartUserId(conversationId)
    if (counterpartId === null) {
      return { ...message, status, content: '', decryptState: 'failed' }
    }
    const result = await decryptSingleContent(message.content, message.senderId, counterpartId)
    return { ...message, status, content: result.content, decryptState: result.state }
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
  // 保证排在末尾，收到 ACK 后会被回填真实序号并重新排序。displayContent 是本地展示用明文
  // （用户实际输入的内容），与真正发到服务端的密文信封是两回事，本地占位消息不需要走解密
  function pushLocalPendingMessage(
    conversationId: number,
    msgId: string,
    displayContent: string,
    msgType: number,
    senderIdentityKeyFingerprint: string | null = null,
  ): void {
    if (currentUserId.value === null) return
    appendOrReplaceMessage(conversationId, {
      id: 0,
      msgId,
      conversationId,
      conversationSeq: Number.MAX_SAFE_INTEGER,
      senderId: currentUserId.value,
      senderName: '',
      msgType,
      content: displayContent,
      filtered: false,
      senderIdentityKeyFingerprint,
      sendTime: new Date().toISOString(),
      status: 'sending',
      decryptState: 'plain',
    })
  }

  // 向一个已存在的会话（单聊或群聊）发送消息：单聊在发送前本地完成加密（ECDH + 棘轮派生
  // messageKey + AEAD 加密），群聊保持明文不变（design.md Decision 1/5，Non-Goals）
  async function sendToConversation(conversationId: number, content: string, msgType = 1): Promise<void> {
    const conversation = conversations.value.find((item) => item.id === conversationId)
    if (!conversation) return
    if (conversation.conversationType === CONVERSATION_TYPE_SINGLE) {
      const toUserId = counterpartUserId(conversationId)
      if (toUserId === null) {
        ElMessage.error('无法确定对方用户，请重新进入该会话后再试')
        return
      }
      const encrypted = await encryptForCounterpart(toUserId, content)
      if (!encrypted) return
      const msgId = ensureSocket().sendSingle(toUserId, encrypted.envelopeJson, msgType, undefined, encrypted.fingerprint)
      pushLocalPendingMessage(conversationId, msgId, content, msgType, encrypted.fingerprint)
    } else {
      const msgId = ensureSocket().sendGroup(conversationId, content, msgType)
      pushLocalPendingMessage(conversationId, msgId, content, msgType)
    }
  }

  // 发起一条全新单聊消息（对方此前没有会话记录，服务端首次发送时自动创建），
  // 返回创建后的会话 id，调用方（发起单聊弹窗）据此调用 selectConversation 切换过去；
  // 对方尚未注册聊天身份公钥时 reject（interceptor 已弹出"该用户尚未注册聊天身份公钥"提示）
  async function startNewSingleChat(toUserId: number, content: string, msgType = 1): Promise<number> {
    const encrypted = await encryptForCounterpart(toUserId, content)
    if (!encrypted) {
      throw new Error('对方尚未开启加密聊天，暂时无法发起会话')
    }
    return new Promise((resolve, reject) => {
      const msgId = ensureSocket().sendSingle(toUserId, encrypted.envelopeJson, msgType, undefined, encrypted.fingerprint)
      pendingSingleCreations.set(msgId, { resolve, reject })
    })
  }

  // 手动重发一条状态为"失败"的消息：复用同一个 msgId（服务端幂等处理，不会重复入库/投递）；
  // 单聊场景下用本地展示明文（target.content 就是用户当初输入的原文，从未被改写成密文）
  // 重新走一次加密，而不是把上次的密文信封原样再发一遍
  async function retrySend(conversationId: number, msgId: string): Promise<void> {
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
      const encrypted = await encryptForCounterpart(toUserId, target.content)
      if (!encrypted) {
        target.status = 'failed'
        return
      }
      ensureSocket().sendSingle(toUserId, encrypted.envelopeJson, target.msgType, msgId, encrypted.fingerprint)
    } else {
      ensureSocket().sendGroup(conversationId, target.content, target.msgType, msgId)
    }
  }

  // 退出登录/离开应用时调用：断开连接并清空全部会话状态（含单聊加密相关的内存态，
  // 身份私钥/共享密钥不做持久化，下次解锁重新从 IndexedDB 的包裹密钥材料解出）
  function reset(): void {
    disconnect()
    conversations.value = []
    currentConversationId.value = null
    currentUserId.value = null
    keysUnlocked.value = false
    identityKeyPair.value = null
    sharedSecretCache.clear()
    Object.keys(messagesByConversation).forEach((key) => delete messagesByConversation[Number(key)])
    Object.keys(membersByConversation).forEach((key) => delete membersByConversation[Number(key)])
    Object.keys(hasMoreByConversation).forEach((key) => delete hasMoreByConversation[Number(key)])
    Object.keys(unreadCounts).forEach((key) => delete unreadCounts[Number(key)])
    Object.keys(counterpartFingerprints).forEach((key) => delete counterpartFingerprints[Number(key)])
    Object.keys(trustWarnings).forEach((key) => delete trustWarnings[Number(key)])
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
    keysUnlocked,
    counterpartFingerprints,
    trustWarnings,
    currentCounterpartUserId,
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
    unlockOrGenerateIdentityKeys,
    ensureCounterpartKey,
    acknowledgeTrustChange,
    computeMyFingerprint,
  }
})
