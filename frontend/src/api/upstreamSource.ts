import request from './request'
import type {
  UpstreamConnectionConfigRequest,
  UpstreamDataType,
  UpstreamDomainConfigUpdateRequest,
  UpstreamDomainConfigVO,
  UpstreamFieldMappingSaveRequest,
  UpstreamFieldMappingVO,
  UpstreamScheduleConfigRequest,
  UpstreamSourceCreateRequest,
  UpstreamSourceUpdateRequest,
  UpstreamSourceVO,
  UpstreamSyncRecordVO,
} from '@/types/upstreamSource'

// 上游数据管理接口封装，组件不直接调用 axios。对应后端
// cn.nihility.rbac.identity.upstream.controller.UpstreamSourceController 全部路由。

// 查询全部上游数据源，不分页
export function listUpstreamSources(): Promise<UpstreamSourceVO[]> {
  return request.get('/identity/upstream-sources')
}

// 查询上游数据源详情
export function getUpstreamSource(id: number): Promise<UpstreamSourceVO> {
  return request.get(`/identity/upstream-sources/${id}`)
}

// 创建上游数据源，enabled 由后端固定为 false
export function createUpstreamSource(data: UpstreamSourceCreateRequest): Promise<UpstreamSourceVO> {
  return request.post('/identity/upstream-sources', data)
}

// 更新上游数据源基础信息（名称、同步方式）
export function updateUpstreamSourceBasicInfo(
  id: number,
  data: UpstreamSourceUpdateRequest,
): Promise<UpstreamSourceVO> {
  return request.put(`/identity/upstream-sources/${id}`, data)
}

// 启用上游数据源
export function enableUpstreamSource(id: number): Promise<UpstreamSourceVO> {
  return request.put(`/identity/upstream-sources/${id}/enable`)
}

// 停用上游数据源
export function disableUpstreamSource(id: number): Promise<UpstreamSourceVO> {
  return request.put(`/identity/upstream-sources/${id}/disable`)
}

// 删除上游数据源，级联删除其下的数据域配置、字段映射配置与同步执行记录
export function deleteUpstreamSource(id: number): Promise<void> {
  return request.delete(`/identity/upstream-sources/${id}`)
}

// 更新连接配置：syncType=API 时更新自定义请求头（整体替换），syncType=DB_TABLE 时更新
// JDBC 连接信息（密码留空表示不修改）
export function updateUpstreamConnectionConfig(
  id: number,
  data: UpstreamConnectionConfigRequest,
): Promise<UpstreamSourceVO> {
  return request.put(`/identity/upstream-sources/${id}/connection`, data)
}

// 更新调度配置：按间隔或按每日固定时间点二选一
export function updateUpstreamScheduleConfig(
  id: number,
  data: UpstreamScheduleConfigRequest,
): Promise<UpstreamSourceVO> {
  return request.put(`/identity/upstream-sources/${id}/schedule`, data)
}

// 立即同步一次，效果与定时到期自动触发一致，但不影响下次定时触发的判定基准
export function manualSyncUpstreamSource(id: number): Promise<void> {
  return request.post(`/identity/upstream-sources/${id}/sync`)
}

// 查询数据源的 3 个数据域配置（组织/用户/任职）
export function listUpstreamDomainConfigs(id: number): Promise<UpstreamDomainConfigVO[]> {
  return request.get(`/identity/upstream-sources/${id}/domains`)
}

// 修改指定数据域的启用开关与取数来源配置
export function updateUpstreamDomainConfig(
  id: number,
  dataType: UpstreamDataType,
  data: UpstreamDomainConfigUpdateRequest,
): Promise<UpstreamDomainConfigVO> {
  return request.put(`/identity/upstream-sources/${id}/domains/${dataType}`, data)
}

// 查询指定数据域的字段映射列表
export function listUpstreamFieldMappings(
  id: number,
  dataType: UpstreamDataType,
): Promise<UpstreamFieldMappingVO[]> {
  return request.get(`/identity/upstream-sources/${id}/domains/${dataType}/field-mappings`)
}

// 整体替换指定数据域的字段映射列表（先删后插语义），提交完整的当前列表
export function replaceUpstreamFieldMappings(
  id: number,
  dataType: UpstreamDataType,
  data: UpstreamFieldMappingSaveRequest[],
): Promise<UpstreamFieldMappingVO[]> {
  return request.put(`/identity/upstream-sources/${id}/domains/${dataType}/field-mappings`, data)
}

// 查询数据源的同步执行记录列表，按时间倒序返回
export function listUpstreamSyncRecords(id: number): Promise<UpstreamSyncRecordVO[]> {
  return request.get(`/identity/upstream-sources/${id}/sync-records`)
}
