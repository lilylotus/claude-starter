<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAppStore } from '@/stores/app'
import * as appApi from '@/api/app'
import * as orgApi from '@/api/org'
import * as userApi from '@/api/user'
import { APP_STATUS_ENABLED, type AppFormRequest, type AppRow } from '@/types/app'
import type { OrgTreeNode } from '@/types/org'

const appStore = useAppStore()

onMounted(() => {
  appStore.fetchPage()
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
// AppFormRequest（其字段类型是提交给后端时已确定非空的 number）
interface AppForm {
  name: string
  code: string
  ownerId: number | null
  orgId: number | null
  showOrder: number
  remark: string
}

const form = reactive<AppForm>({
  name: '',
  code: '',
  ownerId: null,
  orgId: null,
  showOrder: 0,
  remark: '',
})

const rules: FormRules<AppForm> = {
  name: [{ required: true, message: '请输入应用名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入应用编码', trigger: 'blur' }],
  ownerId: [{ required: true, message: '请选择负责人', trigger: 'change' }],
  orgId: [{ required: true, message: '请选择所属组织', trigger: 'change' }],
}

const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增应用' : '编辑应用'))

async function openCreateDialog() {
  await fetchOrgTree()
  dialogMode.value = 'create'
  editingId.value = null
  form.name = ''
  form.code = ''
  form.ownerId = null
  form.orgId = null
  form.showOrder = 0
  form.remark = ''
  ownerOptions.value = []
  dialogVisible.value = true
}

async function openEditDialog(row: AppRow) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  const detail = await appApi.getAppById(row.id)
  await fetchOrgTree()
  form.name = detail.name
  form.code = detail.code
  form.ownerId = detail.ownerId
  form.orgId = detail.orgId
  form.showOrder = detail.showOrder
  form.remark = detail.remark
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
    // 上面的表单校验已经确保 ownerId/orgId 必填，此处已知非空
    const payload: AppFormRequest = {
      name: form.name,
      code: form.code,
      ownerId: form.ownerId as number,
      orgId: form.orgId as number,
      showOrder: form.showOrder,
      remark: form.remark,
    }
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

// ---- 只读详情弹窗 ----

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref<AppRow | null>(null)

async function openDetailDialog(row: AppRow) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detailData.value = await appApi.getAppById(row.id)
  } finally {
    detailLoading.value = false
  }
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
        <el-table-column prop="name" label="应用名称" min-width="140" />
        <el-table-column prop="code" label="应用编码" min-width="120" />
        <el-table-column prop="ownerName" label="负责人" min-width="100" />
        <el-table-column prop="orgName" label="所属组织" min-width="140" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="(row as AppRow).status === APP_STATUS_ENABLED" type="success">启用</el-tag>
            <el-tag v-else type="warning">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="showOrder" label="显示序号" width="90" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetailDialog(row as AppRow)">详情</el-button>
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
        <el-form-item label="应用名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入应用名称" />
        </el-form-item>
        <el-form-item label="应用编码" prop="code">
          <el-input v-model="form.code" placeholder="请输入应用编码" />
        </el-form-item>
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
        <el-form-item label="显示序号" prop="showOrder">
          <el-input-number v-model="form.showOrder" :min="0" style="width: 100%" />
          <div class="app-form-hint">数值越大，排序越靠前</div>
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

    <el-dialog v-model="detailVisible" title="应用详情" width="560px">
      <el-descriptions v-loading="detailLoading" :column="1" border>
        <el-descriptions-item label="应用名称">{{ detailData?.name }}</el-descriptions-item>
        <el-descriptions-item label="应用编码">{{ detailData?.code }}</el-descriptions-item>
        <el-descriptions-item label="负责人">{{ detailData?.ownerName }}</el-descriptions-item>
        <el-descriptions-item label="所属组织">{{ detailData?.orgName }}</el-descriptions-item>
        <el-descriptions-item label="显示序号">{{ detailData?.showOrder }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detailData?.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag v-if="detailData?.status === APP_STATUS_ENABLED" type="success">启用</el-tag>
          <el-tag v-else type="warning">停用</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detailData?.createBy }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detailData?.createTime }}</el-descriptions-item>
        <el-descriptions-item label="更新人">{{ detailData?.updateBy }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ detailData?.updateTime }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="detailVisible = false">关闭</el-button>
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
