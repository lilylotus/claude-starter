// 权限管理相关类型定义，字段命名和后端 PermissionVO / PermissionCreateRequest /
// PermissionUpdateRequest DTO 对齐。

// 状态常量：2000 = 启用，3000 = 停用（-1000 为逻辑删除，后端接口已过滤，前端不会拿到）。
export const PERMISSION_STATUS_ENABLED = 2000
export const PERMISSION_STATUS_DISABLED = 3000

// 权限点列表行/详情数据，来自 GET /api/permissions、GET /api/permissions/{id}、
// POST /api/permissions、PUT /api/permissions/{id} 等接口的返回值
export interface PermissionRow {
  id: number
  name: string
  code: string
  showOrder: number
  remark: string
  status: number
  createBy: string
  createTime: string
  updateBy: string
  updateTime: string
}

// 新增/编辑弹窗提交的请求体，字段与后端 PermissionCreateRequest/PermissionUpdateRequest 一致
export interface PermissionFormRequest {
  name: string
  code: string
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

// 权限点精简选项，来自 GET /api/permissions/options，供角色表单的权限点勾选控件
// 一次性加载全量可选项使用；仅含未删除且启用的权限点，字段与后端 PermissionOptionVO 对齐
export interface PermissionOption {
  id: number
  name: string
  code: string
}
