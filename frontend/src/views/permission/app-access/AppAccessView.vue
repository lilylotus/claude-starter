<script setup lang="ts">
// 应用访问授权页面（/permission/app-access）外层壳：按"策略规则 / 人工例外 /
// 最终生效权限查询"切换三个互斥展示的子面板，拆成三个子组件而不是塞进同一个文件，
// 参照 FormFieldListView.vue 的外层 tabs 用法。
import { ref } from 'vue'
import PolicyRulePanel from './PolicyRulePanel.vue'
import ManualOverridePanel from './ManualOverridePanel.vue'
import EffectiveQueryPanel from './EffectiveQueryPanel.vue'

const activeTab = ref<'policy' | 'override' | 'effective'>('policy')
</script>

<template>
  <div class="app-access-view">
    <el-tabs v-model="activeTab" class="app-access-view__outer-tabs">
      <el-tab-pane label="策略规则" name="policy">
        <policy-rule-panel />
      </el-tab-pane>
      <el-tab-pane label="人工例外" name="override">
        <manual-override-panel />
      </el-tab-pane>
      <el-tab-pane label="最终生效权限查询" name="effective">
        <effective-query-panel />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped lang="scss">
.app-access-view__outer-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 16px;
  }
}
</style>
