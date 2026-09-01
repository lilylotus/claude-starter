<script setup lang="ts">
// 角色详情独立页面：原 RoleManagementView.vue 内的只读详情弹窗迁移至此，通过路由参数
// :id 加载数据，不再依赖父组件传入的 targetId。
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import OperationHistoryPanel from '@/components/OperationHistoryPanel.vue'
import * as roleApi from '@/api/role'
import { ROLE_STATUS_ENABLED, type RoleRow } from '@/types/role'
import { buildPermissionTree, resolvePermissionModuleLabel } from '@/utils/permissionTree'

const route = useRoute()
const router = useRouter()

const roleId = computed(() => Number(route.params.id))

const loading = ref(false)
const loadError = ref('')
const detailData = ref<RoleRow | null>(null)

// 已分配权限点按编码冒号分隔的第一段（模块）分组，只读展示，不提供勾选交互，
// 分组算法与中文模块名映射复用 src/utils/permissionTree.ts，与
// RoleManagementView.vue 新增/编辑弹窗内的权限点树、PermissionManagementView.vue
// 权限点管理树保持一致，避免三处各自维护一份可能漂移的分组/中文名解析逻辑
const groupedPermissions = computed(() =>
  buildPermissionTree(detailData.value?.permissions ?? [], resolvePermissionModuleLabel),
)

async function fetchDetail() {
  loading.value = true
  loadError.value = ''
  try {
    detailData.value = await roleApi.getRoleById(roleId.value)
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '加载角色详情失败'
  } finally {
    loading.value = false
  }
}

onMounted(fetchDetail)

// 显式跳回角色列表路由，而不是 router.back()：详情页可能是直接通过 URL/刷新进入的，
// 浏览器历史栈在这种场景下可能为空或指向无关页面
function goBack() {
  router.push({ name: 'permission-roles' })
}
</script>

<template>
  <div class="role-detail">
    <header class="role-detail__header">
      <el-button link :icon="ArrowLeft" class="role-detail__back" @click="goBack">返回</el-button>
      <h2 class="role-detail__title">角色详情</h2>
    </header>

    <el-alert
      v-if="loadError"
      class="role-detail__error"
      type="error"
      :title="loadError"
      show-icon
      :closable="false"
    />

    <template v-else>
      <section class="role-detail__panel">
        <el-descriptions v-loading="loading" :column="2" border>
          <el-descriptions-item label="角色名称">{{ detailData?.name }}</el-descriptions-item>
          <el-descriptions-item label="角色编码">{{ detailData?.code }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag v-if="detailData?.status === ROLE_STATUS_ENABLED" type="success">启用</el-tag>
            <el-tag v-else type="warning">停用</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="显示序号">{{ detailData?.showOrder }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detailData?.remark || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ detailData?.createBy }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detailData?.createTime }}</el-descriptions-item>
          <el-descriptions-item label="更新人">{{ detailData?.updateBy }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ detailData?.updateTime }}</el-descriptions-item>
        </el-descriptions>
      </section>

      <section class="role-detail__panel">
        <h3 class="role-detail__subtitle">已分配权限点</h3>
        <p v-if="groupedPermissions.length === 0" class="role-detail__empty">暂未分配任何权限点</p>
        <div v-else class="role-permission-groups">
          <div v-for="group in groupedPermissions" :key="group.id" class="role-permission-group">
            <span class="role-permission-group__name">{{ group.label }}</span>
            <div class="role-permission-group__tags">
              <el-tag v-for="item in group.children" :key="item.id" class="role-permission-tag">
                {{ item.label }}
              </el-tag>
            </div>
          </div>
        </div>
      </section>

      <section class="role-detail__panel">
        <OperationHistoryPanel resource-type="role" :target-id="detailData?.id ?? null" />
      </section>
    </template>
  </div>
</template>

<style scoped lang="scss">
.role-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.role-detail__header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.role-detail__back {
  font-size: 14px;
}

.role-detail__title {
  font-size: 16px;
  color: var(--color-ink);
  margin: 0;
}

.role-detail__error {
  margin-bottom: 4px;
}

.role-detail__panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

.role-detail__subtitle {
  font-size: 14px;
  color: var(--color-ink);
  margin: 0 0 12px;
}

.role-detail__empty {
  font-size: 13px;
  color: var(--color-text-tertiary);
  margin: 0;
}

// 按模块分组的权限点列表，用一条虚线 + 圆点串起各模块分组，呼应组织范围/角色多选等
// 子表单一致的"链式连接"视觉语言
.role-permission-groups {
  position: relative;
  padding-left: 16px;
  border-left: 1px dashed var(--chain-line-color);
}

.role-permission-group {
  position: relative;
  padding-bottom: 12px;
  margin-bottom: 12px;
  border-bottom: 1px dashed var(--color-border);
}

.role-permission-group:last-child {
  padding-bottom: 0;
  margin-bottom: 0;
  border-bottom: none;
}

.role-permission-group::before {
  content: '';
  position: absolute;
  left: -20px;
  top: 6px;
  width: var(--chain-dot-size-sm);
  height: var(--chain-dot-size-sm);
  border-radius: 50%;
  background: var(--chain-line-color-active);
}

.role-permission-group__name {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-ink);
  margin-bottom: 8px;
}

.role-permission-group__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.role-permission-tag {
  margin: 0;
}
</style>
