package cn.nihility.rbac.sync.notify.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 通知任务重试/死信状态机相关配置，绑定前缀 {@code rbac.sync.notify-retry}
 * （app-sync-changelog-pull change design.md Decision 6，全部默认值取自该表格）。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rbac.sync.notify-retry")
public class NotifyRetryProperties {

    /** 第一次失败后的重试等待（秒），默认 30 秒。 */
    private long initialIntervalSeconds = 30;

    /** 指数退避倍数，默认 2.0。 */
    private double multiplier = 2.0;

    /** 单次退避等待上限（秒），默认 3600 秒（1 小时），避免最后几次重试间隔无限拉长。 */
    private long maxIntervalSeconds = 3600;

    /** 达到后转 {@code DEAD} 的最大尝试次数，默认 8 次。 */
    private int maxAttempts = 8;

    /**
     * {@code PROCESSING} 状态的租约时长（秒），默认 60 秒，需明显大于通知请求响应超时
     * （现状 3 秒），避免正常响应还没返回就被判定超时重复抢占。
     */
    private long leaseSeconds = 60;

    /** 独立调度器扫描到期 {@code PENDING}/{@code RETRY}/超时 {@code PROCESSING} 的轮询间隔（秒），默认 10 秒。 */
    private long schedulerPollIntervalSeconds = 10;

    /** 调度器单轮最多抢占的任务数，默认 100，避免一次占满发送线程池。 */
    private int schedulerBatchSize = 100;

    /**
     * 调度器轮询间隔的毫秒数，供 {@code @Scheduled(fixedDelayString = ...)} 的 SpEL 表达式
     * 直接引用，避免在注解里做算术运算。
     *
     * @return 轮询间隔（毫秒）
     */
    public long getSchedulerPollIntervalMillis() {
        return schedulerPollIntervalSeconds * 1000L;
    }
}
