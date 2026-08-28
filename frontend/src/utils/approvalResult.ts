import { ElMessage } from 'element-plus'
import type { WriteOperationResult } from '@/types/approval'

// 组织/用户/任职/应用四个管理页面新增/编辑/启用/停用/删除操作调用接口后共用的响应分流逻辑
// （add-master-data-approval-workflow change design.md Decision 9 / tasks.md 6.6）：
// - approvalEnabled=true：展示"已提交审批"提示，不使用响应里的数据、不刷新本地列表——
//   业务数据在审批通过前不会变化，此时刷新列表只会拿到与操作前一致的旧数据，白白发请求
// - approvalEnabled=false：展示调用方传入的"直接生效"提示文案，行为与本 change 之前完全
//   一致；返回 true 告知调用方需要刷新本地列表/表格（业务数据已经真正落库）
//
// 返回值：是否需要刷新本地列表（即 approvalEnabled 是否为 false）
export function notifyWriteResult(result: WriteOperationResult<unknown>, effectiveMessage: string): boolean {
  if (result.approvalEnabled) {
    ElMessage.success('已提交审批，等待审批通过后生效')
    return false
  }
  ElMessage.success(effectiveMessage)
  return true
}
