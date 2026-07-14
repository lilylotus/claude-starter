import request from './request'
import type { PageResult, PositionCreateRequest, PositionFormRequest, PositionRow } from '@/types/position'

// 任职管理接口封装，组件/store 不直接调用 axios。

// 按所属组织 id 分页查询任职记录；orgId 必填——任职记录与组织之间没有父子递归关系，
// 不存在“顶级组织聚合查询”的语义，未选中组织时前端不应调用本接口。
// page/pageSize 不传时后端分别默认 1、10
export function getPositionPage(orgId: number, page?: number, pageSize?: number): Promise<PageResult<PositionRow>> {
  return request.get('/positions', { params: { orgId, page, pageSize } })
}

// 获取任职记录详情，用于编辑表单回填与只读详情弹窗
export function getPositionById(id: number): Promise<PositionRow> {
  return request.get(`/positions/${id}`)
}

// 新增任职记录，须指定一个已存在的用户
export function createPosition(data: PositionCreateRequest): Promise<PositionRow> {
  return request.post('/positions', data)
}

// 编辑任职记录，不支持修改所属用户（请求体不含 userId）
export function updatePosition(id: number, data: PositionFormRequest): Promise<PositionRow> {
  return request.put(`/positions/${id}`, data)
}

// 启用任职记录
export function enablePosition(id: number): Promise<void> {
  return request.put(`/positions/${id}/enable`)
}

// 停用任职记录
export function disablePosition(id: number): Promise<void> {
  return request.put(`/positions/${id}/disable`)
}

// 删除任职记录（逻辑删除，不做物理删除）
export function deletePosition(id: number): Promise<void> {
  return request.delete(`/positions/${id}`)
}
