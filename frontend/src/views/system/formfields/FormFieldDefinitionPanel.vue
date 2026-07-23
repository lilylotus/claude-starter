<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import * as formFieldApi from '@/api/formField'
import * as metadataFieldApi from '@/api/metadataField'
import * as dictApi from '@/api/dict'
import { DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS } from '@/constants/pagination'
import {
  FORM_FIELD_BIZ_TYPE_OPTIONS,
  type FormFieldBizType,
  type MetadataField,
} from '@/types/metadataField'
import {
  FORM_FIELD_CONTROL_TYPE_DICT,
  FORM_FIELD_CONTROL_TYPE_MULTI_DICT,
  FORM_FIELD_CONTROL_TYPE_OPTIONS,
  FORM_FIELD_STATUS_ENABLED,
  type FormFieldDefinition,
} from '@/types/formField'
import { DICT_STATUS_ENABLED, type DictTypeRow } from '@/types/dict'

// 表单管理页面"字段定义"tab（由 FormFieldListView.vue 承载外层 字段定义/导入模板配置
// 两个 tab，本组件只负责字段定义这一个）：按业务对象类型（组织/人员/任职/应用）切换
// 查看字段定义列表；新增/编辑均可从"元数据配置"目录选择数据字段——新增时只能选尚未
// 绑定的，编辑时下拉框额外包含当前绑定项；字段标识完全派生自所选数据字段的当前字段
// 标识，展示为禁用输入框，不可手动编辑，切换数据字段会实时联动更新；绑定承重字段
// （name/code）的定义数据字段选择器禁用（不可改绑），也不提供停用/删除入口，编辑弹窗
// 对应受限属性渲染为禁用态
// （见 openspec/changes/form-field-metadata-enhancements/specs/form-field-definition-management）。

const activeBizType = ref<FormFieldBizType>('ORG')

const list = ref<FormFieldDefinition[]>([])
const listLoading = ref(false)
const page = ref(1)
const pageSize = ref(DEFAULT_PAGE_SIZE)
const total = ref(0)

async function fetchPage(targetPage?: number) {
  if (targetPage !== undefined) page.value = targetPage
  listLoading.value = true
  try {
    const result = await formFieldApi.getFormFieldPage({
      bizType: activeBizType.value,
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
  fetchPage(1)
})

function handleTabChange() {
  fetchPage(1)
}

function handlePageChange(targetPage: number) {
  fetchPage(targetPage)
}

function handleSizeChange(newSize: number) {
  pageSize.value = newSize
  page.value = 1
  fetchPage()
}

function controlTypeLabel(controlType: number): string {
  return FORM_FIELD_CONTROL_TYPE_OPTIONS.find((opt) => opt.value === controlType)?.label ?? String(controlType)
}

// ---- 字典类型下拉框数据源（控件类型选择"字典下拉"时使用）----
// 字典模块没有现成的"查询全部字典类型（不分页）"接口，用大 pageSize 简单处理

const dictTypeOptions = ref<DictTypeRow[]>([])

async function fetchDictTypeOptions() {
  const result = await dictApi.getDictTypePage({ pageSize: 200 })
  dictTypeOptions.value = result.records.filter((item) => item.status === DICT_STATUS_ENABLED)
}

// ---- 新增/编辑弹窗 ----

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
// 当前正在编辑的定义是否绑定承重字段（locked）；仅编辑模式下有意义，新增恒为 false
const editingLocked = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()

// 新增/编辑弹窗共用：当前业务对象类型下可选的元数据字段（新增时为"尚未绑定"的列表；
// 编辑时额外包含当前定义正在绑定的那一个，供改绑下拉框展示"当前绑定 + 其余可选"）
const availableMetadataFields = ref<MetadataField[]>([])

interface FieldDialogForm {
  metadataFieldId: number | null
  fieldName: string
  fieldCode: string
  controlType: number
  dictTypeCode: string | null
  isUnique: boolean
  isRequired: boolean
  showInList: boolean
  showInCreate: boolean
  showInEdit: boolean
  editable: boolean
  validateRegex: string
  placeholder: string
  showOrder: number
}

function blankForm(): FieldDialogForm {
  return {
    metadataFieldId: null,
    fieldName: '',
    fieldCode: '',
    controlType: 1,
    dictTypeCode: null,
    isUnique: false,
    isRequired: false,
    showInList: true,
    showInCreate: true,
    showInEdit: true,
    editable: true,
    validateRegex: '',
    placeholder: '',
    showOrder: 0,
  }
}

const form = reactive<FieldDialogForm>(blankForm())

// 是否为需要关联字典类型的控件类型（下拉单选字典、多选字典下拉）
function isDictLinkedControlType(controlType: number): boolean {
  return controlType === FORM_FIELD_CONTROL_TYPE_DICT || controlType === FORM_FIELD_CONTROL_TYPE_MULTI_DICT
}

function validateDictType(_rule: unknown, value: string | null, callback: (error?: Error) => void) {
  if (!isDictLinkedControlType(form.controlType) || value) {
    callback()
    return
  }
  callback(new Error('控件类型为字典下拉或多选字典下拉时必须选择关联的字典类型'))
}

const rules: FormRules<FieldDialogForm> = {
  metadataFieldId: [{ required: true, message: '请选择要绑定的元数据字段', trigger: 'change' }],
  fieldName: [{ required: true, message: '请输入展示名称', trigger: 'blur' }],
  controlType: [{ required: true, message: '请选择控件类型', trigger: 'change' }],
  dictTypeCode: [{ validator: validateDictType, trigger: 'change' }],
}

const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增字段定义' : '编辑字段定义'))

async function openCreateDialog() {
  await fetchDictTypeOptions()
  availableMetadataFields.value = await metadataFieldApi.fetchAvailableMetadataFields(activeBizType.value)
  dialogMode.value = 'create'
  editingId.value = null
  editingLocked.value = false
  Object.assign(form, blankForm())
  dialogVisible.value = true
}

async function openEditDialog(row: FormFieldDefinition) {
  await fetchDictTypeOptions()
  availableMetadataFields.value = await metadataFieldApi.fetchAvailableMetadataFields(activeBizType.value, row.id)
  dialogMode.value = 'edit'
  editingId.value = row.id
  editingLocked.value = row.locked
  form.metadataFieldId = row.metadataFieldId
  form.fieldName = row.fieldName
  form.fieldCode = row.fieldCode
  form.controlType = row.controlType
  form.dictTypeCode = row.dictTypeCode
  form.isUnique = row.isUnique
  form.isRequired = row.isRequired
  form.showInList = row.showInList
  form.showInCreate = row.showInCreate
  form.showInEdit = row.showInEdit
  form.editable = row.editable
  form.validateRegex = row.validateRegex ?? ''
  form.placeholder = row.placeholder ?? ''
  form.showOrder = row.showOrder
  dialogVisible.value = true
}

// 字段标识完全派生自所选数据字段的当前字段标识，不支持手动编辑；切换数据字段时同步更新
watch(
  () => form.metadataFieldId,
  (newId) => {
    const opt = availableMetadataFields.value.find((item) => item.id === newId)
    form.fieldCode = opt ? opt.fieldCode : ''
  },
)

function closeDialog() {
  dialogVisible.value = false
  formRef.value?.clearValidate()
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload = {
      metadataFieldId: form.metadataFieldId as number,
      fieldName: form.fieldName,
      controlType: form.controlType,
      dictTypeCode: isDictLinkedControlType(form.controlType) ? form.dictTypeCode : null,
      isUnique: form.isUnique,
      isRequired: form.isRequired,
      showInList: form.showInList,
      showInCreate: form.showInCreate,
      showInEdit: form.showInEdit,
      editable: form.editable,
      validateRegex: form.validateRegex,
      placeholder: form.placeholder,
      showOrder: form.showOrder,
    }
    if (dialogMode.value === 'create') {
      await formFieldApi.createFormField(payload)
      ElMessage.success('新增成功')
    } else {
      // 非锁定定义的数据字段下拉框可编辑，切换到与原绑定不同的元数据字段会触发服务端
      // 改绑校验；锁定定义下拉框禁用，metadataFieldId 保持原值提交不受影响
      await formFieldApi.updateFormField(editingId.value as number, payload)
      ElMessage.success('保存成功')
    }
    dialogVisible.value = false
    await fetchPage()
  } finally {
    submitting.value = false
  }
}

// ---- 行操作：启用/停用、删除（绑定承重字段的定义不展示停用/删除入口）----

async function toggleStatus(row: FormFieldDefinition) {
  if (row.status === FORM_FIELD_STATUS_ENABLED) {
    await formFieldApi.disableFormField(row.id)
    ElMessage.success('已停用')
  } else {
    await formFieldApi.enableFormField(row.id)
    ElMessage.success('已启用')
  }
  await fetchPage()
}

async function handleDelete(row: FormFieldDefinition) {
  await ElMessageBox.confirm(`确定要删除字段定义「${row.fieldName}」吗？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await formFieldApi.deleteFormField(row.id)
  ElMessage.success('删除成功')
  await fetchPage()
}
</script>

<template>
  <div class="form-field-management">
    <el-tabs v-model="activeBizType" @tab-change="handleTabChange">
      <el-tab-pane
        v-for="opt in FORM_FIELD_BIZ_TYPE_OPTIONS"
        :key="opt.value"
        :label="opt.label"
        :name="opt.value"
      />
    </el-tabs>

    <div class="form-field-toolbar">
      <el-button type="primary" @click="openCreateDialog">新增</el-button>
    </div>

    <el-table v-loading="listLoading" :data="list" empty-text="暂无字段定义">
      <el-table-column prop="fieldName" label="展示名称" min-width="120" />
      <el-table-column prop="fieldCode" label="字段标识" min-width="120" />
      <el-table-column prop="columnName" label="绑定列名" min-width="110" />
      <el-table-column label="控件类型" width="100">
        <template #default="{ row }">{{ controlTypeLabel((row as FormFieldDefinition).controlType) }}</template>
      </el-table-column>
      <el-table-column label="属性" min-width="220">
        <template #default="{ row }">
          <el-tag v-if="(row as FormFieldDefinition).isRequired" size="small" class="form-field-tag">必填</el-tag>
          <el-tag v-if="(row as FormFieldDefinition).isUnique" size="small" class="form-field-tag">唯一</el-tag>
          <el-tag v-if="(row as FormFieldDefinition).showInList" size="small" class="form-field-tag">列表</el-tag>
          <el-tag v-if="(row as FormFieldDefinition).showInCreate" size="small" class="form-field-tag">新增</el-tag>
          <el-tag v-if="(row as FormFieldDefinition).showInEdit" size="small" class="form-field-tag">编辑</el-tag>
          <el-tag v-if="!(row as FormFieldDefinition).editable" size="small" type="info" class="form-field-tag">
            只读
          </el-tag>
          <el-tag v-if="(row as FormFieldDefinition).locked" size="small" type="warning" class="form-field-tag">
            系统保护
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="showOrder" label="显示序号" width="90" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag v-if="(row as FormFieldDefinition).status === FORM_FIELD_STATUS_ENABLED" type="success">
            启用
          </el-tag>
          <el-tag v-else type="warning">停用</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEditDialog(row as FormFieldDefinition)">编辑</el-button>
          <template v-if="!(row as FormFieldDefinition).locked">
            <el-button
              link
              :type="(row as FormFieldDefinition).status === FORM_FIELD_STATUS_ENABLED ? 'warning' : 'success'"
              @click="toggleStatus(row as FormFieldDefinition)"
            >
              {{ (row as FormFieldDefinition).status === FORM_FIELD_STATUS_ENABLED ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" @click="handleDelete(row as FormFieldDefinition)">删除</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="form-field-pagination"
      background
      layout="sizes, prev, pager, next, total"
      :page-sizes="[...PAGE_SIZE_OPTIONS]"
      :current-page="page"
      :page-size="pageSize"
      :total="total"
      @current-change="handlePageChange"
      @size-change="handleSizeChange"
    />

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" top="5vh" @close="closeDialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="数据字段" prop="metadataFieldId">
          <el-select
            v-model="form.metadataFieldId"
            placeholder="请选择尚未绑定的元数据字段"
            style="width: 100%"
            :disabled="dialogMode === 'edit' && editingLocked"
          >
            <el-option
              v-for="opt in availableMetadataFields"
              :key="opt.id"
              :label="`${opt.fieldName}（${opt.columnName}）`"
              :value="opt.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="展示名称" prop="fieldName">
          <el-input v-model="form.fieldName" placeholder="请输入展示名称" />
        </el-form-item>
        <el-form-item label="字段标识">
          <el-input v-model="form.fieldCode" disabled placeholder="由所选数据字段自动带出" />
        </el-form-item>
        <el-form-item label="控件类型" prop="controlType">
          <el-select v-model="form.controlType" style="width: 100%">
            <el-option v-for="opt in FORM_FIELD_CONTROL_TYPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="isDictLinkedControlType(form.controlType)" label="字典类型" prop="dictTypeCode">
          <el-select v-model="form.dictTypeCode" placeholder="请选择关联的字典类型" style="width: 100%">
            <el-option v-for="opt in dictTypeOptions" :key="opt.id" :label="opt.name" :value="opt.code" />
          </el-select>
        </el-form-item>

        <div class="form-field-switch-grid">
          <el-form-item label="是否唯一">
            <el-switch v-model="form.isUnique" />
          </el-form-item>
          <el-form-item label="是否必填">
            <el-switch v-model="form.isRequired" :disabled="editingLocked" />
          </el-form-item>
          <el-form-item label="列表展示">
            <el-switch v-model="form.showInList" />
          </el-form-item>
          <el-form-item label="新增展示">
            <el-switch v-model="form.showInCreate" :disabled="editingLocked" />
          </el-form-item>
          <el-form-item label="编辑展示">
            <el-switch v-model="form.showInEdit" :disabled="editingLocked" />
          </el-form-item>
          <el-form-item label="是否可编辑">
            <el-switch v-model="form.editable" />
          </el-form-item>
        </div>
        <p v-if="editingLocked" class="form-field-locked-hint">
          该字段由系统保护，必填与新增/编辑表单展示状态不可关闭。
        </p>

        <el-form-item label="正则校验">
          <el-input v-model="form.validateRegex" placeholder="选填，Java 与 JavaScript 都兼容的正则表达式" />
        </el-form-item>
        <el-form-item label="输入提示文字">
          <el-input v-model="form.placeholder" placeholder="选填" />
        </el-form-item>
        <el-form-item label="显示序号">
          <el-input-number v-model="form.showOrder" :min="0" style="width: 100%" />
          <div class="form-field-hint">数值越小，排序越靠前</div>
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
.form-field-management {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

.form-field-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.form-field-tag {
  margin-right: 4px;
  margin-bottom: 2px;
}

.form-field-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.form-field-switch-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: 16px;
}

.form-field-locked-hint {
  font-size: 12px;
  color: var(--color-warning, #e6a23c);
  margin: -4px 0 12px;
}

.form-field-hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-top: 4px;
}

:deep(.el-table .el-button + .el-button) {
  margin-left: 6px;
}
</style>
