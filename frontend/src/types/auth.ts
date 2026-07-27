// 登录表单：username 实际输入的是用户编码（对应 tab_user.code），沿用 username 这个字段名
// 是为了不改动登录页现有输入框语义，展示文案仍然是"用户名"
export interface LoginForm {
  username: string
  password: string
}

// GET /api/auth/public-key 响应
export interface PublicKeyResult {
  publicKey: string
}

// POST /api/auth/login 响应
export interface LoginResult {
  accessKey: string
  // 后端 "yyyy-MM-dd HH:mm:ss" 格式字符串
  accessExpireAt: string
  refreshKey: string
  // 后端 "yyyy-MM-dd HH:mm:ss" 格式字符串
  refreshExpireAt: string
  firstLogin: boolean
}

// POST /api/auth/refresh 响应
export interface RefreshResult {
  accessKey: string
  // 后端 "yyyy-MM-dd HH:mm:ss" 格式字符串
  accessExpireAt: string
}

// POST /api/auth/password 请求体（修改密码，走已登录会话，身份从 identity-token 识别，
// 不含用户标识；明文提交，不需要 RSA 加密）
export interface ChangePasswordForm {
  oldPassword: string
  newPassword: string
}
