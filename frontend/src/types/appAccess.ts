// 应用访问授权相关类型定义，字段命名和后端 cn.nihility.rbac.appaccess 模块 DTO 对齐
// （PolicyVO/PolicyCreateRequest/PolicyUpdateRequest、ManualOverrideVO/
// ManualOverrideUpsertRequest、AppAccessEffectiveItemVO）。

// 策略规则状态常量：2000 = 启用，3000 = 停用，与后端 PolicyStatus 对齐
export const POLICY_STATUS_ENABLED = 2000
export const POLICY_STATUS_DISABLED = 3000

// 用户属性条件运算符，与后端 PolicyAttrOperator 对齐：EQ/NE 比较值为单值，IN 为一组值
export type PolicyAttrOperator = 'EQ' | 'NE' | 'IN'

export const POLICY_ATTR_OPERATOR_OPTIONS: Array<{ value: PolicyAttrOperator; label: string }> = [
  { value: 'EQ', label: '等于' },
  { value: 'NE', label: '不等于' },
  { value: 'IN', label: '属于多值' },
]

// 人工例外类型，与后端 OverrideType 对齐
export type OverrideType = 'GRANT' | 'DENY'

export const OVERRIDE_TYPE_OPTIONS: Array<{ value: OverrideType; label: string }> = [
  { value: 'GRANT', label: '手动追加授权' },
  { value: 'DENY', label: '手动收回授权' },
]

// 最终生效权限判定依据，与后端 EffectiveSourceType 对齐：REVOKED 仅在按用户查询且
// includeRevoked=true 时出现
export type EffectiveSourceType = 'MANUAL_GRANT' | 'POLICY' | 'REVOKED'

// ---- 策略规则：组织范围条件 ----

// 提交请求体元素，对应后端 PolicyOrgScopeRequestItem
export interface PolicyOrgScopeRequestItem {
  orgId: number
  includeChildren: boolean
}

// 详情/列表回显，对应后端 PolicyOrgScopeVO；updateTime 供前端本地计算展示用，
// 实际"待重新执行"判定由后端 PolicyVO.pendingReExecute 统一给出
export interface PolicyOrgScopeVO {
  orgId: number
  orgName: string
  includeChildren: boolean
  updateTime: string
}

// 表单内一行的本地可编辑结构，orgId 允许暂时为空（由用户手动选择）
export interface PolicyOrgScopeFormItem {
  orgId: number | null
  includeChildren: boolean
}

// ---- 策略规则：用户属性条件 ----

// 提交请求体元素，对应后端 PolicyUserAttrRequestItem；EQ/NE 时 values 只含一个元素，
// IN 时可含多个元素
export interface PolicyUserAttrRequestItem {
  metadataFieldId: number
  operator: PolicyAttrOperator
  values: string[]
}

// 详情/列表回显，对应后端 PolicyUserAttrVO
export interface PolicyUserAttrVO {
  metadataFieldId: number
  fieldName: string
  fieldCode: string
  operator: PolicyAttrOperator
  values: string[]
  updateTime: string
}

// 表单内一行的本地可编辑结构，metadataFieldId 允许暂时为空；singleValue 用于 EQ/NE，
// multiValues 用于 IN，两者互斥展示，提交前按 operator 收敛成 values 数组
export interface PolicyUserAttrFormItem {
  metadataFieldId: number | null
  operator: PolicyAttrOperator
  singleValue: string
  multiValues: string[]
}

// ---- 策略规则：目标应用 ----

// 详情回显，对应后端 PolicyTargetAppVO
export interface PolicyTargetAppVO {
  appId: number
  appName: string
  updateTime: string
}

// ---- 策略规则：请求控制条件（浏览器白名单 / IP 网段白名单，均可选） ----

// 浏览器编码，与后端 PolicyRequestControlBrowser 对齐
export type PolicyBrowserCode = 'CHROME' | 'FIREFOX' | 'SAFARI' | 'EDGE' | 'OPERA' | 'IE'

export const POLICY_BROWSER_OPTIONS: Array<{ value: PolicyBrowserCode; label: string }> = [
  { value: 'CHROME', label: 'Chrome' },
  { value: 'FIREFOX', label: 'Firefox' },
  { value: 'SAFARI', label: 'Safari' },
  { value: 'EDGE', label: 'Edge' },
  { value: 'OPERA', label: 'Opera' },
  { value: 'IE', label: 'IE' },
]

// 详情回显，对应后端 PolicyBrowserRuleVO
export interface PolicyBrowserRuleVO {
  browserCode: PolicyBrowserCode
  browserLabel: string
}

// 详情回显，对应后端 PolicyIpRuleVO
export interface PolicyIpRuleVO {
  ipCidr: string
}

// ---- 策略规则本体 ----

// 列表/详情数据，来自 GET /api/app-access/policies、GET /api/app-access/policies/{id}、
// POST/PUT 同名接口的返回值，对应后端 PolicyVO
export interface PolicyVO {
  id: number
  name: string
  remark: string
  status: number
  lastExecTime: string | null
  lastExecBy: string | null
  orgScopes: PolicyOrgScopeVO[]
  userAttrs: PolicyUserAttrVO[]
  // 请求控制条件：浏览器白名单/IP 网段白名单，均可选，不参与"至少一项非空"校验
  browserRules: PolicyBrowserRuleVO[]
  ipRules: PolicyIpRuleVO[]
  targetApps: PolicyTargetAppVO[]
  // 配置已变更、待重新执行：组织范围/用户属性条件/目标应用中任一条最新更新时间晚于
  // lastExecTime 时为 true，由后端纯查询判断给出
  pendingReExecute: boolean
}

// 新增/编辑弹窗提交的请求体，字段与后端 PolicyCreateRequest/PolicyUpdateRequest 一致
export interface PolicyFormRequest {
  name: string
  remark: string
  orgScopes: PolicyOrgScopeRequestItem[]
  userAttrs: PolicyUserAttrRequestItem[]
  // 浏览器编码数组/IP-CIDR 字符串数组，均可选（空数组或不传表示不限制）
  browserRules: PolicyBrowserCode[]
  ipRules: string[]
  targetAppIds: number[]
}

// 分页查询参数，对应 GET /api/app-access/policies 的 query 参数，均可选
export interface PolicyQueryParams {
  name?: string
  status?: number
  page?: number
  pageSize?: number
}

// ---- 人工例外 ----

// 列表数据，来自 GET /api/app-access/manual-overrides、POST 同名接口的返回值，
// 对应后端 ManualOverrideVO
export interface ManualOverrideVO {
  id: number
  userId: number
  userName: string
  appId: number
  appName: string
  overrideType: OverrideType
  remark: string
  createTime: string
  updateTime: string
}

// 新增/更新（upsert）请求体，对应后端 ManualOverrideUpsertRequest；同一 userId+appId
// 重复提交是更新已有记录而不是新增
export interface ManualOverrideUpsertRequest {
  userId: number
  appId: number
  overrideType: OverrideType
  remark: string
}

// 分页查询参数，对应 GET /api/app-access/manual-overrides 的 query 参数，均可选
export interface ManualOverrideQueryParams {
  userId?: number
  appId?: number
  overrideType?: OverrideType
  page?: number
  pageSize?: number
}

// ---- 最终生效权限查询 ----

// 查询结果条目，来自 GET /api/app-access/effective/by-user/{userId}、
// GET /api/app-access/effective/by-app/{appId}，对应后端 AppAccessEffectiveItemVO；
// 按用户查询时 appId/appName 随条目变化，按应用查询时 userId/userName 随条目变化
export interface AppAccessEffectiveItemVO {
  userId: number
  userName: string
  appId: number
  appName: string
  sourceType: EffectiveSourceType
  policyNames: string[]
  // sourceType=POLICY 且命中策略中存在任意一条配置了请求控制条件（浏览器/IP 白名单）时为
  // true；其余场景恒为 false。仅表示"身份层面"判定，实际访问是否放行还需满足对应策略的
  // 浏览器/IP 限制
  hasRequestControl: boolean
}

// 通用分页响应结构，字段命名和后端 cn.nihility.rbac.common.result.PageResult 对齐
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}
