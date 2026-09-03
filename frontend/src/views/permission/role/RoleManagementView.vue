<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, TreeInstance } from 'element-plus'
import { Delete, Plus } from '@element-plus/icons-vue'
import { useRoleStore } from '@/stores/role'
import { PAGE_SIZE_OPTIONS, DEFAULT_PAGE_SIZE } from '@/constants/pagination'
import * as roleApi from '@/api/role'
import * as permissionApi from '@/api/permission'
import * as orgApi from '@/api/org'
import * as metadataFieldApi from '@/api/metadataField'
import {
  ROLE_STATUS_ENABLED,
  USER_ROLE_ATTR_OPERATOR_OPTIONS,
  type RoleFormRequest,
  type RoleRow,
  type UserRoleConditions,
  type UserRoleMatchedUserRow,
  type UserRoleOrgScopeFormItem,
  type UserRoleRuleRow,
  type UserRoleUserAttrFormItem,
} from '@/types/role'
import type { PermissionOption } from '@/types/permission'
import type { OrgTreeNode } from '@/types/org'
import { METADATA_FIELD_STATUS_ENABLED, type MetadataField } from '@/types/metadataField'
import { buildPermissionTree, resolvePermissionModuleLabel, type PermissionTreeNode } from '@/utils/permissionTree'
import { usePermission } from '@/composables/usePermission'

const roleStore = useRoleStore()
const router = useRouter()
const { hasPermission } = usePermission()

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

// ---- 批量规则入口（角色列表操作列）----
// 参见 openspec/changes/add-user-role-batch-assignment/design.md Decision 4（二次设计）：
// 批量添加用户角色改为"持久规则"，保存后组织/用户/任职变化时后端自动重新计算，不再是
// 一次性执行即止的操作。交互分两层：规则列表（第一层）+ 新增/编辑规则表单（第二层）。

// 弹窗内选择器数据源（组织树/用户属性字段，按需在打开规则表单时加载一次，不重复请求）
const ruleOrgTree = ref<OrgTreeNode[]>([])
const ruleMetadataFieldOptions = ref<MetadataField[]>([])
const ruleSelectorsLoaded = ref(false)

async function ensureRuleSelectorsLoaded() {
  if (ruleSelectorsLoaded.value) return
  // 用户属性条件覆盖 USER（用户主表）与 POSITION（任职记录）两类元数据字段，现有
  // getMetadataFieldPageForSyncDomain 接口一次只查一个 bizType，这里分别查两次再合并，
  // 是最小改动的做法（见任务说明：不确定是否支持一次查多个 bizType，就分别查两次再合并）
  const [orgTreeData, userFieldPage, positionFieldPage] = await Promise.all([
    orgApi.getOrgTree(),
    metadataFieldApi.getMetadataFieldPageForSyncDomain('USER'),
    metadataFieldApi.getMetadataFieldPageForSyncDomain('POSITION'),
  ])
  ruleOrgTree.value = orgTreeData
  ruleMetadataFieldOptions.value = [...userFieldPage.records, ...positionFieldPage.records].filter(
    (field) => field.status === METADATA_FIELD_STATUS_ENABLED,
  )
  ruleSelectorsLoaded.value = true
}

// 属性字段下拉展示名带域前缀（"用户-性别"/"任职-任职类型"），方便操作人分辨两个域
function metadataFieldDomainLabel(field: MetadataField): string {
  const domain = field.bizType === 'USER' ? '用户' : '任职'
  return `${domain}-${field.fieldName}（${field.fieldCode}）`
}

// ---- 第一层：规则列表弹窗 ----

const ruleListDialogVisible = ref(false)
const ruleListRole = ref<RoleRow | null>(null)
const ruleListLoading = ref(false)
const ruleList = ref<UserRoleRuleRow[]>([])

async function fetchRuleList() {
  if (!ruleListRole.value) return
  ruleListLoading.value = true
  try {
    ruleList.value = await roleApi.listUserRoleRules(ruleListRole.value.id)
  } finally {
    ruleListLoading.value = false
  }
}

async function openRuleListDialog(row: RoleRow) {
  ruleListRole.value = row
  ruleListDialogVisible.value = true
  await fetchRuleList()
}

function closeRuleListDialog() {
  ruleListDialogVisible.value = false
}

async function handleDeleteRule(rule: UserRoleRuleRow) {
  await ElMessageBox.confirm('删除后将收回该规则已产生的角色关联，确定删除吗？', '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await roleApi.deleteUserRoleRule(rule.id)
  ElMessage.success('删除成功')
  await fetchRuleList()
}

// ---- 第二层：新增/编辑规则表单（嵌套弹窗，在规则列表弹窗之上打开）----

const ruleFormDialogVisible = ref(false)
const ruleFormMode = ref<'create' | 'edit'>('create')
const ruleFormEditingId = ref<number | null>(null)
const ruleFormName = ref('')
const ruleFormRemark = ref('')
const ruleFormOrgScopes = ref<UserRoleOrgScopeFormItem[]>([])
const ruleFormUserAttrs = ref<UserRoleUserAttrFormItem[]>([])
const ruleFormPreviewing = ref(false)
const ruleFormSubmitting = ref(false)
const ruleFormPreviewed = ref(false)
const ruleFormPreviewList = ref<UserRoleMatchedUserRow[]>([])
const ruleFormPreviewTotal = ref(0)
const ruleFormPreviewPage = ref(1)
const ruleFormPreviewPageSize = ref(DEFAULT_PAGE_SIZE)

const ruleFormTitle = computed(() => (ruleFormMode.value === 'create' ? '新增规则' : '编辑规则'))

// 组织范围、用户属性条件均未配置时，"预览"与"保存"按钮均禁用，防止误操作全库打标签
const hasAnyRuleCondition = computed(() => ruleFormOrgScopes.value.length > 0 || ruleFormUserAttrs.value.length > 0)

function resetRulePreview() {
  ruleFormPreviewed.value = false
  ruleFormPreviewList.value = []
  ruleFormPreviewTotal.value = 0
  ruleFormPreviewPage.value = 1
}

async function openRuleCreateForm() {
  ruleFormMode.value = 'create'
  ruleFormEditingId.value = null
  ruleFormName.value = ''
  ruleFormRemark.value = ''
  ruleFormOrgScopes.value = []
  ruleFormUserAttrs.value = []
  resetRulePreview()
  await ensureRuleSelectorsLoaded()
  ruleFormDialogVisible.value = true
}

// 用户属性回显：EQ/NE 时把唯一值填入 singleValue，IN 时把整组值填入 multiValues，
// 与 app-access-authorization 策略规则表单的回显写法一致
function toUserAttrFormItem(attr: { metadataFieldId: number; operator: 'EQ' | 'NE' | 'IN'; values: string[] }) {
  return {
    metadataFieldId: attr.metadataFieldId,
    operator: attr.operator,
    singleValue: attr.operator === 'IN' ? '' : (attr.values[0] ?? ''),
    multiValues: attr.operator === 'IN' ? [...attr.values] : [],
  }
}

// 规则列表行不带 orgScopes/userAttrs（保持轻量，与 AdminRow/GET /admins 同一套"列表不带
// 子集合、详情才带，避免 N+1"约定），编辑弹窗需要单独请求规则详情拿到完整条件再回填
async function openRuleEditForm(rule: UserRoleRuleRow) {
  ruleFormMode.value = 'edit'
  ruleFormEditingId.value = rule.id
  const detail = await roleApi.getUserRoleRuleById(rule.id)
  await ensureRuleSelectorsLoaded()
  ruleFormName.value = detail.name
  ruleFormRemark.value = detail.remark
  ruleFormOrgScopes.value = detail.orgScopes.map((scope) => ({
    orgId: scope.orgId,
    includeChildren: scope.includeChildren,
  }))
  ruleFormUserAttrs.value = detail.userAttrs.map(toUserAttrFormItem)
  resetRulePreview()
  ruleFormDialogVisible.value = true
}

function closeRuleForm() {
  ruleFormDialogVisible.value = false
}

function addRuleOrgScopeRow() {
  ruleFormOrgScopes.value.push({ orgId: null, includeChildren: false })
}

function removeRuleOrgScopeRow(index: number) {
  ruleFormOrgScopes.value.splice(index, 1)
}

function addRuleUserAttrRow() {
  ruleFormUserAttrs.value.push({ metadataFieldId: null, operator: 'EQ', singleValue: '', multiValues: [] })
}

function removeRuleUserAttrRow(index: number) {
  ruleFormUserAttrs.value.splice(index, 1)
}

// 运算符切换为"属于多值"时，若尚无任何值行则补一个空行，方便用户直接输入
function handleRuleOperatorChange(attr: UserRoleUserAttrFormItem) {
  if (attr.operator === 'IN' && attr.multiValues.length === 0) {
    attr.multiValues.push('')
  }
}

function addRuleMultiValue(attr: UserRoleUserAttrFormItem) {
  attr.multiValues.push('')
}

function removeRuleMultiValue(attr: UserRoleUserAttrFormItem, index: number) {
  attr.multiValues.splice(index, 1)
}

// 条件校验：两类条件均可选，但已填写的每一类内部行数据要合法，且两者不能同时为空
function validateRuleConditions(): string {
  if (ruleFormOrgScopes.value.some((scope) => scope.orgId === null)) {
    return '存在未选择组织的组织范围行，请补全或删除'
  }

  if (ruleFormUserAttrs.value.some((attr) => attr.metadataFieldId === null)) {
    return '存在未选择字段的用户属性行，请补全或删除'
  }
  for (const attr of ruleFormUserAttrs.value) {
    if (attr.operator === 'IN') {
      if (attr.multiValues.filter((v) => v.trim()).length === 0) {
        return '用户属性"属于多值"运算符至少需要一个比较值'
      }
    } else if (!attr.singleValue.trim()) {
      return '用户属性的比较值不能为空'
    }
  }

  if (!hasAnyRuleCondition.value) {
    return '组织范围、用户属性条件不能同时为空，请至少配置一类'
  }
  return ''
}

function buildRuleConditions(): UserRoleConditions {
  return {
    orgScopes: ruleFormOrgScopes.value.map((scope) => ({
      orgId: scope.orgId as number,
      includeChildren: scope.includeChildren,
    })),
    userAttrs: ruleFormUserAttrs.value.map((attr) => ({
      metadataFieldId: attr.metadataFieldId as number,
      operator: attr.operator,
      values: attr.operator === 'IN' ? attr.multiValues.filter((v) => v.trim()) : [attr.singleValue.trim()],
    })),
  }
}

async function handleRulePreview(targetPage = 1) {
  const error = validateRuleConditions()
  if (error) {
    ElMessage.error(error)
    return
  }
  ruleFormPreviewing.value = true
  try {
    const result = await roleApi.previewUserRoleRule(buildRuleConditions(), targetPage, ruleFormPreviewPageSize.value)
    ruleFormPreviewList.value = result.records
    ruleFormPreviewTotal.value = result.total
    ruleFormPreviewPage.value = result.page
    ruleFormPreviewPageSize.value = result.pageSize
    ruleFormPreviewed.value = true
  } finally {
    ruleFormPreviewing.value = false
  }
}

async function handleRuleSave() {
  if (!ruleFormName.value.trim()) {
    ElMessage.error('请输入规则名称')
    return
  }
  const error = validateRuleConditions()
  if (error) {
    ElMessage.error(error)
    return
  }
  ruleFormSubmitting.value = true
  try {
    const name = ruleFormName.value.trim()
    const remark = ruleFormRemark.value.trim()
    const conditions = buildRuleConditions()
    const result =
      ruleFormMode.value === 'create'
        ? await roleApi.createUserRoleRule((ruleListRole.value as RoleRow).id, name, remark, conditions)
        : await roleApi.updateUserRoleRule(ruleFormEditingId.value as number, name, remark, conditions)
    ElMessage.success(`规则已保存，当前命中 ${result.matchedUserCount} 名用户`)
    closeRuleForm()
    await fetchRuleList()
  } finally {
    ruleFormSubmitting.value = false
  }
}
</script>

<template>
  <div class="role-management">
    <section class="role-panel">
      <header class="role-panel__header">
        <h2 class="role-panel__title">角色管理</h2>
        <el-button v-if="hasPermission('RoleManagement:role:add')" type="primary" @click="openCreateDialog">新增</el-button>
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
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button v-if="hasPermission('RoleManagement:role:detail')" link type="primary" @click="goToDetail(row as RoleRow)">详情</el-button>
            <el-button v-if="hasPermission('RoleManagement:role:edit')" link type="primary" @click="openEditDialog(row as RoleRow)">编辑</el-button>
            <el-button
              link
              :type="(row as RoleRow).status === ROLE_STATUS_ENABLED ? 'warning' : 'success'"
              v-if="(row as RoleRow).status === ROLE_STATUS_ENABLED ? hasPermission('RoleManagement:role:disable') : hasPermission('RoleManagement:role:enable')"
              @click="toggleStatus(row as RoleRow)"
            >
              {{ (row as RoleRow).status === ROLE_STATUS_ENABLED ? '停用' : '启用' }}
            </el-button>
            <el-button
              v-if="hasPermission('RoleManagement:role:batchAssignUser')"
              link
              type="primary"
              @click="openRuleListDialog(row as RoleRow)"
            >
              批量规则
            </el-button>
            <el-button v-if="hasPermission('RoleManagement:role:delete')" link type="danger" @click="handleDelete(row as RoleRow)">删除</el-button>
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
            empty-text="暂无可分配的权限点"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="ruleListDialogVisible"
      title="批量规则"
      width="760px"
      @close="closeRuleListDialog"
    >
      <div class="rule-list-header">
        <span class="rule-list-header__hint">
          目标角色：<strong>{{ ruleListRole?.name }}</strong>，规则保存后组织/用户/任职信息变化时会自动重新计算命中结果。
        </span>
        <el-button type="primary" @click="openRuleCreateForm">新增规则</el-button>
      </div>

      <el-table v-loading="ruleListLoading" :data="ruleList" empty-text="暂无规则">
        <el-table-column prop="name" label="规则名称" min-width="140" />
        <el-table-column prop="remark" label="备注" min-width="160" />
        <el-table-column label="最近执行时间" min-width="160">
          <template #default="{ row }">
            {{ (row as UserRoleRuleRow).lastExecTime ?? '未执行' }}
          </template>
        </el-table-column>
        <el-table-column prop="matchedUserCount" label="当前命中人数" width="120" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openRuleEditForm(row as UserRoleRuleRow)">编辑</el-button>
            <el-button link type="danger" @click="handleDeleteRule(row as UserRoleRuleRow)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="closeRuleListDialog">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="ruleFormDialogVisible"
      :title="ruleFormTitle"
      width="820px"
      append-to-body
      @close="closeRuleForm"
    >
      <el-form label-width="90px">
        <el-form-item label="规则名称" required>
          <el-input v-model="ruleFormName" placeholder="用于区分同一角色下的多条规则" maxlength="128" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="ruleFormRemark" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
      </el-form>

      <div class="batch-assign-condition-block">
        <div class="batch-assign-condition-block__header">
          <span class="batch-assign-condition-block__title">组织范围条件</span>
          <el-button link type="primary" :icon="Plus" @click="addRuleOrgScopeRow">添加组织</el-button>
        </div>
        <p v-if="ruleFormOrgScopes.length === 0" class="batch-assign-condition-empty">未配置组织范围条件</p>
        <div v-else class="batch-assign-condition-list">
          <div v-for="(scope, index) in ruleFormOrgScopes" :key="index" class="batch-assign-condition-row">
            <el-tree-select
              v-model="scope.orgId"
              :data="ruleOrgTree"
              :props="{ label: 'name', children: 'children' }"
              node-key="id"
              check-strictly
              placeholder="请选择组织"
              style="flex: 1"
            />
            <el-checkbox v-model="scope.includeChildren">含子组织</el-checkbox>
            <el-button link :icon="Delete" type="danger" @click="removeRuleOrgScopeRow(index)" />
          </div>
        </div>
      </div>

      <div class="batch-assign-condition-block">
        <div class="batch-assign-condition-block__header">
          <span class="batch-assign-condition-block__title">用户属性条件</span>
          <el-button link type="primary" :icon="Plus" @click="addRuleUserAttrRow">添加条件</el-button>
        </div>
        <p v-if="ruleFormUserAttrs.length === 0" class="batch-assign-condition-empty">未配置用户属性条件</p>
        <div v-else class="batch-assign-condition-list">
          <div
            v-for="(attr, index) in ruleFormUserAttrs"
            :key="index"
            class="batch-assign-condition-row batch-assign-condition-row--attr"
          >
            <el-select v-model="attr.metadataFieldId" placeholder="选择字段" style="width: 220px">
              <el-option
                v-for="field in ruleMetadataFieldOptions"
                :key="field.id"
                :label="metadataFieldDomainLabel(field)"
                :value="field.id"
              />
            </el-select>
            <el-select v-model="attr.operator" style="width: 110px" @change="handleRuleOperatorChange(attr)">
              <el-option
                v-for="opt in USER_ROLE_ATTR_OPERATOR_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <div v-if="attr.operator === 'IN'" class="batch-assign-condition-values">
              <div v-for="(_, vi) in attr.multiValues" :key="vi" class="batch-assign-condition-value-row">
                <el-input v-model="attr.multiValues[vi]" placeholder="比较值" style="width: 110px" />
                <el-button link :icon="Delete" type="danger" @click="removeRuleMultiValue(attr, vi)" />
              </div>
              <el-button link type="primary" :icon="Plus" @click="addRuleMultiValue(attr)">添加值</el-button>
            </div>
            <el-input v-else v-model="attr.singleValue" placeholder="比较值" style="width: 140px" />
            <el-button link :icon="Delete" type="danger" @click="removeRuleUserAttrRow(index)" />
          </div>
        </div>
      </div>

      <div class="batch-assign-preview-actions">
        <el-button
          type="primary"
          :disabled="!hasAnyRuleCondition"
          :loading="ruleFormPreviewing"
          @click="handleRulePreview(1)"
        >
          预览
        </el-button>
        <span v-if="!hasAnyRuleCondition" class="batch-assign-condition-empty">请至少配置一类条件</span>
      </div>

      <div v-if="ruleFormPreviewed" class="batch-assign-preview-result">
        <p class="batch-assign-preview-summary">共命中 <strong>{{ ruleFormPreviewTotal }}</strong> 名用户</p>
        <el-table :data="ruleFormPreviewList" size="small" empty-text="未命中任何用户" max-height="260">
          <el-table-column prop="userName" label="姓名" min-width="100" />
          <el-table-column prop="userCode" label="编号" min-width="100" />
          <el-table-column prop="orgName" label="所属组织" min-width="140" />
        </el-table>
        <el-pagination
          class="batch-assign-preview-pagination"
          background
          small
          layout="prev, pager, next, total"
          :current-page="ruleFormPreviewPage"
          :page-size="ruleFormPreviewPageSize"
          :total="ruleFormPreviewTotal"
          @current-change="handleRulePreview"
        />
      </div>

      <template #footer>
        <el-button @click="ruleFormDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!hasAnyRuleCondition" :loading="ruleFormSubmitting" @click="handleRuleSave">
          保存
        </el-button>
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

// ---- 批量规则弹窗（规则列表 + 新增/编辑规则表单）----

.rule-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.rule-list-header__hint {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.batch-assign-condition-block {
  margin-bottom: 18px;
}

.batch-assign-condition-block__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.batch-assign-condition-block__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-ink);
}

.batch-assign-condition-empty {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-tertiary);
}

// 条件行用一条虚线 + 圆点串起来，呼应管辖组织范围子表单的"链式连接"视觉语言
.batch-assign-condition-list {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-left: 16px;
  border-left: 1px dashed var(--chain-line-color);
}

.batch-assign-condition-row {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
}

.batch-assign-condition-row--attr {
  align-items: flex-start;
}

.batch-assign-condition-row::before {
  content: '';
  position: absolute;
  left: -20px;
  top: 10px;
  width: var(--chain-dot-size-sm);
  height: var(--chain-dot-size-sm);
  border-radius: 50%;
  background: var(--chain-line-color-active);
}

.batch-assign-condition-values {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.batch-assign-condition-value-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.batch-assign-preview-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 16px 0;
  padding-top: 12px;
  border-top: 1px dashed var(--color-border);
}

.batch-assign-preview-result {
  margin-top: 8px;
}

.batch-assign-preview-summary {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--color-ink);
}

.batch-assign-preview-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
