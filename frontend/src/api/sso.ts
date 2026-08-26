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
  SsoLoginResult,
  SsoPublicKeyResult,
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
