package cn.nihility.rbac.sync.notify.support;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.sync.notify.config.NotifyRetryProperties;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link NotifyRetryScheduleCalculator} 的单元测试：验证初始等待、指数退避倍数、单次等待
 * 上限、达到最大尝试次数转死信这几条规则，均使用 design.md Decision 6 表格里的默认值
 * （initial=30s, multiplier=2.0, max-interval=3600s, max-attempts=8）。
 */
class NotifyRetryScheduleCalculatorTest {

    private NotifyRetryProperties properties;

    private NotifyRetryScheduleCalculator calculator;

    @BeforeEach
    void setUp() {
        properties = new NotifyRetryProperties();
        calculator = new NotifyRetryScheduleCalculator(properties);
    }

    /**
     * 第一次失败（此前失败 0 次）应安排 30 秒后重试。
     */
    @Test
    void decide_shouldScheduleInitialInterval_whenFirstFailure() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0, 0);

        NotifyRetryScheduleCalculator.RetryDecision decision = calculator.decide(0, now);

        assertThat(decision.dead()).isFalse();
        assertThat(decision.retryCount()).isEqualTo(1);
        assertThat(decision.nextRetryTime()).isEqualTo(now.plusSeconds(30));
    }

    /**
     * 第二次失败（此前失败 1 次）应按倍数退避到 60 秒。
     */
    @Test
    void decide_shouldDoubleInterval_whenSecondFailure() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0, 0);

        NotifyRetryScheduleCalculator.RetryDecision decision = calculator.decide(1, now);

        assertThat(decision.retryCount()).isEqualTo(2);
        assertThat(decision.nextRetryTime()).isEqualTo(now.plusSeconds(60));
    }

    /**
     * 退避间隔不应超过配置的单次等待上限（3600 秒）。
     */
    @Test
    void decide_shouldCapAtMaxInterval() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0, 0);

        // 此前失败 6 次（第 7 次失败）：30 * 2^5 = 960 秒，未超上限，验证还未触发封顶。
        NotifyRetryScheduleCalculator.RetryDecision beforeCap = calculator.decide(5, now);
        assertThat(beforeCap.nextRetryTime()).isEqualTo(now.plusSeconds(960));

        // 人为调大倍数/初始等待，制造一个理论上会超过上限的场景，验证被截断到上限。
        properties.setInitialIntervalSeconds(10000);
        NotifyRetryScheduleCalculator.RetryDecision capped = calculator.decide(0, now);
        assertThat(capped.nextRetryTime()).isEqualTo(now.plusSeconds(3600));
    }

    /**
     * 达到最大尝试次数（8）时应判定为死信，不再安排下一次重试时间。
     */
    @Test
    void decide_shouldReturnDead_whenReachMaxAttempts() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0, 0);

        // 此前已失败 7 次，本次（第 8 次）失败后达到 max-attempts=8，应转死信。
        NotifyRetryScheduleCalculator.RetryDecision decision = calculator.decide(7, now);

        assertThat(decision.dead()).isTrue();
        assertThat(decision.retryCount()).isEqualTo(8);
        assertThat(decision.nextRetryTime()).isNull();
    }
}
