import request from './request'
import type { DashboardStats } from '@/types/dashboard'
import type { OperationLogRow } from '@/types/operationLog'

// 首页概览页接口封装，组件不直接调用 axios。

// 获取首页统计卡片数据（身份总数/接入应用/角色数量/权限点）。该接口对所有已登录用户
// 开放，不做角色权限点校验，仅要求 menu 头格式合法，因此固定显式传 Dashboard:stats:view，
// 不依赖 /dashboard 路由（本身也没有 permissionKey）的自动回退
export function getDashboardStats(): Promise<DashboardStats> {
  return request.get('/dashboard/stats', { headers: { menu: 'Dashboard:stats:view' } })
}

// 获取当前登录账号自己最近的操作记录（最多 4 条，按操作发起时间降序）。该接口对所有
// 已登录用户开放，不要求"操作日志管理"查看权限，仅要求 menu 头格式合法，因此固定显式
// 传 Dashboard:recentOperations:view；返回结构与 OperationLogManagement 分页接口单条
// 记录一致，直接复用 OperationLogRow 类型
export function getRecentOperations(): Promise<OperationLogRow[]> {
  return request.get('/dashboard/recent-operations', { headers: { menu: 'Dashboard:recentOperations:view' } })
}
