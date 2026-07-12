<script setup lang="ts">
import { useRoute } from 'vue-router'
import { Tools } from '@element-plus/icons-vue'

const route = useRoute()
</script>

<template>
  <section class="placeholder">
    <el-icon class="placeholder__icon"><Tools /></el-icon>
    <h2 class="placeholder__title">{{ route.meta.title }}</h2>
    <p class="placeholder__desc">{{ route.meta.description }}</p>
    <span class="placeholder__key mono">{{ route.meta.permissionKey }}</span>
  </section>
</template>

<style scoped lang="scss">
.placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  min-height: 420px;
  padding: 48px 24px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}

.placeholder__icon {
  font-size: 40px;
  color: var(--color-primary);
  background: var(--color-primary-soft);
  padding: 18px;
  border-radius: var(--radius-pill);
  margin-bottom: 20px;
}

.placeholder__title {
  font-size: 18px;
  margin-bottom: 8px;
}

.placeholder__desc {
  color: var(--color-text-secondary);
  max-width: 360px;
  line-height: 1.7;
  margin: 0 0 16px;
}

.placeholder__key {
  font-size: 12px;
  color: var(--color-text-tertiary);
  background: var(--color-canvas);
  border: 1px solid var(--color-border);
  padding: 4px 10px;
  border-radius: var(--radius-pill);
}
</style>
