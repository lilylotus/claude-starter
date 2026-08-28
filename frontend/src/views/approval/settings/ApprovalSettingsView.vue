<script setup lang="ts">
// "审批设置"：展示组织/用户/任职/应用四个 bizType 的审批开关状态，页面访问受
// ApprovalManagement:switch:view 权限点门控（路由级，见 router/index.ts）；切换开关
// 操作受 ApprovalManagement:switch:edit 权限点门控——没有该权限时开关渲染为禁用态，
// 仍可看到当前状态，但拖不动（design.md Decision 9 / spec.md"管理页面的审批入口"需求）。
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as approvalSwitchApi from '@/api/approvalSwitch'
import { usePermission } from '@/composables/usePermission'
import { APPROVAL_BIZ_TYPE_OPTIONS, type ApprovalBizType } from '@/types/approval'
import type { ApprovalSwitchRow } from '@/types/approvalSwitch'

const { hasPermission } = usePermission()

const loading = ref(false)
const switches = ref<ApprovalSwitchRow[]>([])

async function fetchSwitches() {
  loading.value = true
  try {
    switches.value = await approvalSwitchApi.getApprovalSwitches()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchSwitches()
})

// 按固定顺序（组织/用户/任职/应用）展示，接口返回顺序不保证时也能稳定渲染；
// 接口未返回某个 bizType 时兜底展示为"未知状态"（enabled 置 false），不阻断整页渲染
function switchFor(bizType: ApprovalBizType): ApprovalSwitchRow {
  return switches.value.find((item) => item.bizType === bizType) ?? { bizType, enabled: false }
}

// el-switch 的 before-change：返回 true 才会真正翻转 v-model 展示的状态，
// 因此这里先调用接口，成功后返回 true（并直接用响应值覆盖本地状态，避免与后端不一致），
// 失败时返回 false（错误提示已由 axios 响应拦截器统一处理，这里不重复提示）
async function handleToggle(bizType: ApprovalBizType, currentEnabled: boolean): Promise<boolean> {
  const nextEnabled = !currentEnabled
  try {
    const updated = await approvalSwitchApi.updateApprovalSwitch(bizType, { enabled: nextEnabled })
    const target = switches.value.find((item) => item.bizType === bizType)
    if (target) {
      target.enabled = updated.enabled
    } else {
      switches.value.push(updated)
    }
    ElMessage.success(nextEnabled ? '已开启审批' : '已关闭审批')
    return true
  } catch {
    return false
  }
}
</script>

<template>
  <div class="approval-settings">
    <section class="approval-settings__panel">
      <header class="approval-settings__header">
        <h2 class="approval-settings__title">审批设置</h2>
        <p class="approval-settings__subtitle">
          按业务对象类型独立控制是否启用审批流程；关闭后对应模块的新增/编辑/启用/停用/删除
          接口调用即立即生效，不再生成审批申请；已存在的待审批申请不受影响。
        </p>
      </header>

      <div v-loading="loading" class="approval-settings__list">
        <div v-for="opt in APPROVAL_BIZ_TYPE_OPTIONS" :key="opt.value" class="approval-settings__item">
          <div class="approval-settings__item-info">
            <span class="approval-settings__item-label">{{ opt.label }}</span>
            <span class="approval-settings__item-desc">
              {{ switchFor(opt.value).enabled ? '当前开启审批：提交后需审批通过才生效' : '当前关闭审批：调用接口即立即生效' }}
            </span>
          </div>
          <el-switch
            :model-value="switchFor(opt.value).enabled"
            :disabled="!hasPermission('ApprovalManagement:switch:edit')"
            :before-change="() => handleToggle(opt.value, switchFor(opt.value).enabled)"
          />
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.approval-settings {
  display: flex;
  flex-direction: column;
}

.approval-settings__panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

.approval-settings__header {
  margin-bottom: 16px;
}

.approval-settings__title {
  font-size: 15px;
  color: var(--color-ink);
  margin: 0 0 6px;
}

.approval-settings__subtitle {
  font-size: 12.5px;
  color: var(--color-text-tertiary);
  margin: 0;
  line-height: 1.6;
}

.approval-settings__list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.approval-settings__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 4px;
  border-bottom: 1px dashed var(--color-border);
}

.approval-settings__item:last-child {
  border-bottom: none;
}

.approval-settings__item-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.approval-settings__item-label {
  font-size: 14px;
  color: var(--color-ink);
  font-weight: 500;
}

.approval-settings__item-desc {
  font-size: 12.5px;
  color: var(--color-text-tertiary);
}
</style>
