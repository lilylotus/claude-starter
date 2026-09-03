// Workflow JSON DSL 前端基础结构校验，规则与后端权威校验器
// cn.nihility.rbac.workflow.designer.compiler.ProcessModelDslValidator 保持一致
// （workflow-approval-engine change design.md Decision 9 / specs/workflow-process-designer
// "发布前结构与业务规则的强制校验"Requirement）：唯一开始节点、至少一个结束节点、节点 id
// 唯一、边引用的节点必须存在、孤立节点检测、开始到结束存在可达路径、条件节点存在兜底默认边、
// 审批节点必填字段完整。全部错误一次性收集返回，不是发现第一个就短路，方便发布前一次性
// 展示全部问题定位信息。
import type { ConditionOperator, EdgeDsl, ProcessModelDsl, ProcessNodeDsl } from '@/types/workflow'

const ALLOWED_OPERATORS: ConditionOperator[] = ['EQ', 'NE', 'GT', 'GTE', 'LT', 'LTE']

export function validateProcessModelDsl(dsl: ProcessModelDsl): string[] {
  const errors: string[] = []
  const nodes = dsl.nodes ?? []
  const edges = dsl.edges ?? []

  if (nodes.length === 0) {
    return ['流程模型不能为空，至少需要包含节点定义']
  }

  const nodeById = new Map<string, ProcessNodeDsl>()
  for (const node of nodes) {
    if (!node.id) {
      errors.push('存在未设置 id 的节点')
      continue
    }
    if (nodeById.has(node.id)) {
      errors.push(`节点 id 重复：${node.id}`)
      continue
    }
    nodeById.set(node.id, node)
  }

  const startCount = nodes.filter((n) => n.type === 'START').length
  if (startCount !== 1) {
    errors.push(`流程模型必须有且仅有一个开始节点，当前数量：${startCount}`)
  }
  const endCount = nodes.filter((n) => n.type === 'END').length
  if (endCount < 1) {
    errors.push('流程模型至少需要一个结束节点')
  }

  for (const edge of edges) {
    if (!edge.from || !nodeById.has(edge.from)) {
      errors.push(`连线引用了不存在的起始节点：${edge.from}`)
    }
    if (!edge.to || !nodeById.has(edge.to)) {
      errors.push(`连线引用了不存在的目标节点：${edge.to}`)
    }
  }

  // 节点/连线引用本身有问题时，后续可达性与条件校验容易产生误报，提前返回。
  if (errors.length > 0) {
    return errors
  }

  const outgoing = new Map<string, EdgeDsl[]>()
  nodeById.forEach((_node, id) => outgoing.set(id, []))
  const hasIncoming = new Set<string>()
  for (const edge of edges) {
    outgoing.get(edge.from)?.push(edge)
    hasIncoming.add(edge.to)
  }

  for (const node of nodes) {
    if (node.type !== 'START' && !hasIncoming.has(node.id)) {
      errors.push(`节点 ${node.id} 未被任何连线指向，属于孤立节点`)
    }
  }

  if (startCount === 1) {
    errors.push(...validateReachability(nodes, nodeById, outgoing))
  }

  errors.push(...validateConditionNodes(nodes, outgoing))
  errors.push(...validateApprovalNodes(nodes))

  return errors
}

function validateReachability(
  nodes: ProcessNodeDsl[],
  nodeById: Map<string, ProcessNodeDsl>,
  outgoing: Map<string, EdgeDsl[]>,
): string[] {
  const startNode = nodes.find((n) => n.type === 'START')
  if (!startNode) return []
  const visited = new Set<string>([startNode.id])
  const queue: string[] = [startNode.id]
  let reachedEnd = false
  while (queue.length > 0) {
    const current = queue.shift() as string
    if (nodeById.get(current)?.type === 'END') {
      reachedEnd = true
    }
    for (const edge of outgoing.get(current) ?? []) {
      if (!visited.has(edge.to)) {
        visited.add(edge.to)
        queue.push(edge.to)
      }
    }
  }
  return reachedEnd ? [] : ['从开始节点无法到达任何结束节点']
}

function validateConditionNodes(nodes: ProcessNodeDsl[], outgoing: Map<string, EdgeDsl[]>): string[] {
  const errors: string[] = []
  for (const node of nodes) {
    if (node.type !== 'CONDITION') continue
    const out = outgoing.get(node.id) ?? []
    const hasDefault = out.some((edge) => !edge.condition)
    if (!hasDefault) {
      errors.push(`条件节点 ${node.id} 缺少默认分支（未携带 condition 的兜底出边）`)
    }
    for (const edge of out) {
      const condition = edge.condition
      if (!condition) continue
      const location = `边 ${edge.from}->${edge.to}`
      if (!condition.field) {
        errors.push(`${location} 的条件缺少字段 field`)
      }
      if (!condition.operator || !ALLOWED_OPERATORS.includes(condition.operator)) {
        errors.push(`${location} 的比较符不在允许范围内（仅支持 EQ/NE/GT/GTE/LT/LTE）：${condition.operator}`)
      }
      if (condition.value === null || condition.value === undefined || condition.value === '') {
        errors.push(`${location} 的条件缺少比较值 value`)
      }
    }
  }
  return errors
}

function validateApprovalNodes(nodes: ProcessNodeDsl[]): string[] {
  const errors: string[] = []
  for (const node of nodes) {
    if (node.type !== 'APPROVAL') continue
    const location = `审批节点 ${node.id}`
    if (!node.assigneeType) {
      errors.push(`${location} 未配置审批人来源 assigneeType`)
    } else if ((node.assigneeType === 'ROLE' || node.assigneeType === 'USER') && !node.assigneeValue) {
      errors.push(`${location} 的审批人来源 ${node.assigneeType} 缺少必填的 assigneeValue`)
    }
    if (node.approvalMode === 'PERCENT') {
      const percent = node.approvalPercent
      if (percent === null || percent === undefined || percent < 1 || percent > 100) {
        errors.push(`${location} 的会签比例 approvalPercent 必须在 1~100 之间`)
      }
    }
    if (!node.emptyAssigneeStrategy) {
      errors.push(`${location} 未配置空审批人策略 emptyAssigneeStrategy`)
    }
  }
  return errors
}
