import request from './request'
import type { AppFormRequest, AppRow, PageResult } from '@/types/app'

// 应用管理接口封装，组件/store 不直接调用 axios。

// 分页查询应用，不支持筛选参数；page/pageSize 不传时后端分别默认 1、10
export function getAppPage(page?: number, pageSize?: number): Promise<PageResult<AppRow>> {
  return request.get('/apps', { params: { page, pageSize } })
}

// 获取应用详情，用于编辑表单回填与只读详情弹窗
export function getAppById(id: number): Promise<AppRow> {
  return request.get(`/apps/${id}`)
}

// 新增应用
export function createApp(data: AppFormRequest): Promise<AppRow> {
  return request.post('/apps', data)
}

// 编辑应用
export function updateApp(id: number, data: AppFormRequest): Promise<AppRow> {
  return request.put(`/apps/${id}`, data)
}

// 启用应用
export function enableApp(id: number): Promise<void> {
  return request.put(`/apps/${id}/enable`)
}

// 停用应用
export function disableApp(id: number): Promise<void> {
  return request.put(`/apps/${id}/disable`)
}

// 删除应用（逻辑删除）
export function deleteApp(id: number): Promise<void> {
  return request.delete(`/apps/${id}`)
}
