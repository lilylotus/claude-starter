<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import * as userApi from '@/api/user'
import * as orgApi from '@/api/org'
import * as dictApi from '@/api/dict'
import {
  USER_GENDER_OPTIONS,
  USER_STATUS_ENABLED,
  type UserFormRequest,
  type UserPositionFormItem,
  type UserRow,
} from '@/types/user'
import type { OrgTreeNode } from '@/types/org'
import type { DictItemOption } from '@/types/dict'

const userStore = useUserStore()

onMounted(() => {
  userStore.fetchPage()
  fetchOrgTree()
  fetchPositionTypeOptions()
})

// ---- 搜索栏（姓名/手机号/身份证号，三者均可选，组合为“与”关系） ----

const nameInput = ref('')
const mobileInput = ref('')
const idCardInput = ref('')

function handleSearch() {
  userStore.search({
    name: nameInput.value.trim(),
    mobile: mobileInput.value.trim(),
    idCard: idCardInput.value.trim(),
  })
}

function handleReset() {
  nameInput.value = ''
  mobileInput.value = ''
  idCardInput.value = ''
  userStore.search({ name: '', mobile: '', idCard: '' })
}

function handlePageChange(targetPage: number) {
  userStore.changePage(targetPage)
}

function genderLabel(gender: number): string {
  return USER_GENDER_OPTIONS.find((opt) => opt.value === gender)?.label ?? '未知'
}

// ---- 组织树（任职信息子表单的组织选择器数据源，一次性加载，不需要防环处理） ----

const orgTree = ref<OrgTreeNode[]>([])

async function fetchOrgTree() {
  orgTree.value = await orgApi.getOrgTree()
}

// ---- 认证类型下拉框（数据源为字典模块 position_type 字典类型下的启用项） ----

const positionTypeOptions = ref<DictItemOption[]>([])

async function fetchPositionTypeOptions() {
  positionTypeOptions.value = await dictApi.getDictItemOptions('position_type')
}

function positionTypeLabel(code: string): string {
  return positionTypeOptions.value.find((opt) => opt.code === code)?.label ?? code
}

// ---- 新增/编辑弹窗 ----

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

function blankPosition(): UserPositionFormItem {
  return {
    orgId: null,
    positionType: '',
    positionAddress: '',
    positionPhone: '',
    showOrder: 0,
    remark: '',
  }
}

const form = reactive<UserFormRequest>({
  name: '',
  code: '',
  gender: 0,
  mobile: '',
  idCard: '',
  showOrder: 0,
  remark: '',
  positions: [],
})

function validateMobile(_rule: unknown, value: string, callback: (error?: Error) => void) {
  if (!value || /^1\d{10}$/.test(value)) {
    callback()
    return
  }
  callback(new Error('手机号格式不正确'))
}

function validateIdCard(_rule: unknown, value: string, callback: (error?: Error) => void) {
  if (!value || /^\d{15}$/.test(value) || /^\d{17}[0-9Xx]$/.test(value)) {
    callback()
    return
  }
  callback(new Error('身份证号格式不正确'))
}

const rules: FormRules<UserFormRequest> = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  code: [{ required: true, message: '请输入编号', trigger: 'blur' }],
  gender: [{ required: true, message: '请选择性别', trigger: 'change' }],
  mobile: [{ validator: validateMobile, trigger: 'blur' }],
  idCard: [{ validator: validateIdCard, trigger: 'blur' }],
}

// 任职信息子表单每行的独立校验规则，通过动态 prop（positions.{index}.xxx）挂载
const positionOrgRule = [{ required: true, message: '请选择所属组织', trigger: 'change' }]
const positionTypeRule = [{ required: true, message: '请选择认证类型', trigger: 'change' }]

const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增用户' : '编辑用户'))

function addPositionRow() {
  form.positions.push(blankPosition())
}

function removePositionRow(index: number) {
  form.positions.splice(index, 1)
}

function resetForm() {
  form.name = ''
  form.code = ''
  form.gender = 0
  form.mobile = ''
  form.idCard = ''
  form.showOrder = 0
  form.remark = ''
  form.positions = []
}

function openCreateDialog() {
  dialogMode.value = 'create'
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

async function openEditDialog(row: UserRow) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  const detail = await userApi.getUserById(row.id)
  form.name = detail.name
  form.code = detail.code
  form.gender = detail.gender
  form.mobile = detail.mobile
  form.idCard = detail.idCard
  form.showOrder = detail.showOrder
  form.remark = detail.remark
  form.positions = detail.positions.map((position) => ({
    id: position.id,
    orgId: position.orgId,
    positionType: position.positionType,
    positionAddress: position.positionAddress,
    positionPhone: position.positionPhone,
    showOrder: position.showOrder,
    remark: position.remark,
  }))
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
    // 上面的表单校验已确保每一行任职记录的 orgId 必填，此处已知非空
    const payload: UserFormRequest = {
      ...form,
      positions: form.positions.map((position) => ({
        ...position,
        orgId: position.orgId as number,
      })),
    }
    if (dialogMode.value === 'create') {
      await userApi.createUser(payload)
      ElMessage.success('新增成功')
    } else {
      await userApi.updateUser(editingId.value as number, payload)
      ElMessage.success('保存成功')
    }
    dialogVisible.value = false
    await userStore.refreshAfterMutation()
  } finally {
    submitting.value = false
  }
}

// ---- 只读详情弹窗 ----

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref<UserRow | null>(null)

async function openDetailDialog(row: UserRow) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detailData.value = await userApi.getUserById(row.id)
  } finally {
    detailLoading.value = false
  }
}

// ---- 行操作：启用/停用、删除 ----

async function toggleStatus(row: UserRow) {
  if (row.status === USER_STATUS_ENABLED) {
    await userApi.disableUser(row.id)
    ElMessage.success('已停用')
  } else {
    await userApi.enableUser(row.id)
    ElMessage.success('已启用')
  }
  await userStore.refreshAfterMutation()
}

async function handleDelete(row: UserRow) {
  await ElMessageBox.confirm(`确定要删除用户「${row.name}」吗？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await userApi.deleteUser(row.id)
  ElMessage.success('删除成功')
  await userStore.refreshAfterMutation()
}
</script>

<template>
  <div class="user-management">
    <section class="user-panel">
      <header class="user-panel__header">
        <h2 class="user-panel__title">用户列表</h2>
        <el-button type="primary" @click="openCreateDialog">新增</el-button>
      </header>

      <div class="user-search">
        <el-input
          v-model="nameInput"
          class="user-search__input"
          placeholder="按姓名搜索"
          clearable
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-input
          v-model="mobileInput"
          class="user-search__input"
          placeholder="按手机号搜索"
          clearable
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-input
          v-model="idCardInput"
          class="user-search__input"
          placeholder="按身份证号搜索"
          clearable
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button :icon="Search" type="primary" plain @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-table v-loading="userStore.loading" :data="userStore.list">
        <el-table-column prop="name" label="姓名" min-width="100" />
        <el-table-column prop="code" label="编号" min-width="100" />
        <el-table-column label="性别" width="80">
          <template #default="{ row }">{{ genderLabel((row as UserRow).gender) }}</template>
        </el-table-column>
        <el-table-column prop="mobile" label="手机号" min-width="120" />
        <el-table-column prop="idCard" label="身份证号" min-width="160" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="(row as UserRow).status === USER_STATUS_ENABLED" type="success">启用</el-tag>
            <el-tag v-else type="warning">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="showOrder" label="显示序号" width="90" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetailDialog(row as UserRow)">详情</el-button>
            <el-button link type="primary" @click="openEditDialog(row as UserRow)">编辑</el-button>
            <el-button
              link
              :type="(row as UserRow).status === USER_STATUS_ENABLED ? 'warning' : 'success'"
              @click="toggleStatus(row as UserRow)"
            >
              {{ (row as UserRow).status === USER_STATUS_ENABLED ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" @click="handleDelete(row as UserRow)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="user-pagination"
        background
        layout="prev, pager, next, total"
        :current-page="userStore.page"
        :page-size="userStore.pageSize"
        :total="userStore.total"
        @current-change="handlePageChange"
      />
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="760px" @close="closeDialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <div class="user-form-grid">
          <el-form-item label="姓名" prop="name">
            <el-input v-model="form.name" placeholder="请输入姓名" />
          </el-form-item>
          <el-form-item label="编号" prop="code">
            <el-input v-model="form.code" placeholder="请输入编号" />
          </el-form-item>
          <el-form-item label="性别" prop="gender">
            <el-select v-model="form.gender" style="width: 100%">
              <el-option v-for="opt in USER_GENDER_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="手机号" prop="mobile">
            <el-input v-model="form.mobile" placeholder="选填" />
          </el-form-item>
          <el-form-item label="身份证号" prop="idCard">
            <el-input v-model="form.idCard" placeholder="选填" />
          </el-form-item>
          <el-form-item label="显示序号" prop="showOrder">
            <el-input-number v-model="form.showOrder" :min="0" style="width: 100%" />
          </el-form-item>
        </div>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>

        <div class="user-position-section">
          <div class="user-position-section__header">
            <span class="user-position-section__title">任职信息</span>
            <el-button link type="primary" @click="addPositionRow">+ 添加任职</el-button>
          </div>

          <p v-if="form.positions.length === 0" class="user-position-empty">暂无任职记录</p>

          <div v-else class="user-position-list">
            <div v-for="(position, index) in form.positions" :key="index" class="user-position-row">
              <div class="user-position-row__fields">
                <el-form-item label="所属组织" label-width="76px" :prop="`positions.${index}.orgId`" :rules="positionOrgRule">
                  <el-tree-select
                    v-model="position.orgId"
                    :data="orgTree"
                    :props="{ label: 'name', children: 'children' }"
                    node-key="id"
                    check-strictly
                    default-expand-all
                    placeholder="请选择组织"
                    style="width: 100%"
                  />
                </el-form-item>
                <el-form-item
                  label="认证类型"
                  label-width="76px"
                  :prop="`positions.${index}.positionType`"
                  :rules="positionTypeRule"
                >
                  <el-select v-model="position.positionType" placeholder="请选择认证类型" style="width: 100%">
                    <el-option
                      v-for="opt in positionTypeOptions"
                      :key="opt.code"
                      :label="opt.label"
                      :value="opt.code"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="任职地址" label-width="76px">
                  <el-input v-model="position.positionAddress" placeholder="选填" />
                </el-form-item>
                <el-form-item label="任职电话" label-width="76px">
                  <el-input v-model="position.positionPhone" placeholder="选填" />
                </el-form-item>
                <el-form-item label="显示序号" label-width="76px">
                  <el-input-number v-model="position.showOrder" :min="0" style="width: 100%" />
                </el-form-item>
                <el-form-item label="备注" label-width="76px">
                  <el-input v-model="position.remark" placeholder="选填" />
                </el-form-item>
              </div>
              <el-button link type="danger" class="user-position-row__remove" @click="removePositionRow(index)">
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

    <el-dialog v-model="detailVisible" title="用户详情" width="760px">
      <el-descriptions v-loading="detailLoading" :column="2" border>
        <el-descriptions-item label="姓名">{{ detailData?.name }}</el-descriptions-item>
        <el-descriptions-item label="编号">{{ detailData?.code }}</el-descriptions-item>
        <el-descriptions-item label="性别">{{ genderLabel(detailData?.gender ?? 0) }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detailData?.mobile || '-' }}</el-descriptions-item>
        <el-descriptions-item label="身份证号">{{ detailData?.idCard || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag v-if="detailData?.status === USER_STATUS_ENABLED" type="success">启用</el-tag>
          <el-tag v-else type="warning">停用</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="显示序号">{{ detailData?.showOrder }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detailData?.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detailData?.createBy }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detailData?.createTime }}</el-descriptions-item>
        <el-descriptions-item label="更新人">{{ detailData?.updateBy }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ detailData?.updateTime }}</el-descriptions-item>
      </el-descriptions>

      <div class="user-detail-positions">
        <h3 class="user-detail-positions__title">任职记录</h3>
        <el-table :data="detailData?.positions ?? []" empty-text="暂无任职记录">
          <el-table-column prop="orgName" label="所属组织" min-width="120" />
          <el-table-column label="认证类型" min-width="100">
            <template #default="{ row }">{{ positionTypeLabel(row.positionType) }}</template>
          </el-table-column>
          <el-table-column prop="positionAddress" label="任职地址" min-width="140" />
          <el-table-column prop="positionPhone" label="任职电话" min-width="120" />
          <el-table-column prop="showOrder" label="显示序号" width="90" />
          <el-table-column prop="remark" label="备注" min-width="120" />
          <el-table-column prop="createBy" label="创建人" min-width="100" />
          <el-table-column prop="createTime" label="创建时间" min-width="160" />
          <el-table-column prop="updateBy" label="更新人" min-width="100" />
          <el-table-column prop="updateTime" label="更新时间" min-width="160" />
        </el-table>
      </div>

      <template #footer>
        <el-button type="primary" @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.user-management {
  display: flex;
  flex-direction: column;
}

.user-panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

.user-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.user-panel__title {
  font-size: 15px;
  color: var(--color-ink);
  margin: 0;
}

.user-search {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.user-search__input {
  width: 200px;
}

.user-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.user-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: 16px;
}

.user-position-section {
  margin-top: 8px;
}

.user-position-section__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.user-position-section__title {
  font-size: 14px;
  color: var(--color-ink);
  font-weight: 600;
}

.user-position-empty {
  font-size: 13px;
  color: var(--color-text-tertiary);
  margin: 0 0 8px;
}

// 任职信息各行用一条虚线 + 圆点串起来，呼应组织树/字典选中态的"链式连接"视觉语言
.user-position-list {
  position: relative;
  padding-left: 16px;
  border-left: 1px dashed var(--chain-line-color);
}

.user-position-row {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding-bottom: 4px;
  margin-bottom: 8px;
  border-bottom: 1px dashed var(--color-border);
}

.user-position-row:last-child {
  border-bottom: none;
}

.user-position-row::before {
  content: '';
  position: absolute;
  left: -20px;
  top: 14px;
  width: var(--chain-dot-size-sm);
  height: var(--chain-dot-size-sm);
  border-radius: 50%;
  background: var(--chain-line-color-active);
}

.user-position-row__fields {
  flex: 1;
  min-width: 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: 12px;
}

.user-position-row__remove {
  margin-top: 6px;
  flex-shrink: 0;
}

.user-detail-positions {
  margin-top: 20px;
}

.user-detail-positions__title {
  font-size: 14px;
  color: var(--color-ink);
  margin: 0 0 8px;
}

// 操作列相邻按钮间距收紧，比 Element Plus 默认更紧凑
:deep(.el-table .el-button + .el-button) {
  margin-left: 6px;
}
</style>
