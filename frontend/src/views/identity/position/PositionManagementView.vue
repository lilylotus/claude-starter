<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type Node from 'element-plus/es/components/tree/src/model/node'
import { usePositionStore } from '@/stores/position'
import OperationHistoryPanel from '@/components/OperationHistoryPanel.vue'
import * as positionApi from '@/api/position'
import * as orgApi from '@/api/org'
import * as userApi from '@/api/user'
import * as dictApi from '@/api/dict'
import { POSITION_STATUS_ENABLED, type PositionFormRequest, type PositionRow } from '@/types/position'
import type { OrgTreeNode } from '@/types/org'
import type { DictItemOption } from '@/types/dict'

const positionStore = usePositionStore()

onMounted(() => {
  // 左侧导航树在 lazy 模式下挂载时会自动对根节点调用一次 load（parentId = 0），
  // 这里不需要主动拉取；任职类型下拉框的数据源在这里预加载。
  // 全量组织树（orgTree，供新增/编辑弹窗内“所属组织”选择器用）不在这里预加载：
  // 只有打开弹窗时才需要它，此处预加载会让绝大多数只浏览任职列表的页面访问都白白
  // 发一次 GET /api/orgs/tree（见 openCreateDialog/openEditDialog）
  fetchPositionTypeOptions()
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

// ---- 任职类型下拉框（数据源为字典模块 position_type 字典类型下的启用项） ----

const positionTypeOptions = ref<DictItemOption[]>([])

async function fetchPositionTypeOptions() {
  positionTypeOptions.value = await dictApi.getDictItemOptions('position_type')
}

function positionTypeLabel(code: string): string {
  return positionTypeOptions.value.find((opt) => opt.code === code)?.label ?? code
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
// userName 仅编辑模式下只读展示所属用户，不随表单一起提交给后端
interface PositionForm {
  userId: number | null
  userName: string
  orgId: number | null
  positionType: string
  positionAddress: string
  positionPhone: string
  showOrder: number
  remark: string
}

const form = reactive<PositionForm>({
  userId: null,
  userName: '',
  orgId: null,
  positionType: '',
  positionAddress: '',
  positionPhone: '',
  showOrder: 0,
  remark: '',
})

const rules: FormRules<PositionForm> = {
  userId: [{ required: true, message: '请选择所属用户', trigger: 'change' }],
  orgId: [{ required: true, message: '请选择所属组织', trigger: 'change' }],
  positionType: [{ required: true, message: '请选择任职类型', trigger: 'change' }],
}

const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增任职' : '编辑任职'))

async function openCreateDialog() {
  // 按钮在未选中左侧组织节点时禁用，理论上不会在 selectedOrgId 为 null 时被调用
  if (positionStore.selectedOrgId === null) return
  await fetchOrgTree()
  dialogMode.value = 'create'
  editingId.value = null
  form.userId = null
  form.userName = ''
  // 所属组织默认预填为左侧当前选中的组织节点，用户仍可手动改选为其他组织
  form.orgId = positionStore.selectedOrgId
  form.positionType = ''
  form.positionAddress = ''
  form.positionPhone = ''
  form.showOrder = 0
  form.remark = ''
  userOptions.value = []
  dialogVisible.value = true
}

async function openEditDialog(row: PositionRow) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  const detail = await positionApi.getPositionById(row.id)
  await fetchOrgTree()
  form.userId = detail.userId
  form.userName = detail.userName
  form.orgId = detail.orgId
  form.positionType = detail.positionType
  form.positionAddress = detail.positionAddress
  form.positionPhone = detail.positionPhone
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
    // 上面的表单校验已经确保 orgId/positionType 必填，此处已知非空
    const payload: PositionFormRequest = {
      orgId: form.orgId as number,
      positionType: form.positionType,
      positionAddress: form.positionAddress,
      positionPhone: form.positionPhone,
      showOrder: form.showOrder,
      remark: form.remark,
    }
    if (dialogMode.value === 'create') {
      // 表单校验已经确保 userId 必填，此处已知非空；编辑接口不接受 userId 字段，
      // 换人须删除重建，因此只在新增分支拼接该字段
      await positionApi.createPosition({ ...payload, userId: form.userId as number })
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

// ---- 只读详情弹窗 ----

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref<PositionRow | null>(null)

async function openDetailDialog(row: PositionRow) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detailData.value = await positionApi.getPositionById(row.id)
  } finally {
    detailLoading.value = false
  }
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
        <el-button type="primary" :disabled="positionStore.selectedOrgId === null" @click="openCreateDialog">
          新增
        </el-button>
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
          <el-table-column label="任职类型" min-width="100">
            <template #default="{ row }">{{ positionTypeLabel((row as PositionRow).positionType) }}</template>
          </el-table-column>
          <el-table-column prop="positionAddress" label="任职地址" min-width="140" />
          <el-table-column prop="positionPhone" label="任职电话" min-width="120" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag v-if="(row as PositionRow).status === POSITION_STATUS_ENABLED" type="success">启用</el-tag>
              <el-tag v-else type="warning">停用</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="showOrder" label="显示序号" width="90" />
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetailDialog(row as PositionRow)">详情</el-button>
              <el-button link type="primary" @click="openEditDialog(row as PositionRow)">编辑</el-button>
              <el-button
                link
                :type="(row as PositionRow).status === POSITION_STATUS_ENABLED ? 'warning' : 'success'"
                @click="toggleStatus(row as PositionRow)"
              >
                {{ (row as PositionRow).status === POSITION_STATUS_ENABLED ? '停用' : '启用' }}
              </el-button>
              <el-button link type="danger" @click="handleDelete(row as PositionRow)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          class="position-pagination"
          background
          layout="prev, pager, next, total"
          :current-page="positionStore.page"
          :page-size="positionStore.pageSize"
          :total="positionStore.total"
          @current-change="handlePageChange"
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
        <el-form-item label="任职类型" prop="positionType">
          <el-select v-model="form.positionType" placeholder="请选择任职类型" style="width: 100%">
            <el-option v-for="opt in positionTypeOptions" :key="opt.code" :label="opt.label" :value="opt.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="任职地址" prop="positionAddress">
          <el-input v-model="form.positionAddress" placeholder="选填" />
        </el-form-item>
        <el-form-item label="任职电话" prop="positionPhone">
          <el-input v-model="form.positionPhone" placeholder="选填" />
        </el-form-item>
        <el-form-item label="显示序号" prop="showOrder">
          <el-input-number v-model="form.showOrder" :min="0" style="width: 100%" />
          <div class="position-form-hint">数值越大，排序越靠前</div>
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

    <el-dialog v-model="detailVisible" title="任职详情" width="560px" destroy-on-close>
      <el-descriptions v-loading="detailLoading" :column="1" border>
        <el-descriptions-item label="所属用户">{{ detailData?.userName }}</el-descriptions-item>
        <el-descriptions-item label="所属组织">{{ detailData?.orgName }}</el-descriptions-item>
        <el-descriptions-item label="任职类型">
          {{ positionTypeLabel(detailData?.positionType ?? '') }}
        </el-descriptions-item>
        <el-descriptions-item label="任职地址">{{ detailData?.positionAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item label="任职电话">{{ detailData?.positionPhone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="显示序号">{{ detailData?.showOrder }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detailData?.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag v-if="detailData?.status === POSITION_STATUS_ENABLED" type="success">启用</el-tag>
          <el-tag v-else type="warning">停用</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detailData?.createBy }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detailData?.createTime }}</el-descriptions-item>
        <el-descriptions-item label="更新人">{{ detailData?.updateBy }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ detailData?.updateTime }}</el-descriptions-item>
      </el-descriptions>

      <OperationHistoryPanel resource-type="position" :target-id="detailData?.id ?? null" />

      <template #footer>
        <el-button type="primary" @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
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
