import type { LoginForm, UserInfo } from '@/types/auth'

// backend/ 目前还没有鉴权接口，这里先用本地模拟登录占位，
// 接口就绪后把函数体换成 request.post('/auth/login', form) 即可，调用方不用改。
export function login(form: LoginForm): Promise<{ token: string; userInfo: UserInfo }> {
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      if (form.username === 'admin' && form.password === 'admin123') {
        resolve({
          token: 'mock-token-' + Date.now(),
          userInfo: {
            id: '1',
            username: 'admin',
            displayName: '系统管理员',
            avatar: '',
            roles: ['super-admin'],
          },
        })
      } else {
        reject(new Error('用户名或密码不正确'))
      }
    }, 600)
  })
}
