// 上游数据管理相关类型定义，字段命名和后端 cn.nihility.rbac.identity.upstream.dto 下各 DTO 对齐
// （UpstreamSourceVO/UpstreamSourceCreateRequest/UpstreamSourceUpdateRequest/
// UpstreamConnectionConfigRequest/UpstreamScheduleConfigRequest/UpstreamDomainConfigVO/
// UpstreamDomainConfigUpdateRequest/UpstreamFieldMappingVO/UpstreamFieldMappingSaveRequest/
// UpstreamSyncRecordVO），常量取值与后端 constant 包下同名类保持一致。

// 同步方式：API（接口）/DB_TABLE（数据库表），与后端 UpstreamSyncType 对齐
export type UpstreamSyncType = 'API' | 'DB_TABLE'

export const UPSTREAM_SYNC_TYPE_OPTIONS: Array<{ value: UpstreamSyncType; label: string }> = [
  { value: 'API', label: '接口' },
  { value: 'DB_TABLE', label: '数据库表' },
]

// 数据域：组织/用户/任职三值，与后端 UpstreamDataType 对齐（不同于 app.sync 的六值 SyncDomain）
export type UpstreamDataType = 'ORG' | 'USER' | 'POSITION'

export const UPSTREAM_DATA_TYPE_OPTIONS: Array<{ value: UpstreamDataType; label: string }> = [
  { value: 'ORG', label: '组织' },
  { value: 'USER', label: '用户' },
  { value: 'POSITION', label: '任职' },
]

// 一次同步执行时处理数据域的固定顺序：组织→用户→任职，与后端 UpstreamDataType.SYNC_ORDER 对齐
export const UPSTREAM_DATA_TYPE_SYNC_ORDER: UpstreamDataType[] = ['ORG', 'USER', 'POSITION']

// 调度方式：按间隔/按每日固定时间点，与后端 UpstreamScheduleType 对齐
export type UpstreamScheduleType = 'INTERVAL' | 'FIXED_TIME'

export const UPSTREAM_SCHEDULE_TYPE_OPTIONS: Array<{ value: UpstreamScheduleType; label: string }> = [
  { value: 'INTERVAL', label: '按间隔' },
  { value: 'FIXED_TIME', label: '按每日固定时间点' },
]

// 间隔单位：分钟/小时，与后端 UpstreamIntervalUnit 对齐
export type UpstreamIntervalUnit = 'MINUTE' | 'HOUR'

export const UPSTREAM_INTERVAL_UNIT_OPTIONS: Array<{ value: UpstreamIntervalUnit; label: string }> = [
  { value: 'MINUTE', label: '分钟' },
  { value: 'HOUR', label: '小时' },
]

// 接口方式请求方式：GET/POST，与后端 UpstreamApiMethod 对齐
export type UpstreamApiMethod = 'GET' | 'POST'

export const UPSTREAM_API_METHOD_OPTIONS: Array<{ value: UpstreamApiMethod; label: string }> = [
  { value: 'GET', label: 'GET' },
  { value: 'POST', label: 'POST' },
]

// 同步触发方式：定时轮询触发/手动触发，与后端 UpstreamTriggerType 对齐
export type UpstreamTriggerType = 'SCHEDULE' | 'MANUAL'

export const UPSTREAM_TRIGGER_TYPE_LABELS: Record<UpstreamTriggerType, string> = {
  SCHEDULE: '定时触发',
  MANUAL: '手动触发',
}

// 同步执行记录状态：全部成功/部分失败/全部失败或异常，与后端 UpstreamSyncStatus 对齐
export type UpstreamSyncStatus = 'SUCCESS' | 'PARTIAL' | 'FAILED'

export const UPSTREAM_SYNC_STATUS_LABELS: Record<UpstreamSyncStatus, string> = {
  SUCCESS: '成功',
  PARTIAL: '部分失败',
  FAILED: '失败',
}

// el-tag type：成功绿色、部分失败橙色、失败红色
export const UPSTREAM_SYNC_STATUS_TAG_TYPE: Record<UpstreamSyncStatus, 'success' | 'warning' | 'danger'> = {
  SUCCESS: 'success',
  PARTIAL: 'warning',
  FAILED: 'danger',
}

// ORG 数据域的"上级组织编码"固定伪字段编码，不经过字段映射表配置，取数结果如包含该列，
// 按组织 code 匹配得到上级组织；取不到/为空/字面 "0" 均视为顶级组织，与后端
// UpstreamOrgPseudoFieldCode.PARENT_CODE 对齐
export const UPSTREAM_ORG_PARENT_CODE_FIELD = '__parentCode'

// POSITION 数据域的两个固定伪字段编码，不经过字段映射表配置，取数结果须包含这两列，
// 用于确定任职记录归属的所属人员/所属组织，与后端 UpstreamPositionPseudoFieldCode 对齐
export const UPSTREAM_POSITION_USER_IDENTIFIER_FIELD = '__userIdentifier'
export const UPSTREAM_POSITION_ORG_CODE_FIELD = '__orgCode'

// 上游数据源列表行/详情数据，来自 GET /api/identity/upstream-sources、
// GET/POST/PUT .../{id}、.../{id}/enable、.../{id}/disable、.../{id}/connection、
// .../{id}/schedule 等接口的返回值。出于安全策略，apiAuthHeaderKeys 只返回 key 列表
// （不返回明文取值），dbPassword 永远不出现在响应体里。
export interface UpstreamSourceVO {
  id: number
  name: string
  syncType: UpstreamSyncType
  enabled: boolean
  scheduleType: UpstreamScheduleType
  intervalUnit: UpstreamIntervalUnit | ''
  intervalValue: number | null
  fixedTime: string | ''
  lastTriggerTime: string | null
  apiAuthHeaderKeys: string[]
  dbJdbcUrl: string | null
  dbUsername: string | null
  createTime: string
  updateTime: string
}

// 创建上游数据源请求体，对应 POST /api/identity/upstream-sources；enabled 由后端固定为 false
export interface UpstreamSourceCreateRequest {
  name: string
  syncType: UpstreamSyncType
}

// 更新上游数据源基础信息请求体，对应 PUT /api/identity/upstream-sources/{id}；
// 创建后仍允许修改同步方式
export interface UpstreamSourceUpdateRequest {
  name: string
  syncType: UpstreamSyncType
}

// 更新连接配置请求体，对应 PUT /api/identity/upstream-sources/{id}/connection；
// apiAuthHeaders 整体替换语义（syncType=API 时生效），dbPassword 留空表示不修改
// 已保存的密码（syncType=DB_TABLE 时生效）
export interface UpstreamConnectionConfigRequest {
  apiAuthHeaders: Record<string, string>
  dbJdbcUrl: string
  dbUsername: string
  dbPassword: string
}

// 更新调度配置请求体，对应 PUT /api/identity/upstream-sources/{id}/schedule
export interface UpstreamScheduleConfigRequest {
  scheduleType: UpstreamScheduleType
  intervalUnit: UpstreamIntervalUnit | ''
  intervalValue: number | null
  fixedTime: string
}

// 数据域配置视图对象，来自 GET /api/identity/upstream-sources/{id}/domains、
// PUT .../domains/{dataType} 的返回值（每个数据源固定返回 ORG/USER/POSITION 三行）
export interface UpstreamDomainConfigVO {
  id: number
  sourceId: number
  dataType: UpstreamDataType
  enabled: boolean
  apiUrl: string | null
  apiMethod: UpstreamApiMethod | ''
  dbSql: string | null
  lastSyncTime: string | null
}

// 修改数据域配置请求体，对应 PUT /api/identity/upstream-sources/{id}/domains/{dataType}
export interface UpstreamDomainConfigUpdateRequest {
  enabled: boolean
  apiUrl: string
  apiMethod: UpstreamApiMethod | ''
  dbSql: string
}

// 上游字段映射视图对象，来自 GET/PUT /api/identity/upstream-sources/{id}/domains/{dataType}/field-mappings；
// upstreamFieldName/upstreamFieldCode 是管理员手工填写、可编辑的源字段信息，fieldName/fieldCode
// 是目标（关联的元数据字段）的实时展示信息，只读——方向与 AppSyncFieldMappingVO 相反
export interface UpstreamFieldMappingVO {
  id: number
  upstreamFieldName: string
  upstreamFieldCode: string
  metadataFieldId: number
  fieldName: string
  fieldCode: string
  transformType: import('@/types/app').TransformType
  transformValue: string | null
}

// 整体替换字段映射列表时单行的请求体，对应 PUT .../field-mappings 的请求体元素
export interface UpstreamFieldMappingSaveRequest {
  upstreamFieldName: string
  upstreamFieldCode: string
  metadataFieldId: number
  transformType: import('@/types/app').TransformType
  transformValue: string | null
}

// 上游数据同步执行记录视图对象，来自 GET /api/identity/upstream-sources/{id}/sync-records，按时间倒序
export interface UpstreamSyncRecordVO {
  id: number
  sourceId: number
  dataType: UpstreamDataType
  triggerType: UpstreamTriggerType
  startTime: string
  endTime: string | null
  status: UpstreamSyncStatus
  totalCount: number
  successCount: number
  failCount: number
  failSummary: string | null
}
