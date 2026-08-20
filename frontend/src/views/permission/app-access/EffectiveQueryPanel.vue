<script setup lang="ts">
// 应用访问授权 - 最终生效权限查询子面板：切换"按用户查询"/"按应用查询"，结果表格
// 展示判定依据（人工授权/策略名称列表/已收回），"包含已收回"开关仅按用户查询模式下
// 有意义（对应后端 includeRevoked 参数）。
import { computed, onMounted, ref } from 'vue'
import * as appAccessApi from '@/api/appAccess'
import * as userApi from '@/api/user'
import * as appApi from '@/api/app'
import { PAGE_SIZE_OPTIONS, DEFAULT_PAGE_SIZE } from '@/constants/pagination'
import type { AppAccessEffectiveItemVO } from '@/types/appAccess'
import type { AppRow } from '@/types/app'

// ---- 查询主体切换：按用户 / 按应用 ----

const queryMode = ref<'byUser' | 'byApp'>('byUser')

// ---- 关联用户远程搜索选择器，与人工例外面板一致（手机号按 mobile 搜索，否则按 name 搜索） ----

const MOBILE_PATTERN = /^1\d{10}$/

interface UserOption {
  id: number
  name: string
  mobile: string
}

const userOptions = ref<UserOption[]>([])
const userSearchLoading = ref(false)
const selectedUserId = ref<number | null>(null)

async function remoteSearchUsers(query: string) {
  if (!query) {
    userOptions.value = []
    return
  }
  userSearchLoading.value = true
  try {
    const params = MOBILE_PATTERN.test(query) ? { mobile: query, pageSize: 20 } : { name: query, pageSize: 20 }
    const result = await userApi.getUserPage(params)
    userOptions.value = result.records.map((user) => ({ id: user.id, name: user.name, mobile: user.mobile }))
  } finally {
    userSearchLoading.value = false
  }
}

// ---- 应用下拉选项：一次性加载 ----

const appOptions = ref<AppRow[]>([])
const appOptionsLoaded = ref(false)
const selectedAppId = ref<number | null>(null)

async function ensureAppOptionsLoaded() {
  if (appOptionsLoaded.value) return
  const result = await appApi.getAppPage(1, 200)
  appOptions.value = result.records
  appOptionsLoaded.value = true
}

onMounted(() => {
  ensureAppOptionsLoaded()
})

// "包含已收回"开关，仅按用户查询模式下有意义
const includeRevoked = ref(false)

// ---- 查询结果 + 分页 ----

const list = ref<AppAccessEffectiveItemVO[]>([])
const listLoading = ref(false)
const page = ref(1)
const pageSize = ref(DEFAULT_PAGE_SIZE)
const total = ref(0)
const searched = ref(false)

const canQuery = computed(() => (queryMode.value === 'byUser' ? selectedUserId.value !== null : selectedAppId.value !== null))

async function fetchList() {
  if (!canQuery.value) return
  listLoading.value = true
  searched.value = true
  try {
    const result =
      queryMode.value === 'byUser'
        ? await appAccessApi.listEffectiveByUser(selectedUserId.value as number, {
            includeRevoked: includeRevoked.value,
            page: page.value,
            pageSize: pageSize.value,
          })
        : await appAccessApi.listEffectiveByApp(selectedAppId.value as number, {
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

function handleSearch() {
  page.value = 1
  fetchList()
}

function handleModeChange() {
  list.value = []
  total.value = 0
  searched.value = false
  page.value = 1
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

// ---- 判定依据展示 ----

function sourceTagType(item: AppAccessEffectiveItemVO): 'success' | 'primary' | 'danger' {
  if (item.sourceType === 'MANUAL_GRANT') return 'success'
  if (item.sourceType === 'REVOKED') return 'danger'
  return 'primary'
}

function sourceLabel(item: AppAccessEffectiveItemVO): string {
  if (item.sourceType === 'MANUAL_GRANT') return '人工追加授权'
  if (item.sourceType === 'REVOKED') return '已收回（人工收回覆盖）'
  return '策略授权'
}
</script>

<template>
  <div class="effective-panel">
    <header class="effective-panel__header">
      <h3 class="effective-panel__title">最终生效权限查询</h3>
    </header>

    <el-form class="effective-filter-form" inline @submit.prevent>
      <el-form-item label="查询方式">
        <el-radio-group v-model="queryMode" @change="handleModeChange">
          <el-radio-button value="byUser">按用户查询</el-radio-button>
          <el-radio-button value="byApp">按应用查询</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-form-item v-if="queryMode === 'byUser'" label="用户">
        <el-select
          v-model="selectedUserId"
          filterable
          remote
          reserve-keyword
          placeholder="输入姓名或手机号搜索用户"
          :remote-method="remoteSearchUsers"
          :loading="userSearchLoading"
          style="width: 220px"
        >
          <el-option
            v-for="opt in userOptions"
            :key="opt.id"
            :label="opt.mobile ? `${opt.name}（${opt.mobile}）` : opt.name"
            :value="opt.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item v-else label="应用">
        <el-select v-model="selectedAppId" filterable placeholder="请选择应用" style="width: 200px">
          <el-option v-for="app in appOptions" :key="app.id" :label="app.name" :value="app.id" />
        </el-select>
      </el-form-item>

      <el-form-item v-if="queryMode === 'byUser'" label="包含已收回">
        <el-switch v-model="includeRevoked" />
      </el-form-item>

      <el-form-item>
        <el-button type="primary" :disabled="!canQuery" @click="handleSearch">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="listLoading" :data="list" :empty-text="searched ? '暂无数据' : '请先选择查询条件后点击查询'">
      <el-table-column v-if="queryMode === 'byUser'" prop="appName" label="应用" min-width="160" />
      <el-table-column v-else prop="userName" label="用户" min-width="140" />
      <el-table-column label="判定依据" min-width="300">
        <template #default="{ row }">
          <el-tag :type="sourceTagType(row as AppAccessEffectiveItemVO)">
            {{ sourceLabel(row as AppAccessEffectiveItemVO) }}
          </el-tag>
          <span v-if="(row as AppAccessEffectiveItemVO).policyNames.length" class="effective-panel__policy-names">
            {{ (row as AppAccessEffectiveItemVO).policyNames.join('、') }}
          </span>
          <el-tooltip
            v-if="(row as AppAccessEffectiveItemVO).hasRequestControl"
            content="部分命中策略配置了请求控制（浏览器/IP 白名单），此结果仅为身份层面判定，实际访问是否放行还需满足对应策略的浏览器/IP 限制"
            placement="top"
          >
            <el-tag type="warning" size="small" effect="plain" class="effective-panel__request-control-tag">
              含请求控制
            </el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="effective-pagination"
      background
      layout="sizes, prev, pager, next, total"
      :page-sizes="[...PAGE_SIZE_OPTIONS]"
      :current-page="page"
      :page-size="pageSize"
      :total="total"
      @current-change="handlePageChange"
      @size-change="handleSizeChange"
    />
  </div>
</template>

<style scoped lang="scss">
.effective-panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

.effective-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.effective-panel__title {
  font-size: 15px;
  color: var(--color-ink);
  margin: 0;
}

.effective-filter-form {
  margin-bottom: 8px;

  :deep(.el-form-item) {
    margin-bottom: 12px;
  }
}

.effective-panel__policy-names {
  margin-left: 8px;
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.effective-panel__request-control-tag {
  margin-left: 8px;
  cursor: help;
}

.effective-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
