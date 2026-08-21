import request from './request'
import type { PageResult, SsoProtocolLogQueryParams, SsoProtocolLogRow } from '@/types/ssoProtocolLog'

// SSO 协议调用日志接口封装，组件不直接调用 axios。只读，没有新增/编辑/删除/详情接口。

// 分页查询 SSO 协议调用日志，登录日志页面通过 sessionId 精确查询某次成功的 SSO 登录
// 之后关联的全部 CAS/OAuth2 协议调用；其余筛选参数可选，page/pageSize 不传时后端
// 分别默认 1、10，排序按调用时间倒序（后端默认行为）
export function getSsoProtocolLogPage(params: SsoProtocolLogQueryParams): Promise<PageResult<SsoProtocolLogRow>> {
  return request.get('/sso-protocol-logs', { params })
}
