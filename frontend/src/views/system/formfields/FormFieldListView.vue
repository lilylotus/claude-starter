<script setup lang="ts">
import { ref } from 'vue'
import FormFieldDefinitionPanel from './FormFieldDefinitionPanel.vue'
import ImportFieldConfigPanel from './ImportFieldConfigPanel.vue'

// 表单管理页面（/system/form-fields）外层壳：按"字段定义 / 导入模板配置"切换两个
// 相对独立的子面板，各自内部再按业务对象类型（组织/人员/任职/应用）二级切换。
// 拆成两个子组件（FormFieldDefinitionPanel.vue、ImportFieldConfigPanel.vue）而非
// 塞进同一个文件，是为了让原有的字段定义管理逻辑保持不变、新增的导入模板配置
// 逻辑独立维护，互不干扰。

const activeOuterTab = ref<'definition' | 'importConfig'>('definition')
</script>

<template>
  <div class="form-field-list-view">
    <el-tabs v-model="activeOuterTab" class="form-field-list-view__outer-tabs">
      <el-tab-pane label="字段定义" name="definition">
        <form-field-definition-panel />
      </el-tab-pane>
      <el-tab-pane label="导入模板配置" name="importConfig">
        <import-field-config-panel />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped lang="scss">
.form-field-list-view__outer-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 16px;
  }
}
</style>
