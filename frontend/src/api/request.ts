import axios, { type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '@/types/api'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

request.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const authStore = useAuthStore()
  if (authStore.token) {
    config.headers.set('Authorization', `Bearer ${authStore.token}`)
  }
  return config
})

request.interceptors.response.use(
  (response): any => {
    // Excel 导入模板下载等接口返回的是原始文件流（非 { code, message, data } 包装
    // 结构，见 cn.nihility.rbac.excelimport.controller.ExcelImportController#downloadTemplate），
    // 调用方通过 responseType: 'blob' 显式声明，这里直接透传响应体，跳过业务码解包
    if (response.config.responseType === 'blob') {
      return response.data
    }
    const body = response.data as ApiResponse
    if (body.code !== 0) {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body.data
  },
  (error) => {
    if (error.response?.status === 401) {
      const authStore = useAuthStore()
      authStore.logout()
      router.push('/login')
      ElMessage.error('登录已过期，请重新登录')
    } else {
      ElMessage.error(error.message || '网络异常')
    }
    return Promise.reject(error)
  },
)

export default request
