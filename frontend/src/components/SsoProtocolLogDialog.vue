<script setup lang="ts">
// 登录日志页面"查看 SSO 调用记录"弹窗：按 sessionId 精确查询某次成功的 SSO 登录
// 之后关联的全部 CAS/OAuth2 协议调用记录（服务票据签发/验证、授权码/令牌签发、
// 用户信息查询、登出等），分页展示。sessionId 只作为查询参数使用，不在页面上展示。
import { ref, watch } from 'vue'
import * as ssoProtocolLogApi from '@/api/ssoProtocolLog'
import { DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS } from '@/constants/pagination'
import {
  EVENT_TYPE_LABELS,
  PROTOCOL_LABELS,
  SSO_PROTOCOL_RESULT_SUCCESS,
  type SsoProtocolLogRow,
} from '@/types/ssoProtocolLog'

const props = defineProps<{
  modelValue: boolean
  sessionId: string | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const visible = ref(props.modelValue)

watch(
  () => props.modelValue,
  (value) => {
    visible.value = value
  },
)

watch(visible, (value) => {
  emit('update:modelValue', value)
})

const list = ref<SsoProtocolLogRow[]>([])
const listLoading = ref(false)
const page = ref(1)
const pageSize = ref(DEFAULT_PAGE_SIZE)
const total = ref(0)

async function fetchList() {
  if (!props.sessionId) return
  listLoading.value = true
  try {
    const result = await ssoProtocolLogApi.getSsoProtocolLogPage({
      sessionId: props.sessionId,
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

// 弹窗打开且 sessionId 有值时才加载列表，重新打开时重置回第一页
watch(
  [() => props.modelValue, () => props.sessionId],
  ([open, sessionId]) => {
    if (!open || !sessionId) return
    page.value = 1
    fetchList()
  },
)

function handlePageChange(targetPage: number) {
  page.value = targetPage
  fetchList()
}

function handleSizeChange(newSize: number) {
  pageSize.value = newSize
  page.value = 1
  fetchList()
}

function protocolLabel(protocol: string): string {
  return PROTOCOL_LABELS[protocol] ?? protocol
}

function eventTypeLabel(eventType: string): string {
  return EVENT_TYPE_LABELS[eventType] ?? eventType
}

function resultTagType(result: number): 'success' | 'danger' {
  return result === SSO_PROTOCOL_RESULT_SUCCESS ? 'success' : 'danger'
}

// 取值为空（null/undefined/空字符串）时统一展示为空占位符
function displayValue(value: string | number | null | undefined): string {
  return value === null || value === undefined || value === '' ? '-' : String(value)
}
</script>

<template>
  <el-dialog v-model="visible" title="SSO 调用记录" width="900px">
    <el-table v-loading="listLoading" :data="list" empty-text="暂无关联的 SSO 协议调用记录">
      <el-table-column prop="createTime" label="调用时间" width="170" />
      <el-table-column label="协议" width="90">
        <template #default="{ row }">{{ protocolLabel((row as SsoProtocolLogRow).protocol) }}</template>
      </el-table-column>
      <el-table-column label="事件类型" width="130">
        <template #default="{ row }">{{ eventTypeLabel((row as SsoProtocolLogRow).eventType) }}</template>
      </el-table-column>
      <el-table-column label="应用" min-width="120">
        <template #default="{ row }">{{ displayValue((row as SsoProtocolLogRow).appId) }}</template>
      </el-table-column>
      <el-table-column label="用户ID" width="90">
        <template #default="{ row }">{{ displayValue((row as SsoProtocolLogRow).userId) }}</template>
      </el-table-column>
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag :type="resultTagType((row as SsoProtocolLogRow).result)">
            {{ (row as SsoProtocolLogRow).resultLabel }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="失败原因" min-width="160">
        <template #default="{ row }">{{ displayValue((row as SsoProtocolLogRow).failReason) }}</template>
      </el-table-column>
      <el-table-column label="客户端IP" width="140">
        <template #default="{ row }">{{ displayValue((row as SsoProtocolLogRow).clientIp) }}</template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="sso-protocol-log-dialog__pagination"
      background
      layout="sizes, prev, pager, next, total"
      :page-sizes="[...PAGE_SIZE_OPTIONS]"
      :current-page="page"
      :page-size="pageSize"
      :total="total"
      @current-change="handlePageChange"
      @size-change="handleSizeChange"
    />

    <template #footer>
      <el-button type="primary" @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.sso-protocol-log-dialog__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
