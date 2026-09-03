package cn.nihility.rbac.workflow.dto;

/**
 * 退回历史节点命令对象。
 *
 * @param taskId         当前审批任务 id（{@code tab_wf_approval_task.id}）
 * @param operatorId     操作人用户 id
 * @param targetNodeId   退回目标节点 id，须为已配置 {@code allow_return=true} 的历史节点
 * @param remark         退回原因，可为空
 * @param idempotencyKey 幂等键，可为空
 */
public record ReturnTaskCommand(Long taskId, Long operatorId, String targetNodeId, String remark,
        String idempotencyKey) {
}
