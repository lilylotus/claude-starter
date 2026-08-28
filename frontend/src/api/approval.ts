import request from './request'
import type {
  ApprovalPageQuery,
  ApprovalRejectRequest,
  ApprovalRequestRow,
  ApprovalSubmitRequest,
  PageResult,
  WriteOperationResult,
} from '@/types/approval'

// 审批申请接口封装，组件/store 不直接调用 axios。

// 提交审批申请：通用入口。组织/用户/任职/应用四个模块现有的新增/编辑/启用/停用/删除接口
// （POST /api/orgs 等）内部已经按各自 bizType 的审批开关状态自动分流为"提交审批"或"直接
// 生效"，四个管理页面不需要另外调用本接口——直接调用 orgApi.createOrg() 等既有函数即可，
// 拿到的响应就是 WriteOperationResult（见 types/approval.ts）。这里仍然封装出来，供将来
// 独立于四个既有模块之外的场景需要直接提交审批申请时复用。
export function submitApprovalRequest(data: ApprovalSubmitRequest): Promise<WriteOperationResult> {
  return request.post('/approval-requests', data)
}

// 审批通过；opinion 可选
export function approveApprovalRequest(id: number, opinion?: string): Promise<void> {
  return request.post(`/approval-requests/${id}/approve`, { opinion })
}

// 审批拒绝，意见必填
export function rejectApprovalRequest(id: number, data: ApprovalRejectRequest): Promise<void> {
  return request.post(`/approval-requests/${id}/reject`, data)
}

// 撤回：仅提交人本人可撤回状态仍为"待审批"的申请
export function cancelApprovalRequest(id: number): Promise<void> {
  return request.post(`/approval-requests/${id}/cancel`)
}

// 我的申请：当前登录用户作为提交人提交的全部申请，支持按 bizType/operationType/status
// 过滤，分页，按提交时间降序；page/pageSize 不传时后端分别默认 1、10
export function getMyApprovalRequests(params: ApprovalPageQuery): Promise<PageResult<ApprovalRequestRow>> {
  return request.get('/approval-requests/mine', { params })
}

// 待我审批：全部"待审批"状态的申请，不区分提交人，需要 ApprovalManagement:request:approve
// 权限点；page/pageSize 不传时后端分别默认 1、10
export function getPendingApprovalRequests(params: ApprovalPageQuery): Promise<PageResult<ApprovalRequestRow>> {
  return request.get('/approval-requests/pending', { params })
}
