import request from './request'
import type { LoginLogQueryParams, LoginLogRow, PageResult } from '@/types/loginLog'

// 登录日志接口封装，组件不直接调用 axios。只读，没有新增/编辑/删除/详情接口。

// 分页查询登录日志，支持按登录账号、登录结果、登录时间范围筛选，
// 全部筛选参数可选；page/pageSize 不传时后端分别默认 1、10
export function getLoginLogPage(params: LoginLogQueryParams): Promise<PageResult<LoginLogRow>> {
  return request.get('/login-logs', { params })
}
