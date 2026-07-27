<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, TreeInstance } from 'element-plus'
import { useRoleStore } from '@/stores/role'
import { PAGE_SIZE_OPTIONS } from '@/constants/pagination'
import * as roleApi from '@/api/role'
import * as permissionApi from '@/api/permission'
import { ROLE_STATUS_ENABLED, type RoleFormRequest, type RoleRow } from '@/types/role'
import type { PermissionOption } from '@/types/permission'
import { buildPermissionTree, resolvePermissionModuleLabel, type PermissionTreeNode } from '@/utils/permissionTree'

const roleStore = useRoleStore()
const router = useRouter()

onMounted(() => {
  roleStore.fetchPage()
})

function handlePageChange(targetPage: number) {
  roleStore.changePage(targetPage)
}

// ---- 弹窗内"权限点分配"勾选控件数据源（按需加载：只在打开新增/编辑弹窗时请求一次，
// 页面进入、翻页、搜索角色列表都不触发） ----

const permissionOptions = ref<PermissionOption[]>([])
const permissionTreeRef = ref<TreeInstance>()

// 按权限点编码冒号分隔的第一段（模块）分组构造两层树：第一层是分组虚拟节点（id 形如
// "group:模块名"，不对应任何真实权限点，仅用于分组展示、全选/半选联动），第二层是
// 具体权限点叶子节点（id 为权限点真实数字 id，label 用中文名称）。分组算法本身抽取为
// 共享工具 buildPermissionTree（src/utils/permissionTree.ts），供权限点管理树复用；分组
// 节点的中文模块名解析同样复用共享的 resolvePermissionModuleLabel，与权限点管理页面
// 保持同一份映射，不重复维护
const permissionTreeData = computed<PermissionTreeNode<PermissionOption>[]>(() =>
  buildPermissionTree(permissionOptions.value, resolvePermissionModuleLabel),
)

async function fetchPermissionOptions() {
  permissionOptions.value = await permissionApi.getPermissionOptions()
}

// ---- 新增/编辑弹窗 ----

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<RoleFormRequest>({
  name: '',
  code: '',
  showOrder: 0,
  remark: '',
  permissionIds: [],
})

const rules: FormRules<RoleFormRequest> = {
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
}

const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增角色' : '编辑角色'))

async function openCreateDialog() {
  dialogMode.value = 'create'
  editingId.value = null
  form.name = ''
  form.code = ''
  form.showOrder = 0
  form.remark = ''
  form.permissionIds = []
  await fetchPermissionOptions()
  dialogVisible.value = true
  await nextTick()
  permissionTreeRef.value?.setCheckedKeys([])
}

async function openEditDialog(row: RoleRow) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  const detail = await roleApi.getRoleById(row.id)
  await fetchPermissionOptions()
  form.name = detail.name
  form.code = detail.code
  form.showOrder = detail.showOrder
  form.remark = detail.remark
  form.permissionIds = (detail.permissions ?? []).map((permission) => permission.id)
  dialogVisible.value = true
  await nextTick()
  permissionTreeRef.value?.setCheckedKeys(form.permissionIds)
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
    // el-tree 的分组虚拟节点 id 是字符串（"group:模块名"），只有叶子节点（真实权限点）
    // 的 id 是数字，getCheckedKeys() 默认不返回半选中的分组节点，这里再按类型过滤一层
    // 保险，确保绝不会把分组虚拟节点当作权限点 id 提交给后端
    const checkedKeys = permissionTreeRef.value?.getCheckedKeys() ?? []
    const permissionIds = checkedKeys.filter((key): key is number => typeof key === 'number')
    const payload: RoleFormRequest = {
      name: form.name,
      code: form.code,
      showOrder: form.showOrder,
      remark: form.remark,
      permissionIds,
    }
    if (dialogMode.value === 'create') {
      await roleApi.createRole(payload)
      ElMessage.success('新增成功')
    } else {
      await roleApi.updateRole(editingId.value as number, payload)
      ElMessage.success('保存成功')
    }
    dialogVisible.value = false
    await roleStore.refreshAfterMutation()
  } finally {
    submitting.value = false
  }
}

// ---- 只读详情：跳转独立详情页 ----

function goToDetail(row: RoleRow) {
  router.push({ name: 'permission-roles-detail', params: { id: row.id } })
}

// ---- 行操作：启用/停用、删除 ----

async function toggleStatus(row: RoleRow) {
  if (row.status === ROLE_STATUS_ENABLED) {
    await roleApi.disableRole(row.id)
    ElMessage.success('已停用')
  } else {
    await roleApi.enableRole(row.id)
    ElMessage.success('已启用')
  }
  await roleStore.refreshAfterMutation()
}

async function handleDelete(row: RoleRow) {
  await ElMessageBox.confirm(`确定要删除角色「${row.name}」吗？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await roleApi.deleteRole(row.id)
  ElMessage.success('删除成功')
  await roleStore.refreshAfterMutation()
}
</script>

<template>
  <div class="role-management">
    <section class="role-panel">
      <header class="role-panel__header">
        <h2 class="role-panel__title">角色管理</h2>
        <el-button type="primary" @click="openCreateDialog">新增</el-button>
      </header>

      <el-table v-loading="roleStore.listLoading" :data="roleStore.list" empty-text="暂无角色">
        <el-table-column prop="name" label="角色名称" min-width="140" />
        <el-table-column prop="code" label="角色编码" min-width="120" />
        <el-table-column prop="remark" label="备注" min-width="160" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="(row as RoleRow).status === ROLE_STATUS_ENABLED" type="success">启用</el-tag>
            <el-tag v-else type="warning">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="showOrder" label="显示序号" width="90" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="goToDetail(row as RoleRow)">详情</el-button>
            <el-button link type="primary" @click="openEditDialog(row as RoleRow)">编辑</el-button>
            <el-button
              link
              :type="(row as RoleRow).status === ROLE_STATUS_ENABLED ? 'warning' : 'success'"
              @click="toggleStatus(row as RoleRow)"
            >
              {{ (row as RoleRow).status === ROLE_STATUS_ENABLED ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" @click="handleDelete(row as RoleRow)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="role-pagination"
        background
        layout="sizes, prev, pager, next, total"
        :page-sizes="[...PAGE_SIZE_OPTIONS]"
        :current-page="roleStore.page"
        :page-size="roleStore.pageSize"
        :total="roleStore.total"
        @current-change="handlePageChange"
        @size-change="roleStore.changePageSize"
      />
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="640px" @close="closeDialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="角色编码" prop="code">
          <el-input v-model="form.code" placeholder="请输入角色编码" />
        </el-form-item>
        <el-form-item label="显示序号" prop="showOrder">
          <el-input-number v-model="form.showOrder" :min="0" style="width: 100%" />
          <div class="role-form-hint">数值越大，排序越靠前</div>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="选填" />
        </el-form-item>
        <el-form-item label="权限点">
          <el-tree
            ref="permissionTreeRef"
            class="role-permission-tree"
            :data="permissionTreeData"
            show-checkbox
            node-key="id"
            :props="{ label: 'label', children: 'children' }"
            default-expand-all
            empty-text="暂无可分配的权限点"
          />
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
.role-panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

.role-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.role-panel__title {
  font-size: 15px;
  color: var(--color-ink);
  margin: 0;
}

.role-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.role-form-hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-top: 4px;
}

// 权限点数量可达上百条，树形勾选控件限定高度并允许内部滚动，避免撑爆弹窗
.role-permission-tree {
  width: 100%;
  max-height: 280px;
  overflow-y: auto;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 8px 4px;
}

// 操作列（详情/编辑/启用停用/删除）相邻按钮间距收紧，比 Element Plus 默认更紧凑
:deep(.el-table .el-button + .el-button) {
  margin-left: 6px;
}
</style>
