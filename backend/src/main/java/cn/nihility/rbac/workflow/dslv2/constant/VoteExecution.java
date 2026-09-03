package cn.nihility.rbac.workflow.dslv2.constant;

/**
 * DSL v2 会签执行方式：候选人是否并行同时处理，还是逐个顺序处理
 * （production-approval-lifecycle change design.md Decision 7）。
 */
public enum VoteExecution {

    /** 并行：候选人同时收到任务，独立各自处理。 */
    PARALLEL,

    /** 串行：候选人按顺序逐个处理，前一人完成后下一人才收到任务。 */
    SEQUENTIAL
}
