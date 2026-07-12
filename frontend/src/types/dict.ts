// 字典管理相关类型定义，字段命名和后端 DictTypeVO / DictItemVO / DictItemOptionVO 对齐。

// 状态常量：2000 = 启用，3000 = 停用（-1000 为逻辑删除，后端接口已过滤，前端不会拿到）。
export const DICT_STATUS_ENABLED = 2000
export const DICT_STATUS_DISABLED = 3000

// 字典类型列表/详情行数据，来自 GET /api/dict-types、GET /api/dict-types/{id}
export interface DictTypeRow {
  id: number
  name: string
  code: string
  showOrder: number
  remark: string
  status: number
  createBy: string
  createTime: string
  updateBy: string
  updateTime: string
}

// 字典项列表/详情行数据，来自 GET /api/dict-items、GET /api/dict-items/{id}
export interface DictItemRow {
  id: number
  dictTypeId: number
  dictTypeName: string
  label: string
  code: string
  showOrder: number
  remark: string
  status: number
  createBy: string
  createTime: string
  updateBy: string
  updateTime: string
}

// 只读查询接口返回的精简字典项选项，来自 GET /api/dicts/items?typeCode=，
// 供其他业务模块（如即将开发的用户管理"认证类型"下拉框）复用
export interface DictItemOption {
  code: string
  label: string
  showOrder: number
}

// 新增/编辑字典类型的请求体
export interface DictTypeFormRequest {
  name: string
  code: string
  showOrder: number
  remark: string
}

// 编辑字典项的请求体：不含 dictTypeId——后端更新接口不支持修改字典项所属的字典类型
export interface DictItemFormRequest {
  label: string
  code: string
  showOrder: number
  remark: string
}

// 新增字典项的请求体：在表单字段基础上附加所属字典类型 id；
// 该 id 固定为当前右侧选中的字典类型，页面上不提供切换入口
export interface DictItemCreateRequest extends DictItemFormRequest {
  dictTypeId: number
}

// 通用分页响应结构，字段命名和后端 cn.nihility.rbac.common.PageResult 对齐
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}
