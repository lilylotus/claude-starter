package cn.nihility.rbac.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

/**
 * 统一的 Redis 操作工具类，收敛各业务模块重复的 {@code stringRedisTemplate.opsForValue()}/
 * {@code opsForHash()} 样板代码，并内置基于 {@link JacksonUtils} 的对象存取能力（写入前
 * 序列化为 JSON 字符串，读取后按目标类型反序列化），字符串 key（{@link #setObject}/
 * {@link #getObject}）与 Hash 字段（{@link #putHashObject}/{@link #getHashObject}）两种
 * 维度均支持直接 put/get 对象实例，避免各处各自手写
 * "{@code JacksonUtils.toJson} + {@code opsForValue().set}" /
 * "{@code opsForValue().get} + {@code JacksonUtils.toObj}" 这类重复代码。静态方法为主，
 * 风格对齐 {@link HttpClientUtils}：内部持有一个由 Spring 在启动阶段注入的
 * {@link StringRedisTemplate} 单例（见
 * {@code cn.nihility.rbac.common.config.RedisUtilsInitializer}），本类自身不依赖 Spring
 * 容器即可编译，但脱离 Spring 环境调用会抛出 {@link IllegalStateException}——不同于
 * {@link HttpClientUtils} 有内置默认值可用，{@link StringRedisTemplate} 必须由 Spring Boot
 * 自动装配，没有可脱离容器工作的合理默认值。
 */
public final class RedisUtils {

    /** 由 Spring 启动阶段注入的字符串 Redis 模板，脱离容器时为 {@code null}。 */
    private static volatile StringRedisTemplate stringRedisTemplate;

    /**
     * 工具类不允许实例化。
     */
    private RedisUtils() {
    }

    /**
     * 注入真正生效的 {@link StringRedisTemplate}，由
     * {@code cn.nihility.rbac.common.config.RedisUtilsInitializer} 在 Spring 容器启动阶段调用。
     *
     * @param template Spring Boot 自动装配的字符串 Redis 模板
     */
    public static void configure(StringRedisTemplate template) {
        stringRedisTemplate = template;
    }

    /**
     * 读取当前生效的 {@link StringRedisTemplate}（未初始化时为 {@code null}），仅供不依赖
     * Spring 容器的纯 Mockito 单元测试（如 {@code TokenServiceImplTest}）在改用 mock 打桩
     * 前保存现场、用例结束后调用 {@link #configure} 还原，避免污染同一 JVM 内其它真实起
     * Spring 容器/Redis 连接的测试用例（本类是 JVM 级静态单例，Gradle 默认在同一 JVM 内顺序
     * 执行多个测试类，不这样做会导致后执行的测试意外拿到前一个测试留下的 mock）。生产代码不
     * 应依赖本方法。
     *
     * @return 当前生效的字符串 Redis 模板，可能为 {@code null}
     */
    public static StringRedisTemplate current() {
        return stringRedisTemplate;
    }

    /**
     * 获取已注入的 {@link StringRedisTemplate}，未完成初始化时抛出异常而不是返回
     * {@code null}，避免调用方拿到 {@link NullPointerException} 却不知道根因是 Spring
     * 容器尚未启动。
     *
     * @return 已注入的字符串 Redis 模板
     */
    private static StringRedisTemplate template() {
        StringRedisTemplate template = stringRedisTemplate;
        if (template == null) {
            throw new IllegalStateException("RedisUtils 尚未完成初始化（StringRedisTemplate 未注入），请确认 Spring 容器已启动");
        }
        return template;
    }

    /**
     * 写入一个字符串值，不设置过期时间。
     *
     * @param key   Redis key
     * @param value 字符串值
     */
    public static void set(String key, String value) {
        template().opsForValue().set(key, value);
    }

    /**
     * 写入一个字符串值并设置过期时间。
     *
     * @param key     Redis key
     * @param value   字符串值
     * @param timeout 过期时间数值
     * @param unit    过期时间单位
     */
    public static void set(String key, String value, long timeout, TimeUnit unit) {
        template().opsForValue().set(key, value, timeout, unit);
    }

    /**
     * 写入一个字符串值并设置过期时间。
     *
     * @param key     Redis key
     * @param value   字符串值
     * @param timeout 过期时间
     */
    public static void set(String key, String value, Duration timeout) {
        template().opsForValue().set(key, value, timeout);
    }

    /**
     * 读取一个字符串值。
     *
     * @param key Redis key
     * @return key 存在且值非空白时返回该值，否则返回空
     */
    public static Optional<String> get(String key) {
        String value = template().opsForValue().get(key);
        return StringUtils.hasText(value) ? Optional.of(value) : Optional.empty();
    }

    /**
     * 把对象序列化为 JSON 字符串写入，不设置过期时间。
     *
     * @param key   Redis key
     * @param value 待序列化的对象
     */
    public static void setObject(String key, Object value) {
        set(key, JacksonUtils.toJson(value));
    }

    /**
     * 把对象序列化为 JSON 字符串写入并设置过期时间。
     *
     * @param key     Redis key
     * @param value   待序列化的对象
     * @param timeout 过期时间数值
     * @param unit    过期时间单位
     */
    public static void setObject(String key, Object value, long timeout, TimeUnit unit) {
        set(key, JacksonUtils.toJson(value), timeout, unit);
    }

    /**
     * 把对象序列化为 JSON 字符串写入并设置过期时间。
     *
     * @param key     Redis key
     * @param value   待序列化的对象
     * @param timeout 过期时间
     */
    public static void setObject(String key, Object value, Duration timeout) {
        set(key, JacksonUtils.toJson(value), timeout);
    }

    /**
     * 读取一个 JSON 字符串值并反序列化为指定类型的对象。
     *
     * @param key   Redis key
     * @param clazz 目标类型
     * @param <T>   目标类型
     * @return key 存在时返回反序列化后的对象，否则返回空
     */
    public static <T> Optional<T> getObject(String key, Class<T> clazz) {
        return get(key).map(json -> JacksonUtils.toObj(json, clazz));
    }

    /**
     * 读取一个 JSON 字符串值并反序列化为指定的（可带泛型的）类型。
     *
     * @param key           Redis key
     * @param typeReference 目标类型描述
     * @param <T>           目标类型
     * @return key 存在时返回反序列化后的对象，否则返回空
     */
    public static <T> Optional<T> getObject(String key, TypeReference<T> typeReference) {
        return get(key).map(json -> JacksonUtils.toObj(json, typeReference));
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
     * 对一个整型计数器 key 执行原子自增（{@code INCR}），首次自增（即自增后值为 1，说明
     * 该 key 此前不存在）时顺带设置过期时间，供需要"按天/按窗口计数且要求原子递增"的限流
     * 场景使用（如短信发送每日上限），避免"先 GET 再判断再 SET"的读改写方式在并发下计数
     * 不准（add-sso-login-methods change design.md Decision 4）。
     *
     * @param key     Redis key
     * @param timeout 首次自增时设置的过期时间数值
     * @param unit    过期时间单位
     * @return 自增后的当前值
     */
    public static Long increment(String key, long timeout, TimeUnit unit) {
        Long value = template().opsForValue().increment(key);
        if (value != null && value == 1L) {
            template().expire(key, timeout, unit);
        }
        return value;
    }

    /**
     * 查询一个 key 的剩余过期时间，供需要"更新值但保留原有剩余有效期"的场景使用（如二维码
     * 登录会话状态流转时不希望每次更新都把有效期重新拉满）。
     *
     * @param key  Redis key
     * @param unit 返回值使用的时间单位
     * @return 剩余过期时间，key 不存在时返回负值（语义同
     *         {@link StringRedisTemplate#getExpire(Object, TimeUnit)}）
     */
    public static Long getExpire(String key, TimeUnit unit) {
        return template().getExpire(key, unit);
    }

    /**
     * 批量写入一个 Hash 的多个字段。
     *
     * @param key  Redis key
     * @param hash 字段名到字段值的映射
     */
    public static void putHash(String key, Map<String, String> hash) {
        template().opsForHash().putAll(key, hash);
    }

    /**
     * 写入一个 Hash 的单个字段。
     *
     * @param key   Redis key
     * @param field 字段名
     * @param value 字段值
     */
    public static void putHashField(String key, String field, String value) {
        template().opsForHash().put(key, field, value);
    }

    /**
     * 读取一个 Hash 的全部字段。
     *
     * @param key Redis key
     * @return 字段名到字段值的映射，key 不存在时返回空 Map
     */
    public static Map<Object, Object> hashEntries(String key) {
        return template().opsForHash().entries(key);
    }

    /**
     * 把对象序列化为 JSON 字符串写入 Hash 的单个字段，供需要在同一个 Hash key 下按字段存取
     * 多个（可能不同类型的）对象实例的场景使用（如同一 key 下既有简单字符串字段，也有需要
     * 整体存取的对象字段）。
     *
     * @param key   Redis key
     * @param field 字段名
     * @param value 待序列化的对象
     */
    public static void putHashObject(String key, String field, Object value) {
        template().opsForHash().put(key, field, JacksonUtils.toJson(value));
    }

    /**
     * 读取 Hash 单个字段的 JSON 字符串值并反序列化为指定类型的对象。
     *
     * @param key   Redis key
     * @param field 字段名
     * @param clazz 目标类型
     * @param <T>   目标类型
     * @return 字段存在时返回反序列化后的对象，否则返回空
     */
    public static <T> Optional<T> getHashObject(String key, String field, Class<T> clazz) {
        Object value = template().opsForHash().get(key, field);
        return value == null ? Optional.empty() : Optional.of(JacksonUtils.toObj(value.toString(), clazz));
    }

    /**
     * 读取 Hash 单个字段的 JSON 字符串值并反序列化为指定的（可带泛型的）类型。
     *
     * @param key           Redis key
     * @param field         字段名
     * @param typeReference 目标类型描述
     * @param <T>           目标类型
     * @return 字段存在时返回反序列化后的对象，否则返回空
     */
    public static <T> Optional<T> getHashObject(String key, String field, TypeReference<T> typeReference) {
        Object value = template().opsForHash().get(key, field);
        return value == null ? Optional.empty() : Optional.of(JacksonUtils.toObj(value.toString(), typeReference));
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
