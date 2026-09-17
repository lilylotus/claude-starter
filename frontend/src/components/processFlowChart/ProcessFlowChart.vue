<script setup lang="ts">
// "审批流程"分级列表：被 ApprovalRequestDetailDialog.vue 内嵌展示，输入是后端
// GET /api/v1/workflow/process-instances/{id} 返回的 ProcessInstanceDetailVO
// （add-approval-remark-and-process-flowchart change design.md Decision 6，2026-09-17
// 修订版）。
//
// - 顶部一行文字摘要：进行中展示当前节点名，已结束展示"已结束（已通过/已拒绝/...）"。
// - 下方按审批级别一级一级纵向展示列表，不再是可缩放拖拽的 Vue Flow 图形画布：层号计算见
//   utils/processGraphLayout.ts（只算层号，不算像素坐标），v1/v2 流程统一走同一套分层。
// - 节点按 status 三态区分：CURRENT（当前审批点）高亮突出，并展示处理人/候选审批人信息；
//   PENDING（未到达）置灰；COMPLETED（已完成）直接内联展示该节点关联的完整审批轨迹（谁、
//   何时、什么意见，会签节点可能有多条），数据同样来自 ProcessGraphNodeVO.records，不需要
//   额外请求、不需要点击（approval-history-detail-entry change design.md Decision 5：
//   原先点击节点弹窗查看的交互改为直接展示，省去多余的一次点击）。
import { computed } from 'vue'
import {
  APPROVAL_RECORD_ACTION_LABEL,
  PROCESS_INSTANCE_STATUS_LABEL,
  type ConditionItemVO,
  type CurrentApproverVO,
  type ProcessGraphEdgeVO,
  type ProcessGraphNodeVO,
  type ProcessInstanceDetailVO,
} from '@/types/workflow'
import type { ApprovalBizType } from '@/types/approval'
import { computeNodeLevels, type ProcessGraphLevel } from '@/utils/processGraphLayout'
import {
  PROCESS_GRAPH_CONDITION_OPERATOR_LABEL,
  PROCESS_GRAPH_NODE_TYPE_ICON,
  PROCESS_GRAPH_NODE_TYPE_LABEL,
} from './typeLabels'

const props = defineProps<{
  detail: ProcessInstanceDetailVO | null
  loading?: boolean
  // 条件分支字段展示名/取值翻译：复用父组件 ApprovalRequestDetailDialog.vue 已经拉取好的
  // 四个 bizType 渲染元数据（labelFor/displayValue），本组件不重新发起请求
  // （design.md Decision 9）。
  resolveFieldLabel: (bizType: ApprovalBizType, field: string) => string
  resolveFieldValueLabel: (bizType: ApprovalBizType, field: string, value: unknown) => string
}>()

const levels = computed<ProcessGraphLevel[]>(() => {
  const detail = props.detail
  if (!detail || detail.nodes.length === 0) return []
  return computeNodeLevels(detail.nodes, detail.edges)
})

// 单个条件项转换成可读文案，如"性别 等于 女"：字段展示名 + 运算符中文 + 取值展示名
// （字典字段自动翻译成选项文案），IS_NULL 等不需要取值的运算符不拼接取值
// （design.md Decision 9）。
function conditionItemText(item: ConditionItemVO): string {
  const bizType = item.fieldBizType as ApprovalBizType
  const fieldLabel = props.resolveFieldLabel(bizType, item.field)
  const operatorLabel = PROCESS_GRAPH_CONDITION_OPERATOR_LABEL[item.operator] ?? item.operator
  if (item.operator === 'IS_NULL') {
    return `${fieldLabel} ${operatorLabel}`
  }
  const valueLabel = props.resolveFieldValueLabel(bizType, item.field, item.value)
  return `${fieldLabel} ${operatorLabel} ${valueLabel}`
}

// 一条边的条件说明文案：多个条件项按 conditionLogic（AND/OR）用"且"/"或"连接；无条件/
// 默认分支（conditions 为空数组）返回 null，不展示任何说明。
function edgeConditionText(edge: ProcessGraphEdgeVO): string | null {
  const conditions = edge.conditions ?? []
  if (conditions.length === 0) return null
  const joiner = edge.conditionLogic === 'OR' ? ' 或 ' : ' 且 '
  return conditions.map(conditionItemText).join(joiner)
}

// 节点 id -> 指向它的条件分支说明文字（入边条件非空时展示），作为该节点下方的小字注释
// 展示，不强求覆盖所有边——普通边/无条件默认分支不展示。
const conditionAnnotations = computed<Map<string, string>>(() => {
  const map = new Map<string, string>()
  const edges = props.detail?.edges ?? []
  edges.forEach((edge) => {
    const text = edgeConditionText(edge)
    if (!text) return
    const existing = map.get(edge.target)
    map.set(edge.target, existing ? `${existing}、${text}` : text)
  })
  return map
})

function nodeLabel(node: ProcessGraphNodeVO): string {
  return node.name || PROCESS_GRAPH_NODE_TYPE_LABEL[node.type] || node.type
}

function statusClass(node: ProcessGraphNodeVO): string {
  return `is-${node.status.toLowerCase()}`
}

// ---- 当前节点（CURRENT）处理人/候选审批人展示（design.md Decision 7 二次修订）：
//      assigned=true 一条一行"处理人：xxx"；assigned=false 按 USER/ROLE 分别汇总成
//      "候选审批人：xxx"，多个同类候选人用顿号连接展示在同一行，不做悬浮/点击交互。 ----

interface ApproverLine {
  key: string
  text: string
}

function approverLines(node: ProcessGraphNodeVO): ApproverLine[] {
  if (node.status !== 'CURRENT') return []
  const approvers = node.currentApprovers ?? []
  if (approvers.length === 0) return []

  const assignedNames = approvers
    .filter((a) => a.assigned)
    .map((a) => a.userName || (a.userId != null ? String(a.userId) : '—'))
  const candidateUserNames = approvers
    .filter((a): a is CurrentApproverVO => !a.assigned && a.roleCode == null)
    .map((a) => a.userName || (a.userId != null ? String(a.userId) : '—'))
  const candidateRoles = approvers.filter((a) => !a.assigned && a.roleCode != null)

  const lines: ApproverLine[] = []
  if (assignedNames.length > 0) {
    lines.push({ key: 'assigned', text: `处理人：${assignedNames.join('、')}` })
  }
  if (candidateUserNames.length > 0) {
    lines.push({ key: 'candidate-user', text: `候选审批人：${candidateUserNames.join('、')}` })
  }
  if (candidateRoles.length > 0) {
    const text = candidateRoles.map((a) => `${a.roleName || a.roleCode}（${a.roleCode}）`).join('、')
    lines.push({ key: 'candidate-role', text: `候选审批人：${text}` })
  }
  return lines
}

const summaryText = computed(() => {
  const detail = props.detail
  if (!detail) return ''
  if (detail.status === 'RUNNING') {
    const currentNames =
      detail.openNodes.length > 0
        ? detail.openNodes.map((node) => node.nodeName).join('、')
        : detail.currentNodeName
    return `当前节点：${currentNames || '—'}`
  }
  return `已结束（${PROCESS_INSTANCE_STATUS_LABEL[detail.status] ?? detail.status}）`
})

function actionLabel(action: string): string {
  return APPROVAL_RECORD_ACTION_LABEL[action] ?? action
}
</script>

<template>
  <div class="process-flow-chart">
    <p class="process-flow-chart__summary">{{ summaryText }}</p>
    <div v-loading="loading" class="process-flow-chart__list-wrap">
      <ol v-if="levels.length > 0" class="process-flow-chart__levels">
        <li
          v-for="group in levels"
          :key="group.level"
          class="process-flow-chart__level"
          :class="{ 'is-active': group.nodes.some((node) => node.status === 'CURRENT') }"
        >
          <span class="process-flow-chart__level-dot" />
          <div class="process-flow-chart__level-body">
            <div class="process-flow-chart__nodes">
              <div v-for="node in group.nodes" :key="node.id" class="process-flow-chart__node" :class="statusClass(node)">
                <div class="process-flow-chart__node-header">
                  <el-icon class="process-flow-chart__node-icon">
                    <component :is="PROCESS_GRAPH_NODE_TYPE_ICON[node.type]" />
                  </el-icon>
                  <span class="process-flow-chart__node-name">{{ nodeLabel(node) }}</span>
                  <el-tag v-if="node.status === 'CURRENT'" type="primary" size="small" effect="dark">进行中</el-tag>
                </div>
                <span class="process-flow-chart__node-type">{{ PROCESS_GRAPH_NODE_TYPE_LABEL[node.type] }}</span>
                <span v-if="conditionAnnotations.get(node.id)" class="process-flow-chart__node-condition">
                  条件：{{ conditionAnnotations.get(node.id) }}
                </span>
                <div v-if="node.status === 'CURRENT'" class="process-flow-chart__node-approvers">
                  <p v-for="line in approverLines(node)" :key="line.key" class="process-flow-chart__node-approver-line">
                    {{ line.text }}
                  </p>
                  <p v-if="approverLines(node).length === 0" class="process-flow-chart__node-approver-line is-empty">
                    暂无处理人/候选审批人信息
                  </p>
                </div>
                <div v-if="node.status === 'COMPLETED'" class="process-flow-chart__node-records">
                  <div v-for="record in node.records ?? []" :key="record.id" class="process-flow-chart__node-record">
                    <div class="process-flow-chart__node-record-row">
                      <span class="process-flow-chart__node-record-operator">
                        {{ record.operatorName || record.operatorId || '—' }}
                      </span>
                      <el-tag size="small">{{ actionLabel(record.action) }}</el-tag>
                    </div>
                    <p class="process-flow-chart__node-record-time">{{ record.createTime }}</p>
                    <p v-if="record.remark" class="process-flow-chart__node-record-remark">{{ record.remark }}</p>
                    <p v-if="record.fromUserName || record.toUserName" class="process-flow-chart__node-record-transfer">
                      {{ record.fromUserName || '—' }} → {{ record.toUserName || '—' }}
                    </p>
                  </div>
                  <p v-if="(node.records ?? []).length === 0" class="process-flow-chart__node-record-empty">
                    暂无处理记录
                  </p>
                </div>
              </div>
            </div>
          </div>
        </li>
      </ol>
      <p v-else-if="!loading" class="process-flow-chart__empty">暂无流程图数据</p>
    </div>
  </div>
</template>

<style scoped lang="scss">
.process-flow-chart {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.process-flow-chart__summary {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.process-flow-chart__list-wrap {
  max-height: 420px;
  width: 100%;
  overflow: auto;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-canvas);
  padding: 16px;
}

.process-flow-chart__empty {
  margin: 0;
  padding: 40px 0;
  text-align: center;
  font-size: 13px;
  color: var(--color-text-tertiary);
}

// 分级列表：圆点 + 虚线连接线，呼应概览页时间线的"链式连接"视觉语言（RBAC
// 身份→角色→权限→资源的层层关联）。
.process-flow-chart__levels {
  list-style: none;
  margin: 0;
  padding: 0 0 0 4px;
}

.process-flow-chart__level {
  position: relative;
  padding-left: 22px;
  padding-bottom: 20px;
  border-left: 1px dashed var(--chain-line-color);

  &:last-child {
    border-left-color: transparent;
    padding-bottom: 0;
  }

  &.is-active {
    border-left-color: var(--chain-line-color-active);
  }
}

.process-flow-chart__level-dot {
  position: absolute;
  left: -5px;
  top: 6px;
  width: var(--chain-dot-size);
  height: var(--chain-dot-size);
  border-radius: 50%;
  background: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-soft);
}

.process-flow-chart__nodes {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.process-flow-chart__node {
  min-width: 200px;
  max-width: 280px;
  border: 2px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
  padding: 10px 14px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  cursor: default;
  transition: box-shadow 0.15s ease;

  &.is-completed {
    border-color: var(--color-success);
    background: #eafaf2;
  }

  &.is-current {
    border-color: var(--color-primary);
    background: var(--color-primary-soft);
    box-shadow: 0 0 0 3px rgba(45, 108, 223, 0.18);
  }

  &.is-pending {
    border-style: dashed;
    background: var(--color-canvas);
    color: var(--color-text-tertiary);
    opacity: 0.75;
  }
}

.process-flow-chart__node-header {
  display: flex;
  align-items: center;
  gap: 6px;
}

.process-flow-chart__node-icon {
  color: var(--color-primary);
  flex-shrink: 0;
}

.process-flow-chart__node.is-pending .process-flow-chart__node-icon {
  color: var(--color-text-tertiary);
}

.process-flow-chart__node-name {
  font-weight: 600;
  font-size: 13px;
  color: var(--color-ink);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.process-flow-chart__node.is-pending .process-flow-chart__node-name {
  color: var(--color-text-tertiary);
  font-weight: 500;
}

.process-flow-chart__node-type {
  font-size: 11px;
  color: var(--color-text-tertiary);
}

.process-flow-chart__node-condition {
  font-size: 11px;
  color: var(--color-warning);
}

.process-flow-chart__node-approvers {
  margin-top: 4px;
  padding-top: 4px;
  border-top: 1px dashed var(--color-border-strong);
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.process-flow-chart__node-approver-line {
  margin: 0;
  font-size: 12px;
  color: var(--color-ink);

  &.is-empty {
    color: var(--color-text-tertiary);
  }
}

.process-flow-chart__node-records {
  margin-top: 4px;
  padding-top: 4px;
  border-top: 1px dashed var(--color-border-strong);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.process-flow-chart__node-record {
  display: flex;
  flex-direction: column;
  gap: 2px;

  & + & {
    padding-top: 6px;
    border-top: 1px dashed var(--color-border);
  }
}

.process-flow-chart__node-record-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--color-ink);
}

.process-flow-chart__node-record-operator {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.process-flow-chart__node-record-time {
  margin: 0;
  color: var(--color-text-tertiary);
  font-size: 11px;
}

.process-flow-chart__node-record-remark {
  margin: 0;
  font-size: 12px;
  color: var(--color-text);
}

.process-flow-chart__node-record-transfer {
  margin: 0;
  font-size: 11px;
  color: var(--color-text-secondary);
}

.process-flow-chart__node-record-empty {
  margin: 0;
  color: var(--color-text-tertiary);
  font-size: 12px;
}
</style>
