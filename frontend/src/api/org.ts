import request from './request'
import type { OrgFormRequest, OrgRow, OrgTreeNode, PageResult } from '@/types/org'
import type { WriteOperationResult } from '@/types/approval'

// 组织管理接口封装，组件/store 不直接调用 axios。

// 获取完整的组织树（已按 showOrder 降序、已排除逻辑删除的节点）
export function getOrgTree(): Promise<OrgTreeNode[]> {
  return request.get('/orgs/tree')
}

// 获取某个组织节点的直接子节点分页列表；parentId 不传时后端按 0（顶级）处理，
// page/pageSize 不传时后端分别默认 1、10
export function getOrgChildren(
  parentId?: number,
  page?: number,
  pageSize?: number,
): Promise<PageResult<OrgRow>> {
  return request.get('/orgs/children', { params: { parentId, page, pageSize } })
}

// 获取某个组织节点的直属子组织列表（不分页），专供左侧组织树懒加载展开使用；
// parentId 不传时后端按 0（顶级）处理。与 getOrgChildren（分页，供右侧表格用）、
// getOrgTree（全量嵌套树，供弹窗上级组织选择器用）都不同，互不影响。
export function getOrgTreeChildren(parentId?: number): Promise<OrgTreeNode[]> {
  return request.get('/orgs/tree/children', { params: parentId !== undefined ? { parentId } : undefined })
}

// 获取单个组织详情，用于编辑表单回填
export function getOrgById(id: number): Promise<OrgRow> {
  return request.get(`/orgs/${id}`)
}

// 新增组织；组织的审批开关（bizType=ORG）开启时，响应 approvalEnabled=true、
// approvalRequest 非空、data 为空，不创建真实组织记录，需等待审批通过后才生效
// （add-master-data-approval-workflow change design.md Decision 9）；开关关闭时行为
// 与本 change 之前一致，approvalEnabled=false、data 为创建后的组织数据
export function createOrg(data: OrgFormRequest): Promise<WriteOperationResult<OrgRow>> {
  return request.post('/orgs', data)
}

// 编辑组织，响应结构同上
export function updateOrg(id: number, data: OrgFormRequest): Promise<WriteOperationResult<OrgRow>> {
  return request.put(`/orgs/${id}`, data)
}

// 启用组织，响应结构同上
export function enableOrg(id: number): Promise<WriteOperationResult<OrgRow>> {
  return request.put(`/orgs/${id}/enable`)
}

// 停用组织，响应结构同上
export function disableOrg(id: number): Promise<WriteOperationResult<OrgRow>> {
  return request.put(`/orgs/${id}/disable`)
}

// 删除组织（逻辑删除，若存在未删除的子组织，审批通过时后端会拒绝并返回错误信息），响应结构同上
export function deleteOrg(id: number): Promise<WriteOperationResult<OrgRow>> {
  return request.delete(`/orgs/${id}`)
}
