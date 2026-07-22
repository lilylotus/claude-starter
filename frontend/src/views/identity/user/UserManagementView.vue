<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
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
import { useDynamicFormFields } from '@/composables/useDynamicFormFields'
import {
  FORM_FIELD_CONTROL_TYPE_DICT,
  FORM_FIELD_CONTROL_TYPE_NUMBER,
  FORM_FIELD_CONTROL_TYPE_TEXT,
  type FormFieldRenderItem,
} from '@/types/formField'

const userStore = useUserStore()
const router = useRouter()

// 除性别（gender）、启停用状态（status）外的全部字段（含原有表字段与 ext1~ext10）
// 统一按"表单字段定义"（bizType=USER）动态渲染，见 design.md 决策 12
const userFields = useDynamicFormFields('USER')

// 内嵌"任职信息"子表单同样接入"表单字段定义"（bizType=POSITION），与独立任职管理页面
// （PositionManagementView.vue）读写同一套动态字段定义，逐行渲染在子表单每一行内
const positionFields = useDynamicFormFields('POSITION')

onMounted(() => {
  userStore.fetchPage()
  userFields.fetchSchema()
  positionFields.fetchSchema()
  fetchPositionTypeOptions()
  // 全量组织树（orgTree，供新增/编辑弹窗内任职子表单“所属组织”选择器用）不在这里预加载：
  // 只有打开弹窗时才需要它，此处预加载会让绝大多数只浏览/搜索用户列表的页面访问都白白
  // 发一次 GET /api/orgs/tree（见 openCreateDialog/openEditDialog）
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

// ---- 任职类型下拉框（数据源为字典模块 position_type 字典类型下的启用项） ----

const positionTypeOptions = ref<DictItemOption[]>([])

async function fetchPositionTypeOptions() {
  positionTypeOptions.value = await dictApi.getDictItemOptions('position_type')
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
    ext1: '',
    ext2: '',
    ext3: '',
    ext4: '',
    ext5: '',
    ext6: '',
    ext7: '',
    ext8: '',
    ext9: '',
    ext10: '',
  }
}

// gender/positions 保持静态声明；其余字段（name/code/mobile/idCard/showOrder/remark/
// ext1~ext10）由 userFields 动态渲染驱动，key 为各自绑定的 columnName
type UserForm = { gender: number; positions: UserPositionFormItem[] } & Record<string, unknown>

const form = reactive<UserForm>({ gender: 0, positions: [] })

// 保留 form 里指定 key（如 gender/positions），清空其余动态字段的 key，供每次打开弹窗前重置
function resetDynamicKeys(target: Record<string, unknown>, keep: string[]) {
  Object.keys(target).forEach((key) => {
    if (!keep.includes(key)) delete target[key]
  })
}

const rules = computed<FormRules>(() => ({
  gender: [{ required: true, message: '请选择性别', trigger: 'change' }],
  ...(dialogMode.value === 'create' ? userFields.createRules : userFields.editRules),
}))

// 任职信息子表单每行的独立校验规则，通过动态 prop（positions.{index}.xxx）挂载
const positionOrgRule = [{ required: true, message: '请选择所属组织', trigger: 'change' }]
const positionTypeRule = [{ required: true, message: '请选择任职类型', trigger: 'change' }]

// 任职信息子表单每行 bizType=POSITION 动态字段的校验规则，取值逻辑与
// PositionManagementView.vue 自身表单一致：新增/编辑分别对应 createRules/editRules，
// 按当前字段的 columnName 取出对应规则（未配置必填/正则时为 undefined，等价于不校验）
function positionDynamicFieldRules(item: FormFieldRenderItem) {
  return (dialogMode.value === 'create' ? positionFields.createRules : positionFields.editRules)[item.columnName]
}

const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增用户' : '编辑用户'))

function addPositionRow() {
  form.positions.push(blankPosition())
}

function removePositionRow(index: number) {
  form.positions.splice(index, 1)
}

// 任职子表单“所属组织”选择器要用的全量组织树只在真正打开弹窗时才按需请求
async function openCreateDialog() {
  await fetchOrgTree()
  dialogMode.value = 'create'
  editingId.value = null
  resetDynamicKeys(form, ['gender', 'positions'])
  Object.assign(form, userFields.buildFormModel(userFields.createFields))
  form.gender = 0
  form.positions = []
  dialogVisible.value = true
}

async function openEditDialog(row: UserRow) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  const detail = await userApi.getUserById(row.id)
  await fetchOrgTree()
  resetDynamicKeys(form, ['gender', 'positions'])
  Object.assign(form, userFields.buildFormModel(userFields.editFields, detail))
  form.gender = detail.gender
  form.positions = detail.positions.map((position) => ({
    id: position.id,
    orgId: position.orgId,
    positionType: position.positionType,
    positionAddress: position.positionAddress,
    positionPhone: position.positionPhone,
    showOrder: position.showOrder,
    remark: position.remark,
    ext1: position.ext1 ?? '',
    ext2: position.ext2 ?? '',
    ext3: position.ext3 ?? '',
    ext4: position.ext4 ?? '',
    ext5: position.ext5 ?? '',
    ext6: position.ext6 ?? '',
    ext7: position.ext7 ?? '',
    ext8: position.ext8 ?? '',
    ext9: position.ext9 ?? '',
    ext10: position.ext10 ?? '',
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
    const payload = {
      ...form,
      positions: form.positions.map((position) => ({
        ...position,
        orgId: position.orgId as number,
      })),
    } as unknown as UserFormRequest
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

// ---- 只读详情：跳转独立详情页 ----

function goToDetail(row: UserRow) {
  router.push({ name: 'identity-users-detail', params: { id: row.id } })
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
        <el-table-column
          v-for="col in userFields.listColumns"
          :key="col.fieldCode"
          :label="col.fieldName"
          min-width="120"
        >
          <template #default="{ row }">
            <span v-if="col.controlType === FORM_FIELD_CONTROL_TYPE_DICT">
              {{ userFields.dictOptionLabel(col, (row as Record<string, unknown>)[col.columnName]) }}
            </span>
            <span v-else>{{ (row as Record<string, unknown>)[col.columnName] }}</span>
          </template>
        </el-table-column>
        <el-table-column label="性别" width="80">
          <template #default="{ row }">{{ genderLabel((row as UserRow).gender) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="(row as UserRow).status === USER_STATUS_ENABLED" type="success">启用</el-tag>
            <el-tag v-else type="warning">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="goToDetail(row as UserRow)">详情</el-button>
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
          <el-form-item label="性别" prop="gender">
            <el-select v-model="form.gender" style="width: 100%">
              <el-option v-for="opt in USER_GENDER_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
          <el-form-item
            v-for="item in (dialogMode === 'create' ? userFields.createFields : userFields.editFields)"
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
        </div>

        <div class="user-position-section">
          <div class="user-position-section__header">
            <span class="user-position-section__title">任职信息</span>
            <el-button link type="primary" @click="addPositionRow">+ 添加任职</el-button>
          </div>

          <p v-if="form.positions.length === 0" class="user-position-empty">暂无任职记录</p>

          <div v-else class="user-position-list">
            <div v-for="(position, index) in form.positions" :key="index" class="user-position-row">
              <div class="user-position-row__fields">
                <el-form-item label="所属组织" label-width="90px" :prop="`positions.${index}.orgId`" :rules="positionOrgRule">
                  <el-tree-select
                    v-model="position.orgId"
                    :data="orgTree"
                    :props="{ label: 'name', children: 'children' }"
                    node-key="id"
                    check-strictly
                    placeholder="请选择组织"
                    style="width: 100%"
                  />
                </el-form-item>
                <el-form-item
                  label="任职类型"
                  label-width="90px"
                  :prop="`positions.${index}.positionType`"
                  :rules="positionTypeRule"
                >
                  <el-select v-model="position.positionType" placeholder="请选择任职类型" style="width: 100%">
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
                <el-form-item
                  v-for="item in (dialogMode === 'create' ? positionFields.createFields : positionFields.editFields).filter(
                    (f) => f.columnName.startsWith('ext'),
                  )"
                  :key="item.fieldCode"
                  :label="item.fieldName"
                  label-width="76px"
                  :prop="`positions.${index}.${item.columnName}`"
                  :rules="positionDynamicFieldRules(item)"
                >
                  <el-input
                    v-if="item.controlType === FORM_FIELD_CONTROL_TYPE_TEXT"
                    v-model="(position[item.columnName] as string)"
                    :placeholder="item.placeholder || `请输入${item.fieldName}`"
                    :disabled="!item.editable"
                  />
                  <el-input-number
                    v-else-if="item.controlType === FORM_FIELD_CONTROL_TYPE_NUMBER"
                    v-model="(position[item.columnName] as number)"
                    :min="0"
                    style="width: 100%"
                    :disabled="!item.editable"
                  />
                  <template v-else>
                    <el-select
                      v-model="(position[item.columnName] as string)"
                      :placeholder="item.placeholder || `请选择${item.fieldName}`"
                      :disabled="!item.editable"
                      style="width: 100%"
                    >
                      <el-option v-for="opt in item.dictOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                    </el-select>
                  </template>
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

// 操作列相邻按钮间距收紧，比 Element Plus 默认更紧凑
:deep(.el-table .el-button + .el-button) {
  margin-left: 6px;
}
</style>
