package cn.nihility.rbac.workflow.dto;

/**
 * 运维强制终止流程实例命令对象（production-approval-lifecycle change design.md 第7节
 * "terminate：独立运维权限和必填原因，结束流程并取消任务，不执行主数据变更"，tasks.md 6.8）。
 *
 * @param processInstanceId 流程实例 id（{@code tab_wf_process_instance.id}）
 * @param operatorId         操作人用户 id（运维人员，不要求是流程发起人/参与者，权限由
 *                            {@code WorkflowDesign:instance:terminate} 独立权限点控制）
 * @param reason              终止原因，必填
 * @param idempotencyKey      幂等键，可为空
 */
public record TerminateCommand(Long processInstanceId, Long operatorId, String reason, String idempotencyKey) {
}
