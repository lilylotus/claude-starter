<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, TreeInstance } from 'element-plus'
import type Node from 'element-plus/es/components/tree/src/model/node'
import { useMenuStore } from '@/stores/menu'
import OperationHistoryPanel from '@/components/OperationHistoryPanel.vue'
import * as menuApi from '@/api/menu'
import {
  MENU_RESOURCE_TYPE_LABELS,
  MENU_RESOURCE_TYPE_MENU,
  MENU_RESOURCE_TYPE_OPTIONS,
  MENU_STATUS_ENABLED,
  type MenuResourceFormRequest,
  type MenuResourceRow,
  type MenuResourceTreeNode,
} from '@/types/menuResource'

const menuStore = useMenuStore()

onMounted(() => {
  // 交互模式照抄 OrgManagementView：不在这里主动触发左侧懒加载树的顶级请求
  // （el-tree 在 lazy 模式下挂载时会自动对根节点调用一次 loadNode），
  // 也不预加载全量资源树（fetchTree，只在打开新增/编辑弹窗时才需要）；
  // 这里只填充右侧表格默认展示的顶级资源第一页数据，不设置 selectedId，
  // 保持“表格数据源（parentId）”与“标题（selectedId）”两个独立状态。
  menuStore.fetchChildren(0)
})

// ---- 左侧资源树（懒加载） ----

const treeProps = { label: 'name', children: 'children' }
const treeRef = ref<TreeInstance>()

function loadNode(node: Node, resolve: (data: MenuResourceTreeNode[]) => void) {
  const parentId = node.level === 0 ? 0 : (node.data as MenuResourceTreeNode).id
  menuStore.loadNavTreeChildren(parentId).then(resolve)
}

function handleNodeClick(node: MenuResourceTreeNode) {
  menuStore.selectNode(node.id, node.name)
}

const rightPanelEmptyText = '暂无下级资源'

const rightPanelTitle = computed(() => {
  if (menuStore.selectedId === null) return ''
  return menuStore.selectedName ? `[${menuStore.selectedName}]下级资源` : ''
})

function handlePageChange(targetPage: number) {
  menuStore.changePage(targetPage)
}

function resourceTypeLabel(resourceType: number): string {
  return MENU_RESOURCE_TYPE_LABELS[resourceType] ?? '-'
}

// 增删改成功后，局部刷新左侧导航树中受影响父节点下的子节点，不重新加载/收起整棵树
async function refreshNavTreeAfterMutation() {
  const parentId = menuStore.currentParentId
  const freshChildren = await menuStore.refreshNavTreeBranch(parentId)
  if (parentId !== 0) {
    treeRef.value?.updateKeyChildren(parentId, freshChildren)
  }
}

// ---- 上级资源选择（新增/编辑弹窗内的 el-tree-select 数据源）----
const treeSelectData = computed(() => [
  { id: 0, name: '顶级资源', children: menuStore.tree } as MenuResourceTreeNode & { children: MenuResourceTreeNode[] },
])

// 编辑时，上级资源不能选择自己或自己的子孙节点，避免出现环
function pruneSubtree(nodes: MenuResourceTreeNode[], excludeId: number): MenuResourceTreeNode[] {
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
  nodes: (MenuResourceTreeNode & { children?: MenuResourceTreeNode[] })[],
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

// 上级资源选择器默认展开的节点 key：新增时只展开虚拟根，编辑时展开到当前上级资源所在路径
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
const treeSelectRenderKey = ref(0)

type MenuForm = Omit<MenuResourceFormRequest, 'parentId'> & {
  parentId: number | null
}

const form = reactive<MenuForm>({
  name: '',
  code: '',
  parentId: null,
  resourceType: MENU_RESOURCE_TYPE_MENU,
  showOrder: 0,
  remark: '',
})

const rules: FormRules<MenuForm> = {
  name: [{ required: true, message: '请输入资源名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入资源编码', trigger: 'blur' }],
  parentId: [{ required: true, message: '请选择上级资源', trigger: 'change' }],
  resourceType: [{ required: true, message: '请选择资源类型', trigger: 'change' }],
}

const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增资源' : '编辑资源'))

async function openCreateDialog() {
  await menuStore.fetchTree()
  dialogMode.value = 'create'
  editingId.value = null
  form.name = ''
  form.code = ''
  form.parentId = menuStore.selectedId
  form.resourceType = MENU_RESOURCE_TYPE_MENU
  form.showOrder = 0
  form.remark = ''
  treeSelectRenderKey.value++
  dialogVisible.value = true
}

async function openEditDialog(row: MenuResourceRow) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  const detail = await menuApi.getMenuById(row.id)
  await menuStore.fetchTree()
  form.name = detail.name
  form.code = detail.code
  form.parentId = detail.parentId
  form.resourceType = detail.resourceType
  form.showOrder = detail.showOrder
  form.remark = detail.remark
  treeSelectRenderKey.value++
  dialogVisible.value = true
}

function closeDialog() {
  dialogVisible.value = false
  formRef.value?.clearValidate()
}

// ---- 只读详情弹窗 ----

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref<MenuResourceRow | null>(null)

async function openDetailDialog(row: MenuResourceRow) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detailData.value = await menuApi.getMenuById(row.id)
  } finally {
    detailLoading.value = false
  }
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    // 上面的表单校验已经确保 parentId/resourceType 必填，此处已知非空
    const payload = form as MenuResourceFormRequest
    if (dialogMode.value === 'create') {
      await menuApi.createMenu(payload)
      ElMessage.success('新增成功')
    } else {
      await menuApi.updateMenu(editingId.value as number, payload)
      ElMessage.success('保存成功')
    }
    dialogVisible.value = false
    await menuStore.refreshAfterMutation()
    await refreshNavTreeAfterMutation()
  } finally {
    submitting.value = false
  }
}

// ---- 行操作：启用/停用、删除 ----

async function toggleStatus(row: MenuResourceRow) {
  if (row.status === MENU_STATUS_ENABLED) {
    await menuApi.disableMenu(row.id)
    ElMessage.success('已停用')
  } else {
    await menuApi.enableMenu(row.id)
    ElMessage.success('已启用')
  }
  await menuStore.refreshAfterMutation()
  await refreshNavTreeAfterMutation()
}

async function handleDelete(row: MenuResourceRow) {
  await ElMessageBox.confirm(`确定要删除资源「${row.name}」吗？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await menuApi.deleteMenu(row.id)
  ElMessage.success('删除成功')
  await menuStore.refreshAfterMutation()
  await refreshNavTreeAfterMutation()
}
</script>

<template>
  <div class="menu-management">
    <section class="menu-panel menu-panel--tree">
      <header class="menu-panel__header">
        <h2 class="menu-panel__title">资源树</h2>
      </header>
      <el-tree
        ref="treeRef"
        v-loading="menuStore.navTreeLoading"
        class="menu-tree"
        :data="menuStore.navTreeTopLevel"
        :props="treeProps"
        node-key="id"
        :indent="8"
        lazy
        :load="loadNode"
        highlight-current
        :current-node-key="menuStore.selectedId ?? undefined"
        @node-click="handleNodeClick"
      />
    </section>

    <section class="menu-panel menu-panel--table">
      <header class="menu-panel__header">
        <h2 class="menu-panel__title">{{ rightPanelTitle }}</h2>
        <el-button type="primary" @click="openCreateDialog">新增</el-button>
      </header>

      <el-table
        v-loading="menuStore.childrenLoading"
        :data="menuStore.children"
        :empty-text="rightPanelEmptyText"
      >
        <el-table-column prop="name" label="资源名称" min-width="140" />
        <el-table-column prop="code" label="资源编码" min-width="120" />
        <el-table-column label="资源类型" width="100">
          <template #default="{ row }">
            <el-tag>{{ resourceTypeLabel(row.resourceType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="showOrder" label="显示序号" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.status === MENU_STATUS_ENABLED" type="success">启用</el-tag>
            <el-tag v-else type="warning">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetailDialog(row as MenuResourceRow)">详情</el-button>
            <el-button link type="primary" @click="openEditDialog(row as MenuResourceRow)">编辑</el-button>
            <el-button
              link
              :type="row.status === MENU_STATUS_ENABLED ? 'warning' : 'success'"
              @click="toggleStatus(row as MenuResourceRow)"
            >
              {{ row.status === MENU_STATUS_ENABLED ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" @click="handleDelete(row as MenuResourceRow)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="menu-pagination"
        background
        layout="prev, pager, next, total"
        :current-page="menuStore.page"
        :page-size="menuStore.pageSize"
        :total="menuStore.total"
        @current-change="handlePageChange"
      />
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="480px" @close="closeDialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="资源名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入资源名称" />
        </el-form-item>
        <el-form-item label="资源编码" prop="code">
          <el-input v-model="form.code" placeholder="请输入资源编码" />
        </el-form-item>
        <el-form-item label="上级资源" prop="parentId">
          <!-- check-strictly：非叶子节点默认点击只会展开/收起，必须开启才能把任意真实资源节点选为上级 -->
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
        <el-form-item label="资源类型" prop="resourceType">
          <el-radio-group v-model="form.resourceType">
            <el-radio-button v-for="option in MENU_RESOURCE_TYPE_OPTIONS" :key="option.value" :value="option.value">
              {{ option.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="显示序号" prop="showOrder">
          <el-input-number v-model="form.showOrder" :min="0" style="width: 100%" />
          <div class="menu-form-hint">数值越大，排序越靠前</div>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="资源详情" width="480px" destroy-on-close>
      <el-descriptions v-loading="detailLoading" :column="1" border>
        <el-descriptions-item label="资源名称">{{ detailData?.name }}</el-descriptions-item>
        <el-descriptions-item label="资源编码">{{ detailData?.code }}</el-descriptions-item>
        <el-descriptions-item label="上级资源">{{ detailData?.parentName ?? '顶级资源' }}</el-descriptions-item>
        <el-descriptions-item label="资源类型">
          {{ detailData ? resourceTypeLabel(detailData.resourceType) : '' }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag v-if="detailData?.status === MENU_STATUS_ENABLED" type="success">启用</el-tag>
          <el-tag v-else type="warning">停用</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="显示序号">{{ detailData?.showOrder }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detailData?.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="新增人">{{ detailData?.createBy }}</el-descriptions-item>
        <el-descriptions-item label="新增时间">{{ detailData?.createTime }}</el-descriptions-item>
        <el-descriptions-item label="更新人">{{ detailData?.updateBy }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ detailData?.updateTime }}</el-descriptions-item>
      </el-descriptions>

      <OperationHistoryPanel resource-type="menu" :target-id="detailData?.id ?? null" />

      <template #footer>
        <el-button type="primary" @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.menu-management {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 16px;
  align-items: start;

  @media (max-width: 960px) {
    grid-template-columns: 1fr;
  }
}

.menu-panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
  min-width: 0;
}

.menu-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.menu-panel__title {
  font-size: 15px;
  color: var(--color-ink);
  margin: 0;
}

.menu-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

// 用一条虚线 + 圆点把父子层级"连"起来，呼应侧边栏子菜单的链式视觉语言
.menu-tree :deep(.el-tree-node__children) {
  position: relative;
  margin-left: 4px;
  padding-left: 6px;
  border-left: 1px dashed var(--chain-line-color);
}

.menu-tree :deep(.el-tree-node__children > .el-tree-node) {
  position: relative;
}

.menu-tree :deep(.el-tree-node__children > .el-tree-node::before) {
  content: '';
  position: absolute;
  left: -6px;
  top: 14px;
  width: var(--chain-dot-size-sm);
  height: var(--chain-dot-size-sm);
  border-radius: 50%;
  background: var(--chain-line-color);
}

.menu-tree :deep(.el-tree-node.is-current > .el-tree-node__children::before),
.menu-tree :deep(.el-tree-node.is-current::before) {
  background: var(--chain-line-color-active);
}

.menu-form-hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-top: 4px;
}

:deep(.el-table .el-button + .el-button) {
  margin-left: 6px;
}
</style>
