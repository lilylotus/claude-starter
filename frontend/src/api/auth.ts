import request from './request'
import { rsaEncrypt } from '@/utils/rsa'
import type {
  ChangePasswordForm,
  LoginForm,
  LoginResult,
  PublicKeyResult,
  RefreshResult,
} from '@/types/auth'

// 获取当前生效的 RSA 公钥（Base64 编码），登录前调用，无需身份校验请求头
export function getPublicKey(): Promise<PublicKeyResult> {
  return request.get('/auth/public-key')
}

// 账号密码登录：先取公钥，用 RSA-OAEP 分别加密账号（用户编码）、密码后再提交，
// 网络请求体中不出现明文密码
export async function login(form: LoginForm): Promise<LoginResult> {
  const { publicKey } = await getPublicKey()
  const [account, password] = await Promise.all([
    rsaEncrypt(publicKey, form.username),
    rsaEncrypt(publicKey, form.password),
  ])
  return request.post('/auth/login', { account, password })
}

// 用 refresh-key 静默换取新的 access-key（refresh-key 本身不变，沿用旧的）
export function refresh(refreshKey: string): Promise<RefreshResult> {
  return request.post('/auth/refresh', { refreshKey })
}

// 修改密码：走已登录会话（身份从 identity-token 识别），明文提交，不需要 RSA 加密
export function changePassword(form: ChangePasswordForm): Promise<void> {
  return request.post('/auth/password', form)
}
