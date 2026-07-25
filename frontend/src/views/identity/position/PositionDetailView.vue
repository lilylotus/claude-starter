<script setup lang="ts">
// 任职详情独立页面：原 PositionManagementView.vue 内的只读详情弹窗迁移至此，通过路由
// 参数 :id 加载数据，不再依赖父组件传入的 targetId。
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import OperationHistoryPanel from '@/components/OperationHistoryPanel.vue'
import * as positionApi from '@/api/position'
import { POSITION_STATUS_ENABLED, type PositionRow } from '@/types/position'
import { useDynamicFormFields } from '@/composables/useDynamicFormFields'
import {
  FORM_FIELD_CONTROL_TYPE_DICT,
  FORM_FIELD_CONTROL_TYPE_MULTI_DICT,
  type FormFieldRenderItem,
} from '@/types/formField'

const route = useRoute()
const router = useRouter()

const positionId = computed(() => Number(route.params.id))

const loading = ref(false)
const loadError = ref('')
const detailData = ref<PositionRow | null>(null)

// 详情页展示当前启用状态的全部自定义字段定义（bizType=POSITION），不受 showInList/
// showInCreate/showInEdit 过滤——详情页是该资源的完整只读视图，见 design.md 决策 2
const positionFields = useDynamicFormFields('POSITION')

// 按字段定义的 columnName 从详情数据里取值；detailData 未声明索引签名（与列表页
// PositionRow 的既有约定一致，只在需要动态取值处就地转换），故此处显式转换一次
function extFieldValue(item: FormFieldRenderItem): unknown {
  if (!detailData.value) return undefined
  return (detailData.value as unknown as Record<string, unknown>)[item.columnName]
}

// 任职类型（positionType）字典编码 → 标签展示：不再单独维护字典选项/翻译函数，改为
// 从 positionFields.schema 中找到 positionType 对应的字段定义，复用扩展字段共用的
// dictOptionLabel() 转换逻辑，见 design.md 决策 2
const positionTypeField = computed(() => positionFields.schema.find((item) => item.columnName === 'positionType'))

async function fetchDetail() {
  loading.value = true
  loadError.value = ''
  try {
    detailData.value = await positionApi.getPositionById(positionId.value)
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '加载任职详情失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchDetail()
  positionFields.fetchSchema()
})

// 显式跳回任职列表路由，而不是 router.back()：详情页可能是直接通过 URL/刷新进入的，
// 浏览器历史栈在这种场景下可能为空或指向无关页面
function goBack() {
  router.push({ name: 'identity-positions' })
}
</script>

<template>
  <div class="position-detail">
    <header class="position-detail__header">
      <el-button link :icon="ArrowLeft" class="position-detail__back" @click="goBack">返回</el-button>
      <h2 class="position-detail__title">任职详情</h2>
    </header>

    <el-alert
      v-if="loadError"
      class="position-detail__error"
      type="error"
      :title="loadError"
      show-icon
      :closable="false"
    />

    <template v-else>
      <section class="position-detail__panel">
        <el-descriptions v-loading="loading" :column="2" border>
          <el-descriptions-item label="所属用户">{{ detailData?.userName }}</el-descriptions-item>
          <el-descriptions-item label="所属组织">{{ detailData?.orgName }}</el-descriptions-item>
          <el-descriptions-item label="任职类型">
            {{ (positionTypeField && positionFields.dictOptionLabel(positionTypeField, detailData?.positionType)) || detailData?.positionType || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag v-if="detailData?.status === POSITION_STATUS_ENABLED" type="success">启用</el-tag>
            <el-tag v-else type="warning">停用</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="任职地址">{{ detailData?.positionAddress || '-' }}</el-descriptions-item>
          <el-descriptions-item label="任职电话">{{ detailData?.positionPhone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="显示序号">{{ detailData?.showOrder }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detailData?.remark || '-' }}</el-descriptions-item>
          <el-descriptions-item
            v-for="item in positionFields.schema.filter((f) => f.columnName.startsWith('ext'))"
            :key="item.fieldCode"
            :label="item.fieldName"
          >
            <span v-if="item.controlType === FORM_FIELD_CONTROL_TYPE_DICT">
              {{ positionFields.dictOptionLabel(item, extFieldValue(item)) || '-' }}
            </span>
            <span v-else-if="item.controlType === FORM_FIELD_CONTROL_TYPE_MULTI_DICT">
              {{ positionFields.dictOptionLabels(item, extFieldValue(item)) || '-' }}
            </span>
            <span v-else>{{ (extFieldValue(item) as string) || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="创建人">{{ detailData?.createBy }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detailData?.createTime }}</el-descriptions-item>
          <el-descriptions-item label="更新人">{{ detailData?.updateBy }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ detailData?.updateTime }}</el-descriptions-item>
        </el-descriptions>
      </section>

      <section class="position-detail__panel">
        <OperationHistoryPanel resource-type="position" :target-id="detailData?.id ?? null" />
      </section>
    </template>
  </div>
</template>

<style scoped lang="scss">
.position-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.position-detail__header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.position-detail__back {
  font-size: 14px;
}

.position-detail__title {
  font-size: 16px;
  color: var(--color-ink);
  margin: 0;
}

.position-detail__error {
  margin-bottom: 4px;
}

.position-detail__panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}
</style>
