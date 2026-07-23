<script setup lang="ts">
// 应用详情独立页面：原 AppManagementView.vue 内的只读详情弹窗迁移至此，通过路由参数
// :id 加载数据，不再依赖父组件传入的 targetId。
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import OperationHistoryPanel from '@/components/OperationHistoryPanel.vue'
import * as appApi from '@/api/app'
import { APP_STATUS_ENABLED, type AppRow } from '@/types/app'
import { useDynamicFormFields } from '@/composables/useDynamicFormFields'
import {
  FORM_FIELD_CONTROL_TYPE_DICT,
  FORM_FIELD_CONTROL_TYPE_MULTI_DICT,
  type FormFieldRenderItem,
} from '@/types/formField'

const route = useRoute()
const router = useRouter()

const appId = computed(() => Number(route.params.id))

const loading = ref(false)
const loadError = ref('')
const detailData = ref<AppRow | null>(null)

// 详情页展示当前启用状态的全部自定义字段定义（bizType=APP），不受 showInList/
// showInCreate/showInEdit 过滤——详情页是该资源的完整只读视图，见 design.md 决策 2
const appFields = useDynamicFormFields('APP')

// 按字段定义的 columnName 从详情数据里取值；detailData 未声明索引签名（与列表页
// AppRow 的既有约定一致，只在需要动态取值处就地转换），故此处显式转换一次
function extFieldValue(item: FormFieldRenderItem): unknown {
  if (!detailData.value) return undefined
  return (detailData.value as unknown as Record<string, unknown>)[item.columnName]
}

async function fetchDetail() {
  loading.value = true
  loadError.value = ''
  try {
    detailData.value = await appApi.getAppById(appId.value)
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '加载应用详情失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchDetail()
  appFields.fetchSchema()
})

// 显式跳回应用列表路由，而不是 router.back()：详情页可能是直接通过 URL/刷新进入的，
// 浏览器历史栈在这种场景下可能为空或指向无关页面
function goBack() {
  router.push({ name: 'application-list' })
}
</script>

<template>
  <div class="app-detail">
    <header class="app-detail__header">
      <el-button link :icon="ArrowLeft" class="app-detail__back" @click="goBack">返回</el-button>
      <h2 class="app-detail__title">应用详情</h2>
    </header>

    <el-alert
      v-if="loadError"
      class="app-detail__error"
      type="error"
      :title="loadError"
      show-icon
      :closable="false"
    />

    <template v-else>
      <section class="app-detail__panel">
        <el-descriptions v-loading="loading" :column="2" border>
          <el-descriptions-item label="应用名称">{{ detailData?.name }}</el-descriptions-item>
          <el-descriptions-item label="应用编码">{{ detailData?.code }}</el-descriptions-item>
          <el-descriptions-item label="负责人">{{ detailData?.ownerName }}</el-descriptions-item>
          <el-descriptions-item label="所属组织">{{ detailData?.orgName }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag v-if="detailData?.status === APP_STATUS_ENABLED" type="success">启用</el-tag>
            <el-tag v-else type="warning">停用</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="显示序号">{{ detailData?.showOrder }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detailData?.remark || '-' }}</el-descriptions-item>
          <el-descriptions-item
            v-for="item in appFields.schema.filter((f) => f.columnName.startsWith('ext'))"
            :key="item.fieldCode"
            :label="item.fieldName"
          >
            <span v-if="item.controlType === FORM_FIELD_CONTROL_TYPE_DICT">
              {{ appFields.dictOptionLabel(item, extFieldValue(item)) || '-' }}
            </span>
            <span v-else-if="item.controlType === FORM_FIELD_CONTROL_TYPE_MULTI_DICT">
              {{ appFields.dictOptionLabels(item, extFieldValue(item)) || '-' }}
            </span>
            <span v-else>{{ (extFieldValue(item) as string) || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="创建人">{{ detailData?.createBy }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detailData?.createTime }}</el-descriptions-item>
          <el-descriptions-item label="更新人">{{ detailData?.updateBy }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ detailData?.updateTime }}</el-descriptions-item>
        </el-descriptions>
      </section>

      <section class="app-detail__panel">
        <OperationHistoryPanel resource-type="app" :target-id="detailData?.id ?? null" />
      </section>
    </template>
  </div>
</template>

<style scoped lang="scss">
.app-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.app-detail__header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.app-detail__back {
  font-size: 14px;
}

.app-detail__title {
  font-size: 16px;
  color: var(--color-ink);
  margin: 0;
}

.app-detail__error {
  margin-bottom: 4px;
}

.app-detail__panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}
</style>
