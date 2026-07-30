<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { MENU_GROUPS, filterMenuGroups } from '@/router/menu'
import { usePermission } from '@/composables/usePermission'

defineProps<{ collapsed: boolean }>()

const route = useRoute()
const { hasPermission } = usePermission()
const visibleGroups = computed(() => filterMenuGroups(MENU_GROUPS, hasPermission))
</script>

<template>
  <div class="side-nav">
    <router-link to="/dashboard" class="side-nav__brand">
      <svg width="24" height="24" viewBox="0 0 32 32" fill="none">
        <rect width="32" height="32" rx="8" fill="var(--color-primary)" />
        <circle cx="9" cy="10" r="3" fill="#fff" />
        <circle cx="23" cy="10" r="3" fill="#fff" />
        <circle cx="16" cy="22" r="3" fill="#fff" />
        <path d="M9 10 L16 22 L23 10" stroke="#fff" stroke-width="2" fill="none" stroke-linecap="round" />
      </svg>
      <span v-if="!collapsed" class="side-nav__brand-text">权限管理系统</span>
    </router-link>

    <el-menu
      class="side-nav__menu"
      :default-active="route.path"
      :collapse="collapsed"
      :collapse-transition="false"
      router
      unique-opened
    >
      <el-sub-menu v-for="group in visibleGroups" :key="group.key" :index="group.key">
        <template #title>
          <el-icon><component :is="group.icon" /></el-icon>
          <span>{{ group.title }}</span>
        </template>
        <el-menu-item v-for="child in group.children" :key="child.path" :index="child.path">
          {{ child.title }}
        </el-menu-item>
      </el-sub-menu>
    </el-menu>
  </div>
</template>

<style scoped lang="scss">
.side-nav {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--color-surface);
  border-right: 1px solid var(--color-border);
}

.side-nav__brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: var(--layout-header-height);
  padding: 0 20px;
  flex-shrink: 0;
  text-decoration: none;
  border-bottom: 1px solid var(--color-border);
}

.side-nav__brand-text {
  font-family: var(--font-display);
  font-weight: 600;
  color: var(--color-ink);
  font-size: 15px;
  white-space: nowrap;
}

.side-nav__menu {
  flex: 1;
  overflow-y: auto;
  border-right: none;
  padding: 8px;

  &:not(.el-menu--collapse) {
    width: 100%;
  }
}

// 子菜单里用一条竖线 + 小圆点把父子层级"连"起来，呼应登录页/概览页的链式视觉语言。
.side-nav__menu :deep(.el-menu) {
  position: relative;
  margin-left: 14px;
  padding-left: 8px;
  border-left: 1px dashed var(--chain-line-color);
}

.side-nav__menu :deep(.el-sub-menu .el-menu-item) {
  position: relative;
  min-width: unset;
  border-radius: var(--radius-sm);
}

.side-nav__menu :deep(.el-sub-menu .el-menu-item::before) {
  content: '';
  position: absolute;
  left: -9px;
  top: 50%;
  width: var(--chain-dot-size-sm);
  height: var(--chain-dot-size-sm);
  border-radius: 50%;
  background: var(--chain-line-color);
  transform: translateY(-50%);
  transition: background-color 0.2s;
}

.side-nav__menu :deep(.el-sub-menu .el-menu-item.is-active::before) {
  background: var(--chain-line-color-active);
  box-shadow: 0 0 0 3px var(--color-primary-soft);
}

.side-nav__menu :deep(.el-menu-item),
.side-nav__menu :deep(.el-sub-menu__title) {
  border-radius: var(--radius-sm);
}
</style>
