package cn.nihility.rbac.workflow.constant;

/**
 * 业务执行状态（production-approval-lifecycle change design.md 第198行，tasks.md 7.3）。
 * 仅 {@link ExecutionMode#RELIABLE_ASYNC} 申请使用，驱动 {@code tab_approval_request
 * .execution_status} 与 {@code tab_wf_business_execution.execution_status} 两列；
 * {@link ExecutionMode#LEGACY_SYNC} 申请该列恒为空。
 */
public final class ExecutionStatus {

    /** 尚未就绪：申请尚未进入终审通过状态，还不满足发起业务执行的条件。 */
    public static final String NOT_READY = "NOT_READY";

    /** 待执行：审批已通过，触发事件已发布，等待消费者领取执行。 */
    public static final String PENDING = "PENDING";

    /** 执行中：消费者已领取事件正在执行业务写操作。 */
    public static final String EXECUTING = "EXECUTING";

    /** 执行成功：业务写操作已落库生效，终态。 */
    public static final String SUCCEEDED = "SUCCEEDED";

    /** 可重试失败：遇到暂时性异常（如连接/锁超时），按原事件重试，非终态。 */
    public static final String FAILED_RETRYABLE = "FAILED_RETRYABLE";

    /** 人工处理失败：目标状态已变化、唯一键冲突、权限收紧等非暂时性失败，终态，不自动重试，
     *  需重新走审批流程产生新快照才能改变业务内容。 */
    public static final String FAILED_MANUAL = "FAILED_MANUAL";

    /** 工具类不允许实例化。 */
    private ExecutionStatus() {
    }
}
