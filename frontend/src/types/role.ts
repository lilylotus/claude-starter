// 角色管理相关类型定义，字段命名和后端 RoleVO / RoleCreateRequest / RoleUpdateRequest DTO 对齐。
import type { PermissionOption } from '@/types/permission'

// 状态常量：2000 = 启用，3000 = 停用（-1000 为逻辑删除，后端接口已过滤，前端不会拿到）。
export const ROLE_STATUS_ENABLED = 2000
export const ROLE_STATUS_DISABLED = 3000

// 角色列表行/详情数据，来自 GET /api/roles、GET /api/roles/{id}、POST /api/roles、
// PUT /api/roles/{id} 等接口的返回值；permissions（已分配权限点）只有详情接口
// （GET /api/roles/{id}）才会返回，分页列表行上该字段为 undefined
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
  permissions?: PermissionOption[]
}

// 新增/编辑弹窗提交的请求体，字段与后端 RoleCreateRequest/RoleUpdateRequest 一致；
// permissionIds 可选，不传或空数组代表不授予/清空全部权限点
export interface RoleFormRequest {
  name: string
  code: string
  showOrder: number
  remark: string
  permissionIds: number[]
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

// ---- 用户角色规则（角色管理"批量规则"入口，/api/user-role-rules 系列接口） ----
// 参见 openspec/changes/add-user-role-batch-assignment/design.md Decision 4：规则是持久化
// 保存的条件配置，保存后组织/用户/任职变化时后端自动重新计算命中结果，不再是"一次性执行
// 即止"的批量操作；组织范围条件、用户属性条件均可选数组，但两者至少一类非空。

// 用户属性条件运算符，语义与 app-access-authorization 的 PolicyAttrOperator 一致：
// EQ/NE 比较值为单值，IN 为一组值
export type UserRoleAttrOperator = 'EQ' | 'NE' | 'IN'

export const USER_ROLE_ATTR_OPERATOR_OPTIONS: Array<{ value: UserRoleAttrOperator; label: string }> = [
  { value: 'EQ', label: '等于' },
  { value: 'NE', label: '不等于' },
  { value: 'IN', label: '属于多值' },
]

// 组织范围条件提交请求体元素，对应后端 orgScopes 数组元素
export interface UserRoleOrgScopeRequestItem {
  orgId: number
  includeChildren: boolean
}

// 组织范围条件表单内一行的本地可编辑结构，orgId 允许暂时为空（由用户手动选择）
export interface UserRoleOrgScopeFormItem {
  orgId: number | null
  includeChildren: boolean
}

// 用户属性条件提交请求体元素，对应后端 userAttrs 数组元素；EQ/NE 时 values 只含一个元素，
// IN 时可含多个元素
export interface UserRoleUserAttrRequestItem {
  metadataFieldId: number
  operator: UserRoleAttrOperator
  values: string[]
}

// 用户属性条件表单内一行的本地可编辑结构，metadataFieldId 允许暂时为空；singleValue 用于
// EQ/NE，multiValues 用于 IN，两者互斥展示，提交前按 operator 收敛成 values 数组
export interface UserRoleUserAttrFormItem {
  metadataFieldId: number | null
  operator: UserRoleAttrOperator
  singleValue: string
  multiValues: string[]
}

// 组织范围条件、用户属性条件两组，规则的新增/预览/编辑请求体共用
export interface UserRoleConditions {
  orgScopes: UserRoleOrgScopeRequestItem[]
  userAttrs: UserRoleUserAttrRequestItem[]
}

// 预览命中用户一行，来自 POST /api/user-role-rules/preview 的分页响应记录
export interface UserRoleMatchedUserRow {
  userId: number
  userName: string
  userCode: string
  orgName: string
}

// 规则列表行，来自 GET /api/user-role-rules?roleId=。保持轻量，不内嵌 orgScopes/userAttrs，
// 与 AdminRow（列表不带子集合、详情才带，避免 N+1）同一套约定；编辑规则表单需要完整条件时
// 改为单独调用 GET /api/user-role-rules/{id}（见下方 UserRoleRuleDetail）
export interface UserRoleRuleRow {
  id: number
  name: string
  remark: string
  lastExecTime: string | null
  matchedUserCount: number
}

// 规则详情，来自 GET /api/user-role-rules/{id}，供编辑规则表单打开时单独请求回填完整条件
export interface UserRoleRuleDetail {
  id: number
  name: string
  remark: string
  orgScopes: UserRoleOrgScopeRequestItem[]
  userAttrs: UserRoleUserAttrRequestItem[]
}

// 新增/编辑规则表单提交的请求体（创建接口额外附带 roleId，编辑接口不含 roleId，规则归属
// 角色不可改；api/role.ts 里两个函数各自拼接 roleId，组件侧统一用这个类型即可）
export interface UserRoleRuleFormRequest extends UserRoleConditions {
  name: string
  remark: string
}

// 创建/编辑规则保存成功后的响应：规则已保存并立即重新执行一次，附带本次命中数量
export interface UserRoleRuleSaveResult {
  id: number
  name: string
  matchedUserCount: number
}
