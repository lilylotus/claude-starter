<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { usePermissionStore } from '@/stores/permission'
import OperationHistoryPanel from '@/components/OperationHistoryPanel.vue'
import * as permissionApi from '@/api/permission'
import { PERMISSION_STATUS_ENABLED, type PermissionFormRequest, type PermissionRow } from '@/types/permission'

const permissionStore = usePermissionStore()

onMounted(() => {
  permissionStore.fetchPage()
})

function handlePageChange(targetPage: number) {
  permissionStore.changePage(targetPage)
}

// ---- 新增/编辑弹窗 ----

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<PermissionFormRequest>({
  name: '',
  code: '',
  showOrder: 0,
  remark: '',
})

const rules: FormRules<PermissionFormRequest> = {
  name: [{ required: true, message: '请输入权限名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入权限编码', trigger: 'blur' }],
}

const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增权限' : '编辑权限'))

function openCreateDialog() {
  dialogMode.value = 'create'
  editingId.value = null
  form.name = ''
  form.code = ''
  form.showOrder = 0
  form.remark = ''
  dialogVisible.value = true
}

async function openEditDialog(row: PermissionRow) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  const detail = await permissionApi.getPermissionById(row.id)
  form.name = detail.name
  form.code = detail.code
  form.showOrder = detail.showOrder
  form.remark = detail.remark
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
    const payload: PermissionFormRequest = {
      name: form.name,
      code: form.code,
      showOrder: form.showOrder,
      remark: form.remark,
    }
    if (dialogMode.value === 'create') {
      await permissionApi.createPermission(payload)
      ElMessage.success('新增成功')
    } else {
      await permissionApi.updatePermission(editingId.value as number, payload)
      ElMessage.success('保存成功')
    }
    dialogVisible.value = false
    await permissionStore.refreshAfterMutation()
  } finally {
    submitting.value = false
  }
}

// ---- 只读详情弹窗 ----

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref<PermissionRow | null>(null)

async function openDetailDialog(row: PermissionRow) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detailData.value = await permissionApi.getPermissionById(row.id)
  } finally {
    detailLoading.value = false
  }
}

// ---- 行操作：启用/停用、删除 ----

async function toggleStatus(row: PermissionRow) {
  if (row.status === PERMISSION_STATUS_ENABLED) {
    await permissionApi.disablePermission(row.id)
    ElMessage.success('已停用')
  } else {
    await permissionApi.enablePermission(row.id)
    ElMessage.success('已启用')
  }
  await permissionStore.refreshAfterMutation()
}

async function handleDelete(row: PermissionRow) {
  await ElMessageBox.confirm(`确定要删除权限「${row.name}」吗？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await permissionApi.deletePermission(row.id)
  ElMessage.success('删除成功')
  await permissionStore.refreshAfterMutation()
}
</script>

<template>
  <div class="permission-management">
    <section class="permission-panel">
      <header class="permission-panel__header">
        <h2 class="permission-panel__title">权限管理</h2>
        <el-button type="primary" @click="openCreateDialog">新增</el-button>
      </header>

      <el-table v-loading="permissionStore.listLoading" :data="permissionStore.list" empty-text="暂无权限">
        <el-table-column prop="name" label="权限名称" min-width="140" />
        <el-table-column prop="code" label="权限编码" min-width="140" />
        <el-table-column prop="remark" label="备注" min-width="160" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="(row as PermissionRow).status === PERMISSION_STATUS_ENABLED" type="success">启用</el-tag>
            <el-tag v-else type="warning">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="showOrder" label="显示序号" width="90" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetailDialog(row as PermissionRow)">详情</el-button>
            <el-button link type="primary" @click="openEditDialog(row as PermissionRow)">编辑</el-button>
            <el-button
              link
              :type="(row as PermissionRow).status === PERMISSION_STATUS_ENABLED ? 'warning' : 'success'"
              @click="toggleStatus(row as PermissionRow)"
            >
              {{ (row as PermissionRow).status === PERMISSION_STATUS_ENABLED ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" @click="handleDelete(row as PermissionRow)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="permission-pagination"
        background
        layout="prev, pager, next, total"
        :current-page="permissionStore.page"
        :page-size="permissionStore.pageSize"
        :total="permissionStore.total"
        @current-change="handlePageChange"
      />
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" @close="closeDialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="权限名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入权限名称" />
        </el-form-item>
        <el-form-item label="权限编码" prop="code">
          <el-input v-model="form.code" placeholder="请输入权限编码" />
        </el-form-item>
        <el-form-item label="显示序号" prop="showOrder">
          <el-input-number v-model="form.showOrder" :min="0" style="width: 100%" />
          <div class="permission-form-hint">数值越大，排序越靠前</div>
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

    <el-dialog v-model="detailVisible" title="权限详情" width="560px" destroy-on-close>
      <el-descriptions v-loading="detailLoading" :column="1" border>
        <el-descriptions-item label="权限名称">{{ detailData?.name }}</el-descriptions-item>
        <el-descriptions-item label="权限编码">{{ detailData?.code }}</el-descriptions-item>
        <el-descriptions-item label="显示序号">{{ detailData?.showOrder }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detailData?.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag v-if="detailData?.status === PERMISSION_STATUS_ENABLED" type="success">启用</el-tag>
          <el-tag v-else type="warning">停用</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detailData?.createBy }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detailData?.createTime }}</el-descriptions-item>
        <el-descriptions-item label="更新人">{{ detailData?.updateBy }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ detailData?.updateTime }}</el-descriptions-item>
      </el-descriptions>

      <OperationHistoryPanel resource-type="permission" :target-id="detailData?.id ?? null" />

      <template #footer>
        <el-button type="primary" @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.permission-panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

.permission-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.permission-panel__title {
  font-size: 15px;
  color: var(--color-ink);
  margin: 0;
}

.permission-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.permission-form-hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-top: 4px;
}

// 操作列（详情/编辑/启用停用/删除）相邻按钮间距收紧，比 Element Plus 默认更紧凑
:deep(.el-table .el-button + .el-button) {
  margin-left: 6px;
}
</style>
