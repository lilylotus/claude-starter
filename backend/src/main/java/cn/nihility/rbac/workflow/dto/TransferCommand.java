package cn.nihility.rbac.workflow.dto;

/**
 * 转办命令对象。
 *
 * @param taskId         审批任务 id（{@code tab_wf_approval_task.id}）
 * @param operatorId     操作人用户 id（原处理人）
 * @param targetUserId   新处理人用户 id
 * @param remark         转办原因，可为空
 * @param idempotencyKey 幂等键，可为空
 */
public record TransferCommand(Long taskId, Long operatorId, Long targetUserId, String remark,
        String idempotencyKey) {
}
