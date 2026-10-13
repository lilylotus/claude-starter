package cn.nihility.rbac.abac.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册 ABAC 模块使用的 {@link Clock} bean，使用应用服务器本地默认时区，不做用户时区换算
 * （abac-policy-engine change design.md Decision 4）。单独声明为 bean 是为了让
 * {@code AbacTimeWindowEvaluator} 的时间窗口判定可以在单元测试中注入
 * {@link Clock#fixed} 等可控时钟，不依赖系统当前真实时间。
 */
@Configuration
public class AbacClockConfig {

    /**
     * 声明系统默认时区的时钟 bean。
     *
     * @return 系统默认时区时钟
     */
    @Bean
    public Clock abacClock() {
        return Clock.systemDefaultZone();
    }
}
