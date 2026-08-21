<script setup lang="ts">
// 应用访问授权 - 策略规则子面板：分页列表（含序号列、"待重新执行"提示、启用/停用、
// 执行、删除）+ 新建/编辑弹窗（组织范围/用户属性/请求控制可同时配置，至少一类非空 +
// 显示序号 + 目标应用多选）。组织范围选择器交互抄自 AppConfigView.vue 的"同步范围"区块
// （el-tree-select + 含子组织勾选 + 增删行）。
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Delete, Plus } from '@element-plus/icons-vue'
import * as appAccessApi from '@/api/appAccess'
import * as orgApi from '@/api/org'
import * as appApi from '@/api/app'
import * as metadataFieldApi from '@/api/metadataField'
import { PAGE_SIZE_OPTIONS, DEFAULT_PAGE_SIZE } from '@/constants/pagination'
import {
  POLICY_ATTR_OPERATOR_OPTIONS,
  POLICY_BROWSER_OPTIONS,
  POLICY_STATUS_ENABLED,
  POLICY_STATUS_DISABLED,
  type PolicyBrowserCode,
  type PolicyFormRequest,
  type PolicyOrgScopeFormItem,
  type PolicyUserAttrFormItem,
  type PolicyVO,
} from '@/types/appAccess'
import type { OrgTreeNode } from '@/types/org'
import { METADATA_FIELD_STATUS_ENABLED, type MetadataField } from '@/types/metadataField'
import type { AppRow } from '@/types/app'
import { usePermission } from '@/composables/usePermission'

const { hasPermission } = usePermission()

// ---- 列表 + 筛选 + 分页 ----

const filters = reactive({
  name: '',
  status: undefined as number | undefined,
})

const list = ref<PolicyVO[]>([])
const listLoading = ref(false)
const page = ref(1)
const pageSize = ref(DEFAULT_PAGE_SIZE)
const total = ref(0)

async function fetchList() {
  listLoading.value = true
  try {
    const result = await appAccessApi.getPolicyPage({
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

// ---- 弹窗内选择器数据源（组织树/用户属性字段/目标应用，按需在打开弹窗时加载一次） ----

const orgTree = ref<OrgTreeNode[]>([])
const metadataFieldOptions = ref<MetadataField[]>([])
const appOptions = ref<AppRow[]>([])
const selectorsLoaded = ref(false)

async function ensureSelectorsLoaded() {
  if (selectorsLoaded.value) return
  const [orgTreeData, fieldPage, appPage] = await Promise.all([
    orgApi.getOrgTree(),
    metadataFieldApi.getMetadataFieldPageForSyncDomain('USER'),
    appApi.getAppPage(1, 200),
  ])
  orgTree.value = orgTreeData
  metadataFieldOptions.value = fieldPage.records.filter((field) => field.status === METADATA_FIELD_STATUS_ENABLED)
  appOptions.value = appPage.records
  selectorsLoaded.value = true
}

// ---- 新建/编辑弹窗 ----

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

interface PolicyForm {
  name: string
  remark: string
  orgScopes: PolicyOrgScopeFormItem[]
  userAttrs: PolicyUserAttrFormItem[]
  // 请求控制条件：浏览器白名单（多选编码）+ IP/网段白名单（可增删的字符串行）
  browserRules: PolicyBrowserCode[]
  ipRules: string[]
  targetAppIds: number[]
  // 显示序号：数值越小优先级越高，运行时按升序取第一条命中身份的策略计算结果
  showOrder: number
}

const form = reactive<PolicyForm>({
  name: '',
  remark: '',
  orgScopes: [],
  userAttrs: [],
  browserRules: [],
  ipRules: [],
  targetAppIds: [],
  showOrder: 0,
})

const rules: FormRules<PolicyForm> = {
  name: [{ required: true, message: '请输入策略名称', trigger: 'blur' }],
  targetAppIds: [{ required: true, type: 'array', min: 1, message: '请至少选择一个目标应用', trigger: 'change' }],
}

const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增策略' : '编辑策略'))

function resetForm() {
  form.name = ''
  form.remark = ''
  form.orgScopes = []
  form.userAttrs = []
  form.browserRules = []
  form.ipRules = []
  form.targetAppIds = []
  form.showOrder = 0
}

async function openCreateDialog() {
  dialogMode.value = 'create'
  editingId.value = null
  resetForm()
  await ensureSelectorsLoaded()
  dialogVisible.value = true
}

// 用户属性回显：EQ/NE 时把唯一值填入 singleValue，IN 时把整组值填入 multiValues
function toUserAttrFormItem(attr: PolicyVO['userAttrs'][number]): PolicyUserAttrFormItem {
  return {
    metadataFieldId: attr.metadataFieldId,
    operator: attr.operator,
    singleValue: attr.operator === 'IN' ? '' : (attr.values[0] ?? ''),
    multiValues: attr.operator === 'IN' ? [...attr.values] : [],
  }
}

async function openEditDialog(row: PolicyVO) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  await ensureSelectorsLoaded()
  const detail = await appAccessApi.getPolicyById(row.id)
  form.name = detail.name
  form.remark = detail.remark
  form.orgScopes = detail.orgScopes.map((scope) => ({ orgId: scope.orgId, includeChildren: scope.includeChildren }))
  form.userAttrs = detail.userAttrs.map(toUserAttrFormItem)
  form.browserRules = detail.browserRules.map((rule) => rule.browserCode)
  form.ipRules = detail.ipRules.map((rule) => rule.ipCidr)
  form.targetAppIds = detail.targetApps.map((app) => app.appId)
  form.showOrder = detail.showOrder
  dialogVisible.value = true
}

function closeDialog() {
  dialogVisible.value = false
  formRef.value?.clearValidate()
}

// ---- 组织范围子表单：增删行 ----

function addOrgScopeRow() {
  form.orgScopes.push({ orgId: null, includeChildren: false })
}

function removeOrgScopeRow(index: number) {
  form.orgScopes.splice(index, 1)
}

// ---- 用户属性子表单：增删行、增删多值 ----

function addUserAttrRow() {
  form.userAttrs.push({ metadataFieldId: null, operator: 'EQ', singleValue: '', multiValues: [] })
}

function removeUserAttrRow(index: number) {
  form.userAttrs.splice(index, 1)
}

// 运算符切换为"属于多值"时，若尚无任何值行则补一个空行，方便用户直接输入
function handleOperatorChange(attr: PolicyUserAttrFormItem) {
  if (attr.operator === 'IN' && attr.multiValues.length === 0) {
    attr.multiValues.push('')
  }
}

function addMultiValue(attr: PolicyUserAttrFormItem) {
  attr.multiValues.push('')
}

function removeMultiValue(attr: PolicyUserAttrFormItem, index: number) {
  attr.multiValues.splice(index, 1)
}

// ---- 请求控制条件子表单：IP/网段白名单增删行（浏览器白名单直接用多选，无需增删） ----

function addIpRuleRow() {
  form.ipRules.push('')
}

function removeIpRuleRow(index: number) {
  form.ipRules.splice(index, 1)
}

// ---- 提交前校验：组织范围/用户属性/请求控制可以同时配置，三者中至少一类非空，
// 且已填写的每一类内部行数据要合法 ----
// （组织范围/用户属性/请求控制的动态行无法用 el-form rules 声明，单独校验）

function validateConditions(): string {
  if (form.orgScopes.some((scope) => scope.orgId === null)) {
    return '存在未选择组织的组织范围行，请补全或删除'
  }

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

  if (form.ipRules.some((ip) => !ip.trim())) {
    return '存在未填写的 IP/网段白名单行，请补全或删除'
  }
  const filledIpRules = form.ipRules.map((ip) => ip.trim()).filter((ip) => ip)

  const hasOrgScope = form.orgScopes.length > 0
  const hasUserAttr = form.userAttrs.length > 0
  const hasRequestControl = form.browserRules.length > 0 || filledIpRules.length > 0
  if (!hasOrgScope && !hasUserAttr && !hasRequestControl) {
    return '组织范围、用户属性、请求控制条件不能同时为空，请至少配置一类'
  }
  return ''
}

function toSubmitPayload(): PolicyFormRequest {
  return {
    name: form.name.trim(),
    remark: form.remark.trim(),
    orgScopes: form.orgScopes.map((scope) => ({ orgId: scope.orgId as number, includeChildren: scope.includeChildren })),
    userAttrs: form.userAttrs.map((attr) => ({
      metadataFieldId: attr.metadataFieldId as number,
      operator: attr.operator,
      values: attr.operator === 'IN' ? attr.multiValues.filter((v) => v.trim()) : [attr.singleValue.trim()],
    })),
    browserRules: [...form.browserRules],
    ipRules: form.ipRules.map((ip) => ip.trim()).filter((ip) => ip),
    targetAppIds: form.targetAppIds,
    showOrder: form.showOrder,
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
      await appAccessApi.createPolicy(payload)
      ElMessage.success('新增成功')
    } else {
      await appAccessApi.updatePolicy(editingId.value as number, payload)
      ElMessage.success('保存成功')
    }
    dialogVisible.value = false
    await fetchList()
  } finally {
    submitting.value = false
  }
}

// ---- 行操作：启用/停用、执行、删除 ----

async function toggleStatus(row: PolicyVO) {
  if (row.status === POLICY_STATUS_ENABLED) {
    await appAccessApi.disablePolicy(row.id)
    ElMessage.success('已停用')
  } else {
    await appAccessApi.enablePolicy(row.id)
    ElMessage.success('已启用')
  }
  await fetchList()
}

const executingId = ref<number | null>(null)

async function handleExecute(row: PolicyVO) {
  await ElMessageBox.confirm(
    `执行后将按当前配置重新计算命中用户，整体重建策略「${row.name}」产生的授权记录（不影响人工例外），确定要执行吗？`,
    '执行确认',
    { type: 'warning', confirmButtonText: '执行', cancelButtonText: '取消' },
  )
  executingId.value = row.id
  try {
    await appAccessApi.executePolicy(row.id)
    ElMessage.success('执行成功')
    await fetchList()
  } finally {
    executingId.value = null
  }
}

async function handleDelete(row: PolicyVO) {
  await ElMessageBox.confirm(
    `确定要删除策略「${row.name}」吗？该策略已产生的全部策略授权记录将被一并删除。`,
    '删除确认',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
  )
  await appAccessApi.deletePolicy(row.id)
  ElMessage.success('删除成功')
  await fetchList()
}
</script>

<template>
  <div class="policy-panel">
    <header class="policy-panel__header">
      <h3 class="policy-panel__title">策略规则</h3>
      <el-button v-if="hasPermission('AppAccessManagement:policy:add')" type="primary" @click="openCreateDialog">
        新增策略
      </el-button>
    </header>

    <el-form class="policy-filter-form" inline @submit.prevent>
      <el-form-item label="名称">
        <el-input v-model="filters.name" placeholder="请输入策略名称" clearable style="width: 200px" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 140px">
          <el-option label="启用" :value="POLICY_STATUS_ENABLED" />
          <el-option label="停用" :value="POLICY_STATUS_DISABLED" />
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
          <el-tag :type="(row as PolicyVO).status === POLICY_STATUS_ENABLED ? 'success' : 'info'">
            {{ (row as PolicyVO).status === POLICY_STATUS_ENABLED ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="待重新执行" width="160">
        <template #default="{ row }">
          <el-tag v-if="(row as PolicyVO).pendingReExecute" type="warning">配置已变更，待重新执行</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="最近执行" min-width="200">
        <template #default="{ row }">
          <span v-if="(row as PolicyVO).lastExecTime">
            {{ (row as PolicyVO).lastExecTime }} · {{ (row as PolicyVO).lastExecBy }}
          </span>
          <span v-else>未执行</span>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="hasPermission('AppAccessManagement:policy:edit')"
            link
            type="primary"
            @click="openEditDialog(row as PolicyVO)"
          >
            编辑
          </el-button>
          <el-button
            v-if="
              (row as PolicyVO).status === POLICY_STATUS_ENABLED
                ? hasPermission('AppAccessManagement:policy:disable')
                : hasPermission('AppAccessManagement:policy:enable')
            "
            link
            :type="(row as PolicyVO).status === POLICY_STATUS_ENABLED ? 'warning' : 'success'"
            @click="toggleStatus(row as PolicyVO)"
          >
            {{ (row as PolicyVO).status === POLICY_STATUS_ENABLED ? '停用' : '启用' }}
          </el-button>
          <el-button
            v-if="hasPermission('AppAccessManagement:policy:execute')"
            link
            type="primary"
            :loading="executingId === (row as PolicyVO).id"
            @click="handleExecute(row as PolicyVO)"
          >
            执行
          </el-button>
          <el-button
            v-if="hasPermission('AppAccessManagement:policy:delete')"
            link
            type="danger"
            @click="handleDelete(row as PolicyVO)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="policy-pagination"
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
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="策略名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入策略名称" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
        <el-form-item label="显示序号" prop="showOrder">
          <el-input-number v-model="form.showOrder" :min="0" :max="99999" controls-position="right" style="width: 160px" />
          <span class="policy-field-hint">数值越小优先级越高，运行时按升序取第一条命中身份的策略计算结果；相同序号按创建顺序排列</span>
        </el-form-item>

        <el-form-item label="组织范围">
          <div class="policy-condition-block">
            <el-button link type="primary" :icon="Plus" @click="addOrgScopeRow">添加组织</el-button>
            <p v-if="form.orgScopes.length === 0" class="policy-condition-empty">未配置组织范围</p>
            <div v-else class="policy-condition-list">
              <div v-for="(scope, index) in form.orgScopes" :key="index" class="policy-condition-row">
                <el-tree-select
                  v-model="scope.orgId"
                  :data="orgTree"
                  :props="{ label: 'name', children: 'children' }"
                  node-key="id"
                  check-strictly
                  placeholder="请选择组织"
                  style="flex: 1"
                />
                <el-checkbox v-model="scope.includeChildren">含子组织</el-checkbox>
                <el-button link :icon="Delete" type="danger" @click="removeOrgScopeRow(index)" />
              </div>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="用户属性">
          <div class="policy-condition-block">
            <el-button link type="primary" :icon="Plus" @click="addUserAttrRow">添加条件</el-button>
            <p v-if="form.userAttrs.length === 0" class="policy-condition-empty">未配置用户属性</p>
            <div v-else class="policy-condition-list">
              <div v-for="(attr, index) in form.userAttrs" :key="index" class="policy-condition-row policy-condition-row--attr">
                <el-select v-model="attr.metadataFieldId" placeholder="选择字段" style="width: 170px">
                  <el-option
                    v-for="field in metadataFieldOptions"
                    :key="field.id"
                    :label="`${field.fieldName}（${field.fieldCode}）`"
                    :value="field.id"
                  />
                </el-select>
                <el-select v-model="attr.operator" style="width: 110px" @change="handleOperatorChange(attr)">
                  <el-option v-for="opt in POLICY_ATTR_OPERATOR_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
                </el-select>
                <div v-if="attr.operator === 'IN'" class="policy-condition-values">
                  <div v-for="(_, vi) in attr.multiValues" :key="vi" class="policy-condition-value-row">
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

        <el-form-item label="请求控制">
          <div class="policy-condition-block">
            <p class="policy-condition-hint">
              浏览器白名单、IP/网段白名单二者至少配置一项；两者内部均可只配置其中一个。
            </p>
            <div class="policy-request-control-row">
              <span class="policy-request-control-label">浏览器白名单</span>
              <el-checkbox-group v-model="form.browserRules">
                <el-checkbox v-for="opt in POLICY_BROWSER_OPTIONS" :key="opt.value" :value="opt.value">
                  {{ opt.label }}
                </el-checkbox>
              </el-checkbox-group>
              <p v-if="form.browserRules.length === 0" class="policy-condition-empty">未配置浏览器白名单</p>
            </div>
            <div class="policy-request-control-row">
              <span class="policy-request-control-label">IP/网段白名单</span>
              <el-button link type="primary" :icon="Plus" @click="addIpRuleRow">添加 IP/网段</el-button>
              <p v-if="form.ipRules.length === 0" class="policy-condition-empty">未配置 IP/网段白名单</p>
              <div v-else class="policy-condition-list">
                <div v-for="(_, index) in form.ipRules" :key="index" class="policy-condition-row">
                  <el-input v-model="form.ipRules[index]" placeholder="如 192.168.1.0/24 或 10.0.0.5" style="flex: 1" />
                  <el-button link :icon="Delete" type="danger" @click="removeIpRuleRow(index)" />
                </div>
              </div>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="目标应用" prop="targetAppIds">
          <el-select v-model="form.targetAppIds" multiple filterable placeholder="请选择目标应用（至少一个）" style="width: 100%">
            <el-option v-for="app in appOptions" :key="app.id" :label="app.name" :value="app.id" />
          </el-select>
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
.policy-panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

.policy-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.policy-panel__title {
  font-size: 15px;
  color: var(--color-ink);
  margin: 0;
}

.policy-filter-form {
  margin-bottom: 8px;

  :deep(.el-form-item) {
    margin-bottom: 12px;
  }
}

.policy-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.policy-condition-block {
  width: 100%;
}

.policy-condition-empty {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.policy-condition-hint {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.policy-field-hint {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.policy-request-control-row {
  display: flex;
  flex-direction: column;
  gap: 6px;

  & + & {
    margin-top: 14px;
  }
}

.policy-request-control-label {
  font-size: 12px;
  color: var(--color-ink);
}

.policy-condition-list {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.policy-condition-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.policy-condition-row--attr {
  align-items: flex-start;
}

.policy-condition-values {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.policy-condition-value-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

:deep(.el-table .el-button + .el-button) {
  margin-left: 6px;
}
</style>
