<script setup lang="ts">
// 聊天敏感词后台管理页面（/system/sensitive-words）：列表分页查询、新增、删除、
// 启用/停用（chat-security spec"敏感词库后台管理"需求，tasks.md 6.9）。只是一个独立的
// 管理表格页面，不需要跨组件共享状态，未使用 Pinia store，纯组件内 ref 维护即可。
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import * as chatApi from '@/api/chat'
import { PAGE_SIZE_OPTIONS } from '@/constants/pagination'
import { SENSITIVE_WORD_STATUS_ENABLED, type SensitiveWordVO } from '@/types/chat'
import { usePermission } from '@/composables/usePermission'

const { hasPermission } = usePermission()

const list = ref<SensitiveWordVO[]>([])
const listLoading = ref(false)

const searchForm = reactive<{ keyword: string; status: number | null }>({ keyword: '', status: null })

const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

async function fetchPage(targetPage?: number) {
  if (targetPage !== undefined) page.value = targetPage
  listLoading.value = true
  try {
    const result = await chatApi.getSensitiveWordPage({
      keyword: searchForm.keyword || undefined,
      status: searchForm.status ?? undefined,
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
  fetchPage()
})

function handleSearch() {
  fetchPage(1)
}

function handleReset() {
  searchForm.keyword = ''
  searchForm.status = null
  fetchPage(1)
}

function handlePageChange(targetPage: number) {
  fetchPage(targetPage)
}

function handlePageSizeChange(newSize: number) {
  pageSize.value = newSize
  fetchPage(1)
}

// 增删改后刷新当前分页；若刷新后当前页超出新的总页数，则回退到最后一页
async function refreshAfterMutation() {
  await fetchPage()
  const lastPage = total.value === 0 ? 1 : Math.ceil(total.value / pageSize.value)
  if (page.value > lastPage) {
    await fetchPage(lastPage)
  }
}

// ---- 新增弹窗 ----

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<{ word: string }>({ word: '' })
const rules: FormRules = {
  word: [{ required: true, message: '请输入敏感词词条', trigger: 'blur' }],
}

function openCreateDialog() {
  form.word = ''
  dialogVisible.value = true
}

function closeDialog() {
  dialogVisible.value = false
  formRef.value?.clearValidate()
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await chatApi.createSensitiveWord({ word: form.word })
    ElMessage.success('新增成功')
    dialogVisible.value = false
    await refreshAfterMutation()
  } finally {
    submitting.value = false
  }
}

// ---- 行操作：启用/停用、删除 ----

async function toggleStatus(row: SensitiveWordVO) {
  if (row.status === SENSITIVE_WORD_STATUS_ENABLED) {
    await chatApi.disableSensitiveWord(row.id)
    ElMessage.success('已停用')
  } else {
    await chatApi.enableSensitiveWord(row.id)
    ElMessage.success('已启用')
  }
  await refreshAfterMutation()
}

async function handleDelete(row: SensitiveWordVO) {
  await ElMessageBox.confirm(`确定要删除敏感词「${row.word}」吗？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await chatApi.deleteSensitiveWord(row.id)
  ElMessage.success('删除成功')
  await refreshAfterMutation()
}
</script>

<template>
  <div class="sensitive-word-management">
    <section class="sensitive-word-panel">
      <header class="sensitive-word-panel__header">
        <h2 class="sensitive-word-panel__title">敏感词管理</h2>
        <el-button
          v-if="hasPermission('SensitiveWordManagement:sensitiveWord:add')"
          type="primary"
          @click="openCreateDialog"
        >
          新增
        </el-button>
      </header>

      <div class="sensitive-word-panel__search">
        <el-input
          v-model="searchForm.keyword"
          placeholder="按词条关键字搜索"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
        <el-select v-model="searchForm.status" placeholder="状态" clearable style="width: 140px">
          <el-option label="启用" :value="2000" />
          <el-option label="停用" :value="3000" />
        </el-select>
        <el-button @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-table v-loading="listLoading" :data="list" empty-text="暂无敏感词">
        <el-table-column prop="word" label="敏感词" min-width="160" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="(row as SensitiveWordVO).status === SENSITIVE_WORD_STATUS_ENABLED" type="success">启用</el-tag>
            <el-tag v-else type="warning">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createBy" label="创建人" width="120" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column prop="updateBy" label="更新人" width="120" />
        <el-table-column prop="updateTime" label="更新时间" width="170" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              :type="(row as SensitiveWordVO).status === SENSITIVE_WORD_STATUS_ENABLED ? 'warning' : 'success'"
              v-if="
                (row as SensitiveWordVO).status === SENSITIVE_WORD_STATUS_ENABLED
                  ? hasPermission('SensitiveWordManagement:sensitiveWord:disable')
                  : hasPermission('SensitiveWordManagement:sensitiveWord:enable')
              "
              @click="toggleStatus(row as SensitiveWordVO)"
            >
              {{ (row as SensitiveWordVO).status === SENSITIVE_WORD_STATUS_ENABLED ? '停用' : '启用' }}
            </el-button>
            <el-button
              v-if="hasPermission('SensitiveWordManagement:sensitiveWord:delete')"
              link
              type="danger"
              @click="handleDelete(row as SensitiveWordVO)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="sensitive-word-pagination"
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

    <el-dialog v-model="dialogVisible" title="新增敏感词" width="420px" @close="closeDialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="敏感词" prop="word">
          <el-input v-model="form.word" placeholder="请输入敏感词词条" maxlength="64" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.sensitive-word-panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

.sensitive-word-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.sensitive-word-panel__title {
  font-size: 15px;
  color: var(--color-ink);
  margin: 0;
}

.sensitive-word-panel__search {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.sensitive-word-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
