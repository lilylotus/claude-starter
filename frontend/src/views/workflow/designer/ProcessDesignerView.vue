<script setup lang="ts">
// 流程设计器画布（workflow-approval-engine change design.md Decision 11，tasks.md 11.5）。
// 基于 @vue-flow/core：左侧拖拽节点面板（开始/审批/条件/结束四种业务语言节点，不暴露
// BPMN 术语），中间画布，右侧抽屉按选中节点类型渲染 NodePropertyPanel.vue。
//
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import { VueFlow, useVueFlow, type Connection, type NodeMouseEvent, type EdgeMouseEvent } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
// 注：@vue-flow/background 这个版本没有独立发布 dist/style.css（背景网格用内联 SVG
// pattern 渲染，不依赖外部样式表），无需在这里额外 import。
import * as workflowApi from '@/api/workflow'
import { useWorkflowDesignerStore, type DesignerNodeType } from '@/stores/workflowDesigner'
import { validateProcessModelDsl } from '@/utils/workflowValidation'
import { usePermission } from '@/composables/usePermission'
import StartNode from './nodes/StartNode.vue'
import ApprovalNode from './nodes/ApprovalNode.vue'
import ConditionNode from './nodes/ConditionNode.vue'
import EndNode from './nodes/EndNode.vue'
import NodePropertyPanel from './panels/NodePropertyPanel.vue'
import VersionHistoryDialog from '../process-model/VersionHistoryDialog.vue'
import type { EdgeConditionDsl } from '@/types/workflow'

const route = useRoute()
const router = useRouter()
const { hasPermission } = usePermission()

const designerStore = useWorkflowDesignerStore()
const { nodes, edges, selectedNode, selectedEdge, hasStartNode, processCode, processName, dirty } =
  storeToRefs(designerStore)

const canEdit = computed(() => hasPermission('WorkflowDesign:model:edit'))
const canPublish = computed(() => hasPermission('WorkflowDesign:model:publish'))
const canView = computed(() => hasPermission('WorkflowDesign:model:view'))

const modelIdNum = computed(() => {
  const raw = route.params.id
  const value = Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isFinite(value) && value > 0 ? value : null
})

const loading = ref(false)
const loadNotice = ref('')
const savingDraft = ref(false)
const publishing = ref(false)
const versionDialogVisible = ref(false)

const { project, screenToFlowCoordinate } = useVueFlow()
const flowWrapperRef = ref<HTMLDivElement | null>(null)

const PALETTE_ITEMS: Array<{ type: DesignerNodeType; label: string; hint: string }> = [
  { type: 'start', label: '开始', hint: '流程唯一入口，通常只需要一个' },
  { type: 'approval', label: '审批', hint: '配置审批人来源、会签模式等规则' },
  { type: 'condition', label: '条件', hint: '按字段条件分流，需保留一条默认分支' },
  { type: 'end', label: '结束', hint: '流程终点，可以有多个' },
]

// ---- 加载流程模型内容 ----

async function loadModel() {
  designerStore.reset()
  loadNotice.value = ''
  if (!modelIdNum.value) {
    loadNotice.value =
      '当前地址未指定流程模型 id，请从流程模型列表的新建入口创建草稿后进入设计器。'
    return
  }
  designerStore.modelId = modelIdNum.value

  loading.value = true
  try {
    const model = await workflowApi.getProcessModel(modelIdNum.value)
    if (model.modelJson) {
      designerStore.fromDsl(JSON.parse(model.modelJson))
    }
    designerStore.processCode = model.processCode ?? designerStore.processCode
    designerStore.processName = model.processName ?? designerStore.processName
    loading.value = false
    return
  } catch {
    // 详情读取失败时退回到最近一次发布快照，保留旧定义的可编辑恢复路径。
  }

  try {
    const versions = await workflowApi.listProcessModelVersions(modelIdNum.value)
    if (versions.length > 0) {
      designerStore.fromDsl(JSON.parse(versions[0].modelJsonSnapshot))
      loadNotice.value =
        `未能直接获取流程模型草稿内容（详情接口后端暂未提供），已改为加载最近一次发布版本 v${versions[0].version} ` +
        '的快照作为编辑起点；如果草稿内容与最近发布版本不一致，保存前请人工核对。'
    } else {
      loadNotice.value = '未查询到该流程模型 id 对应的任何已发布版本，也无法获取草稿详情，当前展示的是一块空白画布。'
    }
  } catch {
    loadNotice.value = '加载该流程模型内容失败（详情接口与版本历史接口均未返回可用数据），当前展示的是一块空白画布。'
  } finally {
    loading.value = false
  }
}

onMounted(loadModel)

// ---- 拖拽添加节点 ----

function onDragStart(event: DragEvent, type: DesignerNodeType) {
  if (type === 'start' && hasStartNode.value) {
    event.preventDefault()
    ElMessage.warning('流程模型只能有一个开始节点')
    return
  }
  event.dataTransfer?.setData('application/wf-node-type', type)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
}

function onDragOver(event: DragEvent) {
  event.preventDefault()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
}

function onDrop(event: DragEvent) {
  event.preventDefault()
  const type = event.dataTransfer?.getData('application/wf-node-type') as DesignerNodeType | ''
  if (!type) return
  if (type === 'start' && hasStartNode.value) {
    ElMessage.warning('流程模型只能有一个开始节点，已阻止添加')
    return
  }
  const bounds = flowWrapperRef.value?.getBoundingClientRect()
  const clientPosition = { x: event.clientX, y: event.clientY }
  const position = bounds
    ? project({ x: clientPosition.x - bounds.left, y: clientPosition.y - bounds.top })
    : screenToFlowCoordinate(clientPosition)
  designerStore.addNode(type, position)
}

// ---- 画布交互 ----

function onNodeClick({ node }: NodeMouseEvent) {
  designerStore.selectNode(node.id)
}

function onEdgeClick({ edge }: EdgeMouseEvent) {
  // 条件节点的出边通过源节点的属性面板统一编辑分支，点击边时直接选中源节点，
  // 避免同一份条件数据出现两个入口
  const sourceNode = nodes.value.find((n) => n.id === edge.source)
  if (sourceNode?.type === 'condition') {
    designerStore.selectNode(edge.source)
  } else {
    designerStore.selectEdge(edge.id)
  }
}

function onPaneClick() {
  designerStore.selectNode(null)
}

function onConnect(connection: Connection) {
  if (!connection.source || !connection.target || connection.source === connection.target) return
  designerStore.addEdge(connection.source, connection.target)
}

function nodeLabel(id: string): string {
  return nodes.value.find((n) => n.id === id)?.data?.label || id
}

// ---- 属性面板事件 ----

function handleUpdateNode(patch: Record<string, unknown>) {
  if (!selectedNode.value) return
  designerStore.updateNodeData(selectedNode.value.id, patch)
}

function handleUpdateEdgeCondition(payload: { edgeId: string; condition: EdgeConditionDsl | null }) {
  designerStore.updateEdgeCondition(payload.edgeId, payload.condition)
}

function handleAddBranch() {
  if (!selectedNode.value) return
  ElMessage.info('请在画布上从该条件节点拖出一条连线到目标节点，即可作为新的分支出现在下方列表中')
}

function handleRemoveBranch(edgeId: string) {
  designerStore.removeEdge(edgeId)
}

function handleDeleteSelectedNode() {
  if (!selectedNode.value) return
  const id = selectedNode.value.id
  designerStore.removeNode(id)
}

function handleDeleteSelectedEdge() {
  if (!selectedEdge.value) return
  designerStore.removeEdge(selectedEdge.value.id)
}

// ---- 保存草稿 / 发布 ----

function currentDsl() {
  return designerStore.toDsl()
}

async function handleSaveDraft() {
  if (!modelIdNum.value) {
    ElMessage.error('当前地址未指定流程模型 id，无法保存草稿')
    return
  }
  if (!processCode.value.trim() || !processName.value.trim()) {
    ElMessage.error('请先填写流程编码与流程名称')
    return
  }
  const dsl = currentDsl()
  const errors = validateProcessModelDsl(dsl)
  if (errors.length > 0) {
    await ElMessageBox.alert(errors.join('\n'), '结构校验存在问题（草稿仍会保存，发布前需修正）', {
      type: 'warning',
      confirmButtonText: '我知道了',
    })
  }
  savingDraft.value = true
  try {
    await workflowApi.saveProcessModelDraft(modelIdNum.value, JSON.stringify(dsl))
    designerStore.dirty = false
    ElMessage.success('草稿已保存')
  } finally {
    savingDraft.value = false
  }
}

async function handlePublish() {
  if (!modelIdNum.value) {
    ElMessage.error('当前地址未指定流程模型 id，无法发布')
    return
  }
  const dsl = currentDsl()
  const errors = validateProcessModelDsl(dsl)
  if (errors.length > 0) {
    await ElMessageBox.alert(errors.join('\n'), '结构校验未通过，无法发布', { type: 'error', confirmButtonText: '知道了' })
    return
  }
  try {
    await ElMessageBox.confirm('发布后将编译当前草稿为新的不可变版本并立即部署，确定发布吗？', '发布确认', {
      type: 'warning',
      confirmButtonText: '确定发布',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  publishing.value = true
  try {
    // 发布前先落一次最新草稿，避免"画布改动了但没点保存"就直接发布，导致发布内容与画布不一致
    await workflowApi.saveProcessModelDraft(modelIdNum.value, JSON.stringify(dsl))
    const result = await workflowApi.publishProcessModel(modelIdNum.value)
    designerStore.dirty = false
    ElMessage.success(`发布成功，新版本号 v${result.version}`)
  } finally {
    publishing.value = false
  }
}

function goBack() {
  router.push({ name: 'workflow-models' })
}
</script>

<template>
  <div class="designer-view">
    <header class="designer-toolbar">
      <div class="designer-toolbar__left">
        <el-button @click="goBack">返回列表</el-button>
        <el-input v-model="processCode" placeholder="流程编码，如 MASTER_DATA_APPROVAL" style="width: 220px" :disabled="!canEdit" />
        <el-input v-model="processName" placeholder="流程名称" style="width: 200px" :disabled="!canEdit" />
        <el-tag v-if="dirty" type="warning" size="small">有未保存的改动</el-tag>
      </div>
      <div class="designer-toolbar__right">
        <el-button v-if="canView" @click="versionDialogVisible = true">版本历史</el-button>
        <el-button v-if="canEdit" type="primary" plain :loading="savingDraft" @click="handleSaveDraft">保存草稿</el-button>
        <el-button v-if="canPublish" type="primary" :loading="publishing" @click="handlePublish">发布</el-button>
      </div>
    </header>

    <el-alert v-if="loadNotice" type="info" show-icon :closable="true" class="designer-notice" :title="loadNotice" />

    <div class="designer-body" v-loading="loading">
      <aside class="designer-palette">
        <p class="designer-palette__title">节点面板</p>
        <p class="designer-palette__hint">从下方拖拽到画布上添加节点</p>
        <div
          v-for="item in PALETTE_ITEMS"
          :key="item.type"
          class="designer-palette__item"
          :class="[`designer-palette__item--${item.type}`, { 'is-disabled': item.type === 'start' && hasStartNode }]"
          :draggable="canEdit"
          @dragstart="onDragStart($event, item.type)"
        >
          <span class="designer-palette__item-label">{{ item.label }}</span>
          <span class="designer-palette__item-hint">{{ item.hint }}</span>
        </div>
      </aside>

      <div ref="flowWrapperRef" class="designer-canvas" @dragover="onDragOver" @drop="onDrop">
        <VueFlow
          v-model:nodes="nodes"
          v-model:edges="edges"
          :nodes-draggable="canEdit"
          :nodes-connectable="canEdit"
          fit-view-on-init
          @node-click="onNodeClick"
          @edge-click="onEdgeClick"
          @pane-click="onPaneClick"
          @connect="onConnect"
        >
          <template #node-start="nodeProps">
            <StartNode v-bind="nodeProps" />
          </template>
          <template #node-approval="nodeProps">
            <ApprovalNode v-bind="nodeProps" />
          </template>
          <template #node-condition="nodeProps">
            <ConditionNode v-bind="nodeProps" />
          </template>
          <template #node-end="nodeProps">
            <EndNode v-bind="nodeProps" />
          </template>
          <Background :gap="16" />
          <Controls />
        </VueFlow>
      </div>
    </div>

    <el-drawer
      :model-value="!!selectedNode || !!selectedEdge"
      :title="selectedNode ? '节点属性' : '连线'"
      size="380px"
      @update:model-value="(v: boolean) => { if (!v) { designerStore.selectNode(null) } }"
    >
      <template v-if="selectedNode">
        <NodePropertyPanel
          :node="selectedNode"
          :outgoing-edges="designerStore.outgoingEdges(selectedNode.id)"
          :node-label="nodeLabel"
          :readonly="!canEdit"
          @update-node="handleUpdateNode"
          @update-edge-condition="handleUpdateEdgeCondition"
          @add-branch="handleAddBranch"
          @remove-branch="handleRemoveBranch"
        />
      </template>
      <template v-else-if="selectedEdge">
        <p class="designer-edge-panel__desc">
          {{ nodeLabel(selectedEdge.source) }} → {{ nodeLabel(selectedEdge.target) }}
        </p>
      </template>

      <template #footer>
        <el-button v-if="canEdit && selectedNode" type="danger" plain @click="handleDeleteSelectedNode">删除该节点</el-button>
        <el-button v-if="canEdit && selectedEdge" type="danger" plain @click="handleDeleteSelectedEdge">删除连线</el-button>
      </template>
    </el-drawer>

    <VersionHistoryDialog v-if="modelIdNum" v-model="versionDialogVisible" :model-id="modelIdNum" />
  </div>
</template>

<style scoped lang="scss">
.designer-view {
  display: flex;
  flex-direction: column;
  height: calc(100vh - var(--layout-header-height) - 48px);
  min-height: 560px;
}

.designer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.designer-toolbar__left,
.designer-toolbar__right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.designer-notice {
  margin-bottom: 12px;
}

.designer-body {
  flex: 1;
  display: flex;
  gap: 12px;
  min-height: 0;
}

.designer-palette {
  width: 200px;
  flex-shrink: 0;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 14px;
  overflow-y: auto;
}

.designer-palette__title {
  margin: 0 0 4px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-ink);
}

.designer-palette__hint {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.designer-palette__item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 12px;
  margin-bottom: 10px;
  border: 1px dashed var(--color-border-strong);
  border-radius: var(--radius-sm);
  cursor: grab;
  background: var(--color-canvas);
  transition: border-color 0.15s ease;

  &:hover {
    border-color: var(--color-primary);
  }

  &.is-disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.designer-palette__item-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-ink);
}

.designer-palette__item-hint {
  font-size: 11px;
  color: var(--color-text-tertiary);
}

.designer-canvas {
  flex: 1;
  min-width: 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--color-canvas);
}

.designer-edge-panel__desc {
  font-size: 13px;
  color: var(--color-ink);
}
</style>
