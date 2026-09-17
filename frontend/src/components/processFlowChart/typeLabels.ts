// ProcessGraphNodeVO.type 的中文展示名与图标，供分级列表 ProcessFlowChart.vue 展示节点
// 类型使用。图标延续原 Vue Flow 节点组件（nodes/Flow*.vue，已废弃）的视觉语言：开始用
// VideoPlay（绿色系）、审批用 UserFilled（品牌蓝）、条件用 Share（橙色系）、结束用
// CircleCheck（灰色系），其余 v2 专有类型（并行分叉/汇合、抄送、自动任务）统一用
// MoreFilled 占位，不追求与其业务含义匹配的专属图标。
import { markRaw, type Component } from 'vue'
import { CircleCheck, MoreFilled, Share, UserFilled, VideoPlay } from '@element-plus/icons-vue'
import type { ProcessGraphConditionOperator, ProcessGraphNodeType } from '@/types/workflow'

export const PROCESS_GRAPH_NODE_TYPE_LABEL: Record<ProcessGraphNodeType, string> = {
  START: '开始',
  APPROVAL: '审批',
  CONDITION: '条件',
  PARALLEL_SPLIT: '并行分叉',
  PARALLEL_JOIN: '并行汇合',
  CC: '抄送',
  AUTO: '自动任务',
  END: '结束',
}

export const PROCESS_GRAPH_NODE_TYPE_ICON: Record<ProcessGraphNodeType, Component> = {
  START: markRaw(VideoPlay),
  APPROVAL: markRaw(UserFilled),
  CONDITION: markRaw(Share),
  PARALLEL_SPLIT: markRaw(MoreFilled),
  PARALLEL_JOIN: markRaw(MoreFilled),
  CC: markRaw(MoreFilled),
  AUTO: markRaw(MoreFilled),
  END: markRaw(CircleCheck),
}

// ConditionItemVO.operator 归一化取值的中文展示名（add-approval-remark-and-process-flowchart
// change design.md Decision 9），供 ProcessFlowChart.vue 把条件分支拼成可读文案，如
// "性别 等于 女"。
export const PROCESS_GRAPH_CONDITION_OPERATOR_LABEL: Record<ProcessGraphConditionOperator, string> = {
  EQ: '等于',
  NE: '不等于',
  GT: '大于',
  GE: '大于等于',
  LT: '小于',
  LE: '小于等于',
  IN: '属于',
  IS_NULL: '为空',
}
