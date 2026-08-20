<script setup lang="ts">
// 应用访问授权 - 人工例外子面板：分页列表（按用户/应用/类型过滤）+ 新建/编辑弹窗
// （选用户、选应用、选 GRANT/DENY、备注）+ 删除。同一 userId+appId 组合的新增/编辑
// 都走同一个 upsert 接口（POST），后端按组合是否已存在决定新增还是更新。
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import * as appAccessApi from '@/api/appAccess'
import * as userApi from '@/api/user'
import * as appApi from '@/api/app'
import { PAGE_SIZE_OPTIONS, DEFAULT_PAGE_SIZE } from '@/constants/pagination'
import { OVERRIDE_TYPE_OPTIONS, type ManualOverrideUpsertRequest, type ManualOverrideVO, type OverrideType } from '@/types/appAccess'
import type { AppRow } from '@/types/app'
import { usePermission } from '@/composables/usePermission'

const { hasPermission } = usePermission()

// ---- 关联用户远程搜索选择器，抄自 AdminManagementView.vue：手机号（11 位、1 开头）
// 按 mobile 搜索，否则按 name 搜索 ----

const MOBILE_PATTERN = /^1\d{10}$/

interface UserOption {
  id: number
  name: string
  mobile: string
}

const userOptions = ref<UserOption[]>([])
const userSearchLoading = ref(false)

async function remoteSearchUsers(query: string) {
  if (!query) {
    userOptions.value = []
    return
  }
  userSearchLoading.value = true
  try {
    const params = MOBILE_PATTERN.test(query) ? { mobile: query, pageSize: 20 } : { name: query, pageSize: 20 }
    const result = await userApi.getUserPage(params)
    userOptions.value = result.records.map((user) => ({ id: user.id, name: user.name, mobile: user.mobile }))
  } finally {
    userSearchLoading.value = false
  }
}

// 筛选栏"按用户过滤"单独维护一份候选，避免和弹窗内的选择互相覆盖
const filterUserOptions = ref<UserOption[]>([])
const filterUserSearchLoading = ref(false)

async function remoteSearchFilterUsers(query: string) {
  if (!query) {
    filterUserOptions.value = []
    return
  }
  filterUserSearchLoading.value = true
  try {
    const params = MOBILE_PATTERN.test(query) ? { mobile: query, pageSize: 20 } : { name: query, pageSize: 20 }
    const result = await userApi.getUserPage(params)
    filterUserOptions.value = result.records.map((user) => ({ id: user.id, name: user.name, mobile: user.mobile }))
  } finally {
    filterUserSearchLoading.value = false
  }
}

// ---- 应用下拉选项：一次性加载，供筛选栏与弹窗共用 ----

const appOptions = ref<AppRow[]>([])

async function fetchAppOptions() {
  const result = await appApi.getAppPage(1, 200)
  appOptions.value = result.records
}

// ---- 列表 + 筛选 + 分页 ----

const filters = reactive({
  userId: undefined as number | undefined,
  appId: undefined as number | undefined,
  overrideType: undefined as OverrideType | undefined,
})

const list = ref<ManualOverrideVO[]>([])
const listLoading = ref(false)
const page = ref(1)
const pageSize = ref(DEFAULT_PAGE_SIZE)
const total = ref(0)

async function fetchList() {
  listLoading.value = true
  try {
    const result = await appAccessApi.getManualOverridePage({
      userId: filters.userId,
      appId: filters.appId,
      overrideType: filters.overrideType,
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
  fetchAppOptions()
})

function handleSearch() {
  page.value = 1
  fetchList()
}

function handleReset() {
  filters.userId = undefined
  filters.appId = undefined
  filters.overrideType = undefined
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

// ---- 新建/编辑弹窗 ----

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const formRef = ref<FormInstance>()

// userId/appId 在校验通过前允许暂时为空（由用户手动选择）
const form = reactive<{ userId: number | null; appId: number | null; overrideType: OverrideType; remark: string }>({
  userId: null,
  appId: null,
  overrideType: 'GRANT',
  remark: '',
})

const rules: FormRules = {
  userId: [{ required: true, message: '请选择用户', trigger: 'change' }],
  appId: [{ required: true, message: '请选择应用', trigger: 'change' }],
  overrideType: [{ required: true, message: '请选择例外类型', trigger: 'change' }],
}

const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增人工例外' : '编辑人工例外'))

function openCreateDialog() {
  dialogMode.value = 'create'
  form.userId = null
  form.appId = null
  form.overrideType = 'GRANT'
  form.remark = ''
  userOptions.value = []
  dialogVisible.value = true
}

function openEditDialog(row: ManualOverrideVO) {
  dialogMode.value = 'edit'
  form.userId = row.userId
  form.appId = row.appId
  form.overrideType = row.overrideType
  form.remark = row.remark
  // 编辑态预置当前用户为可选项，避免下拉框因未搜索而显示为空
  userOptions.value = [{ id: row.userId, name: row.userName, mobile: '' }]
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
    const payload: ManualOverrideUpsertRequest = {
      userId: form.userId as number,
      appId: form.appId as number,
      overrideType: form.overrideType,
      remark: form.remark.trim(),
    }
    await appAccessApi.upsertManualOverride(payload)
    ElMessage.success(dialogMode.value === 'create' ? '新增成功' : '保存成功')
    dialogVisible.value = false
    await fetchList()
  } finally {
    submitting.value = false
  }
}

// ---- 删除 ----

async function handleDelete(row: ManualOverrideVO) {
  await ElMessageBox.confirm(
    `确定要删除用户「${row.userName}」对应用「${row.appName}」的人工例外吗？删除后该组合的最终生效权限退回只看策略授权。`,
    '删除确认',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
  )
  await appAccessApi.deleteManualOverride(row.id)
  ElMessage.success('删除成功')
  await fetchList()
}
</script>

<template>
  <div class="override-panel">
    <header class="override-panel__header">
      <h3 class="override-panel__title">人工例外</h3>
      <el-button v-if="hasPermission('AppAccessManagement:override:add')" type="primary" @click="openCreateDialog">
        新增例外
      </el-button>
    </header>

    <el-form class="override-filter-form" inline @submit.prevent>
      <el-form-item label="用户">
        <el-select
          v-model="filters.userId"
          filterable
          remote
          clearable
          reserve-keyword
          placeholder="输入姓名或手机号搜索"
          :remote-method="remoteSearchFilterUsers"
          :loading="filterUserSearchLoading"
          style="width: 200px"
        >
          <el-option
            v-for="opt in filterUserOptions"
            :key="opt.id"
            :label="opt.mobile ? `${opt.name}（${opt.mobile}）` : opt.name"
            :value="opt.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="应用">
        <el-select v-model="filters.appId" filterable clearable placeholder="全部应用" style="width: 180px">
          <el-option v-for="app in appOptions" :key="app.id" :label="app.name" :value="app.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="filters.overrideType" clearable placeholder="全部类型" style="width: 160px">
          <el-option v-for="opt in OVERRIDE_TYPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="listLoading" :data="list" empty-text="暂无人工例外">
      <el-table-column prop="userName" label="用户" min-width="120" />
      <el-table-column prop="appName" label="应用" min-width="140" />
      <el-table-column label="类型" width="130">
        <template #default="{ row }">
          <el-tag :type="(row as ManualOverrideVO).overrideType === 'GRANT' ? 'success' : 'danger'">
            {{ (row as ManualOverrideVO).overrideType === 'GRANT' ? '手动追加授权' : '手动收回授权' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
      <el-table-column prop="updateTime" label="更新时间" width="170" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="hasPermission('AppAccessManagement:override:add')"
            link
            type="primary"
            @click="openEditDialog(row as ManualOverrideVO)"
          >
            编辑
          </el-button>
          <el-button
            v-if="hasPermission('AppAccessManagement:override:delete')"
            link
            type="danger"
            @click="handleDelete(row as ManualOverrideVO)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="override-pagination"
      background
      layout="sizes, prev, pager, next, total"
      :page-sizes="[...PAGE_SIZE_OPTIONS]"
      :current-page="page"
      :page-size="pageSize"
      :total="total"
      @current-change="handlePageChange"
      @size-change="handleSizeChange"
    />

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="480px" @close="closeDialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="用户" prop="userId">
          <el-select
            v-model="form.userId"
            filterable
            remote
            reserve-keyword
            placeholder="输入姓名或手机号搜索用户"
            :remote-method="remoteSearchUsers"
            :loading="userSearchLoading"
            :disabled="dialogMode === 'edit'"
            style="width: 100%"
          >
            <el-option
              v-for="opt in userOptions"
              :key="opt.id"
              :label="opt.mobile ? `${opt.name}（${opt.mobile}）` : opt.name"
              :value="opt.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="应用" prop="appId">
          <el-select v-model="form.appId" filterable placeholder="请选择应用" :disabled="dialogMode === 'edit'" style="width: 100%">
            <el-option v-for="app in appOptions" :key="app.id" :label="app.name" :value="app.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型" prop="overrideType">
          <el-radio-group v-model="form.overrideType">
            <el-radio v-for="opt in OVERRIDE_TYPE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="选填" />
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
.override-panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

.override-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.override-panel__title {
  font-size: 15px;
  color: var(--color-ink);
  margin: 0;
}

.override-filter-form {
  margin-bottom: 8px;

  :deep(.el-form-item) {
    margin-bottom: 12px;
  }
}

.override-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

:deep(.el-table .el-button + .el-button) {
  margin-left: 6px;
}
</style>
