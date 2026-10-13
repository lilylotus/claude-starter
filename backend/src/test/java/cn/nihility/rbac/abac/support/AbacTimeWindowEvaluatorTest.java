package cn.nihility.rbac.abac.support;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.abac.entity.AbacPolicyTimeWindowEntity;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link AbacTimeWindowEvaluator} 的单元测试，覆盖"指定星期+命中"、"指定星期+不命中"、
 * "不指定星期（每天适用）"三种场景，以及多条时间窗口 OR 语义、空列表恒满足的分支
 * （abac-policy-engine change tasks.md 3.2）。通过注入 {@link Clock#fixed} 固定时钟，
 * 不依赖系统当前真实时间。
 */
class AbacTimeWindowEvaluatorTest {

    /** 固定的"周三 20:00"测试时钟所在时区。 */
    private static final ZoneId ZONE_ID = ZoneId.systemDefault();

    /**
     * 构造一个固定在给定本地时间的 {@link Clock}。
     *
     * @param dateTime 固定的本地时间
     * @return 固定时钟
     */
    private Clock fixedClockAt(LocalDateTime dateTime) {
        return Clock.fixed(dateTime.atZone(ZONE_ID).toInstant(), ZONE_ID);
    }

    /**
     * 指定星期的时间窗口，当前时间命中该星期且落在起止时间内时，应判定为满足
     * （spec.md"成功配置指定星期的时间窗口" Scenario，周六全天）。
     */
    @Test
    void matches_shouldReturnTrue_whenDayOfWeekSpecifiedAndMatches() {
        // 2024-01-06 是周六
        LocalDateTime saturdayNoon = LocalDateTime.of(2024, 1, 6, 12, 0);
        AbacTimeWindowEvaluator evaluator = new AbacTimeWindowEvaluator(fixedClockAt(saturdayNoon));
        AbacPolicyTimeWindowEntity window = AbacPolicyTimeWindowEntity.builder()
                .dayOfWeek(6)
                .startTime(LocalTime.MIDNIGHT)
                .endTime(LocalTime.of(23, 59, 59))
                .build();

        boolean result = evaluator.matches(window);

        assertThat(result).isTrue();
    }

    /**
     * 指定星期的时间窗口，当前时间不是该星期时，应判定为不满足（spec.md"命中时间窗口
     * 条件时判定为拒绝"对应的反例分支：周三不命中仅周六适用的窗口）。
     */
    @Test
    void matches_shouldReturnFalse_whenDayOfWeekSpecifiedAndDoesNotMatch() {
        // 2024-01-03 是周三
        LocalDateTime wednesdayNoon = LocalDateTime.of(2024, 1, 3, 12, 0);
        AbacTimeWindowEvaluator evaluator = new AbacTimeWindowEvaluator(fixedClockAt(wednesdayNoon));
        AbacPolicyTimeWindowEntity window = AbacPolicyTimeWindowEntity.builder()
                .dayOfWeek(6)
                .startTime(LocalTime.MIDNIGHT)
                .endTime(LocalTime.of(23, 59, 59))
                .build();

        boolean result = evaluator.matches(window);

        assertThat(result).isFalse();
    }

    /**
     * 不指定星期的时间窗口，每天均适用，当前时间落在起止时间内时应判定为满足
     * （spec.md"成功配置每天适用的时间窗口" Scenario）。
     */
    @Test
    void matches_shouldReturnTrue_whenDayOfWeekNotSpecifiedAndWithinRange() {
        // 2024-01-03 周三 20:00，落在 18:00-23:59:59 范围内
        LocalDateTime wednesdayEvening = LocalDateTime.of(2024, 1, 3, 20, 0);
        AbacTimeWindowEvaluator evaluator = new AbacTimeWindowEvaluator(fixedClockAt(wednesdayEvening));
        AbacPolicyTimeWindowEntity window = AbacPolicyTimeWindowEntity.builder()
                .dayOfWeek(null)
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(23, 59, 59))
                .build();

        boolean result = evaluator.matches(window);

        assertThat(result).isTrue();
    }

    /**
     * 不指定星期的时间窗口，当前时间不在起止时间范围内时应判定为不满足（spec.md"用户属性
     * 与时间窗口都配置时需同时满足才算命中" Scenario 对应的时间窗口不满足分支：周三
     * 10:00 不在 18:00-24:00 范围内）。
     */
    @Test
    void matches_shouldReturnFalse_whenDayOfWeekNotSpecifiedAndOutOfRange() {
        LocalDateTime wednesdayMorning = LocalDateTime.of(2024, 1, 3, 10, 0);
        AbacTimeWindowEvaluator evaluator = new AbacTimeWindowEvaluator(fixedClockAt(wednesdayMorning));
        AbacPolicyTimeWindowEntity window = AbacPolicyTimeWindowEntity.builder()
                .dayOfWeek(null)
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(23, 59, 59))
                .build();

        boolean result = evaluator.matches(window);

        assertThat(result).isFalse();
    }

    /**
     * 多条时间窗口中命中任意一条即视为满足（OR 语义，spec.md"多条时间窗口满足任意一条
     * 即可" Scenario：周一至周五 18:00-24:00、周六周日全天，当前为周日 10:00，命中第二条）。
     */
    @Test
    void matchesAny_shouldReturnTrue_whenAnyWindowMatches() {
        // 2024-01-07 是周日
        LocalDateTime sundayMorning = LocalDateTime.of(2024, 1, 7, 10, 0);
        AbacTimeWindowEvaluator evaluator = new AbacTimeWindowEvaluator(fixedClockAt(sundayMorning));
        AbacPolicyTimeWindowEntity weekdayEvening = AbacPolicyTimeWindowEntity.builder()
                .dayOfWeek(1)
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(23, 59, 59))
                .build();
        AbacPolicyTimeWindowEntity weekendAllDay = AbacPolicyTimeWindowEntity.builder()
                .dayOfWeek(7)
                .startTime(LocalTime.MIDNIGHT)
                .endTime(LocalTime.of(23, 59, 59))
                .build();

        boolean result = evaluator.matchesAny(List.of(weekdayEvening, weekendAllDay));

        assertThat(result).isTrue();
    }

    /**
     * 多条时间窗口全部未命中时，应判定为不满足。
     */
    @Test
    void matchesAny_shouldReturnFalse_whenNoWindowMatches() {
        LocalDateTime wednesdayMorning = LocalDateTime.of(2024, 1, 3, 10, 0);
        AbacTimeWindowEvaluator evaluator = new AbacTimeWindowEvaluator(fixedClockAt(wednesdayMorning));
        AbacPolicyTimeWindowEntity weekdayEvening = AbacPolicyTimeWindowEntity.builder()
                .dayOfWeek(3)
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(23, 59, 59))
                .build();

        boolean result = evaluator.matchesAny(List.of(weekdayEvening));

        assertThat(result).isFalse();
    }

    /**
     * 未配置任何时间窗口时，该维度视为恒满足（不做限制）。
     */
    @Test
    void matchesAny_shouldReturnTrue_whenWindowListEmpty() {
        AbacTimeWindowEvaluator evaluator = new AbacTimeWindowEvaluator(fixedClockAt(LocalDateTime.now()));

        boolean result = evaluator.matchesAny(List.of());

        assertThat(result).isTrue();
    }
}
