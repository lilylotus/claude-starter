package cn.nihility.rbac.workflow.assignee;

/**
 * 审批人解析上下文，{@link AssigneeResolver#resolve(AssigneeResolveContext)} 的入参。
 * {@code applicantId}/{@code applicantOrgId} 读取自 {@code tab_wf_process_instance} 发起时
 * 快照，不实时重查申请人当前组织（workflow-approval-engine change design.md Decision 6）。
 * 流程实例上下文缺失（如流程并非经由 {@code WorkflowService.start} 启动、无法定位对应的
 * {@code tab_wf_process_instance} 行）时，{@code processInstanceId}/{@code applicantId}/
 * {@code applicantOrgId} 均可能为 {@code null}，依赖 {@code applicant} 上下文的解析器
 * （{@code INITIATOR}/{@code APPLICANT_DEPT_LEADER} 等）此时应返回空集合，交由空审批人策略
 * 兜底，不抛出异常。
 *
 * @param processInstanceId 流程实例 id（{@code tab_wf_process_instance.id}），可为空
 * @param nodeId             当前节点 id
 * @param assigneeValue      节点审批人规则的 {@code assignee_value}，按解析器类型解释，可为空
 * @param applicantId        发起人用户 id，可为空
 * @param applicantOrgId     发起人所属组织 id，可为空
 */
public record AssigneeResolveContext(
        Long processInstanceId,
        String nodeId,
        String assigneeValue,
        Long applicantId,
        Long applicantOrgId) {
}
