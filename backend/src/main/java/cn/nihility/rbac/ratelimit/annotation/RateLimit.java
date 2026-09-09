package cn.nihility.rbac.ratelimit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式接口调用频率限制注解，标注在 Controller 方法上（add-captcha-rate-limit change
 * tasks.md 3.2）。{@code routeKey} 由切面按"目标类全限定名 + # + 方法名"自动生成，不要求
 * 业务方手动指定（design.md Decision 7）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 时间窗口秒数，默认 -1 表示使用 {@code rbac.rate-limit.window-seconds} 全局默认配置。
     *
     * @return 时间窗口秒数
     */
    long windowSeconds() default -1;

    /**
     * 时间窗口内最大请求次数，默认 -1 表示使用 {@code rbac.rate-limit.max-requests}
     * 全局默认配置。
     *
     * @return 最大请求次数
     */
    int maxRequests() default -1;
}
