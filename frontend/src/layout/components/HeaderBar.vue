<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Fold, Expand, ArrowDown, SwitchButton } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const props = defineProps<{ collapsed: boolean }>()
const emit = defineEmits<{ 'toggle-collapsed': [] }>()

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const crumbs = computed(() =>
  route.matched
    .filter((record) => record.meta.title && record.meta.title !== '首页')
    .map((record) => record.meta.title),
)

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>

<template>
  <header class="header-bar">
    <div class="header-bar__left">
      <button class="header-bar__toggle" type="button" @click="emit('toggle-collapsed')">
        <el-icon size="18"><component :is="props.collapsed ? Expand : Fold" /></el-icon>
      </button>

      <nav class="header-bar__crumbs" aria-label="面包屑">
        <template v-for="(title, index) in crumbs" :key="title">
          <span class="header-bar__crumb-dot" v-if="index > 0" aria-hidden="true" />
          <span class="header-bar__crumb" :class="{ 'is-current': index === crumbs.length - 1 }">
            {{ title }}
          </span>
        </template>
      </nav>
    </div>

    <div class="header-bar__right">
      <el-dropdown trigger="click">
        <button class="header-bar__user">
          <el-avatar :size="30" style="background: var(--color-primary)">
            {{ authStore.userInfo?.displayName?.slice(0, 1) }}
          </el-avatar>
          <span class="header-bar__user-name">{{ authStore.userInfo?.displayName }}</span>
          <el-icon size="14"><ArrowDown /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item :icon="SwitchButton" @click="handleLogout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<style scoped lang="scss">
.header-bar {
  height: var(--layout-header-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
}

.header-bar__left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-bar__toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: var(--radius-sm);
  color: var(--color-text-secondary);
  cursor: pointer;

  &:hover {
    background: var(--color-primary-soft);
    color: var(--color-primary);
  }
}

.header-bar__crumbs {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-bar__crumb {
  font-size: 14px;
  color: var(--color-text-secondary);

  &.is-current {
    color: var(--color-ink);
    font-weight: 600;
  }
}

.header-bar__crumb-dot {
  width: var(--chain-dot-size-sm);
  height: var(--chain-dot-size-sm);
  border-radius: 50%;
  background: var(--color-border-strong);
}

.header-bar__right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-bar__user {
  display: flex;
  align-items: center;
  gap: 8px;
  border: none;
  background: transparent;
  padding: 4px 8px;
  border-radius: var(--radius-pill);
  cursor: pointer;

  &:hover {
    background: var(--color-canvas);
  }
}

.header-bar__user-name {
  font-size: 14px;
  color: var(--color-text);
}
</style>
