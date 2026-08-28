package cn.nihility.rbac.sync.notify.constant;

/**
 * 通知任务状态机状态常量，对应 {@code tab_app_notify_record.task_status} 列
 * （app-sync-changelog-pull change design.md Decision 6）：任务创建时为 {@link #PENDING}，
 * 被即时发送优化或调度器原子抢占后转为 {@link #PROCESSING}；发送成功转 {@link #SUCCESS}；
 * 网络异常/408/429/5xx 转 {@link #RETRY} 等待下次重试，达到最大尝试次数或遇到其他 4xx 转
 * {@link #DEAD}（死信，需管理端手动重推）。{@link #PROCESSING} 状态若超过租约仍未完成，
 * 调度器会把它当作到期任务重新抢占。
 */
public final class NotifyTaskStatus {

    /** 待发送：任务已落库，尚未被抢占处理。 */
    public static final String PENDING = "PENDING";

    /** 处理中：已被抢占，正在发起 HTTP 请求，持有 {@code lease_until} 租约。 */
    public static final String PROCESSING = "PROCESSING";

    /** 待重试：上一次尝试失败且判定为可重试，等待 {@code next_retry_time} 到期。 */
    public static final String RETRY = "RETRY";

    /** 成功：已收到 2xx 响应，终态。 */
    public static final String SUCCESS = "SUCCESS";

    /** 死信：达到最大尝试次数，或遇到不可重试的失败，终态，需管理端手动重推。 */
    public static final String DEAD = "DEAD";

    /**
     * 工具类不允许实例化。
     */
    private NotifyTaskStatus() {
    }
}
