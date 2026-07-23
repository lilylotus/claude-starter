<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import * as importFieldConfigApi from '@/api/importFieldConfig'
import * as formFieldApi from '@/api/formField'
import { FORM_FIELD_BIZ_TYPE_OPTIONS, type FormFieldBizType } from '@/types/metadataField'
import { FORM_FIELD_STATUS_ENABLED, type FormFieldDefinition } from '@/types/formField'
import type { ImportFieldConfig } from '@/types/importFieldConfig'

// "导入模板配置" tab：按业务对象类型（组织/人员/任职/应用）维护 Excel 导入字段配置
// 列表（按 showOrder 升序，后端已排好序），驱动导入模板生成与批量导入的表头匹配、
// 主键匹配、必填校验；新增/编辑弹窗从当前业务对象类型下启用的表单字段定义中选择
// "关联字段"，选中后带出默认表头名称与字段标识。POSITION（人员编号/组织编码）与
// APP（负责人编号/组织编码）分类下各自预置的固定标识列（locked=true）标记为
// "系统保护"，隐藏删除入口，编辑弹窗内禁止取消主键/必填标记、禁止改绑关联字段
// （见 design.md 决策 2）。

const activeBizType = ref<FormFieldBizType>('ORG')

const list = ref<ImportFieldConfig[]>([])
const listLoading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

async function fetchPage(targetPage?: number) {
  if (targetPage !== undefined) page.value = targetPage
  listLoading.value = true
  try {
    const result = await importFieldConfigApi.getImportFieldConfigPage({
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

// ---- "关联字段"选择器数据源：当前业务对象类型下状态为启用的表单字段定义 ----
// 表单字段定义列表接口不支持按状态过滤，用较大的 pageSize 取回该 bizType 下全部
// 未删除定义后在前端过滤出启用状态的，供选择器下拉框使用

const availableFormFields = ref<FormFieldDefinition[]>([])

async function fetchAvailableFormFields() {
  const result = await formFieldApi.getFormFieldPage({ bizType: activeBizType.value, pageSize: 200 })
  availableFormFields.value = result.records.filter((item) => item.status === FORM_FIELD_STATUS_ENABLED)
}

// ---- 新增/编辑弹窗 ----

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
// 当前正在编辑的配置是否为系统保护（locked）配置；仅编辑模式下有意义，新增恒为 false
const editingLocked = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()

interface ImportFieldConfigDialogForm {
  formFieldDefinitionId: number | null
  fieldCode: string
  excelHeaderName: string
  isPrimaryKey: boolean
  isRequired: boolean
  showOrder: number
}

function blankForm(): ImportFieldConfigDialogForm {
  return {
    formFieldDefinitionId: null,
    fieldCode: '',
    excelHeaderName: '',
    isPrimaryKey: false,
    isRequired: false,
    showOrder: 0,
  }
}

const form = reactive<ImportFieldConfigDialogForm>(blankForm())

const rules: FormRules<ImportFieldConfigDialogForm> = {
  formFieldDefinitionId: [{ required: true, message: '请选择要关联的表单字段', trigger: 'change' }],
  excelHeaderName: [{ required: true, message: '请输入 Excel 表头文字', trigger: 'blur' }],
}

const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增导入字段配置' : '编辑导入字段配置'))

async function openCreateDialog() {
  await fetchAvailableFormFields()
  dialogMode.value = 'create'
  editingId.value = null
  editingLocked.value = false
  Object.assign(form, blankForm())
  dialogVisible.value = true
}

async function openEditDialog(row: ImportFieldConfig) {
  if (!row.locked) {
    await fetchAvailableFormFields()
  }
  dialogMode.value = 'edit'
  editingId.value = row.id
  editingLocked.value = row.locked
  form.formFieldDefinitionId = row.formFieldDefinitionId
  form.fieldCode = row.fieldCode
  form.excelHeaderName = row.excelHeaderName
  form.isPrimaryKey = row.isPrimaryKey
  form.isRequired = row.isRequired
  form.showOrder = row.showOrder
  dialogVisible.value = true
}

// 关联字段完全决定字段标识与默认表头名称，切换选择时联动更新（表头名称仍可在
// 联动后手动调整），与 FormFieldDefinitionPanel.vue 里数据字段→字段标识的联动写法一致
function handleRelatedFieldChange(id: number | null) {
  const opt = availableFormFields.value.find((item) => item.id === id)
  form.fieldCode = opt ? opt.fieldCode : ''
  form.excelHeaderName = opt ? opt.fieldName : ''
}

function closeDialog() {
  dialogVisible.value = false
  formRef.value?.clearValidate()
}

async function submitForm() {
  if (!editingLocked.value) {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return
  }

  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await importFieldConfigApi.createImportFieldConfig({
        bizType: activeBizType.value,
        formFieldDefinitionId: form.formFieldDefinitionId,
        fieldCode: form.fieldCode,
        excelHeaderName: form.excelHeaderName,
        isPrimaryKey: form.isPrimaryKey,
        isRequired: form.isRequired,
        showOrder: form.showOrder,
      })
      ElMessage.success('新增成功')
    } else {
      // 系统保护配置的关联字段固定为空（不可改绑），主键/必填开关禁用不可修改，
      // 提交时原样带上当前值，服务端会再次校验拒绝任何试图放开保护的改动
      await importFieldConfigApi.updateImportFieldConfig(editingId.value as number, {
        formFieldDefinitionId: editingLocked.value ? null : form.formFieldDefinitionId,
        excelHeaderName: form.excelHeaderName,
        isPrimaryKey: form.isPrimaryKey,
        isRequired: form.isRequired,
        showOrder: form.showOrder,
      })
      ElMessage.success('保存成功')
    }
    dialogVisible.value = false
    await fetchPage()
  } finally {
    submitting.value = false
  }
}

// ---- 行操作：删除（系统保护配置不展示删除入口）----

async function handleDelete(row: ImportFieldConfig) {
  await ElMessageBox.confirm(`确定要删除导入字段配置「${row.excelHeaderName}」吗？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await importFieldConfigApi.deleteImportFieldConfig(row.id)
  ElMessage.success('删除成功')
  await fetchPage()
}
</script>

<template>
  <div class="import-field-config-management">
    <el-tabs v-model="activeBizType" @tab-change="handleTabChange">
      <el-tab-pane
        v-for="opt in FORM_FIELD_BIZ_TYPE_OPTIONS"
        :key="opt.value"
        :label="opt.label"
        :name="opt.value"
      />
    </el-tabs>

    <div class="import-field-config-toolbar">
      <el-button type="primary" @click="openCreateDialog">新增</el-button>
    </div>

    <el-table v-loading="listLoading" :data="list" empty-text="暂无导入字段配置">
      <el-table-column label="Excel 表头" min-width="140">
        <template #default="{ row }">
          <span :class="{ 'import-field-config-required-header': (row as ImportFieldConfig).isRequired }">
            {{ (row as ImportFieldConfig).excelHeaderName }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="fieldCode" label="字段标识" min-width="140" />
      <el-table-column label="属性" min-width="200">
        <template #default="{ row }">
          <el-tag v-if="(row as ImportFieldConfig).isPrimaryKey" size="small" class="import-field-config-tag">
            主键
          </el-tag>
          <el-tag v-if="(row as ImportFieldConfig).isRequired" size="small" class="import-field-config-tag">
            必填
          </el-tag>
          <el-tag v-if="(row as ImportFieldConfig).locked" size="small" type="warning" class="import-field-config-tag">
            系统保护
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="showOrder" label="显示序号" width="90" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEditDialog(row as ImportFieldConfig)">编辑</el-button>
          <el-button
            v-if="!(row as ImportFieldConfig).locked"
            link
            type="danger"
            @click="handleDelete(row as ImportFieldConfig)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="import-field-config-pagination"
      background
      layout="prev, pager, next, total"
      :current-page="page"
      :page-size="pageSize"
      :total="total"
      @current-change="handlePageChange"
    />

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" top="8vh" @close="closeDialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <template v-if="editingLocked">
          <el-form-item label="字段标识">
            <el-input :model-value="form.fieldCode" disabled />
          </el-form-item>
          <p class="import-field-config-locked-hint">
            该配置由系统保护，是导入时定位关联人员/组织所需的固定标识列，不可改绑关联字段，
            也不可取消主键或必填标记。
          </p>
        </template>
        <template v-else>
          <el-form-item label="关联字段" prop="formFieldDefinitionId">
            <el-select
              v-model="form.formFieldDefinitionId"
              placeholder="请选择当前业务对象类型下启用的表单字段"
              style="width: 100%"
              @change="handleRelatedFieldChange"
            >
              <el-option
                v-for="opt in availableFormFields"
                :key="opt.id"
                :label="`${opt.fieldName}（${opt.fieldCode}）`"
                :value="opt.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="字段标识">
            <el-input v-model="form.fieldCode" disabled placeholder="由所选关联字段自动带出" />
          </el-form-item>
        </template>
        <el-form-item label="Excel 表头" prop="excelHeaderName">
          <el-input v-model="form.excelHeaderName" placeholder="请输入 Excel 表头文字" />
        </el-form-item>
        <el-form-item label="是否主键">
          <el-switch v-model="form.isPrimaryKey" :disabled="editingLocked" />
        </el-form-item>
        <el-form-item label="是否必填">
          <el-switch v-model="form.isRequired" :disabled="editingLocked" />
        </el-form-item>
        <el-form-item label="显示序号">
          <el-input-number v-model="form.showOrder" :min="0" style="width: 100%" />
          <div class="import-field-config-hint">数值越小，排序越靠前</div>
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
.import-field-config-management {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

.import-field-config-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.import-field-config-required-header {
  color: var(--color-danger, #f56c6c);
  font-weight: 600;
}

.import-field-config-tag {
  margin-right: 4px;
  margin-bottom: 2px;
}

.import-field-config-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.import-field-config-locked-hint {
  font-size: 12px;
  color: var(--color-warning, #e6a23c);
  margin: -4px 0 12px;
}

.import-field-config-hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-top: 4px;
}

:deep(.el-table .el-button + .el-button) {
  margin-left: 6px;
}
</style>
