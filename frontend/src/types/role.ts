// 角色管理相关类型定义，字段命名和后端 RoleVO / RoleCreateRequest / RoleUpdateRequest DTO 对齐。

// 状态常量：2000 = 启用，3000 = 停用（-1000 为逻辑删除，后端接口已过滤，前端不会拿到）。
export const ROLE_STATUS_ENABLED = 2000
export const ROLE_STATUS_DISABLED = 3000

// 角色列表行/详情数据，来自 GET /api/roles、GET /api/roles/{id}、POST /api/roles、
// PUT /api/roles/{id} 等接口的返回值
export interface RoleRow {
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

// 新增/编辑弹窗提交的请求体，字段与后端 RoleCreateRequest/RoleUpdateRequest 一致
export interface RoleFormRequest {
  name: string
  code: string
  showOrder: number
  remark: string
}

// 不分页的角色选项，来自 GET /api/roles/options，供其他模块（如管理员管理）的
// 角色多选下拉框使用；仅含未删除且启用的角色
export interface RoleOption {
  id: number
  name: string
  code: string
}

// 通用分页响应结构，字段命名和后端 cn.nihility.rbac.common.PageResult 对齐
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}
