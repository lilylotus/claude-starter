// 应用管理相关类型定义，字段命名和后端 AppVO / AppCreateRequest / AppUpdateRequest DTO 对齐。

// 状态常量：2000 = 启用，3000 = 停用（-1000 为逻辑删除，后端接口已过滤，前端不会拿到）。
export const APP_STATUS_ENABLED = 2000
export const APP_STATUS_DISABLED = 3000

// 应用列表行/详情数据，来自 GET /api/apps、GET /api/apps/{id}、POST /api/apps、
// PUT /api/apps/{id} 等接口的返回值
export interface AppRow {
  id: number
  name: string
  code: string
  ownerId: number
  ownerName: string
  orgId: number
  orgName: string
  showOrder: number
  remark: string
  status: number
  createBy: string
  createTime: string
  updateBy: string
  updateTime: string
}

// 新增/编辑弹窗提交的请求体，字段与后端 AppCreateRequest/AppUpdateRequest 一致
export interface AppFormRequest {
  name: string
  code: string
  ownerId: number
  orgId: number
  showOrder: number
  remark: string
}

// 通用分页响应结构，字段命名和后端 cn.nihility.rbac.common.PageResult 对齐
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}
