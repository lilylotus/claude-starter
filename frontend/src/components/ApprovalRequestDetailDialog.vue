<script setup lang="ts">
// 审批申请详情弹窗：被"我的申请""待我审批"两个页面共用。新增类申请只展示 requestPayload
// 里的新值；更新类申请同时展示 targetSnapshot（目标记录当前值）与 requestPayload（申请新值）
// 的新旧对照；启用/停用/删除类申请不携带 requestPayload，只展示基础信息。
//
// 字段展示名复用 form-field-definition-management 能力的渲染元数据接口（GET
// /api/form-fields/render-schema?bizType=），与四个管理页面新增/编辑表单动态渲染同一套
// 元数据来源（design.md Decision 8），不重新维护一份字段展示名映射；四个 bizType 的渲染
// 元数据在弹窗组件挂载时一次性拉取，代价是四次轻量 GET 请求换取实现简洁（弹窗内会被
// 反复复用于不同 bizType 的申请行，不适合每次切换行都重新拉取/销毁对应 bizType 的元数据）。
import { computed, onMounted } from 'vue'
import { useDynamicFormFields } from '@/composables/useDynamicFormFields'
import {
  FORM_FIELD_CONTROL_TYPE_DICT,
  FORM_FIELD_CONTROL_TYPE_MULTI_DICT,
  type FormFieldRenderItem,
} from '@/types/formField'
import {
  APPROVAL_BIZ_TYPE_OPTIONS,
  APPROVAL_OPERATION_TYPE_OPTIONS,
  APPROVAL_STATUS_APPROVED,
  APPROVAL_STATUS_OPTIONS,
  APPROVAL_STATUS_PENDING,
  APPROVAL_STATUS_REJECTED,
  type ApprovalBizType,
  type ApprovalOperationType,
  type ApprovalRequestRow,
} from '@/types/approval'

const props = defineProps<{
  modelValue: boolean
  row: ApprovalRequestRow | null
}>()

const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

// 四个 bizType 各自的渲染元数据，弹窗挂载时一次性拉取
const orgFields = useDynamicFormFields('ORG')
const userFields = useDynamicFormFields('USER')
const positionFields = useDynamicFormFields('POSITION')
const appFields = useDynamicFormFields('APP')

onMounted(() => {
  orgFields.fetchSchema()
  userFields.fetchSchema()
  positionFields.fetchSchema()
  appFields.fetchSchema()
})

type DynamicFields = typeof orgFields

function schemaFor(bizType: ApprovalBizType): DynamicFields {
  switch (bizType) {
    case 'ORG':
      return orgFields
    case 'USER':
      return userFields
    case 'POSITION':
      return positionFields
    case 'APP':
      return appFields
  }
}

// 渲染元数据未覆盖的关联字段（外键类 id），给出静态展示名兜底；其余原始 JSON key
// 直接原样展示（宁可展示原始字段名，也不要因为找不到映射而丢字段）
const STATIC_LABELS: Record<ApprovalBizType, Record<string, string>> = {
  ORG: { parentId: '上级组织' },
  USER: {},
  POSITION: { orgId: '所属组织', userId: '所属用户' },
  APP: { orgId: '所属组织', ownerId: '负责人' },
}

function schemaItemFor(bizType: ApprovalBizType, key: string): FormFieldRenderItem | undefined {
  return schemaFor(bizType).schema.find((item: FormFieldRenderItem) => item.columnName === key)
}

function labelFor(bizType: ApprovalBizType, key: string): string {
  return schemaItemFor(bizType, key)?.fieldName ?? STATIC_LABELS[bizType][key] ?? key
}

// 把原始值转换为可读展示：字典/多选字典按渲染元数据翻译成标签；布尔值转"是/否"；
// 数组/对象（如 USER 更新申请里的 positions）退化为 JSON 文本展示，空值统一展示为"-"
function displayValue(bizType: ApprovalBizType, item: FormFieldRenderItem | undefined, raw: unknown): string {
  if (raw === undefined || raw === null || raw === '') return '-'
  const fields = schemaFor(bizType)
  if (item?.controlType === FORM_FIELD_CONTROL_TYPE_DICT) {
    return fields.dictOptionLabel(item, raw) || String(raw)
  }
  if (item?.controlType === FORM_FIELD_CONTROL_TYPE_MULTI_DICT) {
    return fields.dictOptionLabels(item, raw) || '-'
  }
  if (typeof raw === 'boolean') return raw ? '是' : '否'
  if (Array.isArray(raw) || typeof raw === 'object') return JSON.stringify(raw)
  return String(raw)
}

interface FieldDiffRow {
  key: string
  label: string
  newValue: string
  oldValue: string | null
}

// requestPayload（新值）与 targetSnapshot（目标记录当前值，仅 UPDATE 提供）合并成
// 统一的字段展示行；CREATE 申请没有 targetSnapshot，oldValue 恒为 null（模板据此
// 决定是否渲染"旧值 -> 新值"对照，还是只展示新值）。USER 的 positions 是任职记录
// 数组，字段含义按 POSITION bizType 的渲染元数据单独展开成结构化卡片（见
// userPositions），不适合和其余标量字段一样走 JSON.stringify 兜底，这里排除掉，
// 避免被渲染两次
const fieldRows = computed<FieldDiffRow[]>(() => {
  const row = props.row
  if (!row) return []
  const payload = row.requestPayload ?? {}
  const snapshot = row.targetSnapshot ?? {}
  const keys = Array.from(new Set([...Object.keys(payload), ...Object.keys(snapshot)])).filter(
    (key) => !(row.bizType === 'USER' && key === 'positions'),
  )
  return keys.map((key) => {
    const item = schemaItemFor(row.bizType, key)
    return {
      key,
      label: labelFor(row.bizType, key),
      newValue: displayValue(row.bizType, item, payload[key]),
      oldValue: row.targetSnapshot ? displayValue(row.bizType, item, snapshot[key]) : null,
    }
  })
})

interface PositionFieldRow {
  label: string
  value: string
}

// 单条任职记录展开成"字段名: 值"列表，字段含义/字典翻译复用 POSITION bizType 的
// 渲染元数据（与任职管理页面新增/编辑表单同一套元数据来源）；orgId 优先展示
// targetSnapshot 里已经带的 orgName（组织名称），requestPayload 里的新增任职记录
// 没有 orgName 只能展示 orgId 本身。ext1~ext10 内容为空时不占位展示，避免大多数
// 未使用扩展字段的任职记录出现一长串空值
function positionFieldRows(position: Record<string, unknown>): PositionFieldRow[] {
  const rows: PositionFieldRow[] = []
  rows.push({
    label: labelFor('POSITION', 'orgId'),
    value: (position.orgName as string) || (position.orgId != null ? String(position.orgId) : '-'),
  })
  for (const key of ['positionType', 'positionAddress', 'positionPhone', 'remark']) {
    rows.push({ label: labelFor('POSITION', key), value: displayValue('POSITION', schemaItemFor('POSITION', key), position[key]) })
  }
  for (let i = 1; i <= 10; i += 1) {
    const key = `ext${i}`
    const raw = position[key]
    if (raw !== undefined && raw !== null && raw !== '') {
      rows.push({ label: labelFor('POSITION', key), value: displayValue('POSITION', schemaItemFor('POSITION', key), raw) })
    }
  }
  return rows
}

interface UserPositionsDisplay {
  hasOld: boolean
  newList: PositionFieldRow[][]
  oldList: PositionFieldRow[][]
}

// USER 新增/编辑申请的 positions 字段（任职记录数组）单独展开成结构化卡片列表，
// 而不是退化成 JSON 文本；UPDATE 申请（targetSnapshot 非空）分"变更前/变更后"两栏
// 分别列出，不逐条按 id 匹配做字段级对照——任职记录本身随用户更新整体新增/替换/
// 删除（design.md），没有稳定的"同一条记录"语义可供逐字段对照
const userPositions = computed<UserPositionsDisplay | null>(() => {
  const row = props.row
  if (!row || row.bizType !== 'USER') return null
  const payload = (row.requestPayload as Record<string, unknown> | null) ?? {}
  const snapshot = row.targetSnapshot as Record<string, unknown> | null
  const newPositions = Array.isArray(payload.positions) ? (payload.positions as Record<string, unknown>[]) : []
  const oldPositions =
    snapshot && Array.isArray(snapshot.positions) ? (snapshot.positions as Record<string, unknown>[]) : null
  if (newPositions.length === 0 && (!oldPositions || oldPositions.length === 0)) return null
  return {
    hasOld: oldPositions !== null,
    newList: newPositions.map(positionFieldRows),
    oldList: (oldPositions ?? []).map(positionFieldRows),
  }
})

function bizTypeLabel(bizType: ApprovalBizType): string {
  return APPROVAL_BIZ_TYPE_OPTIONS.find((opt) => opt.value === bizType)?.label ?? bizType
}

function operationTypeLabel(operationType: ApprovalOperationType): string {
  return APPROVAL_OPERATION_TYPE_OPTIONS.find((opt) => opt.value === operationType)?.label ?? operationType
}

function statusLabel(status: number): string {
  return APPROVAL_STATUS_OPTIONS.find((opt) => opt.value === status)?.label ?? String(status)
}

function statusTagType(status: number): 'warning' | 'success' | 'danger' | 'info' {
  if (status === APPROVAL_STATUS_PENDING) return 'warning'
  if (status === APPROVAL_STATUS_APPROVED) return 'success'
  if (status === APPROVAL_STATUS_REJECTED) return 'danger'
  return 'info'
}
</script>

<template>
  <el-dialog v-model="visible" title="申请详情" width="640px">
    <template v-if="row">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="业务对象类型">{{ bizTypeLabel(row.bizType) }}</el-descriptions-item>
        <el-descriptions-item label="操作类型">{{ operationTypeLabel(row.operationType) }}</el-descriptions-item>
        <el-descriptions-item label="目标记录ID">{{ row.targetId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="生效记录ID">{{ row.resultTargetId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="申请状态">
          <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="提交人">{{ row.createByName || row.createBy }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ row.createTime }}</el-descriptions-item>
        <el-descriptions-item label="审批人">{{ row.approverName || row.approverId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审批时间">{{ row.approveTime ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="审批意见" :span="2">{{ row.opinion || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="fieldRows.length > 0" class="approval-detail-fields">
        <h4 class="approval-detail-fields__title">
          {{ row.targetSnapshot ? '变更内容（旧值 → 新值）' : '申请内容' }}
        </h4>
        <ul class="approval-detail-fields__list">
          <li v-for="item in fieldRows" :key="item.key" class="approval-detail-fields__item">
            <span class="approval-detail-fields__label">{{ item.label }}</span>
            <span v-if="item.oldValue !== null" class="approval-detail-fields__values">
              <span class="approval-detail-fields__old">{{ item.oldValue }}</span>
              <span class="approval-detail-fields__arrow">→</span>
              <span class="approval-detail-fields__new">{{ item.newValue }}</span>
            </span>
            <span v-else class="approval-detail-fields__values">
              <span class="approval-detail-fields__new">{{ item.newValue }}</span>
            </span>
          </li>
        </ul>
      </div>

      <div v-if="userPositions" class="approval-detail-fields approval-detail-positions">
        <h4 class="approval-detail-fields__title">
          任职信息{{ userPositions.hasOld ? '（变更前 → 变更后）' : '' }}
        </h4>
        <div v-if="userPositions.hasOld" class="approval-detail-positions__columns">
          <div class="approval-detail-positions__column">
            <div class="approval-detail-positions__column-title">变更前</div>
            <p v-if="userPositions.oldList.length === 0" class="approval-detail-fields__empty">无</p>
            <div
              v-for="(fields, idx) in userPositions.oldList"
              :key="`old-${idx}`"
              class="approval-detail-position-card"
            >
              <div class="approval-detail-position-card__title">任职 {{ idx + 1 }}</div>
              <div v-for="field in fields" :key="field.label" class="approval-detail-position-card__row">
                <span class="approval-detail-fields__label">{{ field.label }}</span>
                <span>{{ field.value }}</span>
              </div>
            </div>
          </div>
          <div class="approval-detail-positions__column">
            <div class="approval-detail-positions__column-title">变更后</div>
            <p v-if="userPositions.newList.length === 0" class="approval-detail-fields__empty">无</p>
            <div
              v-for="(fields, idx) in userPositions.newList"
              :key="`new-${idx}`"
              class="approval-detail-position-card"
            >
              <div class="approval-detail-position-card__title">任职 {{ idx + 1 }}</div>
              <div v-for="field in fields" :key="field.label" class="approval-detail-position-card__row">
                <span class="approval-detail-fields__label">{{ field.label }}</span>
                <span>{{ field.value }}</span>
              </div>
            </div>
          </div>
        </div>
        <template v-else>
          <p v-if="userPositions.newList.length === 0" class="approval-detail-fields__empty">无</p>
          <div
            v-for="(fields, idx) in userPositions.newList"
            :key="idx"
            class="approval-detail-position-card"
          >
            <div class="approval-detail-position-card__title">任职 {{ idx + 1 }}</div>
            <div v-for="field in fields" :key="field.label" class="approval-detail-position-card__row">
              <span class="approval-detail-fields__label">{{ field.label }}</span>
              <span>{{ field.value }}</span>
            </div>
          </div>
        </template>
      </div>

      <p
        v-if="fieldRows.length === 0 && !userPositions"
        class="approval-detail-fields__empty"
      >
        该操作不涉及字段变更，审批通过后将直接对目标记录执行状态切换/删除
      </p>
    </template>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.approval-detail-fields {
  margin-top: 16px;
}

.approval-detail-fields__title {
  font-size: 13px;
  color: var(--color-ink);
  margin: 0 0 8px;
}

.approval-detail-fields__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.approval-detail-fields__item {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: 13px;
  flex-wrap: wrap;
  padding-bottom: 8px;
  border-bottom: 1px dashed var(--color-border);
}

.approval-detail-fields__item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.approval-detail-fields__label {
  flex-shrink: 0;
  min-width: 84px;
  color: var(--color-text-secondary);
}

.approval-detail-fields__values {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  color: var(--color-ink);
}

.approval-detail-fields__old {
  color: var(--color-text-secondary);
}

.approval-detail-fields__arrow {
  color: var(--color-primary);
}

.approval-detail-fields__new {
  color: var(--color-ink);
  font-weight: 500;
}

.approval-detail-fields__empty {
  margin-top: 16px;
  font-size: 13px;
  color: var(--color-text-tertiary);
}

.approval-detail-positions__columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.approval-detail-positions__column-title {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-bottom: 8px;
}

.approval-detail-position-card {
  border: 1px solid var(--color-border);
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 10px;
  font-size: 13px;
}

.approval-detail-position-card:last-child {
  margin-bottom: 0;
}

.approval-detail-position-card__title {
  font-weight: 500;
  color: var(--color-ink);
  margin-bottom: 6px;
}

.approval-detail-position-card__row {
  display: flex;
  gap: 10px;
  padding: 2px 0;
  color: var(--color-ink);
}
</style>
