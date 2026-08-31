import request from './request'
import type {
  AddConversationMemberRequest,
  ChatMessageVO,
  ConversationMemberVO,
  ConversationVO,
  CreateGroupConversationRequest,
  PageResult,
  SensitiveWordCreateRequest,
  SensitiveWordVO,
} from '@/types/chat'

// 聊天会话/消息/敏感词管理的 REST 接口封装，组件/store 不直接调用 axios。
// 单聊/群聊消息的实时收发走 WebSocket 网关（见 src/utils/chatSocket.ts），不经过这里。

// 查询当前用户参与的会话列表（单聊+群聊），按最近消息时间倒序
export function getConversations(): Promise<ConversationVO[]> {
  return request.get('/chat/conversations')
}

// 分页查询会话历史消息：不传 beforeSeq 时查最新一页；返回结果按 conversationSeq 降序
// （最新在前），调用方展示前需自行反转为时间正序
export function getMessages(
  conversationId: number,
  beforeSeq?: number,
  pageSize = 20,
): Promise<PageResult<ChatMessageVO>> {
  return request.get(`/chat/conversations/${conversationId}/messages`, { params: { beforeSeq, pageSize } })
}

// 创建群聊：当前登录用户自动成为群主并计入初始成员，memberUserIds 不需要包含自己
export function createGroupConversation(data: CreateGroupConversationRequest): Promise<ConversationVO> {
  return request.post('/chat/conversations/group', data)
}

// 查询群成员列表，调用方须是该会话的当前成员（单聊也可调用，返回双方两条成员记录，
// 供解析对方 userId 用于 WebSocket 发送单聊消息）
export function getConversationMembers(conversationId: number): Promise<ConversationMemberVO[]> {
  return request.get(`/chat/conversations/${conversationId}/members`)
}

// 添加群成员，已在群内的成员按幂等处理，不重复添加
export function addConversationMembers(conversationId: number, userIds: number[]): Promise<void> {
  const data: AddConversationMemberRequest = { userIds }
  return request.post(`/chat/conversations/${conversationId}/members`, data)
}

// 移除群成员/退出群聊：targetUserId 等于当前用户即为主动退出，否则需群主权限
export function removeConversationMember(conversationId: number, targetUserId: number): Promise<void> {
  return request.delete(`/chat/conversations/${conversationId}/members/${targetUserId}`)
}

// ---- 敏感词后台管理 ----

// 分页查询敏感词，keyword/status/page/pageSize 均可选
export function getSensitiveWordPage(params: {
  keyword?: string
  status?: number
  page?: number
  pageSize?: number
}): Promise<PageResult<SensitiveWordVO>> {
  return request.get('/chat/sensitive-words', { params })
}

// 新增敏感词
export function createSensitiveWord(data: SensitiveWordCreateRequest): Promise<SensitiveWordVO> {
  return request.post('/chat/sensitive-words', data)
}

// 删除敏感词（物理删除）
export function deleteSensitiveWord(id: number): Promise<void> {
  return request.delete(`/chat/sensitive-words/${id}`)
}

// 启用敏感词
export function enableSensitiveWord(id: number): Promise<SensitiveWordVO> {
  return request.put(`/chat/sensitive-words/${id}/enable`)
}

// 停用敏感词
export function disableSensitiveWord(id: number): Promise<SensitiveWordVO> {
  return request.put(`/chat/sensitive-words/${id}/disable`)
}
