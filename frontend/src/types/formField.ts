// 表单字段定义相关类型定义，字段命名和后端 FormFieldDefinitionVO / FormFieldDefinitionCreateRequest /
// FormFieldDefinitionUpdateRequest / FormFieldRenderItemVO / FormFieldDictOptionVO 对齐。

import type { FormFieldBizType } from './metadataField'

export type { FormFieldBizType }

// 控件类型常量：1 = 文本框，2 = 数字框，3 = 字典下拉，4 = 日期，5 = 多选字典下拉，
// 和后端 cn.nihility.rbac.formfield.constant.FormFieldControlType 对齐。
export const FORM_FIELD_CONTROL_TYPE_TEXT = 1
export const FORM_FIELD_CONTROL_TYPE_NUMBER = 2
export const FORM_FIELD_CONTROL_TYPE_DICT = 3
export const FORM_FIELD_CONTROL_TYPE_DATE = 4
export const FORM_FIELD_CONTROL_TYPE_MULTI_DICT = 5

export const FORM_FIELD_CONTROL_TYPE_OPTIONS: Array<{ value: number; label: string }> = [
  { value: FORM_FIELD_CONTROL_TYPE_TEXT, label: '文本框' },
  { value: FORM_FIELD_CONTROL_TYPE_NUMBER, label: '数字框' },
  { value: FORM_FIELD_CONTROL_TYPE_DICT, label: '字典下拉' },
  { value: FORM_FIELD_CONTROL_TYPE_DATE, label: '日期' },
  { value: FORM_FIELD_CONTROL_TYPE_MULTI_DICT, label: '多选字典下拉' },
]

// 状态常量：2000 = 启用，3000 = 停用（-1000 为逻辑删除，后端接口已过滤，前端不会拿到）。
export const FORM_FIELD_STATUS_ENABLED = 2000
export const FORM_FIELD_STATUS_DISABLED = 3000

// 表单字段定义列表行/详情数据，来自 GET /api/form-fields、GET /api/form-fields/{id}、
// POST /api/form-fields、PUT /api/form-fields/{id}
export interface FormFieldDefinition {
  id: number
  bizType: FormFieldBizType
  metadataFieldId: number
  columnName: string
  fieldName: string
  fieldCode: string
  controlType: number
  dictTypeCode: string | null
  dictTypeName: string | null
  isUnique: boolean
  isRequired: boolean
  showInList: boolean
  showInCreate: boolean
  showInEdit: boolean
  editable: boolean
  validateRegex: string | null
  placeholder: string | null
  showOrder: number
  status: number
  // 是否为承重字段（锁定字段），计算得出，不落库；锁定的定义不可停用/删除，
  // 也不可把必填/新增或编辑表单展示改为否
  locked: boolean
  createBy: string
  createTime: string
  updateBy: string
  updateTime: string
}

// 字典下拉可选项，来自渲染元数据接口内嵌的 dictOptions
export interface FormFieldDictOption {
  label: string
  value: string
}

// 动态字段渲染元数据条目，来自 GET /api/form-fields/render-schema，驱动组织/用户/
// 任职/应用四个管理页面的列表列与新增/编辑表单动态渲染
export interface FormFieldRenderItem {
  fieldCode: string
  fieldName: string
  columnName: string
  controlType: number
  isRequired: boolean
  isUnique: boolean
  showInList: boolean
  showInCreate: boolean
  showInEdit: boolean
  editable: boolean
  locked: boolean
  validateRegex: string | null
  placeholder: string | null
  showOrder: number
  dictOptions: FormFieldDictOption[]
}

// 新增/编辑表单字段定义弹窗共用的表单字段；fieldCode 不在此列——它完全由服务端从绑定的
// 元数据字段派生，创建/编辑请求都不携带，前端仅在展示层（禁用输入框）读取响应值
export interface FormFieldDefinitionFormFields {
  fieldName: string
  controlType: number
  dictTypeCode: string | null
  isUnique: boolean
  isRequired: boolean
  showInList: boolean
  showInCreate: boolean
  showInEdit: boolean
  editable: boolean
  validateRegex: string
  placeholder: string
  showOrder: number
}

// 新增字段定义请求体：在编辑表单字段基础上附加绑定的元数据字段 id；fieldCode 由服务端
// 从该元数据字段派生，不由客户端提交
export interface FormFieldDefinitionCreateRequest extends FormFieldDefinitionFormFields {
  metadataFieldId: number
}

// 编辑字段定义请求体：不含 bizType（绑定关系所属业务对象类型不可跨越）；metadataFieldId
// 可选，省略或等于当前值表示不改绑，传入不同值触发服务端改绑校验（锁定定义禁止改绑）；
// 改绑成功后 fieldCode 会随之刷新为新绑定元数据字段的当前值（服务端派生，不由客户端提交）
export interface FormFieldDefinitionUpdateRequest extends FormFieldDefinitionFormFields {
  metadataFieldId?: number
}

// 通用分页响应结构，字段命名和后端 cn.nihility.rbac.common.PageResult 对齐
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}
