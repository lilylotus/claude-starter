package cn.nihility.rbac.workflow.dto;

/**
 * 反对（阈值制会签节点专用反对票）命令对象（production-approval-lifecycle change design.md
 * 第7节，tasks.md 6.3）。
 *
 * @param taskId         审批任务 id（{@code tab_wf_approval_task.id}）
 * @param operatorId     操作人用户 id
 * @param remark         处理意见，可为空
 * @param idempotencyKey 幂等键，可为空
 */
public record DisagreeCommand(Long taskId, Long operatorId, String remark, String idempotencyKey) {
}
