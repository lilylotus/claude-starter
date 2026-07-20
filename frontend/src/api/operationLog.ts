import request from './request'
import type { OperationLogDetail, OperationLogQueryParams, OperationLogRow, PageResult } from '@/types/operationLog'

// 操作日志管理接口封装，组件不直接调用 axios。只读，没有新增/编辑/删除接口。

// 分页查询操作日志，支持按模块、资源类型、操作类型、操作人、操作时间范围筛选，
// 全部筛选参数可选；page/pageSize 不传时后端分别默认 1、10
export function getOperationLogPage(params: OperationLogQueryParams): Promise<PageResult<OperationLogRow>> {
  return request.get('/operation-logs', { params })
}

// 获取操作日志详情，用于只读详情弹窗
export function getOperationLogById(id: number): Promise<OperationLogDetail> {
  return request.get(`/operation-logs/${id}`)
}
