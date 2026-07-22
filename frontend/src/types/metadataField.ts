// 元数据字段配置相关类型定义，字段命名和后端 MetadataFieldVO / MetadataFieldUpdateRequest 对齐。

// 表单字段定义体系覆盖的业务对象类型，和后端 cn.nihility.rbac.formfield.constant.FormFieldBizType 对齐，
// 元数据字段配置模块与表单字段定义模块共用同一份取值。
export type FormFieldBizType = 'ORG' | 'USER' | 'POSITION' | 'APP'

export const FORM_FIELD_BIZ_TYPE_OPTIONS: Array<{ value: FormFieldBizType; label: string }> = [
  { value: 'ORG', label: '组织' },
  { value: 'USER', label: '人员' },
  { value: 'POSITION', label: '任职' },
  { value: 'APP', label: '应用' },
]

// 状态常量：2000 = 启用，3000 = 停用（-1000 为逻辑删除，后端接口已过滤，前端不会拿到）。
export const METADATA_FIELD_STATUS_ENABLED = 2000
export const METADATA_FIELD_STATUS_DISABLED = 3000

// 元数据字段列表行/详情数据，来自 GET /api/metadata-fields、GET /api/metadata-fields/{id}、
// GET /api/metadata-fields/available
export interface MetadataField {
  id: number
  bizType: FormFieldBizType
  tableName: string
  columnName: string
  columnType: string
  fieldCode: string
  fieldName: string
  status: number
  createBy: string
  createTime: string
  updateBy: string
  updateTime: string
}

// 编辑元数据字段的请求体：字段名称、字段标识可改，表名称/字段列名/字段类型/业务对象类型
// 描述真实数据库结构，创建（迁移写入）后不可修改；fieldCode 需在同一 bizType 下唯一，
// 重复会被后端拒绝
export interface MetadataFieldUpdateRequest {
  fieldName: string
  fieldCode: string
}

// 通用分页响应结构，字段命名和后端 cn.nihility.rbac.common.PageResult 对齐
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}
