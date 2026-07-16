import request from './request'
import type {
  MenuResourceFormRequest,
  MenuResourceRow,
  MenuResourceTreeNode,
  PageResult,
} from '@/types/menuResource'

// 菜单管理（资源）接口封装，组件/store 不直接调用 axios。

// 获取完整的资源树（已按 showOrder 降序、已排除逻辑删除的节点）
export function getMenuTree(): Promise<MenuResourceTreeNode[]> {
  return request.get('/menus/tree')
}

// 获取某个资源节点的直接子节点分页列表；parentId 不传时后端按 0（顶级）处理，
// page/pageSize 不传时后端分别默认 1、10
export function getMenuChildren(
  parentId?: number,
  page?: number,
  pageSize?: number,
): Promise<PageResult<MenuResourceRow>> {
  return request.get('/menus/children', { params: { parentId, page, pageSize } })
}

// 获取某个资源节点的直属子资源列表（不分页），专供左侧资源树懒加载展开使用；
// parentId 不传时后端按 0（顶级）处理。与 getMenuChildren（分页，供右侧表格用）、
// getMenuTree（全量嵌套树，供弹窗上级资源选择器用）都不同，互不影响。
export function getMenuTreeChildren(parentId?: number): Promise<MenuResourceTreeNode[]> {
  return request.get('/menus/tree/children', { params: parentId !== undefined ? { parentId } : undefined })
}

// 获取单个资源详情，用于编辑表单回填
export function getMenuById(id: number): Promise<MenuResourceRow> {
  return request.get(`/menus/${id}`)
}

// 新增资源
export function createMenu(data: MenuResourceFormRequest): Promise<MenuResourceRow> {
  return request.post('/menus', data)
}

// 编辑资源
export function updateMenu(id: number, data: MenuResourceFormRequest): Promise<MenuResourceRow> {
  return request.put(`/menus/${id}`, data)
}

// 启用资源
export function enableMenu(id: number): Promise<void> {
  return request.put(`/menus/${id}/enable`)
}

// 停用资源
export function disableMenu(id: number): Promise<void> {
  return request.put(`/menus/${id}/disable`)
}

// 删除资源（逻辑删除，若存在未删除的子资源后端会拒绝并返回错误信息）
export function deleteMenu(id: number): Promise<void> {
  return request.delete(`/menus/${id}`)
}
