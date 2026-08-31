// 聊天 WebSocket 网关地址配置（chat-gateway-core change design.md Decision 9）。
//
// 后端聊天网关与业务 HTTP 端口分离监听独立端口（backend/src/main/resources/application.yml
// `chat.gateway.port`，本地开发默认 48091；`chat.gateway.websocket-path` 默认 `/ws/chat`；
// `chat.gateway.tls.enabled` 本地开发默认 false，即本地用 `ws://` 而不是 `wss://`，浏览器
// 不需要额外信任自签名证书），因此不能像 `/api` 那样简单复用 vite.config.ts 的反向代理
// （反向代理面向的是同源 HTTP 请求，WebSocket 长连接经代理转发会引入额外的复杂度，本阶段
// 单节点场景下没有必要）——前端直接按下面的规则拼出网关地址，绕开 5173 开发服务器直连后端。
//
// 可通过 Vite 环境变量覆盖（新建 frontend/.env.local，不提交到仓库），无需改代码即可指向
// 不同的 host/port（如测试环境的独立域名 + wss），按优先级：
//   1. VITE_CHAT_WS_URL：完整地址（如 wss://chat.example.com/ws/chat），设置后忽略下面几项
//   2. VITE_CHAT_WS_SCHEME / VITE_CHAT_WS_HOST / VITE_CHAT_WS_PORT / VITE_CHAT_WS_PATH：分段覆盖
//   3. 都未设置时的默认值：scheme 跟随当前页面协议（https 页面下用 wss，否则用 ws），
//      host 跟随当前页面 hostname（与后端 HTTP 部署在同一台机器时开箱可用），
//      port 默认 48091，path 默认 /ws/chat

const DEFAULT_PORT = '48091'
const DEFAULT_PATH = '/ws/chat'

// 解析出当前应使用的聊天网关 WebSocket 地址
export function resolveChatGatewayUrl(): string {
  const explicitUrl = import.meta.env.VITE_CHAT_WS_URL
  if (explicitUrl) return explicitUrl

  const scheme = import.meta.env.VITE_CHAT_WS_SCHEME || (window.location.protocol === 'https:' ? 'wss' : 'ws')
  const host = import.meta.env.VITE_CHAT_WS_HOST || window.location.hostname
  const port = import.meta.env.VITE_CHAT_WS_PORT || DEFAULT_PORT
  const path = import.meta.env.VITE_CHAT_WS_PATH || DEFAULT_PATH
  return `${scheme}://${host}:${port}${path}`
}
