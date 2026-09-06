package cn.nihility.rbac.workflow.outbox.support;

import cn.nihility.rbac.workflow.outbox.config.OutboxRetryProperties;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Outbox 事件重试退避时间计算器（production-approval-lifecycle change design.md 第10节
 * "失败指数退避+抖动，建议最多8次/24小时后人工处理，可配置"，tasks.md 7.1）：按配置的初始
 * 等待、指数退避倍数、单次等待上限、抖动比例计算下一次重试时间；达到最大尝试次数或事件
 * 存活时长超过最大存活时长任一条件即判定为需要转入人工处理队列（{@code FAILED}），不再
 * 安排重试。单独抽成一个纯计算组件，便于脱离状态机落库逻辑单测，套用
 * {@code cn.nihility.rbac.sync.notify.support.NotifyRetryScheduleCalculator} 已验证的
 * 结构。
 */
@Component
@RequiredArgsConstructor
public class OutboxRetryScheduleCalculator {

    /** 重试/租约/死信相关配置。 */
    private final OutboxRetryProperties properties;

    /**
     * 根据本次失败之前已失败的次数与事件创建时间，决定这次失败之后应该转 {@code PENDING}
     * （安排重试）还是 {@code FAILED}（转人工处理队列）。
     *
     * @param attemptCountBeforeThisFailure 本次失败之前已失败的次数
     * @param eventCreateTime               事件首次落库时间，用于判断是否已超过最大存活时长
     * @param now                           当前时刻，作为计算下一次重试时间的基准
     * @return 重试决策：转 {@code PENDING} 时携带计算出的下一次重试时间，转 {@code FAILED}
     *         时 {@code nextRetryTime} 为 {@code null}
     */
    public RetryDecision decide(int attemptCountBeforeThisFailure, LocalDateTime eventCreateTime, LocalDateTime now) {
        int attemptCount = attemptCountBeforeThisFailure + 1;
        boolean maxAttemptsReached = attemptCount >= properties.getMaxAttempts();
        boolean maxAgeReached = eventCreateTime != null
                && Duration.between(eventCreateTime, now).toHours() >= properties.getMaxAgeHours();
        if (maxAttemptsReached || maxAgeReached) {
            return RetryDecision.dead(attemptCount);
        }
        long delaySeconds = computeDelaySecondsWithJitter(attemptCount);
        return RetryDecision.retry(attemptCount, now.plusSeconds(delaySeconds));
    }

    /**
     * 按"初始等待 * 倍数^(尝试序号-1)"计算退避等待秒数，截断到单次等待上限后叠加随机抖动
     * （区间 {@code [delay * (1 - jitterRatio), delay]}），避免大量事件在同一时刻集中重试。
     *
     * @param attemptNumber 本次失败对应的尝试序号（从 1 开始）
     * @return 叠加抖动后的退避等待秒数
     */
    private long computeDelaySecondsWithJitter(int attemptNumber) {
        double baseDelay = properties.getInitialIntervalSeconds()
                * Math.pow(properties.getMultiplier(), attemptNumber - 1);
        double cappedDelay = Math.min(baseDelay, properties.getMaxIntervalSeconds());
        double jitterRatio = Math.min(Math.max(properties.getJitterRatio(), 0d), 1d);
        double minDelay = cappedDelay * (1 - jitterRatio);
        if (minDelay >= cappedDelay) {
            return (long) cappedDelay;
        }
        return (long) ThreadLocalRandom.current().nextDouble(minDelay, cappedDelay + 1);
    }

    /**
     * 一次重试退避决策结果。
     *
     * @param dead          是否应转为人工处理队列（不再安排重试）
     * @param attemptCount  本次失败后的累计已失败次数
     * @param nextRetryTime 下一次允许重试的时间，{@code dead} 为 {@code true} 时为 {@code null}
     */
    public record RetryDecision(boolean dead, int attemptCount, LocalDateTime nextRetryTime) {

        /**
         * 构造一个"转人工处理队列"的决策结果。
         *
         * @param attemptCount 本次失败后的累计已失败次数
         * @return 死信决策结果
         */
        public static RetryDecision dead(int attemptCount) {
            return new RetryDecision(true, attemptCount, null);
        }

        /**
         * 构造一个"安排重试"的决策结果。
         *
         * @param attemptCount  本次失败后的累计已失败次数
         * @param nextRetryTime 下一次允许重试的时间
         * @return 重试决策结果
         */
        public static RetryDecision retry(int attemptCount, LocalDateTime nextRetryTime) {
            return new RetryDecision(false, attemptCount, nextRetryTime);
        }
    }
}
