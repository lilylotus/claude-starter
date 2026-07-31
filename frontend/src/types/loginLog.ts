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

// 分页查询筛选参数，字段与后端 GET /api/login-logs 的 query 参数一致，全部可选
export interface LoginLogQueryParams {
  loginAccount?: string
  loginResult?: number
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
  failReason: string | null
  loginIp: string | null
  loginTerminal: string | null
  loginOs: string | null
  loginBrowser: string | null
  loginUserAgent: string | null
  createTime: string
}
