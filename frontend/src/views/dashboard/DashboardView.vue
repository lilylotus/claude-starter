<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { UserFilled, Grid, Lock, Setting } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { MENU_GROUPS } from '@/router/menu'

const router = useRouter()
const authStore = useAuthStore()

const today = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })

const stats = [
  { label: '身份总数', value: 128, delta: '+6', up: true, icon: UserFilled },
  { label: '接入应用', value: 14, delta: '+1', up: true, icon: Grid },
  { label: '角色数量', value: 22, delta: '0', up: null, icon: Lock },
  { label: '权限点', value: 96, delta: '+9', up: true, icon: Setting },
]

const activity = [
  { actor: '系统管理员', action: '为角色「审计员」新增了权限点 system:log:view', time: '10 分钟前' },
  { actor: '系统管理员', action: '创建了新用户「王芳」并分配到「客服组」', time: '1 小时前' },
  { actor: '系统管理员', action: '轮换了应用「工单系统」的访问密钥', time: '昨天 16:20' },
  { actor: '系统管理员', action: '调整了组织架构：新增部门「质量保障部」', time: '昨天 09:05' },
]

const quickLinks = computed(() => MENU_GROUPS)
</script>

<template>
  <div class="dashboard">
    <header class="dashboard__greeting">
      <h1>你好，{{ authStore.accountCode || '管理员' }}</h1>
      <p>{{ today }} · 这是身份与权限体系的整体概览</p>
    </header>

    <section class="dashboard__stats">
      <article v-for="stat in stats" :key="stat.label" class="stat-card">
        <div class="stat-card__icon">
          <el-icon size="20"><component :is="stat.icon" /></el-icon>
        </div>
        <div class="stat-card__body">
          <span class="stat-card__label">{{ stat.label }}</span>
          <span class="stat-card__value mono">{{ stat.value }}</span>
        </div>
        <span
          class="stat-card__delta mono"
          :class="{ 'is-up': stat.up === true, 'is-flat': stat.up === null }"
        >
          {{ stat.delta }}
        </span>
      </article>
    </section>

    <section class="dashboard__columns">
      <div class="panel">
        <h2 class="panel__title">最近操作</h2>
        <ol class="timeline">
          <li v-for="(item, index) in activity" :key="index" class="timeline__item">
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

.stat-card__delta {
  font-size: 12px;
  padding: 3px 8px;
  border-radius: var(--radius-pill);
  background: var(--color-canvas);
  color: var(--color-text-tertiary);

  &.is-up {
    background: rgba(33, 167, 107, 0.1);
    color: var(--color-success);
  }
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
