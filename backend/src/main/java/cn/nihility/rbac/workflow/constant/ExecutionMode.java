package cn.nihility.rbac.workflow.constant;

/**
 * 业务执行模式（production-approval-lifecycle change design.md Decision 6）。
 */
public final class ExecutionMode {

    /** 历史同步执行：审批通过接口返回前同步调用业务模块方法，行为与 workflow-approval-engine
     *  change 落地时完全一致。 */
    public static final String LEGACY_SYNC = "LEGACY_SYNC";

    /** 可靠异步执行：审批通过后经 Outbox 异步执行，最终同意与业务生效解耦（第 7 节，本轮
     *  未实现执行器本身，仅数据模型与绑定层面支持声明该模式）。 */
    public static final String RELIABLE_ASYNC = "RELIABLE_ASYNC";

    /** 工具类不允许实例化。 */
    private ExecutionMode() {
    }
}
