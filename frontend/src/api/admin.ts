import request from './request'
import type { AdminFormRequest, AdminRow, PageResult } from '@/types/admin'

// 管理员管理接口封装，组件/store 不直接调用 axios。

// 分页查询管理员，不支持筛选参数；page/pageSize 不传时后端分别默认 1、10
export function getAdminPage(page?: number, pageSize?: number): Promise<PageResult<AdminRow>> {
  return request.get('/admins', { params: { page, pageSize } })
}

// 获取管理员详情（含关联角色列表、管辖组织范围列表），用于编辑表单回填与只读详情弹窗
export function getAdminById(id: number): Promise<AdminRow> {
  return request.get(`/admins/${id}`)
}

// 新增管理员，roleIds/orgScopes 可一并整体提交创建
export function createAdmin(data: AdminFormRequest): Promise<AdminRow> {
  return request.post('/admins', data)
}

// 编辑管理员，roleIds/orgScopes 需回传完整列表，后端整体同步覆盖既有关联行
export function updateAdmin(id: number, data: AdminFormRequest): Promise<AdminRow> {
  return request.put(`/admins/${id}`, data)
}

// 启用管理员
export function enableAdmin(id: number): Promise<void> {
  return request.put(`/admins/${id}/enable`)
}

// 停用管理员
export function disableAdmin(id: number): Promise<void> {
  return request.put(`/admins/${id}/disable`)
}

// 删除管理员（逻辑删除，不级联清理其名下的角色/组织管辖范围关联行）
export function deleteAdmin(id: number): Promise<void> {
  return request.delete(`/admins/${id}`)
}
