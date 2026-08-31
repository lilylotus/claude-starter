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
  content: string
  filtered: boolean
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
  content: string
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
  sendTime: string
  offline: boolean
}

// ERROR 帧体（服务端 -> 客户端），code 取值见后端 ChatErrorCode（1001~1006）
export interface ErrorFrameBody {
  code: number
  message: string
  msgId: string | null
}
