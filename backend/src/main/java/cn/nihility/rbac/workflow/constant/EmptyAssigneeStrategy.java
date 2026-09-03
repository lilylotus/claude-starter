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
    REJECT,

    /** 阻塞：节点/会签实例停在无处理人的待分配状态，不自动通过，需运维重分配后才能继续
     *  （production-approval-lifecycle change design.md Decision 5，DSL v2 专用，v1 编译器
     *  从不产生该取值）。 */
    BLOCK,

    /** 兜底角色：解析结果为空时改用 {@code fallback_role_code} 指定的角色；仍为空按
     *  {@link #BLOCK} 处理（production-approval-lifecycle change design.md Decision 5，
     *  DSL v2 专用，v1 编译器从不产生该取值）。 */
    FALLBACK_ROLE
}
