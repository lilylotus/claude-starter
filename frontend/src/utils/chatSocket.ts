// 聊天网关 WebSocket 客户端：原生 WebSocket 封装 + 自定义二进制协议帧编解码 +
// 指数退避断线重连 + 重连后重新认证 + 心跳定时发送 + ACK 超时重发（chat-gateway-core
// change design.md Decision 2/3/9，字节级帧结构与后端
// cn.nihility.rbac.chat.gateway.protocol.ChatFrameCodec 完全对齐）。
//
// 协议帧结构（固定 10 字节头 + 变长 body，均为 big-endian）：
//   [magic:4B][version:1B][msgType:1B][bodyLength:4B][body: bodyLength 字节 UTF-8 JSON]
// 每个 WebSocket 二进制帧（ArrayBuffer）承载恰好一个完整协议帧。
//
// 本文件是纯客户端协议/连接管理逻辑，不依赖 Element Plus 等 UI 库、不直接操作 Pinia
// store（保持和 utils/rsa.ts、utils/datetime.ts 同样的“纯工具”定位），由
// src/stores/chat.ts 通过回调把状态变化/收到的消息接入响应式状态。

import { ChatFrameType, type ChatFrameTypeValue } from '@/types/chat'
import type {
  AckFrameBody,
  ErrorFrameBody,
  LoginAckFrameBody,
  MessagePushFrameBody,
} from '@/types/chat'

// 协议魔数（ASCII "CHAT"），与后端 ChatFrameCodec.MAGIC 一致
const MAGIC = 0x43_48_41_54
// 当前协议版本，与后端 ChatFrameCodec.VERSION 一致
const VERSION = 1
// 固定帧头长度：魔数 4B + 版本 1B + 消息类型 1B + 长度域 4B
const HEADER_LENGTH = 10

// 心跳发送周期（毫秒）：必须明显小于后端 chat.gateway.idle-timeout-seconds（默认 60 秒）
const HEARTBEAT_INTERVAL_MS = 30_000
// 发送带 msgId 的消息（CHAT_SINGLE/CHAT_GROUP）后等待 ACK 的超时时间（毫秒）
const ACK_TIMEOUT_MS = 5_000
// 超时未收到 ACK 时允许的最大自动重发次数，避免无限重试
const MAX_AUTO_RETRIES = 3
// 指数退避重连间隔（毫秒）：1s/2s/4s/8s，超过数组长度后固定使用最后一档（8s）
const RECONNECT_BACKOFF_STEPS_MS = [1000, 2000, 4000, 8000]

// 连接状态：idle=尚未连接过，connecting=首次连接中，open=已连接且已认证成功，
// reconnecting=断线后正在按退避间隔重连，closed=已手动断开（不会自动重连）
export type ChatConnectionState = 'idle' | 'connecting' | 'open' | 'reconnecting' | 'closed'

interface DecodedFrame {
  type: number
  body: unknown
}

// 生成客户端消息幂等 id：优先用浏览器原生 randomUUID，不支持时退化为时间戳+随机数拼接
function generateMsgId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}

// 把协议帧编码为 ArrayBuffer，供 WebSocket.send 直接发送
function encodeFrame(type: ChatFrameTypeValue, bodyObject: unknown): ArrayBuffer {
  const json = JSON.stringify(bodyObject ?? {})
  const bodyBytes = new TextEncoder().encode(json)
  const buffer = new ArrayBuffer(HEADER_LENGTH + bodyBytes.length)
  const view = new DataView(buffer)
  view.setUint32(0, MAGIC, false)
  view.setUint8(4, VERSION)
  view.setUint8(5, type)
  view.setUint32(6, bodyBytes.length, false)
  new Uint8Array(buffer, HEADER_LENGTH).set(bodyBytes)
  return buffer
}

// 把收到的 ArrayBuffer 解析为协议帧；帧头损坏/魔数或版本不匹配时抛出异常，调用方丢弃该帧
function decodeFrame(buffer: ArrayBuffer): DecodedFrame {
  if (buffer.byteLength < HEADER_LENGTH) {
    throw new Error('协议帧长度不足，无法解析帧头')
  }
  const view = new DataView(buffer)
  const magic = view.getUint32(0, false)
  if (magic !== MAGIC) {
    throw new Error('协议帧魔数不匹配')
  }
  const version = view.getUint8(4)
  if (version !== VERSION) {
    throw new Error(`不支持的协议版本：${version}`)
  }
  const type = view.getUint8(5)
  const bodyLength = view.getUint32(6, false)
  if (HEADER_LENGTH + bodyLength !== buffer.byteLength) {
    throw new Error('协议帧长度域与实际消息体长度不一致')
  }
  const bodyBytes = new Uint8Array(buffer, HEADER_LENGTH, bodyLength)
  const json = new TextDecoder('utf-8').decode(bodyBytes)
  const body = json ? JSON.parse(json) : {}
  return { type, body }
}

interface PendingSend {
  frameType: ChatFrameTypeValue
  body: unknown
  retries: number
  timer: number | null
}

export interface ChatSocketOptions {
  // 聊天网关 WebSocket 地址
  url: string
  // 每次（含重连）发起认证前取一次当前有效的 accessKey，取实时值而不是构造时的快照，
  // 保证 access-key 被 axios 拦截器静默刷新后，重连时用的是最新值
  getAccessKey: () => string
  onStateChange?: (state: ChatConnectionState) => void
  onLoginAck?: (body: LoginAckFrameBody) => void
  onMessagePush?: (body: MessagePushFrameBody) => void
  onAck?: (body: AckFrameBody) => void
  onError?: (body: ErrorFrameBody) => void
  // 某条 msgId 消息达到最大自动重发次数仍未收到 ACK，判定为发送失败
  onSendFailed?: (msgId: string) => void
}

// 聊天网关 WebSocket 客户端。每个页面会话建议只持有一个实例（由 stores/chat.ts 创建管理），
// 不是无状态的纯函数集合——内部维护连接、心跳定时器、重连定时器、待确认消息表。
export class ChatSocketClient {
  private readonly options: ChatSocketOptions
  private ws: WebSocket | null = null
  private manualClose = true
  private reconnectAttempts = 0
  private reconnectTimer: number | null = null
  private heartbeatTimer: number | null = null
  private readonly pending = new Map<string, PendingSend>()

  constructor(options: ChatSocketOptions) {
    this.options = options
  }

  // 发起连接（含首次连接与用户主动重新连接）；断线重连场景由内部 handleClose 自动触发，
  // 不需要调用方重复调用本方法
  connect(): void {
    this.manualClose = false
    this.reconnectAttempts = 0
    this.clearReconnectTimer()
    this.openSocket()
  }

  // 主动断开：清理定时器与待确认消息表，不会触发自动重连；供离开聊天页面/退出登录时调用
  disconnect(): void {
    this.manualClose = true
    this.clearReconnectTimer()
    this.stopHeartbeat()
    this.pending.forEach((entry) => {
      if (entry.timer !== null) window.clearTimeout(entry.timer)
    })
    this.pending.clear()
    if (this.ws) {
      // 主动断开不需要再触发 onclose 里的重连逻辑，提前置空引用后再关闭
      const socket = this.ws
      this.ws = null
      socket.onopen = null
      socket.onmessage = null
      socket.onerror = null
      socket.onclose = null
      socket.close()
    }
    this.setState('closed')
  }

  // 发送单聊消息，返回本次使用的 msgId；重发失败消息时可显式传入原 msgId 复用
  // （服务端按 msgId 幂等处理，见 chat-messaging spec"消息 ACK 确认与幂等重发"需求）
  sendSingle(toUserId: number, content: string, msgType = 1, msgId: string = generateMsgId()): string {
    const body = { msgId, toUserId, msgType, content }
    this.trackPending(msgId, ChatFrameType.CHAT_SINGLE, body)
    this.sendFrame(ChatFrameType.CHAT_SINGLE, body)
    return msgId
  }

  // 发送群聊消息，返回本次使用的 msgId；用法同 sendSingle
  sendGroup(conversationId: number, content: string, msgType = 1, msgId: string = generateMsgId()): string {
    const body = { msgId, conversationId, msgType, content }
    this.trackPending(msgId, ChatFrameType.CHAT_GROUP, body)
    this.sendFrame(ChatFrameType.CHAT_GROUP, body)
    return msgId
  }

  private openSocket(): void {
    this.setState(this.reconnectAttempts > 0 ? 'reconnecting' : 'connecting')
    const ws = new WebSocket(this.options.url)
    ws.binaryType = 'arraybuffer'
    ws.onopen = () => {
      this.reconnectAttempts = 0
      this.sendLogin()
    }
    ws.onmessage = (event) => this.handleMessage(event)
    ws.onerror = () => {
      // 具体错误信息浏览器不暴露给 JS，真正的状态流转统一交给随后触发的 onclose 处理
    }
    ws.onclose = () => this.handleClose()
    this.ws = ws
  }

  private sendLogin(): void {
    this.sendFrame(ChatFrameType.LOGIN, { accessKey: this.options.getAccessKey() })
  }

  private handleMessage(event: MessageEvent): void {
    if (!(event.data instanceof ArrayBuffer)) return
    let frame: DecodedFrame
    try {
      frame = decodeFrame(event.data)
    } catch {
      // 帧头损坏/魔数版本不匹配：丢弃该帧，不影响连接本身
      return
    }
    switch (frame.type) {
      case ChatFrameType.LOGIN_ACK: {
        const body = frame.body as LoginAckFrameBody
        if (body.success) {
          this.setState('open')
          this.startHeartbeat()
        }
        // 认证失败时不主动 setState('closed')：服务端会主动关闭连接，
        // 交给 handleClose 统一处理状态流转，避免状态被写两次
        this.options.onLoginAck?.(body)
        break
      }
      case ChatFrameType.HEARTBEAT_ACK:
        break
      case ChatFrameType.ACK: {
        const body = frame.body as AckFrameBody
        this.resolvePending(body.msgId)
        this.options.onAck?.(body)
        break
      }
      case ChatFrameType.MESSAGE_PUSH:
        this.options.onMessagePush?.(frame.body as MessagePushFrameBody)
        break
      case ChatFrameType.ERROR: {
        const body = frame.body as ErrorFrameBody
        if (body.msgId) this.resolvePending(body.msgId)
        this.options.onError?.(body)
        break
      }
      default:
        break
    }
  }

  private handleClose(): void {
    this.stopHeartbeat()
    this.ws = null
    if (this.manualClose) {
      this.setState('closed')
      return
    }
    this.scheduleReconnect()
  }

  private scheduleReconnect(): void {
    this.setState('reconnecting')
    const stepIndex = Math.min(this.reconnectAttempts, RECONNECT_BACKOFF_STEPS_MS.length - 1)
    const delay = RECONNECT_BACKOFF_STEPS_MS[stepIndex]
    this.reconnectAttempts += 1
    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = null
      if (!this.manualClose) this.openSocket()
    }, delay)
  }

  private clearReconnectTimer(): void {
    if (this.reconnectTimer !== null) {
      window.clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
  }

  private startHeartbeat(): void {
    this.stopHeartbeat()
    this.heartbeatTimer = window.setInterval(() => {
      this.sendFrame(ChatFrameType.HEARTBEAT, {})
    }, HEARTBEAT_INTERVAL_MS)
  }

  private stopHeartbeat(): void {
    if (this.heartbeatTimer !== null) {
      window.clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }

  private setState(state: ChatConnectionState): void {
    this.options.onStateChange?.(state)
  }

  private sendFrame(type: ChatFrameTypeValue, body: unknown): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return
    this.ws.send(encodeFrame(type, body))
  }

  // 记录一条待 ACK 确认的消息，超时未确认则自动重发（同一 msgId），达到重发上限后判定失败
  private trackPending(msgId: string, frameType: ChatFrameTypeValue, body: unknown): void {
    const existing = this.pending.get(msgId)
    if (existing?.timer !== null && existing?.timer !== undefined) {
      window.clearTimeout(existing.timer)
    }
    const entry: PendingSend = { frameType, body, retries: existing?.retries ?? 0, timer: null }
    entry.timer = window.setTimeout(() => this.retryOrFail(msgId), ACK_TIMEOUT_MS)
    this.pending.set(msgId, entry)
  }

  private retryOrFail(msgId: string): void {
    const entry = this.pending.get(msgId)
    if (!entry) return
    if (entry.retries >= MAX_AUTO_RETRIES) {
      this.pending.delete(msgId)
      this.options.onSendFailed?.(msgId)
      return
    }
    entry.retries += 1
    this.sendFrame(entry.frameType, entry.body)
    entry.timer = window.setTimeout(() => this.retryOrFail(msgId), ACK_TIMEOUT_MS)
  }

  private resolvePending(msgId: string): void {
    const entry = this.pending.get(msgId)
    if (!entry) return
    if (entry.timer !== null) window.clearTimeout(entry.timer)
    this.pending.delete(msgId)
  }
}
