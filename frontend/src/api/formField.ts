import request from './request'
import type {
  FormFieldBizType,
  FormFieldDefinition,
  FormFieldDefinitionCreateRequest,
  FormFieldDefinitionUpdateRequest,
  FormFieldRenderItem,
  PageResult,
} from '@/types/formField'

// 表单字段定义接口封装，组件/store 不直接调用 axios。

// 按业务对象类型分页查询字段定义；bizType/page/pageSize 均可选，
// 不传 bizType 时查询全部，page/pageSize 不传时后端分别默认 1、10
export function getFormFieldPage(params: {
  bizType?: FormFieldBizType
  page?: number
  pageSize?: number
}): Promise<PageResult<FormFieldDefinition>> {
  return request.get('/form-fields', { params })
}

// 获取字段定义详情
export function getFormFieldById(id: number): Promise<FormFieldDefinition> {
  return request.get(`/form-fields/${id}`)
}

// 新增字段定义，metadataFieldId 必须指向一个当前状态为启用、且未被其他有效定义绑定的元数据字段
export function createFormField(data: FormFieldDefinitionCreateRequest): Promise<FormFieldDefinition> {
  return request.post('/form-fields', data)
}

// 编辑字段定义，不支持修改绑定的元数据字段（请求体不含 metadataFieldId）
export function updateFormField(id: number, data: FormFieldDefinitionUpdateRequest): Promise<FormFieldDefinition> {
  return request.put(`/form-fields/${id}`, data)
}

// 启用字段定义
export function enableFormField(id: number): Promise<FormFieldDefinition> {
  return request.put(`/form-fields/${id}/enable`)
}

// 停用字段定义（绑定承重字段的定义后端会拒绝并返回错误信息）
export function disableFormField(id: number): Promise<FormFieldDefinition> {
  return request.put(`/form-fields/${id}/disable`)
}

// 删除字段定义（逻辑删除，绑定承重字段的定义后端会拒绝并返回错误信息）
export function deleteFormField(id: number): Promise<void> {
  return request.delete(`/form-fields/${id}`)
}

// 查询指定业务对象类型下全部启用状态的字段定义渲染元数据，供组织/用户/任职/应用
// 四个管理页面动态渲染列表列与新增/编辑表单；bizType 必填
export function fetchFormFieldRenderSchema(bizType: FormFieldBizType): Promise<FormFieldRenderItem[]> {
  return request.get('/form-fields/render-schema', { params: { bizType } })
}
