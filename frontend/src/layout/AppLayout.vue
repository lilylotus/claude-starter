<script setup lang="ts">
import { ref } from 'vue'
import SideNav from './components/SideNav.vue'
import HeaderBar from './components/HeaderBar.vue'

const collapsed = ref(false)
</script>

<template>
  <div class="app-layout">
    <aside class="app-layout__aside" :style="{ width: collapsed ? 'var(--layout-sider-collapsed-width)' : 'var(--layout-sider-width)' }">
      <SideNav :collapsed="collapsed" />
    </aside>

    <div class="app-layout__body">
      <HeaderBar :collapsed="collapsed" @toggle-collapsed="collapsed = !collapsed" />
      <main class="app-layout__main">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped lang="scss">
.app-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.app-layout__aside {
  flex-shrink: 0;
  transition: width 0.2s ease;
}

.app-layout__body {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.app-layout__main {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}
</style>
