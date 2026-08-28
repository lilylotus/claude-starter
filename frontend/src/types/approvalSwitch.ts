// 审批开关相关类型定义，字段命名和后端 ApprovalSwitchVO 对齐（design.md Decision 9）。

import type { ApprovalBizType } from './approval'

// 单个业务对象类型的审批开关状态，来自 GET /api/approval-switches、
// PUT /api/approval-switches/{bizType}
export interface ApprovalSwitchRow {
  bizType: ApprovalBizType
  enabled: boolean
  updateBy?: string
  updateTime?: string
}

// 修改审批开关的请求体
export interface ApprovalSwitchUpdateRequest {
  enabled: boolean
}
