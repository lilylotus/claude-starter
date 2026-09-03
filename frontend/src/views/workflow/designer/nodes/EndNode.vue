<script setup lang="ts">
// "结束"节点：只暴露一个 target 连接点，不允许有出边；流程模型至少需要一个结束节点，
// 允许多个（对应不同的最终状态，如"已通过"/"已拒绝"）。
import { Handle, Position } from '@vue-flow/core'
import { CircleCheck } from '@element-plus/icons-vue'
import type { DesignerNodeData } from '@/stores/workflowDesigner'

defineProps<{ data: DesignerNodeData; selected?: boolean }>()
</script>

<template>
  <div class="wf-node wf-node--end" :class="{ 'is-selected': selected }">
    <Handle type="target" :position="Position.Left" />
    <el-icon class="wf-node__icon"><CircleCheck /></el-icon>
    <span class="wf-node__label">{{ data.label || '结束' }}</span>
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

.wf-node--end {
  border-color: var(--color-text-tertiary);
  background: var(--color-canvas);

  .wf-node__icon {
    color: var(--color-text-secondary);
  }
}
</style>
