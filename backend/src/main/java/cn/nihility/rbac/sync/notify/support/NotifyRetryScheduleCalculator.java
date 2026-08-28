package cn.nihility.rbac.sync.notify.support;

import cn.nihility.rbac.sync.notify.config.NotifyRetryProperties;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 通知任务重试退避时间计算器（app-sync-changelog-pull change design.md Decision 6）：
 * 按配置的初始等待、指数退避倍数、单次等待上限计算下一次重试时间，达到最大尝试次数时
 * 判定为死信，不再安排重试。单独抽成一个纯计算组件，便于脱离状态机落库逻辑单测。
 */
@Component
@RequiredArgsConstructor
public class NotifyRetryScheduleCalculator {

    /** 重试退避相关配置。 */
    private final NotifyRetryProperties properties;

    /**
     * 根据本次失败之前已失败的次数，决定这次失败之后应该转 {@code RETRY} 还是 {@code DEAD}。
     *
     * @param retryCountBeforeThisAttempt 本次失败之前已失败的次数
     * @param now                         当前时刻，作为计算下一次重试时间的基准
     * @return 重试决策：转 {@code RETRY} 时携带计算出的下一次重试时间，转 {@code DEAD} 时
     *         {@code nextRetryTime} 为 {@code null}
     */
    public RetryDecision decide(int retryCountBeforeThisAttempt, LocalDateTime now) {
        int retryCount = retryCountBeforeThisAttempt + 1;
        if (retryCount >= properties.getMaxAttempts()) {
            return RetryDecision.dead(retryCount);
        }
        long delaySeconds = computeDelaySeconds(retryCount);
        return RetryDecision.retry(retryCount, now.plusSeconds(delaySeconds));
    }

    /**
     * 按"初始等待 * 倍数^(尝试序号-1)"计算退避等待秒数，并截断到单次等待上限。
     *
     * @param attemptNumber 本次失败对应的尝试序号（从 1 开始）
     * @return 退避等待秒数，不超过 {@link NotifyRetryProperties#getMaxIntervalSeconds()}
     */
    private long computeDelaySeconds(int attemptNumber) {
        double delay = properties.getInitialIntervalSeconds()
                * Math.pow(properties.getMultiplier(), attemptNumber - 1);
        return (long) Math.min(delay, properties.getMaxIntervalSeconds());
    }

    /**
     * 一次重试退避决策结果。
     *
     * @param dead          是否应转为死信（不再安排重试）
     * @param retryCount    本次失败后的累计已失败次数
     * @param nextRetryTime 下一次允许重试的时间，{@code dead} 为 {@code true} 时为 {@code null}
     */
    public record RetryDecision(boolean dead, int retryCount, LocalDateTime nextRetryTime) {

        /**
         * 构造一个"转死信"的决策结果。
         *
         * @param retryCount 本次失败后的累计已失败次数
         * @return 死信决策结果
         */
        public static RetryDecision dead(int retryCount) {
            return new RetryDecision(true, retryCount, null);
        }

        /**
         * 构造一个"安排重试"的决策结果。
         *
         * @param retryCount    本次失败后的累计已失败次数
         * @param nextRetryTime 下一次允许重试的时间
         * @return 重试决策结果
         */
        public static RetryDecision retry(int retryCount, LocalDateTime nextRetryTime) {
            return new RetryDecision(false, retryCount, nextRetryTime);
        }
    }
}
