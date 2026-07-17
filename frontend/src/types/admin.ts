// 管理员管理相关类型定义，字段命名和后端 AdminVO / AdminCreateRequest / AdminUpdateRequest DTO 对齐。

// 状态常量：2000 = 启用，3000 = 停用（-1000 为逻辑删除，后端接口已过滤，前端不会拿到）。
export const ADMIN_STATUS_ENABLED = 2000
export const ADMIN_STATUS_DISABLED = 3000

// 管理员名下关联的一个角色，来自 GET /api/admins/{id} 详情接口内嵌的 roles 数组
// （分页列表接口不填充该字段，避免 N+1）
export interface AdminRoleRow {
  roleId: number
  roleName: string
}

// 管理员名下管辖的一个组织范围，来自 GET /api/admins/{id} 详情接口内嵌的
// orgScopes 数组（分页列表接口不填充该字段，避免 N+1）
export interface AdminOrgScopeRow {
  orgId: number
  orgName: string
  includeChildren: boolean
}

// 管理员列表行/详情数据，来自 GET /api/admins、GET /api/admins/{id}、POST /api/admins、
// PUT /api/admins/{id} 等接口的返回值；分页列表接口返回时 roles/orgScopes 恒为空数组
export interface AdminRow {
  id: number
  name: string
  code: string
  userId: number
  userName: string
  roles: AdminRoleRow[]
  orgScopes: AdminOrgScopeRow[]
  showOrder: number
  remark: string
  status: number
  createBy: string
  createTime: string
  updateBy: string
  updateTime: string
}

// 新增/编辑弹窗内，管辖组织范围子表单一行的数据结构
export interface AdminOrgScopeFormItem {
  orgId: number | null
  includeChildren: boolean
}

// 新增/编辑管理员的请求体，roleIds/orgScopes 为该管理员关联数据的完整列表，
// 提交时后端整体同步（先删除既有关联行，再按列表整批插入）
export interface AdminFormRequest {
  name: string
  code: string
  userId: number
  roleIds: number[]
  orgScopes: Array<{ orgId: number; includeChildren: boolean }>
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
