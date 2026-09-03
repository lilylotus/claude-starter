package cn.nihility.rbac.workflow.dto;

/**
 * 审批拒绝（驳回，直接终止流程）命令对象。
 *
 * @param taskId         审批任务 id（{@code tab_wf_approval_task.id}）
 * @param operatorId     操作人用户 id
 * @param remark         拒绝原因
 * @param idempotencyKey 幂等键，可为空
 */
public record RejectCommand(Long taskId, Long operatorId, String remark, String idempotencyKey) {
}
