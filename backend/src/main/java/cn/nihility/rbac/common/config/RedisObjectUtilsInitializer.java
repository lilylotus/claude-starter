package cn.nihility.rbac.common.config;

import cn.nihility.rbac.common.util.RedisObjectUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 把 {@link RedisObjectTemplateConfig} 定义的 {@code objectRedisTemplate} Bean 推送给
 * {@link RedisObjectUtils}，使其静态方法可用（对齐 {@link RedisUtilsInitializer} 把
 * {@code StringRedisTemplate} 推送给 {@code RedisUtils} 的既有组织方式）。按 Bean 名称
 * 精确注入（构造器参数上直接标注 {@link Qualifier}，不用 Lombok
 * {@code @RequiredArgsConstructor}——本仓库未配置 {@code lombok.config} 的
 * {@code copyableAnnotations}，字段上的 {@link Qualifier} 不会被复制到 Lombok 生成的构造器
 * 参数上，会导致 Spring 在两个同为 {@link RedisTemplate} 原始类型的候选 Bean（本 Bean 与
 * Spring Boot 自动装配的默认 {@code redisTemplate} Bean）之间产生歧义），避免装配歧义。
 */
@Component
public class RedisObjectUtilsInitializer {

    /** {@link RedisObjectTemplateConfig#objectRedisTemplate} 定义的对象 Redis 模板。 */
    private final RedisTemplate<String, Object> objectRedisTemplate;

    /**
     * 构造函数，按 Bean 名称精确注入 {@code objectRedisTemplate}。
     *
     * @param objectRedisTemplate {@link RedisObjectTemplateConfig#objectRedisTemplate} 定义的对象 Redis 模板
     */
    public RedisObjectUtilsInitializer(@Qualifier("objectRedisTemplate") RedisTemplate<String, Object> objectRedisTemplate) {
        this.objectRedisTemplate = objectRedisTemplate;
    }

    /**
     * Bean 初始化完成后把当前模板推送给 {@link RedisObjectUtils}。
     */
    @PostConstruct
    public void init() {
        RedisObjectUtils.configure(objectRedisTemplate);
    }
}
