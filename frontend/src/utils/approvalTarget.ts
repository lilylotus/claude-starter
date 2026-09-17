// "审批对象"展示名称解析工具：把 ApprovalRequestRow 里分散在 requestPayload/targetSnapshot
// 两个字段中的原始业务数据，按 bizType 解析成人类可读的展示文案，供"我的申请"、
// "待我审批"、申请详情弹窗三处 UI 共用，避免重复实现三份等价逻辑
// （approval-target-display-name change design.md Decision 2）。
import type { ApprovalRequestRow } from '@/types/approval'

// 从 Record<string, unknown> 里取一个字符串字段，非字符串类型一律当作缺失处理
function readString(source: Record<string, unknown> | null | undefined, key: string): string | undefined {
  const value = source?.[key]
  return typeof value === 'string' ? value : undefined
}

// 解析"审批对象"展示名称：
// - 数据来源优先取 targetSnapshot（UPDATE/ENABLE/DISABLE/DELETE 四类操作的目标记录当前值），
//   为空时退化到 requestPayload（CREATE 类型提交的新值），CREATE 场景 targetSnapshot 必为空，
//   这一行退化逻辑天然覆盖了全部五种操作类型，不需要再按操作类型额外分支判断。
// - USER/ORG/APP 三类业务对象统一用 name 字段展示。
// - POSITION 没有独立的 name 字段，用 userName/orgName 组合展示"用户姓名 - 组织名称"，
//   某一侧缺失时只展示存在的一侧。
// - 所有来源都取不到值时返回 '-'，与列表里其余空值字段的展示保持一致。
export function resolveApprovalTargetLabel(
  row: Pick<ApprovalRequestRow, 'bizType' | 'targetId' | 'requestPayload' | 'targetSnapshot'>,
): string {
  const source = (row.targetSnapshot ?? row.requestPayload) as Record<string, unknown> | null | undefined

  if (row.bizType === 'USER' || row.bizType === 'ORG' || row.bizType === 'APP') {
    return readString(source, 'name') ?? '-'
  }

  if (row.bizType === 'POSITION') {
    const userName = readString(source, 'userName')
    const orgName = readString(source, 'orgName')
    if (userName && orgName) return `${userName} - ${orgName}`
    if (userName) return userName
    if (orgName) return orgName
    return '-'
  }

  return '-'
}
