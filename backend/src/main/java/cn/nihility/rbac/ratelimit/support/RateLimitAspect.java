package cn.nihility.rbac.ratelimit.support;

import cn.nihility.rbac.common.util.ClientRequestUtils;
import cn.nihility.rbac.common.util.RedisUtils;
import cn.nihility.rbac.ratelimit.annotation.RateLimit;
import cn.nihility.rbac.ratelimit.config.RbacRateLimitProperties;
import cn.nihility.rbac.ratelimit.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 接口调用频率限制切面（add-captcha-rate-limit change tasks.md 4.1/4.2）：按
 * "目标类全限定名 + # + 方法名"拼接 {@code routeKey}，用
 * {@link ClientRequestUtils#resolveClientIp} 取客户端 IP，基于 Redis 固定窗口计数
 * （{@link RedisUtils#increment}）判断是否超过最大请求次数。标注了 {@link RateLimit} 的方法
 * 才会被本切面拦截，未标注的方法不受影响。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    /** 限流计数 Redis key 前缀，完整 key 为该前缀 + routeKey + 客户端 IP。 */
    private static final String RATE_LIMIT_KEY_PREFIX = "rate-limit:";

    /** 无法解析出客户端 IP 时使用的占位值。 */
    private static final String UNKNOWN_CLIENT_IP = "unknown";

    /** 接口调用频率限制全局默认配置。 */
    private final RbacRateLimitProperties rateLimitProperties;

    /**
     * 环绕标注了 {@link RateLimit} 注解的方法，判断是否超过限流阈值。
     *
     * @param joinPoint 环绕连接点
     * @return 未超限时为原方法返回值
     * @throws Throwable 原方法抛出的异常，或超限时抛出的 {@link RateLimitExceededException}
     */
    @Around("@annotation(cn.nihility.rbac.ratelimit.annotation.RateLimit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return joinPoint.proceed();
        }

        long windowSeconds = rateLimit.windowSeconds() > 0 ? rateLimit.windowSeconds()
                : rateLimitProperties.getWindowSeconds();
        int maxRequests = rateLimit.maxRequests() > 0 ? rateLimit.maxRequests()
                : rateLimitProperties.getMaxRequests();

        String routeKey = method.getDeclaringClass().getName() + "#" + method.getName();
        String clientIp = resolveClientIp();
        String key = RATE_LIMIT_KEY_PREFIX + routeKey + ":" + clientIp;

        Long currentCount = RedisUtils.increment(key, windowSeconds, TimeUnit.SECONDS);
        if (currentCount != null && currentCount > maxRequests) {
            throw new RateLimitExceededException();
        }
        return joinPoint.proceed();
    }

    /**
     * 解析当前请求的客户端 IP，取不到当前 HTTP 请求（如非 Web 请求线程内调用）时返回占位值，
     * 避免限流计数因 IP 缺失而互相污染。
     *
     * @return 客户端 IP，取不到时为 {@link #UNKNOWN_CLIENT_IP}
     */
    private String resolveClientIp() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return UNKNOWN_CLIENT_IP;
        }
        HttpServletRequest request = servletRequestAttributes.getRequest();
        String clientIp = ClientRequestUtils.resolveClientIp(request);
        return StringUtils.hasText(clientIp) ? clientIp : UNKNOWN_CLIENT_IP;
    }
}
