<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, TreeInstance } from 'element-plus'
import type Node from 'element-plus/es/components/tree/src/model/node'
import { useOrgStore } from '@/stores/org'
import * as orgApi from '@/api/org'
import * as excelImportApi from '@/api/excelImport'
import BatchImportDialog from '@/components/BatchImportDialog.vue'
import { ORG_STATUS_ENABLED, type OrgFormRequest, type OrgRow, type OrgTreeNode } from '@/types/org'
import { useDynamicFormFields } from '@/composables/useDynamicFormFields'
import {
  FORM_FIELD_CONTROL_TYPE_DATE,
  FORM_FIELD_CONTROL_TYPE_DICT,
  FORM_FIELD_CONTROL_TYPE_MULTI_DICT,
  FORM_FIELD_CONTROL_TYPE_NUMBER,
  FORM_FIELD_CONTROL_TYPE_TEXT,
} from '@/types/formField'

const orgStore = useOrgStore()
const router = useRouter()

// 除上级组织（parentId）、启停用状态（status）外的全部字段（含原有表字段与 ext1~ext10）
// 统一按"表单字段定义"（bizType=ORG）动态渲染，见 design.md 决策 12
const orgFields = useDynamicFormFields('ORG')

onMounted(() => {
  orgFields.fetchSchema()
  // 左侧导航树不需要在这里主动拉取顶级节点：el-tree 在 lazy 模式下挂载时会自动对
  // 根节点调用一次 loadNode（parentId = 0），navTreeTopLevel 只用于增删改之后的
  // 局部刷新（见 orgStore.refreshNavTreeBranch），这里主动调用会造成重复请求
  // 隐式触发一次顶级组织（parentId = 0）的分页查询填充右侧表格，
  // 但不调用 selectNode／不设置 selectedId：默认态下标题仍保持空白，
  // 表格数据源（parentId）与标题（selectedId）是两个独立状态，详见 design.md 决策 3
  //
  // 全量组织树（orgStore.tree，供弹窗“上级组织”选择器用）不在这里预加载：
  // 只有打开新增/编辑弹窗时才需要它，此处预加载会让绝大多数从不新增/编辑的页面访问
  // 都白白发一次 GET /api/orgs/tree（见 openCreateDialog/openEditDialog）
  orgStore.fetchChildren(0)
})

// ---- 左侧组织树（懒加载） ----

const treeProps = { label: 'name', children: 'children' }
const treeRef = ref<TreeInstance>()

// el-tree 的 load 回调：node.level === 0 时代表根这一层（虚拟根，非某个真实节点），
// 请求 parentId = 0；否则用当前节点数据的 id 作为 parentId
function loadNode(node: Node, resolve: (data: OrgTreeNode[]) => void) {
  const parentId = node.level === 0 ? 0 : (node.data as OrgTreeNode).id
  orgStore.loadNavTreeChildren(parentId).then(resolve)
}

function handleNodeClick(node: OrgTreeNode) {
  orgStore.selectNode(node.id, node.name)
}

const rightPanelEmptyText = '暂无下级组织'

// 右侧面板标题：未选中任何左侧树节点时保持空白，选中后显示”[组织名称]下级组织”；
// 节点名称直接来自点击事件（懒加载树节点数据自带 name），不依赖全量树查找
const rightPanelTitle = computed(() => {
  if (orgStore.selectedId === null) return ''
  return orgStore.selectedName ? `[${orgStore.selectedName}]下级组织` : ''
})

function handlePageChange(targetPage: number) {
  orgStore.changePage(targetPage)
}

// 增删改成功后，局部刷新左侧导航树中受影响父节点（当前右侧表格对应的 currentParentId）
// 下的子节点，不重新加载/收起整棵树。currentParentId === 0（顶级）时不是树上任何真实
// 节点的 node-key，updateKeyChildren 对它不生效，store 里的 refreshNavTreeBranch 已经
// 处理了这种情况（改为刷新 navTreeTopLevel，整体重新赋给 el-tree 的 :data）。
async function refreshNavTreeAfterMutation() {
  const parentId = orgStore.currentParentId
  const freshChildren = await orgStore.refreshNavTreeBranch(parentId)
  if (parentId !== 0) {
    treeRef.value?.updateKeyChildren(parentId, freshChildren)
  }
}

// ---- Excel 批量导入：下载模板 / 上传批量导入 ----

const batchImportVisible = ref(false)

async function handleDownloadTemplate() {
  await excelImportApi.downloadImportTemplate('ORG')
}

// 批量导入一次可能在文件里任意多个不同父组织下新增/更新组织，局部刷新
// （refreshNavTreeAfterMutation）覆盖不到；改为右侧表格仍局部刷新，左侧导航树整体
// 重置（navTreeVersion++ 触发 el-tree 的 :key 变化、销毁重建，见 design.md 决策 1）
async function handleImported() {
  await orgStore.refreshAfterMutation()
  orgStore.navTreeVersion++
}

// ---- 上级组织选择（新增/编辑弹窗内的 el-tree-select 数据源）----
// 在原始组织树前面拼一个虚拟的“顶级组织”根节点，代表 parentId = 0；
// 该虚拟节点可以被选中——选中它即代表新增/编辑为第一层级组织
const treeSelectData = computed(() => [
  { id: 0, name: '顶级组织', children: orgStore.tree } as OrgTreeNode & { children: OrgTreeNode[] },
])

// 编辑时，上级组织不能选择自己或自己的子孙节点，避免出现环
function pruneSubtree(nodes: OrgTreeNode[], excludeId: number): OrgTreeNode[] {
  return nodes
    .filter((node) => node.id !== excludeId)
    .map((node) => ({ ...node, children: pruneSubtree(node.children ?? [], excludeId) }))
}

const editableTreeSelectData = computed(() => {
  if (editingId.value === null) return treeSelectData.value
  return pruneSubtree(treeSelectData.value, editingId.value)
})

// 在（含虚拟根的）树中查找目标节点从根到自身的祖先路径（含自身），找不到返回 null
function findAncestorPath(
  nodes: (OrgTreeNode & { children?: OrgTreeNode[] })[],
  targetId: number,
  path: number[] = [],
): number[] | null {
  for (const node of nodes) {
    const nextPath = [...path, node.id]
    if (node.id === targetId) return nextPath
    const found = findAncestorPath(node.children ?? [], targetId, nextPath)
    if (found) return found
  }
  return null
}

// 上级组织选择器默认展开的节点 key：
// 新增时只展开虚拟根，仅显示顶级组织；编辑时展开到当前上级组织所在路径（不含其自身），
// 使其在列表中可见但不展开更深层级；每次打开弹窗都靠 treeSelectRenderKey 重新挂载生效
const treeSelectExpandedKeys = computed(() => {
  if (dialogMode.value === 'create' || form.parentId === null) return [0]
  const path = findAncestorPath(editableTreeSelectData.value, form.parentId)
  return path ? path.slice(0, -1) : [0]
})

// ---- 新增/编辑弹窗 ----

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()
// el-tree 只在初始挂载时读取 default-expanded-keys，此后非响应式；弹窗内容常驻不会
// 因切换新增/编辑重新挂载，因此每次打开弹窗都递增这个 key 强制 el-tree-select 重新挂载
const treeSelectRenderKey = ref(0)

// 表单里的 parentId 在“未选中任何左侧树节点就新增”时允许暂时为空（由用户手动选择），
// 提交前会校验为必填，真正提交给后端时保证已经是合法的 number；其余字段（name/code/
// showOrder/remark/ext1~ext10）由 orgFields 动态渲染驱动，key 为各自绑定的 columnName，
// 不在这里静态声明
type OrgForm = { parentId: number | null } & Record<string, unknown>

const form = reactive<OrgForm>({ parentId: null })

// 保留 form 里指定 key（如 parentId），清空其余动态字段的 key，供每次打开弹窗前重置
function resetDynamicKeys(target: Record<string, unknown>, keep: string[]) {
  Object.keys(target).forEach((key) => {
    if (!keep.includes(key)) delete target[key]
  })
}

const rules = computed<FormRules>(() => ({
  parentId: [{ required: true, message: '请选择上级组织', trigger: 'change' }],
  ...(dialogMode.value === 'create' ? orgFields.createRules : orgFields.editRules),
}))

const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增组织' : '编辑组织'))

// 上级组织选择器要用的全量组织树只在真正打开弹窗时才按需请求（见 design.md 决策 1），
// 请求完成、拿到最新数据后再展示弹窗
async function openCreateDialog() {
  await orgStore.fetchTree()
  dialogMode.value = 'create'
  editingId.value = null
  resetDynamicKeys(form, ['parentId'])
  Object.assign(form, orgFields.buildFormModel(orgFields.createFields))
  // 未选中左侧树节点时不预填默认值，交由用户在弹窗里手动选择一个真实组织节点
  form.parentId = orgStore.selectedId
  treeSelectRenderKey.value++
  dialogVisible.value = true
}

async function openEditDialog(row: OrgRow) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  const detail = await orgApi.getOrgById(row.id)
  await orgStore.fetchTree()
  resetDynamicKeys(form, ['parentId'])
  Object.assign(form, orgFields.buildFormModel(orgFields.editFields, detail))
  form.parentId = detail.parentId
  treeSelectRenderKey.value++
  dialogVisible.value = true
}

function closeDialog() {
  dialogVisible.value = false
  formRef.value?.clearValidate()
}

// ---- 只读详情：跳转独立详情页 ----

function goToDetail(row: OrgRow) {
  router.push({ name: 'identity-orgs-detail', params: { id: row.id } })
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    // 上面的表单校验已经确保 parentId 必填，此处已知非空；动态字段（含 MULTI_DICT）
    // 先经 buildSubmitModel 把多选字典的数组值序列化成逗号分隔字符串，再与静态覆盖
    // 字段合并，避免把数组直接提交给后端导致 HttpMessageNotReadableException
    const activeFields = dialogMode.value === 'create' ? orgFields.createFields : orgFields.editFields
    const payload = {
      ...orgFields.buildSubmitModel(activeFields, form),
      parentId: form.parentId as number,
    } as unknown as OrgFormRequest
    if (dialogMode.value === 'create') {
      await orgApi.createOrg(payload)
      ElMessage.success('新增成功')
    } else {
      await orgApi.updateOrg(editingId.value as number, payload)
      ElMessage.success('保存成功')
    }
    dialogVisible.value = false
    await orgStore.refreshAfterMutation()
    await refreshNavTreeAfterMutation()
  } finally {
    submitting.value = false
  }
}

// ---- 行操作：启用/停用、删除 ----

async function toggleStatus(row: OrgRow) {
  if (row.status === ORG_STATUS_ENABLED) {
    await orgApi.disableOrg(row.id)
    ElMessage.success('已停用')
  } else {
    await orgApi.enableOrg(row.id)
    ElMessage.success('已启用')
  }
  await orgStore.refreshAfterMutation()
  await refreshNavTreeAfterMutation()
}

async function handleDelete(row: OrgRow) {
  await ElMessageBox.confirm(`确定要删除组织「${row.name}」吗？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await orgApi.deleteOrg(row.id)
  ElMessage.success('删除成功')
  await orgStore.refreshAfterMutation()
  await refreshNavTreeAfterMutation()
}
</script>

<template>
  <div class="org-management">
    <section class="org-panel org-panel--tree">
      <header class="org-panel__header">
        <h2 class="org-panel__title">组织架构</h2>
      </header>
      <el-tree
        :key="orgStore.navTreeVersion"
        ref="treeRef"
        v-loading="orgStore.navTreeLoading"
        class="org-tree"
        :data="orgStore.navTreeTopLevel"
        :props="treeProps"
        node-key="id"
        :indent="8"
        lazy
        :load="loadNode"
        highlight-current
        :current-node-key="orgStore.selectedId ?? undefined"
        @node-click="handleNodeClick"
      />
    </section>

    <section class="org-panel org-panel--table">
      <header class="org-panel__header">
        <!--
          标题依赖 selectedId（未选中任何左侧树节点时保持空白），
          与下方表格的数据源（当前查询用的 parentId，默认展示顶级组织）是两个独立状态，
          默认态下“标题空白、表格已有数据”是有意为之，不要为了“统一”把两者合并
        -->
        <h2 class="org-panel__title">{{ rightPanelTitle }}</h2>
        <div class="org-panel__actions">
          <el-button @click="handleDownloadTemplate">下载导入模板</el-button>
          <el-button @click="batchImportVisible = true">批量导入</el-button>
          <el-button type="primary" @click="openCreateDialog">新增</el-button>
        </div>
      </header>

      <el-table
        v-loading="orgStore.childrenLoading"
        :data="orgStore.children"
        :empty-text="rightPanelEmptyText"
      >
        <el-table-column
          v-for="col in orgFields.listColumns"
          :key="col.fieldCode"
          :label="col.fieldName"
          min-width="120"
        >
          <template #default="{ row }">
            <span v-if="col.controlType === FORM_FIELD_CONTROL_TYPE_DICT">
              {{ orgFields.dictOptionLabel(col, (row as Record<string, unknown>)[col.columnName]) }}
            </span>
            <span v-else-if="col.controlType === FORM_FIELD_CONTROL_TYPE_MULTI_DICT">
              {{ orgFields.dictOptionLabels(col, (row as Record<string, unknown>)[col.columnName]) }}
            </span>
            <span v-else>{{ (row as Record<string, unknown>)[col.columnName] }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.status === ORG_STATUS_ENABLED" type="success">启用</el-tag>
            <el-tag v-else type="warning">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="goToDetail(row as OrgRow)">详情</el-button>
            <el-button link type="primary" @click="openEditDialog(row as OrgRow)">编辑</el-button>
            <el-button
              link
              :type="row.status === ORG_STATUS_ENABLED ? 'warning' : 'success'"
              @click="toggleStatus(row as OrgRow)"
            >
              {{ row.status === ORG_STATUS_ENABLED ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" @click="handleDelete(row as OrgRow)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="org-pagination"
        background
        layout="prev, pager, next, total"
        :current-page="orgStore.page"
        :page-size="orgStore.pageSize"
        :total="orgStore.total"
        @current-change="handlePageChange"
      />
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="480px" @close="closeDialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="上级组织" prop="parentId">
          <!-- check-strictly：el-tree-select 默认单选模式下只有叶子节点可被点击选中，
               非叶子节点点击只会展开/收起；上级组织可能本身已经有子组织，必须开启
               check-strictly 才能把任意真实组织节点选为上级 -->
          <el-tree-select
            :key="treeSelectRenderKey"
            v-model="form.parentId"
            :data="editableTreeSelectData"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            :default-expanded-keys="treeSelectExpandedKeys"
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item
          v-for="item in (dialogMode === 'create' ? orgFields.createFields : orgFields.editFields)"
          :key="item.fieldCode"
          :label="item.fieldName"
          :prop="item.columnName"
        >
          <el-input
            v-if="item.controlType === FORM_FIELD_CONTROL_TYPE_TEXT"
            v-model="(form[item.columnName] as string)"
            :placeholder="item.placeholder || `请输入${item.fieldName}`"
            :disabled="!item.editable"
          />
          <el-input-number
            v-else-if="item.controlType === FORM_FIELD_CONTROL_TYPE_NUMBER"
            v-model="(form[item.columnName] as number)"
            :min="0"
            style="width: 100%"
            :disabled="!item.editable"
          />
          <el-date-picker
            v-else-if="item.controlType === FORM_FIELD_CONTROL_TYPE_DATE"
            v-model="(form[item.columnName] as string | null)"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
            :placeholder="item.placeholder || `请选择${item.fieldName}`"
            :disabled="!item.editable"
          />
          <el-select
            v-else-if="item.controlType === FORM_FIELD_CONTROL_TYPE_MULTI_DICT"
            v-model="(form[item.columnName] as string[])"
            multiple
            :placeholder="item.placeholder || `请选择${item.fieldName}`"
            :disabled="!item.editable"
            style="width: 100%"
          >
            <el-option v-for="opt in item.dictOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
          <template v-else>
            <el-select
              v-model="(form[item.columnName] as string)"
              :placeholder="item.placeholder || `请选择${item.fieldName}`"
              :disabled="!item.editable"
              style="width: 100%"
            >
              <el-option v-for="opt in item.dictOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </template>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <batch-import-dialog v-model="batchImportVisible" biz-type="ORG" @imported="handleImported" />
  </div>
</template>

<style scoped lang="scss">
.org-management {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 16px;
  align-items: start;

  @media (max-width: 960px) {
    grid-template-columns: 1fr;
  }
}

.org-panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
  // grid item 的隐式 min-width 默认是 auto（不小于内容的最小内在宽度），
  // 右侧表格列宽之和常常超过 1fr 轨道的实际宽度，若不覆盖为 0，
  // 面板（含头部的“新增”按钮）会被表格撑宽，导致要滚动整个页面才能看到按钮。
  min-width: 0;
}

.org-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.org-panel__title {
  font-size: 15px;
  color: var(--color-ink);
  margin: 0;
}

.org-panel__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.org-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

// 用一条虚线 + 圆点把父子层级"连"起来，呼应侧边栏子菜单的链式视觉语言
.org-tree :deep(.el-tree-node__children) {
  position: relative;
  margin-left: 4px;
  padding-left: 6px;
  border-left: 1px dashed var(--chain-line-color);
}

.org-tree :deep(.el-tree-node__children > .el-tree-node) {
  position: relative;
}

// 圆点的 left 值须与上面 .el-tree-node__children 的 padding-left 保持一致（互为负值），
// 否则圆点会偏离虚线
.org-tree :deep(.el-tree-node__children > .el-tree-node::before) {
  content: '';
  position: absolute;
  left: -6px;
  top: 14px;
  width: var(--chain-dot-size-sm);
  height: var(--chain-dot-size-sm);
  border-radius: 50%;
  background: var(--chain-line-color);
}

.org-tree :deep(.el-tree-node.is-current > .el-tree-node__children::before),
.org-tree :deep(.el-tree-node.is-current::before) {
  background: var(--chain-line-color-active);
}

.org-form-hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-top: 4px;
}

// 操作列（详情/编辑/启用停用/删除）相邻按钮间距收紧，比 Element Plus 默认更紧凑
:deep(.el-table .el-button + .el-button) {
  margin-left: 6px;
}
</style>
