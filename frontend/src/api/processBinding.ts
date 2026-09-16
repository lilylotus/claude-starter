import request from './request'
import type { ProcessBindingRequest, ProcessBindingVO } from '@/types/processBinding'

// 业务绑定管理接口封装，组件不直接调用 axios。接口路径/请求响应结构以后端
// cn.nihility.rbac.workflow.dslv2.binding.WorkflowProcessBindingController 为准
// （approval-process-binding-console change design.md）。

// 查询业务绑定列表。对应权限点 WorkflowDesign:binding:view。
export function listBindings(): Promise<ProcessBindingVO[]> {
  return request.get('/workflow/process-bindings')
}

// 查询单条业务绑定详情。对应权限点 WorkflowDesign:binding:view。
export function getBinding(bindingId: number): Promise<ProcessBindingVO> {
  return request.get(`/workflow/process-bindings/${bindingId}`)
}

// 新建业务绑定。对应权限点 WorkflowDesign:binding:edit。
export function createBinding(payload: ProcessBindingRequest): Promise<ProcessBindingVO> {
  return request.post('/workflow/process-bindings', payload)
}

// 切换业务绑定指向的流程定义版本（含显式回滚），需携带当前 revision 作为
// expectedRevision 做乐观锁校验。对应权限点 WorkflowDesign:binding:edit。
export function switchBindingDefinition(
  bindingId: number,
  payload: ProcessBindingRequest,
): Promise<ProcessBindingVO> {
  return request.put(`/workflow/process-bindings/${bindingId}`, payload)
}

// 启用业务绑定。对应权限点 WorkflowDesign:binding:edit。
export function enableBinding(bindingId: number): Promise<void> {
  return request.post(`/workflow/process-bindings/${bindingId}/enable`)
}

// 禁用业务绑定。对应权限点 WorkflowDesign:binding:edit。
export function disableBinding(bindingId: number): Promise<void> {
  return request.post(`/workflow/process-bindings/${bindingId}/disable`)
}

// 删除业务绑定（软删除）。对应权限点 WorkflowDesign:binding:delete。
export function deleteBinding(bindingId: number): Promise<void> {
  return request.delete(`/workflow/process-bindings/${bindingId}`)
}
