import request from './request'
import type {
  AbacPolicyFormRequest,
  AbacPolicyQueryParams,
  AbacPolicyVO,
  PageResult,
} from '@/types/abacPolicy'

// ABAC 策略管理接口封装，组件不直接调用 axios。

// 分页查询策略规则，支持按名称模糊搜索、按状态过滤，均可选
export function getAbacPolicyPage(params: AbacPolicyQueryParams): Promise<PageResult<AbacPolicyVO>> {
  return request.get('/abac/policies', { params })
}

// 查询策略规则详情，含目标资源编码/用户属性条件/时间窗口条件完整回显
export function getAbacPolicyById(id: number): Promise<AbacPolicyVO> {
  return request.get(`/abac/policies/${id}`)
}

// 新增策略规则
export function createAbacPolicy(data: AbacPolicyFormRequest): Promise<AbacPolicyVO> {
  return request.post('/abac/policies', data)
}

// 编辑策略规则，目标资源编码/用户属性条件/时间窗口条件均为整体替换语义
export function updateAbacPolicy(id: number, data: AbacPolicyFormRequest): Promise<AbacPolicyVO> {
  return request.put(`/abac/policies/${id}`, data)
}

// 启用策略规则
export function enableAbacPolicy(id: number): Promise<AbacPolicyVO> {
  return request.put(`/abac/policies/${id}/enable`)
}

// 停用策略规则
export function disableAbacPolicy(id: number): Promise<AbacPolicyVO> {
  return request.put(`/abac/policies/${id}/disable`)
}

// 删除策略规则，物理删除并级联清理目标资源编码/用户属性条件/时间窗口条件
export function deleteAbacPolicy(id: number): Promise<void> {
  return request.delete(`/abac/policies/${id}`)
}
