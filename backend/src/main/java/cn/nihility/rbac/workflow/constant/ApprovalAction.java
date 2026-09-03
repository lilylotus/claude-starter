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

    /**
     * 工具类不允许实例化。
     */
    private ApprovalAction() {
    }
}
