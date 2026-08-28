import axios, { type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '@/types/api'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

// 公钥获取/登录/刷新接口不需要携带 identity-token/menu 请求头（登录前或未持有有效
// access-key 时调用），也不参与"access-key 过期 -> 静默刷新重试"的逻辑，避免刷新
// 接口自身失败时递归触发刷新
const AUTH_HEADER_WHITELIST = ['/auth/public-key', '/auth/login', '/auth/refresh']

function isAuthWhitelisted(url?: string): boolean {
  if (!url) return false
  return AUTH_HEADER_WHITELIST.some((prefix) => url.startsWith(prefix))
}

const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

request.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  if (isAuthWhitelisted(config.url)) {
    return config
  }
  const authStore = useAuthStore()
  if (authStore.accessKey) {
    config.headers.set('identity-token', authStore.accessKey)
  }
  // menu 头优先取调用方显式传入的值（如 getMyPermissions() 这类不依附于当前路由的
  // 自助接口，调用时机可能早于路由跳转完成，这时 currentRoute 还是旧路由甚至没有
  // permissionKey，硬取会导致头缺失/错误进而触发下面的"刷新重试"死循环），
  // 显式值缺失时才回退到当前路由的 permissionKey
  if (!config.headers.get('menu')) {
    const permissionKey = router.currentRoute.value.meta.permissionKey
    if (permissionKey) {
      config.headers.set('menu', permissionKey)
    }
  }
  return config
})

// 单飞（single-flight）静默刷新：并发的多个请求同时遇到 access-key 过期时，只发起一次
// 刷新接口调用，其余请求共享同一个 Promise，都等它 resolve 后各自用新 accessKey 重试
let refreshingPromise: Promise<string> | null = null

function triggerRefresh(): Promise<string> {
  if (!refreshingPromise) {
    const authStore = useAuthStore()
    refreshingPromise = authStore.refreshAccess().finally(() => {
      refreshingPromise = null
    })
  }
  return refreshingPromise
}

function redirectToLogin() {
  const authStore = useAuthStore()
  authStore.logout()
  const redirect = router.currentRoute.value.fullPath
  if (router.currentRoute.value.name !== 'login') {
    router.push({ name: 'login', query: { redirect } })
  }
}

request.interceptors.response.use(
  async (response): Promise<any> => {
    // Excel 导入模板下载等接口返回的是原始文件流（非 { code, message, data } 包装
    // 结构，见 cn.nihility.rbac.excelimport.controller.ExcelImportController#downloadTemplate），
    // 调用方通过 responseType: 'blob' 显式声明，这里直接透传响应体，跳过业务码解包
    if (response.config.responseType === 'blob') {
      return response.data
    }
    const body = response.data as ApiResponse
    if (body.code === 0) {
      return body.data
    }

    // 后端 IdentityAuthFilter 身份校验失败/首登拦截时 HTTP 状态码始终是 200，只有响应体
    // 里的 code 是 401（未登录/access-key 过期/menu 头缺失或非法）或 4010（首登强制改密），
    // 因此这两种情况都会走到成功回调这里，而不是下面的错误处理函数
    if (body.code === 401) {
      // originalConfig 上打的 _retriedAfterRefresh 标记用于限制"刷新后重试"最多发生一次：
      // 如果重试后的这次请求依然拿到 401（说明问题不是 access-key 过期能解决的，比如后端
      // 对这个具体请求的鉴权判断本身有问题），旧代码会在这里再次判定 isRefreshValid 为真
      // 从而无限重复"刷新 -> 重试 -> 401 -> 刷新"，表现为不断请求 /api/auth/refresh 并
      // 卡死界面；加上这个标记后，同一个原始请求最多只触发一次静默刷新重试
      const originalConfig = response.config as typeof response.config & { _retriedAfterRefresh?: boolean }
      if (
        !isAuthWhitelisted(originalConfig.url) &&
        !originalConfig._retriedAfterRefresh &&
        useAuthStore().isRefreshValid
      ) {
        originalConfig._retriedAfterRefresh = true
        try {
          const newAccessKey = await triggerRefresh()
          originalConfig.headers.set('identity-token', newAccessKey)
          return request(originalConfig)
        } catch {
          // 刷新失败，落到下面统一的"清空登录态并跳转登录页"逻辑
        }
      }
      redirectToLogin()
      return Promise.reject(new Error(body.message || '登录已过期，请重新登录'))
    }

    if (body.code === 4010) {
      const authStore = useAuthStore()
      authStore.setFirstLogin(true)
      if (router.currentRoute.value.name !== 'change-password') {
        router.push({ name: 'change-password' })
      }
      return Promise.reject(new Error(body.message || '请先修改密码'))
    }

    // 403：已登录但当前用户的角色权限点集合不包含请求的 menu 编码（IdentityAuthFilter
    // 新增的运行时鉴权判断），不跳转、不清空登录态，只提示，走和其余未识别业务错误码
    // 相同的兜底路径，仅把默认文案换成更贴切的说法
    if (body.code === 403) {
      ElMessage.error(body.message || '无权限访问')
      return Promise.reject(new Error(body.message || '无权限访问'))
    }

    ElMessage.error(body.message || '请求失败')
    return Promise.reject(new Error(body.message || '请求失败'))
  },
  (error) => {
    if (error.response?.status === 401) {
      redirectToLogin()
      ElMessage.error('登录已过期，请重新登录')
    } else {
      ElMessage.error(error.message || '网络异常')
    }
    return Promise.reject(error)
  },
)

export default request
