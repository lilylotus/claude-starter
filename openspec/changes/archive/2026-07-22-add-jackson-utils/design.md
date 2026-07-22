## Context

调查（详见对话历史，未落盘为独立文档）确认：全仓库没有 fastjson/gson，仅 Jackson；`jackson-databind`、`jackson-datatype-jsr310`、`jackson-datatype-jdk8` 均已通过 `spring-boot-starter-web` 传递引入到 classpath，无需新增 Gradle 依赖。当前 3 处直接使用 `ObjectMapper` 的代码都是构造注入 Spring 容器托管的默认单例（无任何定制），其中只有 `OperationLogRecorderImpl`/`OperationLogQueryServiceImpl` 这两处在真实生产路径上被调用（序列化/反序列化操作日志的字段变更 diff）；`GlobalResponseAdvice` 里手动序列化的分支只有在 Controller 方法直接返回裸 `String` 时才会触发，全仓库搜索确认当前没有任何 Controller 方法是这个签名，这条分支目前是防御性代码、无实际调用路径。

已与用户确认的关键决策：`JacksonUtils` 的初始化配置（排除 `null`、日期格式化）**仅在工具类内部生效**，不接管 Spring 自动装配、用于 HTTP 响应真正序列化的那个 `ObjectMapper` Bean。也就是说本次改动是纯粹的"新增一个独立工具类 + 让已有 3 处直接用 `ObjectMapper` 的代码改用它"，不改变任何 REST 接口当前对外的 JSON 输出格式。

## Goals / Non-Goals

**Goals:**
- 提供一个自包含的静态工具类 `JacksonUtils`，封装序列化（`toJson`）、反序列化（`toObj`，支持 `String`/`byte[]`/`InputStream` 三种输入来源 × `Class`/`TypeReference`/`Type` 三种目标类型描述）、对象间转换（`convert`，支持 `Class`/`TypeReference`/`JavaType` 三种目标类型描述）。
- 统一日期类型（`LocalDateTime`/`LocalDate`/`LocalTime`/`java.util.Date`）的序列化与反序列化格式，避免以后每个用到日期字段的地方各自处理格式不一致的问题（仅对通过 `JacksonUtils` 处理的对象生效）。
- 把现有 3 处重复的、各自处理异常/拼类型的 `ObjectMapper` 使用代码收敛到 `JacksonUtils`，行为保持等价（不改变调用方现有的异常处理策略——调用方仍各自 `try/catch` 后降级，`JacksonUtils` 本身遇到序列化/反序列化失败时直接抛出运行时异常，不吞异常、不做静默降级）。

**Non-Goals:**
- 不改变 Spring 自动装配、用于 HTTP 响应真正序列化的 `ObjectMapper` Bean 的任何行为；普通 `@RestController` 接口返回的 JSON 格式（null 字段是否保留、日期字段的实际格式）本次改动后不变。
- 不引入新的 Gradle 依赖。
- 不新增全局配置文件改动（`application.yml` 不变）。
- 不为尚不存在的使用场景（如缓存、第三方对接）预先设计接口，仅实现用户明确列出的 4 类方法（`toObj`/`toJson`/`convert` + 初始化配置本身）。

## Decisions

### 1. `JacksonUtils` 的形态：纯静态工具类，独立维护 `ObjectMapper`，不做 Spring Bean

```java
public final class JacksonUtils {
    private static final ObjectMapper MAPPER = buildObjectMapper();
    private JacksonUtils() {}
    // toJson / toObj / convert 静态方法
}
```

不使用 `componentModel`/`@Component` 把它注册为 Spring Bean、也不通过 `@Autowired` 注入——遵循 CLAUDE.md 里 MapStruct `XxxConvert` 的静态单例约定同一种风格（"调用方直接 `XxxConvert.INSTANCE.xxx(...)`，不做构造器注入"），`JacksonUtils` 作为纯函数式的工具类没有状态、不需要依赖注入的生命周期管理，直接静态方法调用最简单直接，也让 `OperationLogRecorderImpl`/`OperationLogQueryServiceImpl`/`GlobalResponseAdvice` 三个类可以彻底移除 `ObjectMapper` 构造参数，构造函数更精简。

### 2. `ObjectMapper` 初始化配置

```java
private static ObjectMapper buildObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
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

    mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
    return mapper;
}
```

日期格式选用项目里 Java 后端最常见的 `yyyy-MM-dd HH:mm:ss` / `yyyy-MM-dd` / `HH:mm:ss`（对应 `LocalDateTime`/`LocalDate`/`LocalTime`），而不是当前 Spring Boot 默认的 ISO-8601（`2026-07-21T10:00:00`）——这是 Java 后端项目里最通行的约定，可读性更好，且带空格分隔的格式与 `SimpleDateFormat` 处理 `java.util.Date` 的格式保持一致，三种日期类型在通过 `JacksonUtils` 处理时行为统一。`SimpleDateFormat` 不是线程安全的，但这里只在 `buildObjectMapper()` 初始化时创建一次并交给 `ObjectMapper.setDateFormat`，`ObjectMapper` 内部序列化时会做好线程安全处理（每次使用前 clone），不需要调用方关心。

### 3. `toObj`/`convert` 的重载矩阵：按用户需求原样实现，不做归一化

用户的原始需求里，`toObj` 的目标类型描述是 `Class`、`TypeReference`、`Type`（`java.lang.reflect.Type`），而 `convert` 的目标类型描述是 `Class`、`TypeReference`、`JavaType`（`com.fasterxml.jackson.databind.JavaType`）——两者第三个重载用的类型描述不同。按字面需求原样实现，不强行统一成一致的三选项集合：

```java
// toObj：来源 3 种 × 目标类型描述 3 种 = 9 个重载
public static <T> T toObj(String json, Class<T> clazz);
public static <T> T toObj(String json, TypeReference<T> typeReference);
public static <T> T toObj(String json, Type type);
public static <T> T toObj(byte[] bytes, Class<T> clazz);
public static <T> T toObj(byte[] bytes, TypeReference<T> typeReference);
public static <T> T toObj(byte[] bytes, Type type);
public static <T> T toObj(InputStream in, Class<T> clazz);
public static <T> T toObj(InputStream in, TypeReference<T> typeReference);
public static <T> T toObj(InputStream in, Type type);

// toJson：对象 -> JSON 字符串
public static String toJson(Object obj);

// convert：对象 -> 对象，目标类型描述 3 种
public static <T> T convert(Object obj, Class<T> clazz);
public static <T> T convert(Object obj, TypeReference<T> typeReference);
public static <T> T convert(Object obj, JavaType javaType);
```

`toObj(..., Type type)` 内部通过 `MAPPER.getTypeFactory().constructType(type)` 把 `java.lang.reflect.Type` 转成 Jackson 的 `JavaType` 再反序列化；`convert(Object obj, JavaType javaType)` 直接调用 `MAPPER.convertValue(obj, javaType)`。

### 4. 异常处理：`JacksonUtils` 直接抛出运行时异常，不吞异常

`toJson`/`toObj`/`convert` 内部捕获 `JsonProcessingException`/`IOException` 等受检异常后，统一包装为 `IllegalStateException`（与 `GlobalResponseAdvice` 现有代码里 `throw new IllegalStateException("响应序列化失败", e)` 的风格保持一致）重新抛出，不在工具类内部吞异常、也不返回 `null`/空集合兜底。理由：`JacksonUtils` 是通用工具类，不知道调用方期望的降级策略是什么（有的调用方想抛给全局异常处理器变成一个业务错误响应，有的想像 `OperationLogRecorderImpl` 现在这样捕获后降级写入空数组）——把降级策略留给调用方自己决定，工具类只负责"能转就转，转不了就明确报错"。

对应到本次要迁移的 2 个真实调用点：
- `OperationLogRecorderImpl.toJson` 方法保留自己的 `try/catch`，只是内部实现从 `objectMapper.writeValueAsString(changes)` 改为 `JacksonUtils.toJson(changes)`，捕获 `JacksonUtils` 抛出的 `IllegalStateException` 后仍然降级返回 `"[]"`，行为不变。
- `OperationLogQueryServiceImpl.parseChangeDetail` 同理，`try/catch` 包裹 `JacksonUtils.toObj(...)` 调用，失败后仍降级返回 `List.of()`，行为不变。
- `GlobalResponseAdvice.writeAsJsonString` 直接调用 `JacksonUtils.toJson(...)`，不再需要自己 `catch JsonProcessingException` 转 `IllegalStateException`——`JacksonUtils` 内部已经做了这层转换，直接让异常往上抛即可，方法体可以简化。

## Risks / Trade-offs

- `JacksonUtils` 与 Spring 自动装配的 `ObjectMapper` 是两套独立配置，日后如果有人不小心在需要"和 REST 接口输出保持一致格式"的场景误用了 `JacksonUtils`，可能会看到不一致的日期格式（`JacksonUtils` 输出 `2026-07-21 10:00:00`，普通接口输出 `2026-07-21T10:00:00`）。风险较低（当前唯一涉及日期字段的调用点其实没有——`OperationLogFieldChangeVO` 只有 `field`/`oldValue`/`newValue` 三个 `String` 字段），但在类注释里会明确写清楚"仅用于内部序列化场景（如落库前的 JSON 快照），不用于替代 Spring 自动装配的 HTTP 响应序列化"，降低未来误用概率。
- 两个单元测试文件的构造函数调用需要同步改成单参，如果遗漏会导致编译失败（不是运行时才发现的隐患，`./gradlew test` 会直接报错，风险可控）。

## Migration Plan

纯代码改动，无数据库迁移、无需要停机或分批发布的步骤，前后端不涉及联动（这是后端内部重构）。

## Open Questions

无——配置影响范围已通过用户确认（仅工具类内部生效，不接管全局 HTTP 序列化）。
