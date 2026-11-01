<script setup lang="ts">
// 视频管理页：上传（带进度）+ 分页列表（文件名关键字/上传时间范围筛选）+ 单条/批量逻辑删除
// + 弹窗播放。见 openspec/changes/add-video-management，design.md Decision 3/7。
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadFile, UploadInstance } from 'element-plus'
import { VideoPlay } from '@element-plus/icons-vue'
import * as videoApi from '@/api/video'
import { DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS } from '@/constants/pagination'
import type { VideoVO } from '@/types/video'
import { usePermission } from '@/composables/usePermission'

const { hasPermission } = usePermission()

// ---- 筛选栏 + 分页表格 ----

const keyword = ref('')
// daterange 选择器绑定值：value-format 为 'YYYY-MM-DD'，未选择时为 null
const dateRange = ref<[string, string] | null>(null)

const list = ref<VideoVO[]>([])
const listLoading = ref(false)
const page = ref(1)
const pageSize = ref(DEFAULT_PAGE_SIZE)
const total = ref(0)

// 上传时间范围：起始日取 00:00:00，结束日取 23:59:59，与后端 startTime/endTime 的 ISO
// LocalDateTime 字符串格式对齐
function resolveTimeRange(): { startTime?: string; endTime?: string } {
  if (!dateRange.value) return {}
  const [start, end] = dateRange.value
  return { startTime: `${start}T00:00:00`, endTime: `${end}T23:59:59` }
}

async function fetchList() {
  listLoading.value = true
  try {
    const { startTime, endTime } = resolveTimeRange()
    const result = await videoApi.listVideos({
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

onMounted(fetchList)

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

// 文件大小展示：字节数转换为更易读的 KB/MB/GB
function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`
}

// 格式列：从内容类型 video/mp4 中取出 mp4 并大写展示
function formatVideoType(contentType: string): string {
  return (contentType.split('/')[1] ?? contentType).toUpperCase()
}

// ---- 上传：单文件，先选、再手动点击开始上传，展示进度 ----

const uploadDialogVisible = ref(false)
const uploadRef = ref<UploadInstance>()
const pendingFile = ref<File | null>(null)
const uploading = ref(false)
const uploadPercent = ref(0)

const acceptAttr = videoApi.VIDEO_ACCEPT_SUFFIXES.join(',')

// 前端预校验格式与大小（最终以后端校验为准），不合法时提示并清掉选择
function handleFileChange(file: UploadFile) {
  const raw = file.raw
  if (!raw) return
  const lowerName = raw.name.toLowerCase()
  const validSuffix = videoApi.VIDEO_ACCEPT_SUFFIXES.some((suffix) => lowerName.endsWith(suffix))
  if (!validSuffix) {
    ElMessage.error('仅支持 mp4/webm/ogg 格式的视频')
    uploadRef.value?.clearFiles()
    pendingFile.value = null
    return
  }
  if (raw.size > videoApi.VIDEO_MAX_FILE_SIZE) {
    ElMessage.error('视频文件大小不能超过 2GB')
    uploadRef.value?.clearFiles()
    pendingFile.value = null
    return
  }
  pendingFile.value = raw
}

function handleFileRemove() {
  pendingFile.value = null
}

// limit=1 时再次选择文件会触发 exceed：用新文件替换已选文件
function handleFileExceed(files: File[]) {
  uploadRef.value?.clearFiles()
  const next = files[0]
  if (next) {
    uploadRef.value?.handleStart(next as Parameters<UploadInstance['handleStart']>[0])
  }
}

function openUploadDialog() {
  uploadPercent.value = 0
  uploadDialogVisible.value = true
}

function resetUploadDialog() {
  pendingFile.value = null
  uploadPercent.value = 0
  uploadRef.value?.clearFiles()
}

// 上传进行中禁止关闭弹窗，避免用户误以为已取消而实际请求仍在后台进行
function beforeCloseUploadDialog(done: () => void) {
  if (uploading.value) {
    ElMessage.warning('视频上传中，请等待上传完成')
    return
  }
  done()
}

async function handleStartUpload() {
  if (!pendingFile.value) return
  uploading.value = true
  uploadPercent.value = 0
  try {
    await videoApi.uploadVideo(pendingFile.value, (percent) => {
      uploadPercent.value = percent
    })
    ElMessage.success('上传成功')
    uploadDialogVisible.value = false
    page.value = 1
    await fetchList()
  } finally {
    uploading.value = false
  }
}

// ---- 多选 + 单条/批量删除 ----

const selectedRows = ref<VideoVO[]>([])

function handleSelectionChange(rows: VideoVO[]) {
  selectedRows.value = rows
}

async function handleDelete(row: VideoVO) {
  await videoApi.deleteVideo(row.id)
  ElMessage.success('删除成功')
  await fetchList()
}

const batchDeleting = ref(false)

async function handleBatchDelete() {
  if (selectedRows.value.length === 0) return
  batchDeleting.value = true
  try {
    const deletedCount = await videoApi.batchDeleteVideos(selectedRows.value.map((row) => row.id))
    ElMessage.success(`成功删除 ${deletedCount} 个视频`)
    selectedRows.value = []
    await fetchList()
  } finally {
    batchDeleting.value = false
  }
}

// ---- 播放：打开弹窗时换取短期播放凭证，<video controls> 直接使用流式播放地址 ----

// 播放器默认音量（0~1），每次打开播放弹窗/重试时新建的播放器都从该音量开始，用户可自行调节
const DEFAULT_VIDEO_VOLUME = 0.4

const playerVisible = ref(false)
const playingVideo = ref<VideoVO | null>(null)
const streamUrl = ref('')
const playerLoading = ref(false)
const playerError = ref(false)

const playerTitle = computed(() => playingVideo.value?.originalFileName ?? '播放视频')

async function loadStream() {
  if (!playingVideo.value) return
  playerLoading.value = true
  playerError.value = false
  streamUrl.value = ''
  try {
    const { ticket } = await videoApi.createVideoPlayTicket(playingVideo.value.id)
    streamUrl.value = videoApi.buildVideoStreamUrl(playingVideo.value.id, ticket)
  } catch {
    playerError.value = true
  } finally {
    playerLoading.value = false
  }
}

function openPlayer(row: VideoVO) {
  playingVideo.value = row
  playerVisible.value = true
  loadStream()
}

// 在 loadstart（开始加载、早于 autoplay 实际出声）时设置默认音量，避免自动播放以 100% 音量起播
function handleVideoLoadStart(event: Event) {
  ;(event.target as HTMLVideoElement).volume = DEFAULT_VIDEO_VOLUME
}

// 凭证失效、文件缺失或浏览器不支持该编码时 <video> 触发 error，提示并允许重新换取凭证重试
function handleVideoError() {
  if (!streamUrl.value) return
  playerError.value = true
}

// 关闭弹窗时清空 src，让浏览器停止继续下载视频数据
function handlePlayerClosed() {
  streamUrl.value = ''
  playingVideo.value = null
  playerError.value = false
}
</script>

<template>
  <div class="video-management">
    <section class="video-panel">
      <header class="video-panel__header">
        <h2 class="video-panel__title">视频管理</h2>
        <div class="video-panel__actions">
          <el-popconfirm
            v-if="hasPermission('VideoManagement:video:delete')"
            title="确定要删除选中的视频吗？"
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
          <el-button v-if="hasPermission('VideoManagement:video:add')" type="primary" @click="openUploadDialog">
            上传视频
          </el-button>
        </div>
      </header>

      <el-form class="video-filter-form" inline @submit.prevent>
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

      <el-table v-loading="listLoading" :data="list" empty-text="暂无视频" @selection-change="handleSelectionChange">
        <el-table-column v-if="hasPermission('VideoManagement:video:delete')" type="selection" width="48" />
        <el-table-column label="文件名" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="video-name" @click="openPlayer(row as VideoVO)">
              <el-icon class="video-name__icon"><VideoPlay /></el-icon>
              {{ (row as VideoVO).originalFileName }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="格式" width="90">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ formatVideoType((row as VideoVO).contentType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="文件大小" width="120">
          <template #default="{ row }">{{ formatFileSize((row as VideoVO).fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="上传时间" width="170" />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openPlayer(row as VideoVO)">播放</el-button>
            <el-popconfirm
              v-if="hasPermission('VideoManagement:video:delete')"
              title="确定要删除这个视频吗？"
              confirm-button-text="删除"
              cancel-button-text="取消"
              @confirm="handleDelete(row as VideoVO)"
            >
              <template #reference>
                <el-button link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="video-pagination"
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

    <el-dialog
      v-model="uploadDialogVisible"
      title="上传视频"
      width="560px"
      :close-on-click-modal="false"
      :before-close="beforeCloseUploadDialog"
      @closed="resetUploadDialog"
    >
      <el-upload
        ref="uploadRef"
        drag
        :limit="1"
        :auto-upload="false"
        :accept="acceptAttr"
        :disabled="uploading"
        :on-change="handleFileChange"
        :on-remove="handleFileRemove"
        :on-exceed="handleFileExceed"
      >
        <div class="video-upload-dialog__tip">点击选择或拖拽视频文件到此处</div>
        <template #tip>
          <div class="video-upload-dialog__hint">支持 mp4/webm/ogg 格式，单个文件不超过 2GB</div>
        </template>
      </el-upload>

      <div v-if="uploading || uploadPercent > 0" class="video-upload-dialog__progress">
        <el-progress :percentage="uploadPercent" :status="uploadPercent === 100 && !uploading ? 'success' : undefined" />
        <p v-if="uploading && uploadPercent === 100" class="video-upload-dialog__hint">文件已发送，服务器保存中…</p>
      </div>

      <template #footer>
        <el-button :disabled="uploading" @click="uploadDialogVisible = false">关闭</el-button>
        <el-button type="primary" :loading="uploading" :disabled="!pendingFile" @click="handleStartUpload">
          开始上传
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="playerVisible"
      :title="playerTitle"
      width="860px"
      destroy-on-close
      @closed="handlePlayerClosed"
    >
      <div v-loading="playerLoading" class="video-player">
        <div v-if="playerError" class="video-player__error">
          <p>视频播放失败：凭证可能已过期，或当前浏览器不支持该视频的编码格式</p>
          <el-button type="primary" @click="loadStream">重试</el-button>
        </div>
        <video
          v-else-if="streamUrl"
          class="video-player__video"
          :src="streamUrl"
          controls
          autoplay
          preload="metadata"
          @loadstart="handleVideoLoadStart"
          @error="handleVideoError"
        />
      </div>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.video-panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

.video-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.video-panel__title {
  font-size: 15px;
  color: var(--color-ink);
  margin: 0;
}

.video-panel__actions {
  display: flex;
  gap: 8px;
}

.video-filter-form {
  margin-bottom: 8px;

  :deep(.el-form-item) {
    margin-bottom: 12px;
  }
}

.video-name {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-ink);
  cursor: pointer;

  &:hover {
    color: var(--el-color-primary);
  }
}

.video-name__icon {
  color: var(--el-color-primary);
}

.video-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.video-upload-dialog__tip {
  font-size: 13px;
  color: var(--color-text-tertiary);
  padding: 12px 0;
}

.video-upload-dialog__hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin: 6px 0 0;
}

.video-upload-dialog__progress {
  margin-top: 16px;
  border-top: 1px dashed var(--color-border);
  padding-top: 12px;
}

.video-player {
  min-height: 240px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #000;
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.video-player__video {
  width: 100%;
  max-height: 70vh;
  display: block;
}

.video-player__error {
  color: #fff;
  text-align: center;
  padding: 32px 16px;
  font-size: 13px;
}
</style>
