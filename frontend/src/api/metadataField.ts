import request from './request'
import type { FormFieldBizType, MetadataField, MetadataFieldUpdateRequest, PageResult } from '@/types/metadataField'

// 元数据字段配置接口封装，组件/store 不直接调用 axios。目录只能通过数据库迁移预置，
// 本模块不提供新增/删除接口。

// 按业务对象类型分页查询元数据字段；bizType/page/pageSize 均可选，
// 不传 bizType 时查询全部，page/pageSize 不传时后端分别默认 1、10
export function getMetadataFieldPage(params: {
  bizType?: FormFieldBizType
  page?: number
  pageSize?: number
}): Promise<PageResult<MetadataField>> {
  return request.get('/metadata-fields', { params })
}

// 获取元数据字段详情
export function getMetadataFieldById(id: number): Promise<MetadataField> {
  return request.get(`/metadata-fields/${id}`)
}

// 编辑元数据字段，仅 fieldName 可改
export function updateMetadataField(id: number, data: MetadataFieldUpdateRequest): Promise<MetadataField> {
  return request.put(`/metadata-fields/${id}`, data)
}

// 启用元数据字段
export function enableMetadataField(id: number): Promise<MetadataField> {
  return request.put(`/metadata-fields/${id}/enable`)
}

// 停用元数据字段（若被有效表单字段定义绑定，后端会拒绝并返回错误信息）
export function disableMetadataField(id: number): Promise<MetadataField> {
  return request.put(`/metadata-fields/${id}/disable`)
}

// 查询指定业务对象类型下"可用"的元数据字段（状态启用且未被任何有效表单字段定义绑定），
// 供表单管理新增字段定义时选择；bizType 必填
export function fetchAvailableMetadataFields(bizType: FormFieldBizType): Promise<MetadataField[]> {
  return request.get('/metadata-fields/available', { params: { bizType } })
}
