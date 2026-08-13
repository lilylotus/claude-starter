package cn.nihility.rbac.identity.upstream.support;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.identity.upstream.constant.UpstreamIntervalUnit;
import cn.nihility.rbac.identity.upstream.constant.UpstreamScheduleType;
import cn.nihility.rbac.identity.upstream.entity.UpstreamSourceEntity;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamSourceMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link UpstreamSyncScheduler#isDue} 的单元测试，覆盖按间隔/按固定时间点到期判定的
 * 边界条件（tasks.md 10.1）。
 */
@ExtendWith(MockitoExtension.class)
class UpstreamSyncSchedulerTest {

    /** 被测组件的上游数据源数据访问依赖，使用 Mockito 打桩（{@code isDue} 不实际调用，
     * 仅用于构造被测组件）。 */
    @Mock
    private UpstreamSourceMapper upstreamSourceMapper;

    /** 被测组件的同步执行引擎依赖，使用 Mockito 打桩（{@code isDue} 不实际调用）。 */
    @Mock
    private UpstreamSyncExecutor upstreamSyncExecutor;

    /** 被测组件实例。 */
    private final UpstreamSyncScheduler scheduler = new UpstreamSyncScheduler(upstreamSourceMapper,
            upstreamSyncExecutor);

    /**
     * {@code INTERVAL} 类型，{@code lastTriggerTime} 为空时视为立即到期（数据源刚启用/
     * 刚创建时首次轮询即触发一次）。
     */
    @Test
    void isDue_shouldReturnTrue_whenIntervalAndNeverTriggered() {
        UpstreamSourceEntity source = UpstreamSourceEntity.builder()
                .scheduleType(UpstreamScheduleType.INTERVAL)
                .intervalUnit(UpstreamIntervalUnit.MINUTE)
                .intervalValue(30)
                .lastTriggerTime(null)
                .build();

        assertThat(scheduler.isDue(source, LocalDateTime.now())).isTrue();
    }

    /**
     * {@code INTERVAL} 类型（分钟单位），距上次触发时间恰好未满配置间隔时不到期。
     */
    @Test
    void isDue_shouldReturnFalse_whenIntervalMinuteNotElapsed() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 10, 0, 0);
        UpstreamSourceEntity source = UpstreamSourceEntity.builder()
                .scheduleType(UpstreamScheduleType.INTERVAL)
                .intervalUnit(UpstreamIntervalUnit.MINUTE)
                .intervalValue(30)
                .lastTriggerTime(now.minusMinutes(29))
                .build();

        assertThat(scheduler.isDue(source, now)).isFalse();
    }

    /**
     * {@code INTERVAL} 类型（分钟单位），距上次触发时间恰好满足配置间隔时到期
     * （边界值：{@code elapsed == due} 应判定到期）。
     */
    @Test
    void isDue_shouldReturnTrue_whenIntervalMinuteExactlyElapsed() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 10, 0, 0);
        UpstreamSourceEntity source = UpstreamSourceEntity.builder()
                .scheduleType(UpstreamScheduleType.INTERVAL)
                .intervalUnit(UpstreamIntervalUnit.MINUTE)
                .intervalValue(30)
                .lastTriggerTime(now.minusMinutes(30))
                .build();

        assertThat(scheduler.isDue(source, now)).isTrue();
    }

    /**
     * {@code INTERVAL} 类型（小时单位），距上次触发时间超过配置间隔时到期。
     */
    @Test
    void isDue_shouldReturnTrue_whenIntervalHourElapsed() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 10, 0, 0);
        UpstreamSourceEntity source = UpstreamSourceEntity.builder()
                .scheduleType(UpstreamScheduleType.INTERVAL)
                .intervalUnit(UpstreamIntervalUnit.HOUR)
                .intervalValue(2)
                .lastTriggerTime(now.minusHours(3))
                .build();

        assertThat(scheduler.isDue(source, now)).isTrue();
    }

    /**
     * {@code FIXED_TIME} 类型，当前时间的 {@code HH:mm} 等于配置值且当天尚未触发过时
     * 到期。
     */
    @Test
    void isDue_shouldReturnTrue_whenFixedTimeMatchesAndNotTriggeredToday() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 1, 0, 0);
        UpstreamSourceEntity source = UpstreamSourceEntity.builder()
                .scheduleType(UpstreamScheduleType.FIXED_TIME)
                .fixedTime("01:00")
                .lastTriggerTime(now.minusDays(1))
                .build();

        assertThat(scheduler.isDue(source, now)).isTrue();
    }

    /**
     * {@code FIXED_TIME} 类型，当前时间的 {@code HH:mm} 不等于配置值时不到期。
     */
    @Test
    void isDue_shouldReturnFalse_whenFixedTimeNotMatch() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 1, 5, 0);
        UpstreamSourceEntity source = UpstreamSourceEntity.builder()
                .scheduleType(UpstreamScheduleType.FIXED_TIME)
                .fixedTime("01:00")
                .lastTriggerTime(null)
                .build();

        assertThat(scheduler.isDue(source, now)).isFalse();
    }

    /**
     * {@code FIXED_TIME} 类型，当前时间的 {@code HH:mm} 等于配置值但当天已经触发过时
     * 不重复到期（避免同一分钟内 tick 抖动或轮询延迟导致同一天重复触发多次）。
     */
    @Test
    void isDue_shouldReturnFalse_whenFixedTimeAlreadyTriggeredToday() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 1, 0, 0);
        UpstreamSourceEntity source = UpstreamSourceEntity.builder()
                .scheduleType(UpstreamScheduleType.FIXED_TIME)
                .fixedTime("01:00")
                .lastTriggerTime(now.minusMinutes(1))
                .build();

        assertThat(scheduler.isDue(source, now)).isFalse();
    }

    /**
     * {@code FIXED_TIME} 类型，{@code lastTriggerTime} 为空且当前时间匹配配置值时到期。
     */
    @Test
    void isDue_shouldReturnTrue_whenFixedTimeMatchesAndNeverTriggered() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 1, 0, 0);
        UpstreamSourceEntity source = UpstreamSourceEntity.builder()
                .scheduleType(UpstreamScheduleType.FIXED_TIME)
                .fixedTime("01:00")
                .lastTriggerTime(null)
                .build();

        assertThat(scheduler.isDue(source, now)).isTrue();
    }
}
