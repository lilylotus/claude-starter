<script setup lang="ts">
// 字典类型详情独立页面：原 DictManagementView.vue 内的字典类型只读详情弹窗迁移至此，
// 通过路由参数 :id 加载数据，不再依赖父组件传入的 targetId。
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import OperationHistoryPanel from '@/components/OperationHistoryPanel.vue'
import * as dictApi from '@/api/dict'
import { DICT_STATUS_ENABLED, type DictTypeRow } from '@/types/dict'

const route = useRoute()
const router = useRouter()

const dictTypeId = computed(() => Number(route.params.id))

const loading = ref(false)
const loadError = ref('')
const detailData = ref<DictTypeRow | null>(null)

async function fetchDetail() {
  loading.value = true
  loadError.value = ''
  try {
    detailData.value = await dictApi.getDictTypeById(dictTypeId.value)
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '加载字典类型详情失败'
  } finally {
    loading.value = false
  }
}

onMounted(fetchDetail)

// 字典类型和字典项详情都返回同一个字典管理列表页，显式跳回该路由，
// 而不是 router.back()：详情页可能是直接通过 URL/刷新进入的，浏览器历史栈在这种
// 场景下可能为空或指向无关页面
function goBack() {
  router.push({ name: 'system-dicts' })
}
</script>

<template>
  <div class="dict-type-detail">
    <header class="dict-type-detail__header">
      <el-button link :icon="ArrowLeft" class="dict-type-detail__back" @click="goBack">返回</el-button>
      <h2 class="dict-type-detail__title">字典类型详情</h2>
    </header>

    <el-alert
      v-if="loadError"
      class="dict-type-detail__error"
      type="error"
      :title="loadError"
      show-icon
      :closable="false"
    />

    <template v-else>
      <section class="dict-type-detail__panel">
        <el-descriptions v-loading="loading" :column="2" border>
          <el-descriptions-item label="类型名称">{{ detailData?.name }}</el-descriptions-item>
          <el-descriptions-item label="编码">{{ detailData?.code }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag v-if="detailData?.status === DICT_STATUS_ENABLED" type="success">启用</el-tag>
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

      <section class="dict-type-detail__panel">
        <OperationHistoryPanel resource-type="dictType" :target-id="detailData?.id ?? null" />
      </section>
    </template>
  </div>
</template>

<style scoped lang="scss">
.dict-type-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dict-type-detail__header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.dict-type-detail__back {
  font-size: 14px;
}

.dict-type-detail__title {
  font-size: 16px;
  color: var(--color-ink);
  margin: 0;
}

.dict-type-detail__error {
  margin-bottom: 4px;
}

.dict-type-detail__panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}
</style>
