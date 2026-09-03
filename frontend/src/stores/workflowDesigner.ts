import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type {
  ApprovalNodeDsl,
  AssigneeType,
  ApprovalMode,
  EdgeConditionDsl,
  EmptyAssigneeStrategy,
  ProcessModelDsl,
  ProcessNodeDsl,
} from '@/types/workflow'

// 画布上四种自定义节点类型 key（对应 Vue Flow node.type，用于选取
// nodes/<Type>Node.vue 自定义节点模板），与 DSL 的 START/APPROVAL/CONDITION/END
// 判别字段大写字面量一一对应，见下方 NODE_TYPE_TO_DSL/DSL_TYPE_TO_NODE。
export type DesignerNodeType = 'start' | 'approval' | 'condition' | 'end'

// 画布节点承载的业务数据：开始/结束/条件节点仅使用 label；审批节点额外携带
// tab_wf_node_assignee_rule 对应的全部字段，供 NodePropertyPanel.vue 双向编辑。
export interface DesignerNodeData {
  label: string
  assigneeType?: AssigneeType | null
  assigneeValue?: string | null
  approvalMode?: ApprovalMode | null
  approvalPercent?: number | null
  emptyAssigneeStrategy?: EmptyAssigneeStrategy | null
  allowSelfApproval?: boolean
  allowTransfer?: boolean
  allowDelegate?: boolean
  allowAddSign?: boolean
  allowReturn?: boolean
}

// 画布连线承载的业务数据：仅从"条件"节点出发的边才有意义，为空表示无条件流转
// （兜底默认分支）。
export interface DesignerEdgeData {
  condition?: EdgeConditionDsl | null
}

// 不直接复用 @vue-flow/core 的泛型 Node<Data>/Edge<Data> 类型：GraphNode/Node 的多层
// 泛型互相引用在这里会触发 vue-tsc "Type instantiation is excessively deep" 报错
// （TS2589）。改为定义结构上兼容的最小接口——只声明 Vue Flow 渲染/v-model 真正需要读写
// 的字段（id/type/position/data/label，边则是 id/source/target/data），
// type/data/label 都放宽为可选以同时兼容 Node（输入 prop，字段基本可选）与
// GraphNode（update:nodes 事件回传，字段更全但同样结构兼容）两个方向的赋值。
export interface DesignerNode {
  id: string
  type?: DesignerNodeType
  position: { x: number; y: number }
  data?: DesignerNodeData
  label?: string
}

export interface DesignerEdge {
  id: string
  source: string
  target: string
  data?: DesignerEdgeData
}

const NODE_TYPE_LABEL: Record<DesignerNodeType, string> = {
  start: '开始',
  approval: '审批',
  condition: '条件',
  end: '结束',
}

const DSL_TYPE_TO_NODE_TYPE: Record<string, DesignerNodeType> = {
  START: 'start',
  APPROVAL: 'approval',
  CONDITION: 'condition',
  END: 'end',
}

let nodeSeq = 0
let edgeSeq = 0

// 生成画布内部唯一的节点/连线 id；节点 id 同时也是发布后写入 BPMN userTask 的 id
// （tab_wf_node_assignee_rule.node_id），保持简短且不含特殊字符。
function generateNodeId(type: DesignerNodeType): string {
  nodeSeq += 1
  return `${type}_${Date.now().toString(36)}_${nodeSeq}`
}

function generateEdgeId(): string {
  edgeSeq += 1
  return `edge_${Date.now().toString(36)}_${edgeSeq}`
}

function defaultApprovalData(label: string): DesignerNodeData {
  return {
    label,
    assigneeType: null,
    assigneeValue: null,
    approvalMode: 'SINGLE',
    approvalPercent: null,
    emptyAssigneeStrategy: 'TO_WORKFLOW_ADMIN',
    allowSelfApproval: false,
    allowTransfer: false,
    allowDelegate: false,
    allowAddSign: false,
    allowReturn: false,
  }
}

// 画布编辑态 store：维护当前编辑流程模型的 nodes/edges（Vue Flow 格式）、选中节点、
// 脏标记，并提供与后端 Workflow JSON DSL 的双向转换（workflow-approval-engine change
// design.md Decision 9/11，tasks.md 11.4）。
export const useWorkflowDesignerStore = defineStore('workflowDesigner', () => {
  const modelId = ref<number | null>(null)
  const processCode = ref('')
  const processName = ref('')
  const nodes = ref<DesignerNode[]>([])
  const edges = ref<DesignerEdge[]>([])
  const selectedNodeId = ref<string | null>(null)
  const selectedEdgeId = ref<string | null>(null)
  // 脏标记：画布内容相对上一次保存草稿/加载是否有未保存改动
  const dirty = ref(false)

  const selectedNode = computed(() => nodes.value.find((n) => n.id === selectedNodeId.value) ?? null)
  const selectedEdge = computed(() => edges.value.find((e) => e.id === selectedEdgeId.value) ?? null)
  const hasStartNode = computed(() => nodes.value.some((n) => n.type === 'start'))

  function markDirty() {
    dirty.value = true
  }

  function reset() {
    modelId.value = null
    processCode.value = ''
    processName.value = ''
    nodes.value = []
    edges.value = []
    selectedNodeId.value = null
    selectedEdgeId.value = null
    dirty.value = false
  }

  function selectNode(id: string | null) {
    selectedNodeId.value = id
    selectedEdgeId.value = null
  }

  function selectEdge(id: string | null) {
    selectedEdgeId.value = id
    selectedNodeId.value = null
  }

  function addNode(type: DesignerNodeType, position: { x: number; y: number }): string {
    const id = generateNodeId(type)
    const label = NODE_TYPE_LABEL[type]
    const data: DesignerNodeData = type === 'approval' ? defaultApprovalData(label) : { label }
    nodes.value.push({ id, type, position, data, label })
    markDirty()
    return id
  }

  function removeNode(id: string) {
    nodes.value = nodes.value.filter((n) => n.id !== id)
    edges.value = edges.value.filter((e) => e.source !== id && e.target !== id)
    if (selectedNodeId.value === id) selectedNodeId.value = null
    markDirty()
  }

  function updateNodePosition(id: string, position: { x: number; y: number }) {
    const node = nodes.value.find((n) => n.id === id)
    if (!node) return
    node.position = position
  }

  function updateNodeData(id: string, patch: Partial<DesignerNodeData>) {
    const node = nodes.value.find((n) => n.id === id)
    if (!node) return
    node.data = { ...(node.data ?? { label: '' }), ...patch }
    if (patch.label !== undefined) node.label = patch.label
    markDirty()
  }

  function addEdge(source: string, target: string, condition?: EdgeConditionDsl | null): string {
    const id = generateEdgeId()
    edges.value.push({ id, source, target, data: { condition: condition ?? null } })
    markDirty()
    return id
  }

  function removeEdge(id: string) {
    edges.value = edges.value.filter((e) => e.id !== id)
    if (selectedEdgeId.value === id) selectedEdgeId.value = null
    markDirty()
  }

  function updateEdgeCondition(id: string, condition: EdgeConditionDsl | null) {
    const edge = edges.value.find((e) => e.id === id)
    if (!edge) return
    edge.data = { ...(edge.data ?? {}), condition }
    markDirty()
  }

  function outgoingEdges(nodeId: string): DesignerEdge[] {
    return edges.value.filter((e) => e.source === nodeId)
  }

  // ---- DSL <-> Vue Flow 双向转换 ----

  function nodeToDsl(node: DesignerNode): ProcessNodeDsl {
    const data = (node.data ?? { label: '' }) as DesignerNodeData
    switch (node.type as DesignerNodeType) {
      case 'start':
        return { id: node.id, type: 'START', name: data.label }
      case 'end':
        return { id: node.id, type: 'END', name: data.label }
      case 'condition':
        return { id: node.id, type: 'CONDITION', name: data.label }
      case 'approval':
      default:
        return {
          id: node.id,
          type: 'APPROVAL',
          name: data.label,
          assigneeType: data.assigneeType ?? null,
          assigneeValue: data.assigneeValue ?? null,
          approvalMode: data.approvalMode ?? null,
          approvalPercent: data.approvalPercent ?? null,
          emptyAssigneeStrategy: data.emptyAssigneeStrategy ?? null,
          allowSelfApproval: !!data.allowSelfApproval,
          allowTransfer: !!data.allowTransfer,
          allowDelegate: !!data.allowDelegate,
          allowAddSign: !!data.allowAddSign,
          allowReturn: !!data.allowReturn,
        } satisfies ApprovalNodeDsl
    }
  }

  // 导出当前画布内容为 Workflow JSON DSL，供保存草稿/发布前校验使用；不导出节点坐标
  // （DSL Schema 本身不携带 x/y，见 design.md Decision 9 示例）。
  function toDsl(): ProcessModelDsl {
    return {
      processCode: processCode.value,
      processName: processName.value,
      nodes: nodes.value.map(nodeToDsl),
      edges: edges.value.map((edge) => ({
        from: edge.source,
        to: edge.target,
        condition: edge.data?.condition ?? undefined,
      })),
    }
  }

  // 从 Workflow JSON DSL 恢复画布状态（新建/编辑草稿、只读查看历史版本快照均复用）。
  // DSL 不携带节点坐标，用 autoLayout 做一次按可达层级从左到右的简单自动布局，
  // 用户仍可在画布上自由拖拽调整，只是这份坐标不会随 DSL 一起持久化——见 autoLayout 注释。
  function fromDsl(dsl: ProcessModelDsl) {
    processCode.value = dsl.processCode ?? ''
    processName.value = dsl.processName ?? ''
    const layout = autoLayout(dsl)
    nodes.value = (dsl.nodes ?? []).map((n) => {
      const type = DSL_TYPE_TO_NODE_TYPE[n.type] ?? 'approval'
      const label = n.name ?? NODE_TYPE_LABEL[type]
      const data: DesignerNodeData =
        n.type === 'APPROVAL'
          ? {
              label,
              assigneeType: n.assigneeType ?? null,
              assigneeValue: n.assigneeValue ?? null,
              approvalMode: n.approvalMode ?? 'SINGLE',
              approvalPercent: n.approvalPercent ?? null,
              emptyAssigneeStrategy: n.emptyAssigneeStrategy ?? 'TO_WORKFLOW_ADMIN',
              allowSelfApproval: !!n.allowSelfApproval,
              allowTransfer: !!n.allowTransfer,
              allowDelegate: !!n.allowDelegate,
              allowAddSign: !!n.allowAddSign,
              allowReturn: !!n.allowReturn,
            }
          : { label }
      return {
        id: n.id,
        type,
        position: layout.get(n.id) ?? { x: 40, y: 40 },
        data,
        label,
      }
    })
    edges.value = (dsl.edges ?? []).map((e) => ({
      id: generateEdgeId(),
      source: e.from,
      target: e.to,
      data: { condition: e.condition ?? null },
    }))
    selectedNodeId.value = null
    selectedEdgeId.value = null
    dirty.value = false
  }

  return {
    modelId,
    processCode,
    processName,
    nodes,
    edges,
    selectedNodeId,
    selectedEdgeId,
    selectedNode,
    selectedEdge,
    hasStartNode,
    dirty,
    reset,
    selectNode,
    selectEdge,
    addNode,
    removeNode,
    updateNodePosition,
    updateNodeData,
    addEdge,
    removeEdge,
    updateEdgeCondition,
    outgoingEdges,
    toDsl,
    fromDsl,
    markDirty,
  }
})

// 简单的 BFS 分层自动布局：从开始节点出发按"距离"分层，同层节点纵向错开，
// 保证首次从 DSL 加载时节点不重叠、连线大致可读；无法从开始节点触达的孤立节点统一排在
// 最后一层之后，保证仍然有确定坐标可展示。
function autoLayout(dsl: ProcessModelDsl): Map<string, { x: number; y: number }> {
  const positions = new Map<string, { x: number; y: number }>()
  const nodes = dsl.nodes ?? []
  const edges = dsl.edges ?? []
  if (nodes.length === 0) return positions

  const outgoing = new Map<string, string[]>()
  nodes.forEach((n) => outgoing.set(n.id, []))
  edges.forEach((e) => outgoing.get(e.from)?.push(e.to))

  const levels = new Map<string, number>()
  const startNode = nodes.find((n) => n.type === 'START')
  if (startNode) {
    const queue: string[] = [startNode.id]
    levels.set(startNode.id, 0)
    while (queue.length > 0) {
      const current = queue.shift() as string
      const level = levels.get(current) ?? 0
      for (const next of outgoing.get(current) ?? []) {
        if (!levels.has(next) || (levels.get(next) as number) > level + 1) {
          levels.set(next, level + 1)
          queue.push(next)
        }
      }
    }
  }

  const maxLevel = levels.size > 0 ? Math.max(...Array.from(levels.values())) : 0
  let orphanLevel = maxLevel + 1
  nodes.forEach((n) => {
    if (!levels.has(n.id)) {
      levels.set(n.id, orphanLevel)
      orphanLevel += 1
    }
  })

  const columnGap = 220
  const rowGap = 120
  const countByLevel = new Map<number, number>()
  nodes.forEach((n) => {
    const level = levels.get(n.id) ?? 0
    const row = countByLevel.get(level) ?? 0
    countByLevel.set(level, row + 1)
    positions.set(n.id, { x: level * columnGap + 60, y: row * rowGap + 60 })
  })
  return positions
}
