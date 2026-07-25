<script setup lang="ts">
// 组织/人员/任职/应用四个管理页面共用的批量导入上传弹窗：选择 Excel 文件 → 调用对应
// bizType 的批量导入接口 → 在弹窗内展示成功/失败汇总，存在失败行时提供"下载失败明细"
// 按钮下载后端生成的标注版错误文件。四个页面只需传入不同的 bizType，弹窗内部逻辑完全一致。
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadFile, UploadInstance, UploadRawFile } from 'element-plus'
import * as excelImportApi from '@/api/excelImport'
import type { FormFieldBizType, ImportResult } from '@/types/importFieldConfig'
import { FORM_FIELD_BIZ_TYPE_OPTIONS } from '@/types/metadataField'

const props = defineProps<{
  modelValue: boolean
  bizType: FormFieldBizType
}>()

// 弹窗标题按业务对象类型显示，如"组织批量导入"/"人员批量导入"
const dialogTitle = computed(() => {
  const label = FORM_FIELD_BIZ_TYPE_OPTIONS.find((option) => option.value === props.bizType)?.label ?? ''
  return `${label}批量导入`
})

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

// 把后端返回的 Base64 错误文件解码为 Blob 并触发浏览器下载，写法与
// src/api/excelImport.ts 里 downloadImportTemplate() 触发下载模板的方式保持一致
function handleDownloadErrorFile() {
  const base64 = result.value?.errorFileBase64
  const fileName = result.value?.errorFileName
  if (!base64 || !fileName) return

  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i)
  }
  const blob = new Blob([bytes], {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  })

  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
</script>

<template>
  <el-dialog v-model="visible" :title="dialogTitle" width="560px" @close="resetState">
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
      <p class="batch-import-dialog__summary">
        成功 {{ result.successCount }} 条，失败 {{ result.failList.length }} 条
      </p>
      <el-button
        v-if="result.failList.length > 0"
        type="danger"
        plain
        size="small"
        @click="handleDownloadErrorFile"
      >
        下载失败明细
      </el-button>
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

.batch-import-dialog__summary {
  color: var(--color-text-primary);
  font-weight: 600;
  margin: 0 0 8px;
}
</style>
