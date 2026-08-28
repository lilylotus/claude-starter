import request from './request'
import type { PageResult, PositionCreateRequest, PositionFormRequest, PositionRow } from '@/types/position'
import type { WriteOperationResult } from '@/types/approval'

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

// 新增任职记录，须指定一个已存在的用户；任职的审批开关（bizType=POSITION）开启时，
// 响应 approvalEnabled=true、approvalRequest 非空、data 为空，不创建真实任职记录，需等待
// 审批通过后才生效（add-master-data-approval-workflow change design.md Decision 9）；
// 开关关闭时行为与本 change 之前一致，approvalEnabled=false、data 为创建后的任职记录
export function createPosition(data: PositionCreateRequest): Promise<WriteOperationResult<PositionRow>> {
  return request.post('/positions', data)
}

// 编辑任职记录，不支持修改所属用户（请求体不含 userId），响应结构同上
export function updatePosition(id: number, data: PositionFormRequest): Promise<WriteOperationResult<PositionRow>> {
  return request.put(`/positions/${id}`, data)
}

// 启用任职记录，响应结构同上
export function enablePosition(id: number): Promise<WriteOperationResult<PositionRow>> {
  return request.put(`/positions/${id}/enable`)
}

// 停用任职记录，响应结构同上
export function disablePosition(id: number): Promise<WriteOperationResult<PositionRow>> {
  return request.put(`/positions/${id}/disable`)
}

// 删除任职记录（逻辑删除，不做物理删除），响应结构同上
export function deletePosition(id: number): Promise<WriteOperationResult<PositionRow>> {
  return request.delete(`/positions/${id}`)
}
