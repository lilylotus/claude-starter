import request from './request'
import type { PageResult, PermissionFormRequest, PermissionOption, PermissionRow } from '@/types/permission'

// 权限管理接口封装，组件/store 不直接调用 axios。

// 分页查询权限点，不支持筛选参数；page/pageSize 不传时后端分别默认 1、10
export function getPermissionPage(page?: number, pageSize?: number): Promise<PageResult<PermissionRow>> {
  return request.get('/permissions', { params: { page, pageSize } })
}

// 获取不分页的权限点选项（未删除且启用），供角色表单的权限点勾选控件按需加载全量可选项；
// 仅在打开角色新增/编辑弹窗时调用，不在页面进入/翻页时预先请求
export function getPermissionOptions(): Promise<PermissionOption[]> {
  return request.get('/permissions/options')
}

// 获取权限点详情，用于编辑表单回填与只读详情弹窗
export function getPermissionById(id: number): Promise<PermissionRow> {
  return request.get(`/permissions/${id}`)
}

// 新增权限点
export function createPermission(data: PermissionFormRequest): Promise<PermissionRow> {
  return request.post('/permissions', data)
}

// 编辑权限点
export function updatePermission(id: number, data: PermissionFormRequest): Promise<PermissionRow> {
  return request.put(`/permissions/${id}`, data)
}

// 启用权限点
export function enablePermission(id: number): Promise<void> {
  return request.put(`/permissions/${id}/enable`)
}

// 停用权限点
export function disablePermission(id: number): Promise<void> {
  return request.put(`/permissions/${id}/disable`)
}

// 删除权限点（逻辑删除）
export function deletePermission(id: number): Promise<void> {
  return request.delete(`/permissions/${id}`)
}
