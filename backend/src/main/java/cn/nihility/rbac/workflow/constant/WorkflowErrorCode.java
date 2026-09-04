package cn.nihility.rbac.workflow.constant;

/**
 * 审批引擎相关的专用业务错误码，供
 * {@link cn.nihility.rbac.common.exception.BusinessException#BusinessException(int, String)}
 * 使用（production-approval-lifecycle change design.md 第8节，tasks.md 6.2）。
 */
public final class WorkflowErrorCode {

    /** 幂等冲突：同一幂等键（{@code X-Request-Id}）被复用提交了不同的请求内容，拒绝处理，
     *  不能当作对首次请求的重试直接返回旧结果。 */
    public static final int IDEMPOTENCY_CONFLICT = 4090;

    /**
     * 工具类不允许实例化。
     */
    private WorkflowErrorCode() {
    }
}
