// SSO 协议调用日志相关类型定义，字段命名和后端 SsoProtocolLogVO / SsoProtocolLogQueryRequest DTO 对齐。

import type { PageResult } from './operationLog'

export type { PageResult }

// 协议类型：CAS / OAUTH2 / NONE，与后端 cn.nihility.rbac.app.authconfig.constant.AuthProtocol 一一对应
export const PROTOCOL_LABELS: Record<string, string> = {
  CAS: 'CAS',
  OAUTH2: 'OAuth2',
  NONE: '-',
}

// 事件类型下拉/展示标签，与后端 cn.nihility.rbac.ssoprotocollog 事件类型常量一一对应
export const EVENT_TYPE_LABELS: Record<string, string> = {
  LOGIN: '登录票据签发',
  SERVICE_VALIDATE: '票据验证',
  LOGOUT: '登出',
  AUTHORIZE: '授权码签发',
  TOKEN: '令牌签发/刷新',
  USERINFO: '用户信息查询',
}

export const EVENT_TYPE_OPTIONS: { value: string; label: string }[] = Object.entries(EVENT_TYPE_LABELS).map(
  ([value, label]) => ({ value, label }),
)

// 结果：1=成功，2=失败，复用 login-log-management 的数值约定
export const SSO_PROTOCOL_RESULT_SUCCESS = 1
export const SSO_PROTOCOL_RESULT_FAILED = 2

// 分页查询筛选参数，字段与后端 GET /api/sso-protocol-logs 的 query 参数一致，全部可选。
// 登录日志页面的关联查看入口只使用 sessionId 精确查询，其余筛选项为查询接口的通用能力，一并声明以便复用
export interface SsoProtocolLogQueryParams {
  sessionId?: string
  appRefId?: number
  protocol?: string
  eventType?: string
  result?: number
  startTime?: string
  endTime?: string
  page?: number
  pageSize?: number
}

// SSO 协议调用日志列表行，来自 GET /api/sso-protocol-logs 分页接口，不拆分详情接口
export interface SsoProtocolLogRow {
  id: number
  appRefId: number | null
  appId: string | null
  protocol: string
  eventType: string
  userId: number | null
  result: number
  resultLabel: string
  failReason: string | null
  clientIp: string | null
  userAgent: string | null
  createTime: string
  // 本次调用所属 SSO 会话标识（会话令牌哈希摘要），处理链路尚未解析出会话时为空
  sessionId: string | null
}
