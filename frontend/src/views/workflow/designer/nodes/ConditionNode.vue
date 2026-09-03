<script setup lang="ts">
// "条件"节点：菱形网关外观（不向使用者暴露"排他网关"等 BPMN 术语），分支条件通过该节点
// 的出边配置（NodePropertyPanel.vue 选中节点后编辑），必须保留至少一条不带 condition 的
// 兜底默认分支。
import { Handle, Position } from '@vue-flow/core'
import { Share } from '@element-plus/icons-vue'
import type { DesignerNodeData } from '@/stores/workflowDesigner'

defineProps<{ data: DesignerNodeData; selected?: boolean }>()
</script>

<template>
  <div class="wf-node wf-node--condition" :class="{ 'is-selected': selected }">
    <Handle type="target" :position="Position.Left" />
    <el-icon class="wf-node__icon"><Share /></el-icon>
    <span class="wf-node__label">{{ data.label || '条件' }}</span>
    <Handle type="source" :position="Position.Right" />
  </div>
</template>

<style scoped lang="scss">
.wf-node--condition {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 96px;
  padding: 8px 16px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-warning);
  background: var(--color-accent-soft);
  box-shadow: var(--shadow-sm);
  font-size: 13px;
  color: var(--color-ink);
  transform: skewX(-8deg);

  .wf-node__icon,
  .wf-node__label {
    transform: skewX(8deg);
  }

  .wf-node__icon {
    color: var(--color-warning);
  }

  &.is-selected {
    border-color: var(--color-primary);
    box-shadow: 0 0 0 2px var(--color-primary-soft);
  }
}
</style>
