package cn.nihility.rbac.workflow.constant;

/**
 * 流程实例状态，对应 {@code tab_wf_process_instance.status}。
 */
public final class ProcessInstanceStatus {

    /** 运行中。 */
    public static final String RUNNING = "RUNNING";

    /** 已通过。 */
    public static final String APPROVED = "APPROVED";

    /** 已拒绝。 */
    public static final String REJECTED = "REJECTED";

    /** 已撤回。 */
    public static final String WITHDRAWN = "WITHDRAWN";

    /** 已终止（如空审批人策略为 REJECT 触发的系统终止）。 */
    public static final String TERMINATED = "TERMINATED";

    /**
     * 工具类不允许实例化。
     */
    private ProcessInstanceStatus() {
    }
}
