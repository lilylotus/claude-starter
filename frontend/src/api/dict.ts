import request from './request'
import type {
  DictItemCreateRequest,
  DictItemFormRequest,
  DictItemOption,
  DictItemRow,
  DictTypeFormRequest,
  DictTypeRow,
  PageResult,
} from '@/types/dict'

// 字典管理接口封装，组件/store 不直接调用 axios。

// ---- 字典类型 ----

// 分页查询字典类型，支持按名称或编码模糊搜索；keyword/page/pageSize 均可选，
// 不传时后端分别按“不过滤”“第 1 页”“每页 10 条”处理
export function getDictTypePage(params: {
  keyword?: string
  page?: number
  pageSize?: number
}): Promise<PageResult<DictTypeRow>> {
  return request.get('/dict-types', { params })
}

// 获取字典类型详情
export function getDictTypeById(id: number): Promise<DictTypeRow> {
  return request.get(`/dict-types/${id}`)
}

// 新增字典类型
export function createDictType(data: DictTypeFormRequest): Promise<DictTypeRow> {
  return request.post('/dict-types', data)
}

// 编辑字典类型
export function updateDictType(id: number, data: DictTypeFormRequest): Promise<DictTypeRow> {
  return request.put(`/dict-types/${id}`, data)
}

// 启用字典类型
export function enableDictType(id: number): Promise<void> {
  return request.put(`/dict-types/${id}/enable`)
}

// 停用字典类型
export function disableDictType(id: number): Promise<void> {
  return request.put(`/dict-types/${id}/disable`)
}

// 删除字典类型（逻辑删除，若存在未删除的字典项后端会拒绝并返回错误信息）
export function deleteDictType(id: number): Promise<void> {
  return request.delete(`/dict-types/${id}`)
}

// ---- 字典项 ----

// 按所属字典类型分页查询字典项；page/pageSize 可选，不传时后端分别默认 1、10
export function getDictItemPage(params: {
  dictTypeId: number
  page?: number
  pageSize?: number
}): Promise<PageResult<DictItemRow>> {
  return request.get('/dict-items', { params })
}

// 获取字典项详情
export function getDictItemById(id: number): Promise<DictItemRow> {
  return request.get(`/dict-items/${id}`)
}

// 新增字典项，dictTypeId 固定为当前选中的字典类型
export function createDictItem(data: DictItemCreateRequest): Promise<DictItemRow> {
  return request.post('/dict-items', data)
}

// 编辑字典项，不支持修改所属字典类型
export function updateDictItem(id: number, data: DictItemFormRequest): Promise<DictItemRow> {
  return request.put(`/dict-items/${id}`, data)
}

// 启用字典项
export function enableDictItem(id: number): Promise<void> {
  return request.put(`/dict-items/${id}/enable`)
}

// 停用字典项
export function disableDictItem(id: number): Promise<void> {
  return request.put(`/dict-items/${id}/disable`)
}

// 删除字典项（逻辑删除）
export function deleteDictItem(id: number): Promise<void> {
  return request.delete(`/dict-items/${id}`)
}

// ---- 只读查询（供其他业务模块复用，如用户管理"认证类型"下拉框）----

// 按字典类型编码查询其下全部启用状态的字典项精简列表；typeCode 不存在/已停用/已删除时返回空数组
export function getDictItemOptions(typeCode: string): Promise<DictItemOption[]> {
  return request.get('/dicts/items', { params: { typeCode } })
}
