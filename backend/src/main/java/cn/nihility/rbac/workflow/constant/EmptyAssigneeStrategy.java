package cn.nihility.rbac.workflow.constant;

/**
 * 空审批人（含"审批人为发起人本人且不允许自审"场景）处理策略，对应
 * {@code tab_wf_node_assignee_rule.empty_assignee_strategy}。
 */
public enum EmptyAssigneeStrategy {

    /** 转配置的流程管理员角色。 */
    TO_WORKFLOW_ADMIN,

    /** 自动完成该节点，流程进入下一节点。 */
    AUTO_SKIP,

    /** 终止流程并记录失败原因。 */
    REJECT
}
