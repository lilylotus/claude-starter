<script setup lang="ts">
// 嵌入各业务模块只读详情弹窗内的"操作历史"小型分页列表：展示该资源实例（resourceType +
// targetId）自身的新增/编辑/启用/停用历史，按操作发起时间降序排列。点击"查看变更"打开
// 复用的 OperationLogDetailDialog 展示该条记录的字段级变更详情。
import { ref, watch } from 'vue'
import * as operationLogApi from '@/api/operationLog'
import OperationLogDetailDialog from '@/components/OperationLogDetailDialog.vue'
import {
  OPERATION_TYPE_CREATE,
  OPERATION_TYPE_DELETE,
  OPERATION_TYPE_DISABLE,
  OPERATION_TYPE_ENABLE,
  OPERATION_TYPE_UPDATE,
  type OperationLogRow,
} from '@/types/operationLog'

const props = defineProps<{
  resourceType: string
  targetId: number | null
}>()

const PAGE_SIZE = 5

const list = ref<OperationLogRow[]>([])
const listLoading = ref(false)
const page = ref(1)
const total = ref(0)

async function fetchList() {
  if (props.targetId === null) {
    list.value = []
    total.value = 0
    return
  }
  listLoading.value = true
  try {
    const result = await operationLogApi.getOperationLogPage({
      resourceType: props.resourceType,
      targetId: props.targetId,
      page: page.value,
      pageSize: PAGE_SIZE,
    })
    list.value = result.records
    total.value = result.total
    page.value = result.page
  } finally {
    listLoading.value = false
  }
}

// targetId 为 null 代表父组件详情数据尚未加载完成的过渡态，不发请求、展示空状态；
// 一旦有值（含切换到另一条记录时），重置到第 1 页重新加载
watch(
  () => props.targetId,
  () => {
    page.value = 1
    fetchList()
  },
  { immediate: true },
)

function handlePageChange(targetPage: number) {
  page.value = targetPage
  fetchList()
}

// 操作类型对应的标签颜色，与独立操作日志页面保持一致
function operationTagType(type: number): 'success' | 'primary' | 'warning' | 'danger' | 'info' {
  switch (type) {
    case OPERATION_TYPE_CREATE:
    case OPERATION_TYPE_ENABLE:
      return 'success'
    case OPERATION_TYPE_UPDATE:
      return 'primary'
    case OPERATION_TYPE_DISABLE:
      return 'warning'
    case OPERATION_TYPE_DELETE:
      return 'danger'
    default:
      return 'info'
  }
}

// ---- 字段变更详情弹窗（叠在父详情弹窗之上） ----

const detailVisible = ref(false)
const selectedLogId = ref<number | null>(null)

function openDetailDialog(row: OperationLogRow) {
  selectedLogId.value = row.id
  detailVisible.value = true
}
</script>

<template>
  <div class="operation-history-panel">
    <h3 class="operation-history-panel__title">操作历史</h3>
    <el-table v-loading="listLoading" :data="list" size="small" empty-text="暂无操作记录">
      <el-table-column prop="createTime" label="操作时间" min-width="150" />
      <el-table-column label="操作类型" width="90">
        <template #default="{ row }">
          <el-tag :type="operationTagType((row as OperationLogRow).operationType)" size="small">
            {{ (row as OperationLogRow).operationTypeLabel }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createBy" label="操作人" min-width="100" />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetailDialog(row as OperationLogRow)">查看变更</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > 0"
      class="operation-history-panel__pagination"
      small
      layout="prev, pager, next"
      :current-page="page"
      :page-size="PAGE_SIZE"
      :total="total"
      @current-change="handlePageChange"
    />

    <OperationLogDetailDialog v-model="detailVisible" :log-id="selectedLogId" />
  </div>
</template>

<style scoped lang="scss">
.operation-history-panel {
  margin-top: 20px;
}

.operation-history-panel__title {
  font-size: 14px;
  color: var(--color-ink);
  margin: 0 0 8px;
}

.operation-history-panel__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
