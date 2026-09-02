// 登录日志相关类型定义，字段命名和后端 LoginLogVO / LoginLogQueryRequest DTO 对齐。

import type { PageResult } from './operationLog'

export type { PageResult }

// 登录结果：1=成功，2=失败
export const LOGIN_RESULT_SUCCESS = 1
export const LOGIN_RESULT_FAILED = 2

// 登录结果下拉选项，前端硬编码，与后端 cn.nihility.rbac.loginlog.constant.LoginResult 一一对应
export const LOGIN_RESULT_OPTIONS: { value: number; label: string }[] = [
  { value: LOGIN_RESULT_SUCCESS, label: '成功' },
  { value: LOGIN_RESULT_FAILED, label: '失败' },
]

// 登录方式：口令/短信/扫码，与后端新增列 tab_login_log.login_method 取值对齐
// （见 openspec/changes/add-sso-login-methods）；管理端直接登录固定为 PASSWORD，
// SSO 登录页按用户实际选用的方式记录
export type LoginMethod = 'PASSWORD' | 'SMS' | 'QRCODE'

// 登录方式下拉选项，前端硬编码
export const LOGIN_METHOD_OPTIONS: { value: LoginMethod; label: string }[] = [
  { value: 'PASSWORD', label: '口令' },
  { value: 'SMS', label: '短信' },
  { value: 'QRCODE', label: '扫码' },
]

// 登录方式取值到中文文案的映射，供表格列展示使用
export const LOGIN_METHOD_LABELS: Record<LoginMethod, string> = {
  PASSWORD: '口令',
  SMS: '短信',
  QRCODE: '扫码',
}

// 分页查询筛选参数，字段与后端 GET /api/login-logs 的 query 参数一致，全部可选
export interface LoginLogQueryParams {
  loginAccount?: string
  loginResult?: number
  loginMethod?: LoginMethod
  startTime?: string
  endTime?: string
  page?: number
  pageSize?: number
}

// 登录日志列表行，来自 GET /api/login-logs 分页接口，不拆分详情接口
export interface LoginLogRow {
  id: number
  loginAccount: string | null
  userId: number | null
  userName: string | null
  loginResult: number
  loginResultLabel: string
  loginMethod: LoginMethod
  failReason: string | null
  loginIp: string | null
  loginTerminal: string | null
  loginOs: string | null
  loginBrowser: string | null
  loginUserAgent: string | null
  createTime: string
  // 本次登录建立的 SSO 会话标识（会话令牌的哈希摘要，不是原始令牌），仅通过 SSO 登录页
  // 登录成功时非空；管理端口令登录、登录失败均为空。前端只用它作为查询关联的 SSO 协议
  // 调用记录的参数，不在表格列里展示这个哈希值本身
  sessionId: string | null
}
