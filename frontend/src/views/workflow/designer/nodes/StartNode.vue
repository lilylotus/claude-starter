<script setup lang="ts">
// "开始"节点：画布上通常只允许一个（ProcessDesignerView.vue 的拖拽面板对开始节点做了
// 数量限制交互提示），只暴露一个 source 连接点，不允许有入边。
import { Handle, Position } from '@vue-flow/core'
import { VideoPlay } from '@element-plus/icons-vue'
import type { DesignerNodeData } from '@/stores/workflowDesigner'

defineProps<{ data: DesignerNodeData; selected?: boolean }>()
</script>

<template>
  <div class="wf-node wf-node--start" :class="{ 'is-selected': selected }">
    <el-icon class="wf-node__icon"><VideoPlay /></el-icon>
    <span class="wf-node__label">{{ data.label || '开始' }}</span>
    <Handle type="source" :position="Position.Right" />
  </div>
</template>

<style scoped lang="scss">
.wf-node {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 96px;
  padding: 8px 14px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--color-border-strong);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
  font-size: 13px;
  color: var(--color-ink);

  &.is-selected {
    border-color: var(--color-primary);
    box-shadow: 0 0 0 2px var(--color-primary-soft);
  }
}

.wf-node--start {
  border-color: var(--color-success);
  background: #eafaf2;

  .wf-node__icon {
    color: var(--color-success);
  }
}
</style>
