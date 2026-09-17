<script setup lang="ts">
// "审批历史"：当前登录用户自己已经审批过（同意/拒绝/转办/委派/加签/退回）的历史记录，
// 按业务对象类型过滤、标准分页展示。数据来自工作流引擎的已办任务查询
// （GET /api/v1/workflow/tasks/done），页面结构参照"我的申请"/"待我审批"既有列表页
// （顶部筛选栏 + el-table + 分页），仅本次不提供"查看详情"操作列（design.md Non-Goals）。
// 页面访问权限由路由 meta.permissionKey（ApprovalManagement:record:view）在路由守卫层
// 门控，本页面内不再重复权限判断（无操作按钮，无需按钮级 hasPermission 门控）。
import { onMounted, ref } from 'vue'
import * as workflowApi from '@/api/workflow'
import { PAGE_SIZE_OPTIONS } from '@/constants/pagination'
import { APPROVAL_BIZ_TYPE_OPTIONS, type ApprovalBizType } from '@/types/approval'
import { APPROVAL_RECORD_ACTION_LABEL, type ApprovalTaskVO } from '@/types/workflow'

const list = ref<ApprovalTaskVO[]>([])
const listLoading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const filterBizType = ref<ApprovalBizType | ''>('')

async function fetchList(targetPage = page.value) {
  listLoading.value = true
  try {
    const result = await workflowApi.getDoneApprovalTasks({
      businessType: filterBizType.value || undefined,
      page: targetPage,
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

function handleFilterChange() {
  fetchList(1)
}

function handleReset() {
  filterBizType.value = ''
  fetchList(1)
}

function handlePageChange(targetPage: number) {
  fetchList(targetPage)
}

function handlePageSizeChange(newSize: number) {
  pageSize.value = newSize
  fetchList(1)
}

// 处理结果文案：复用既有 APPROVAL_RECORD_ACTION_LABEL 映射，未覆盖的取值原样展示。
function actionLabel(action: string | null): string {
  if (!action) return '-'
  return APPROVAL_RECORD_ACTION_LABEL[action] ?? action
}
</script>

<template>
  <div class="approval-history">
    <section class="approval-history__panel">
      <header class="approval-history__header">
        <h2 class="approval-history__title">审批历史</h2>
      </header>

      <div class="approval-history__filters">
        <el-select
          v-model="filterBizType"
          placeholder="业务对象类型"
          clearable
          style="width: 160px"
          @change="handleFilterChange"
        >
          <el-option v-for="opt in APPROVAL_BIZ_TYPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-table v-loading="listLoading" :data="list">
        <el-table-column label="业务对象类型" width="120">
          <template #default="{ row }">
            {{ APPROVAL_BIZ_TYPE_OPTIONS.find((opt) => opt.value === (row as ApprovalTaskVO).businessType)?.label ?? (row as ApprovalTaskVO).businessType }}
          </template>
        </el-table-column>
        <el-table-column prop="nodeName" label="节点名称" min-width="120">
          <template #default="{ row }">{{ (row as ApprovalTaskVO).nodeName || '-' }}</template>
        </el-table-column>
        <el-table-column label="处理结果" width="100">
          <template #default="{ row }">{{ actionLabel((row as ApprovalTaskVO).action) }}</template>
        </el-table-column>
        <el-table-column label="处理意见" min-width="160">
          <template #default="{ row }">{{ (row as ApprovalTaskVO).remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="申请人" min-width="100">
          <template #default="{ row }">
            {{ (row as ApprovalTaskVO).applicantName || (row as ApprovalTaskVO).applicantId || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="finishedTime" label="处理时间" min-width="160">
          <template #default="{ row }">{{ (row as ApprovalTaskVO).finishedTime || '-' }}</template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="approval-history__pagination"
        background
        layout="sizes, prev, pager, next, total"
        :page-sizes="[...PAGE_SIZE_OPTIONS]"
        :current-page="page"
        :page-size="pageSize"
        :total="total"
        @current-change="handlePageChange"
        @size-change="handlePageSizeChange"
      />
    </section>
  </div>
</template>

<style scoped lang="scss">
.approval-history {
  display: flex;
  flex-direction: column;
}

.approval-history__panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

.approval-history__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.approval-history__title {
  font-size: 15px;
  color: var(--color-ink);
  margin: 0;
}

.approval-history__filters {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.approval-history__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
