<script setup lang="ts">
// 流程模型版本历史弹窗：按版本号倒序展示全部已发布版本（发布人/发布时间/状态），点击
// "查看快照"展开该版本发布时刻的 DSL 快照，只读格式化 JSON 展示，不提供编辑入口
// （workflow-approval-engine change specs/workflow-process-designer "版本历史查看"
// Requirement）。对应权限点 WorkflowDesign:model:view。
import { ref, watch } from 'vue'
import * as workflowApi from '@/api/workflow'
import { PROCESS_MODEL_STATUS_LABEL, type ProcessDefinitionVersionVO } from '@/types/workflow'

const props = defineProps<{
  modelValue: boolean
  modelId: number
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const loading = ref(false)
const versions = ref<ProcessDefinitionVersionVO[]>([])

async function fetchVersions() {
  loading.value = true
  try {
    versions.value = await workflowApi.listProcessModelVersions(props.modelId)
  } finally {
    loading.value = false
  }
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) fetchVersions()
  },
)

function formatSnapshot(snapshot: string): string {
  try {
    return JSON.stringify(JSON.parse(snapshot), null, 2)
  } catch {
    return snapshot
  }
}

function close() {
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog :model-value="modelValue" title="版本历史" width="760px" @update:model-value="(v: boolean) => !v && close()">
    <el-table v-loading="loading" :data="versions" empty-text="暂无已发布版本">
      <el-table-column label="版本" width="90">
        <template #default="{ row }">v{{ (row as ProcessDefinitionVersionVO).version }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="(row as ProcessDefinitionVersionVO).status === 'PUBLISHED' ? 'success' : 'info'">
            {{ PROCESS_MODEL_STATUS_LABEL[(row as ProcessDefinitionVersionVO).status] ?? (row as ProcessDefinitionVersionVO).status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="publishedBy" label="发布人" width="120" />
      <el-table-column prop="publishedTime" label="发布时间" min-width="160" />
      <el-table-column label="DSL 快照" min-width="120">
        <template #default="{ row }">
          <el-popover placement="left" width="480" trigger="click">
            <template #reference>
              <el-button link type="primary">查看快照</el-button>
            </template>
            <pre class="version-snapshot">{{ formatSnapshot((row as ProcessDefinitionVersionVO).modelJsonSnapshot) }}</pre>
          </el-popover>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-button @click="close">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.version-snapshot {
  max-height: 360px;
  overflow: auto;
  margin: 0;
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
