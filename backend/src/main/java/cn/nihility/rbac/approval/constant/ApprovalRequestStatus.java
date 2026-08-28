package cn.nihility.rbac.approval.constant;

/**
 * 审批申请生命周期状态常量。
 */
public final class ApprovalRequestStatus {

    /** 待审批。 */
    public static final int PENDING = 1000;

    /** 已通过。 */
    public static final int APPROVED = 2000;

    /** 已拒绝。 */
    public static final int REJECTED = 3000;

    /** 已撤回。 */
    public static final int CANCELLED = 4000;

    /** 工具类不允许实例化。 */
    private ApprovalRequestStatus() {
    }
}
