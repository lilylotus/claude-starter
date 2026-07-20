<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import * as operationLogApi from '@/api/operationLog'
import {
  MODULE_OPTIONS,
  OPERATION_TYPE_CREATE,
  OPERATION_TYPE_DELETE,
  OPERATION_TYPE_DISABLE,
  OPERATION_TYPE_ENABLE,
  OPERATION_TYPE_OPTIONS,
  OPERATION_TYPE_UPDATE,
  RESOURCE_TYPE_OPTIONS,
  type OperationLogDetail,
  type OperationLogRow,
} from '@/types/operationLog'

// ---- 筛选栏 + 分页表格 ----

const filters = reactive({
  module: '',
  resourceType: '',
  operationType: undefined as number | undefined,
  createBy: '',
})

// datetimerange 选择器绑定值：value-format 为 'YYYY-MM-DDTHH:mm:ss'，未选择时为 null
const dateRange = ref<[string, string] | null>(null)

const list = ref<OperationLogRow[]>([])
const listLoading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

async function fetchList() {
  listLoading.value = true
  try {
    const result = await operationLogApi.getOperationLogPage({
      module: filters.module || undefined,
      resourceType: filters.resourceType || undefined,
      operationType: filters.operationType,
      createBy: filters.createBy.trim() || undefined,
      startTime: dateRange.value?.[0],
      endTime: dateRange.value?.[1],
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
})

function handleSearch() {
  page.value = 1
  fetchList()
}

function handleReset() {
  filters.module = ''
  filters.resourceType = ''
  filters.operationType = undefined
  filters.createBy = ''
  dateRange.value = null
  page.value = 1
  fetchList()
}

function handlePageChange(targetPage: number) {
  page.value = targetPage
  fetchList()
}

// 操作类型对应的标签颜色，仅作展示区分，不影响业务逻辑
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

// 取值为空（null/undefined/空字符串）时统一展示为空占位符
function displayValue(value: string | null | undefined): string {
  return value === null || value === undefined || value === '' ? '-' : value
}

// ---- 只读详情弹窗 ----

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref<OperationLogDetail | null>(null)

async function openDetailDialog(row: OperationLogRow) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detailData.value = await operationLogApi.getOperationLogById(row.id)
  } finally {
    detailLoading.value = false
  }
}
</script>

<template>
  <div class="log-management">
    <section class="log-panel">
      <header class="log-panel__header">
        <h2 class="log-panel__title">操作日志</h2>
      </header>

      <el-form class="log-filter-form" inline @submit.prevent>
        <el-form-item label="模块">
          <el-select v-model="filters.module" placeholder="全部模块" clearable style="width: 160px">
            <el-option v-for="opt in MODULE_OPTIONS" :key="opt" :label="opt" :value="opt" />
          </el-select>
        </el-form-item>
        <el-form-item label="资源类型">
          <el-select v-model="filters.resourceType" placeholder="全部资源类型" clearable style="width: 160px">
            <el-option v-for="opt in RESOURCE_TYPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作类型">
          <el-select v-model="filters.operationType" placeholder="全部操作类型" clearable style="width: 140px">
            <el-option v-for="opt in OPERATION_TYPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="filters.createBy" placeholder="请输入操作人" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="操作时间">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="listLoading" :data="list" empty-text="暂无操作日志">
        <el-table-column prop="createTime" label="操作时间" width="170" />
        <el-table-column prop="module" label="操作模块" min-width="110" />
        <el-table-column prop="resourceName" label="资源类型" width="100" />
        <el-table-column label="操作类型" width="100">
          <template #default="{ row }">
            <el-tag :type="operationTagType((row as OperationLogRow).operationType)">
              {{ (row as OperationLogRow).operationTypeLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetName" label="被操作对象" min-width="140" />
        <el-table-column prop="createBy" label="操作人" width="110" />
        <el-table-column label="操作发起 IP" width="140">
          <template #default="{ row }">{{ displayValue((row as OperationLogRow).operateIp) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetailDialog(row as OperationLogRow)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="log-pagination"
        background
        layout="prev, pager, next, total"
        :current-page="page"
        :page-size="pageSize"
        :total="total"
        @current-change="handlePageChange"
      />
    </section>

    <el-dialog v-model="detailVisible" title="操作日志详情" width="680px">
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
        <el-button type="primary" @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.log-panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

.log-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.log-panel__title {
  font-size: 15px;
  color: var(--color-ink);
  margin: 0;
}

.log-filter-form {
  margin-bottom: 8px;

  :deep(.el-form-item) {
    margin-bottom: 12px;
  }
}

.log-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.log-detail-changes {
  margin-top: 20px;
}

.log-detail-changes__title {
  font-size: 14px;
  color: var(--color-ink);
  margin: 0 0 8px;
}

// 操作列按钮间距收紧，与其他管理页面保持一致
:deep(.el-table .el-button + .el-button) {
  margin-left: 6px;
}
</style>
