import type { AxiosRequestConfig } from 'axios'
import request from './request'
import type { OperationLogDetail, OperationLogQueryParams, OperationLogRow, PageResult } from '@/types/operationLog'

// 操作日志管理接口封装，组件不直接调用 axios。只读，没有新增/编辑/删除接口。

// 分页查询操作日志，支持按模块、资源类型、操作类型、操作人、操作时间范围筛选，
// 全部筛选参数可选；page/pageSize 不传时后端分别默认 1、10。可选的 config 参数用于
// 透传自定义 axios 配置（如首页概览页调用时没有对应路由 permissionKey，需要显式传
// headers.menu），不传时行为与原来完全一致
export function getOperationLogPage(
  params: OperationLogQueryParams,
  config?: AxiosRequestConfig,
): Promise<PageResult<OperationLogRow>> {
  return request.get('/operation-logs', { ...config, params })
}

// 获取操作日志详情，用于只读详情弹窗
export function getOperationLogById(id: number): Promise<OperationLogDetail> {
  return request.get(`/operation-logs/${id}`)
}
