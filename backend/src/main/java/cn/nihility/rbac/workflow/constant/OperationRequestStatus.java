package cn.nihility.rbac.workflow.constant;

/**
 * 操作幂等记录状态，对应 {@code tab_wf_operation_request.status}。
 */
public final class OperationRequestStatus {

    /** 已成功执行。 */
    public static final String SUCCESS = "SUCCESS";

    /** 执行失败（当前实现依赖事务回滚保证失败请求不留痕，本状态预留供未来扩展使用）。 */
    public static final String FAILED = "FAILED";

    /**
     * 工具类不允许实例化。
     */
    private OperationRequestStatus() {
    }
}
