<script setup lang="ts">
// 流程模型列表页（workflow-approval-engine change tasks.md 11.7）。
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as workflowApi from '@/api/workflow'
import { usePermission } from '@/composables/usePermission'
import { PROCESS_MODEL_STATUS_LABEL, type ProcessModelRow, type ProcessModelStatus } from '@/types/workflow'
import VersionHistoryDialog from './VersionHistoryDialog.vue'

const router = useRouter()
const { hasPermission } = usePermission()

const loading = ref(false)
const models = ref<ProcessModelRow[]>([])
const createDialogVisible = ref(false)
const creating = ref(false)
const createForm = reactive({ processCode: '', processName: '' })

async function fetchModels() {
  loading.value = true
  try {
    models.value = await workflowApi.listProcessModels()
  } catch {
    models.value = []
  } finally {
    loading.value = false
  }
}

fetchModels()

async function handleCreate() {
  if (!createForm.processCode.trim() || !createForm.processName.trim()) {
    ElMessage.error('请填写流程编码和流程名称')
    return
  }
  creating.value = true
  try {
    const model = await workflowApi.createProcessModel(createForm.processCode.trim(), createForm.processName.trim())
    createDialogVisible.value = false
    createForm.processCode = ''
    createForm.processName = ''
    ElMessage.success('流程草稿已创建')
    await router.push({ name: 'workflow-designer', params: { id: model.id } })
  } finally {
    creating.value = false
  }
}

function statusTagType(status: ProcessModelStatus): 'info' | 'success' | 'warning' {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'DISABLED') return 'warning'
  return 'info'
}

function goDesigner(id: number) {
  router.push({ name: 'workflow-designer', params: { id } })
}

const historyDialogVisible = ref(false)
const historyModelId = ref<number | null>(null)
function openHistory(id: number) {
  historyModelId.value = id
  historyDialogVisible.value = true
}

async function handlePublish(id: number) {
  try {
    await ElMessageBox.confirm('发布后将编译当前草稿为新的不可变版本并立即部署，确定发布吗？', '发布确认', {
      type: 'warning',
      confirmButtonText: '确定发布',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  const result = await workflowApi.publishProcessModel(id)
  ElMessage.success(`发布成功，新版本号 v${result.version}`)
  await fetchModels()
}

async function handleDisable(id: number) {
  try {
    await ElMessageBox.confirm(
      '下线后将拒绝以该流程编码发起新的流程实例，运行中的实例不受影响，确定下线吗？',
      '下线确认',
      { type: 'warning', confirmButtonText: '确定下线', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await workflowApi.disableProcessModel(id)
  ElMessage.success('已下线')
  await fetchModels()
}

async function handleEnable(id: number) {
  await workflowApi.enableProcessModel(id)
  ElMessage.success('已重新启用')
  await fetchModels()
}

const canView = computed(() => hasPermission('WorkflowDesign:model:view'))
const canPublish = computed(() => hasPermission('WorkflowDesign:model:publish'))
const canDisable = computed(() => hasPermission('WorkflowDesign:model:disable'))
</script>

<template>
  <div class="process-model-panel">
    <header class="process-model-panel__header">
      <h2 class="process-model-panel__title">流程模型</h2>
      <el-button v-if="hasPermission('WorkflowDesign:model:edit')" type="primary" @click="createDialogVisible = true">
        新建流程
      </el-button>
    </header>

    <el-table v-loading="loading" :data="models" empty-text="暂无流程模型">
      <el-table-column prop="processCode" label="流程编码" min-width="200" />
      <el-table-column prop="processName" label="流程名称" min-width="160" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType((row as ProcessModelRow).status)">
            {{ PROCESS_MODEL_STATUS_LABEL[(row as ProcessModelRow).status] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="currentDefinitionId" label="当前生效版本 id" min-width="140" />
      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="goDesigner((row as ProcessModelRow).id)">进入设计器</el-button>
          <el-button v-if="canView" link type="primary" @click="openHistory((row as ProcessModelRow).id)">版本历史</el-button>
          <el-button v-if="canPublish" link type="primary" @click="handlePublish((row as ProcessModelRow).id)">发布</el-button>
          <el-button
            v-if="canDisable && (row as ProcessModelRow).status === 'PUBLISHED'"
            link
            type="warning"
            @click="handleDisable((row as ProcessModelRow).id)"
          >
            下线
          </el-button>
          <el-button
            v-if="canDisable && (row as ProcessModelRow).status === 'DISABLED'"
            link
            type="success"
            @click="handleEnable((row as ProcessModelRow).id)"
          >
            启用
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <VersionHistoryDialog v-if="historyModelId" v-model="historyDialogVisible" :model-id="historyModelId" />

    <el-dialog v-model="createDialogVisible" title="新建流程模型" width="440px" :close-on-click-modal="false">
      <el-form label-width="84px" @submit.prevent="handleCreate">
        <el-form-item label="流程编码" required>
          <el-input v-model="createForm.processCode" placeholder="如 USER_CHANGE" maxlength="64" />
        </el-form-item>
        <el-form-item label="流程名称" required>
          <el-input v-model="createForm.processName" placeholder="如 人员变更审批" maxlength="128" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">创建并设计</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.process-model-panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

.process-model-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.process-model-panel__title {
  font-size: 15px;
  color: var(--color-ink);
  margin: 0;
}

:deep(.el-table .el-button + .el-button) {
  margin-left: 6px;
}
</style>
