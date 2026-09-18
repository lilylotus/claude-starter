// 聊天模块类型定义：REST 接口 DTO 字段与后端 cn.nihility.rbac.chat.dto 下的
// ConversationVO/ConversationMemberVO/ChatMessageVO/SensitiveWordVO 等对齐；
// WebSocket 协议帧体（LOGIN/ACK/MESSAGE_PUSH 等）字段与后端
// cn.nihility.rbac.chat.gateway.protocol.body 下同名类对齐（design.md Decision 2/3/9）。

// ---- 会话类型/成员角色/状态常量（与后端 ConversationType/ConversationMemberRole/
// ConversationMemberStatus/SensitiveWordStatus 常量对齐） ----

export const CONVERSATION_TYPE_SINGLE = 1
export const CONVERSATION_TYPE_GROUP = 2

export const CONVERSATION_MEMBER_ROLE_OWNER = 1
export const CONVERSATION_MEMBER_ROLE_MEMBER = 2

export const CONVERSATION_MEMBER_STATUS_NORMAL = 2000
export const CONVERSATION_MEMBER_STATUS_LEFT = 3000

export const SENSITIVE_WORD_STATUS_ENABLED = 2000
export const SENSITIVE_WORD_STATUS_DISABLED = 3000

// ---- REST DTO ----

// GET /api/chat/conversations 返回项
export interface ConversationVO {
  id: number
  conversationType: number
  name: string
  memberCount: number
  lastMessageContent: string | null
  lastMessageSenderId: number | null
  // 后端 "yyyy-MM-dd HH:mm:ss" 格式字符串
  lastMessageSendTime: string | null
  createTime: string
}

// GET /api/chat/conversations/{id}/members 返回项
export interface ConversationMemberVO {
  userId: number
  userName: string
  role: number
  joinedTime: string
  status: number
}

// GET /api/chat/conversations/{id}/messages 返回项（分页结果按 conversationSeq 降序，
// 组件展示前需自行反转为时间正序，见 api/chat.ts getMessages 注释）
export interface ChatMessageVO {
  id: number
  msgId: string
  conversationId: number
  conversationSeq: number
  senderId: number
  senderName: string
  msgType: number
  // 群聊为明文；单聊为客户端生成的密文信封 JSON 字符串（chatCrypto.ChatCiphertextEnvelope
  // 序列化结果），服务端只透传/落库，前端需在本地解密后展示（chat-end-to-end-encryption
  // change design.md Decision 1）
  content: string
  filtered: boolean
  // 单聊消息发送方身份公钥指纹快照；群聊消息/历史明文消息为空。前端据此判断本条消息是否
  // 需要走解密路径，不依赖会话类型查表（对"本地尚未同步到的新会话"场景更健壮）
  senderIdentityKeyFingerprint: string | null
  sendTime: string
}

export interface SensitiveWordVO {
  id: number
  word: string
  status: number
  createBy: string
  createTime: string
  updateBy: string
  updateTime: string
}

// POST /api/chat/conversations/group 请求体
export interface CreateGroupConversationRequest {
  name: string
  memberUserIds: number[]
}

// POST /api/chat/conversations/{id}/members 请求体
export interface AddConversationMemberRequest {
  userIds: number[]
}

// POST /api/chat/sensitive-words 请求体
export interface SensitiveWordCreateRequest {
  word: string
}

// 通用分页响应结构，字段命名和后端 cn.nihility.rbac.common.result.PageResult 对齐
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}

// ---- WebSocket 协议帧类型（design.md Decision 2/3：10 字节固定帧头 + UTF-8 JSON body）----

// 帧头消息类型字节值，和后端 cn.nihility.rbac.chat.gateway.protocol.ChatFrameType 对齐
export const ChatFrameType = {
  LOGIN: 0x01,
  LOGIN_ACK: 0x02,
  HEARTBEAT: 0x03,
  HEARTBEAT_ACK: 0x04,
  CHAT_SINGLE: 0x05,
  CHAT_GROUP: 0x06,
  MESSAGE_PUSH: 0x07,
  ACK: 0x08,
  ERROR: 0x09,
} as const

export type ChatFrameTypeValue = (typeof ChatFrameType)[keyof typeof ChatFrameType]

// LOGIN 帧体（客户端 -> 服务端）
export interface LoginFrameBody {
  accessKey: string
}

// LOGIN_ACK 帧体（服务端 -> 客户端）
export interface LoginAckFrameBody {
  success: boolean
  userId: number | null
  message: string
}

// CHAT_SINGLE 帧体（客户端 -> 服务端）
export interface ChatSingleFrameBody {
  msgId: string
  toUserId: number
  msgType: number
  // 客户端生成的密文信封 JSON 字符串，服务端不解析（见 ChatMessageVO.content 注释）
  content: string
  // 发送方身份公钥指纹快照，服务端原样落库供接收端做安全码比对
  senderIdentityKeyFingerprint: string
}

// CHAT_GROUP 帧体（客户端 -> 服务端）
export interface ChatGroupFrameBody {
  msgId: string
  conversationId: number
  msgType: number
  content: string
}

// ACK 帧体（服务端 -> 客户端）
export interface AckFrameBody {
  msgId: string
  conversationId: number
  conversationSeq: number
  sendTime: string
}

// MESSAGE_PUSH 帧体（服务端 -> 客户端，实时投递与离线补偿推送共用）
export interface MessagePushFrameBody {
  msgId: string
  conversationId: number
  conversationSeq: number
  senderId: number
  msgType: number
  content: string
  // 单聊消息发送方身份公钥指纹快照；群聊消息为空
  senderIdentityKeyFingerprint: string | null
  sendTime: string
  offline: boolean
}

// ERROR 帧体（服务端 -> 客户端），code 取值见后端 ChatErrorCode（1001~1006）
export interface ErrorFrameBody {
  code: number
  message: string
  msgId: string | null
}

// ---- 聊天密钥目录（单聊端到端加密，chat-end-to-end-encryption change） ----

// POST /api/v1/chat/keys/me 请求体
export interface ChatKeyRegisterRequest {
  // X25519 身份公钥，Base64 编码
  identityPublicKey: string
}

// POST /api/v1/chat/keys/me、GET /api/v1/chat/keys/{userId} 响应
export interface ChatUserKeyVO {
  userId: number
  identityPublicKey: string
  // 服务端留痕用指纹（对 identityPublicKey 原始字符串做 SHA-256），仅供审计；客户端做
  // 安全码比对/TOFU 判断必须使用自己对解码后公钥字节计算出的指纹
  // （chatCrypto.computeFingerprint），不能直接信任这个字段
  keyFingerprint: string
  updateTime: string
}
