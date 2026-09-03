import request from './request'
import type { ProcessDefinitionVersionVO, ProcessModelRow, PublishResultVO } from '@/types/workflow'

// 流程设计器相关接口封装，组件/store 不直接调用 axios。
// 五个生命周期接口的路径/请求响应结构均以后端
// cn.nihility.rbac.workflow.designer.controller.WorkflowProcessModelController 为准
// （workflow-approval-engine change design.md Decision 11）。

// 保存流程模型草稿：仅更新 tab_wf_process_model.model_json，不触发部署，不影响当前
// 已发布/正在运行的版本。对应权限点 WorkflowDesign:model:edit。
export function saveProcessModelDraft(id: number, modelJson: string): Promise<void> {
  return request.put(`/workflow/process-models/${id}/draft`, { modelJson })
}

// 发布流程模型：编译当前草稿并部署为一个新的不可变版本。对应权限点
// WorkflowDesign:model:publish。
export function publishProcessModel(id: number): Promise<PublishResultVO> {
  return request.post(`/workflow/process-models/${id}/publish`)
}

// 下线流程模型当前生效版本：拒绝新发起，不影响运行中实例。对应权限点
// WorkflowDesign:model:disable。
export function disableProcessModel(id: number): Promise<void> {
  return request.post(`/workflow/process-models/${id}/disable`)
}

// 重新启用流程模型当前生效版本。对应权限点 WorkflowDesign:model:disable
// （与下线复用同一权限点，两者是同一按钮的启/停两态）。
export function enableProcessModel(id: number): Promise<void> {
  return request.post(`/workflow/process-models/${id}/enable`)
}

// 查询流程模型版本历史（按版本号倒序）。对应权限点 WorkflowDesign:model:view。
export function listProcessModelVersions(id: number): Promise<ProcessDefinitionVersionVO[]> {
  return request.get(`/workflow/process-models/${id}/versions`)
}

// 流程模型列表、详情和创建接口；列表与详情对应 WorkflowDesign:model:view，创建对应
// WorkflowDesign:model:edit。
export function listProcessModels(): Promise<ProcessModelRow[]> {
  return request.get('/workflow/process-models')
}

export function getProcessModel(id: number): Promise<ProcessModelRow> {
  return request.get(`/workflow/process-models/${id}`)
}

export function createProcessModel(processCode: string, processName: string): Promise<ProcessModelRow> {
  return request.post('/workflow/process-models', { processCode, processName })
}

export function copyProcessModel(id: number, processCode: string, processName: string): Promise<ProcessModelRow> {
  return request.post(`/workflow/process-models/${id}/copy`, { processCode, processName })
}
