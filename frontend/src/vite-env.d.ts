/// <reference types="vite/client" />

// 自定义 Vite 环境变量声明：补充 vite/client 默认 ImportMetaEnv 之外的项目专属变量，
// 供 import.meta.env.VITE_* 在 TS 下有类型提示，不需要每处都用 `as any` 绕过。
interface ImportMetaEnv {
  // 聊天 WebSocket 网关完整地址（含协议/host/port/path），显式设置时优先级最高，
  // 覆盖下面几个分段变量与默认值；未设置时由 src/config/chatGateway.ts 按分段变量
  // 或运行时页面地址推导拼接，见该文件注释。
  readonly VITE_CHAT_WS_URL?: string
  readonly VITE_CHAT_WS_SCHEME?: string
  readonly VITE_CHAT_WS_HOST?: string
  readonly VITE_CHAT_WS_PORT?: string
  readonly VITE_CHAT_WS_PATH?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
