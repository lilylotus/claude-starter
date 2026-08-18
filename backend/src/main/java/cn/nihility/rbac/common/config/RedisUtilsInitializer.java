package cn.nihility.rbac.common.config;

import cn.nihility.rbac.common.util.RedisUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 把 Spring Boot 自动装配的 {@link StringRedisTemplate} 推送给 {@link RedisUtils}，使其静态
 * 方法可用（对齐 {@link HttpClientProperties} 把配置推送给 {@code HttpClientUtils} 的既有
 * 组织方式）。{@link RedisUtils} 本身不注册为 Spring Bean，脱离本类完成注入前调用其静态
 * 方法会抛出 {@link IllegalStateException}。
 */
@Component
@RequiredArgsConstructor
public class RedisUtilsInitializer {

    /** Spring Boot 按 {@code spring.data.redis.*} 配置自动装配的字符串 Redis 模板。 */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Bean 初始化完成后把当前模板推送给 {@link RedisUtils}。
     */
    @PostConstruct
    public void init() {
        RedisUtils.configure(stringRedisTemplate);
    }
}
