<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, TreeInstance } from 'element-plus'
import type Node from 'element-plus/es/components/tree/src/model/node'
import { usePermissionStore } from '@/stores/permission'
import * as permissionApi from '@/api/permission'
import { PERMISSION_STATUS_ENABLED, type PermissionFormRequest, type PermissionRow } from '@/types/permission'
import { buildPermissionTree, resolvePermissionModuleLabel, type PermissionTreeNode } from '@/utils/permissionTree'

const permissionStore = usePermissionStore()
const router = useRouter()

// ---- 按模块分组的两层虚拟树（分组算法复用 src/utils/permissionTree.ts，与角色管理
// 弹窗内的权限点勾选树共用同一份实现，分组节点的中文模块名解析同样复用共享的
// resolvePermissionModuleLabel）。后端 GET /api/permissions/list 已按 showOrder
// 升序、id 升序排好序，buildPermissionTree 只负责分组、保留原始顺序，不重新排序 ----
const permissionTreeData = computed<PermissionTreeNode<PermissionRow>[]>(() =>
  buildPermissionTree(permissionStore.list, resolvePermissionModuleLabel),
)

const treeRef = ref<TreeInstance>()
const treeProps = { label: 'label', children: 'children' }

// 当前展开的节点 key 集合：仅在用户手动展开/收起、或点击"全部展开/全部收起"时变化；
// 增/改/启停用/删除触发的数据刷新不会重置这个集合，从而满足"已展开的分组节点保持
// 展开状态不被折叠"的交互要求——树在数据刷新时会按这个集合重新展开匹配的节点。页面
// 打开时默认全部收起，不做首次自动展开
const expandedKeys = ref<Array<string | number>>([])

async function loadInitialData() {
  await permissionStore.loadList()
}

onMounted(loadInitialData)

function handleNodeExpand(_data: unknown, node: Node) {
  const key = node.data.id as string | number
  if (!expandedKeys.value.includes(key)) expandedKeys.value.push(key)
}

function handleNodeCollapse(_data: unknown, node: Node) {
  const key = node.data.id as string | number
  const index = expandedKeys.value.indexOf(key)
  if (index !== -1) expandedKeys.value.splice(index, 1)
}

function expandAll() {
  expandedKeys.value = permissionTreeData.value.map((group) => group.id)
}

function collapseAll() {
  for (const key of expandedKeys.value) {
    treeRef.value?.getNode(key)?.collapse()
  }
  expandedKeys.value = []
}

// el-tree 默认插槽的 data 参数类型是宽松的 TreeNodeData（Record<string, any>），这里
// 收窄回 buildPermissionTree 实际产出的类型，避免模板里到处写内联类型断言
function asTreeNode(data: unknown): PermissionTreeNode<PermissionRow> {
  return data as PermissionTreeNode<PermissionRow>
}

// ---- 新增/编辑弹窗（表单字段与原实现一致，不改动） ----

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<PermissionFormRequest>({
  name: '',
  code: '',
  showOrder: 0,
  remark: '',
})

const rules: FormRules<PermissionFormRequest> = {
  name: [{ required: true, message: '请输入权限名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入权限编码', trigger: 'blur' }],
}

const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增权限' : '编辑权限'))

function openCreateDialog() {
  dialogMode.value = 'create'
  editingId.value = null
  form.name = ''
  form.code = ''
  form.showOrder = 0
  form.remark = ''
  dialogVisible.value = true
}

async function openEditDialog(row: PermissionRow) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  const detail = await permissionApi.getPermissionById(row.id)
  form.name = detail.name
  form.code = detail.code
  form.showOrder = detail.showOrder
  form.remark = detail.remark
  dialogVisible.value = true
}

function closeDialog() {
  dialogVisible.value = false
  formRef.value?.clearValidate()
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload: PermissionFormRequest = {
      name: form.name,
      code: form.code,
      showOrder: form.showOrder,
      remark: form.remark,
    }
    if (dialogMode.value === 'create') {
      await permissionApi.createPermission(payload)
      ElMessage.success('新增成功')
    } else {
      await permissionApi.updatePermission(editingId.value as number, payload)
      ElMessage.success('保存成功')
    }
    dialogVisible.value = false
    await permissionStore.refreshAfterMutation()
  } finally {
    submitting.value = false
  }
}

// ---- 只读详情：跳转独立详情页 ----

function goToDetail(row: PermissionRow) {
  router.push({ name: 'permission-points-detail', params: { id: row.id } })
}

// ---- 行操作：启用/停用、删除 ----

async function toggleStatus(row: PermissionRow) {
  if (row.status === PERMISSION_STATUS_ENABLED) {
    await permissionApi.disablePermission(row.id)
    ElMessage.success('已停用')
  } else {
    await permissionApi.enablePermission(row.id)
    ElMessage.success('已启用')
  }
  await permissionStore.refreshAfterMutation()
}

async function handleDelete(row: PermissionRow) {
  await ElMessageBox.confirm(`确定要删除权限「${row.name}」吗？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await permissionApi.deletePermission(row.id)
  ElMessage.success('删除成功')
  await permissionStore.refreshAfterMutation()
}
</script>

<template>
  <div class="permission-management">
    <section class="permission-panel">
      <header class="permission-panel__header">
        <h2 class="permission-panel__title">权限管理</h2>
        <div class="permission-panel__actions">
          <el-button @click="expandAll">全部展开</el-button>
          <el-button @click="collapseAll">全部收起</el-button>
          <el-button type="primary" @click="openCreateDialog">新增</el-button>
        </div>
      </header>

      <el-tree
        ref="treeRef"
        v-loading="permissionStore.listLoading"
        class="permission-tree"
        :data="permissionTreeData"
        :props="treeProps"
        node-key="id"
        :default-expanded-keys="expandedKeys"
        :expand-on-click-node="false"
        empty-text="暂无权限点"
        @node-expand="handleNodeExpand"
        @node-collapse="handleNodeCollapse"
      >
        <template #default="{ node, data }">
          <div v-if="asTreeNode(data).raw" class="permission-tree__leaf">
            <span class="permission-tree__leaf-name">{{ asTreeNode(data).raw!.name }}</span>
            <span class="permission-tree__leaf-code">{{ asTreeNode(data).raw!.code }}</span>
            <el-tag v-if="asTreeNode(data).raw!.status === PERMISSION_STATUS_ENABLED" type="success" size="small">
              启用
            </el-tag>
            <el-tag v-else type="warning" size="small">停用</el-tag>
            <span class="permission-tree__leaf-actions">
              <el-button link type="primary" @click.stop="goToDetail(asTreeNode(data).raw!)">详情</el-button>
              <el-button link type="primary" @click.stop="openEditDialog(asTreeNode(data).raw!)">编辑</el-button>
              <el-button
                link
                :type="asTreeNode(data).raw!.status === PERMISSION_STATUS_ENABLED ? 'warning' : 'success'"
                @click.stop="toggleStatus(asTreeNode(data).raw!)"
              >
                {{ asTreeNode(data).raw!.status === PERMISSION_STATUS_ENABLED ? '停用' : '启用' }}
              </el-button>
              <el-button link type="danger" @click.stop="handleDelete(asTreeNode(data).raw!)">删除</el-button>
            </span>
          </div>
          <div v-else class="permission-tree__group">
            <span class="permission-tree__group-name">{{ node.label }}</span>
            <el-tag size="small" round>{{ asTreeNode(data).count ?? 0 }}</el-tag>
          </div>
        </template>
      </el-tree>
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" @close="closeDialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="权限名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入权限名称" />
        </el-form-item>
        <el-form-item label="权限编码" prop="code">
          <el-input v-model="form.code" placeholder="请输入权限编码" />
        </el-form-item>
        <el-form-item label="显示序号" prop="showOrder">
          <el-input-number v-model="form.showOrder" :min="0" style="width: 100%" />
          <div class="permission-form-hint">数值越大，排序越靠前</div>
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
  </div>
</template>

<style scoped lang="scss">
.permission-panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

.permission-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.permission-panel__title {
  font-size: 15px;
  color: var(--color-ink);
  margin: 0;
}

.permission-panel__actions {
  display: flex;
  gap: 8px;
}

.permission-form-hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-top: 4px;
}

.permission-tree__group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.permission-tree__group-name {
  color: var(--color-ink);
  font-weight: 500;
}

.permission-tree__leaf {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.permission-tree__leaf-name {
  color: var(--color-ink);
}

.permission-tree__leaf-code {
  color: var(--color-text-tertiary);
  font-size: 12px;
}

.permission-tree__leaf-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
}

.permission-tree__leaf-actions :deep(.el-button + .el-button) {
  margin-left: 6px;
}

// 用一条虚线 + 圆点把父子层级“连”起来，呼应侧边栏子菜单、菜单管理树的链式视觉语言
.permission-tree :deep(.el-tree-node__children) {
  position: relative;
  margin-left: 4px;
  padding-left: 6px;
  border-left: 1px dashed var(--chain-line-color);
}

.permission-tree :deep(.el-tree-node__children > .el-tree-node) {
  position: relative;
}

.permission-tree :deep(.el-tree-node__children > .el-tree-node::before) {
  content: '';
  position: absolute;
  left: -6px;
  top: 14px;
  width: var(--chain-dot-size-sm);
  height: var(--chain-dot-size-sm);
  border-radius: 50%;
  background: var(--chain-line-color);
}

.permission-tree :deep(.el-tree-node.is-current > .el-tree-node__children::before),
.permission-tree :deep(.el-tree-node.is-current::before) {
  background: var(--chain-line-color-active);
}

.permission-tree :deep(.el-tree-node__content) {
  height: auto;
  min-height: 36px;
  padding: 4px 0;
}
</style>
