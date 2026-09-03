package cn.nihility.rbac.workflow.constant;

/**
 * 审批任务状态，对应 {@code tab_wf_approval_task.status}。
 */
public final class TaskStatus {

    /** 待处理（含候选组未认领）。 */
    public static final String PENDING = "PENDING";

    /** 候选组任务已被认领。 */
    public static final String CLAIMED = "CLAIMED";

    /** 已完成（通过/拒绝/自动跳过）。 */
    public static final String COMPLETED = "COMPLETED";

    /** 已转办（原任务作废，处理人变更后仍视为同一条待办，此状态用于历史记录展示）。 */
    public static final String TRANSFERRED = "TRANSFERRED";

    /** 已退回。 */
    public static final String RETURNED = "RETURNED";

    /** 已取消（如 DSL v2 会签哨兵分支被真实候选人替换后作废，不计入票数）。 */
    public static final String CANCELLED = "CANCELLED";

    /**
     * 工具类不允许实例化。
     */
    private TaskStatus() {
    }
}
