<script setup lang="ts">
// 业务绑定管理页面（approval-process-binding-console change）。按业务类型（组织/用户/
// 任职/应用）分 Tab，每个 Tab 内按操作类型（新增/更新/启用/停用/删除）分组展示全局绑定 +
// 该操作类型下的组织范围覆盖绑定列表，支持新建组织覆盖绑定/切换已有绑定指向的版本/启停。
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import * as bindingApi from '@/api/processBinding'
import * as workflowApi from '@/api/workflow'
import * as orgApi from '@/api/org'
import { usePermission } from '@/composables/usePermission'
import type { OrgTreeNode } from '@/types/org'
import type { ProcessDefinitionVersionVO, ProcessModelRow } from '@/types/workflow'
import {
  BIZ_TYPE_OPTIONS,
  DEFAULT_EXECUTION_MODE,
  OPERATION_TYPE_OPTIONS,
  type BizType,
  type OperationType,
  type ProcessBindingRequest,
  type ProcessBindingVO,
  type ScopeType,
} from '@/types/processBinding'

const { hasPermission } = usePermission()
const canEdit = computed(() => hasPermission('WorkflowDesign:binding:edit'))
const canDelete = computed(() => hasPermission('WorkflowDesign:binding:delete'))

const loading = ref(false)
const bindings = ref<ProcessBindingVO[]>([])
const models = ref<ProcessModelRow[]>([])
// 按 modelId 缓存该模型的版本历史，供 definitionId 展示解析与"切换绑定"弹窗的第二级
// 下拉复用，避免对同一模型重复请求（design.md 决策5、tasks.md 3.2）。
const versionsByModel = reactive<Record<number, ProcessDefinitionVersionVO[]>>({})
const orgTree = ref<OrgTreeNode[]>([])

const activeBizType = ref<BizType>('ORG')

// definitionId -> 展示信息（所属模型、模型名、版本号、版本状态）的内存 join 结果。
interface DefinitionDisplay {
  modelId: number
  processCode: string
  processName: string
  version: number
  status: 'PUBLISHED' | 'DISABLED'
}

const definitionMap = computed<Map<number, DefinitionDisplay>>(() => {
  const map = new Map<number, DefinitionDisplay>()
  for (const model of models.value) {
    const versions = versionsByModel[model.id] || []
    for (const version of versions) {
      map.set(version.id, {
        modelId: model.id,
        processCode: model.processCode,
        processName: model.processName,
        version: version.version,
        status: version.status,
      })
    }
  }
  return map
})

function definitionLabel(definitionId: number): string {
  const info = definitionMap.value.get(definitionId)
  return info ? `${info.processName} v${info.version}` : `定义 #${definitionId}（未找到所属模型）`
}

function isDefinitionOffline(definitionId: number): boolean {
  const info = definitionMap.value.get(definitionId)
  return info ? info.status !== 'PUBLISHED' : false
}

function findOrgName(nodes: OrgTreeNode[], id: number): string | null {
  for (const node of nodes) {
    if (node.id === id) return node.name
    if (node.children?.length) {
      const found = findOrgName(node.children, id)
      if (found) return found
    }
  }
  return null
}

function orgName(scopeId: number | null): string {
  if (scopeId === null || scopeId === undefined) return '-'
  return findOrgName(orgTree.value, scopeId) ?? `组织 #${scopeId}`
}

async function loadAll() {
  loading.value = true
  try {
    const [bindingList, modelList, tree] = await Promise.all([
      bindingApi.listBindings(),
      workflowApi.listProcessModels(),
      orgApi.getOrgTree(),
    ])
    bindings.value = bindingList
    models.value = modelList
    orgTree.value = tree
    await Promise.all(
      modelList.map(async (model) => {
        versionsByModel[model.id] = await workflowApi.listProcessModelVersions(model.id)
      }),
    )
  } finally {
    loading.value = false
  }
}

loadAll()

// 每个 Tab 内一次只看一种操作类型（新增/更新/启用/停用/删除），通过下拉切换，不再把
// 五种操作类型的表格同时平铺展示（初版曾一次列出全部 5 个，用户反馈信息量过大）。
const activeOperationType = ref<OperationType>('CREATE')

// 切换业务类型 Tab 后操作类型下拉重置为默认的"新增"，避免带着上一个业务类型选中的操作
// 类型（如"删除"）切过来，让人误以为当前也在看"删除"、实际却是新 Tab 尚未选择过的状态。
watch(activeBizType, () => {
  activeOperationType.value = 'CREATE'
})

// 按 bizType + operationType 分组：全局绑定（最多一条）+ 组织范围覆盖绑定列表
interface BindingGroup {
  operationType: OperationType
  label: string
  global: ProcessBindingVO | null
  orgScoped: ProcessBindingVO[]
}

function groupFor(bizType: BizType, operationType: OperationType): BindingGroup {
  const opOption = OPERATION_TYPE_OPTIONS.find((op) => op.value === operationType)!
  const opBindings = bindings.value.filter(
    (b) => b.bizType === bizType && b.operationType === operationType,
  )
  return {
    operationType,
    label: opOption.label,
    global: opBindings.find((b) => b.scopeType === 'GLOBAL') ?? null,
    orgScoped: opBindings.filter((b) => b.scopeType === 'ORG'),
  }
}

function rowsFor(group: BindingGroup): ProcessBindingVO[] {
  return group.global ? [group.global, ...group.orgScoped] : group.orgScoped
}

// ---- 新建/切换绑定弹窗 ----

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'switch'>('create')
const dialogSubmitting = ref(false)
const formRef = ref<FormInstance>()

interface BindingForm {
  bizType: BizType
  operationType: OperationType
  scopeType: ScopeType
  scopeId: number | null
  modelId: number | null
  definitionId: number | null
  expectedRevision: number | null
  bindingId: number | null
}

const form = reactive<BindingForm>({
  bizType: 'ORG',
  operationType: 'CREATE',
  scopeType: 'ORG',
  scopeId: null,
  modelId: null,
  definitionId: null,
  expectedRevision: null,
  bindingId: null,
})

const rules: FormRules<BindingForm> = {
  scopeId: [
    {
      validator: (_rule, value, callback) => {
        if (form.scopeType === 'ORG' && !value) {
          callback(new Error('请选择组织'))
        } else {
          callback()
        }
      },
      trigger: 'change',
    },
  ],
  modelId: [{ required: true, message: '请选择流程模型', trigger: 'change' }],
  definitionId: [{ required: true, message: '请选择已发布版本', trigger: 'change' }],
}

const publishedVersionsForForm = computed<ProcessDefinitionVersionVO[]>(() => {
  if (!form.modelId) return []
  return (versionsByModel[form.modelId] || []).filter((version) => version.status === 'PUBLISHED')
})

const bizTypeLabel = computed(
  () => BIZ_TYPE_OPTIONS.find((opt) => opt.value === form.bizType)?.label ?? form.bizType,
)
const operationTypeLabel = computed(
  () => OPERATION_TYPE_OPTIONS.find((opt) => opt.value === form.operationType)?.label ?? form.operationType,
)

function resetForm() {
  form.scopeId = null
  form.modelId = null
  form.definitionId = null
  form.expectedRevision = null
  form.bindingId = null
  formRef.value?.clearValidate()
}

function openCreateDialog(bizType: BizType, operationType: OperationType, scopeType: ScopeType) {
  dialogMode.value = 'create'
  form.bizType = bizType
  form.operationType = operationType
  form.scopeType = scopeType
  resetForm()
  dialogVisible.value = true
}

function openSwitchDialog(binding: ProcessBindingVO) {
  dialogMode.value = 'switch'
  form.bizType = binding.bizType
  form.operationType = binding.operationType
  form.scopeType = binding.scopeType
  form.scopeId = binding.scopeId ?? null
  const info = definitionMap.value.get(binding.definitionId)
  form.modelId = info ? info.modelId : null
  form.definitionId = binding.definitionId
  form.expectedRevision = binding.revision
  form.bindingId = binding.id
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

function handleModelChange() {
  form.definitionId = null
}

async function submitDialog() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!form.definitionId) {
    ElMessage.error('请选择已发布版本')
    return
  }

  const payload: ProcessBindingRequest = {
    bizType: form.bizType,
    operationType: form.operationType,
    scopeType: form.scopeType,
    scopeId: form.scopeType === 'GLOBAL' ? undefined : form.scopeId,
    definitionId: form.definitionId,
    executionMode: DEFAULT_EXECUTION_MODE,
  }

  dialogSubmitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await bindingApi.createBinding(payload)
      ElMessage.success('新建绑定成功')
    } else {
      payload.expectedRevision = form.expectedRevision ?? undefined
      await bindingApi.switchBindingDefinition(form.bindingId as number, payload)
      ElMessage.success('切换版本成功')
    }
    dialogVisible.value = false
    await loadAll()
  } finally {
    dialogSubmitting.value = false
  }
}

async function handleEnable(binding: ProcessBindingVO) {
  await bindingApi.enableBinding(binding.id)
  ElMessage.success('已启用')
  await loadAll()
}

async function handleDisable(binding: ProcessBindingVO) {
  await bindingApi.disableBinding(binding.id)
  ElMessage.success('已停用')
  await loadAll()
}

async function handleDelete(binding: ProcessBindingVO) {
  await ElMessageBox.confirm('确定要删除该业务绑定吗？删除后该维度可以重新配置绑定', '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await bindingApi.deleteBinding(binding.id)
  ElMessage.success('已删除')
  await loadAll()
}
</script>

<template>
  <div class="binding-panel" v-loading="loading">
    <header class="binding-panel__header">
      <h2 class="binding-panel__title">业务绑定</h2>
      <p class="binding-panel__desc">
        按业务对象与操作类型配置生效的审批流程版本，全局绑定作为兜底，组织范围覆盖绑定优先于全局绑定。
      </p>
    </header>

    <el-tabs v-model="activeBizType">
      <el-tab-pane v-for="bt in BIZ_TYPE_OPTIONS" :key="bt.value" :label="bt.label" :name="bt.value">
        <div class="binding-op-selector">
          <span class="binding-op-selector__label">操作类型</span>
          <el-select v-model="activeOperationType" style="width: 160px">
            <el-option v-for="op in OPERATION_TYPE_OPTIONS" :key="op.value" :label="op.label" :value="op.value" />
          </el-select>
        </div>

        <template v-for="group in [groupFor(bt.value, activeOperationType)]" :key="group.operationType">
          <el-card class="binding-op-card" shadow="never">
            <template #header>
              <div class="binding-op-card__header">
                <span class="binding-op-card__title">{{ group.label }}</span>
                <div v-if="canEdit">
                  <el-button
                    v-if="!group.global"
                    size="small"
                    @click="openCreateDialog(bt.value, group.operationType, 'GLOBAL')"
                  >
                    新建全局绑定
                  </el-button>
                  <el-tooltip
                    content="按指定组织覆盖上面的全局绑定，只对该组织（及未单独覆盖的下级组织）的这类操作生效，不改变其他组织仍使用的全局绑定"
                    placement="top"
                  >
                    <el-button
                      size="small"
                      type="primary"
                      @click="openCreateDialog(bt.value, group.operationType, 'ORG')"
                    >
                      新建组织范围覆盖绑定
                    </el-button>
                  </el-tooltip>
                </div>
              </div>
            </template>

            <el-table :data="rowsFor(group)" empty-text="暂无绑定" size="small">
              <el-table-column label="范围" width="180">
                <template #default="{ row }">
                  <el-tag v-if="(row as ProcessBindingVO).scopeType === 'GLOBAL'" type="info">全局</el-tag>
                  <span v-else>{{ orgName((row as ProcessBindingVO).scopeId) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="流程版本" min-width="260">
                <template #default="{ row }">
                  <span>{{ definitionLabel((row as ProcessBindingVO).definitionId) }}</span>
                  <el-tag
                    v-if="isDefinitionOffline((row as ProcessBindingVO).definitionId)"
                    type="danger"
                    size="small"
                    style="margin-left: 8px"
                  >
                    绑定指向的版本已下线
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="executionMode" label="执行模式" width="140" />
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="(row as ProcessBindingVO).enabled ? 'success' : 'info'">
                    {{ (row as ProcessBindingVO).enabled ? '启用' : '停用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column v-if="canEdit || canDelete" label="操作" width="260" fixed="right">
                <template #default="{ row }">
                  <el-button v-if="canEdit" link type="primary" @click="openSwitchDialog(row as ProcessBindingVO)">
                    切换版本
                  </el-button>
                  <el-button
                    v-if="canEdit && (row as ProcessBindingVO).enabled"
                    link
                    type="warning"
                    @click="handleDisable(row as ProcessBindingVO)"
                  >
                    停用
                  </el-button>
                  <el-button
                    v-if="canEdit && !(row as ProcessBindingVO).enabled"
                    link
                    type="success"
                    @click="handleEnable(row as ProcessBindingVO)"
                  >
                    启用
                  </el-button>
                  <el-button v-if="canDelete" link type="danger" @click="handleDelete(row as ProcessBindingVO)">
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </template>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新建绑定' : '切换绑定版本'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="业务类型">
          <span>{{ bizTypeLabel }}</span>
        </el-form-item>
        <el-form-item label="操作类型">
          <span>{{ operationTypeLabel }}</span>
        </el-form-item>
        <el-form-item label="绑定范围">
          <el-radio-group v-model="form.scopeType" :disabled="dialogMode === 'switch'">
            <el-radio value="GLOBAL">全局</el-radio>
            <el-radio value="ORG">指定组织</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.scopeType === 'ORG'" label="所属组织" prop="scopeId">
          <el-tree-select
            v-model="form.scopeId"
            :data="orgTree"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            check-strictly
            :disabled="dialogMode === 'switch'"
            placeholder="请选择组织"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="流程模型" prop="modelId">
          <el-select v-model="form.modelId" placeholder="请选择流程模型" style="width: 100%" @change="handleModelChange">
            <el-option v-for="model in models" :key="model.id" :label="model.processName" :value="model.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="流程版本" prop="definitionId">
          <el-select
            v-model="form.definitionId"
            placeholder="请选择已发布版本"
            style="width: 100%"
            :disabled="!form.modelId"
            no-data-text="该模型尚无已发布版本"
          >
            <el-option
              v-for="version in publishedVersionsForForm"
              :key="version.id"
              :label="`v${version.version}（发布人 ${version.publishedBy ?? '-'}，${version.publishedTime}）`"
              :value="version.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="执行模式">
          <span>LEGACY_SYNC（同步执行）</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dialogSubmitting" @click="submitDialog">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.binding-panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

.binding-panel__header {
  margin-bottom: 16px;
}

.binding-panel__title {
  font-size: 15px;
  color: var(--color-ink);
  margin: 0 0 4px;
}

.binding-panel__desc {
  font-size: 12px;
  color: var(--color-text-secondary, #909399);
  margin: 0;
}

.binding-op-selector {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.binding-op-selector__label {
  font-size: 13px;
  color: var(--color-text-secondary, #909399);
}

.binding-op-card {
  margin-bottom: 16px;
}

.binding-op-card:last-child {
  margin-bottom: 0;
}

.binding-op-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.binding-op-card__title {
  font-weight: 600;
  color: var(--color-ink);
}

:deep(.el-table .el-button + .el-button) {
  margin-left: 6px;
}
</style>
