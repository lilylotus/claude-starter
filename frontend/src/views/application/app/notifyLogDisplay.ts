const NOTIFY_DATA_TYPE_LABELS: Readonly<Record<string, string>> = {
  ORG: '组织',
  USER: '用户',
  POSITION: '任职',
  APP: '应用',
  ROLE: '角色',
}

/** 将通知日志的数据类型编码转换为中文；未知编码保留原值。 */
export function formatNotifyDataType(dataType: string | null | undefined): string {
  if (!dataType) return '-'
  return NOTIFY_DATA_TYPE_LABELS[dataType] ?? dataType
}

/** 将通知对象显示为“id（名称）”；名称不可用时保留 id。 */
export function formatNotifyBiz(
  bizId: number | null | undefined,
  bizName: string | null | undefined,
): string {
  if (bizId === null || bizId === undefined) return '-'
  return bizName ? `${bizId}（${bizName}）` : String(bizId)
}
