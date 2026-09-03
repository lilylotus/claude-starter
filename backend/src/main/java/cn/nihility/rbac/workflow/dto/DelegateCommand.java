package cn.nihility.rbac.workflow.dto;

/**
 * 委派命令对象，受托人完成后归还原处理人（Flowable 原生委派语义）。
 *
 * @param taskId         审批任务 id（{@code tab_wf_approval_task.id}）
 * @param operatorId     操作人用户 id（原处理人）
 * @param targetUserId   受托人用户 id
 * @param remark         委派原因，可为空
 * @param idempotencyKey 幂等键，可为空
 */
public record DelegateCommand(Long taskId, Long operatorId, Long targetUserId, String remark,
        String idempotencyKey) {
}
