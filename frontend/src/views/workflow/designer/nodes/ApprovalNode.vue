<script setup lang="ts">
// "审批"节点：矩形卡片，展示节点名称 + 审批人来源/会签模式摘要，双击/单击选中后在右侧
// NodePropertyPanel.vue 编辑完整字段（tab_wf_node_assignee_rule 对应的全部列）。
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import { UserFilled } from '@element-plus/icons-vue'
import type { DesignerNodeData } from '@/stores/workflowDesigner'
import { APPROVAL_MODE_OPTIONS, ASSIGNEE_TYPE_OPTIONS } from '@/types/workflow'

const props = defineProps<{ data: DesignerNodeData; selected?: boolean }>()

const assigneeLabel = computed(() => {
  const found = ASSIGNEE_TYPE_OPTIONS.find((opt) => opt.value === props.data.assigneeType)
  return found ? found.label : '未配置审批人'
})

const modeLabel = computed(() => {
  const found = APPROVAL_MODE_OPTIONS.find((opt) => opt.value === props.data.approvalMode)
  return found ? found.label : ''
})
</script>

<template>
  <div class="wf-node wf-node--approval" :class="{ 'is-selected': selected }">
    <Handle type="target" :position="Position.Left" />
    <div class="wf-node__header">
      <el-icon class="wf-node__icon"><UserFilled /></el-icon>
      <span class="wf-node__label">{{ data.label || '审批' }}</span>
    </div>
    <div class="wf-node__meta">
      <span class="wf-node__tag">{{ assigneeLabel }}</span>
      <span v-if="modeLabel" class="wf-node__tag wf-node__tag--mode">{{ modeLabel }}</span>
    </div>
    <Handle type="source" :position="Position.Right" />
  </div>
</template>

<style scoped lang="scss">
.wf-node--approval {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 160px;
  padding: 10px 14px;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border-strong);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);

  &.is-selected {
    border-color: var(--color-primary);
    box-shadow: 0 0 0 2px var(--color-primary-soft);
  }
}

.wf-node__header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-ink);
}

.wf-node__icon {
  color: var(--color-primary);
}

.wf-node__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.wf-node__tag {
  font-size: 11px;
  color: var(--color-primary);
  background: var(--color-primary-soft);
  border-radius: var(--radius-pill);
  padding: 1px 8px;
}

.wf-node__tag--mode {
  color: var(--color-accent);
  background: var(--color-accent-soft);
}
</style>
