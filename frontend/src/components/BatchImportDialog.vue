<script setup lang="ts">
// 组织/人员/任职/应用四个管理页面共用的批量导入上传弹窗：选择 Excel 文件 → 调用对应
// bizType 的批量导入接口 → 在弹窗内展示成功条数与失败明细列表（行号 + 原因）。
// 四个页面只需传入不同的 bizType，弹窗内部逻辑完全一致。
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadFile, UploadInstance, UploadRawFile } from 'element-plus'
import * as excelImportApi from '@/api/excelImport'
import type { FormFieldBizType, ImportResult } from '@/types/importFieldConfig'

const props = defineProps<{
  modelValue: boolean
  bizType: FormFieldBizType
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  // 导入完成（不论是否存在失败行）后触发，供页面刷新当前列表
  imported: []
}>()

const visible = ref(props.modelValue)

watch(
  () => props.modelValue,
  (value) => {
    visible.value = value
    if (value) resetState()
  },
)

watch(visible, (value) => {
  emit('update:modelValue', value)
})

const uploadRef = ref<UploadInstance>()
const selectedFile = ref<File | null>(null)
const importing = ref(false)
const result = ref<ImportResult | null>(null)

function resetState() {
  selectedFile.value = null
  result.value = null
  uploadRef.value?.clearFiles()
}

function handleFileChange(uploadFile: UploadFile) {
  selectedFile.value = (uploadFile.raw as UploadRawFile | undefined) ?? null
}

function handleFileRemove() {
  selectedFile.value = null
}

function handleExceed() {
  ElMessage.warning('一次只能选择一个 Excel 文件，请先移除已选文件')
}

async function handleImport() {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择要导入的 Excel 文件')
    return
  }
  importing.value = true
  try {
    result.value = await excelImportApi.batchImportExcel(props.bizType, selectedFile.value)
    emit('imported')
  } finally {
    importing.value = false
  }
}

function handleClose() {
  visible.value = false
}
</script>

<template>
  <el-dialog v-model="visible" title="批量导入" width="560px" @close="resetState">
    <el-upload
      ref="uploadRef"
      drag
      :auto-upload="false"
      :limit="1"
      accept=".xlsx"
      :on-change="handleFileChange"
      :on-remove="handleFileRemove"
      :on-exceed="handleExceed"
    >
      <div class="batch-import-dialog__tip">点击选择或拖拽 .xlsx 文件到此处</div>
      <template #tip>
        <div class="batch-import-dialog__hint">请先下载导入模板并按模板整理数据后再上传</div>
      </template>
    </el-upload>

    <div v-if="result" class="batch-import-dialog__result">
      <p class="batch-import-dialog__success">成功导入 {{ result.successCount }} 条</p>
      <template v-if="result.failList.length > 0">
        <p class="batch-import-dialog__fail-title">失败 {{ result.failList.length }} 条：</p>
        <el-table :data="result.failList" size="small" max-height="240" border>
          <el-table-column prop="rowNo" label="行号" width="80" />
          <el-table-column prop="reason" label="失败原因" min-width="220" />
        </el-table>
      </template>
    </div>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
      <el-button type="primary" :loading="importing" @click="handleImport">开始导入</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.batch-import-dialog__tip {
  font-size: 13px;
  color: var(--color-text-tertiary);
  padding: 12px 0;
}

.batch-import-dialog__hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.batch-import-dialog__result {
  margin-top: 16px;
  border-top: 1px dashed var(--color-border);
  padding-top: 12px;
}

.batch-import-dialog__success {
  color: var(--color-success, #67c23a);
  font-weight: 600;
  margin: 0 0 8px;
}

.batch-import-dialog__fail-title {
  color: var(--color-danger, #e5484d);
  margin: 0 0 8px;
}
</style>
