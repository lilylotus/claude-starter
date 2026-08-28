<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type Node from 'element-plus/es/components/tree/src/model/node'
import { usePositionStore } from '@/stores/position'
import { PAGE_SIZE_OPTIONS } from '@/constants/pagination'
import * as positionApi from '@/api/position'
import * as orgApi from '@/api/org'
import * as userApi from '@/api/user'
import * as excelImportApi from '@/api/excelImport'
import * as excelExportApi from '@/api/excelExport'
import BatchImportDialog from '@/components/BatchImportDialog.vue'
import { POSITION_STATUS_ENABLED, type PositionFormRequest, type PositionRow } from '@/types/position'
import type { OrgTreeNode } from '@/types/org'
import { useDynamicFormFields } from '@/composables/useDynamicFormFields'
import {
  FORM_FIELD_CONTROL_TYPE_DATE,
  FORM_FIELD_CONTROL_TYPE_DICT,
  FORM_FIELD_CONTROL_TYPE_MULTI_DICT,
  FORM_FIELD_CONTROL_TYPE_NUMBER,
  FORM_FIELD_CONTROL_TYPE_TEXT,
} from '@/types/formField'
import { usePermission } from '@/composables/usePermission'

const positionStore = usePositionStore()
const router = useRouter()
const { hasPermission } = usePermission()

// 除关联组织（orgId）、关联用户（userId）、启停用状态（status）外的全部字段
// （含原有表字段、认证类型 positionType 与 ext1~ext10）统一按“表单字段定义”
// （bizType=POSITION）动态渲染，见 design.md 决策 2
const positionFields = useDynamicFormFields('POSITION')

onMounted(() => {
  // 左侧导航树在 lazy 模式下挂载时会自动对根节点调用一次 load（parentId = 0），
  // 这里不需要主动拉取。
  // 全量组织树（orgTree，供新增/编辑弹窗内“所属组织”选择器用）不在这里预加载：
  // 只有打开弹窗时才需要它，此处预加载会让绝大多数只浏览任职列表的页面访问都白白
  // 发一次 GET /api/orgs/tree（见 openCreateDialog/openEditDialog）
  positionFields.fetchSchema()
})

// ---- 左侧组织树（懒加载，纯导航用途） ----

const treeProps = { label: 'name', children: 'children' }

// el-tree 的 load 回调：node.level === 0 时代表根这一层（虚拟根，非某个真实节点），
// 请求 parentId = 0；否则用当前节点数据的 id 作为 parentId
function loadNode(node: Node, resolve: (data: OrgTreeNode[]) => void) {
  const parentId = node.level === 0 ? 0 : (node.data as OrgTreeNode).id
  positionStore.loadNavTreeChildren(parentId).then(resolve)
}

// 当前选中组织的名称，仅用于拼接右侧面板标题；未选中时保持空串
const selectedOrgName = ref('')

function handleNodeClick(node: OrgTreeNode) {
  selectedOrgName.value = node.name
  positionStore.selectNode(node.id)
}

// 右侧面板标题：未选中任何左侧树节点时保持空白，与下方是否展示空状态提示是两回事
const rightPanelTitle = computed(() => (selectedOrgName.value ? `[${selectedOrgName.value}]任职人员` : ''))

function handlePageChange(targetPage: number) {
  positionStore.changePage(targetPage)
}

// ---- 弹窗内“所属组织”选择器数据源（一次性加载全量组织树，不需要防环处理） ----

const orgTree = ref<OrgTreeNode[]>([])

async function fetchOrgTree() {
  orgTree.value = await orgApi.getOrgTree()
}

// ---- Excel 批量导入：下载模板 / 上传批量导入 ----
// 任职导入模板含"人员编号""组织编码"两个固定标识列，见 design.md 决策 2

const batchImportVisible = ref(false)

async function handleDownloadTemplate() {
  await excelImportApi.downloadImportTemplate('POSITION')
}

// ---- Excel 导出：按当前登录用户管辖组织范围导出全量任职数据 ----

async function handleExportExcel() {
  await excelExportApi.exportExcel('POSITION')
}

async function handleImported() {
  await positionStore.refreshAfterMutation()
}

// ---- 所属用户远程搜索选择器（新增弹窗专用，复用 GET /api/users?name= 分页接口） ----

interface UserOption {
  id: number
  name: string
  code: string
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
    const result = await userApi.getUserPage({ name: query, pageSize: 20 })
    userOptions.value = result.records.map((user) => ({ id: user.id, name: user.name, code: user.code }))
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

// 表单里的 userId/orgId 在校验通过前允许暂时为空（由用户手动选择），
// userName 仅编辑模式下只读展示所属用户，不随表单一起提交给后端；其余字段
// （positionType/positionAddress/positionPhone/showOrder/remark/ext1~ext10）
// 由 positionFields 动态渲染驱动，key 为各自绑定的 columnName
interface PositionFormStatic {
  userId: number | null
  userName: string
  orgId: number | null
}

type PositionForm = PositionFormStatic & Record<string, unknown>

const form = reactive<PositionForm>({
  userId: null,
  userName: '',
  orgId: null,
})

// 保留 form 里指定 key（如 userId/userName/orgId），清空其余动态字段的 key，
// 供每次打开弹窗前重置
function resetDynamicKeys(target: Record<string, unknown>, keep: string[]) {
  Object.keys(target).forEach((key) => {
    if (!keep.includes(key)) delete target[key]
  })
}

const rules = computed<FormRules>(() => ({
  userId: [{ required: true, message: '请选择所属用户', trigger: 'change' }],
  orgId: [{ required: true, message: '请选择所属组织', trigger: 'change' }],
  ...(dialogMode.value === 'create' ? positionFields.createRules : positionFields.editRules),
}))

const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增任职' : '编辑任职'))

async function openCreateDialog() {
  // 按钮在未选中左侧组织节点时禁用，理论上不会在 selectedOrgId 为 null 时被调用
  if (positionStore.selectedOrgId === null) return
  await fetchOrgTree()
  dialogMode.value = 'create'
  editingId.value = null
  resetDynamicKeys(form, ['userId', 'userName', 'orgId'])
  Object.assign(form, positionFields.buildFormModel(positionFields.createFields))
  form.userId = null
  form.userName = ''
  // 所属组织默认预填为左侧当前选中的组织节点，用户仍可手动改选为其他组织
  form.orgId = positionStore.selectedOrgId
  userOptions.value = []
  dialogVisible.value = true
}

async function openEditDialog(row: PositionRow) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  const detail = await positionApi.getPositionById(row.id)
  await fetchOrgTree()
  resetDynamicKeys(form, ['userId', 'userName', 'orgId'])
  Object.assign(form, positionFields.buildFormModel(positionFields.editFields, detail))
  form.userId = detail.userId
  form.userName = detail.userName
  form.orgId = detail.orgId
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
    // 上面的表单校验已经确保 orgId 必填、positionType 等动态字段的必填/正则规则（来自
    // positionFields.createRules/editRules）；userId/userName 单独处理（userName 只读
    // 展示，不提交给后端），其余动态字段（含 positionType、MULTI_DICT）先经
    // buildSubmitModel 把多选字典的数组值序列化成逗号分隔字符串，避免把数组直接提交
    // 给后端导致 HttpMessageNotReadableException
    const { userId, userName: _userName, orgId, ...dynamicValues } = form
    const activeFields = dialogMode.value === 'create' ? positionFields.createFields : positionFields.editFields
    const payload = {
      ...positionFields.buildSubmitModel(activeFields, dynamicValues),
      orgId: orgId as number,
    } as unknown as PositionFormRequest
    if (dialogMode.value === 'create') {
      // 表单校验已经确保 userId 必填，此处已知非空；编辑接口不接受 userId 字段，
      // 换人须删除重建，因此只在新增分支拼接该字段
      await positionApi.createPosition({ ...payload, userId: userId as number })
      ElMessage.success('新增成功')
    } else {
      await positionApi.updatePosition(editingId.value as number, payload)
      ElMessage.success('保存成功')
    }
    dialogVisible.value = false
    await positionStore.refreshAfterMutation()
  } finally {
    submitting.value = false
  }
}

// ---- 只读详情：跳转独立详情页 ----

function goToDetail(row: PositionRow) {
  router.push({ name: 'identity-positions-detail', params: { id: row.id } })
}

// ---- 行操作：启用/停用、删除 ----
// 任职记录的增删改不影响左侧组织树本身，无需像组织管理那样联动刷新左侧树

async function toggleStatus(row: PositionRow) {
  if (row.status === POSITION_STATUS_ENABLED) {
    await positionApi.disablePosition(row.id)
    ElMessage.success('已停用')
  } else {
    await positionApi.enablePosition(row.id)
    ElMessage.success('已启用')
  }
  await positionStore.refreshAfterMutation()
}

async function handleDelete(row: PositionRow) {
  await ElMessageBox.confirm(`确定要删除「${row.userName}」在「${row.orgName}」的任职记录吗？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await positionApi.deletePosition(row.id)
  ElMessage.success('删除成功')
  await positionStore.refreshAfterMutation()
}
</script>

<template>
  <div class="position-management">
    <section class="position-panel position-panel--tree">
      <header class="position-panel__header">
        <h2 class="position-panel__title">组织架构</h2>
      </header>
      <el-tree
        class="position-tree"
        :data="positionStore.navTreeTopLevel"
        :props="treeProps"
        node-key="id"
        :indent="8"
        lazy
        :load="loadNode"
        highlight-current
        :current-node-key="positionStore.selectedOrgId ?? undefined"
        @node-click="handleNodeClick"
      />
    </section>

    <section class="position-panel position-panel--table">
      <header class="position-panel__header">
        <!-- 未选中任何左侧树节点时标题保持空白，是本页面刻意的默认态 -->
        <h2 class="position-panel__title">{{ rightPanelTitle }}</h2>
        <div class="position-panel__actions">
          <el-button v-if="hasPermission('PositionManagement:position:importTemplate')" @click="handleDownloadTemplate">下载导入模板</el-button>
          <el-button v-if="hasPermission('PositionManagement:position:import')" @click="batchImportVisible = true">批量导入</el-button>
          <el-button v-if="hasPermission('PositionManagement:position:export')" @click="handleExportExcel">导出Excel</el-button>
          <el-button
            v-if="hasPermission('PositionManagement:position:add')"
            type="primary"
            :disabled="positionStore.selectedOrgId === null"
            @click="openCreateDialog"
          >
            新增
          </el-button>
        </div>
      </header>

      <el-empty
        v-if="positionStore.selectedOrgId === null"
        class="position-empty"
        description="请先在左侧选择组织，查看其任职人员"
      />

      <template v-else>
        <el-table v-loading="positionStore.listLoading" :data="positionStore.list" empty-text="暂无任职记录">
          <el-table-column prop="userName" label="姓名" min-width="100" />
          <el-table-column prop="orgName" label="组织" min-width="120" />
          <!-- 任职类型（positionType）已随 positionFields.listColumns 动态渲染（showInList=true），
               不再单独硬编码一列，避免同一字段渲染两次，见 design.md 决策 2 -->
          <el-table-column
            v-for="col in positionFields.listColumns"
            :key="col.fieldCode"
            :label="col.fieldName"
            min-width="120"
          >
            <template #default="{ row }">
              <span v-if="col.controlType === FORM_FIELD_CONTROL_TYPE_DICT">
                {{ positionFields.dictOptionLabel(col, (row as Record<string, unknown>)[col.columnName]) }}
              </span>
              <span v-else-if="col.controlType === FORM_FIELD_CONTROL_TYPE_MULTI_DICT">
                {{ positionFields.dictOptionLabels(col, (row as Record<string, unknown>)[col.columnName]) }}
              </span>
              <span v-else>{{ (row as Record<string, unknown>)[col.columnName] }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag v-if="(row as PositionRow).status === POSITION_STATUS_ENABLED" type="success">启用</el-tag>
              <el-tag v-else type="warning">停用</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="{ row }">
              <el-button v-if="hasPermission('PositionManagement:position:detail')" link type="primary" @click="goToDetail(row as PositionRow)">详情</el-button>
              <el-button v-if="hasPermission('PositionManagement:position:edit')" link type="primary" @click="openEditDialog(row as PositionRow)">编辑</el-button>
              <el-button
                link
                :type="(row as PositionRow).status === POSITION_STATUS_ENABLED ? 'warning' : 'success'"
                v-if="(row as PositionRow).status === POSITION_STATUS_ENABLED ? hasPermission('PositionManagement:position:disable') : hasPermission('PositionManagement:position:enable')"
                @click="toggleStatus(row as PositionRow)"
              >
                {{ (row as PositionRow).status === POSITION_STATUS_ENABLED ? '停用' : '启用' }}
              </el-button>
              <el-button v-if="hasPermission('PositionManagement:position:delete')" link type="danger" @click="handleDelete(row as PositionRow)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          class="position-pagination"
          background
          layout="sizes, prev, pager, next, total"
          :page-sizes="[...PAGE_SIZE_OPTIONS]"
          :current-page="positionStore.page"
          :page-size="positionStore.pageSize"
          :total="positionStore.total"
          @current-change="handlePageChange"
          @size-change="positionStore.changePageSize"
        />
      </template>
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" @close="closeDialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="所属用户" prop="userId">
          <!-- 新增时通过远程搜索选择已存在用户；编辑时所属用户不可修改，只读展示 -->
          <el-select
            v-if="dialogMode === 'create'"
            v-model="form.userId"
            filterable
            remote
            reserve-keyword
            placeholder="输入姓名搜索用户"
            :remote-method="remoteSearchUsers"
            :loading="userSearchLoading"
            style="width: 100%"
          >
            <el-option
              v-for="opt in userOptions"
              :key="opt.id"
              :label="`${opt.name}（${opt.code}）`"
              :value="opt.id"
            />
          </el-select>
          <el-input v-else :model-value="form.userName" disabled />
        </el-form-item>
        <el-form-item label="所属组织" prop="orgId">
          <el-tree-select
            v-model="form.orgId"
            :data="orgTree"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            check-strictly
            placeholder="请选择组织"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item
          v-for="item in (dialogMode === 'create' ? positionFields.createFields : positionFields.editFields)"
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

    <batch-import-dialog v-model="batchImportVisible" biz-type="POSITION" @imported="handleImported" />
  </div>
</template>

<style scoped lang="scss">
.position-management {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 16px;
  align-items: start;

  @media (max-width: 960px) {
    grid-template-columns: 1fr;
  }
}

.position-panel {
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

.position-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.position-panel__title {
  font-size: 15px;
  color: var(--color-ink);
  margin: 0;
}

.position-panel__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.position-empty {
  padding: 32px 0;
}

.position-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

// 用一条虚线 + 圆点把父子层级"连"起来，呼应侧边栏子菜单/组织管理左侧树的链式视觉语言
.position-tree :deep(.el-tree-node__children) {
  position: relative;
  margin-left: 4px;
  padding-left: 6px;
  border-left: 1px dashed var(--chain-line-color);
}

.position-tree :deep(.el-tree-node__children > .el-tree-node) {
  position: relative;
}

// 圆点的 left 值须与上面 .el-tree-node__children 的 padding-left 保持一致（互为负值），
// 否则圆点会偏离虚线
.position-tree :deep(.el-tree-node__children > .el-tree-node::before) {
  content: '';
  position: absolute;
  left: -6px;
  top: 14px;
  width: var(--chain-dot-size-sm);
  height: var(--chain-dot-size-sm);
  border-radius: 50%;
  background: var(--chain-line-color);
}

.position-tree :deep(.el-tree-node.is-current > .el-tree-node__children::before),
.position-tree :deep(.el-tree-node.is-current::before) {
  background: var(--chain-line-color-active);
}

.position-form-hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-top: 4px;
}

// 操作列（详情/编辑/启用停用/删除）相邻按钮间距收紧，比 Element Plus 默认更紧凑
:deep(.el-table .el-button + .el-button) {
  margin-left: 6px;
}
</style>
