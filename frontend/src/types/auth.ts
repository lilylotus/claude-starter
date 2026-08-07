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

// GET /api/auth/permissions 响应：当前登录用户拥有的全量三段式权限编码集合，
// 以及管辖组织范围是否受限（供组织管理"上级组织"等选择器收紧可选范围）
export interface PermissionCodesResult {
  codes: string[]
  orgScopeRestricted: boolean
}
