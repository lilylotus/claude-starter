<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { UserFilled, Grid, OfficeBuilding, Avatar } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { MENU_GROUPS, filterMenuGroups } from '@/router/menu'
import { usePermission } from '@/composables/usePermission'
import { getDashboardStats, getRecentOperations } from '@/api/dashboard'
import type { DashboardStats } from '@/types/dashboard'
import type { OperationLogRow } from '@/types/operationLog'

const router = useRouter()
const authStore = useAuthStore()
const { hasPermission } = usePermission()

const today = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })

// 统计卡片元信息（label/icon 固定不变），value 从统计接口回填；四个数值共用同一次
// 请求的 loading/error 状态，初始不设 0，避免请求未返回时被误认为真实数据
const statsMeta = [
  { key: 'orgCount' as const, label: '组织总数', icon: OfficeBuilding },
  { key: 'userCount' as const, label: '身份总数', icon: UserFilled },
  { key: 'appCount' as const, label: '接入应用', icon: Grid },
  { key: 'adminCount' as const, label: '管理员总数', icon: Avatar },
]

const statsLoading = ref(true)
const statsError = ref(false)
const statsData = ref<DashboardStats | null>(null)

const statCards = computed(() =>
  statsMeta.map((meta) => ({
    label: meta.label,
    icon: meta.icon,
    value: statsData.value ? statsData.value[meta.key] : undefined,
  })),
)

const activityLoading = ref(true)
const activityError = ref(false)
const activityRecords = ref<OperationLogRow[]>([])

// 用操作日志的模块/被操作对象/操作类型拼出一句通顺的中文描述，time 单独展示
const activityItems = computed(() =>
  activityRecords.value.map((record) => ({
    actor: record.createBy,
    action: `对${record.module}下的「${record.targetName}」执行了${record.operationTypeLabel}`,
    time: record.createTime,
  })),
)

async function loadStats() {
  statsLoading.value = true
  statsError.value = false
  try {
    statsData.value = await getDashboardStats()
  } catch {
    statsError.value = true
  } finally {
    statsLoading.value = false
  }
}

async function loadActivity() {
  activityLoading.value = true
  activityError.value = false
  try {
    activityRecords.value = await getRecentOperations()
  } catch {
    activityError.value = true
  } finally {
    activityLoading.value = false
  }
}

onMounted(() => {
  Promise.allSettled([loadStats(), loadActivity()])
})

const quickLinks = computed(() => filterMenuGroups(MENU_GROUPS, hasPermission))
</script>

<template>
  <div class="dashboard">
    <header class="dashboard__greeting">
      <h1>你好，{{ authStore.accountCode || '管理员' }}</h1>
      <p>{{ today }} · 这是身份与权限体系的整体概览</p>
    </header>

    <section v-if="statsError" class="dashboard__stats-error">统计数据加载失败，请稍后重试</section>
    <section v-else class="dashboard__stats" v-loading="statsLoading">
      <article v-for="stat in statCards" :key="stat.label" class="stat-card">
        <div class="stat-card__icon">
          <el-icon size="20"><component :is="stat.icon" /></el-icon>
        </div>
        <div class="stat-card__body">
          <span class="stat-card__label">{{ stat.label }}</span>
          <span class="stat-card__value mono">{{ stat.value ?? '--' }}</span>
        </div>
      </article>
    </section>

    <section class="dashboard__columns">
      <div class="panel">
        <h2 class="panel__title">最近操作</h2>
        <p v-if="activityError" class="panel__hint">操作记录加载失败，请稍后重试</p>
        <p v-else-if="!activityLoading && activityItems.length === 0" class="panel__hint">暂无操作记录</p>
        <ol v-else class="timeline" v-loading="activityLoading">
          <li v-for="(item, index) in activityItems" :key="index" class="timeline__item">
            <span class="timeline__dot" />
            <div class="timeline__content">
              <p class="timeline__text"><strong>{{ item.actor }}</strong> {{ item.action }}</p>
              <span class="timeline__time mono">{{ item.time }}</span>
            </div>
          </li>
        </ol>
      </div>

      <div class="panel">
        <h2 class="panel__title">快速入口</h2>
        <div class="quick-grid">
          <button
            v-for="group in quickLinks"
            :key="group.key"
            class="quick-card"
            type="button"
            @click="router.push(group.children[0].path)"
          >
            <el-icon size="18" color="var(--color-primary)"><component :is="group.icon" /></el-icon>
            <span>{{ group.title }}</span>
          </button>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.dashboard__greeting {
  margin-bottom: 24px;

  h1 {
    font-size: 22px;
    margin-bottom: 6px;
  }

  p {
    color: var(--color-text-secondary);
    margin: 0;
  }
}

.dashboard__stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 18px;
  box-shadow: var(--shadow-sm);
}

.stat-card__icon {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-md);
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.stat-card__body {
  display: flex;
  flex-direction: column;
  flex: 1;
}

.stat-card__label {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.stat-card__value {
  font-size: 22px;
  font-weight: 600;
  color: var(--color-ink);
}

.dashboard__stats-error {
  margin-bottom: 24px;
  padding: 18px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  color: var(--color-text-secondary);
  font-size: 13.5px;
  text-align: center;
}

.panel__hint {
  color: var(--color-text-secondary);
  font-size: 13.5px;
  margin: 0;
}

.dashboard__columns {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 16px;

  @media (max-width: 960px) {
    grid-template-columns: 1fr;
  }
}

.panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

.panel__title {
  font-size: 15px;
  margin-bottom: 16px;
}

.timeline {
  list-style: none;
  margin: 0;
  padding: 0 0 0 4px;
}

.timeline__item {
  position: relative;
  padding-left: 20px;
  padding-bottom: 20px;
  border-left: 1px dashed var(--chain-line-color);

  &:last-child {
    border-left-color: transparent;
    padding-bottom: 0;
  }
}

.timeline__dot {
  position: absolute;
  left: -5px;
  top: 2px;
  width: var(--chain-dot-size);
  height: var(--chain-dot-size);
  border-radius: 50%;
  background: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-soft);
}

.timeline__text {
  margin: 0 0 4px;
  font-size: 13.5px;
  line-height: 1.6;
  color: var(--color-text);

  strong {
    color: var(--color-ink);
  }
}

.timeline__time {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.quick-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-canvas);
  cursor: pointer;
  font-size: 13.5px;
  color: var(--color-text);
  transition: border-color 0.15s, background-color 0.15s;

  &:hover {
    border-color: var(--color-primary);
    background: var(--color-primary-soft);
    color: var(--color-primary);
  }
}
</style>
