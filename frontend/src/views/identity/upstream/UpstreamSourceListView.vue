<script setup lang="ts">
// 上游数据管理列表页：管理多个"上游数据源"配置，参照 AppManagementView.vue 列表页写法。
// 列表接口不分页（一次性返回全部数据源），因此不接入 Pinia store，直接用组件内 ref 状态。
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import * as upstreamSourceApi from '@/api/upstreamSource'
import {
  UPSTREAM_SCHEDULE_TYPE_OPTIONS,
  UPSTREAM_SYNC_TYPE_OPTIONS,
  type UpstreamSourceCreateRequest,
  type UpstreamSourceVO,
  type UpstreamSyncType,
} from '@/types/upstreamSource'
import { usePermission } from '@/composables/usePermission'

const router = useRouter()
const { hasPermission } = usePermission()

const listLoading = ref(false)
const list = ref<UpstreamSourceVO[]>([])

async function fetchList() {
  listLoading.value = true
  try {
    list.value = await upstreamSourceApi.listUpstreamSources()
  } finally {
    listLoading.value = false
  }
}

onMounted(() => {
  fetchList()
})

function syncTypeLabel(syncType: UpstreamSyncType) {
  return UPSTREAM_SYNC_TYPE_OPTIONS.find((item) => item.value === syncType)?.label ?? syncType
}

function scheduleSummary(row: UpstreamSourceVO) {
  const typeLabel = UPSTREAM_SCHEDULE_TYPE_OPTIONS.find((item) => item.value === row.scheduleType)?.label ?? '-'
  if (row.scheduleType === 'INTERVAL') {
    const unitLabel = row.intervalUnit === 'HOUR' ? '小时' : '分钟'
    return row.intervalValue ? `${typeLabel}（每 ${row.intervalValue} ${unitLabel}）` : typeLabel
  }
  return row.fixedTime ? `${typeLabel}（${row.fixedTime}）` : typeLabel
}

// ---- 新增/编辑弹窗：只填名称+同步方式，其余连接/调度/数据范围配置在独立配置页完成 ----

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<UpstreamSourceCreateRequest>({
  name: '',
  syncType: 'API',
})

const rules: FormRules = {
  name: [
    { required: true, message: '请输入数据源名称', trigger: 'blur' },
    { max: 128, message: '数据源名称长度不能超过 128 个字符', trigger: 'blur' },
  ],
  syncType: [{ required: true, message: '请选择同步方式', trigger: 'change' }],
}

const dialogTitle = ref('新增上游数据源')

function openCreateDialog() {
  dialogMode.value = 'create'
  editingId.value = null
  dialogTitle.value = '新增上游数据源'
  form.name = ''
  form.syncType = 'API'
  dialogVisible.value = true
}

function openEditDialog(row: UpstreamSourceVO) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  dialogTitle.value = '编辑上游数据源'
  form.name = row.name
  form.syncType = row.syncType
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
    if (dialogMode.value === 'create') {
      await upstreamSourceApi.createUpstreamSource(form)
      ElMessage.success('新增成功')
    } else {
      await upstreamSourceApi.updateUpstreamSourceBasicInfo(editingId.value as number, form)
      ElMessage.success('保存成功')
    }
    dialogVisible.value = false
    await fetchList()
  } finally {
    submitting.value = false
  }
}

// ---- 配置：跳转独立配置路由页 ----

function goToConfig(row: UpstreamSourceVO) {
  router.push({ name: 'identity-upstream-config', params: { id: row.id } })
}

// ---- 启用/停用、删除 ----

async function toggleEnabled(row: UpstreamSourceVO) {
  if (row.enabled) {
    await ElMessageBox.confirm(`确定要停用数据源「${row.name}」吗？停用后不再参与定时同步。`, '停用确认', {
      type: 'warning',
      confirmButtonText: '停用',
      cancelButtonText: '取消',
    })
    await upstreamSourceApi.disableUpstreamSource(row.id)
    ElMessage.success('已停用')
  } else {
    await upstreamSourceApi.enableUpstreamSource(row.id)
    ElMessage.success('已启用')
  }
  await fetchList()
}

async function handleDelete(row: UpstreamSourceVO) {
  await ElMessageBox.confirm(
    `确定要删除数据源「${row.name}」吗？将级联删除其下的数据域配置、字段映射与同步执行记录，不可恢复。`,
    '删除确认',
    {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    },
  )
  await upstreamSourceApi.deleteUpstreamSource(row.id)
  ElMessage.success('删除成功')
  await fetchList()
}
</script>

<template>
  <div class="upstream-management">
    <section class="upstream-panel">
      <header class="upstream-panel__header">
        <h2 class="upstream-panel__title">上游数据管理</h2>
        <div class="upstream-panel__actions">
          <el-button v-if="hasPermission('UpstreamManagement:source:add')" type="primary" @click="openCreateDialog">
            新增
          </el-button>
        </div>
      </header>

      <el-table v-loading="listLoading" :data="list" empty-text="暂无上游数据源">
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column label="同步方式" width="110">
          <template #default="{ row }">{{ syncTypeLabel((row as UpstreamSourceVO).syncType) }}</template>
        </el-table-column>
        <el-table-column label="调度配置" min-width="180">
          <template #default="{ row }">{{ scheduleSummary(row as UpstreamSourceVO) }}</template>
        </el-table-column>
        <el-table-column label="启用状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="(row as UpstreamSourceVO).enabled" type="success">启用</el-tag>
            <el-tag v-else type="info">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上次触发时间" min-width="160">
          <template #default="{ row }">{{ (row as UpstreamSourceVO).lastTriggerTime ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="160" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="hasPermission('UpstreamManagement:source:config')"
              link
              type="primary"
              @click="goToConfig(row as UpstreamSourceVO)"
            >
              配置
            </el-button>
            <el-button
              v-if="hasPermission('UpstreamManagement:source:edit')"
              link
              type="primary"
              @click="openEditDialog(row as UpstreamSourceVO)"
            >
              编辑
            </el-button>
            <el-button
              link
              :type="(row as UpstreamSourceVO).enabled ? 'warning' : 'success'"
              v-if="
                (row as UpstreamSourceVO).enabled
                  ? hasPermission('UpstreamManagement:source:disable')
                  : hasPermission('UpstreamManagement:source:enable')
              "
              @click="toggleEnabled(row as UpstreamSourceVO)"
            >
              {{ (row as UpstreamSourceVO).enabled ? '停用' : '启用' }}
            </el-button>
            <el-button
              v-if="hasPermission('UpstreamManagement:source:delete')"
              link
              type="danger"
              @click="handleDelete(row as UpstreamSourceVO)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="480px" @close="closeDialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入数据源名称" maxlength="128" />
        </el-form-item>
        <el-form-item label="同步方式" prop="syncType">
          <el-radio-group v-model="form.syncType">
            <el-radio v-for="item in UPSTREAM_SYNC_TYPE_OPTIONS" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio>
          </el-radio-group>
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
.upstream-panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

.upstream-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.upstream-panel__title {
  font-size: 15px;
  color: var(--color-ink);
  margin: 0;
}

.upstream-panel__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

// 操作列相邻按钮间距收紧，与 AppManagementView.vue 保持一致
:deep(.el-table .el-button + .el-button) {
  margin-left: 6px;
}
</style>
