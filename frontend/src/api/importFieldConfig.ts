import request from './request'
import type {
  FormFieldBizType,
  ImportFieldConfig,
  ImportFieldConfigCreateRequest,
  ImportFieldConfigUpdateRequest,
  PageResult,
} from '@/types/importFieldConfig'

// Excel 导入字段配置接口封装，组件/store 不直接调用 axios。

// 按业务对象类型分页查询导入字段配置；bizType/page/pageSize 均可选，
// 不传 bizType 时查询全部，page/pageSize 不传时后端分别默认 1、10；按 showOrder 降序排列
export function getImportFieldConfigPage(params: {
  bizType?: FormFieldBizType
  page?: number
  pageSize?: number
}): Promise<PageResult<ImportFieldConfig>> {
  return request.get('/import-field-configs', { params })
}

// 获取导入字段配置详情
export function getImportFieldConfigById(id: number): Promise<ImportFieldConfig> {
  return request.get(`/import-field-configs/${id}`)
}

// 新增导入字段配置；formFieldDefinitionId 非空时须指向一个存在且启用、bizType 一致的
// 表单字段定义；为空时须直接提交 fieldCode
export function createImportFieldConfig(data: ImportFieldConfigCreateRequest): Promise<ImportFieldConfig> {
  return request.post('/import-field-configs', data)
}

// 编辑导入字段配置；系统保护（locked）的配置后端会拒绝改绑/取消主键或必填标记
export function updateImportFieldConfig(
  id: number,
  data: ImportFieldConfigUpdateRequest,
): Promise<ImportFieldConfig> {
  return request.put(`/import-field-configs/${id}`, data)
}

// 删除导入字段配置（逻辑删除，系统保护的配置后端会拒绝并返回错误信息）
export function deleteImportFieldConfig(id: number): Promise<void> {
  return request.delete(`/import-field-configs/${id}`)
}
