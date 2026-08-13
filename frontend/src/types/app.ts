// 应用管理相关类型定义，字段命名和后端 AppVO / AppCreateRequest / AppUpdateRequest DTO 对齐。

// 状态常量：2000 = 启用，3000 = 停用（-1000 为逻辑删除，后端接口已过滤，前端不会拿到）。
export const APP_STATUS_ENABLED = 2000
export const APP_STATUS_DISABLED = 3000

// 应用列表行/详情数据，来自 GET /api/apps、GET /api/apps/{id}、POST /api/apps、
// PUT /api/apps/{id} 等接口的返回值
export interface AppRow {
  id: number
  name: string
  code: string
  ownerId: number
  ownerName: string
  orgId: number
  orgName: string
  showOrder: number
  remark: string
  // ext1~ext10：可开放配置的扩展字段，是否展示/可编辑由"表单字段定义"（bizType=APP）驱动，
  // 未被任何启用字段定义绑定时恒为空
  ext1?: string
  ext2?: string
  ext3?: string
  ext4?: string
  ext5?: string
  ext6?: string
  ext7?: string
  ext8?: string
  ext9?: string
  ext10?: string
  status: number
  createBy: string
  createTime: string
  updateBy: string
  updateTime: string
}

// 新增/编辑弹窗提交的请求体，字段与后端 AppCreateRequest/AppUpdateRequest 一致
export interface AppFormRequest {
  name: string
  code: string
  ownerId: number
  orgId: number
  showOrder: number
  remark: string
  ext1?: string
  ext2?: string
  ext3?: string
  ext4?: string
  ext5?: string
  ext6?: string
  ext7?: string
  ext8?: string
  ext9?: string
  ext10?: string
}

// 通用分页响应结构，字段命名和后端 cn.nihility.rbac.common.PageResult 对齐
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}

// 签名算法可选值，与后端 app/constant/SignAlgorithm 对齐
export type SignAlgorithm = 'SHA256' | 'SM3'

// 同步方式可选值，与后端 app/constant/SyncMode 对齐；整个应用一份，不区分组织/用户/应用/字典
export type SyncMode = 'NOTIFY' | 'PULL'

// 应用对外接口配置，来自 GET /api/apps/{id}/config、PUT .../sign-algorithm、PUT .../sync
// 的返回值；出于安全策略，永远不包含 secretKey 字段（明文只在重置接口的响应里出现一次）。
// 数据范围（组织/用户/任职/应用/角色/字典各自的启用开关+分页大小）从 v2 起改由独立的
// AppSyncDomainConfigVO 六行承载（见下方 GET /api/apps/{id}/config/sync/domains），
// 不再随这个接口一起返回。
export interface AppConfigVO {
  appId: string
  accessKey: string
  signAlgorithm: SignAlgorithm
  syncMode: SyncMode
  // 同步方式为 NOTIFY 时才有意义；PULL 模式下也可能保留上一次填写的值（后端不强制清空）
  notifyUrl: string
  notifyParams: Record<string, string>
  // 是否需要签名/验签校验：出站"通知"请求携带签名参数、入站"拉取"请求做验签校验
  needSign: boolean
  createBy: string
  createTime: string
  updateBy: string
  updateTime: string
}

// 修改签名算法请求体，对应 PUT /api/apps/{id}/config/sign-algorithm
export interface SignAlgorithmUpdateRequest {
  signAlgorithm: SignAlgorithm
}

// 修改同步配置请求体，对应 PUT /api/apps/{id}/config/sync；notifyUrl 是否必填取决于 syncMode
// （NOTIFY 时必填、格式须为 http/https），前端表单需要按 syncMode 联动做提交前校验
export interface SyncConfigUpdateRequest {
  syncMode: SyncMode
  notifyUrl: string
  notifyParams: Record<string, string>
  needSign: boolean
}

// 重置 SecretKey 响应，对应 POST /api/apps/{id}/config/secret-key/reset；
// 仅此一个接口会返回明文，调用方不应把它存进 Pinia store 或 localStorage
export interface SecretKeyResult {
  secretKey: string
}

// ---- 同步配置·数据范围：数据域（组织/用户/任职/应用/角色/字典），与后端
// app/sync/constant/SyncDomain 对齐 ----
export type SyncDomain = 'ORG' | 'USER' | 'POSITION' | 'APP' | 'ROLE' | 'DICT'

export const SYNC_DOMAIN_OPTIONS: Array<{ value: SyncDomain; label: string }> = [
  { value: 'ORG', label: '组织' },
  { value: 'USER', label: '用户' },
  { value: 'POSITION', label: '任职' },
  { value: 'APP', label: '应用' },
  { value: 'ROLE', label: '角色' },
  { value: 'DICT', label: '字典' },
]

// 支持字段级同步映射配置的数据域：组织/用户/任职/应用/角色五个，不含字典
// （字典仍只是布尔开关+分页大小，见 design.md Decision 10）
export const SYNC_DOMAIN_FIELD_MAPPING_DOMAINS: SyncDomain[] = ['ORG', 'USER', 'POSITION', 'APP', 'ROLE']

// 转换方式：不转换 / 固定值 / 转换脚本，与后端 app/sync/constant/TransformType 对齐
export type TransformType = 'NO_TRANSFORM' | 'FIXED_VALUE' | 'SCRIPT'

export const TRANSFORM_TYPE_OPTIONS: Array<{ value: TransformType; label: string }> = [
  { value: 'NO_TRANSFORM', label: '不转换' },
  { value: 'FIXED_VALUE', label: '固定值' },
  { value: 'SCRIPT', label: '转换脚本' },
]

// 单个数据域的同步配置行，来自 GET /api/apps/{id}/config/sync/domains、
// PUT /api/apps/{id}/config/sync/domains/{syncDomain} 的返回值
export interface AppSyncDomainConfigVO {
  syncDomain: SyncDomain
  syncEnabled: boolean
  pageSize: number
}

// 修改单个数据域同步配置的请求体，对应 PUT /api/apps/{id}/config/sync/domains/{syncDomain}
export interface AppSyncDomainConfigUpdateRequest {
  syncEnabled: boolean
  pageSize: number
}

// 字段级同步映射配置行，来自 GET/PUT /api/apps/{id}/config/sync/field-mappings；
// fieldName/fieldCode 是源字段（元数据字段目录）的实时信息，只读展示，不随请求提交
export interface AppSyncFieldMappingVO {
  id: number
  metadataFieldId: number
  fieldName: string
  fieldCode: string
  appFieldName: string
  appFieldCode: string
  transformType: TransformType
  transformValue: string | null
}

// 整体替换字段映射列表时单行的请求体，对应 PUT /api/apps/{id}/config/sync/field-mappings
// 的请求体元素；transformValue 是否必填取决于 transformType（FIXED_VALUE/SCRIPT 必填）
export interface AppSyncFieldMappingSaveRequest {
  metadataFieldId: number
  appFieldName: string
  appFieldCode: string
  transformType: TransformType
  transformValue: string | null
}
