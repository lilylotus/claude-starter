package cn.nihility.rbac.workflow.dto;

/**
 * 撤回流程实例命令对象。
 *
 * @param processInstanceId 流程实例 id（{@code tab_wf_process_instance.id}）
 * @param operatorId        操作人用户 id，须为流程发起人
 * @param remark            撤回原因，可为空
 * @param idempotencyKey    幂等键，可为空
 */
public record WithdrawCommand(Long processInstanceId, Long operatorId, String remark, String idempotencyKey) {
}
