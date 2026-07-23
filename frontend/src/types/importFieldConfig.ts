// Excel 导入字段配置相关类型定义，字段命名和后端 ImportFieldConfigVO /
// ImportFieldConfigCreateRequest / ImportFieldConfigUpdateRequest / ImportResultVO /
// ImportFailItemVO 对齐（cn.nihility.rbac.excelimport 模块）。

import type { FormFieldBizType } from './metadataField'

export type { FormFieldBizType }

// 状态常量：2000 = 启用，-1000 = 已逻辑删除（后端接口已过滤，前端不会拿到）。
// 导入字段配置没有"停用"这个中间状态，只有启用/删除两种，和表单字段定义不同。
export const IMPORT_FIELD_CONFIG_STATUS_ENABLED = 2000

// 导入字段配置列表行/详情数据，来自 GET /api/import-field-configs、
// GET /api/import-field-configs/{id}、POST /api/import-field-configs、
// PUT /api/import-field-configs/{id}
export interface ImportFieldConfig {
  id: number
  bizType: FormFieldBizType
  formFieldDefinitionId: number | null
  fieldCode: string
  excelHeaderName: string
  isPrimaryKey: boolean
  isRequired: boolean
  showOrder: number
  status: number
  // 是否为系统保护（锁定）配置，计算得出，不落库：POSITION 分类下 fieldCode 为
  // __userCode/__orgCode 的两条固定标识列；锁定配置不可删除，也不可把主键/必填
  // 标记改为否
  locked: boolean
  createBy: string
  createTime: string
  updateBy: string
  updateTime: string
}

// 新增导入字段配置请求体。formFieldDefinitionId 非空时 fieldCode 由服务端从所绑定的
// 表单字段定义派生，请求中提交的 fieldCode 会被忽略；formFieldDefinitionId 为空时
// fieldCode 必填且由服务端直接采用
export interface ImportFieldConfigCreateRequest {
  bizType: FormFieldBizType
  formFieldDefinitionId: number | null
  fieldCode: string
  excelHeaderName: string
  isPrimaryKey: boolean
  isRequired: boolean
  showOrder: number
}

// 编辑导入字段配置请求体：不含 bizType（创建后不可改）与 fieldCode（完全派生，不由
// 客户端提交）。formFieldDefinitionId 省略或等于当前值表示不改绑；锁定配置拒绝改绑，
// 也拒绝把 isPrimaryKey/isRequired 改为 false
export interface ImportFieldConfigUpdateRequest {
  formFieldDefinitionId: number | null
  excelHeaderName: string
  isPrimaryKey: boolean
  isRequired: boolean
  showOrder: number
}

// 批量导入单行失败明细：Excel 行号（从 1 开始，含表头行）与失败原因
export interface ImportFailItem {
  rowNo: number
  reason: string
}

// 批量导入结果：成功导入条数与失败明细列表
export interface ImportResult {
  successCount: number
  failList: ImportFailItem[]
}

// 通用分页响应结构，字段命名和后端 cn.nihility.rbac.common.PageResult 对齐
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}
