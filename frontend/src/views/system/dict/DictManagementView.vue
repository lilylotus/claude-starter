<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { useDictStore } from '@/stores/dict'
import * as dictApi from '@/api/dict'
import { PAGE_SIZE_OPTIONS } from '@/constants/pagination'
import {
  DICT_STATUS_ENABLED,
  type DictItemFormRequest,
  type DictItemRow,
  type DictTypeFormRequest,
  type DictTypeRow,
} from '@/types/dict'
import { usePermission } from '@/composables/usePermission'

const dictStore = useDictStore()
const router = useRouter()
const { hasPermission } = usePermission()

onMounted(() => {
  dictStore.fetchTypes()
})

// ---- 左侧：字典类型分页列表 + 搜索 ----

const typeKeywordInput = ref('')

function handleTypeSearch() {
  dictStore.searchTypes(typeKeywordInput.value.trim())
}

function handleTypeSearchClear() {
  typeKeywordInput.value = ''
  dictStore.searchTypes('')
}

function handleTypesPageChange(targetPage: number) {
  dictStore.changeTypesPage(targetPage)
}

// 点击左侧一行：选中该字典类型，右侧联动展示其字典项（重置为第一页）
function handleTypeRowClick(row: DictTypeRow) {
  dictStore.selectType(row)
}

// 右侧面板标题：未选中任何左侧字典类型时保持空白
const rightPanelTitle = computed(() =>
  dictStore.selectedTypeId === null ? '' : `${dictStore.selectedTypeName}的字典项`,
)

const rightPanelEmptyText = computed(() =>
  dictStore.selectedTypeId === null ? '请选择左侧字典类型' : '暂无字典项',
)

function handleItemsPageChange(targetPage: number) {
  dictStore.changeItemsPage(targetPage)
}

// ---- 字典类型：新增/编辑弹窗 ----

const typeDialogVisible = ref(false)
const typeDialogMode = ref<'create' | 'edit'>('create')
const typeEditingId = ref<number | null>(null)
const typeSubmitting = ref(false)
const typeFormRef = ref<FormInstance>()

const typeForm = reactive<DictTypeFormRequest>({
  name: '',
  code: '',
  showOrder: 0,
  remark: '',
})

const typeRules: FormRules<DictTypeFormRequest> = {
  name: [{ required: true, message: '请输入字典类型名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }],
}

const typeDialogTitle = computed(() => (typeDialogMode.value === 'create' ? '新增字典类型' : '编辑字典类型'))

function openCreateTypeDialog() {
  typeDialogMode.value = 'create'
  typeEditingId.value = null
  typeForm.name = ''
  typeForm.code = ''
  typeForm.showOrder = 0
  typeForm.remark = ''
  typeDialogVisible.value = true
}

async function openEditTypeDialog(row: DictTypeRow) {
  typeDialogMode.value = 'edit'
  typeEditingId.value = row.id
  const detail = await dictApi.getDictTypeById(row.id)
  typeForm.name = detail.name
  typeForm.code = detail.code
  typeForm.showOrder = detail.showOrder
  typeForm.remark = detail.remark
  typeDialogVisible.value = true
}

function closeTypeDialog() {
  typeDialogVisible.value = false
  typeFormRef.value?.clearValidate()
}

// ---- 字典类型：只读详情：跳转独立详情页 ----

function goToTypeDetail(row: DictTypeRow) {
  router.push({ name: 'system-dicts-type-detail', params: { id: row.id } })
}

async function submitTypeForm() {
  const valid = await typeFormRef.value?.validate().catch(() => false)
  if (!valid) return

  typeSubmitting.value = true
  try {
    if (typeDialogMode.value === 'create') {
      await dictApi.createDictType(typeForm)
      ElMessage.success('新增成功')
      typeDialogVisible.value = false
      await dictStore.refreshAfterTypeMutation()
    } else {
      const id = typeEditingId.value as number
      await dictApi.updateDictType(id, typeForm)
      ElMessage.success('保存成功')
      typeDialogVisible.value = false
      await dictStore.refreshAfterTypeMutation(id)
    }
  } finally {
    typeSubmitting.value = false
  }
}

// ---- 字典类型：启用/停用、删除 ----

async function toggleTypeStatus(row: DictTypeRow) {
  if (row.status === DICT_STATUS_ENABLED) {
    await dictApi.disableDictType(row.id)
    ElMessage.success('已停用')
  } else {
    await dictApi.enableDictType(row.id)
    ElMessage.success('已启用')
  }
  await dictStore.refreshAfterTypeMutation(row.id)
}

async function handleDeleteType(row: DictTypeRow) {
  await ElMessageBox.confirm(`确定要删除字典类型「${row.name}」吗？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await dictApi.deleteDictType(row.id)
  ElMessage.success('删除成功')
  await dictStore.refreshAfterTypeMutation(row.id)
}

// ---- 字典项：新增/编辑弹窗 ----

const itemDialogVisible = ref(false)
const itemDialogMode = ref<'create' | 'edit'>('create')
const itemEditingId = ref<number | null>(null)
const itemSubmitting = ref(false)
const itemFormRef = ref<FormInstance>()

const itemForm = reactive<DictItemFormRequest>({
  label: '',
  code: '',
  showOrder: 0,
  remark: '',
})

const itemRules: FormRules<DictItemFormRequest> = {
  label: [{ required: true, message: '请输入字典项标签', trigger: 'blur' }],
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }],
}

const itemDialogTitle = computed(() => (itemDialogMode.value === 'create' ? '新增字典项' : '编辑字典项'))

function openCreateItemDialog() {
  // 按钮在未选中左侧字典类型时禁用，理论上不会在 selectedTypeId 为 null 时被调用
  if (dictStore.selectedTypeId === null) return
  itemDialogMode.value = 'create'
  itemEditingId.value = null
  itemForm.label = ''
  itemForm.code = ''
  itemForm.showOrder = 0
  itemForm.remark = ''
  itemDialogVisible.value = true
}

async function openEditItemDialog(row: DictItemRow) {
  itemDialogMode.value = 'edit'
  itemEditingId.value = row.id
  const detail = await dictApi.getDictItemById(row.id)
  itemForm.label = detail.label
  itemForm.code = detail.code
  itemForm.showOrder = detail.showOrder
  itemForm.remark = detail.remark
  itemDialogVisible.value = true
}

function closeItemDialog() {
  itemDialogVisible.value = false
  itemFormRef.value?.clearValidate()
}

// ---- 字典项：只读详情：跳转独立详情页 ----

function goToItemDetail(row: DictItemRow) {
  router.push({ name: 'system-dicts-item-detail', params: { id: row.id } })
}

async function submitItemForm() {
  const valid = await itemFormRef.value?.validate().catch(() => false)
  if (!valid) return
  if (dictStore.selectedTypeId === null) return

  itemSubmitting.value = true
  try {
    if (itemDialogMode.value === 'create') {
      // 新增字典项时所属类型固定为当前选中的字典类型，不提供切换入口
      await dictApi.createDictItem({ ...itemForm, dictTypeId: dictStore.selectedTypeId })
      ElMessage.success('新增成功')
    } else {
      await dictApi.updateDictItem(itemEditingId.value as number, itemForm)
      ElMessage.success('保存成功')
    }
    itemDialogVisible.value = false
    await dictStore.refreshAfterItemMutation()
  } finally {
    itemSubmitting.value = false
  }
}

// ---- 字典项：启用/停用、删除 ----

async function toggleItemStatus(row: DictItemRow) {
  if (row.status === DICT_STATUS_ENABLED) {
    await dictApi.disableDictItem(row.id)
    ElMessage.success('已停用')
  } else {
    await dictApi.enableDictItem(row.id)
    ElMessage.success('已启用')
  }
  await dictStore.refreshAfterItemMutation()
}

async function handleDeleteItem(row: DictItemRow) {
  await ElMessageBox.confirm(`确定要删除字典项「${row.label}」吗？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await dictApi.deleteDictItem(row.id)
  ElMessage.success('删除成功')
  await dictStore.refreshAfterItemMutation()
}
</script>

<template>
  <div class="dict-management">
    <section class="dict-panel dict-panel--types">
      <header class="dict-panel__header">
        <h2 class="dict-panel__title">字典类型</h2>
        <el-button v-if="hasPermission('DictManagement:dictType:add')" type="primary" @click="openCreateTypeDialog">新增</el-button>
      </header>

      <el-input
        v-model="typeKeywordInput"
        class="dict-search"
        placeholder="按名称或编码搜索"
        clearable
        @keyup.enter="handleTypeSearch"
        @clear="handleTypeSearchClear"
      >
        <template #suffix>
          <el-icon class="dict-search__icon" @click="handleTypeSearch"><Search /></el-icon>
        </template>
      </el-input>

      <el-table
        v-loading="dictStore.typesLoading"
        :data="dictStore.types"
        highlight-current-row
        row-key="id"
        :current-row-key="dictStore.selectedTypeId ?? undefined"
        @row-click="handleTypeRowClick"
      >
        <el-table-column prop="name" label="名称" min-width="120" />
        <el-table-column prop="code" label="编码" min-width="120" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.status === DICT_STATUS_ENABLED" type="success">启用</el-tag>
            <el-tag v-else type="warning">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button v-if="hasPermission('DictManagement:dictType:detail')" link type="primary" @click.stop="goToTypeDetail(row as DictTypeRow)">详情</el-button>
            <el-button v-if="hasPermission('DictManagement:dictType:edit')" link type="primary" @click.stop="openEditTypeDialog(row as DictTypeRow)">编辑</el-button>
            <el-button
              link
              :type="row.status === DICT_STATUS_ENABLED ? 'warning' : 'success'"
              v-if="row.status === DICT_STATUS_ENABLED ? hasPermission('DictManagement:dictType:disable') : hasPermission('DictManagement:dictType:enable')"
              @click.stop="toggleTypeStatus(row as DictTypeRow)"
            >
              {{ row.status === DICT_STATUS_ENABLED ? '停用' : '启用' }}
            </el-button>
            <el-button v-if="hasPermission('DictManagement:dictType:delete')" link type="danger" @click.stop="handleDeleteType(row as DictTypeRow)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="dict-pagination"
        background
        layout="sizes, prev, pager, next, total"
        :page-sizes="[...PAGE_SIZE_OPTIONS]"
        :current-page="dictStore.typesPage"
        :page-size="dictStore.typesPageSize"
        :total="dictStore.typesTotal"
        @current-change="handleTypesPageChange"
        @size-change="dictStore.changeTypesPageSize"
      />
    </section>

    <section class="dict-panel dict-panel--items">
      <header class="dict-panel__header">
        <!-- 未选中左侧任何字典类型时标题保持空白，是本页面刻意的默认态 -->
        <h2 class="dict-panel__title">{{ rightPanelTitle }}</h2>
        <el-button
          v-if="hasPermission('DictManagement:dictItem:add')"
          type="primary"
          :disabled="dictStore.selectedTypeId === null"
          @click="openCreateItemDialog"
        >
          新增
        </el-button>
      </header>

      <el-table v-loading="dictStore.itemsLoading" :data="dictStore.items" :empty-text="rightPanelEmptyText">
        <el-table-column prop="label" label="标签" min-width="120" />
        <el-table-column prop="code" label="编码" min-width="120" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.status === DICT_STATUS_ENABLED" type="success">启用</el-tag>
            <el-tag v-else type="warning">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="showOrder" label="显示序号" width="90" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button v-if="hasPermission('DictManagement:dictItem:detail')" link type="primary" @click="goToItemDetail(row as DictItemRow)">详情</el-button>
            <el-button v-if="hasPermission('DictManagement:dictItem:edit')" link type="primary" @click="openEditItemDialog(row as DictItemRow)">编辑</el-button>
            <el-button
              link
              :type="row.status === DICT_STATUS_ENABLED ? 'warning' : 'success'"
              v-if="row.status === DICT_STATUS_ENABLED ? hasPermission('DictManagement:dictItem:disable') : hasPermission('DictManagement:dictItem:enable')"
              @click="toggleItemStatus(row as DictItemRow)"
            >
              {{ row.status === DICT_STATUS_ENABLED ? '停用' : '启用' }}
            </el-button>
            <el-button v-if="hasPermission('DictManagement:dictItem:delete')" link type="danger" @click="handleDeleteItem(row as DictItemRow)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="dict-pagination"
        background
        layout="sizes, prev, pager, next, total"
        :page-sizes="[...PAGE_SIZE_OPTIONS]"
        :current-page="dictStore.itemsPage"
        :page-size="dictStore.itemsPageSize"
        :total="dictStore.itemsTotal"
        @current-change="handleItemsPageChange"
        @size-change="dictStore.changeItemsPageSize"
      />
    </section>

    <el-dialog v-model="typeDialogVisible" :title="typeDialogTitle" width="480px" @close="closeTypeDialog">
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-width="90px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="typeForm.name" placeholder="请输入字典类型名称" />
        </el-form-item>
        <el-form-item label="编码" prop="code">
          <el-input v-model="typeForm.code" placeholder="请输入编码" />
        </el-form-item>
        <el-form-item label="显示序号" prop="showOrder">
          <el-input-number v-model="typeForm.showOrder" :min="0" style="width: 100%" />
          <div class="dict-form-hint">数值越大，排序越靠前</div>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="typeForm.remark" type="textarea" :rows="3" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="typeSubmitting" @click="submitTypeForm">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="itemDialogVisible" :title="itemDialogTitle" width="480px" @close="closeItemDialog">
      <el-form ref="itemFormRef" :model="itemForm" :rules="itemRules" label-width="90px">
        <el-form-item label="所属类型">
          <el-input :model-value="dictStore.selectedTypeName" disabled />
        </el-form-item>
        <el-form-item label="标签" prop="label">
          <el-input v-model="itemForm.label" placeholder="请输入字典项标签" />
        </el-form-item>
        <el-form-item label="编码" prop="code">
          <el-input v-model="itemForm.code" placeholder="请输入编码" />
        </el-form-item>
        <el-form-item label="显示序号" prop="showOrder">
          <el-input-number v-model="itemForm.showOrder" :min="0" style="width: 100%" />
          <div class="dict-form-hint">数值越大，排序越靠前</div>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="itemForm.remark" type="textarea" :rows="3" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="itemSubmitting" @click="submitItemForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.dict-management {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  align-items: start;

  @media (max-width: 960px) {
    grid-template-columns: 1fr;
  }
}

.dict-panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
  // grid item 的隐式 min-width 默认是 auto，表格列宽之和常常超过 1fr 轨道的实际宽度，
  // 若不覆盖为 0，面板会被表格撑宽，导致要滚动整个页面才能看到头部按钮
  min-width: 0;
}

.dict-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.dict-panel__title {
  font-size: 15px;
  color: var(--color-ink);
  margin: 0;
}

.dict-search {
  margin-bottom: 12px;
}

.dict-search__icon {
  cursor: pointer;
  color: var(--color-text-tertiary);
}

.dict-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.dict-form-hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-top: 4px;
}

// 左侧当前选中行用品牌蓝底色 + 左侧竖线高亮，呼应组织树"选中态"的链式连接语言
.dict-panel--types :deep(.el-table__row.current-row) {
  position: relative;
  background: var(--color-primary-soft);
}

.dict-panel--types :deep(.el-table__row.current-row td) {
  position: relative;
}

.dict-panel--types :deep(.el-table__row.current-row td:first-child::before) {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--chain-line-color-active);
}

.dict-panel--types :deep(.el-table__row) {
  cursor: pointer;
}

// 操作列相邻按钮间距收紧，比 Element Plus 默认更紧凑
:deep(.el-table .el-button + .el-button) {
  margin-left: 6px;
}
</style>
