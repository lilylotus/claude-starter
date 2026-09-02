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

// SSO 登录页支持的认证方式，与后端 AppAuthConfigEntity.loginMethods 取值对齐；
// PASSWORD 恒定出现，SMS/QRCODE 按应用配置决定是否出现
export type SsoLoginMethod = 'PASSWORD' | 'SMS' | 'QRCODE'

// GET /api/authn/sso/login-methods?redirect= 响应：当前这次登录（按 redirect 反解出的
// 目标应用）允许展示的认证方式列表；redirect 缺失/解析不出应用时固定为 ['PASSWORD']
export type SsoLoginMethodListResult = SsoLoginMethod[]

// GET /api/authn/sso/session/status 响应：当前浏览器（仅看 Cookie，不看 redirect）
// 是否已有有效 SSO 会话，扫码确认页据此决定展示口令登录表单还是"确认登录"按钮
export interface SsoSessionStatusResult {
  authenticated: boolean
}

// POST /api/authn/sso/sms/code 请求体：发送短信验证码，出于防枚举考虑，无论手机号是否
// 真实存在均返回成功
export interface SsoSmsCodeRequest {
  redirect: string
  mobile: string
}

// POST /api/authn/sso/sms/login 请求体：校验验证码并完成登录
export interface SsoSmsLoginRequest {
  redirect: string
  mobile: string
  code: string
}

// POST /api/authn/sso/qrcode/session 请求体
export interface SsoQrcodeSessionRequest {
  redirect: string
}

// POST /api/authn/sso/qrcode/session 响应：token 为一次性令牌，confirmPath 为确认页的
// 相对路径（含 ?token= 查询参数），前端需自行拼接 window.location.origin 得到完整地址
// 再渲染成二维码
export interface SsoQrcodeSessionResult {
  token: string
  confirmPath: string
}

// 扫码会话状态：待扫码/已扫码待确认/已确认（本次响应已附带登录 Cookie）/已过期
export type SsoQrcodeStatus = 'PENDING' | 'SCANNED' | 'CONFIRMED' | 'EXPIRED'

// GET /api/authn/sso/qrcode/{token}/status 响应
export interface SsoQrcodeStatusResult {
  status: SsoQrcodeStatus
  firstLogin: boolean | null
}
