package cn.nihility.rbac.sync.notify.dto;

/**
 * 一次实际通知发送尝试的结果（app-sync-changelog-pull change design.md Decision 6），由
 * {@code AppNotifyService#sendOnce} 返回，不修改任务状态，仅描述"这次请求结果如何、是否值得
 * 再重试"，具体状态流转（转 {@code SUCCESS}/{@code RETRY}/{@code DEAD}）由调用方
 * （{@code AppNotifyTaskService}）结合当前已尝试次数决定。
 *
 * @param success    是否收到 2xx 响应
 * @param retryable  失败时是否属于可重试的失败类型（网络异常/408/429/5xx），{@code success}
 *                   为 {@code true} 时恒为 {@code false}（无意义）
 * @param httpStatus 外部接口返回的 HTTP 状态码，网络异常等未收到响应时为 {@code null}
 * @param errorMsg   失败原因摘要，{@code success} 为 {@code true} 时为 {@code null}
 */
public record NotifyAttemptOutcome(boolean success, boolean retryable, Integer httpStatus, String errorMsg) {

    /**
     * 构造一个成功结果。
     *
     * @param httpStatus 外部接口返回的 2xx 状态码
     * @return 成功结果
     */
    public static NotifyAttemptOutcome success(Integer httpStatus) {
        return new NotifyAttemptOutcome(true, false, httpStatus, null);
    }

    /**
     * 构造一个"可重试"的失败结果（网络异常/408/429/5xx）。
     *
     * @param httpStatus 外部接口返回的状态码，网络异常等未收到响应时为 {@code null}
     * @param errorMsg   失败原因摘要
     * @return 可重试的失败结果
     */
    public static NotifyAttemptOutcome retry(Integer httpStatus, String errorMsg) {
        return new NotifyAttemptOutcome(false, true, httpStatus, errorMsg);
    }

    /**
     * 构造一个"不可重试"的失败结果（其他 4xx，或目标应用配置已不存在）。
     *
     * @param httpStatus 外部接口返回的状态码，配置不存在等场景为 {@code null}
     * @param errorMsg   失败原因摘要
     * @return 不可重试的失败结果
     */
    public static NotifyAttemptOutcome dead(Integer httpStatus, String errorMsg) {
        return new NotifyAttemptOutcome(false, false, httpStatus, errorMsg);
    }
}
