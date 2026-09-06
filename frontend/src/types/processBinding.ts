// 业务绑定相关类型定义，字段与后端 cn.nihility.rbac.workflow.dslv2.dto.ProcessBindingVO /
// ProcessBindingRequest 逐字段对齐（approval-process-binding-console change design.md）。

// 业务对象类型：组织/用户/任职/应用
export type BizType = 'ORG' | 'USER' | 'POSITION' | 'APP'

export const BIZ_TYPE_OPTIONS: Array<{ value: BizType; label: string }> = [
  { value: 'ORG', label: '组织' },
  { value: 'USER', label: '用户' },
  { value: 'POSITION', label: '任职' },
  { value: 'APP', label: '应用' },
]

// 操作类型：新增/更新/启用/停用/删除
export type OperationType = 'CREATE' | 'UPDATE' | 'ENABLE' | 'DISABLE' | 'DELETE'

export const OPERATION_TYPE_OPTIONS: Array<{ value: OperationType; label: string }> = [
  { value: 'CREATE', label: '新增' },
  { value: 'UPDATE', label: '更新' },
  { value: 'ENABLE', label: '启用' },
  { value: 'DISABLE', label: '停用' },
  { value: 'DELETE', label: '删除' },
]

// 绑定范围类型：ORG（指定组织覆盖） / GLOBAL（全局兜底）
export type ScopeType = 'ORG' | 'GLOBAL'

// 执行模式：本页面只暴露 LEGACY_SYNC（design.md Non-Goals，RELIABLE_ASYNC 暂不开放配置入口）
export type ExecutionMode = 'LEGACY_SYNC' | 'RELIABLE_ASYNC'

export const DEFAULT_EXECUTION_MODE: ExecutionMode = 'LEGACY_SYNC'

// 业务绑定返回对象，对应后端 ProcessBindingVO；不包含流程模型名称/版本号，需要前端
// 自行按 definitionId join 流程模型版本接口的数据做展示解析（design.md 决策5）。
export interface ProcessBindingVO {
  id: number
  bizType: BizType
  operationType: OperationType
  scopeType: ScopeType
  scopeId: number | null
  definitionId: number
  executionMode: string
  revision: number
  enabled: boolean
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

// 新建/切换业务绑定的请求体，对应后端 ProcessBindingRequest。
export interface ProcessBindingRequest {
  bizType: BizType
  operationType: OperationType
  scopeType: ScopeType
  scopeId?: number | null
  definitionId: number
  executionMode: string
  expectedRevision?: number | null
}
