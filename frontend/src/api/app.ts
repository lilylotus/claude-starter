import request from './request'
import type {
  AppAuthConfigUpdateRequest,
  AppAuthConfigVO,
  AppConfigVO,
  AppFormRequest,
  AppNotifyRecordQueryParams,
  AppNotifyRecordRow,
  AppPullRecordQueryParams,
  AppPullRecordRow,
  AppRow,
  AppSyncDomainConfigUpdateRequest,
  AppSyncDomainConfigVO,
  AppSyncFieldMappingSaveRequest,
  AppSyncFieldMappingVO,
  AppSyncOrgScopeRow,
  AppSyncOrgScopeSaveRequest,
  AppUserinfoFieldMappingSaveRequest,
  AppUserinfoFieldMappingVO,
  PageResult,
  SecretKeyResult,
  SignAlgorithmUpdateRequest,
  SyncConfigUpdateRequest,
  SyncDomain,
} from '@/types/app'
import type { WriteOperationResult } from '@/types/approval'

// 应用管理接口封装，组件/store 不直接调用 axios。

// 分页查询应用，不支持筛选参数；page/pageSize 不传时后端分别默认 1、10
export function getAppPage(page?: number, pageSize?: number): Promise<PageResult<AppRow>> {
  return request.get('/apps', { params: { page, pageSize } })
}

// 获取应用详情，用于编辑表单回填与只读详情弹窗
export function getAppById(id: number): Promise<AppRow> {
  return request.get(`/apps/${id}`)
}

// 新增应用；应用的审批开关（bizType=APP）开启时，响应 approvalEnabled=true、
// approvalRequest 非空、data 为空，不创建真实应用记录，需等待审批通过后才生效
// （add-master-data-approval-workflow change design.md Decision 9）；开关关闭时行为
// 与本 change 之前一致，approvalEnabled=false、data 为创建后的应用数据
export function createApp(data: AppFormRequest): Promise<WriteOperationResult<AppRow>> {
  return request.post('/apps', data)
}

// 编辑应用，响应结构同上
export function updateApp(id: number, data: AppFormRequest): Promise<WriteOperationResult<AppRow>> {
  return request.put(`/apps/${id}`, data)
}

// 启用应用，响应结构同上
export function enableApp(id: number): Promise<WriteOperationResult<AppRow>> {
  return request.put(`/apps/${id}/enable`)
}

// 停用应用，响应结构同上
export function disableApp(id: number): Promise<WriteOperationResult<AppRow>> {
  return request.put(`/apps/${id}/disable`)
}

// 删除应用（逻辑删除），响应结构同上
export function deleteApp(id: number): Promise<WriteOperationResult<AppRow>> {
  return request.delete(`/apps/${id}`)
}

// 获取应用的对外接口配置（AppId/AccessKey/签名算法/同步开关），不含 SecretKey 明文
export function getAppConfig(id: number): Promise<AppConfigVO> {
  return request.get(`/apps/${id}/config`)
}

// 修改签名算法
export function updateAppSignAlgorithm(id: number, data: SignAlgorithmUpdateRequest): Promise<AppConfigVO> {
  return request.put(`/apps/${id}/config/sign-algorithm`, data)
}

// 修改同步配置（同步方式、通知回调地址/参数、是否需要签名/验签校验；组织/用户/任职/应用/
// 角色/字典的数据范围配置已迁移到下方 sync/domains、sync/field-mappings 系列接口，不再随
// 这个接口一起提交）
export function updateAppSyncConfig(id: number, data: SyncConfigUpdateRequest): Promise<AppConfigVO> {
  return request.put(`/apps/${id}/config/sync`, data)
}

// 重置 SecretKey，响应体是本次改动里唯一会返回明文密钥的接口，调用方不应长期持有该值
export function resetAppSecretKey(id: number): Promise<SecretKeyResult> {
  return request.post(`/apps/${id}/config/secret-key/reset`)
}

// ---- 同步配置·数据范围：数据域（组织/用户/任职/应用/角色/字典）启用开关+分页大小 ----

// 一次性查询该应用的 6 个数据域配置行
export function listAppSyncDomainConfigs(id: number): Promise<AppSyncDomainConfigVO[]> {
  return request.get(`/apps/${id}/config/sync/domains`)
}

// 修改单个数据域的启用开关/拉取分页大小
export function updateAppSyncDomainConfig(
  id: number,
  syncDomain: SyncDomain,
  data: AppSyncDomainConfigUpdateRequest,
): Promise<AppSyncDomainConfigVO> {
  return request.put(`/apps/${id}/config/sync/domains/${syncDomain}`, data)
}

// ---- 同步配置·字段映射：组织/用户/任职/应用/角色五个数据域的字段级同步映射 ----

// 查询某个数据域当前的字段映射列表（不含字典，字典不支持字段级配置）
export function listAppSyncFieldMappings(id: number, domain: SyncDomain): Promise<AppSyncFieldMappingVO[]> {
  return request.get(`/apps/${id}/config/sync/field-mappings`, { params: { domain } })
}

// 整体替换某个数据域的字段映射列表（先删后插语义，见 design.md Decision 5），
// 提交完整的当前列表，而不是增量的新增/删除
export function replaceAppSyncFieldMappings(
  id: number,
  domain: SyncDomain,
  data: AppSyncFieldMappingSaveRequest[],
): Promise<AppSyncFieldMappingVO[]> {
  return request.put(`/apps/${id}/config/sync/field-mappings`, data, { params: { domain } })
}

// ---- 同步配置·同步范围：组织/用户/任职三个数据域按应用配置的组织范围（全部数据/指定组织范围）----

// 查询某个数据域当前配置的同步范围（组织列表），空数组表示"全部数据"
export function listAppSyncOrgScope(id: number, domain: SyncDomain): Promise<AppSyncOrgScopeRow[]> {
  return request.get(`/apps/${id}/config/sync/domains/${domain}/org-scope`)
}

// 整体替换某个数据域的同步范围列表（先删后插语义），提交空数组即改为"全部数据"
export function replaceAppSyncOrgScope(
  id: number,
  domain: SyncDomain,
  data: AppSyncOrgScopeSaveRequest[],
): Promise<AppSyncOrgScopeRow[]> {
  return request.put(`/apps/${id}/config/sync/domains/${domain}/org-scope`, data)
}

// ---- 认证管理：单点登录协议配置（CAS/OAuth2.0） ----

// 查询应用当前的认证配置（协议类型、匹配列表、只读协议接口地址）
export function getAppAuthConfig(id: number): Promise<AppAuthConfigVO> {
  return request.get(`/apps/${id}/config/auth`)
}

// 修改应用的认证配置（协议类型、对应的匹配列表）
export function updateAppAuthConfig(id: number, data: AppAuthConfigUpdateRequest): Promise<AppAuthConfigVO> {
  return request.put(`/apps/${id}/config/auth`, data)
}

// ---- 认证管理·用户信息响应字段映射：CAS 票据验证/OAuth2 userinfo 接口共用，每个应用一份 ----

// 查询用户信息字段映射列表；未保存过时后端现算返回默认两行（用户ID、姓名）
export function getAppUserinfoFieldMappings(id: number): Promise<AppUserinfoFieldMappingVO[]> {
  return request.get(`/apps/${id}/config/auth/userinfo-field-mappings`)
}

// 整体替换用户信息字段映射列表（先删后插语义），提交完整的当前列表
export function replaceAppUserinfoFieldMappings(
  id: number,
  data: AppUserinfoFieldMappingSaveRequest[],
): Promise<AppUserinfoFieldMappingVO[]> {
  return request.put(`/apps/${id}/config/auth/userinfo-field-mappings`, data)
}

// ---- 同步配置·通知/拉取日志：只读分页查询，无新增/编辑/删除接口 ----

// 分页查询该应用的通知日志，支持按通知状态、时间范围筛选，全部筛选参数可选；
// page/pageSize 不传时后端分别默认 1、10
export function getAppNotifyRecordPage(
  id: number,
  params: AppNotifyRecordQueryParams,
): Promise<PageResult<AppNotifyRecordRow>> {
  return request.get(`/apps/${id}/config/sync/notify-records`, { params })
}

// 分页查询该应用的拉取日志，支持按时间范围筛选，全部筛选参数可选；
// page/pageSize 不传时后端分别默认 1、10
export function getAppPullRecordPage(
  id: number,
  params: AppPullRecordQueryParams,
): Promise<PageResult<AppPullRecordRow>> {
  return request.get(`/apps/${id}/config/sync/pull-records`, { params })
}
