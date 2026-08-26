// SSO 专用登录页面/接口的类型定义，独立于管理端 src/types/auth.ts
// （SSO 登录接口与管理端登录接口字段形状恰好相同，但语义/生命周期完全独立，
// 不共享类型定义，避免两边被动耦合）

// GET /api/authn/sso/public-key 响应
export interface SsoPublicKeyResult {
  publicKey: string
}

// POST /api/authn/sso/login 请求体：account/password 均为 RSA-OAEP（SHA-256）密文
export interface SsoLoginRequest {
  account: string
  password: string
}

// POST /api/authn/sso/login 响应
export interface SsoLoginResult {
  firstLogin: boolean
}

// POST /api/authn/sso/password 请求体，用户身份由 HttpOnly SSO 会话确定
export interface SsoChangePasswordRequest {
  oldPassword: string
  newPassword: string
}
