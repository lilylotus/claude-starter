package cn.nihility.rbac.workflow.dslv2.constant;

/**
 * DSL v2 审批节点会签投票规则，与执行方式（{@link VoteExecution}）分开配置
 * （production-approval-lifecycle change design.md Decision 7）。
 */
public enum VoteMode {

    /** 全部候选人同意才通过，阈值 K = N。 */
    ALL,

    /** 任一候选人同意即通过，阈值 K = 1。 */
    ANY,

    /** 同意票数达到配置比例即通过，阈值 K = ceil(N × percent / 100)。 */
    PERCENT
}
