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
  CONDITION_FIELD_BIZ_TYPE_LABEL,
  EMPTY_ASSIGNEE_STRATEGY_OPTIONS,
  getConditionOperatorOptions,
  type ConditionFieldOption,
  type ConditionOperator,
  type EdgeConditionDsl,
} from '@/types/workflow'
import {
  FORM_FIELD_CONTROL_TYPE_DATE,
  FORM_FIELD_CONTROL_TYPE_DICT,
  FORM_FIELD_CONTROL_TYPE_NUMBER,
  type FormFieldBizType,
} from '@/types/formField'

const props = defineProps<{
  node: DesignerNode
  outgoingEdges: DesignerEdge[]
  nodeLabel: (id: string) => string
  // 条件分支"字段"下拉的数据源：组织/用户/任职/应用四类业务对象的表单字段定义合并列表，
  // 已过滤掉多选字典类型字段，由 ProcessDesignerView.vue 并行请求 render-schema 后传入。
  conditionFieldOptions: ConditionFieldOption[]
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

function emptyCondition(): EdgeConditionDsl {
  return { fieldBizType: '', field: '', operator: 'EQ', value: null }
}

function toggleBranchDefault(edge: DesignerEdge, isDefault: boolean) {
  if (isDefault) {
    emit('update-edge-condition', { edgeId: edge.id, condition: null })
  } else {
    emit('update-edge-condition', { edgeId: edge.id, condition: emptyCondition() })
  }
}

function updateConditionField(edge: DesignerEdge, patch: Partial<EdgeConditionDsl>) {
  const current: EdgeConditionDsl = edge.data?.condition ?? emptyCondition()
  emit('update-edge-condition', { edgeId: edge.id, condition: { ...current, ...patch } })
}

// 按业务类型分组的字段下拉数据（Element Plus el-option-group），组内选项按 fieldName 展示，
// 只展示实际有字段的业务类型分组；顺序固定为 组织/用户/任职/应用。
const FIELD_GROUP_ORDER: FormFieldBizType[] = ['ORG', 'USER', 'POSITION', 'APP']
const groupedFieldOptions = computed(() =>
  FIELD_GROUP_ORDER.map((bizType) => ({
    bizType,
    label: CONDITION_FIELD_BIZ_TYPE_LABEL[bizType],
    options: props.conditionFieldOptions.filter((opt) => opt.bizType === bizType),
  })).filter((group) => group.options.length > 0),
)

// 字段下拉用 `${bizType}::${fieldCode}` 作为选项的复合 value，因为不同业务类型下可能
// 存在相同的 fieldCode（如都叫 remark），裸 fieldCode 无法保证跨分组唯一。
function fieldOptionKey(bizType: string, fieldCode: string): string {
  return `${bizType}::${fieldCode}`
}

function conditionFieldSelectValue(condition: EdgeConditionDsl | null | undefined): string {
  if (!condition?.fieldBizType || !condition?.field) return ''
  return fieldOptionKey(condition.fieldBizType, condition.field)
}

// 根据 condition 当前的 fieldBizType/field 反查完整的字段选项（含 controlType/dictOptions），
// 驱动比较符/比较值控件的联动展示；未选字段或选中了一个已不在数据源里的字段（如字段被停用）
// 时返回 undefined，比较符/比较值控件按"未选字段"处理并禁用。
function findConditionFieldOption(condition: EdgeConditionDsl | null | undefined): ConditionFieldOption | undefined {
  if (!condition?.fieldBizType || !condition?.field) return undefined
  return props.conditionFieldOptions.find(
    (opt) => opt.bizType === condition.fieldBizType && opt.fieldCode === condition.field,
  )
}

function conditionOperatorOptions(condition: EdgeConditionDsl | null | undefined) {
  return getConditionOperatorOptions(findConditionFieldOption(condition)?.controlType)
}

// 切换"字段"选择后：比较值语义完全依赖所选字段的控件类型，统一清空避免留下类型不匹配的
// 脏值；比较符仅在新字段的控件类型下仍然合法时保留，否则重置为默认的"等于"
// （design.md Decision 6 / tasks.md 4.2）。
function handleFieldSelect(edge: DesignerEdge, compositeKey: string) {
  const separatorIndex = compositeKey.indexOf('::')
  if (separatorIndex < 0) return
  const bizType = compositeKey.slice(0, separatorIndex)
  const fieldCode = compositeKey.slice(separatorIndex + 2)
  const option = props.conditionFieldOptions.find((opt) => opt.bizType === bizType && opt.fieldCode === fieldCode)
  const currentOperator = edge.data?.condition?.operator
  const allowedOperators = getConditionOperatorOptions(option?.controlType).map((opt) => opt.value)
  const nextOperator: ConditionOperator =
    currentOperator && allowedOperators.includes(currentOperator) ? currentOperator : 'EQ'
  updateConditionField(edge, { fieldBizType: bizType, field: fieldCode, operator: nextOperator, value: null })
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
            <el-select
              :model-value="conditionFieldSelectValue(edge.data.condition)"
              placeholder="请选择字段"
              style="width: 160px"
              :disabled="readonly"
              @update:model-value="(v: string) => handleFieldSelect(edge, v)"
            >
              <el-option-group v-for="group in groupedFieldOptions" :key="group.bizType" :label="group.label">
                <el-option
                  v-for="opt in group.options"
                  :key="opt.fieldCode"
                  :label="opt.fieldName"
                  :value="fieldOptionKey(opt.bizType, opt.fieldCode)"
                />
              </el-option-group>
            </el-select>
            <el-select
              :model-value="edge.data.condition.operator"
              placeholder="请先选择字段"
              style="width: 110px"
              :disabled="readonly || !findConditionFieldOption(edge.data.condition)"
              @update:model-value="(v: ConditionOperator) => updateConditionField(edge, { operator: v })"
            >
              <el-option
                v-for="opt in conditionOperatorOptions(edge.data.condition)"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>

            <template v-if="findConditionFieldOption(edge.data.condition)?.controlType === FORM_FIELD_CONTROL_TYPE_NUMBER">
              <el-input-number
                :model-value="(edge.data.condition.value as number | null | undefined) ?? undefined"
                placeholder="比较值"
                style="width: 140px"
                :disabled="readonly"
                :controls="false"
                @update:model-value="(v: number | undefined) => updateConditionField(edge, { value: v ?? null })"
              />
            </template>
            <template v-else-if="findConditionFieldOption(edge.data.condition)?.controlType === FORM_FIELD_CONTROL_TYPE_DICT">
              <el-select
                :model-value="(edge.data.condition.value as string | null | undefined) ?? undefined"
                placeholder="请选择比较值"
                style="width: 140px"
                :disabled="readonly"
                @update:model-value="(v: string) => updateConditionField(edge, { value: v })"
              >
                <el-option
                  v-for="opt in findConditionFieldOption(edge.data.condition)?.dictOptions ?? []"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </template>
            <template v-else-if="findConditionFieldOption(edge.data.condition)?.controlType === FORM_FIELD_CONTROL_TYPE_DATE">
              <el-date-picker
                :model-value="(edge.data.condition.value as string | null | undefined) ?? null"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择比较值"
                style="width: 140px"
                :disabled="readonly"
                @update:model-value="(v: string | null) => updateConditionField(edge, { value: v })"
              />
            </template>
            <template v-else>
              <el-input
                :model-value="(edge.data.condition.value as string | null | undefined) ?? ''"
                :placeholder="findConditionFieldOption(edge.data.condition) ? '比较值' : '请先选择字段'"
                style="width: 140px"
                :disabled="readonly || !findConditionFieldOption(edge.data.condition)"
                @update:model-value="(v: string) => updateConditionField(edge, { value: v })"
              />
            </template>
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
  flex-wrap: wrap;
}
</style>
