import request from './request'
import type {
  AppAccessEffectiveItemVO,
  ManualOverrideQueryParams,
  ManualOverrideUpsertRequest,
  ManualOverrideVO,
  PageResult,
  PolicyFormRequest,
  PolicyQueryParams,
  PolicyVO,
} from '@/types/appAccess'

// 应用访问授权接口封装，组件不直接调用 axios。

// ---- 策略规则 ----

// 分页查询策略规则，支持按名称模糊搜索、按状态过滤，均可选
export function getPolicyPage(params: PolicyQueryParams): Promise<PageResult<PolicyVO>> {
  return request.get('/app-access/policies', { params })
}

// 查询策略规则详情，含组织范围/用户属性条件/目标应用完整回显
export function getPolicyById(id: number): Promise<PolicyVO> {
  return request.get(`/app-access/policies/${id}`)
}

// 新增策略规则
export function createPolicy(data: PolicyFormRequest): Promise<PolicyVO> {
  return request.post('/app-access/policies', data)
}

// 编辑策略规则，组织范围/用户属性条件/目标应用均为整体替换语义
export function updatePolicy(id: number, data: PolicyFormRequest): Promise<PolicyVO> {
  return request.put(`/app-access/policies/${id}`, data)
}

// 启用策略规则
export function enablePolicy(id: number): Promise<PolicyVO> {
  return request.put(`/app-access/policies/${id}/enable`)
}

// 停用策略规则
export function disablePolicy(id: number): Promise<PolicyVO> {
  return request.put(`/app-access/policies/${id}/disable`)
}

// 删除策略规则，级联删除该策略已产生的全部策略授权记录
export function deletePolicy(id: number): Promise<void> {
  return request.delete(`/app-access/policies/${id}`)
}

// 手动执行策略规则：重新计算命中用户并整体重建该策略产生的策略授权记录
export function executePolicy(id: number): Promise<PolicyVO> {
  return request.post(`/app-access/policies/${id}/execute`)
}

// ---- 人工例外 ----

// 分页查询人工例外，支持按用户/应用/例外类型过滤，均可选
export function getManualOverridePage(params: ManualOverrideQueryParams): Promise<PageResult<ManualOverrideVO>> {
  return request.get('/app-access/manual-overrides', { params })
}

// 新增或更新人工例外：userId+appId 组合已存在记录时更新，不新增一行
export function upsertManualOverride(data: ManualOverrideUpsertRequest): Promise<ManualOverrideVO> {
  return request.post('/app-access/manual-overrides', data)
}

// 删除人工例外，删除后该用户+应用组合的最终生效权限退回只看策略授权
export function deleteManualOverride(id: number): Promise<void> {
  return request.delete(`/app-access/manual-overrides/${id}`)
}

// ---- 最终生效权限查询（只读） ----

// 按用户查询其当前最终生效可访问的全部应用；includeRevoked=true 时额外返回被人工收回、
// 但本应命中策略/人工授权的应用，标记为 REVOKED，供审计查看
export function listEffectiveByUser(
  userId: number,
  params: { includeRevoked?: boolean; page?: number; pageSize?: number },
): Promise<PageResult<AppAccessEffectiveItemVO>> {
  return request.get(`/app-access/effective/by-user/${userId}`, { params })
}

// 按应用查询其当前最终生效可访问的全部用户
export function listEffectiveByApp(
  appId: number,
  params: { page?: number; pageSize?: number },
): Promise<PageResult<AppAccessEffectiveItemVO>> {
  return request.get(`/app-access/effective/by-app/${appId}`, { params })
}
