import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { LoginForm, UserInfo } from '@/types/auth'
import * as authApi from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem('rbac_token') ?? '')
  const userInfo = ref<UserInfo | null>(
    JSON.parse(localStorage.getItem('rbac_user') ?? 'null'),
  )

  const isLoggedIn = computed(() => Boolean(token.value))

  async function login(form: LoginForm) {
    const result = await authApi.login(form)
    token.value = result.token
    userInfo.value = result.userInfo
    localStorage.setItem('rbac_token', result.token)
    localStorage.setItem('rbac_user', JSON.stringify(result.userInfo))
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('rbac_token')
    localStorage.removeItem('rbac_user')
  }

  return { token, userInfo, isLoggedIn, login, logout }
})
