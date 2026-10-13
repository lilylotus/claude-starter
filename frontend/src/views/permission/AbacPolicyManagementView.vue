<script setup lang="ts">
// ABAC 策略管理：单页列表（名称/备注/启用状态/显示序号/目标资源编码/操作列）+ 新增/
// 编辑弹窗（目标资源编码多选 + 用户属性条件动态表单 + 时间窗口动态表单）+ 启用/停用/
// 删除交互，交互模式参照 views/permission/app-access/PolicyRulePanel.vue（同样是策略
// 规则 CRUD + 用户属性条件动态表单），但本页面的策略模型更简单——只有目标资源编码+
// 用户属性条件+时间窗口，不需要组织范围/请求控制条件/执行按钮那些板块，因此不复用该
// 组件本身，而是在本页面内按同一套交互风格重新实现一份更简洁的版本。
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Delete, Plus } from '@element-plus/icons-vue'
import * as abacPolicyApi from '@/api/abacPolicy'
import * as metadataFieldApi from '@/api/metadataField'
import * as permissionApi from '@/api/permission'
import { PAGE_SIZE_OPTIONS, DEFAULT_PAGE_SIZE } from '@/constants/pagination'
import {
  ABAC_ATTR_OPERATOR_OPTIONS,
  ABAC_DAY_OF_WEEK_OPTIONS,
  ABAC_POLICY_STATUS_DISABLED,
  ABAC_POLICY_STATUS_ENABLED,
  type AbacPolicyFormRequest,
  type AbacPolicyTimeWindowFormItem,
  type AbacPolicyUserAttrFormItem,
  type AbacPolicyVO,
} from '@/types/abacPolicy'
import { METADATA_FIELD_STATUS_ENABLED, type MetadataField } from '@/types/metadataField'
import { PERMISSION_STATUS_ENABLED, type PermissionRow } from '@/types/permission'
import { usePermission } from '@/composables/usePermission'

const { hasPermission } = usePermission()

// ---- 列表 + 筛选 + 分页 ----

const filters = reactive({
  name: '',
  status: undefined as number | undefined,
})

const list = ref<AbacPolicyVO[]>([])
const listLoading = ref(false)
const page = ref(1)
const pageSize = ref(DEFAULT_PAGE_SIZE)
const total = ref(0)

async function fetchList() {
  listLoading.value = true
  try {
    const result = await abacPolicyApi.getAbacPolicyPage({
      name: filters.name.trim() || undefined,
      status: filters.status,
      page: page.value,
      pageSize: pageSize.value,
    })
    list.value = result.records
    total.value = result.total
    page.value = result.page
    pageSize.value = result.pageSize
  } finally {
    listLoading.value = false
  }
}

onMounted(() => {
  fetchList()
})

function handleSearch() {
  page.value = 1
  fetchList()
}

function handleReset() {
  filters.name = ''
  filters.status = undefined
  page.value = 1
  fetchList()
}

function handlePageChange(targetPage: number) {
  page.value = targetPage
  fetchList()
}

function handleSizeChange(newSize: number) {
  pageSize.value = newSize
  page.value = 1
  fetchList()
}

// ---- 弹窗内选择器数据源（权限点列表/用户域元数据字段，按需在打开弹窗时加载一次） ----

const permissionOptions = ref<PermissionRow[]>([])
const metadataFieldOptions = ref<MetadataField[]>([])
const selectorsLoaded = ref(false)

async function ensureSelectorsLoaded() {
  if (selectorsLoaded.value) return
  const [permissionList, fieldPage] = await Promise.all([
    permissionApi.getPermissionList(),
    metadataFieldApi.getMetadataFieldPageForSyncDomain('USER'),
  ])
  // 目标资源编码只从当前启用中的权限点选取，停用的权限点不应再被新策略绑定
  permissionOptions.value = permissionList.filter((item) => item.status === PERMISSION_STATUS_ENABLED)
  metadataFieldOptions.value = fieldPage.records.filter((field) => field.status === METADATA_FIELD_STATUS_ENABLED)
  selectorsLoaded.value = true
}

// ---- 新建/编辑弹窗 ----

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

interface AbacPolicyForm {
  name: string
  remark: string
  showOrder: number
  resourceCodes: string[]
  userAttrs: AbacPolicyUserAttrFormItem[]
  timeWindows: AbacPolicyTimeWindowFormItem[]
}

const form = reactive<AbacPolicyForm>({
  name: '',
  remark: '',
  showOrder: 0,
  resourceCodes: [],
  userAttrs: [],
  timeWindows: [],
})

const rules: FormRules<AbacPolicyForm> = {
  name: [{ required: true, message: '请输入策略名称', trigger: 'blur' }],
  resourceCodes: [{ required: true, type: 'array', min: 1, message: '请至少选择一个目标资源编码', trigger: 'change' }],
}

const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增策略' : '编辑策略'))

function resetForm() {
  form.name = ''
  form.remark = ''
  form.showOrder = 0
  form.resourceCodes = []
  form.userAttrs = []
  form.timeWindows = []
}

async function openCreateDialog() {
  dialogMode.value = 'create'
  editingId.value = null
  resetForm()
  await ensureSelectorsLoaded()
  dialogVisible.value = true
}

// 用户属性回显：EQ/NE 时把唯一值填入 singleValue，IN 时把整组值填入 multiValues
function toUserAttrFormItem(attr: AbacPolicyVO['userAttrs'][number]): AbacPolicyUserAttrFormItem {
  return {
    metadataFieldId: attr.metadataFieldId,
    operator: attr.operator,
    singleValue: attr.operator === 'IN' ? '' : (attr.values[0] ?? ''),
    multiValues: attr.operator === 'IN' ? [...attr.values] : [],
  }
}

function toTimeWindowFormItem(window: AbacPolicyVO['timeWindows'][number]): AbacPolicyTimeWindowFormItem {
  return {
    dayOfWeek: window.dayOfWeek ?? null,
    startTime: window.startTime,
    endTime: window.endTime,
  }
}

async function openEditDialog(row: AbacPolicyVO) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  await ensureSelectorsLoaded()
  const detail = await abacPolicyApi.getAbacPolicyById(row.id)
  form.name = detail.name
  form.remark = detail.remark
  form.showOrder = detail.showOrder
  form.resourceCodes = [...detail.resourceCodes]
  form.userAttrs = detail.userAttrs.map(toUserAttrFormItem)
  form.timeWindows = detail.timeWindows.map(toTimeWindowFormItem)
  dialogVisible.value = true
}

function closeDialog() {
  dialogVisible.value = false
  formRef.value?.clearValidate()
}

// ---- 用户属性子表单：增删行、增删多值 ----

function addUserAttrRow() {
  form.userAttrs.push({ metadataFieldId: null, operator: 'EQ', singleValue: '', multiValues: [] })
}

function removeUserAttrRow(index: number) {
  form.userAttrs.splice(index, 1)
}

// 运算符切换为"属于多值"时，若尚无任何值行则补一个空行，方便用户直接输入
function handleOperatorChange(attr: AbacPolicyUserAttrFormItem) {
  if (attr.operator === 'IN' && attr.multiValues.length === 0) {
    attr.multiValues.push('')
  }
}

function addMultiValue(attr: AbacPolicyUserAttrFormItem) {
  attr.multiValues.push('')
}

function removeMultiValue(attr: AbacPolicyUserAttrFormItem, index: number) {
  attr.multiValues.splice(index, 1)
}

// ---- 时间窗口子表单：增删行 ----
// 一行 = 一个可选星期（不选代表每天均适用）+ 一段起止时间；需要"周一至周五"这类多天
// 共用同一段时间时，逐天各加一行即可（与后端表结构 dayOfWeek 单值对齐，design.md
// Decision 4），同一策略内多条时间窗口之间取 OR 语义。

function addTimeWindowRow() {
  form.timeWindows.push({ dayOfWeek: null, startTime: '', endTime: '' })
}

function removeTimeWindowRow(index: number) {
  form.timeWindows.splice(index, 1)
}

// ---- 提交前校验：用户属性条件、时间窗口条件两者中至少配置一类，且已填写的每一类
// 内部行数据要合法（动态行无法用 el-form rules 声明，单独校验）----

function validateConditions(): string {
  if (form.userAttrs.some((attr) => attr.metadataFieldId === null)) {
    return '存在未选择字段的用户属性行，请补全或删除'
  }
  for (const attr of form.userAttrs) {
    if (attr.operator === 'IN') {
      if (attr.multiValues.filter((v) => v.trim()).length === 0) {
        return '用户属性"属于多值"运算符至少需要一个比较值'
      }
    } else if (!attr.singleValue.trim()) {
      return '用户属性的比较值不能为空'
    }
  }
  const fieldIds = form.userAttrs.map((attr) => attr.metadataFieldId)
  if (new Set(fieldIds).size !== fieldIds.length) {
    return '同一策略内不允许重复配置同一个用户属性字段'
  }

  for (const windowItem of form.timeWindows) {
    if (!windowItem.startTime || !windowItem.endTime) {
      return '存在未填写起止时间的时间窗口行，请补全或删除'
    }
    if (windowItem.endTime <= windowItem.startTime) {
      return '时间窗口的结束时间必须晚于起始时间'
    }
  }

  const hasUserAttr = form.userAttrs.length > 0
  const hasTimeWindow = form.timeWindows.length > 0
  if (!hasUserAttr && !hasTimeWindow) {
    return '用户属性条件、时间窗口条件不能同时为空，请至少配置一类'
  }
  return ''
}

function toSubmitPayload(): AbacPolicyFormRequest {
  return {
    name: form.name.trim(),
    remark: form.remark.trim(),
    showOrder: form.showOrder,
    resourceCodes: [...form.resourceCodes],
    userAttrs: form.userAttrs.map((attr) => ({
      metadataFieldId: attr.metadataFieldId as number,
      operator: attr.operator,
      values: attr.operator === 'IN' ? attr.multiValues.filter((v) => v.trim()) : [attr.singleValue.trim()],
    })),
    timeWindows: form.timeWindows.map((windowItem) => ({
      dayOfWeek: windowItem.dayOfWeek ?? undefined,
      startTime: windowItem.startTime,
      endTime: windowItem.endTime,
    })),
  }
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  const conditionError = validateConditions()
  if (conditionError) {
    ElMessage.error(conditionError)
    return
  }

  submitting.value = true
  try {
    const payload = toSubmitPayload()
    if (dialogMode.value === 'create') {
      await abacPolicyApi.createAbacPolicy(payload)
      ElMessage.success('新增成功')
    } else {
      await abacPolicyApi.updateAbacPolicy(editingId.value as number, payload)
      ElMessage.success('保存成功')
    }
    dialogVisible.value = false
    await fetchList()
  } finally {
    submitting.value = false
  }
}

// ---- 行操作：启用/停用、删除 ----

async function toggleStatus(row: AbacPolicyVO) {
  if (row.status === ABAC_POLICY_STATUS_ENABLED) {
    await abacPolicyApi.disableAbacPolicy(row.id)
    ElMessage.success('已停用')
  } else {
    await abacPolicyApi.enableAbacPolicy(row.id)
    ElMessage.success('已启用')
  }
  await fetchList()
}

async function handleDelete(row: AbacPolicyVO) {
  await ElMessageBox.confirm(`确定要删除策略「${row.name}」吗？该操作不可恢复。`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await abacPolicyApi.deleteAbacPolicy(row.id)
  ElMessage.success('删除成功')
  await fetchList()
}
</script>

<template>
  <div class="abac-policy-view">
    <header class="abac-policy-view__header">
      <div>
        <h2 class="abac-policy-view__title">ABAC 策略管理</h2>
        <p class="abac-policy-view__desc">
          在 RBAC 权限点校验通过之后，按用户属性与时间窗口进一步收紧对指定资源的访问；命中策略即拒绝访问，未命中任何策略的资源行为不受影响。
        </p>
      </div>
      <el-button v-if="hasPermission('AbacManagement:policy:add')" type="primary" @click="openCreateDialog">
        新增策略
      </el-button>
    </header>

    <el-form class="abac-policy-filter-form" inline @submit.prevent>
      <el-form-item label="名称">
        <el-input v-model="filters.name" placeholder="请输入策略名称" clearable style="width: 200px" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 140px">
          <el-option label="启用" :value="ABAC_POLICY_STATUS_ENABLED" />
          <el-option label="停用" :value="ABAC_POLICY_STATUS_DISABLED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="listLoading" :data="list" empty-text="暂无策略规则">
      <el-table-column prop="showOrder" label="序号" width="80" />
      <el-table-column prop="name" label="策略名称" min-width="150" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="(row as AbacPolicyVO).status === ABAC_POLICY_STATUS_ENABLED ? 'success' : 'info'">
            {{ (row as AbacPolicyVO).status === ABAC_POLICY_STATUS_ENABLED ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="目标资源编码" min-width="220">
        <template #default="{ row }">
          <el-tag v-for="code in (row as AbacPolicyVO).resourceCodes" :key="code" class="abac-policy-resource-tag" size="small">
            {{ code }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="hasPermission('AbacManagement:policy:edit')"
            link
            type="primary"
            @click="openEditDialog(row as AbacPolicyVO)"
          >
            编辑
          </el-button>
          <el-button
            v-if="
              (row as AbacPolicyVO).status === ABAC_POLICY_STATUS_ENABLED
                ? hasPermission('AbacManagement:policy:disable')
                : hasPermission('AbacManagement:policy:enable')
            "
            link
            :type="(row as AbacPolicyVO).status === ABAC_POLICY_STATUS_ENABLED ? 'warning' : 'success'"
            @click="toggleStatus(row as AbacPolicyVO)"
          >
            {{ (row as AbacPolicyVO).status === ABAC_POLICY_STATUS_ENABLED ? '停用' : '启用' }}
          </el-button>
          <el-button
            v-if="hasPermission('AbacManagement:policy:delete')"
            link
            type="danger"
            @click="handleDelete(row as AbacPolicyVO)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="abac-policy-pagination"
      background
      layout="sizes, prev, pager, next, total"
      :page-sizes="[...PAGE_SIZE_OPTIONS]"
      :current-page="page"
      :page-size="pageSize"
      :total="total"
      @current-change="handlePageChange"
      @size-change="handleSizeChange"
    />

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="760px" @close="closeDialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="策略名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入策略名称" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
        <el-form-item label="显示序号" prop="showOrder">
          <el-input-number v-model="form.showOrder" :min="0" :max="99999" controls-position="right" style="width: 160px" />
          <span class="abac-policy-field-hint">仅用于列表展示排序，不参与判定优先级</span>
        </el-form-item>

        <el-form-item label="目标资源编码" prop="resourceCodes">
          <el-select v-model="form.resourceCodes" multiple filterable placeholder="请选择目标资源编码（至少一个）" style="width: 100%">
            <el-option
              v-for="permissionItem in permissionOptions"
              :key="permissionItem.id"
              :label="`${permissionItem.name}（${permissionItem.code}）`"
              :value="permissionItem.code"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="用户属性条件">
          <div class="abac-policy-condition-block">
            <el-button link type="primary" :icon="Plus" @click="addUserAttrRow">添加条件</el-button>
            <p v-if="form.userAttrs.length === 0" class="abac-policy-condition-empty">未配置用户属性条件</p>
            <div v-else class="abac-policy-condition-list">
              <div v-for="(attr, index) in form.userAttrs" :key="index" class="abac-policy-condition-row abac-policy-condition-row--attr">
                <el-select v-model="attr.metadataFieldId" placeholder="选择字段" style="width: 170px">
                  <el-option
                    v-for="field in metadataFieldOptions"
                    :key="field.id"
                    :label="`${field.fieldName}（${field.fieldCode}）`"
                    :value="field.id"
                  />
                </el-select>
                <el-select v-model="attr.operator" style="width: 110px" @change="handleOperatorChange(attr)">
                  <el-option v-for="opt in ABAC_ATTR_OPERATOR_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
                </el-select>
                <div v-if="attr.operator === 'IN'" class="abac-policy-condition-values">
                  <div v-for="(_, vi) in attr.multiValues" :key="vi" class="abac-policy-condition-value-row">
                    <el-input v-model="attr.multiValues[vi]" placeholder="比较值" style="width: 110px" />
                    <el-button link :icon="Delete" type="danger" @click="removeMultiValue(attr, vi)" />
                  </div>
                  <el-button link type="primary" :icon="Plus" @click="addMultiValue(attr)">添加值</el-button>
                </div>
                <el-input v-else v-model="attr.singleValue" placeholder="比较值" style="width: 140px" />
                <el-button link :icon="Delete" type="danger" @click="removeUserAttrRow(index)" />
              </div>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="时间窗口条件">
          <div class="abac-policy-condition-block">
            <p class="abac-policy-condition-hint">
              不选择星期代表每天均适用；需要覆盖多天共用同一段时间时，逐天各添加一行；全天应填
              00:00:00 ~ 23:59:59，结束时间必须晚于起始时间。
            </p>
            <el-button link type="primary" :icon="Plus" @click="addTimeWindowRow">添加时间窗口</el-button>
            <p v-if="form.timeWindows.length === 0" class="abac-policy-condition-empty">未配置时间窗口条件</p>
            <div v-else class="abac-policy-condition-list">
              <div v-for="(windowItem, index) in form.timeWindows" :key="index" class="abac-policy-condition-row">
                <el-select v-model="windowItem.dayOfWeek" clearable placeholder="每天" style="width: 110px">
                  <el-option v-for="opt in ABAC_DAY_OF_WEEK_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
                </el-select>
                <el-time-picker
                  v-model="windowItem.startTime"
                  value-format="HH:mm:ss"
                  placeholder="起始时间"
                  style="width: 140px"
                />
                <span class="abac-policy-time-separator">至</span>
                <el-time-picker
                  v-model="windowItem.endTime"
                  value-format="HH:mm:ss"
                  placeholder="结束时间"
                  style="width: 140px"
                />
                <el-button link :icon="Delete" type="danger" @click="removeTimeWindowRow(index)" />
              </div>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.abac-policy-view {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

.abac-policy-view__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.abac-policy-view__title {
  font-size: 18px;
  color: var(--color-ink);
  margin: 0 0 6px;
}

.abac-policy-view__desc {
  font-size: 13px;
  color: var(--color-text-tertiary);
  margin: 0;
  max-width: 640px;
}

.abac-policy-filter-form {
  margin-bottom: 8px;

  :deep(.el-form-item) {
    margin-bottom: 12px;
  }
}

.abac-policy-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.abac-policy-resource-tag {
  margin: 2px 4px 2px 0;
}

.abac-policy-condition-block {
  width: 100%;
}

.abac-policy-condition-empty {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.abac-policy-condition-hint {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.abac-policy-field-hint {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.abac-policy-condition-list {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.abac-policy-condition-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.abac-policy-condition-row--attr {
  align-items: flex-start;
}

.abac-policy-condition-values {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.abac-policy-condition-value-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.abac-policy-time-separator {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

:deep(.el-table .el-button + .el-button) {
  margin-left: 6px;
}
</style>
