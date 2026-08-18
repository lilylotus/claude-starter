package cn.nihility.rbac.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;

/**
 * 基于 {@code objectRedisTemplate}（{@code RedisTemplate<String, Object>}，value/hashValue
 * 用 {@link Jackson2JsonRedisSerializer} 包装，见
 * {@code cn.nihility.rbac.common.config.RedisObjectTemplateConfig}）的对象存取工具类：
 * {@code opsForValue()}/{@code opsForHash()} 均可直接 put 对象实例，Redis 客户端层面完成
 * 序列化，调用方不需要手动转 JSON 字符串。
 * <p>
 * {@link Jackson2JsonRedisSerializer} 按固定的 {@code Object.class} 反序列化，不嵌入具体类型
 * 信息，读回的原始值对复杂对象而言是一个 {@code LinkedHashMap}（Jackson 处理未知目标类型的
 * 标准行为），因此本类的 {@code get}/{@code getHash} 提供按 {@link Class}/
 * {@link TypeReference} 指定目标类型的重载，内部用 {@link JacksonUtils#convert} 把该原始值
 * 就地转换为目标类型（不经过 JSON 字符串中转），供只需要原始值（如已知是字符串/数字等
 * 简单类型）的场景使用不带目标类型的重载。
 * <p>
 * 静态方法为主，风格对齐 {@link RedisUtils}：内部持有一个由 Spring 在启动阶段注入的
 * {@link RedisTemplate} 单例（见
 * {@code cn.nihility.rbac.common.config.RedisObjectUtilsInitializer}），脱离 Spring 环境
 * 调用会抛出 {@link IllegalStateException}。
 */
public final class RedisObjectUtils {

    /** 由 Spring 启动阶段注入的对象 Redis 模板，脱离容器时为 {@code null}。 */
    private static volatile RedisTemplate<String, Object> redisTemplate;

    /**
     * 工具类不允许实例化。
     */
    private RedisObjectUtils() {
    }

    /**
     * 注入真正生效的 {@code objectRedisTemplate}，由
     * {@code cn.nihility.rbac.common.config.RedisObjectUtilsInitializer} 在 Spring 容器启动
     * 阶段调用。
     *
     * @param template Spring 装配的对象 Redis 模板
     */
    public static void configure(RedisTemplate<String, Object> template) {
        redisTemplate = template;
    }

    /**
     * 获取已注入的 {@code objectRedisTemplate}，未完成初始化时抛出异常而不是返回
     * {@code null}，避免调用方拿到 {@link NullPointerException} 却不知道根因是 Spring
     * 容器尚未启动。
     *
     * @return 已注入的对象 Redis 模板
     */
    private static RedisTemplate<String, Object> template() {
        RedisTemplate<String, Object> current = redisTemplate;
        if (current == null) {
            throw new IllegalStateException("RedisObjectUtils 尚未完成初始化（objectRedisTemplate 未注入），请确认 Spring 容器已启动");
        }
        return current;
    }

    /**
     * 写入一个对象实例，不设置过期时间。
     *
     * @param key   Redis key
     * @param value 待写入的对象实例
     */
    public static void set(String key, Object value) {
        template().opsForValue().set(key, value);
    }

    /**
     * 写入一个对象实例并设置过期时间。
     *
     * @param key     Redis key
     * @param value   待写入的对象实例
     * @param timeout 过期时间数值
     * @param unit    过期时间单位
     */
    public static void set(String key, Object value, long timeout, TimeUnit unit) {
        template().opsForValue().set(key, value, timeout, unit);
    }

    /**
     * 写入一个对象实例并设置过期时间。
     *
     * @param key     Redis key
     * @param value   待写入的对象实例
     * @param timeout 过期时间
     */
    public static void set(String key, Object value, Duration timeout) {
        template().opsForValue().set(key, value, timeout);
    }

    /**
     * 读取一个对象的原始值（复杂对象为 {@code LinkedHashMap}，简单类型为其本身），key 不存在
     * 时返回空。
     *
     * @param key Redis key
     * @return key 存在时返回读取到的原始值，否则返回空
     */
    public static Optional<Object> get(String key) {
        return Optional.ofNullable(template().opsForValue().get(key));
    }

    /**
     * 读取一个对象并转换为指定类型，key 不存在时返回空。
     *
     * @param key   Redis key
     * @param clazz 目标类型
     * @param <T>   目标类型
     * @return key 存在时返回转换后的对象，否则返回空
     */
    public static <T> Optional<T> get(String key, Class<T> clazz) {
        return get(key).map(value -> JacksonUtils.convert(value, clazz));
    }

    /**
     * 读取一个对象并转换为指定的（可带泛型的）类型，key 不存在时返回空。
     *
     * @param key           Redis key
     * @param typeReference 目标类型描述
     * @param <T>           目标类型
     * @return key 存在时返回转换后的对象，否则返回空
     */
    public static <T> Optional<T> get(String key, TypeReference<T> typeReference) {
        return get(key).map(value -> JacksonUtils.convert(value, typeReference));
    }

    /**
     * 删除一个 key，key 不存在时视为成功。
     *
     * @param key Redis key
     * @return 是否实际删除了一条记录
     */
    public static Boolean delete(String key) {
        return template().delete(key);
    }

    /**
     * 判断一个 key 是否存在。
     *
     * @param key Redis key
     * @return 是否存在
     */
    public static Boolean hasKey(String key) {
        return template().hasKey(key);
    }

    /**
     * 给一个已存在的 key 设置/刷新过期时间。
     *
     * @param key     Redis key
     * @param timeout 过期时间数值
     * @param unit    过期时间单位
     * @return 是否设置成功
     */
    public static Boolean expire(String key, long timeout, TimeUnit unit) {
        return template().expire(key, timeout, unit);
    }

    /**
     * 写入一个 Hash 的单个字段为对象实例。
     *
     * @param key   Redis key
     * @param field 字段名
     * @param value 待写入的对象实例
     */
    public static void putHash(String key, String field, Object value) {
        template().opsForHash().put(key, field, value);
    }

    /**
     * 读取一个 Hash 单个字段的原始值（复杂对象为 {@code LinkedHashMap}），字段不存在时返回空。
     *
     * @param key   Redis key
     * @param field 字段名
     * @return 字段存在时返回读取到的原始值，否则返回空
     */
    public static Optional<Object> getHash(String key, String field) {
        return Optional.ofNullable(template().opsForHash().get(key, field));
    }

    /**
     * 读取一个 Hash 单个字段并转换为指定类型，字段不存在时返回空。
     *
     * @param key   Redis key
     * @param field 字段名
     * @param clazz 目标类型
     * @param <T>   目标类型
     * @return 字段存在时返回转换后的对象，否则返回空
     */
    public static <T> Optional<T> getHash(String key, String field, Class<T> clazz) {
        return getHash(key, field).map(value -> JacksonUtils.convert(value, clazz));
    }

    /**
     * 读取一个 Hash 单个字段并转换为指定的（可带泛型的）类型，字段不存在时返回空。
     *
     * @param key           Redis key
     * @param field         字段名
     * @param typeReference 目标类型描述
     * @param <T>           目标类型
     * @return 字段存在时返回转换后的对象，否则返回空
     */
    public static <T> Optional<T> getHash(String key, String field, TypeReference<T> typeReference) {
        return getHash(key, field).map(value -> JacksonUtils.convert(value, typeReference));
    }

    /**
     * 读取一个 Hash 的全部字段（原始值）。
     *
     * @param key Redis key
     * @return 字段名到字段原始值的映射，key 不存在时返回空 Map
     */
    public static Map<Object, Object> hashEntries(String key) {
        return template().opsForHash().entries(key);
    }

    /**
     * 删除 Hash 的一个或多个字段。
     *
     * @param key    Redis key
     * @param fields 待删除的字段名
     * @return 实际删除的字段数量
     */
    public static Long deleteHashField(String key, Object... fields) {
        return template().opsForHash().delete(key, fields);
    }
}
