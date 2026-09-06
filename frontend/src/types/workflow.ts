// 流程设计器相关类型定义：Workflow JSON DSL 节点/边结构与后端
// cn.nihility.rbac.workflow.designer.dto.ProcessModelDsl 及其节点子类型逐字段对齐
// （workflow-approval-engine change design.md Decision 9 "Workflow JSON DSL Schema"）。
// 节点按 `type` 判别字段做多态区分，取值固定为 START/APPROVAL/CONDITION/END，
// 与后端 ProcessNodeDsl 上 @JsonTypeInfo(property = "type") 的多态反序列化配置一致。

import {
  FORM_FIELD_CONTROL_TYPE_DATE,
  FORM_FIELD_CONTROL_TYPE_NUMBER,
  type FormFieldBizType,
  type FormFieldDictOption,
} from './formField'

// ---- 审批人来源类型：对应后端 cn.nihility.rbac.workflow.constant.AssigneeType 枚举 ----
export type AssigneeType =
  | 'USER'
  | 'ROLE'
  | 'POSITION'
  | 'ORG_LEADER'
  | 'APPLICANT_DEPT_LEADER'
  | 'APPLICANT_DEPT_PARENT_LEADER'
  | 'INITIATOR'
  | 'PREVIOUS_APPROVER'

export const ASSIGNEE_TYPE_OPTIONS: Array<{ value: AssigneeType; label: string }> = [
  { value: 'USER', label: '指定人员' },
  { value: 'ROLE', label: '指定角色' },
  { value: 'POSITION', label: '指定岗位' },
  { value: 'ORG_LEADER', label: '指定组织负责人' },
  { value: 'APPLICANT_DEPT_LEADER', label: '发起人部门负责人' },
  { value: 'APPLICANT_DEPT_PARENT_LEADER', label: '发起人部门上级负责人' },
  { value: 'INITIATOR', label: '流程发起人' },
  { value: 'PREVIOUS_APPROVER', label: '上一节点审批人' },
]

// ---- 审批模式：对应后端 cn.nihility.rbac.workflow.constant.ApprovalMode 枚举 ----
export type ApprovalMode = 'SINGLE' | 'AND' | 'OR' | 'PERCENT'

export const APPROVAL_MODE_OPTIONS: Array<{ value: ApprovalMode; label: string }> = [
  { value: 'SINGLE', label: '单人审批' },
  { value: 'AND', label: '会签（全部通过）' },
  { value: 'OR', label: '或签（任一通过）' },
  { value: 'PERCENT', label: '比例通过' },
]

// ---- 空审批人策略：对应后端 cn.nihility.rbac.workflow.constant.EmptyAssigneeStrategy 枚举 ----
export type EmptyAssigneeStrategy = 'TO_WORKFLOW_ADMIN' | 'AUTO_SKIP' | 'REJECT'

export const EMPTY_ASSIGNEE_STRATEGY_OPTIONS: Array<{ value: EmptyAssigneeStrategy; label: string }> = [
  { value: 'TO_WORKFLOW_ADMIN', label: '转流程管理员' },
  { value: 'AUTO_SKIP', label: '自动跳过' },
  { value: 'REJECT', label: '终止流程' },
]

// ---- 条件分支比较符：白名单固定为 EQ/NE/GT/GTE/LT/LTE，不允许自由表达式
//      （对应后端 ProcessModelDslValidator.ALLOWED_OPERATORS） ----
export type ConditionOperator = 'EQ' | 'NE' | 'GT' | 'GTE' | 'LT' | 'LTE'

export const CONDITION_OPERATOR_OPTIONS: Array<{ value: ConditionOperator; label: string }> = [
  { value: 'EQ', label: '等于' },
  { value: 'NE', label: '不等于' },
  { value: 'GT', label: '大于' },
  { value: 'GTE', label: '大于等于' },
  { value: 'LT', label: '小于' },
  { value: 'LTE', label: '小于等于' },
]

// ---- 条件字段可选列表：合并组织/用户/任职/应用四类业务对象的表单字段渲染元数据
//      （GET /api/form-fields/render-schema），供设计器条件分支"字段"下拉使用
//      （workflow-condition-payload-fields change design.md Decision 6）。已过滤掉
//      controlType=5（多选字典）的字段——无法做单值比较，不允许选为条件字段。 ----
export interface ConditionFieldOption {
  bizType: FormFieldBizType
  fieldCode: string
  fieldName: string
  controlType: number
  dictOptions: FormFieldDictOption[]
}

// 条件字段下拉按业务类型分组展示的分组标题
export const CONDITION_FIELD_BIZ_TYPE_LABEL: Record<FormFieldBizType, string> = {
  ORG: '组织',
  USER: '用户',
  POSITION: '任职',
  APP: '应用',
}

// 按选中字段的 controlType 返回当前允许的比较符选项：数字框(2)/日期(4) 支持全部六个，
// 文本框(1)/字典下拉(3)（以及未选中字段时的兜底）仅允许等于/不等于
// （design.md Decision 4，与后端 ProcessModelDslValidator 的收窄规则保持一致）。
export function getConditionOperatorOptions(
  controlType: number | null | undefined,
): Array<{ value: ConditionOperator; label: string }> {
  if (controlType === FORM_FIELD_CONTROL_TYPE_NUMBER || controlType === FORM_FIELD_CONTROL_TYPE_DATE) {
    return CONDITION_OPERATOR_OPTIONS
  }
  return CONDITION_OPERATOR_OPTIONS.filter((opt) => opt.value === 'EQ' || opt.value === 'NE')
}

// ---- DSL 节点定义（discriminated union，判别字段 type） ----

export interface StartNodeDsl {
  id: string
  type: 'START'
  name?: string | null
}

export interface EndNodeDsl {
  id: string
  type: 'END'
  name?: string | null
}

export interface ConditionNodeDsl {
  id: string
  type: 'CONDITION'
  name?: string | null
}

// 审批节点字段与 tab_wf_node_assignee_rule 逐字段对应，见后端 ApprovalNodeDsl。
export interface ApprovalNodeDsl {
  id: string
  type: 'APPROVAL'
  name?: string | null
  assigneeType: AssigneeType | null
  assigneeValue: string | null
  approvalMode: ApprovalMode | null
  approvalPercent: number | null
  emptyAssigneeStrategy: EmptyAssigneeStrategy | null
  allowSelfApproval: boolean
  allowTransfer: boolean
  allowDelegate: boolean
  allowAddSign: boolean
  allowReturn: boolean
}

export type ProcessNodeDsl = StartNodeDsl | ApprovalNodeDsl | ConditionNodeDsl | EndNodeDsl

// 条件节点出边携带的分支条件，对应后端 EdgeConditionDsl。field 从自由文本改为必须从
// fieldBizType 对应业务类型的表单字段定义中选择的 fieldCode（workflow-condition-payload-fields
// change design.md Decision 2）；value 的具体类型随选中字段的 controlType 而定——文本框/
// 字典下拉为 string，数字框为 number，日期为 "YYYY-MM-DD" 格式的 ISO 日期字符串。
export interface EdgeConditionDsl {
  fieldBizType: string
  field: string
  operator: ConditionOperator
  value: unknown
}

// 连线，对应后端 EdgeDsl；condition 为空表示无条件流转（条件节点的兜底默认分支）。
export interface EdgeDsl {
  from: string
  to: string
  condition?: EdgeConditionDsl | null
}

// Workflow JSON DSL 顶层结构，对应后端 ProcessModelDsl，是
// tab_wf_process_model.model_json / tab_wf_process_definition.model_json_snapshot 的
// 反序列化形态。
export interface ProcessModelDsl {
  processCode: string
  processName: string
  nodes: ProcessNodeDsl[]
  edges: EdgeDsl[]
}

// ---- 流程模型生命周期相关类型 ----

export type ProcessModelStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED'

export const PROCESS_MODEL_STATUS_LABEL: Record<ProcessModelStatus, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  DISABLED: '已下线',
}

// 流程模型主数据行，字段对应后端 cn.nihility.rbac.workflow.entity.ProcessModelEntity；
// 供列表展示与"进入设计器编辑"时解析 modelJson 使用。
export interface ProcessModelRow {
  id: number
  processCode: string
  processName: string
  modelJson: string
  status: ProcessModelStatus
  currentDefinitionId: number | null
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

// 发布结果，字段对应后端 PublishResultVO。
export interface PublishResultVO {
  processDefinitionId: number
  version: number
  flowableDefinitionKey: string
  flowableDefinitionId: string
  publishedTime: string
}

// 版本历史一行，字段对应后端 ProcessDefinitionVersionVO；modelJsonSnapshot 为该版本发布
// 时刻的 DSL 快照文本，只读展示，不提供编辑入口。
export interface ProcessDefinitionVersionVO {
  id: number
  version: number
  status: 'PUBLISHED' | 'DISABLED'
  publishedBy: string | null
  publishedTime: string
  modelJsonSnapshot: string
}
