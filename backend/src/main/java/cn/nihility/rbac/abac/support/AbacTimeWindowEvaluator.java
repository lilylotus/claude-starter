package cn.nihility.rbac.abac.support;

import cn.nihility.rbac.abac.entity.AbacPolicyTimeWindowEntity;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * ABAC 时间窗口条件判定组件：判断服务器当前时间（通过注入的 {@link Clock} 获取，供单元
 * 测试注入可控时钟，不依赖系统当前真实时间）是否落在某条时间窗口内——{@code dayOfWeek}
 * 为空时每天均适用，否则只在对应星期适用；起止时间按 {@link LocalTime} 比较，均为闭区间
 * （abac-policy-engine change design.md Decision 4）。
 */
@Component
@RequiredArgsConstructor
public class AbacTimeWindowEvaluator {

    /** 用于获取当前时间的时钟，默认使用应用服务器本地默认时区（见 {@code AbacClockConfig}）。 */
    private final Clock clock;

    /**
     * 判断当前时间是否落在给定的单条时间窗口内。
     *
     * @param window 时间窗口条件
     * @return 是否落在窗口内
     */
    public boolean matches(AbacPolicyTimeWindowEntity window) {
        LocalDateTime now = LocalDateTime.now(clock);
        Integer dayOfWeek = window.getDayOfWeek();
        if (dayOfWeek != null && dayOfWeek != now.getDayOfWeek().getValue()) {
            return false;
        }

        LocalTime currentTime = now.toLocalTime();
        return !currentTime.isBefore(window.getStartTime()) && !currentTime.isAfter(window.getEndTime());
    }

    /**
     * 判断当前时间是否命中一组时间窗口条件中的任意一条（OR 语义，abac-policy-engine
     * change design.md Decision 4）；条件列表为空时视为该维度恒满足（不做限制）。
     *
     * @param windows 时间窗口条件列表，可为空
     * @return 是否命中任意一条
     */
    public boolean matchesAny(List<AbacPolicyTimeWindowEntity> windows) {
        if (windows == null || windows.isEmpty()) {
            return true;
        }
        return windows.stream().anyMatch(this::matches);
    }
}
