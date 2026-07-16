import request from './request'
import type { PageResult, RoleFormRequest, RoleRow } from '@/types/role'

// 角色管理接口封装，组件/store 不直接调用 axios。

// 分页查询角色，不支持筛选参数；page/pageSize 不传时后端分别默认 1、10
export function getRolePage(page?: number, pageSize?: number): Promise<PageResult<RoleRow>> {
  return request.get('/roles', { params: { page, pageSize } })
}

// 获取角色详情，用于编辑表单回填与只读详情弹窗
export function getRoleById(id: number): Promise<RoleRow> {
  return request.get(`/roles/${id}`)
}

// 新增角色
export function createRole(data: RoleFormRequest): Promise<RoleRow> {
  return request.post('/roles', data)
}

// 编辑角色
export function updateRole(id: number, data: RoleFormRequest): Promise<RoleRow> {
  return request.put(`/roles/${id}`, data)
}

// 启用角色
export function enableRole(id: number): Promise<void> {
  return request.put(`/roles/${id}/enable`)
}

// 停用角色
export function disableRole(id: number): Promise<void> {
  return request.put(`/roles/${id}/disable`)
}

// 删除角色（逻辑删除）
export function deleteRole(id: number): Promise<void> {
  return request.delete(`/roles/${id}`)
}
