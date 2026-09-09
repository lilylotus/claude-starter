package cn.nihility.rbac.ratelimit.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 接口调用频率限制全局默认配置，绑定前缀 {@code rbac.rate-limit}
 * （add-captcha-rate-limit change tasks.md 3.1）：未在 {@code @RateLimit} 注解上显式覆盖
 * 时间窗口秒数/最大请求次数时使用的兜底值。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rbac.rate-limit")
public class RbacRateLimitProperties {

    /** 全局默认时间窗口秒数，默认 60 秒。 */
    private long windowSeconds = 60;

    /** 全局默认时间窗口内最大请求次数，默认 60 次。 */
    private int maxRequests = 60;
}
