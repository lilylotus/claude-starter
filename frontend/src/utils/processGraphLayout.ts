// 流程实例详情"审批流程"分级列表的层号计算（add-approval-remark-and-process-flowchart
// change design.md Decision 6，2026-09-17 修订版：改为按审批级别一级一级纵向展示的列表，
// 不再用 Vue Flow 画布做像素坐标定位）。
//
// 不追求通用的图布局算法，只做一次简单的拓扑分层：按 edges 做 Kahn 拓扑排序，每个节点的
// 层号取"全部前驱节点层号的最大值 + 1"，保证有依赖关系的节点一定分在更靠后的层。v1/v2
// 流程统一走这一套分层，不再区分"是否有服务端坐标"（ProcessGraphNodeVO.x/y 这次不用）。
import type { ProcessGraphEdgeVO, ProcessGraphNodeVO } from '@/types/workflow'

export interface ProcessGraphLevel {
  level: number
  nodes: ProcessGraphNodeVO[]
}

export function computeNodeLevels(nodes: ProcessGraphNodeVO[], edges: ProcessGraphEdgeVO[]): ProcessGraphLevel[] {
  const nodeIds = nodes.map((node) => node.id)
  const nodeIdSet = new Set(nodeIds)
  const adjacency = new Map<string, string[]>()
  const inDegree = new Map<string, number>()
  nodeIds.forEach((id) => {
    adjacency.set(id, [])
    inDegree.set(id, 0)
  })
  edges.forEach((edge) => {
    if (!nodeIdSet.has(edge.source) || !nodeIdSet.has(edge.target)) return
    adjacency.get(edge.source)!.push(edge.target)
    inDegree.set(edge.target, (inDegree.get(edge.target) ?? 0) + 1)
  })

  const level = new Map<string, number>()
  const remaining = new Map(inDegree)
  const queue: string[] = []
  nodeIds.forEach((id) => {
    if ((remaining.get(id) ?? 0) === 0) {
      queue.push(id)
      level.set(id, 0)
    }
  })

  const visited = new Set<string>()
  let head = 0
  while (head < queue.length) {
    const id = queue[head]
    head += 1
    if (visited.has(id)) continue
    visited.add(id)
    const currentLevel = level.get(id) ?? 0
    for (const next of adjacency.get(id) ?? []) {
      level.set(next, Math.max(level.get(next) ?? 0, currentLevel + 1))
      remaining.set(next, (remaining.get(next) ?? 1) - 1)
      if ((remaining.get(next) ?? 0) <= 0 && !visited.has(next)) {
        queue.push(next)
      }
    }
  }

  // 兜底：理论上审批流程图是 DAG，不会有环；但如果数据异常导致某些节点未被拓扑遍历到
  // （如快照解析出的孤立节点），统一放在第 0 层，避免分级列表漏掉节点。
  nodeIds.forEach((id) => {
    if (!level.has(id)) level.set(id, 0)
  })

  const byLevel = new Map<number, ProcessGraphNodeVO[]>()
  nodes.forEach((node) => {
    const l = level.get(node.id) ?? 0
    if (!byLevel.has(l)) byLevel.set(l, [])
    byLevel.get(l)!.push(node)
  })

  return Array.from(byLevel.entries())
    .sort(([a], [b]) => a - b)
    .map(([l, levelNodes]) => ({ level: l, nodes: levelNodes }))
}
