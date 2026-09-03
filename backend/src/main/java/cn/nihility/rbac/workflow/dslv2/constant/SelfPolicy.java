package cn.nihility.rbac.workflow.dslv2.constant;

/**
 * DSL v2 审批节点自审处理策略，默认 {@link #EXCLUDE}（production-approval-lifecycle
 * change design.md Decision 5）。
 */
public enum SelfPolicy {

    /** 排除：候选人中若包含流程发起人本人，将其剔除；剔除后候选人集合为空则按该节点的
     *  {@code emptyPolicy} 处理。 */
    EXCLUDE,

    /** 允许：不剔除发起人本人，允许自审；仅当发布审核显式放行该配置时才允许发布。 */
    ALLOW
}
