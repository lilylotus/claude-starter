<script setup lang="ts">
// 嵌入各业务模块详情页的"操作历史"面板：展示该资源实例（resourceType + targetId）自身的
// 新增/编辑/启用/停用/删除历史，按操作发起时间降序排列。每条记录的字段级变更明细
// （changeDetail，随分页接口一并返回）默认直接铺开展示，不再需要点击"查看变更"二级弹窗。
import { ref, watch } from 'vue'
import * as operationLogApi from '@/api/operationLog'
import {
  OPERATION_SOURCE_IMPORT,
  OPERATION_TYPE_CREATE,
  OPERATION_TYPE_DELETE,
  OPERATION_TYPE_DISABLE,
  OPERATION_TYPE_ENABLE,
  OPERATION_TYPE_UPDATE,
  type OperationLogRow,
} from '@/types/operationLog'

const props = defineProps<{
  resourceType: string
  targetId: number | null
}>()

const PAGE_SIZE = 5

const list = ref<OperationLogRow[]>([])
const listLoading = ref(false)
const page = ref(1)
const total = ref(0)

async function fetchList() {
  if (props.targetId === null) {
    list.value = []
    total.value = 0
    return
  }
  listLoading.value = true
  try {
    const result = await operationLogApi.getOperationLogPage({
      resourceType: props.resourceType,
      targetId: props.targetId,
      page: page.value,
      pageSize: PAGE_SIZE,
    })
    list.value = result.records
    total.value = result.total
    page.value = result.page
  } finally {
    listLoading.value = false
  }
}

// targetId 为 null 代表宿主详情数据尚未加载完成的过渡态，不发请求、展示空状态；
// 一旦有值（含切换到另一条记录时），重置到第 1 页重新加载
watch(
  () => props.targetId,
  () => {
    page.value = 1
    fetchList()
  },
  { immediate: true },
)

function handlePageChange(targetPage: number) {
  page.value = targetPage
  fetchList()
}

// 操作类型对应的标签颜色，与独立操作日志页面保持一致
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
</script>

<template>
  <div class="operation-history-panel">
    <h3 class="operation-history-panel__title">操作历史</h3>

    <div v-loading="listLoading" class="history-timeline">
      <ol v-if="list.length > 0" class="history-timeline__list">
        <li v-for="row in list" :key="row.id" class="history-timeline__item">
          <span class="history-timeline__dot" />
          <div class="history-timeline__card">
            <header class="history-timeline__header">
              <span class="history-timeline__time mono">{{ row.createTime }}</span>
              <el-tag :type="operationTagType(row.operationType)" size="small">
                {{ row.operationTypeLabel }}
              </el-tag>
              <el-tag v-if="row.operateSource === OPERATION_SOURCE_IMPORT" type="info" size="small" effect="plain">
                Excel 导入
              </el-tag>
              <span class="history-timeline__operator">{{ row.createBy }}</span>
            </header>

            <ul v-if="row.changeDetail && row.changeDetail.length > 0" class="history-change-list">
              <li v-for="(change, index) in row.changeDetail" :key="index" class="history-change-list__item">
                <span class="history-change-list__field">{{ change.field }}</span>
                <span class="history-change-list__values">
                  <span class="history-change-list__old">{{ change.oldValue }}</span>
                  <span class="history-change-list__arrow">→</span>
                  <span class="history-change-list__new">{{ change.newValue }}</span>
                </span>
              </li>
            </ul>
            <p v-else class="history-change-list__empty">无字段变更明细</p>
          </div>
        </li>
      </ol>
      <el-empty v-else description="暂无操作记录" :image-size="64" />
    </div>

    <el-pagination
      v-if="total > 0"
      class="operation-history-panel__pagination"
      small
      layout="prev, pager, next"
      :current-page="page"
      :page-size="PAGE_SIZE"
      :total="total"
      @current-change="handlePageChange"
    />
  </div>
</template>

<style scoped lang="scss">
.operation-history-panel {
  margin-top: 20px;
}

.operation-history-panel__title {
  font-size: 14px;
  color: var(--color-ink);
  margin: 0 0 12px;
}

.operation-history-panel__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.history-timeline {
  min-height: 40px;
}

.history-timeline__list {
  list-style: none;
  margin: 0;
  padding: 0 0 0 4px;
}

// 每条历史记录用一条虚线 + 圆点串起来，呼应侧边栏子菜单连接线/概览页时间线的
// "链式连接"视觉语言
.history-timeline__item {
  position: relative;
  padding-left: 22px;
  padding-bottom: 18px;
  border-left: 1px dashed var(--chain-line-color);

  &:last-child {
    border-left-color: transparent;
    padding-bottom: 0;
  }
}

.history-timeline__dot {
  position: absolute;
  left: -5px;
  top: 4px;
  width: var(--chain-dot-size);
  height: var(--chain-dot-size);
  border-radius: 50%;
  background: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-soft);
}

.history-timeline__card {
  background: var(--color-canvas);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 12px 14px;
}

.history-timeline__header {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.history-timeline__time {
  font-size: 12.5px;
  color: var(--color-text-tertiary);
}

.history-timeline__operator {
  font-size: 13px;
  color: var(--color-text);
}

.history-change-list {
  list-style: none;
  margin: 0;
  padding: 8px 0 0;
  border-top: 1px dashed var(--color-border);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.history-change-list__item {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: 13px;
  flex-wrap: wrap;
}

.history-change-list__field {
  flex-shrink: 0;
  min-width: 72px;
  color: var(--color-text-secondary);
}

.history-change-list__values {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  color: var(--color-ink);
}

.history-change-list__old {
  color: var(--color-text-secondary);
}

.history-change-list__arrow {
  color: var(--color-primary);
}

.history-change-list__new {
  color: var(--color-ink);
  font-weight: 500;
}

.history-change-list__empty {
  margin: 8px 0 0;
  padding-top: 8px;
  border-top: 1px dashed var(--color-border);
  font-size: 12.5px;
  color: var(--color-text-tertiary);
}
</style>
