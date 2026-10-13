// ABAC 策略管理相关类型定义，字段命名和后端 cn.nihility.rbac.abac.dto 对齐
// （AbacPolicyVO/AbacPolicyCreateRequest/AbacPolicyUpdateRequest/AbacPolicyQueryRequest）。

// 策略状态常量：2000 = 启用，3000 = 停用，与后端 AbacPolicyStatus 对齐
export const ABAC_POLICY_STATUS_ENABLED = 2000
export const ABAC_POLICY_STATUS_DISABLED = 3000

// 用户属性条件运算符，与后端 AbacAttrOperator 对齐：EQ/NE 比较值为单值，IN 为一组值
export type AbacAttrOperator = 'EQ' | 'NE' | 'IN'

export const ABAC_ATTR_OPERATOR_OPTIONS: Array<{ value: AbacAttrOperator; label: string }> = [
  { value: 'EQ', label: '等于' },
  { value: 'NE', label: '不等于' },
  { value: 'IN', label: '属于多值' },
]

// 星期选项：1=周一...7=周日，与后端 dayOfWeek 对齐；不选表示每天均适用
export const ABAC_DAY_OF_WEEK_OPTIONS: Array<{ value: number; label: string }> = [
  { value: 1, label: '周一' },
  { value: 2, label: '周二' },
  { value: 3, label: '周三' },
  { value: 4, label: '周四' },
  { value: 5, label: '周五' },
  { value: 6, label: '周六' },
  { value: 7, label: '周日' },
]

// ---- 用户属性条件 ----

// 提交请求体元素，对应后端 AbacPolicyUserAttrRequestItem；EQ/NE 时 values 只含一个元素，
// IN 时可含多个元素
export interface AbacPolicyUserAttrRequestItem {
  metadataFieldId: number
  operator: AbacAttrOperator
  values: string[]
}

// 详情回显，对应后端 AbacPolicyUserAttrVO
export interface AbacPolicyUserAttrVO {
  metadataFieldId: number
  fieldName: string
  fieldCode: string
  operator: AbacAttrOperator
  values: string[]
}

// 表单内一行的本地可编辑结构，metadataFieldId 允许暂时为空；singleValue 用于 EQ/NE，
// multiValues 用于 IN，两者互斥展示，提交前按 operator 收敛成 values 数组
export interface AbacPolicyUserAttrFormItem {
  metadataFieldId: number | null
  operator: AbacAttrOperator
  singleValue: string
  multiValues: string[]
}

// ---- 时间窗口条件 ----

// 提交请求体元素，对应后端 AbacPolicyTimeWindowRequestItem；dayOfWeek 不填代表每天均
// 适用，startTime/endTime 为 "HH:mm:ss" 格式字符串（后端 java.time.LocalTime 无法表示
// 24:00，"全天"须表示为 00:00:00~23:59:59，结束时间必须严格晚于起始时间）
export interface AbacPolicyTimeWindowRequestItem {
  dayOfWeek?: number
  startTime: string
  endTime: string
}

// 详情回显，对应后端 AbacPolicyTimeWindowVO
export interface AbacPolicyTimeWindowVO {
  dayOfWeek?: number
  startTime: string
  endTime: string
}

// 表单内一行的本地可编辑结构；dayOfWeek 为 null 表示不限定星期（每天均适用）；
// startTime/endTime 用 el-time-picker 编辑，值为 "HH:mm:ss" 字符串
export interface AbacPolicyTimeWindowFormItem {
  dayOfWeek: number | null
  startTime: string
  endTime: string
}

// ---- 策略规则本体 ----

// 列表/详情数据，来自 GET /api/abac/policies、GET /api/abac/policies/{id}、
// POST/PUT 同名接口的返回值，对应后端 AbacPolicyVO
export interface AbacPolicyVO {
  id: number
  name: string
  remark: string
  status: number
  // 显示序号，仅用于列表展示排序，不参与判定优先级（design.md Decision 1 的 OR 语义
  // 不需要优先级）
  showOrder: number
  resourceCodes: string[]
  userAttrs: AbacPolicyUserAttrVO[]
  timeWindows: AbacPolicyTimeWindowVO[]
  createBy: string
  createTime: string
  updateBy: string
  updateTime: string
}

// 新增/编辑弹窗提交的请求体，字段与后端 AbacPolicyCreateRequest/AbacPolicyUpdateRequest 一致
export interface AbacPolicyFormRequest {
  name: string
  remark: string
  showOrder: number
  resourceCodes: string[]
  userAttrs: AbacPolicyUserAttrRequestItem[]
  timeWindows: AbacPolicyTimeWindowRequestItem[]
}

// 分页查询参数，对应 GET /api/abac/policies 的 query 参数，均可选
export interface AbacPolicyQueryParams {
  name?: string
  status?: number
  page?: number
  pageSize?: number
}

// 通用分页响应结构，字段命名和后端 cn.nihility.rbac.common.result.PageResult 对齐
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}
