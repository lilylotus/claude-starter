<script setup lang="ts">
// "我的申请"：当前登录用户提交的全部审批申请，按状态/业务对象类型/操作类型过滤，
// 可撤回"待审批"状态的申请，点击可查看详情（新增类展示新值，编辑类展示新旧对照）。
// 自助操作，任何登录用户都能访问——见 router/menu.ts、router/index.ts 里对该路由
// permissionKey 的处理说明。
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as approvalApi from '@/api/approval'
import { PAGE_SIZE_OPTIONS } from '@/constants/pagination'
import ApprovalRequestDetailDialog from '@/components/ApprovalRequestDetailDialog.vue'
import {
  APPROVAL_BIZ_TYPE_OPTIONS,
  APPROVAL_OPERATION_TYPE_OPTIONS,
  APPROVAL_STATUS_OPTIONS,
  APPROVAL_STATUS_PENDING,
  type ApprovalBizType,
  type ApprovalOperationType,
  type ApprovalRequestRow,
} from '@/types/approval'

const list = ref<ApprovalRequestRow[]>([])
const listLoading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const filterBizType = ref<ApprovalBizType | ''>('')
const filterOperationType = ref<ApprovalOperationType | ''>('')
const filterStatus = ref<number | ''>('')

async function fetchList(targetPage = page.value) {
  listLoading.value = true
  try {
    const result = await approvalApi.getMyApprovalRequests({
      bizType: filterBizType.value || undefined,
      operationType: filterOperationType.value || undefined,
      status: filterStatus.value === '' ? undefined : filterStatus.value,
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
  filterOperationType.value = ''
  filterStatus.value = ''
  fetchList(1)
}

function handlePageChange(targetPage: number) {
  fetchList(targetPage)
}

function handlePageSizeChange(newSize: number) {
  pageSize.value = newSize
  fetchList(1)
}

// ---- 详情弹窗 ----

const detailVisible = ref(false)
const detailRow = ref<ApprovalRequestRow | null>(null)

function openDetail(row: ApprovalRequestRow) {
  detailRow.value = row
  detailVisible.value = true
}

// ---- 撤回：仅"待审批"状态可撤回 ----

async function handleCancel(row: ApprovalRequestRow) {
  await ElMessageBox.confirm('确定要撤回该申请吗？撤回后不会执行任何业务数据变更。', '撤回确认', {
    type: 'warning',
    confirmButtonText: '撤回',
    cancelButtonText: '取消',
  })
  await approvalApi.cancelApprovalRequest(row.id)
  ElMessage.success('已撤回')
  await fetchList()
}
</script>

<template>
  <div class="my-approval">
    <section class="my-approval__panel">
      <header class="my-approval__header">
        <h2 class="my-approval__title">我的申请</h2>
      </header>

      <div class="my-approval__filters">
        <el-select v-model="filterBizType" placeholder="业务对象类型" clearable style="width: 160px" @change="handleFilterChange">
          <el-option v-for="opt in APPROVAL_BIZ_TYPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-select
          v-model="filterOperationType"
          placeholder="操作类型"
          clearable
          style="width: 140px"
          @change="handleFilterChange"
        >
          <el-option v-for="opt in APPROVAL_OPERATION_TYPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-select v-model="filterStatus" placeholder="申请状态" clearable style="width: 140px" @change="handleFilterChange">
          <el-option v-for="opt in APPROVAL_STATUS_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-table v-loading="listLoading" :data="list">
        <el-table-column prop="id" label="申请编号" width="90" />
        <el-table-column label="业务对象类型" width="120">
          <template #default="{ row }">
            {{ APPROVAL_BIZ_TYPE_OPTIONS.find((opt) => opt.value === (row as ApprovalRequestRow).bizType)?.label }}
          </template>
        </el-table-column>
        <el-table-column label="操作类型" width="100">
          <template #default="{ row }">
            {{
              APPROVAL_OPERATION_TYPE_OPTIONS.find((opt) => opt.value === (row as ApprovalRequestRow).operationType)
                ?.label
            }}
          </template>
        </el-table-column>
        <el-table-column label="目标记录ID" width="100">
          <template #default="{ row }">{{ (row as ApprovalRequestRow).targetId ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            {{ APPROVAL_STATUS_OPTIONS.find((opt) => opt.value === (row as ApprovalRequestRow).status)?.label }}
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="提交时间" min-width="160" />
        <el-table-column label="审批人" min-width="100">
          <template #default="{ row }">{{ (row as ApprovalRequestRow).approverName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="approveTime" label="审批时间" min-width="160" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row as ApprovalRequestRow)">详情</el-button>
            <el-button
              v-if="(row as ApprovalRequestRow).status === APPROVAL_STATUS_PENDING"
              link
              type="danger"
              @click="handleCancel(row as ApprovalRequestRow)"
            >
              撤回
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="my-approval__pagination"
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

    <approval-request-detail-dialog v-model="detailVisible" :row="detailRow" />
  </div>
</template>

<style scoped lang="scss">
.my-approval {
  display: flex;
  flex-direction: column;
}

.my-approval__panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

.my-approval__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.my-approval__title {
  font-size: 15px;
  color: var(--color-ink);
  margin: 0;
}

.my-approval__filters {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.my-approval__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

:deep(.el-table .el-button + .el-button) {
  margin-left: 6px;
}
</style>
