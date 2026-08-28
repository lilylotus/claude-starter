// 审批申请相关类型定义，字段命名和后端 ApprovalRequestVO / WriteOperationResultVO 对齐
// （add-master-data-approval-workflow change design.md Decision 1/9）。

// 业务对象类型：组织/用户/任职/应用，与 form-field-definition-management 能力共用的
// FormFieldBizType 同一套取值，故直接复用该类型定义，不重复声明一份等价的字符串联合类型。
import type { FormFieldBizType } from './formField'

export type ApprovalBizType = FormFieldBizType

export const APPROVAL_BIZ_TYPE_OPTIONS: Array<{ value: ApprovalBizType; label: string }> = [
  { value: 'ORG', label: '组织' },
  { value: 'USER', label: '用户' },
  { value: 'POSITION', label: '任职' },
  { value: 'APP', label: '应用' },
]

// 操作类型：新增/更新/启用/停用/删除，和后端 ApprovalOperationType 常量对齐
export type ApprovalOperationType = 'CREATE' | 'UPDATE' | 'ENABLE' | 'DISABLE' | 'DELETE'

export const APPROVAL_OPERATION_TYPE_OPTIONS: Array<{ value: ApprovalOperationType; label: string }> = [
  { value: 'CREATE', label: '新增' },
  { value: 'UPDATE', label: '编辑' },
  { value: 'ENABLE', label: '启用' },
  { value: 'DISABLE', label: '停用' },
  { value: 'DELETE', label: '删除' },
]

// 申请状态常量：1000=待审批，2000=已通过，3000=已拒绝，4000=已撤回，
// 和后端 ApprovalRequestStatus 常量对齐（独立于四个业务模块自身的 2000/3000/-1000 状态语义）
export const APPROVAL_STATUS_PENDING = 1000
export const APPROVAL_STATUS_APPROVED = 2000
export const APPROVAL_STATUS_REJECTED = 3000
export const APPROVAL_STATUS_CANCELLED = 4000

export const APPROVAL_STATUS_OPTIONS: Array<{ value: number; label: string }> = [
  { value: APPROVAL_STATUS_PENDING, label: '待审批' },
  { value: APPROVAL_STATUS_APPROVED, label: '已通过' },
  { value: APPROVAL_STATUS_REJECTED, label: '已拒绝' },
  { value: APPROVAL_STATUS_CANCELLED, label: '已撤回' },
]

// 审批申请行数据，来自 GET /api/approval-requests/mine、GET /api/approval-requests/pending。
// requestPayload/targetSnapshot 由查询接口直接内嵌返回（design.md Decision 8："查询结果
// SHALL 携带足够信息供前端渲染申请详情"），不需要额外的详情接口：
// - CREATE：只有 requestPayload（新值），targetSnapshot 为空（无旧值可对比）
// - UPDATE：requestPayload（新值）与 targetSnapshot（目标记录当前值）同时非空，供新旧对照
// - ENABLE/DISABLE/DELETE：两者均为空，提交时不携带请求体，详情只需展示 targetId 等基础信息
export interface ApprovalRequestRow {
  id: number
  bizType: ApprovalBizType
  operationType: ApprovalOperationType
  targetId: number | null
  resultTargetId: number | null
  status: number
  createBy: string
  createByName?: string
  createTime: string
  approverId: number | null
  approverName?: string
  approveTime: string | null
  opinion: string | null
  requestPayload?: Record<string, unknown> | null
  targetSnapshot?: Record<string, unknown> | null
}

// 四个模块新增/编辑/启用/停用/删除接口统一返回的"写操作结果"包装对象：
// approvalEnabled=true 时 approvalRequest 非空、data 为空；
// approvalEnabled=false 时 data 非空（创建/更新/状态切换后的业务数据）、approvalRequest 为空
export interface WriteOperationResult<T = unknown> {
  approvalEnabled: boolean
  approvalRequest: ApprovalRequestRow | null
  data: T | null
}

// 提交审批申请的请求体（通用入口，见 api/approval.ts submitApprovalRequest 的说明）
export interface ApprovalSubmitRequest {
  bizType: ApprovalBizType
  operationType: ApprovalOperationType
  targetId?: number
  requestPayload?: Record<string, unknown>
}

// 审批拒绝请求体：意见必填
export interface ApprovalRejectRequest {
  opinion: string
}

// "我的申请"/"待我审批"分页查询参数，均可选
export interface ApprovalPageQuery {
  bizType?: ApprovalBizType
  operationType?: ApprovalOperationType
  status?: number
  page?: number
  pageSize?: number
}

// 通用分页响应结构，字段命名和后端 cn.nihility.rbac.common.PageResult 对齐
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}
