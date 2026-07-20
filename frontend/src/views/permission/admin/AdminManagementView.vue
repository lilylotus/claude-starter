<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAdminStore } from '@/stores/admin'
import OperationHistoryPanel from '@/components/OperationHistoryPanel.vue'
import * as adminApi from '@/api/admin'
import * as orgApi from '@/api/org'
import * as userApi from '@/api/user'
import * as roleApi from '@/api/role'
import {
  ADMIN_STATUS_ENABLED,
  type AdminFormRequest,
  type AdminOrgScopeFormItem,
  type AdminRow,
} from '@/types/admin'
import type { OrgTreeNode } from '@/types/org'
import type { RoleOption } from '@/types/role'

const adminStore = useAdminStore()

onMounted(() => {
  adminStore.fetchPage()
  // 管理员角色多选下拉数据源在这里预加载：角色数量级与字典项/任职类型相当，
  // 不需要像组织树那样延迟到打开弹窗才加载。
  fetchRoleOptions()
  // 全量组织树（orgTree，供新增/编辑弹窗内“管辖组织范围”子表单选择器用）不在这里
  // 预加载：只有打开弹窗时才需要它，此处预加载会让绝大多数只浏览管理员列表的页面
  // 访问都白白发一次 GET /api/orgs/tree（见 openCreateDialog/openEditDialog）
})

function handlePageChange(targetPage: number) {
  adminStore.changePage(targetPage)
}

// ---- 弹窗内“管辖组织范围”子表单选择器数据源（一次性加载全量组织树，不需要防环处理） ----

const orgTree = ref<OrgTreeNode[]>([])

async function fetchOrgTree() {
  orgTree.value = await orgApi.getOrgTree()
}

// ---- 管理员角色多选下拉框数据源（数据源为角色选项接口，页面挂载时一次性加载） ----

const roleOptions = ref<RoleOption[]>([])

async function fetchRoleOptions() {
  roleOptions.value = await roleApi.getRoleOptions()
}

// ---- 关联用户远程搜索选择器（复用 GET /api/users?name=/?mobile= 分页接口） ----
// 输入内容形如手机号（11 位、1 开头）时按 mobile 搜索，否则按 name 搜索；
// name/mobile 后端是“与”关系，不能同时传，否则会搜不到人

const MOBILE_PATTERN = /^1\d{10}$/

interface UserOption {
  id: number
  name: string
  mobile: string
}

const userOptions = ref<UserOption[]>([])
const userSearchLoading = ref(false)

async function remoteSearchUsers(query: string) {
  if (!query) {
    userOptions.value = []
    return
  }
  userSearchLoading.value = true
  try {
    const params = MOBILE_PATTERN.test(query) ? { mobile: query, pageSize: 20 } : { name: query, pageSize: 20 }
    const result = await userApi.getUserPage(params)
    userOptions.value = result.records.map((user) => ({ id: user.id, name: user.name, mobile: user.mobile }))
  } finally {
    userSearchLoading.value = false
  }
}

// ---- 新增/编辑弹窗 ----

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

// userId 在校验通过前允许暂时为空（由用户手动选择），因此不直接复用
// AdminFormRequest（其字段类型是提交给后端时已确定非空的 number）
interface AdminForm {
  name: string
  code: string
  userId: number | null
  roleIds: number[]
  orgScopes: AdminOrgScopeFormItem[]
  showOrder: number
  remark: string
}

function blankOrgScope(): AdminOrgScopeFormItem {
  return {
    orgId: null,
    includeChildren: false,
  }
}

const form = reactive<AdminForm>({
  name: '',
  code: '',
  userId: null,
  roleIds: [],
  orgScopes: [],
  showOrder: 0,
  remark: '',
})

const rules: FormRules<AdminForm> = {
  name: [{ required: true, message: '请输入管理员名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入管理员编码', trigger: 'blur' }],
  userId: [{ required: true, message: '请选择关联用户', trigger: 'change' }],
}

// 管辖组织范围子表单每行的独立校验规则，通过动态 prop（orgScopes.{index}.orgId）挂载
const orgScopeOrgRule = [{ required: true, message: '请选择组织', trigger: 'change' }]

const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增管理员' : '编辑管理员'))

function addOrgScopeRow() {
  form.orgScopes.push(blankOrgScope())
}

function removeOrgScopeRow(index: number) {
  form.orgScopes.splice(index, 1)
}

function resetForm() {
  form.name = ''
  form.code = ''
  form.userId = null
  form.roleIds = []
  form.orgScopes = []
  form.showOrder = 0
  form.remark = ''
}

async function openCreateDialog() {
  await fetchOrgTree()
  dialogMode.value = 'create'
  editingId.value = null
  resetForm()
  userOptions.value = []
  dialogVisible.value = true
}

async function openEditDialog(row: AdminRow) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  const detail = await adminApi.getAdminById(row.id)
  await fetchOrgTree()
  form.name = detail.name
  form.code = detail.code
  form.userId = detail.userId
  form.roleIds = detail.roles.map((role) => role.roleId)
  form.orgScopes = detail.orgScopes.map((scope) => ({
    orgId: scope.orgId,
    includeChildren: scope.includeChildren,
  }))
  form.showOrder = detail.showOrder
  form.remark = detail.remark
  // 回显关联用户选项，避免刚打开编辑弹窗时下拉框因搜索结果为空而显示不出已选中的用户姓名
  userOptions.value = [{ id: detail.userId, name: detail.userName, mobile: '' }]
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
    // 上面的表单校验已确保 userId 必填、每一行管辖组织范围的 orgId 必填，此处已知非空
    const payload: AdminFormRequest = {
      name: form.name,
      code: form.code,
      userId: form.userId as number,
      roleIds: form.roleIds,
      orgScopes: form.orgScopes.map((scope) => ({
        orgId: scope.orgId as number,
        includeChildren: scope.includeChildren,
      })),
      showOrder: form.showOrder,
      remark: form.remark,
    }
    if (dialogMode.value === 'create') {
      await adminApi.createAdmin(payload)
      ElMessage.success('新增成功')
    } else {
      await adminApi.updateAdmin(editingId.value as number, payload)
      ElMessage.success('保存成功')
    }
    dialogVisible.value = false
    await adminStore.refreshAfterMutation()
  } finally {
    submitting.value = false
  }
}

// ---- 只读详情弹窗 ----

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref<AdminRow | null>(null)

async function openDetailDialog(row: AdminRow) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detailData.value = await adminApi.getAdminById(row.id)
  } finally {
    detailLoading.value = false
  }
}

// ---- 行操作：启用/停用、删除 ----

async function toggleStatus(row: AdminRow) {
  if (row.status === ADMIN_STATUS_ENABLED) {
    await adminApi.disableAdmin(row.id)
    ElMessage.success('已停用')
  } else {
    await adminApi.enableAdmin(row.id)
    ElMessage.success('已启用')
  }
  await adminStore.refreshAfterMutation()
}

async function handleDelete(row: AdminRow) {
  await ElMessageBox.confirm(`确定要删除管理员「${row.name}」吗？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await adminApi.deleteAdmin(row.id)
  ElMessage.success('删除成功')
  await adminStore.refreshAfterMutation()
}
</script>

<template>
  <div class="admin-management">
    <section class="admin-panel">
      <header class="admin-panel__header">
        <h2 class="admin-panel__title">管理员管理</h2>
        <el-button type="primary" @click="openCreateDialog">新增</el-button>
      </header>

      <el-table v-loading="adminStore.listLoading" :data="adminStore.list" empty-text="暂无管理员">
        <el-table-column prop="name" label="管理员名称" min-width="140" />
        <el-table-column prop="code" label="管理员编码" min-width="120" />
        <el-table-column prop="userName" label="关联用户" min-width="120" />
        <el-table-column prop="showOrder" label="显示序号" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="(row as AdminRow).status === ADMIN_STATUS_ENABLED" type="success">启用</el-tag>
            <el-tag v-else type="warning">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetailDialog(row as AdminRow)">详情</el-button>
            <el-button link type="primary" @click="openEditDialog(row as AdminRow)">编辑</el-button>
            <el-button
              link
              :type="(row as AdminRow).status === ADMIN_STATUS_ENABLED ? 'warning' : 'success'"
              @click="toggleStatus(row as AdminRow)"
            >
              {{ (row as AdminRow).status === ADMIN_STATUS_ENABLED ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" @click="handleDelete(row as AdminRow)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="admin-pagination"
        background
        layout="prev, pager, next, total"
        :current-page="adminStore.page"
        :page-size="adminStore.pageSize"
        :total="adminStore.total"
        @current-change="handlePageChange"
      />
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="680px" @close="closeDialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="管理员名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入管理员名称" />
        </el-form-item>
        <el-form-item label="管理员编码" prop="code">
          <el-input v-model="form.code" placeholder="请输入管理员编码" />
        </el-form-item>
        <el-form-item label="关联用户" prop="userId">
          <el-select
            v-model="form.userId"
            filterable
            remote
            reserve-keyword
            placeholder="输入姓名或手机号搜索用户"
            :remote-method="remoteSearchUsers"
            :loading="userSearchLoading"
            style="width: 100%"
          >
            <el-option
              v-for="opt in userOptions"
              :key="opt.id"
              :label="opt.mobile ? `${opt.name}（${opt.mobile}）` : opt.name"
              :value="opt.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="管理员角色" prop="roleIds">
          <el-select v-model="form.roleIds" multiple placeholder="请选择管理员角色" style="width: 100%">
            <el-option v-for="opt in roleOptions" :key="opt.id" :label="opt.name" :value="opt.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="显示序号" prop="showOrder">
          <el-input-number v-model="form.showOrder" :min="0" style="width: 100%" />
          <div class="admin-form-hint">数值越大，排序越靠前</div>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="选填" />
        </el-form-item>

        <div class="admin-org-scope-section">
          <div class="admin-org-scope-section__header">
            <span class="admin-org-scope-section__title">管辖组织范围</span>
            <el-button link type="primary" @click="addOrgScopeRow">+ 添加组织</el-button>
          </div>

          <p v-if="form.orgScopes.length === 0" class="admin-org-scope-empty">暂无管辖组织范围</p>

          <div v-else class="admin-org-scope-list">
            <div v-for="(scope, index) in form.orgScopes" :key="index" class="admin-org-scope-row">
              <div class="admin-org-scope-row__fields">
                <el-form-item
                  label="组织"
                  label-width="60px"
                  :prop="`orgScopes.${index}.orgId`"
                  :rules="orgScopeOrgRule"
                >
                  <el-tree-select
                    v-model="scope.orgId"
                    :data="orgTree"
                    :props="{ label: 'name', children: 'children' }"
                    node-key="id"
                    check-strictly
                    placeholder="请选择组织"
                    style="width: 100%"
                  />
                </el-form-item>
                <el-form-item label="" label-width="0">
                  <el-checkbox v-model="scope.includeChildren">含子组织</el-checkbox>
                </el-form-item>
              </div>
              <el-button link type="danger" class="admin-org-scope-row__remove" @click="removeOrgScopeRow(index)">
                删除
              </el-button>
            </div>
          </div>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="管理员详情" width="680px">
      <el-descriptions v-loading="detailLoading" :column="1" border>
        <el-descriptions-item label="管理员名称">{{ detailData?.name }}</el-descriptions-item>
        <el-descriptions-item label="管理员编码">{{ detailData?.code }}</el-descriptions-item>
        <el-descriptions-item label="关联用户">{{ detailData?.userName }}</el-descriptions-item>
        <el-descriptions-item label="管理员角色">
          <template v-if="detailData?.roles.length">
            <el-tag v-for="role in detailData.roles" :key="role.roleId" class="admin-detail-role-tag">
              {{ role.roleName }}
            </el-tag>
          </template>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="显示序号">{{ detailData?.showOrder }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detailData?.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag v-if="detailData?.status === ADMIN_STATUS_ENABLED" type="success">启用</el-tag>
          <el-tag v-else type="warning">停用</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detailData?.createBy }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detailData?.createTime }}</el-descriptions-item>
        <el-descriptions-item label="更新人">{{ detailData?.updateBy }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ detailData?.updateTime }}</el-descriptions-item>
      </el-descriptions>

      <div class="admin-detail-org-scopes">
        <h3 class="admin-detail-org-scopes__title">管辖组织范围</h3>
        <el-table :data="detailData?.orgScopes ?? []" empty-text="暂无管辖组织范围">
          <el-table-column prop="orgName" label="组织名称" min-width="160" />
          <el-table-column label="含子组织" width="100">
            <template #default="{ row }">{{ row.includeChildren ? '是' : '否' }}</template>
          </el-table-column>
        </el-table>
      </div>

      <OperationHistoryPanel resource-type="admin" :target-id="detailData?.id ?? null" />

      <template #footer>
        <el-button type="primary" @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.admin-panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

.admin-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.admin-panel__title {
  font-size: 15px;
  color: var(--color-ink);
  margin: 0;
}

.admin-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.admin-form-hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-top: 4px;
}

.admin-org-scope-section {
  margin-top: 8px;
}

.admin-org-scope-section__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.admin-org-scope-section__title {
  font-size: 14px;
  color: var(--color-ink);
  font-weight: 600;
}

.admin-org-scope-empty {
  font-size: 13px;
  color: var(--color-text-tertiary);
  margin: 0 0 8px;
}

// 管辖组织范围各行用一条虚线 + 圆点串起来，呼应组织树/用户任职子表单的"链式连接"视觉语言
.admin-org-scope-list {
  position: relative;
  padding-left: 16px;
  border-left: 1px dashed var(--chain-line-color);
}

.admin-org-scope-row {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding-bottom: 4px;
  margin-bottom: 8px;
  border-bottom: 1px dashed var(--color-border);
}

.admin-org-scope-row:last-child {
  border-bottom: none;
}

.admin-org-scope-row::before {
  content: '';
  position: absolute;
  left: -20px;
  top: 14px;
  width: var(--chain-dot-size-sm);
  height: var(--chain-dot-size-sm);
  border-radius: 50%;
  background: var(--chain-line-color-active);
}

.admin-org-scope-row__fields {
  flex: 1;
  min-width: 0;
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  column-gap: 12px;
}

.admin-org-scope-row__remove {
  margin-top: 6px;
  flex-shrink: 0;
}

.admin-detail-org-scopes {
  margin-top: 20px;
}

.admin-detail-org-scopes__title {
  font-size: 14px;
  color: var(--color-ink);
  margin: 0 0 8px;
}

.admin-detail-role-tag {
  margin-right: 6px;
}

// 操作列（详情/编辑/启用停用/删除）相邻按钮间距收紧，比 Element Plus 默认更紧凑
:deep(.el-table .el-button + .el-button) {
  margin-left: 6px;
}
</style>
