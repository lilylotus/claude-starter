package cn.nihility.rbac.workflow.outbox.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Outbox 事件重试/租约/死信相关配置，绑定前缀 {@code rbac.workflow.outbox}
 * （production-approval-lifecycle change design.md 第10节"失败指数退避+抖动，建议最多8次/
 * 24小时后人工处理，可配置"，tasks.md 7.1）。默认值对齐 design.md 建议值，套用
 * {@code cn.nihility.rbac.sync.notify.config.NotifyRetryProperties} 已验证的字段设计。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rbac.workflow.outbox")
public class OutboxRetryProperties {

    /** 第一次失败后的重试等待（秒），默认 30 秒。 */
    private long initialIntervalSeconds = 30;

    /** 指数退避倍数，默认 2.0。 */
    private double multiplier = 2.0;

    /** 单次退避等待上限（秒），默认 3600 秒（1 小时），避免最后几次重试间隔无限拉长。 */
    private long maxIntervalSeconds = 3600;

    /** 抖动比例（0~1），实际等待在 {@code [delay * (1 - jitterRatio), delay]} 区间内随机取值，
     *  默认 0.2，避免大量事件在同一时刻集中重试造成瞬时压力尖峰。 */
    private double jitterRatio = 0.2;

    /** 达到后转 {@code FAILED} 人工处理队列的最大尝试次数，默认 8 次。 */
    private int maxAttempts = 8;

    /** 达到后转 {@code FAILED} 人工处理队列的事件最大存活时长（小时，以事件创建时间为基准），
     *  默认 24 小时。 */
    private long maxAgeHours = 24;

    /**
     * {@code LEASED} 状态的租约时长（秒），默认 60 秒，需明显大于单次消费处理的预期耗时，
     * 避免正常处理还没完成就被判定租约超时重复抢占。
     */
    private long leaseSeconds = 60;
}
