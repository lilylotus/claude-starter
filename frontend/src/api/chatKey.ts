import request from './request'
import type { ChatKeyRegisterRequest, ChatUserKeyVO } from '@/types/chat'

// 聊天密钥目录 REST 接口封装（单聊端到端加密，chat-end-to-end-encryption change）。
// 这两个接口显式传 menu 头而不依赖当前路由的 permissionKey：注册本人公钥发生在登录成功
// 之后、进入 /chat 路由之前（此时 router.currentRoute 还是旧路由，没有 Chat 模块的
// permissionKey），依赖当前路由推导会导致 menu 头缺失触发响应拦截器的静默刷新重试死循环
// （与 api/auth.ts getMyPermissions 同样的问题）。权限资源.txt 里 Chat 模块的既有约定是
// "单聊消息的实时收发通过 WebSocket 网关而非 REST 资源，仍复用 Chat:conversation:view
// 作为页面/相关只读查询接口的 menu 请求头"，密钥目录接口同属 Chat 模块的配套能力，沿用
// 同一权限点，不单独登记新权限编码。
const CHAT_MENU = 'Chat:conversation:view'

// 注册/更新本人聊天身份公钥；只能写当前登录用户自己的记录（用户 id 由后端从登录态解析）
export function registerMyKey(data: ChatKeyRegisterRequest): Promise<ChatUserKeyVO> {
  return request.post('/v1/chat/keys/me', data, { headers: { menu: CHAT_MENU } })
}

// 查询目标用户的聊天身份公钥；目标用户尚未注册时后端返回业务错误（非 0 code），
// 响应拦截器会自动 ElMessage 提示并 reject，调用方 catch 后按"无法建立加密会话"处理即可
export function getUserKey(userId: number): Promise<ChatUserKeyVO> {
  return request.get(`/v1/chat/keys/${userId}`, { headers: { menu: CHAT_MENU } })
}
