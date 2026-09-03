package cn.nihility.rbac.workflow.dslv2.constant;

/**
 * DSL v2 审批节点空审批人处理策略，默认 {@link #BLOCK}，与 v1
 * {@code EmptyAssigneeStrategy} 语义不同（v1 默认转流程管理员，v2 默认阻塞待人工处理，
 * production-approval-lifecycle change design.md Decision 5）。
 */
public enum EmptyPolicy {

    /** 阻塞：节点停在内部等待分配状态，不自动通过，需运维重分配审批人后才能继续。 */
    BLOCK,

    /** 兜底角色：解析结果为空时改用配置的兜底角色；兜底角色解析结果仍为空时按
     *  {@link #BLOCK} 处理。 */
    FALLBACK_ROLE
}
