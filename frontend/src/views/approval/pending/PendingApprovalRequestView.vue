<script setup lang="ts">
// "待我审批"：全部"待审批"状态的申请（不区分提交人），仅 ApprovalManagement:request:approve
// 权限点持有者可见（路由级门控见 router/index.ts，按钮级门控见下方 hasPermission 判断，
// 与其余管理页面"页面 + 按钮"两层门控的既有约定保持一致）。可查看详情、批准、拒绝
// （拒绝需要填写意见，走弹窗输入框）。
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import * as approvalApi from '@/api/approval'
import { PAGE_SIZE_OPTIONS } from '@/constants/pagination'
import { usePermission } from '@/composables/usePermission'
import ApprovalRequestDetailDialog from '@/components/ApprovalRequestDetailDialog.vue'
import {
  APPROVAL_BIZ_TYPE_OPTIONS,
  APPROVAL_OPERATION_TYPE_OPTIONS,
  type ApprovalBizType,
  type ApprovalOperationType,
  type ApprovalRequestRow,
} from '@/types/approval'

const { hasPermission } = usePermission()

const list = ref<ApprovalRequestRow[]>([])
const listLoading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const filterBizType = ref<ApprovalBizType | ''>('')
const filterOperationType = ref<ApprovalOperationType | ''>('')

async function fetchList(targetPage = page.value) {
  listLoading.value = true
  try {
    const result = await approvalApi.getPendingApprovalRequests({
      bizType: filterBizType.value || undefined,
      operationType: filterOperationType.value || undefined,
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

// ---- 批准 ----

async function handleApprove(row: ApprovalRequestRow) {
  await ElMessageBox.confirm('确定要批准该申请吗？批准后将立即执行对应的创建/更新/状态切换/删除操作。', '批准确认', {
    type: 'warning',
    confirmButtonText: '批准',
    cancelButtonText: '取消',
  })
  await approvalApi.approveApprovalRequest(row.id)
  ElMessage.success('已批准')
  await fetchList()
}

// ---- 拒绝：意见必填，走弹窗输入框 ----

const rejectDialogVisible = ref(false)
const rejectFormRef = ref<FormInstance>()
const rejectSubmitting = ref(false)
const rejectTargetId = ref<number | null>(null)
const rejectForm = reactive({ opinion: '' })

const rejectRules: FormRules = {
  opinion: [{ required: true, message: '请填写拒绝意见', trigger: 'blur' }],
}

function openRejectDialog(row: ApprovalRequestRow) {
  rejectTargetId.value = row.id
  rejectForm.opinion = ''
  rejectDialogVisible.value = true
}

function closeRejectDialog() {
  rejectDialogVisible.value = false
  rejectFormRef.value?.clearValidate()
}

async function submitReject() {
  const valid = await rejectFormRef.value?.validate().catch(() => false)
  if (!valid || rejectTargetId.value === null) return

  rejectSubmitting.value = true
  try {
    await approvalApi.rejectApprovalRequest(rejectTargetId.value, { opinion: rejectForm.opinion })
    ElMessage.success('已拒绝')
    rejectDialogVisible.value = false
    await fetchList()
  } finally {
    rejectSubmitting.value = false
  }
}
</script>

<template>
  <div class="pending-approval">
    <section class="pending-approval__panel">
      <header class="pending-approval__header">
        <h2 class="pending-approval__title">待我审批</h2>
      </header>

      <div class="pending-approval__filters">
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
        <el-table-column label="提交人" min-width="100">
          <template #default="{ row }">
            {{ (row as ApprovalRequestRow).createByName || (row as ApprovalRequestRow).createBy }}
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="提交时间" min-width="160" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row as ApprovalRequestRow)">详情</el-button>
            <el-button
              v-if="hasPermission('ApprovalManagement:request:approve')"
              link
              type="success"
              @click="handleApprove(row as ApprovalRequestRow)"
            >
              批准
            </el-button>
            <el-button
              v-if="hasPermission('ApprovalManagement:request:approve')"
              link
              type="danger"
              @click="openRejectDialog(row as ApprovalRequestRow)"
            >
              拒绝
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pending-approval__pagination"
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

    <el-dialog v-model="rejectDialogVisible" title="拒绝申请" width="480px" @close="closeRejectDialog">
      <el-form ref="rejectFormRef" :model="rejectForm" :rules="rejectRules" label-width="80px">
        <el-form-item label="拒绝意见" prop="opinion">
          <el-input v-model="rejectForm.opinion" type="textarea" :rows="3" placeholder="请填写拒绝意见" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="rejectSubmitting" @click="submitReject">确认拒绝</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.pending-approval {
  display: flex;
  flex-direction: column;
}

.pending-approval__panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

.pending-approval__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.pending-approval__title {
  font-size: 15px;
  color: var(--color-ink);
  margin: 0;
}

.pending-approval__filters {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.pending-approval__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

:deep(.el-table .el-button + .el-button) {
  margin-left: 6px;
}
</style>
