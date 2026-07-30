<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import * as metadataFieldApi from '@/api/metadataField'
import { DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS } from '@/constants/pagination'
import {
  FORM_FIELD_BIZ_TYPE_OPTIONS,
  METADATA_FIELD_STATUS_ENABLED,
  type FormFieldBizType,
  type MetadataField,
} from '@/types/metadataField'
import { usePermission } from '@/composables/usePermission'

const { hasPermission } = usePermission()

// 元数据配置页面：按业务对象类型（组织/人员/任职/应用）切换查看"可开放配置"的表字段目录；
// 目录只能通过数据库迁移预置，本页面支持编辑字段名称/字段标识（同一 bizType 下唯一）、
// 查看详情、启用/停用，不提供新增/删除入口
// （见 openspec/changes/form-field-definition-management/specs/metadata-field-management）。

const activeBizType = ref<FormFieldBizType>('ORG')

const list = ref<MetadataField[]>([])
const listLoading = ref(false)
const page = ref(1)
const pageSize = ref(DEFAULT_PAGE_SIZE)
const total = ref(0)

async function fetchPage(targetPage?: number) {
  if (targetPage !== undefined) page.value = targetPage
  listLoading.value = true
  try {
    const result = await metadataFieldApi.getMetadataFieldPage({
      bizType: activeBizType.value,
      page: page.value,
      pageSize: pageSize.value,
    })
    list.value = result.records
    total.value = result.total
    page.value = result.page
    pageSize.value = result.pageSize
  } finally {
    listLoading.value = false
  }
}

onMounted(() => {
  fetchPage(1)
})

function handleTabChange() {
  fetchPage(1)
}

function handlePageChange(targetPage: number) {
  fetchPage(targetPage)
}

function handleSizeChange(newSize: number) {
  pageSize.value = newSize
  page.value = 1
  fetchPage()
}

// ---- 编辑弹窗：字段名称 + 字段标识 ----

const editDialogVisible = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({ fieldName: '', fieldCode: '' })

const rules: FormRules<typeof form> = {
  fieldName: [{ required: true, message: '请输入字段名称', trigger: 'blur' }],
  fieldCode: [{ required: true, message: '请输入字段标识', trigger: 'blur' }],
}

function openEditDialog(row: MetadataField) {
  editingId.value = row.id
  form.fieldName = row.fieldName
  form.fieldCode = row.fieldCode
  editDialogVisible.value = true
}

function closeEditDialog() {
  editDialogVisible.value = false
  formRef.value?.clearValidate()
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await metadataFieldApi.updateMetadataField(editingId.value as number, {
      fieldName: form.fieldName,
      fieldCode: form.fieldCode,
    })
    ElMessage.success('保存成功')
    editDialogVisible.value = false
    await fetchPage()
  } finally {
    submitting.value = false
  }
}

// ---- 详情弹窗：只读展示表名称/字段列名/字段类型/字段名称/状态 ----

const detailDialogVisible = ref(false)
const detailRow = ref<MetadataField | null>(null)

async function openDetailDialog(row: MetadataField) {
  detailRow.value = await metadataFieldApi.getMetadataFieldById(row.id)
  detailDialogVisible.value = true
}

// ---- 启用/停用 ----

async function toggleStatus(row: MetadataField) {
  if (row.status === METADATA_FIELD_STATUS_ENABLED) {
    await metadataFieldApi.disableMetadataField(row.id)
    ElMessage.success('已停用')
  } else {
    await metadataFieldApi.enableMetadataField(row.id)
    ElMessage.success('已启用')
  }
  await fetchPage()
}
</script>

<template>
  <div class="metadata-field-management">
    <el-tabs v-model="activeBizType" @tab-change="handleTabChange">
      <el-tab-pane
        v-for="opt in FORM_FIELD_BIZ_TYPE_OPTIONS"
        :key="opt.value"
        :label="opt.label"
        :name="opt.value"
      />
    </el-tabs>

    <el-table v-loading="listLoading" :data="list" empty-text="暂无元数据字段">
      <el-table-column prop="tableName" label="表名称" min-width="120" />
      <el-table-column prop="columnName" label="字段列名" min-width="120" />
      <el-table-column prop="columnType" label="字段类型" min-width="120" />
      <el-table-column prop="fieldCode" label="字段标识" min-width="120" />
      <el-table-column prop="fieldName" label="字段名称" min-width="120" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag v-if="(row as MetadataField).status === METADATA_FIELD_STATUS_ENABLED" type="success">
            启用
          </el-tag>
          <el-tag v-else type="warning">停用</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button v-if="hasPermission('MetadataFieldManagement:metadataField:detail')" link type="primary" @click="openDetailDialog(row as MetadataField)">详情</el-button>
          <el-button v-if="hasPermission('MetadataFieldManagement:metadataField:edit')" link type="primary" @click="openEditDialog(row as MetadataField)">编辑</el-button>
          <el-button
            link
            :type="(row as MetadataField).status === METADATA_FIELD_STATUS_ENABLED ? 'warning' : 'success'"
            v-if="(row as MetadataField).status === METADATA_FIELD_STATUS_ENABLED ? hasPermission('MetadataFieldManagement:metadataField:disable') : hasPermission('MetadataFieldManagement:metadataField:enable')"
            @click="toggleStatus(row as MetadataField)"
          >
            {{ (row as MetadataField).status === METADATA_FIELD_STATUS_ENABLED ? '停用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="metadata-field-pagination"
      background
      layout="sizes, prev, pager, next, total"
      :page-sizes="[...PAGE_SIZE_OPTIONS]"
      :current-page="page"
      :page-size="pageSize"
      :total="total"
      @current-change="handlePageChange"
      @size-change="handleSizeChange"
    />

    <el-dialog v-model="editDialogVisible" title="编辑元数据字段" width="480px" @close="closeEditDialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="字段名称" prop="fieldName">
          <el-input v-model="form.fieldName" placeholder="请输入字段名称" />
        </el-form-item>
        <el-form-item label="字段标识" prop="fieldCode">
          <el-input v-model="form.fieldCode" placeholder="请输入字段标识" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailDialogVisible" title="元数据字段详情" width="480px">
      <el-descriptions v-if="detailRow" :column="1" border>
        <el-descriptions-item label="表名称">{{ detailRow.tableName }}</el-descriptions-item>
        <el-descriptions-item label="字段列名">{{ detailRow.columnName }}</el-descriptions-item>
        <el-descriptions-item label="字段类型">{{ detailRow.columnType }}</el-descriptions-item>
        <el-descriptions-item label="字段标识">{{ detailRow.fieldCode }}</el-descriptions-item>
        <el-descriptions-item label="字段名称">{{ detailRow.fieldName }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag v-if="detailRow.status === METADATA_FIELD_STATUS_ENABLED" type="success">启用</el-tag>
          <el-tag v-else type="warning">停用</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detailRow.createBy }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detailRow.createTime }}</el-descriptions-item>
        <el-descriptions-item label="更新人">{{ detailRow.updateBy }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ detailRow.updateTime }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.metadata-field-management {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

.metadata-field-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

:deep(.el-table .el-button + .el-button) {
  margin-left: 6px;
}
</style>
