import request from './request'
import type {
  PageResult,
  RoleFormRequest,
  RoleOption,
  RoleRow,
  UserRoleConditions,
  UserRoleMatchedUserRow,
  UserRoleRuleDetail,
  UserRoleRuleRow,
  UserRoleRuleSaveResult,
} from '@/types/role'

// 角色管理接口封装，组件/store 不直接调用 axios。

// 分页查询角色，不支持筛选参数；page/pageSize 不传时后端分别默认 1、10
export function getRolePage(page?: number, pageSize?: number): Promise<PageResult<RoleRow>> {
  return request.get('/roles', { params: { page, pageSize } })
}

// 获取不分页的角色选项（未删除且启用），供其他模块的角色多选/单选下拉框使用
export function getRoleOptions(): Promise<RoleOption[]> {
  return request.get('/roles/options')
}

// 获取角色详情，用于编辑表单回填与只读详情弹窗
export function getRoleById(id: number): Promise<RoleRow> {
  return request.get(`/roles/${id}`)
}

// 新增角色
export function createRole(data: RoleFormRequest): Promise<RoleRow> {
  return request.post('/roles', data)
}

// 编辑角色
export function updateRole(id: number, data: RoleFormRequest): Promise<RoleRow> {
  return request.put(`/roles/${id}`, data)
}

// 启用角色
export function enableRole(id: number): Promise<void> {
  return request.put(`/roles/${id}/enable`)
}

// 停用角色
export function disableRole(id: number): Promise<void> {
  return request.put(`/roles/${id}/disable`)
}

// 删除角色（逻辑删除）
export function deleteRole(id: number): Promise<void> {
  return request.delete(`/roles/${id}`)
}

// 后端摘要行字段为 hitCount（当前命中人数），这里转换成本模块 UserRoleRuleRow 的
// matchedUserCount 命名，与创建/编辑规则响应的字段风格保持一致
interface UserRoleRuleSummaryRaw {
  id: number
  name: string
  remark: string
  lastExecTime: string | null
  hitCount: number
}

// 查询某角色下的全部用户角色规则（名称、备注、最近执行时间、当前命中人数）；保持轻量，
// 不内嵌 orgScopes/userAttrs，与 GET /admins（AdminRow 不带 roles/orgScopes）同一套约定
export function listUserRoleRules(roleId: number): Promise<UserRoleRuleRow[]> {
  return request
    .get<UserRoleRuleSummaryRaw[], UserRoleRuleSummaryRaw[]>('/user-role-rules', { params: { roleId } })
    .then((rows) =>
      rows.map((row) => ({
        id: row.id,
        name: row.name,
        remark: row.remark,
        lastExecTime: row.lastExecTime,
        matchedUserCount: row.hitCount,
      })),
    )
}

// 获取规则详情（含完整 orgScopes/userAttrs），供编辑规则表单打开时单独请求回填，
// 避免列表接口为每条规则都返回条件子集合造成不必要的 N+1
export function getUserRoleRuleById(ruleId: number): Promise<UserRoleRuleDetail> {
  return request.get(`/user-role-rules/${ruleId}`)
}

// 预览接口响应行字段为 { id, name, code, orgName }（与用户列表接口同款命名，代表"这一行
// 就是一个用户"），这里转换成本模块 UserRoleMatchedUserRow 的 userId/userName/userCode 命名，
// 与页面其余"引用某个用户"的字段风格保持一致
interface UserRoleMatchedUserRaw {
  id: number
  name: string
  code: string
  orgName: string
}

// 纯条件试算（不依赖已保存的规则，也不写库），page/pageSize 用于分页浏览命中名单；
// 使用 post<T, T> 双泛型写法让返回类型对齐 request.ts 拦截器实际解包出来的业务数据，
// 而不是默认泛型下的 AxiosResponse
export function previewUserRoleRule(
  conditions: UserRoleConditions,
  page: number,
  pageSize: number,
): Promise<PageResult<UserRoleMatchedUserRow>> {
  return request
    .post<PageResult<UserRoleMatchedUserRaw>, PageResult<UserRoleMatchedUserRaw>>('/user-role-rules/preview', {
      ...conditions,
      page,
      pageSize,
    })
    .then((res) => ({
      records: res.records.map((row) => ({
        userId: row.id,
        userName: row.name,
        userCode: row.code,
        orgName: row.orgName,
      })),
      total: res.total,
      page: res.page,
      pageSize: res.pageSize,
    }))
}

// 创建/编辑规则的后端响应是完整的规则详情（含 hitCount 等字段），这里同样转换成
// UserRoleRuleSaveResult 的 matchedUserCount 命名，只取组件实际需要的字段
interface UserRoleRuleDetailRaw {
  id: number
  name: string
  hitCount: number
}

// 新增规则：保存成功后后端立即执行一次，响应带本次命中数量
export function createUserRoleRule(
  roleId: number,
  name: string,
  remark: string,
  conditions: UserRoleConditions,
): Promise<UserRoleRuleSaveResult> {
  return request
    .post<UserRoleRuleDetailRaw, UserRoleRuleDetailRaw>('/user-role-rules', { roleId, name, remark, ...conditions })
    .then((res) => ({ id: res.id, name: res.name, matchedUserCount: res.hitCount }))
}

// 编辑规则（规则归属角色不可改，请求体不含 roleId）：保存成功后同样立即重新执行一次
export function updateUserRoleRule(
  ruleId: number,
  name: string,
  remark: string,
  conditions: UserRoleConditions,
): Promise<UserRoleRuleSaveResult> {
  return request
    .put<UserRoleRuleDetailRaw, UserRoleRuleDetailRaw>(`/user-role-rules/${ruleId}`, { name, remark, ...conditions })
    .then((res) => ({ id: res.id, name: res.name, matchedUserCount: res.hitCount }))
}

// 删除规则：级联收回该规则已产生的角色关联
export function deleteUserRoleRule(ruleId: number): Promise<void> {
  return request.delete(`/user-role-rules/${ruleId}`)
}
