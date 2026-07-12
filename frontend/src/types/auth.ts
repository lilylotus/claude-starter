// 登录表单
export interface LoginForm {
  username: string
  password: string
}

// 登录成功后的用户信息（字段命名与后端 DTO 对齐，后端接口就绪后按此结构对接）
export interface UserInfo {
  id: string
  username: string
  displayName: string
  avatar: string
  roles: string[]
}
