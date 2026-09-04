package cn.nihility.rbac.workflow.constant;

/**
 * 审批轨迹动作类型常量，对应 {@code tab_wf_approval_record.action}。
 */
public final class ApprovalAction {

    /** 提交申请，启动流程。 */
    public static final String SUBMIT = "SUBMIT";

    /** 审批通过。 */
    public static final String APPROVE = "APPROVE";

    /** 审批拒绝（驳回，直接终止流程）。 */
    public static final String REJECT = "REJECT";

    /** 反对票（阈值制会签节点专用，只计入反对票数，不立即终止流程，
     *  production-approval-lifecycle change design.md 第7节/tasks.md 6.3）。 */
    public static final String DISAGREE = "DISAGREE";

    /** 退回历史节点。 */
    public static final String RETURN = "RETURN";

    /** 转办。 */
    public static final String TRANSFER = "TRANSFER";

    /** 委派。 */
    public static final String DELEGATE = "DELEGATE";

    /** 加签。 */
    public static final String ADD_SIGN = "ADD_SIGN";

    /** 撤回。 */
    public static final String WITHDRAW = "WITHDRAW";

    /** 终止（如空审批人策略为 REJECT 时系统自动终止）。 */
    public static final String TERMINATE = "TERMINATE";

    /** 运维重分配审批人（DSL v2 专用，空审批人 BLOCK 策略触发后的恢复操作）。 */
    public static final String REASSIGN = "REASSIGN";

    /**
     * 工具类不允许实例化。
     */
    private ApprovalAction() {
    }
}
