<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAppStore } from '@/stores/app'
import * as appApi from '@/api/app'
import * as orgApi from '@/api/org'
import * as userApi from '@/api/user'
import { APP_STATUS_ENABLED, type AppFormRequest, type AppRow } from '@/types/app'
import type { OrgTreeNode } from '@/types/org'
import { useDynamicFormFields } from '@/composables/useDynamicFormFields'
import {
  FORM_FIELD_CONTROL_TYPE_DATE,
  FORM_FIELD_CONTROL_TYPE_DICT,
  FORM_FIELD_CONTROL_TYPE_MULTI_DICT,
  FORM_FIELD_CONTROL_TYPE_NUMBER,
  FORM_FIELD_CONTROL_TYPE_TEXT,
} from '@/types/formField'

const appStore = useAppStore()
const router = useRouter()

// 除负责人（ownerId）、所属组织（orgId）、启停用状态（status）外的全部字段
// （含原有表字段与 ext1~ext10）统一按"表单字段定义"（bizType=APP）动态渲染，见 design.md 决策 12
const appFields = useDynamicFormFields('APP')

onMounted(() => {
  appStore.fetchPage()
  appFields.fetchSchema()
  // 全量组织树（orgTree，供新增/编辑弹窗内“所属组织”选择器用）不在这里预加载：
  // 只有打开弹窗时才需要它，此处预加载会让绝大多数只浏览应用列表的页面访问都白白
  // 发一次 GET /api/orgs/tree（见 openCreateDialog/openEditDialog）
})

function handlePageChange(targetPage: number) {
  appStore.changePage(targetPage)
}

// ---- 弹窗内“所属组织”选择器数据源（一次性加载全量组织树，不需要防环处理） ----

const orgTree = ref<OrgTreeNode[]>([])

async function fetchOrgTree() {
  orgTree.value = await orgApi.getOrgTree()
}

// ---- 负责人远程搜索选择器（复用 GET /api/users?name=/?mobile= 分页接口） ----
// 输入内容形如手机号（11 位、1 开头）时按 mobile 搜索，否则按 name 搜索；
// name/mobile 后端是“与”关系，不能同时传，否则会搜不到人

const MOBILE_PATTERN = /^1\d{10}$/

interface OwnerOption {
  id: number
  name: string
  mobile: string
}

const ownerOptions = ref<OwnerOption[]>([])
const ownerSearchLoading = ref(false)

async function remoteSearchOwners(query: string) {
  if (!query) {
    ownerOptions.value = []
    return
  }
  ownerSearchLoading.value = true
  try {
    const params = MOBILE_PATTERN.test(query) ? { mobile: query, pageSize: 20 } : { name: query, pageSize: 20 }
    const result = await userApi.getUserPage(params)
    ownerOptions.value = result.records.map((user) => ({ id: user.id, name: user.name, mobile: user.mobile }))
  } finally {
    ownerSearchLoading.value = false
  }
}

// ---- 新增/编辑弹窗 ----

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

// ownerId/orgId 在校验通过前允许暂时为空（由用户手动选择），因此不直接复用
// AppFormRequest（其字段类型是提交给后端时已确定非空的 number）；其余字段
// （name/code/showOrder/remark/ext1~ext10）由 appFields 动态渲染驱动，
// key 为各自绑定的 columnName
interface AppFormStatic {
  ownerId: number | null
  orgId: number | null
}

type AppForm = AppFormStatic & Record<string, unknown>

const form = reactive<AppForm>({
  ownerId: null,
  orgId: null,
})

// 保留 form 里指定 key（如 ownerId/orgId），清空其余动态字段的 key，供每次打开弹窗前重置
function resetDynamicKeys(target: Record<string, unknown>, keep: string[]) {
  Object.keys(target).forEach((key) => {
    if (!keep.includes(key)) delete target[key]
  })
}

const rules = computed<FormRules>(() => ({
  ownerId: [{ required: true, message: '请选择负责人', trigger: 'change' }],
  orgId: [{ required: true, message: '请选择所属组织', trigger: 'change' }],
  ...(dialogMode.value === 'create' ? appFields.createRules : appFields.editRules),
}))

const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增应用' : '编辑应用'))

async function openCreateDialog() {
  await fetchOrgTree()
  dialogMode.value = 'create'
  editingId.value = null
  resetDynamicKeys(form, ['ownerId', 'orgId'])
  Object.assign(form, appFields.buildFormModel(appFields.createFields))
  form.ownerId = null
  form.orgId = null
  ownerOptions.value = []
  dialogVisible.value = true
}

async function openEditDialog(row: AppRow) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  const detail = await appApi.getAppById(row.id)
  await fetchOrgTree()
  resetDynamicKeys(form, ['ownerId', 'orgId'])
  Object.assign(form, appFields.buildFormModel(appFields.editFields, detail))
  form.ownerId = detail.ownerId
  form.orgId = detail.orgId
  // 回显负责人选项，避免刚打开编辑弹窗时下拉框因搜索结果为空而显示不出已选中的负责人姓名
  ownerOptions.value = [{ id: detail.ownerId, name: detail.ownerName, mobile: '' }]
  dialogVisible.value = true
}

function closeDialog() {
  dialogVisible.value = false
  formRef.value?.clearValidate()
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    // 上面的表单校验已经确保 ownerId/orgId 必填，此处已知非空；动态字段（含 MULTI_DICT）
    // 先经 buildSubmitModel 把多选字典的数组值序列化成逗号分隔字符串，再与静态覆盖
    // 字段合并，避免把数组直接提交给后端导致 HttpMessageNotReadableException
    const activeFields = dialogMode.value === 'create' ? appFields.createFields : appFields.editFields
    const payload = {
      ...appFields.buildSubmitModel(activeFields, form),
      ownerId: form.ownerId as number,
      orgId: form.orgId as number,
    } as unknown as AppFormRequest
    if (dialogMode.value === 'create') {
      await appApi.createApp(payload)
      ElMessage.success('新增成功')
    } else {
      await appApi.updateApp(editingId.value as number, payload)
      ElMessage.success('保存成功')
    }
    dialogVisible.value = false
    await appStore.refreshAfterMutation()
  } finally {
    submitting.value = false
  }
}

// ---- 只读详情：跳转独立详情页 ----

function goToDetail(row: AppRow) {
  router.push({ name: 'application-list-detail', params: { id: row.id } })
}

// ---- 行操作：启用/停用、删除 ----

async function toggleStatus(row: AppRow) {
  if (row.status === APP_STATUS_ENABLED) {
    await appApi.disableApp(row.id)
    ElMessage.success('已停用')
  } else {
    await appApi.enableApp(row.id)
    ElMessage.success('已启用')
  }
  await appStore.refreshAfterMutation()
}

async function handleDelete(row: AppRow) {
  await ElMessageBox.confirm(`确定要删除应用「${row.name}」吗？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await appApi.deleteApp(row.id)
  ElMessage.success('删除成功')
  await appStore.refreshAfterMutation()
}
</script>

<template>
  <div class="app-management">
    <section class="app-panel">
      <header class="app-panel__header">
        <h2 class="app-panel__title">应用管理</h2>
        <el-button type="primary" @click="openCreateDialog">新增</el-button>
      </header>

      <el-table v-loading="appStore.listLoading" :data="appStore.list" empty-text="暂无应用">
        <el-table-column
          v-for="col in appFields.listColumns"
          :key="col.fieldCode"
          :label="col.fieldName"
          min-width="120"
        >
          <template #default="{ row }">
            <span v-if="col.controlType === FORM_FIELD_CONTROL_TYPE_DICT">
              {{ appFields.dictOptionLabel(col, (row as Record<string, unknown>)[col.columnName]) }}
            </span>
            <span v-else-if="col.controlType === FORM_FIELD_CONTROL_TYPE_MULTI_DICT">
              {{ appFields.dictOptionLabels(col, (row as Record<string, unknown>)[col.columnName]) }}
            </span>
            <span v-else>{{ (row as Record<string, unknown>)[col.columnName] }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="ownerName" label="负责人" min-width="100" />
        <el-table-column prop="orgName" label="所属组织" min-width="140" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="(row as AppRow).status === APP_STATUS_ENABLED" type="success">启用</el-tag>
            <el-tag v-else type="warning">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="goToDetail(row as AppRow)">详情</el-button>
            <el-button link type="primary" @click="openEditDialog(row as AppRow)">编辑</el-button>
            <el-button
              link
              :type="(row as AppRow).status === APP_STATUS_ENABLED ? 'warning' : 'success'"
              @click="toggleStatus(row as AppRow)"
            >
              {{ (row as AppRow).status === APP_STATUS_ENABLED ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" @click="handleDelete(row as AppRow)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="app-pagination"
        background
        layout="prev, pager, next, total"
        :current-page="appStore.page"
        :page-size="appStore.pageSize"
        :total="appStore.total"
        @current-change="handlePageChange"
      />
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" @close="closeDialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="负责人" prop="ownerId">
          <el-select
            v-model="form.ownerId"
            filterable
            remote
            reserve-keyword
            placeholder="输入姓名或手机号搜索负责人"
            :remote-method="remoteSearchOwners"
            :loading="ownerSearchLoading"
            style="width: 100%"
          >
            <el-option
              v-for="opt in ownerOptions"
              :key="opt.id"
              :label="opt.mobile ? `${opt.name}（${opt.mobile}）` : opt.name"
              :value="opt.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="所属组织" prop="orgId">
          <el-tree-select
            v-model="form.orgId"
            :data="orgTree"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            check-strictly
            placeholder="请选择组织"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item
          v-for="item in (dialogMode === 'create' ? appFields.createFields : appFields.editFields)"
          :key="item.fieldCode"
          :label="item.fieldName"
          :prop="item.columnName"
        >
          <el-input
            v-if="item.controlType === FORM_FIELD_CONTROL_TYPE_TEXT"
            v-model="(form[item.columnName] as string)"
            :placeholder="item.placeholder || `请输入${item.fieldName}`"
            :disabled="!item.editable"
          />
          <el-input-number
            v-else-if="item.controlType === FORM_FIELD_CONTROL_TYPE_NUMBER"
            v-model="(form[item.columnName] as number)"
            :min="0"
            style="width: 100%"
            :disabled="!item.editable"
          />
          <el-date-picker
            v-else-if="item.controlType === FORM_FIELD_CONTROL_TYPE_DATE"
            v-model="(form[item.columnName] as string | null)"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
            :placeholder="item.placeholder || `请选择${item.fieldName}`"
            :disabled="!item.editable"
          />
          <el-select
            v-else-if="item.controlType === FORM_FIELD_CONTROL_TYPE_MULTI_DICT"
            v-model="(form[item.columnName] as string[])"
            multiple
            :placeholder="item.placeholder || `请选择${item.fieldName}`"
            :disabled="!item.editable"
            style="width: 100%"
          >
            <el-option v-for="opt in item.dictOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
          <template v-else>
            <el-select
              v-model="(form[item.columnName] as string)"
              :placeholder="item.placeholder || `请选择${item.fieldName}`"
              :disabled="!item.editable"
              style="width: 100%"
            >
              <el-option v-for="opt in item.dictOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </template>
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
.app-panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

.app-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.app-panel__title {
  font-size: 15px;
  color: var(--color-ink);
  margin: 0;
}

.app-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.app-form-hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-top: 4px;
}

// 操作列（详情/编辑/启用停用/删除）相邻按钮间距收紧，比 Element Plus 默认更紧凑
:deep(.el-table .el-button + .el-button) {
  margin-left: 6px;
}
</style>
