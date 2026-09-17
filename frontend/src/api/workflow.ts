import request from './request'
import type {
  ApprovalTaskVO,
  DoneApprovalTaskQuery,
  ProcessDefinitionVersionVO,
  ProcessInstanceDetailVO,
  ProcessModelPageQuery,
  ProcessModelPageResult,
  ProcessModelRow,
  PublishResultVO,
} from '@/types/workflow'
import type { PageResult } from '@/types/approval'

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

// 管理列表按页查询；业务绑定选择器继续使用上面的全量列表接口。
export function pageProcessModels(params: ProcessModelPageQuery): Promise<ProcessModelPageResult> {
  return request.get('/workflow/process-models/page', { params })
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

// 流程实例详情：当前节点、完整只读节点/连线图、完整审批轨迹，供"我的申请"/"待我审批"
// 详情弹窗展示（add-approval-remark-and-process-flowchart change design.md Decision 6）。
// 注意接口挂在 /api/v1/... 前缀下（cn.nihility.rbac.workflow.controller.WorkflowTaskController），
// 与本文件其余流程模型设计器接口的 /api/workflow/... 前缀不同，axios baseURL 已含 /api，
// 这里补上 /v1 前缀。后端会校验当前用户是否为该实例的申请人/历史操作人/当前候选人，
// 无权限时返回非 0 code，由 request.ts 拦截器统一提示并 reject。
export function getProcessInstanceDetail(processInstanceId: number): Promise<ProcessInstanceDetailVO> {
  return request.get(`/v1/workflow/process-instances/${processInstanceId}`)
}

// "审批历史"页面：查询当前登录用户自己已经处理完成的审批任务分页列表，对应权限点
// ApprovalManagement:record:view（add-approval-history-menu change design.md Decision 4）。
// 与上面 getProcessInstanceDetail 一样挂在 /api/v1/workflow/... 前缀下。
export function getDoneApprovalTasks(query: DoneApprovalTaskQuery): Promise<PageResult<ApprovalTaskVO>> {
  return request.get('/v1/workflow/tasks/done', { params: query })
}
