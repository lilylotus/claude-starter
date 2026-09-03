package cn.nihility.rbac.workflow.constant;

/**
 * 节点审批模式，对应 {@code tab_wf_node_assignee_rule.approval_mode}。
 */
public enum ApprovalMode {

    /** 单人审批（含候选组，任一候选人处理即完成）。 */
    SINGLE,

    /** 会签，全部候选人通过才算通过，任一驳回立即终止。 */
    AND,

    /** 或签，任一候选人通过即算通过。 */
    OR,

    /** 按比例通过，达到 {@code approval_percent} 配置的比例即算通过。 */
    PERCENT
}
