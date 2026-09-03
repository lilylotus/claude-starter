package cn.nihility.rbac.workflow.dto;

/**
 * 审批通过命令对象。
 *
 * @param taskId         审批任务 id（{@code tab_wf_approval_task.id}）
 * @param operatorId     操作人用户 id
 * @param remark         处理意见，可为空
 * @param idempotencyKey 幂等键，可为空
 */
public record ApproveCommand(Long taskId, Long operatorId, String remark, String idempotencyKey) {
}
