<script setup lang="ts">
// 用户详情独立页面：原 UserManagementView.vue 内的只读详情弹窗（含任职记录表格）迁移至
// 此，通过路由参数 :id 加载数据，不再依赖父组件传入的 targetId。
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import OperationHistoryPanel from '@/components/OperationHistoryPanel.vue'
import * as userApi from '@/api/user'
import * as dictApi from '@/api/dict'
import { USER_GENDER_OPTIONS, USER_STATUS_ENABLED, type UserPositionRow, type UserRow } from '@/types/user'
import type { DictItemOption } from '@/types/dict'
import { useDynamicFormFields } from '@/composables/useDynamicFormFields'
import { FORM_FIELD_CONTROL_TYPE_DICT, type FormFieldRenderItem } from '@/types/formField'

const route = useRoute()
const router = useRouter()

const userId = computed(() => Number(route.params.id))

const loading = ref(false)
const loadError = ref('')
const detailData = ref<UserRow | null>(null)

// 详情页展示当前启用状态的全部自定义字段定义（bizType=USER），不受 showInList/
// showInCreate/showInEdit 过滤——详情页是该资源的完整只读视图，见 design.md 决策 2
const userFields = useDynamicFormFields('USER')

// 用户名下任职记录表格追加展示 bizType=POSITION 当前启用状态的自定义字段
const positionFields = useDynamicFormFields('POSITION')

// 按字段定义的 columnName 从用户详情数据里取值；detailData 未声明索引签名（与列表页
// UserRow 的既有约定一致，只在需要动态取值处就地转换），故此处显式转换一次
function extFieldValue(item: FormFieldRenderItem): unknown {
  if (!detailData.value) return undefined
  return (detailData.value as unknown as Record<string, unknown>)[item.columnName]
}

// 按字段定义的 columnName 从某条任职记录行里取值
function positionExtFieldValue(row: UserPositionRow, item: FormFieldRenderItem): unknown {
  return (row as unknown as Record<string, unknown>)[item.columnName]
}

function genderLabel(gender: number): string {
  return USER_GENDER_OPTIONS.find((opt) => opt.value === gender)?.label ?? '未知'
}

// ---- 任职类型下拉框选项（数据源为字典模块 position_type 字典类型下的启用项），
// 用于把任职记录里的 positionType 编码翻译为中文标签 ----

const positionTypeOptions = ref<DictItemOption[]>([])

async function fetchPositionTypeOptions() {
  positionTypeOptions.value = await dictApi.getDictItemOptions('position_type')
}

function positionTypeLabel(code: string): string {
  return positionTypeOptions.value.find((opt) => opt.code === code)?.label ?? code
}

async function fetchDetail() {
  loading.value = true
  loadError.value = ''
  try {
    detailData.value = await userApi.getUserById(userId.value)
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '加载用户详情失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchDetail()
  fetchPositionTypeOptions()
  userFields.fetchSchema()
  positionFields.fetchSchema()
})

// 显式跳回用户列表路由，而不是 router.back()：详情页可能是直接通过 URL/刷新进入的，
// 浏览器历史栈在这种场景下可能为空或指向无关页面
function goBack() {
  router.push({ name: 'identity-users' })
}
</script>

<template>
  <div class="user-detail">
    <header class="user-detail__header">
      <el-button link :icon="ArrowLeft" class="user-detail__back" @click="goBack">返回</el-button>
      <h2 class="user-detail__title">用户详情</h2>
    </header>

    <el-alert
      v-if="loadError"
      class="user-detail__error"
      type="error"
      :title="loadError"
      show-icon
      :closable="false"
    />

    <template v-else>
      <section class="user-detail__panel">
        <el-descriptions v-loading="loading" :column="2" border>
          <el-descriptions-item label="姓名">{{ detailData?.name }}</el-descriptions-item>
          <el-descriptions-item label="编号">{{ detailData?.code }}</el-descriptions-item>
          <el-descriptions-item label="性别">{{ genderLabel(detailData?.gender ?? 0) }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ detailData?.mobile || '-' }}</el-descriptions-item>
          <el-descriptions-item label="身份证号">{{ detailData?.idCard || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag v-if="detailData?.status === USER_STATUS_ENABLED" type="success">启用</el-tag>
            <el-tag v-else type="warning">停用</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="显示序号">{{ detailData?.showOrder }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detailData?.remark || '-' }}</el-descriptions-item>
          <el-descriptions-item
            v-for="item in userFields.schema.filter((f) => f.columnName.startsWith('ext'))"
            :key="item.fieldCode"
            :label="item.fieldName"
          >
            <span v-if="item.controlType === FORM_FIELD_CONTROL_TYPE_DICT">
              {{ userFields.dictOptionLabel(item, extFieldValue(item)) || '-' }}
            </span>
            <span v-else>{{ (extFieldValue(item) as string) || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="创建人">{{ detailData?.createBy }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detailData?.createTime }}</el-descriptions-item>
          <el-descriptions-item label="更新人">{{ detailData?.updateBy }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ detailData?.updateTime }}</el-descriptions-item>
        </el-descriptions>
      </section>

      <section class="user-detail__panel">
        <h3 class="user-detail__positions-title">任职记录</h3>
        <el-table :data="detailData?.positions ?? []" empty-text="暂无任职记录">
          <el-table-column prop="orgName" label="所属组织" min-width="120" />
          <el-table-column label="任职类型" min-width="100">
            <template #default="{ row }">{{ positionTypeLabel(row.positionType) }}</template>
          </el-table-column>
          <el-table-column prop="positionAddress" label="任职地址" min-width="140" />
          <el-table-column prop="positionPhone" label="任职电话" min-width="120" />
          <el-table-column prop="showOrder" label="显示序号" width="90" />
          <el-table-column prop="remark" label="备注" min-width="120" />
          <el-table-column
            v-for="item in positionFields.schema.filter((f) => f.columnName.startsWith('ext'))"
            :key="item.fieldCode"
            :label="item.fieldName"
            min-width="120"
          >
            <template #default="{ row }">
              <span v-if="item.controlType === FORM_FIELD_CONTROL_TYPE_DICT">
                {{ positionFields.dictOptionLabel(item, positionExtFieldValue(row as UserPositionRow, item)) || '-' }}
              </span>
              <span v-else>{{ positionExtFieldValue(row as UserPositionRow, item) || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="createBy" label="创建人" min-width="100" />
          <el-table-column prop="createTime" label="创建时间" min-width="160" />
          <el-table-column prop="updateBy" label="更新人" min-width="100" />
          <el-table-column prop="updateTime" label="更新时间" min-width="160" />
        </el-table>
      </section>

      <section class="user-detail__panel">
        <OperationHistoryPanel resource-type="user" :target-id="detailData?.id ?? null" />
      </section>
    </template>
  </div>
</template>

<style scoped lang="scss">
.user-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.user-detail__header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-detail__back {
  font-size: 14px;
}

.user-detail__title {
  font-size: 16px;
  color: var(--color-ink);
  margin: 0;
}

.user-detail__error {
  margin-bottom: 4px;
}

.user-detail__panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

.user-detail__positions-title {
  font-size: 14px;
  color: var(--color-ink);
  margin: 0 0 12px;
}
</style>
