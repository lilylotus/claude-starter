package cn.nihility.rbac.sync.openapi.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 对账摘要接口的 canonical JSON 编码工具（app-sync-changelog-pull change design.md
 * Decision 10）：键按字典序排列、{@code null} 显式保留不省略字段、UTF-8 编码，保证同一份
 * 记录内容无论 {@link Map} 插入顺序如何都编码为完全相同的字节序列，是摘要值可重复计算、
 * 与内容变化敏感的基础。
 *
 * <p>使用独立于 {@code cn.nihility.rbac.common.util.JacksonUtils} 的专用
 * {@link ObjectMapper} 实例：后者为落库/对外响应场景配置为排除 {@code null} 字段
 * （{@code Include.NON_NULL}），会破坏本工具"null 显式保留"的要求，因此不能复用。</p>
 */
public final class SyncDigestCanonicalCodec {

    /** {@code LocalDateTime} 的统一序列化格式，与 {@code JacksonUtils} 保持一致的可读格式。 */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 本工具专用的 {@link ObjectMapper} 实例：保留 {@code null} 字段、Map 按 key 排序输出。 */
    private static final ObjectMapper CANONICAL_MAPPER = buildCanonicalMapper();

    /**
     * 工具类不允许实例化。
     */
    private SyncDigestCanonicalCodec() {
    }

    /**
     * 把一条记录 Map 编码为 canonical JSON 的 UTF-8 字节数组。
     *
     * @param record 待编码的记录 Map，允许值为 {@code null}
     * @return canonical JSON 编码后的 UTF-8 字节数组
     */
    public static byte[] encode(Map<String, Object> record) {
        try {
            String json = CANONICAL_MAPPER.writeValueAsString(record);
            return json.getBytes(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("对账摘要记录 canonical JSON 编码失败", e);
        }
    }

    /**
     * 构建 canonical 编码专用的 {@link ObjectMapper}：{@code SerializationFeature.
     * ORDER_MAP_ENTRIES_BY_KEYS} 保证 {@link Map} 序列化时按 key 的自然顺序（字符串即字典序）
     * 输出，不受原始插入顺序影响，同时对嵌套 {@link Map} 同样生效；不排除 {@code null} 字段
     * （默认 {@code Include.ALWAYS}），日期统一格式化、不以时间戳形式输出。
     *
     * @return 已完成配置的 {@link ObjectMapper}
     */
    private static ObjectMapper buildCanonicalMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME_FORMATTER));
        mapper.registerModule(javaTimeModule);
        return mapper;
    }
}
