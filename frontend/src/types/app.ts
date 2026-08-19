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
  // 同步总开关：整个应用一份，默认开启。关闭后该应用不再产生新的数据变更记录、不再收到
  // 通知、拉取接口（按 id / 按序列号）一律返回空结果；已产生的历史变更记录不会被清空，
  // 重新打开后可继续访问，不做补偿/追溯发送
  syncMasterEnabled: boolean
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
  // 同步总开关：必填，语义同 AppConfigVO.syncMasterEnabled
  syncMasterEnabled: boolean
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

// 支持"同步范围"（全部数据/指定组织范围）配置的数据域：组织/用户/任职三个，
// 应用/角色/字典没有"归属组织"的概念，不提供该配置（见 design.md Decision 2/4）
export const SYNC_DOMAIN_ORG_SCOPE_DOMAINS: SyncDomain[] = ['ORG', 'USER', 'POSITION']

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

// ---- 同步配置·同步范围：组织/用户/任职三个数据域按应用配置的组织范围（全部数据/指定
// 组织范围），与后台管理员"管辖组织范围"物理隔离的独立一套配置，形状对齐
// types/admin.ts 里的 AdminOrgScopeRow/AdminOrgScopeFormItem ----

// 同步范围行，来自 GET /api/apps/{id}/config/sync/domains/{syncDomain}/org-scope、
// PUT 同一地址的返回值；空数组表示"全部数据"
export interface AppSyncOrgScopeRow {
  orgId: number
  orgName: string
  includeChildren: boolean
}

// 整体替换同步范围时子表单一行的本地可编辑结构，orgId 允许暂时为空（由用户手动选择）
export interface AppSyncOrgScopeFormItem {
  orgId: number | null
  includeChildren: boolean
}

// 整体替换同步范围列表的请求体元素，对应
// PUT /api/apps/{id}/config/sync/domains/{syncDomain}/org-scope 的请求体
export interface AppSyncOrgScopeSaveRequest {
  orgId: number
  includeChildren: boolean
}

// ---- 认证管理：单点登录协议配置（CAS/OAuth2.0），与后端
// app/authconfig/constant/AuthProtocol 对齐；只做协议接入前置配置维护，不含协议运行时
// 鉴权逻辑（见 openspec/changes/app-auth-protocol-config） ----

export type AuthProtocol = 'NONE' | 'CAS' | 'OAUTH2'

export const AUTH_PROTOCOL_OPTIONS: Array<{ value: AuthProtocol; label: string }> = [
  { value: 'NONE', label: '无' },
  { value: 'CAS', label: 'CAS' },
  { value: 'OAUTH2', label: 'OAuth2.0' },
]

// 应用认证配置，来自 GET /api/apps/{id}/config/auth、PUT 同一地址的返回值；6 个协议接口
// 地址由后端按该应用的 AppId 实时计算返回（路径部分，不含域名），无论 authProtocol 取值
// 为何都会返回，方便管理员提前查看/复制
export interface AppAuthConfigVO {
  authProtocol: AuthProtocol
  // 回跳地址 ANT 匹配规则列表，CAS/OAuth2.0 及未来新增协议共用同一份存储，语义为
  // "当前生效协议下允许的回跳地址匹配列表"（不是多协议的并集）
  servicePatterns: string[]
  // 登出通知回调地址：单点登出发生时，后端以 POST 表单方式回调该地址通知本应用登出
  // （CAS 协议回传 ticket，OAuth2.0 协议回传 access_token），可为空，非空时须为合法
  // http/https URL；随协议类型/匹配列表一并读写，不单独提供保存接口
  logoutNotifyUrl: string
  casLoginUrl: string
  casServiceValidateUrl: string
  casLogoutUrl: string
  oauthAuthorizeUrl: string
  oauthTokenUrl: string
  oauthUserInfoUrl: string
}

// 修改认证配置请求体，对应 PUT /api/apps/{id}/config/auth；协议类型为 CAS/OAuth2.0 时
// servicePatterns 不能为空，协议类型为无时会被后端清空（不保留旧值）；
// logoutNotifyUrl 可留空，非空时须为合法 http/https URL
export interface AppAuthConfigUpdateRequest {
  authProtocol: AuthProtocol
  servicePatterns: string[]
  logoutNotifyUrl: string
}

// ---- 认证管理·用户信息响应字段映射：每个应用一份，CAS 票据验证/OAuth2 userinfo 接口共用，
// 与后端 app/authconfig 模块对齐（见 openspec/changes/add-sso-userinfo-field-mapping）。
// metadataFieldId 允许为 null，表示固定的"用户ID"伪字段（不在元数据字段目录里，
// 因为 id 是主键，见 design.md Decision 2），此时 fieldName/fieldCode 由后端回填为
// 固定字面量 "用户ID"/"id" ----

// 用户信息字段映射行，来自 GET /api/apps/{id}/config/auth/userinfo-field-mappings、
// PUT 同一地址的返回值；未保存过时后端现算返回默认两行（用户ID、姓名），此时每行 id 为 null
export interface AppUserinfoFieldMappingVO {
  id: number | null
  metadataFieldId: number | null
  fieldName: string
  fieldCode: string
  appFieldName: string
  appFieldCode: string
  transformType: TransformType
  transformValue: string | null
}

// 整体替换用户信息字段映射列表时单行的请求体，对应
// PUT /api/apps/{id}/config/auth/userinfo-field-mappings 的请求体元素；metadataFieldId
// 为 null 表示"用户ID"伪字段，transformValue 是否必填取决于 transformType（FIXED_VALUE/
// SCRIPT 必填）
export interface AppUserinfoFieldMappingSaveRequest {
  metadataFieldId: number | null
  appFieldName: string
  appFieldCode: string
  transformType: TransformType
  transformValue: string | null
}

// ---- 同步配置·通知/拉取日志：应用配置页"通知日志"“拉取日志”两个标签页，与后端
// cn.nihility.rbac.app.sync.controller.AppSyncLogController 对齐（见
// openspec/changes/add-app-sync-notify-pull-logs）。均为管理端只读分页查询，不提供
// 新增/编辑/删除接口 ----

// 通知状态：1=成功，2=失败，与后端 sync/notify/constant/NotifyStatus 对齐
export const NOTIFY_STATUS_SUCCESS = 1
export const NOTIFY_STATUS_FAILURE = 2

export const NOTIFY_STATUS_OPTIONS: Array<{ value: number; label: string }> = [
  { value: NOTIFY_STATUS_SUCCESS, label: '成功' },
  { value: NOTIFY_STATUS_FAILURE, label: '失败' },
]

// 通知日志查询参数，对应 GET /api/apps/{id}/config/sync/notify-records 的 query 参数，
// 全部可选；startTime/endTime 为 ISO-8601 时间，省略时不做时间范围限制
export interface AppNotifyRecordQueryParams {
  notifyStatus?: number
  startTime?: string
  endTime?: string
  page?: number
  pageSize?: number
}

// 通知日志列表行，来自 GET /api/apps/{id}/config/sync/notify-records 分页接口；
// dataType/bizId/notifyUrl 三列历史记录可能为空（旧数据未落这三列，见 proposal.md），
// httpStatus 未收到响应时为空，errorMsg 仅失败时有值
export interface AppNotifyRecordRow {
  dataType: string | null
  bizId: number | null
  notifyStatus: number
  httpStatus: number | null
  notifyUrl: string | null
  errorMsg: string | null
  createTime: string
}

// 拉取日志查询参数，对应 GET /api/apps/{id}/config/sync/pull-records 的 query 参数，
// 全部可选；startTime/endTime 为 ISO-8601 时间，省略时不做时间范围限制
export interface AppPullRecordQueryParams {
  startTime?: string
  endTime?: string
  page?: number
  pageSize?: number
}

// 拉取日志列表行，来自 GET /api/apps/{id}/config/sync/pull-records 分页接口；
// dataType 未传数据类型时为空，requestSummary 为可读的请求参数摘要文本
export interface AppPullRecordRow {
  dataType: string | null
  requestSummary: string
  resultCount: number
  createTime: string
}
