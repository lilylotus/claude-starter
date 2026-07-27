import request from './request'
import type { PageResult, UserDetail, UserFormRequest, UserRow } from '@/types/user'

// 用户管理接口封装，组件/store 不直接调用 axios。

// 分页查询用户，支持按姓名/手机号/身份证号模糊搜索，均可选，组合时为“与”关系；
// page/pageSize 不传时后端分别默认 1、10
export function getUserPage(params: {
  name?: string
  mobile?: string
  idCard?: string
  page?: number
  pageSize?: number
}): Promise<PageResult<UserRow>> {
  return request.get('/users', { params })
}

// 获取用户详情（含其名下全部任职记录及回填的 orgName），用于编辑表单回填与只读详情弹窗
export function getUserById(id: number): Promise<UserDetail> {
  return request.get(`/users/${id}`)
}

// 新增用户，可同时提交 0 到多条任职记录一并创建
export function createUser(data: UserFormRequest): Promise<UserDetail> {
  return request.post('/users', data)
}

// 编辑用户，positions 需回传该用户任职记录的完整列表，后端按 id 是否存在做增量 diff
export function updateUser(id: number, data: UserFormRequest): Promise<UserDetail> {
  return request.put(`/users/${id}`, data)
}

// 启用用户
export function enableUser(id: number): Promise<void> {
  return request.put(`/users/${id}/enable`)
}

// 停用用户
export function disableUser(id: number): Promise<void> {
  return request.put(`/users/${id}/disable`)
}

// 删除用户（逻辑删除，不级联处理其名下的任职记录）
export function deleteUser(id: number): Promise<void> {
  return request.delete(`/users/${id}`)
}

// 重置密码：将该用户密码重置为默认密码，并将其标记为待首次登录强制改密状态；
// 不影响用户的启停用状态（status），调用方无需刷新列表数据
export function resetPassword(id: number): Promise<void> {
  return request.put(`/users/${id}/reset-password`)
}
