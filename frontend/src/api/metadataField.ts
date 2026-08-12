import request from './request'
import type {
  FormFieldBizType,
  MetadataField,
  MetadataFieldBizType,
  MetadataFieldUpdateRequest,
  PageResult,
} from '@/types/metadataField'

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

// 编辑元数据字段，fieldName、fieldCode 均可改（fieldCode 需在同一 bizType 下唯一，重复会被拒绝）
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
// 供表单管理新增/编辑字段定义时选择；bizType 必填。
// excludeDefinitionId 可选：传入编辑中的表单字段定义 id 时，响应会额外带上该定义当前
// 绑定的元数据字段（即便它已被占用），供编辑弹窗下拉框展示"当前绑定 + 其余可选"；
// 新增弹窗调用时不传，行为不变。
export function fetchAvailableMetadataFields(
  bizType: FormFieldBizType,
  excludeDefinitionId?: number,
): Promise<MetadataField[]> {
  return request.get('/metadata-fields/available', { params: { bizType, excludeDefinitionId } })
}

// 应用同步字段映射选择源字段专用：复用现有分页查询接口（不是上面的 available 接口，见
// openspec/changes/app-sync-field-mapping/design.md Decision 8——available 接口语义是
// "未被表单字段定义绑定"，会漏掉已被绑定的核心字段，与字段映射选择源字段的语义不匹配）。
// bizType 取值范围比 FormFieldBizType 多一个 'ROLE'，一次性拉一页（pageSize 默认 200，
// 单个数据域的字段数量级在几十条以内，不需要真正的分页交互），调用方按 status 过滤展示。
export function getMetadataFieldPageForSyncDomain(
  bizType: MetadataFieldBizType,
  pageSize = 200,
): Promise<PageResult<MetadataField>> {
  return request.get('/metadata-fields', { params: { bizType, pageSize } })
}
