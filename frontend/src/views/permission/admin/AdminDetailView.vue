<script setup lang="ts">
// 管理员详情独立页面：原 AdminManagementView.vue 内的只读详情弹窗迁移至此，
// 通过路由参数 :id 加载数据，不再依赖父组件传入的 targetId。
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import OperationHistoryPanel from '@/components/OperationHistoryPanel.vue'
import * as adminApi from '@/api/admin'
import { ADMIN_STATUS_ENABLED, type AdminRow } from '@/types/admin'

const route = useRoute()
const router = useRouter()

const adminId = computed(() => Number(route.params.id))

const loading = ref(false)
const loadError = ref('')
const detailData = ref<AdminRow | null>(null)

async function fetchDetail() {
  loading.value = true
  loadError.value = ''
  try {
    detailData.value = await adminApi.getAdminById(adminId.value)
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '加载管理员详情失败'
  } finally {
    loading.value = false
  }
}

onMounted(fetchDetail)

// 显式跳回管理员列表路由，而不是 router.back()：详情页可能是直接通过 URL/刷新进入的，
// 浏览器历史栈在这种场景下可能为空或指向无关页面
function goBack() {
  router.push({ name: 'permission-admins' })
}
</script>

<template>
  <div class="admin-detail">
    <header class="admin-detail__header">
      <el-button link :icon="ArrowLeft" class="admin-detail__back" @click="goBack">返回</el-button>
      <h2 class="admin-detail__title">管理员详情</h2>
    </header>

    <el-alert
      v-if="loadError"
      class="admin-detail__error"
      type="error"
      :title="loadError"
      show-icon
      :closable="false"
    />

    <template v-else>
      <section class="admin-detail__panel">
        <el-descriptions v-loading="loading" :column="2" border>
          <el-descriptions-item label="管理员名称">{{ detailData?.name }}</el-descriptions-item>
          <el-descriptions-item label="管理员编码">{{ detailData?.code }}</el-descriptions-item>
          <el-descriptions-item label="关联用户">{{ detailData?.userName }}</el-descriptions-item>
          <el-descriptions-item label="管理员角色">
            <template v-if="detailData?.roles.length">
              <el-tag v-for="role in detailData.roles" :key="role.roleId" class="admin-detail-role-tag">
                {{ role.roleName }}
              </el-tag>
            </template>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="显示序号">{{ detailData?.showOrder }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag v-if="detailData?.status === ADMIN_STATUS_ENABLED" type="success">启用</el-tag>
            <el-tag v-else type="warning">停用</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detailData?.remark || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ detailData?.createBy }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detailData?.createTime }}</el-descriptions-item>
          <el-descriptions-item label="更新人">{{ detailData?.updateBy }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ detailData?.updateTime }}</el-descriptions-item>
        </el-descriptions>
      </section>

      <section class="admin-detail__panel">
        <h3 class="admin-detail__subtitle">管辖组织范围</h3>
        <el-table :data="detailData?.orgScopes ?? []" empty-text="暂无管辖组织范围">
          <el-table-column prop="orgName" label="组织名称" min-width="160" />
          <el-table-column label="含子组织" width="100">
            <template #default="{ row }">{{ row.includeChildren ? '是' : '否' }}</template>
          </el-table-column>
        </el-table>
      </section>

      <section class="admin-detail__panel">
        <OperationHistoryPanel resource-type="admin" :target-id="detailData?.id ?? null" />
      </section>
    </template>
  </div>
</template>

<style scoped lang="scss">
.admin-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.admin-detail__header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.admin-detail__back {
  font-size: 14px;
}

.admin-detail__title {
  font-size: 16px;
  color: var(--color-ink);
  margin: 0;
}

.admin-detail__error {
  margin-bottom: 4px;
}

.admin-detail__panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

.admin-detail__subtitle {
  font-size: 14px;
  color: var(--color-ink);
  margin: 0 0 12px;
}

.admin-detail-role-tag {
  margin-right: 6px;
}
</style>
