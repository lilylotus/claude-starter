import request from './request'
import type {
  AppConfigVO,
  AppFormRequest,
  AppRow,
  PageResult,
  SecretKeyResult,
  SignAlgorithmUpdateRequest,
  SyncConfigUpdateRequest,
} from '@/types/app'

// 应用管理接口封装，组件/store 不直接调用 axios。

// 分页查询应用，不支持筛选参数；page/pageSize 不传时后端分别默认 1、10
export function getAppPage(page?: number, pageSize?: number): Promise<PageResult<AppRow>> {
  return request.get('/apps', { params: { page, pageSize } })
}

// 获取应用详情，用于编辑表单回填与只读详情弹窗
export function getAppById(id: number): Promise<AppRow> {
  return request.get(`/apps/${id}`)
}

// 新增应用
export function createApp(data: AppFormRequest): Promise<AppRow> {
  return request.post('/apps', data)
}

// 编辑应用
export function updateApp(id: number, data: AppFormRequest): Promise<AppRow> {
  return request.put(`/apps/${id}`, data)
}

// 启用应用
export function enableApp(id: number): Promise<void> {
  return request.put(`/apps/${id}/enable`)
}

// 停用应用
export function disableApp(id: number): Promise<void> {
  return request.put(`/apps/${id}/disable`)
}

// 删除应用（逻辑删除）
export function deleteApp(id: number): Promise<void> {
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

// 修改同步配置（组织/用户/应用/字典四个开关）
export function updateAppSyncConfig(id: number, data: SyncConfigUpdateRequest): Promise<AppConfigVO> {
  return request.put(`/apps/${id}/config/sync`, data)
}

// 重置 SecretKey，响应体是本次改动里唯一会返回明文密钥的接口，调用方不应长期持有该值
export function resetAppSecretKey(id: number): Promise<SecretKeyResult> {
  return request.post(`/apps/${id}/config/secret-key/reset`)
}
