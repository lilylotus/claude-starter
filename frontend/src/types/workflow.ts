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

// 流程模型分页查询参数，对应后端 page/pageSize。
export interface ProcessModelPageQuery {
  page: number
  pageSize: number
}

// 流程模型分页结果，对应后端 PageResult<ProcessModelVO>。
export interface ProcessModelPageResult {
  records: ProcessModelRow[]
  total: number
  page: number
  pageSize: number
}

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

// ---- 流程实例详情（add-approval-remark-and-process-flowchart change design.md Decision 4/6）：
//      "我的申请"/"待我审批"详情弹窗展示当前节点、完整流程拓扑与审批轨迹所需的类型，
//      对应后端 GET /api/v1/workflow/process-instances/{id} 返回的 ProcessInstanceDetailVO。 ----

export type ProcessInstanceStatus = 'RUNNING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN' | 'TERMINATED'

export const PROCESS_INSTANCE_STATUS_LABEL: Record<ProcessInstanceStatus, string> = {
  RUNNING: '进行中',
  APPROVED: '已通过',
  REJECTED: '已拒绝',
  WITHDRAWN: '已撤回',
  TERMINATED: '已终止',
}

// 流程实例当前开放节点：并行分叉场景下可能同时存在多个。
export interface OpenNodeVO {
  nodeId: string
  nodeName: string
}

// 审批轨迹动作类型文案，对应后端既有动作常量；未覆盖的取值原样展示 action 字面量兜底。
export const APPROVAL_RECORD_ACTION_LABEL: Record<string, string> = {
  SUBMIT: '提交',
  APPROVE: '批准',
  REJECT: '拒绝',
  DISAGREE: '不同意',
  TRANSFER: '转办',
  DELEGATE: '委派',
  RETURN: '退回',
  ADD_SIGN: '加签',
  WITHDRAW: '撤回',
  TERMINATE: '终止',
}

// 审批轨迹一条记录，字段对应后端 ApprovalRecordVO。
export interface ApprovalRecordVO {
  id: number
  processInstanceId: number
  taskId: number | null
  nodeId: string | null
  nodeName: string | null
  operatorId: number | null
  operatorName: string | null
  action: string
  remark: string | null
  fromUserId: number | null
  fromUserName: string | null
  toUserId: number | null
  toUserName: string | null
  createTime: string
}

// 流程图节点类型字面量，对应后端 ProcessGraphNodeVO.type；PARALLEL_SPLIT/PARALLEL_JOIN/CC/AUTO
// 是 v2 流程才有的节点类型，前端图组件对未识别的类型统一走通用占位节点展示。
export type ProcessGraphNodeType =
  | 'START'
  | 'APPROVAL'
  | 'CONDITION'
  | 'PARALLEL_SPLIT'
  | 'PARALLEL_JOIN'
  | 'CC'
  | 'AUTO'
  | 'END'

// 流程图节点三态状态：已完成/进行中/未到达。
export type ProcessGraphNodeStatus = 'COMPLETED' | 'CURRENT' | 'PENDING'

// 当前节点（status=CURRENT）的处理人/候选审批人信息，字段对应后端 CurrentApproverVO
// （add-approval-remark-and-process-flowchart change design.md Decision 7 二次修订）。
// assigned=true 表示已认领的指定处理人（userId/userName 非空）；assigned=false 表示尚未
// 认领的候选人——USER 类型时 userId/userName 非空，ROLE 类型时 roleCode/roleName 非空，
// 不含 resolveBasis（用户已简化为仅展示角色名称和编码）。
export interface CurrentApproverVO {
  userId: number | null
  userName: string | null
  roleCode: string | null
  roleName: string | null
  assigned: boolean
}

// 流程实例详情完整节点图中的单个只读节点，字段对应后端 ProcessGraphNodeVO。x/y 字段仍会
// 返回但分级列表展示不再使用（见 utils/processGraphLayout.ts 的层号计算，不做像素定位）。
export interface ProcessGraphNodeVO {
  id: string
  type: ProcessGraphNodeType
  name: string | null
  x: number | null
  y: number | null
  status: ProcessGraphNodeStatus
  records: ApprovalRecordVO[]
  currentApprovers: CurrentApproverVO[]
}

// 条件分支比较符归一化取值，对应后端 ConditionItemVO.operator（add-approval-remark-and-
// process-flowchart change design.md Decision 9：v1 的 GTE/LTE 已归一化为 GE/LE，前端只需
// 维护一套中文映射，见 components/processFlowChart/typeLabels.ts 的
// PROCESS_GRAPH_CONDITION_OPERATOR_LABEL）。
export type ProcessGraphConditionOperator = 'EQ' | 'NE' | 'GT' | 'GE' | 'LT' | 'LE' | 'IN' | 'IS_NULL'

// 条件分支单个条件项，字段对应后端 ConditionItemVO；field 是字段编码（fieldCode），前端需
// 按 fieldBizType 查对应业务对象类型的渲染元数据取展示名/翻译取值（复用
// ApprovalRequestDetailDialog.vue 已有的 labelFor/displayValue 逻辑，通过 prop 传给
// ProcessFlowChart.vue，不在子组件里重新请求渲染元数据）。value 是原始比较值，未做任何
// 格式化。
export interface ConditionItemVO {
  fieldBizType: string
  field: string
  operator: ProcessGraphConditionOperator
  value: unknown
}

// 流程实例详情完整节点图中的一条只读连线，字段对应后端 ProcessGraphEdgeVO；conditions 为该
// 边的条件分支列表（无条件/默认分支为空数组，不是 null），conditionLogic 为多条件项之间的
// 逻辑关系（AND/OR，conditions.length <= 1 时可能为 null）。
export interface ProcessGraphEdgeVO {
  id: string
  source: string
  target: string
  conditions: ConditionItemVO[]
  conditionLogic: string | null
}

// 流程实例详情，字段对应后端 ProcessInstanceDetailVO。
export interface ProcessInstanceDetailVO {
  id: number
  flowableInstanceId: string
  businessType: string
  businessId: number | null
  title: string | null
  applicantId: number | null
  applicantName: string | null
  status: ProcessInstanceStatus
  currentNodeId: string | null
  currentNodeName: string | null
  openNodes: OpenNodeVO[]
  startedTime: string
  finishedTime: string | null
  records: ApprovalRecordVO[]
  nodes: ProcessGraphNodeVO[]
  edges: ProcessGraphEdgeVO[]
}
