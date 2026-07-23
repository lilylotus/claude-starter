// 操作日志管理相关类型定义，字段命名和后端 OperationLogVO / OperationLogDetailVO /
// OperationLogQueryRequest DTO 对齐。

// 通用分页响应结构，字段命名和后端 cn.nihility.rbac.common.PageResult 对齐
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}

// 操作类型：1=新增，2=编辑，3=启用，4=停用，5=删除
export const OPERATION_TYPE_CREATE = 1
export const OPERATION_TYPE_UPDATE = 2
export const OPERATION_TYPE_ENABLE = 3
export const OPERATION_TYPE_DISABLE = 4
export const OPERATION_TYPE_DELETE = 5

// 操作类型下拉选项，前端硬编码，与后端 cn.nihility.rbac.operationlog.constant.OperationType 一一对应
export const OPERATION_TYPE_OPTIONS: { value: number; label: string }[] = [
  { value: OPERATION_TYPE_CREATE, label: '新增' },
  { value: OPERATION_TYPE_UPDATE, label: '编辑' },
  { value: OPERATION_TYPE_ENABLE, label: '启用' },
  { value: OPERATION_TYPE_DISABLE, label: '停用' },
  { value: OPERATION_TYPE_DELETE, label: '删除' },
]

// 操作来源：0=界面操作，1=Excel 导入，与后端
// cn.nihility.rbac.operationlog.constant.OperationSource 一一对应
export const OPERATION_SOURCE_MANUAL = 0
export const OPERATION_SOURCE_IMPORT = 1

// 模块下拉选项，前端硬编码，9 个业务模块中文名（字典类型/字典项同属"字典管理"模块）
export const MODULE_OPTIONS: string[] = [
  '组织管理',
  '用户管理',
  '任职管理',
  '应用管理',
  '角色管理',
  '权限点管理',
  '管理员管理',
  '菜单管理',
  '字典管理',
]

// 资源类型下拉选项，前端硬编码，与后端 cn.nihility.rbac.operationlog.constant.OperationLogResourceType 十项一一对应
export const RESOURCE_TYPE_OPTIONS: { value: string; label: string }[] = [
  { value: 'org', label: '组织' },
  { value: 'user', label: '用户' },
  { value: 'position', label: '任职' },
  { value: 'app', label: '应用' },
  { value: 'role', label: '角色' },
  { value: 'permission', label: '权限点' },
  { value: 'admin', label: '管理员' },
  { value: 'menu', label: '菜单' },
  { value: 'dictType', label: '字典类型' },
  { value: 'dictItem', label: '字典项' },
]

// 分页查询筛选参数，字段与后端 GET /api/operation-logs 的 query 参数一致，全部可选
export interface OperationLogQueryParams {
  module?: string
  resourceType?: string
  targetId?: number
  operationType?: number
  createBy?: string
  startTime?: string
  endTime?: string
  page?: number
  pageSize?: number
}

// 字段级变更详情，field 为中文字段名；新增操作 oldValue 为空、删除操作 newValue 为空
export interface OperationLogFieldChange {
  field: string
  oldValue: string | null
  newValue: string | null
}

// 操作日志列表行，来自 GET /api/operation-logs 分页接口；changeDetail 为该次操作的
// 字段级变更明细，随分页响应一并返回（详情接口 GET /api/operation-logs/{id} 也有同名字段）
export interface OperationLogRow {
  id: number
  module: string
  resourceType: string
  resourceName: string
  operationType: number
  operationTypeLabel: string
  operateSource: number
  operateSourceLabel: string
  targetId: number
  targetName: string
  operateIp: string | null
  operateTerminal: string | null
  operateOs: string | null
  operateBrowser: string | null
  createBy: string
  createTime: string
  changeDetail?: OperationLogFieldChange[]
}

// 操作日志详情，来自 GET /api/operation-logs/{id}，在列表行字段基础上追加原始
// User-Agent 与结构化的字段变更列表
export interface OperationLogDetail extends OperationLogRow {
  operateUserAgent: string | null
  changeDetail: OperationLogFieldChange[]
}
