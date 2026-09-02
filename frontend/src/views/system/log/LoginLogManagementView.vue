<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { QuestionFilled } from '@element-plus/icons-vue'
import * as loginLogApi from '@/api/loginLog'
import SsoProtocolLogDialog from '@/components/SsoProtocolLogDialog.vue'
import { DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS } from '@/constants/pagination'
import {
  LOGIN_METHOD_LABELS,
  LOGIN_METHOD_OPTIONS,
  LOGIN_RESULT_OPTIONS,
  LOGIN_RESULT_SUCCESS,
  type LoginLogRow,
  type LoginMethod,
} from '@/types/loginLog'

// ---- 筛选栏 + 分页表格 ----

const filters = reactive({
  loginAccount: '',
  loginResult: undefined as number | undefined,
  loginMethod: undefined as LoginMethod | undefined,
})

// datetimerange 选择器绑定值：value-format 为 'YYYY-MM-DDTHH:mm:ss'，未选择时为 null
const dateRange = ref<[string, string] | null>(null)

const list = ref<LoginLogRow[]>([])
const listLoading = ref(false)
const page = ref(1)
const pageSize = ref(DEFAULT_PAGE_SIZE)
const total = ref(0)

async function fetchList() {
  listLoading.value = true
  try {
    const result = await loginLogApi.getLoginLogPage({
      loginAccount: filters.loginAccount.trim() || undefined,
      loginResult: filters.loginResult,
      loginMethod: filters.loginMethod,
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
  filters.loginAccount = ''
  filters.loginResult = undefined
  filters.loginMethod = undefined
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

// 登录结果对应的标签颜色，仅作展示区分，不影响业务逻辑
function loginResultTagType(result: number): 'success' | 'danger' | 'info' {
  return result === LOGIN_RESULT_SUCCESS ? 'success' : 'danger'
}

// 取值为空（null/undefined/空字符串）时统一展示为空占位符
function displayValue(value: string | null | undefined): string {
  return value === null || value === undefined || value === '' ? '-' : value
}

// 把终端类型/操作系统/浏览器拼接为一列展示，过滤掉空值，避免出现 "null / Chrome" 这种脏字符串
function deviceInfo(row: LoginLogRow): string {
  const parts = [row.loginTerminal, row.loginOs, row.loginBrowser].filter(
    (part): part is string => !!part,
  )
  return parts.length > 0 ? parts.join(' / ') : '-'
}

// ---- 查看关联的 SSO 协议调用记录（仅登录成功且关联了 SSO 会话标识的行才展示入口） ----

function canViewSsoProtocolLogs(row: LoginLogRow): boolean {
  return row.loginResult === LOGIN_RESULT_SUCCESS && !!row.sessionId
}

const ssoProtocolLogVisible = ref(false)
const selectedSessionId = ref<string | null>(null)

function openSsoProtocolLogDialog(row: LoginLogRow) {
  selectedSessionId.value = row.sessionId
  ssoProtocolLogVisible.value = true
}
</script>

<template>
  <div class="log-management">
    <section class="log-panel">
      <header class="log-panel__header">
        <h2 class="log-panel__title">登录日志</h2>
      </header>

      <el-form class="log-filter-form" inline @submit.prevent>
        <el-form-item label="登录账号">
          <el-input v-model="filters.loginAccount" placeholder="请输入登录账号" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="登录结果">
          <el-select v-model="filters.loginResult" placeholder="全部结果" clearable style="width: 120px">
            <el-option v-for="opt in LOGIN_RESULT_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="登录方式">
          <el-select v-model="filters.loginMethod" placeholder="全部方式" clearable style="width: 120px">
            <el-option v-for="opt in LOGIN_METHOD_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="登录时间">
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

      <el-table v-loading="listLoading" :data="list" empty-text="暂无登录日志">
        <el-table-column prop="createTime" label="登录时间" width="170" />
        <el-table-column label="登录账号" min-width="130">
          <template #default="{ row }">{{ displayValue((row as LoginLogRow).loginAccount) }}</template>
        </el-table-column>
        <el-table-column label="用户姓名" width="110">
          <template #default="{ row }">{{ displayValue((row as LoginLogRow).userName) }}</template>
        </el-table-column>
        <el-table-column label="登录结果" width="100">
          <template #default="{ row }">
            <el-tag :type="loginResultTagType((row as LoginLogRow).loginResult)">
              {{ (row as LoginLogRow).loginResultLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="登录方式" width="90">
          <template #default="{ row }">{{ LOGIN_METHOD_LABELS[(row as LoginLogRow).loginMethod] }}</template>
        </el-table-column>
        <el-table-column label="失败原因" min-width="140">
          <template #default="{ row }">{{ displayValue((row as LoginLogRow).failReason) }}</template>
        </el-table-column>
        <el-table-column label="IP" width="140">
          <template #default="{ row }">{{ displayValue((row as LoginLogRow).loginIp) }}</template>
        </el-table-column>
        <el-table-column label="设备信息" min-width="180">
          <template #default="{ row }">{{ deviceInfo(row as LoginLogRow) }}</template>
        </el-table-column>
        <el-table-column min-width="220">
          <template #header>
            <span>会话ID</span>
            <el-tooltip content="会话标识（摘要值，不可用于登录）" placement="top">
              <el-icon class="log-panel__col-tip"><QuestionFilled /></el-icon>
            </el-tooltip>
          </template>
          <template #default="{ row }">{{ displayValue((row as LoginLogRow).sessionId) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="canViewSsoProtocolLogs(row as LoginLogRow)"
              link
              type="primary"
              @click="openSsoProtocolLogDialog(row as LoginLogRow)"
            >
              协议详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="log-pagination"
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

    <SsoProtocolLogDialog v-model="ssoProtocolLogVisible" :session-id="selectedSessionId" />
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

.log-panel__col-tip {
  margin-left: 4px;
  color: var(--color-text-secondary);
  vertical-align: middle;
  cursor: help;
}

.log-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
