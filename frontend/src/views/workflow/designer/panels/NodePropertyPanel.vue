<script setup lang="ts">
// 节点属性面板：按选中节点类型渲染不同表单。
// - START/END：仅可编辑展示名称。
// - APPROVAL：审批人来源/会签模式/空审批人策略/自审转办委派加签退回开关，字段逐一对应
//   tab_wf_node_assignee_rule 各列（workflow-approval-engine change design.md Decision 9/11）。
// - CONDITION：编辑该节点全部出边的分支条件（字段/比较符/比较值），支持添加/删除分支，
//   并高亮标记哪一条是"不带 condition"的兜底默认分支；发布前必须至少保留一条。
import { computed } from 'vue'
import type { DesignerEdge, DesignerNode, DesignerNodeType } from '@/stores/workflowDesigner'
import {
  APPROVAL_MODE_OPTIONS,
  ASSIGNEE_TYPE_OPTIONS,
  CONDITION_OPERATOR_OPTIONS,
  EMPTY_ASSIGNEE_STRATEGY_OPTIONS,
  type ConditionOperator,
  type EdgeConditionDsl,
} from '@/types/workflow'

const props = defineProps<{
  node: DesignerNode
  outgoingEdges: DesignerEdge[]
  nodeLabel: (id: string) => string
  readonly?: boolean
}>()

const emit = defineEmits<{
  (e: 'update-node', patch: Record<string, unknown>): void
  (e: 'update-edge-condition', payload: { edgeId: string; condition: EdgeConditionDsl | null }): void
  (e: 'add-branch'): void
  (e: 'remove-branch', edgeId: string): void
}>()

const nodeType = computed(() => props.node.type as DesignerNodeType)

function updateLabel(value: string) {
  emit('update-node', { label: value })
}

function updateField(field: string, value: unknown) {
  emit('update-node', { [field]: value })
}

const hasDefaultBranch = computed(() => props.outgoingEdges.some((edge) => !edge.data?.condition))

function toggleBranchDefault(edge: DesignerEdge, isDefault: boolean) {
  if (isDefault) {
    emit('update-edge-condition', { edgeId: edge.id, condition: null })
  } else {
    emit('update-edge-condition', {
      edgeId: edge.id,
      condition: { field: '', operator: 'EQ', value: '' },
    })
  }
}

function updateConditionField(edge: DesignerEdge, patch: Partial<EdgeConditionDsl>) {
  const current: EdgeConditionDsl = edge.data?.condition ?? { field: '', operator: 'EQ', value: '' }
  emit('update-edge-condition', { edgeId: edge.id, condition: { ...current, ...patch } })
}
</script>

<template>
  <div class="node-property-panel">
    <template v-if="nodeType === 'start' || nodeType === 'end'">
      <el-form label-width="80px" :disabled="readonly">
        <el-form-item label="节点名称">
          <el-input :model-value="node.data?.label" placeholder="选填" @update:model-value="updateLabel" />
        </el-form-item>
        <p class="node-property-panel__hint">
          {{ nodeType === 'start' ? '开始节点不携带审批属性，流程模型内有且仅能有一个。' : '结束节点不携带审批属性，流程模型内至少需要一个。' }}
        </p>
      </el-form>
    </template>

    <template v-else-if="nodeType === 'approval'">
      <el-form label-width="110px" :disabled="readonly">
        <el-form-item label="节点名称">
          <el-input :model-value="node.data?.label" placeholder="如：部门负责人审批" @update:model-value="updateLabel" />
        </el-form-item>
        <el-form-item label="审批人来源" required>
          <el-select
            :model-value="node.data?.assigneeType"
            placeholder="请选择审批人来源"
            style="width: 100%"
            @update:model-value="(v: string) => updateField('assigneeType', v)"
          >
            <el-option v-for="opt in ASSIGNEE_TYPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="node.data?.assigneeType === 'ROLE' || node.data?.assigneeType === 'USER'"
          :label="node.data?.assigneeType === 'ROLE' ? '角色编码' : '用户 id'"
          required
        >
          <el-input
            :model-value="node.data?.assigneeValue"
            :placeholder="node.data?.assigneeType === 'ROLE' ? '如：SECURITY_ADMIN' : '用户 id，多个以逗号分隔'"
            @update:model-value="(v: string) => updateField('assigneeValue', v)"
          />
        </el-form-item>
        <el-form-item
          v-else-if="
            node.data?.assigneeType === 'ORG_LEADER' ||
            node.data?.assigneeType === 'APPLICANT_DEPT_LEADER' ||
            node.data?.assigneeType === 'APPLICANT_DEPT_PARENT_LEADER'
          "
          label="要求的管理员角色"
        >
          <el-input
            :model-value="node.data?.assigneeValue"
            placeholder="要求持有的管理员角色编码，如：DEPT_LEADER"
            @update:model-value="(v: string) => updateField('assigneeValue', v)"
          />
        </el-form-item>

        <el-form-item label="会签模式" required>
          <el-select
            :model-value="node.data?.approvalMode"
            style="width: 100%"
            @update:model-value="(v: string) => updateField('approvalMode', v)"
          >
            <el-option v-for="opt in APPROVAL_MODE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="node.data?.approvalMode === 'PERCENT'" label="通过比例" required>
          <el-input-number
            :model-value="node.data?.approvalPercent ?? undefined"
            :min="1"
            :max="100"
            style="width: 100%"
            @update:model-value="(v: number | undefined) => updateField('approvalPercent', v ?? null)"
          />
          <span class="node-property-panel__unit">%</span>
        </el-form-item>

        <el-form-item label="空审批人策略" required>
          <el-select
            :model-value="node.data?.emptyAssigneeStrategy"
            style="width: 100%"
            @update:model-value="(v: string) => updateField('emptyAssigneeStrategy', v)"
          >
            <el-option
              v-for="opt in EMPTY_ASSIGNEE_STRATEGY_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="操作权限">
          <div class="node-property-panel__switches">
            <el-checkbox
              :model-value="node.data?.allowSelfApproval"
              @update:model-value="(v) => updateField('allowSelfApproval', !!v)"
            >
              允许自审
            </el-checkbox>
            <el-checkbox
              :model-value="node.data?.allowTransfer"
              @update:model-value="(v) => updateField('allowTransfer', !!v)"
            >
              允许转办
            </el-checkbox>
            <el-checkbox
              :model-value="node.data?.allowDelegate"
              @update:model-value="(v) => updateField('allowDelegate', !!v)"
            >
              允许委派
            </el-checkbox>
            <el-checkbox
              :model-value="node.data?.allowAddSign"
              @update:model-value="(v) => updateField('allowAddSign', !!v)"
            >
              允许加签
            </el-checkbox>
            <el-checkbox
              :model-value="node.data?.allowReturn"
              @update:model-value="(v) => updateField('allowReturn', !!v)"
            >
              允许退回到该节点
            </el-checkbox>
          </div>
        </el-form-item>
      </el-form>
    </template>

    <template v-else-if="nodeType === 'condition'">
      <el-form label-width="80px" :disabled="readonly">
        <el-form-item label="节点名称">
          <el-input :model-value="node.data?.label" placeholder="如：金额判断" @update:model-value="updateLabel" />
        </el-form-item>
      </el-form>

      <div class="node-property-panel__branch-header">
        <span class="node-property-panel__branch-title">分支条件（出边）</span>
        <el-button v-if="!readonly" link type="primary" @click="emit('add-branch')">添加分支</el-button>
      </div>

      <el-alert
        v-if="!hasDefaultBranch"
        type="warning"
        :closable="false"
        show-icon
        title="缺少默认分支"
        description="请至少保留一条不设置比较条件的兜底分支，否则发布会被拒绝。"
        class="node-property-panel__branch-alert"
      />

      <p v-if="outgoingEdges.length === 0" class="node-property-panel__hint">
        当前条件节点还没有任何出边，请先在画布上从该节点拖出连线到目标节点，再回到这里配置条件。
      </p>

      <div v-else class="node-property-panel__branch-list">
        <div v-for="edge in outgoingEdges" :key="edge.id" class="node-property-panel__branch-row">
          <div class="node-property-panel__branch-row-header">
            <span class="node-property-panel__branch-target">→ {{ nodeLabel(edge.target) }}</span>
            <el-checkbox
              :model-value="!edge.data?.condition"
              :disabled="readonly"
              @update:model-value="(v) => toggleBranchDefault(edge, !!v)"
            >
              作为默认兜底分支
            </el-checkbox>
            <el-button v-if="!readonly" link type="danger" @click="emit('remove-branch', edge.id)">删除</el-button>
          </div>
          <div v-if="edge.data?.condition" class="node-property-panel__branch-condition">
            <el-input
              :model-value="edge.data.condition.field"
              placeholder="字段（流程启动变量）"
              style="width: 140px"
              :disabled="readonly"
              @update:model-value="(v: string) => updateConditionField(edge, { field: v })"
            />
            <el-select
              :model-value="edge.data.condition.operator"
              style="width: 110px"
              :disabled="readonly"
              @update:model-value="(v: ConditionOperator) => updateConditionField(edge, { operator: v })"
            >
              <el-option v-for="opt in CONDITION_OPERATOR_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-input
              :model-value="String(edge.data.condition.value ?? '')"
              placeholder="比较值"
              style="width: 120px"
              :disabled="readonly"
              @update:model-value="(v: string) => updateConditionField(edge, { value: v })"
            />
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.node-property-panel__hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
  line-height: 1.6;
  margin: 0;
}

.node-property-panel__unit {
  margin-left: 8px;
  color: var(--color-text-secondary);
}

.node-property-panel__switches {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.node-property-panel__branch-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 16px 0 8px;
}

.node-property-panel__branch-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-ink);
}

.node-property-panel__branch-alert {
  margin-bottom: 12px;
}

// 分支行用一条虚线 + 圆点串起来，呼应项目"链式连接"视觉语言（身份->角色->权限层层关联）
.node-property-panel__branch-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-left: 16px;
  border-left: 1px dashed var(--chain-line-color);
}

.node-property-panel__branch-row {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.node-property-panel__branch-row::before {
  content: '';
  position: absolute;
  left: -20px;
  top: 6px;
  width: var(--chain-dot-size-sm);
  height: var(--chain-dot-size-sm);
  border-radius: 50%;
  background: var(--chain-line-color-active);
}

.node-property-panel__branch-row-header {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.node-property-panel__branch-target {
  font-size: 13px;
  color: var(--color-ink);
  font-weight: 600;
}

.node-property-panel__branch-condition {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
