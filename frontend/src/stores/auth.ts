import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { LoginForm } from '@/types/auth'
import * as authApi from '@/api/auth'
import { parseBackendDateTime } from '@/utils/datetime'

const STORAGE_KEY = 'rbac_auth_session'

// 本地持久化的登录态快照
interface AuthSession {
  accessKey: string
  accessExpireAt: number
  refreshKey: string
  refreshExpireAt: number
  firstLogin: boolean
  // 登录表单里输入的用户编码，仅用于界面展示（问候语/头像），后端登录响应本身不携带
  // 用户资料字段，不作为身份凭据使用
  accountCode: string
}

function loadSession(): Partial<AuthSession> {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) return {}
  try {
    return JSON.parse(raw) as AuthSession
  } catch {
    return {}
  }
}

export const useAuthStore = defineStore('auth', () => {
  const initial = loadSession()

  const accessKey = ref(initial.accessKey ?? '')
  const accessExpireAt = ref(initial.accessExpireAt ?? 0)
  const refreshKey = ref(initial.refreshKey ?? '')
  const refreshExpireAt = ref(initial.refreshExpireAt ?? 0)
  const firstLogin = ref(initial.firstLogin ?? false)
  const accountCode = ref(initial.accountCode ?? '')

  function persist() {
    const session: AuthSession = {
      accessKey: accessKey.value,
      accessExpireAt: accessExpireAt.value,
      refreshKey: refreshKey.value,
      refreshExpireAt: refreshExpireAt.value,
      firstLogin: firstLogin.value,
      accountCode: accountCode.value,
    }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
  }

  // access-key 是否仍在本地判定的有效期内
  const isAccessValid = computed(() => Boolean(accessKey.value) && accessExpireAt.value > Date.now())
  // refresh-key 是否仍在本地判定的有效期内
  const isRefreshValid = computed(() => Boolean(refreshKey.value) && refreshExpireAt.value > Date.now())
  // 只要 access-key 或 refresh-key 任一未过期就算"已登录"：access 过期但 refresh 未过期时，
  // 交给请求拦截器静默刷新，不应该在路由守卫这一层直接判定未登录并踢回登录页
  const isLoggedIn = computed(() => isAccessValid.value || isRefreshValid.value)

  async function login(form: LoginForm) {
    const result = await authApi.login(form)
    accessKey.value = result.accessKey
    accessExpireAt.value = parseBackendDateTime(result.accessExpireAt)
    refreshKey.value = result.refreshKey
    refreshExpireAt.value = parseBackendDateTime(result.refreshExpireAt)
    firstLogin.value = result.firstLogin
    accountCode.value = form.username
    persist()
  }

  // 用当前 refreshKey 静默换取新的 accessKey，返回新 accessKey 供调用方（axios 拦截器）
  // 重试原始请求；供 request.ts 的单飞刷新逻辑调用
  async function refreshAccess(): Promise<string> {
    const result = await authApi.refresh(refreshKey.value)
    accessKey.value = result.accessKey
    accessExpireAt.value = parseBackendDateTime(result.accessExpireAt)
    persist()
    return accessKey.value
  }

  function setFirstLogin(value: boolean) {
    firstLogin.value = value
    persist()
  }

  function logout() {
    accessKey.value = ''
    accessExpireAt.value = 0
    refreshKey.value = ''
    refreshExpireAt.value = 0
    firstLogin.value = false
    accountCode.value = ''
    localStorage.removeItem(STORAGE_KEY)
  }

  return {
    accessKey,
    accessExpireAt,
    refreshKey,
    refreshExpireAt,
    firstLogin,
    accountCode,
    isLoggedIn,
    isRefreshValid,
    login,
    refreshAccess,
    setFirstLogin,
    logout,
  }
})
