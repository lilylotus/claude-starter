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
// 的返回值；出于安全策略，永远不包含 secretKey 字段（明文只在重置接口的响应里出现一次）
export interface AppConfigVO {
  appId: string
  accessKey: string
  signAlgorithm: SignAlgorithm
  syncOrgEnabled: boolean
  syncUserEnabled: boolean
  syncAppEnabled: boolean
  syncDictEnabled: boolean
  syncMode: SyncMode
  // 同步方式为 NOTIFY 时才有意义；PULL 模式下也可能保留上一次填写的值（后端不强制清空）
  notifyUrl: string
  notifyParams: Record<string, string>
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
  syncOrgEnabled: boolean
  syncUserEnabled: boolean
  syncAppEnabled: boolean
  syncDictEnabled: boolean
  syncMode: SyncMode
  notifyUrl: string
  notifyParams: Record<string, string>
}

// 重置 SecretKey 响应，对应 POST /api/apps/{id}/config/secret-key/reset；
// 仅此一个接口会返回明文，调用方不应把它存进 Pinia store 或 localStorage
export interface SecretKeyResult {
  secretKey: string
}
