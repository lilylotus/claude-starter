<script setup lang="ts">
// 图片管理页：上传 + 分页列表（文件名关键字/上传时间范围筛选）+ 单条/批量逻辑删除。
// 见 openspec/changes/add-image-gallery，design.md Decision 6/9/10。
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadFile, UploadFiles, UploadInstance, UploadRawFile } from 'element-plus'
import * as imageApi from '@/api/image'
import { DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS } from '@/constants/pagination'
import type { ImageBatchUploadResultVO, ImageVO } from '@/types/image'
import { usePermission } from '@/composables/usePermission'
import AuthorizedImage from '@/components/AuthorizedImage.vue'

const { hasPermission } = usePermission()

// ---- 筛选栏 + 分页表格 ----

const keyword = ref('')
// daterange 选择器绑定值：value-format 为 'YYYY-MM-DD'，未选择时为 null
const dateRange = ref<[string, string] | null>(null)

const list = ref<ImageVO[]>([])
const listLoading = ref(false)
const page = ref(1)
const pageSize = ref(DEFAULT_PAGE_SIZE)
const total = ref(0)

// 上传时间范围：起始日取 00:00:00，结束日取 23:59:59，与后端 startTime/endTime 的
// ISO LocalDateTime 字符串格式对齐（design.md Decision 10）
function resolveTimeRange(): { startTime?: string; endTime?: string } {
  if (!dateRange.value) return {}
  const [start, end] = dateRange.value
  return { startTime: `${start}T00:00:00`, endTime: `${end}T23:59:59` }
}

async function fetchList() {
  listLoading.value = true
  try {
    const { startTime, endTime } = resolveTimeRange()
    const result = await imageApi.listImages({
      page: page.value,
      pageSize: pageSize.value,
      keyword: keyword.value.trim() || undefined,
      startTime,
      endTime,
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
  keyword.value = ''
  dateRange.value = null
  page.value = 1
  fetchList()
}

function handlePageChange(targetPage: number) {
  page.value = targetPage
  fetchList()
}

function handleSizeChange(newSize: number) {
  pageSize.value = newSize
  page.value = 1
  fetchList()
}

// 文件大小展示：字节数转换为更易读的 KB/MB
function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

// ---- 上传：先选、再手动点击开始上传，支持一次选择多个文件 ----
// 后端批量上传接口一个文件失败不影响其它文件（design.md Decision 11），这里把上传弹窗内
// el-upload 的内部文件列表作为唯一数据源，on-change/on-remove 都重新同步一次 pendingFiles，
// 不用手动按 uid 去重/摘除。

const uploadDialogVisible = ref(false)
const uploadRef = ref<UploadInstance>()
const pendingFiles = ref<UploadRawFile[]>([])
const uploading = ref(false)
const uploadResult = ref<ImageBatchUploadResultVO[] | null>(null)

const uploadSuccessCount = computed(() => uploadResult.value?.filter((item) => item.success).length ?? 0)
const uploadFailedItems = computed(() => uploadResult.value?.filter((item) => !item.success) ?? [])

function syncPendingFiles(_file: UploadFile, uploadFiles: UploadFiles) {
  pendingFiles.value = uploadFiles
    .map((item) => item.raw)
    .filter((raw): raw is UploadRawFile => !!raw)
}

function resetUploadDialog() {
  pendingFiles.value = []
  uploadResult.value = null
  uploadRef.value?.clearFiles()
}

function openUploadDialog() {
  uploadResult.value = null
  uploadDialogVisible.value = true
}

async function handleStartUpload() {
  if (pendingFiles.value.length === 0) return
  uploading.value = true
  try {
    const results = await imageApi.uploadImagesBatch(pendingFiles.value)
    uploadResult.value = results
    const successCount = results.filter((item) => item.success).length
    const failedCount = results.length - successCount
    if (failedCount === 0) {
      ElMessage.success(`成功上传 ${successCount} 张图片`)
    } else {
      ElMessage.warning(`成功上传 ${successCount} 张，失败 ${failedCount} 张，详情见下方列表`)
    }
    pendingFiles.value = []
    uploadRef.value?.clearFiles()
    page.value = 1
    await fetchList()
  } finally {
    uploading.value = false
  }
}

// ---- 多选 + 单条/批量删除 ----

const selectedRows = ref<ImageVO[]>([])

function handleSelectionChange(rows: ImageVO[]) {
  selectedRows.value = rows
}

async function handleDelete(row: ImageVO) {
  await imageApi.deleteImage(row.id)
  ElMessage.success('删除成功')
  await fetchList()
}

const batchDeleting = ref(false)

async function handleBatchDelete() {
  if (selectedRows.value.length === 0) return
  batchDeleting.value = true
  try {
    const ids = selectedRows.value.map((row) => row.id)
    const deletedCount = await imageApi.batchDeleteImages(ids)
    ElMessage.success(`成功删除 ${deletedCount} 张图片`)
    selectedRows.value = []
    await fetchList()
  } finally {
    batchDeleting.value = false
  }
}
</script>

<template>
  <div class="image-management">
    <section class="image-panel">
      <header class="image-panel__header">
        <h2 class="image-panel__title">图片管理</h2>
        <div class="image-panel__actions">
          <el-popconfirm
            v-if="hasPermission('ImageManagement:image:delete')"
            title="确定要删除选中的图片吗？"
            confirm-button-text="删除"
            cancel-button-text="取消"
            @confirm="handleBatchDelete"
          >
            <template #reference>
              <el-button type="danger" plain :disabled="selectedRows.length === 0" :loading="batchDeleting">
                批量删除
              </el-button>
            </template>
          </el-popconfirm>
          <el-button
            v-if="hasPermission('ImageManagement:image:add')"
            type="primary"
            @click="openUploadDialog"
          >
            上传图片
          </el-button>
        </div>
      </header>

      <el-form class="image-filter-form" inline @submit.prevent>
        <el-form-item label="文件名">
          <el-input v-model="keyword" placeholder="请输入文件名关键字" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="上传时间">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table
        v-loading="listLoading"
        :data="list"
        empty-text="暂无图片"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column label="缩略图" width="100">
          <template #default="{ row }">
            <AuthorizedImage
              :id="(row as ImageVO).id"
              :lazy="false"
              fit="cover"
              class="image-management__thumb"
            />
          </template>
        </el-table-column>
        <el-table-column prop="originalFileName" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column label="文件大小" width="110">
          <template #default="{ row }">{{ formatFileSize((row as ImageVO).fileSize) }}</template>
        </el-table-column>
        <el-table-column label="图片尺寸" width="120">
          <template #default="{ row }">
            {{ (row as ImageVO).imageWidth }} x {{ (row as ImageVO).imageHeight }}
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="上传时间" width="170" />
        <el-table-column v-if="hasPermission('ImageManagement:image:delete')" label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-popconfirm
              title="确定要删除这张图片吗？"
              confirm-button-text="删除"
              cancel-button-text="取消"
              @confirm="handleDelete(row as ImageVO)"
            >
              <template #reference>
                <el-button link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="image-pagination"
        background
        layout="sizes, prev, pager, next, total"
        :page-sizes="[...PAGE_SIZE_OPTIONS]"
        :current-page="page"
        :page-size="pageSize"
        :total="total"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </section>

    <el-dialog v-model="uploadDialogVisible" title="上传图片" width="560px" @close="resetUploadDialog">
      <el-upload
        ref="uploadRef"
        multiple
        drag
        :auto-upload="false"
        accept=".jpg,.jpeg,.png,.gif"
        :on-change="syncPendingFiles"
        :on-remove="syncPendingFiles"
      >
        <div class="image-upload-dialog__tip">点击选择或拖拽多个文件到此处</div>
        <template #tip>
          <div class="image-upload-dialog__hint">支持 jpg/jpeg/png/gif 格式，可一次选择多个文件</div>
        </template>
      </el-upload>

      <div v-if="uploadResult" class="image-upload-dialog__result">
        <p class="image-upload-dialog__summary">
          成功 {{ uploadSuccessCount }} 张，失败 {{ uploadFailedItems.length }} 张
        </p>
        <ul v-if="uploadFailedItems.length > 0" class="image-upload-dialog__failed-list">
          <li v-for="item in uploadFailedItems" :key="item.originalFileName">
            {{ item.originalFileName }}：{{ item.message }}
          </li>
        </ul>
      </div>

      <template #footer>
        <el-button @click="uploadDialogVisible = false">关闭</el-button>
        <el-button
          type="primary"
          :loading="uploading"
          :disabled="pendingFiles.length === 0"
          @click="handleStartUpload"
        >
          开始上传
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.image-panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

.image-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.image-panel__title {
  font-size: 15px;
  color: var(--color-ink);
  margin: 0;
}

.image-panel__actions {
  display: flex;
  gap: 8px;
}

.image-filter-form {
  margin-bottom: 8px;

  :deep(.el-form-item) {
    margin-bottom: 12px;
  }
}

.image-management__thumb {
  width: 64px;
  height: 64px;
  border-radius: var(--radius-sm);
  cursor: pointer;
}

.image-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.image-upload-dialog__tip {
  font-size: 13px;
  color: var(--color-text-tertiary);
  padding: 12px 0;
}

.image-upload-dialog__hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.image-upload-dialog__result {
  margin-top: 16px;
  border-top: 1px dashed var(--color-border);
  padding-top: 12px;
}

.image-upload-dialog__summary {
  color: var(--color-text-primary);
  font-weight: 600;
  margin: 0 0 8px;
}

.image-upload-dialog__failed-list {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  color: var(--color-danger, #f56c6c);
}
</style>
