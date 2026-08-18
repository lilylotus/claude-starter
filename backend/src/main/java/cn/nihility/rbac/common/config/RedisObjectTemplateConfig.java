package cn.nihility.rbac.common.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 定义一个专用于对象存取的 {@code RedisTemplate<String, Object>} Bean：key/hashKey 仍用
 * {@link StringRedisSerializer}（与 {@code StringRedisTemplate} 保持一致的 key 可读性），
 * value/hashValue 用 {@link Jackson2JsonRedisSerializer} 包装，使 {@code opsForValue()}/
 * {@code opsForHash()} 可以直接 put/get 对象实例，不需要调用方手动做 JSON 序列化/反序列化。
 * Bean 名称为 {@code objectRedisTemplate}，与 Spring Boot 自动装配的默认
 * {@code redisTemplate} Bean（{@code RedisTemplate<Object, Object>}，本项目未使用）共存，
 * 不覆盖、不冲突。
 */
@Configuration
public class RedisObjectTemplateConfig {

    /** {@code LocalDateTime} 的统一序列化 / 反序列化格式，对齐 {@code JacksonUtils}。 */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** {@code LocalDate} 的统一序列化 / 反序列化格式，对齐 {@code JacksonUtils}。 */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** {@code LocalTime} 的统一序列化 / 反序列化格式，对齐 {@code JacksonUtils}。 */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * 定义 {@code objectRedisTemplate} Bean。
     *
     * @param connectionFactory Spring Boot 按 {@code spring.data.redis.*} 配置自动装配的连接工厂
     * @return 已完成序列化器配置的 {@code RedisTemplate<String, Object>}
     */
    @Bean
    public RedisTemplate<String, Object> objectRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        Jackson2JsonRedisSerializer<Object> valueSerializer = new Jackson2JsonRedisSerializer<>(buildObjectMapper(), Object.class);

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 构建本 Bean 专用的 {@link ObjectMapper}：日期类型统一格式化、序列化排除 {@code null}
     * 字段、反序列化忽略目标类未定义的字段，与 {@code JacksonUtils} 的既有配置保持一致。
     *
     * @return 已完成初始化配置的 {@link ObjectMapper}
     */
    private ObjectMapper buildObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME_FORMATTER));
        javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DATE_TIME_FORMATTER));
        javaTimeModule.addSerializer(LocalDate.class, new LocalDateSerializer(DATE_FORMATTER));
        javaTimeModule.addDeserializer(LocalDate.class, new LocalDateDeserializer(DATE_FORMATTER));
        javaTimeModule.addSerializer(LocalTime.class, new LocalTimeSerializer(TIME_FORMATTER));
        javaTimeModule.addDeserializer(LocalTime.class, new LocalTimeDeserializer(TIME_FORMATTER));
        mapper.registerModule(javaTimeModule);

        return mapper;
    }
}
