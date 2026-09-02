// SSO 专用登录接口封装：独立于 src/api/request.ts 的最小 axios 实例，不导入/复用
// 那套拦截器（那套逻辑会附带 identity-token/menu 请求头、做静默刷新，语义完全
// 不适用于这里——SSO 登录页面服务于未登录的外部浏览器，不持有任何管理端会话）。
//
// 后端 SSO 登录接口（cn.nihility.rbac.sso.controller.SsoLoginController）走的是
// 普通 Controller + GlobalResponseAdvice，响应体仍是 { code, message, data } 包装
// 结构，因此这里需要自行处理"检查 code === 0、非 0 时提示错误"的逻辑。
//
// 登录成功后后端通过 HttpOnly Set-Cookie 下发浏览器级 SSO 会话，跨域请求需要
// withCredentials: true 才能携带该 Cookie。
import axios from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '@/types/api'
import type {
  SsoChangePasswordRequest,
  SsoLoginMethodListResult,
  SsoLoginResult,
  SsoPublicKeyResult,
  SsoQrcodeSessionResult,
  SsoQrcodeStatusResult,
  SsoSessionStatusResult,
} from '@/types/sso'

const ssoRequest = axios.create({
  baseURL: '/api',
  timeout: 10000,
  withCredentials: true,
})

ssoRequest.interceptors.response.use(
  (response): any => {
    const body = response.data as ApiResponse
    if (body.code === 0) {
      return body.data
    }
    ElMessage.error(body.message || '请求失败')
    return Promise.reject(new Error(body.message || '请求失败'))
  },
  (error) => {
    ElMessage.error(error.message || '网络异常')
    return Promise.reject(error)
  },
)

// 获取 SSO 登录页使用的 RSA 公钥（复用管理端登录同一份密钥材料，仅密钥复用，
// 接口/会话逻辑完全独立）
export function getSsoPublicKey(): Promise<SsoPublicKeyResult> {
  return ssoRequest.get('/authn/sso/public-key')
}

// SSO 账号密码登录：account/password 均为调用方已用 RSA-OAEP 加密好的密文。
// 登录成功后后端下发 HttpOnly 的 sso_session Cookie，并返回是否需要首次登录改密。
export function ssoLogin(account: string, password: string): Promise<SsoLoginResult> {
  return ssoRequest.post('/authn/sso/login', { account, password })
}

// 使用浏览器自动携带的 HttpOnly SSO 会话完成首次登录改密。
export function ssoChangePassword(request: SsoChangePasswordRequest): Promise<void> {
  return ssoRequest.post('/authn/sso/password', request)
}

// 查询本次登录（按 redirect 反解出的目标应用）允许展示的认证方式列表；redirect 为空时
// 传 undefined，后端固定只返回 ['PASSWORD']
export function getSsoLoginMethods(redirect?: string): Promise<SsoLoginMethodListResult> {
  return ssoRequest.get('/authn/sso/login-methods', { params: { redirect } })
}

// 查询当前浏览器（仅看 Cookie）是否已有有效 SSO 会话，供扫码确认页判断展示口令表单
// 还是"确认登录"按钮
export function getSsoSessionStatus(): Promise<SsoSessionStatusResult> {
  return ssoRequest.get('/authn/sso/session/status')
}

// 发送短信验证码；无论手机号是否真实存在均返回成功（防枚举），前端只需提示"验证码已发送"
export function ssoSendSmsCode(redirect: string, mobile: string): Promise<void> {
  return ssoRequest.post('/authn/sso/sms/code', { redirect, mobile })
}

// 校验短信验证码并完成登录，响应结构与口令登录一致（是否需要首登改密）
export function ssoSmsLogin(redirect: string, mobile: string, code: string): Promise<SsoLoginResult> {
  return ssoRequest.post('/authn/sso/sms/login', { redirect, mobile, code })
}

// 创建一次性扫码登录会话，返回令牌与确认页相对路径
export function createSsoQrcodeSession(redirect: string): Promise<SsoQrcodeSessionResult> {
  return ssoRequest.post('/authn/sso/qrcode/session', { redirect })
}

// 查询扫码会话当前状态（PC 端轮询用）；命中 CONFIRMED 时本次响应已附带登录 Cookie
export function getSsoQrcodeStatus(token: string): Promise<SsoQrcodeStatusResult> {
  return ssoRequest.get(`/authn/sso/qrcode/${token}/status`)
}

// 手机浏览器扫码后标记该令牌"已扫码"，仅用于 PC 端 UI 提示，失败也无需处理（尽力而为）
export function markSsoQrcodeScanned(token: string): Promise<void> {
  return ssoRequest.post(`/authn/sso/qrcode/${token}/scan`)
}

// 手机浏览器点击"确认登录"，携带自身 SSO 会话 Cookie 完成确认；调用失败（未登录/令牌
// 失效）走响应拦截器统一提示
export function confirmSsoQrcodeLogin(token: string): Promise<void> {
  return ssoRequest.post(`/authn/sso/qrcode/${token}/confirm`)
}
