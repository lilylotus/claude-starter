// 后端统一响应结构：{ code, message, data }
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}
