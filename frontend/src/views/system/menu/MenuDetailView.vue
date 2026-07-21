<script setup lang="ts">
// 菜单资源详情独立页面：原 MenuManagementView.vue 内的只读详情弹窗迁移至此，
// 通过路由参数 :id 加载数据，不再依赖父组件传入的 targetId。
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import OperationHistoryPanel from '@/components/OperationHistoryPanel.vue'
import * as menuApi from '@/api/menu'
import { MENU_RESOURCE_TYPE_LABELS, MENU_STATUS_ENABLED, type MenuResourceRow } from '@/types/menuResource'

const route = useRoute()
const router = useRouter()

const menuId = computed(() => Number(route.params.id))

const loading = ref(false)
const loadError = ref('')
const detailData = ref<MenuResourceRow | null>(null)

function resourceTypeLabel(resourceType: number): string {
  return MENU_RESOURCE_TYPE_LABELS[resourceType] ?? '-'
}

async function fetchDetail() {
  loading.value = true
  loadError.value = ''
  try {
    detailData.value = await menuApi.getMenuById(menuId.value)
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '加载资源详情失败'
  } finally {
    loading.value = false
  }
}

onMounted(fetchDetail)

// 显式跳回菜单资源列表路由，而不是 router.back()：详情页可能是直接通过 URL/刷新进入的，
// 浏览器历史栈在这种场景下可能为空或指向无关页面
function goBack() {
  router.push({ name: 'system-menus' })
}
</script>

<template>
  <div class="menu-detail">
    <header class="menu-detail__header">
      <el-button link :icon="ArrowLeft" class="menu-detail__back" @click="goBack">返回</el-button>
      <h2 class="menu-detail__title">资源详情</h2>
    </header>

    <el-alert
      v-if="loadError"
      class="menu-detail__error"
      type="error"
      :title="loadError"
      show-icon
      :closable="false"
    />

    <template v-else>
      <section class="menu-detail__panel">
        <el-descriptions v-loading="loading" :column="2" border>
          <el-descriptions-item label="资源名称">{{ detailData?.name }}</el-descriptions-item>
          <el-descriptions-item label="资源编码">{{ detailData?.code }}</el-descriptions-item>
          <el-descriptions-item label="上级资源">{{ detailData?.parentName ?? '顶级资源' }}</el-descriptions-item>
          <el-descriptions-item label="资源类型">
            {{ detailData ? resourceTypeLabel(detailData.resourceType) : '' }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag v-if="detailData?.status === MENU_STATUS_ENABLED" type="success">启用</el-tag>
            <el-tag v-else type="warning">停用</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="显示序号">{{ detailData?.showOrder }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detailData?.remark || '-' }}</el-descriptions-item>
          <el-descriptions-item label="新增人">{{ detailData?.createBy }}</el-descriptions-item>
          <el-descriptions-item label="新增时间">{{ detailData?.createTime }}</el-descriptions-item>
          <el-descriptions-item label="更新人">{{ detailData?.updateBy }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ detailData?.updateTime }}</el-descriptions-item>
        </el-descriptions>
      </section>

      <section class="menu-detail__panel">
        <OperationHistoryPanel resource-type="menu" :target-id="detailData?.id ?? null" />
      </section>
    </template>
  </div>
</template>

<style scoped lang="scss">
.menu-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.menu-detail__header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.menu-detail__back {
  font-size: 14px;
}

.menu-detail__title {
  font-size: 16px;
  color: var(--color-ink);
  margin: 0;
}

.menu-detail__error {
  margin-bottom: 4px;
}

.menu-detail__panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}
</style>
