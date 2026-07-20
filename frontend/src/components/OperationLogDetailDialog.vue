<script setup lang="ts">
// 操作日志"字段变更详情"只读弹窗，从 OperationLogManagementView.vue 抽取而来，
// 供独立的操作日志页面与各业务模块详情弹窗内嵌的 OperationHistoryPanel 共用，
// 避免同一份展示逻辑在多处重复实现。
import { ref, watch } from 'vue'
import * as operationLogApi from '@/api/operationLog'
import type { OperationLogDetail } from '@/types/operationLog'

const props = defineProps<{
  modelValue: boolean
  logId: number | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const visible = ref(props.modelValue)
const detailLoading = ref(false)
const detailData = ref<OperationLogDetail | null>(null)

watch(
  () => props.modelValue,
  (value) => {
    visible.value = value
  },
)

watch(visible, (value) => {
  emit('update:modelValue', value)
})

// 弹窗打开且 logId 有值时才加载详情，避免弹窗关闭后 logId 变化触发多余请求
watch(
  [() => props.modelValue, () => props.logId],
  async ([open, logId]) => {
    if (!open || logId === null) return
    detailLoading.value = true
    try {
      detailData.value = await operationLogApi.getOperationLogById(logId)
    } finally {
      detailLoading.value = false
    }
  },
)

// 取值为空（null/undefined/空字符串）时统一展示为空占位符
function displayValue(value: string | null | undefined): string {
  return value === null || value === undefined || value === '' ? '-' : value
}
</script>

<template>
  <el-dialog v-model="visible" title="操作日志详情" width="680px">
    <el-descriptions v-loading="detailLoading" :column="2" border>
      <el-descriptions-item label="操作模块">{{ detailData?.module }}</el-descriptions-item>
      <el-descriptions-item label="资源类型">{{ detailData?.resourceName }}</el-descriptions-item>
      <el-descriptions-item label="操作类型">{{ detailData?.operationTypeLabel }}</el-descriptions-item>
      <el-descriptions-item label="被操作对象">{{ detailData?.targetName }}</el-descriptions-item>
      <el-descriptions-item label="操作人">{{ detailData?.createBy }}</el-descriptions-item>
      <el-descriptions-item label="操作发起时间">{{ detailData?.createTime }}</el-descriptions-item>
      <el-descriptions-item label="操作发起 IP">{{ displayValue(detailData?.operateIp) }}</el-descriptions-item>
      <el-descriptions-item label="操作终端">{{ displayValue(detailData?.operateTerminal) }}</el-descriptions-item>
      <el-descriptions-item label="操作系统">{{ displayValue(detailData?.operateOs) }}</el-descriptions-item>
      <el-descriptions-item label="操作浏览器">{{ displayValue(detailData?.operateBrowser) }}</el-descriptions-item>
      <el-descriptions-item label="原始 User-Agent" :span="2">
        {{ displayValue(detailData?.operateUserAgent) }}
      </el-descriptions-item>
    </el-descriptions>

    <div class="log-detail-changes">
      <h3 class="log-detail-changes__title">字段变更</h3>
      <el-table :data="detailData?.changeDetail ?? []" empty-text="无字段变更">
        <el-table-column prop="field" label="字段名" width="140" />
        <el-table-column label="旧值" min-width="160">
          <template #default="{ row }">{{ displayValue(row.oldValue) }}</template>
        </el-table-column>
        <el-table-column label="新值" min-width="160">
          <template #default="{ row }">{{ displayValue(row.newValue) }}</template>
        </el-table-column>
      </el-table>
    </div>

    <template #footer>
      <el-button type="primary" @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.log-detail-changes {
  margin-top: 20px;
}

.log-detail-changes__title {
  font-size: 14px;
  color: var(--color-ink);
  margin: 0 0 8px;
}
</style>
