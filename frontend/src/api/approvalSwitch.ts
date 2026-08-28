import request from './request'
import type { ApprovalSwitchRow, ApprovalSwitchUpdateRequest } from '@/types/approvalSwitch'
import type { ApprovalBizType } from '@/types/approval'

// 审批开关接口封装，组件/store 不直接调用 axios。

// 查询组织/用户/任职/应用四个 bizType 当前的审批开关状态
export function getApprovalSwitches(): Promise<ApprovalSwitchRow[]> {
  return request.get('/approval-switches')
}

// 修改指定 bizType 的审批开关状态
export function updateApprovalSwitch(
  bizType: ApprovalBizType,
  data: ApprovalSwitchUpdateRequest,
): Promise<ApprovalSwitchRow> {
  return request.put(`/approval-switches/${bizType}`, data)
}
