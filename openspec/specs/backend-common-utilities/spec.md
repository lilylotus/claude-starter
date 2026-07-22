# backend-common-utilities Specification

## Purpose
后端跨模块复用的公共基础设施能力，与 CLAUDE.md 里 `common/` 目录下已有的 `Result`、`GlobalResponseAdvice`、`BusinessException`、`GlobalExceptionHandler` 同属一类定位：不属于任何具体业务模块，供各业务模块直接复用，避免重复实现。目前包含统一的 JSON 序列化/反序列化/类型转换工具类 `JacksonUtils`。

## Requirements
### Requirement: 统一的 JSON 序列化/反序列化工具类
系统 SHALL 提供一个位于 `cn.nihility.rbac.common.util` 包下的静态工具类 `JacksonUtils`，封装 JSON 序列化、反序列化与对象间类型转换能力，供后端各模块复用，避免各处重复注入 `ObjectMapper` 并各自处理初始化配置与异常。`JacksonUtils` 内部维护的 `ObjectMapper` 实例 SHALL 是独立于 Spring 自动装配、用于 HTTP 响应实际序列化的 `ObjectMapper` Bean 的另一份实例，两者互不影响；调用 `JacksonUtils` 不 SHALL 改变任何 `@RestController` 接口对外返回的 JSON 输出格式。

`JacksonUtils` 内部的 `ObjectMapper` SHALL 应用以下初始化配置：
- 序列化时排除值为 `null` 的字段。
- 反序列化时，若 JSON 内容包含目标类未定义的字段，SHALL 忽略该字段而不是抛出异常。
- `LocalDateTime`、`LocalDate`、`LocalTime`、`java.util.Date` 这四种日期类型 SHALL 使用统一的自定义格式序列化与反序列化（分别为 `yyyy-MM-dd HH:mm:ss`、`yyyy-MM-dd`、`HH:mm:ss`、`yyyy-MM-dd HH:mm:ss`），且不以时间戳形式序列化日期。

`JacksonUtils` SHALL 提供以下静态方法：
- `toJson(Object obj)`：把任意对象序列化为 JSON 字符串。
- `toObj(...)`：把 JSON 内容反序列化为对象，来源支持 `String`、`byte[]`、`InputStream` 三种，目标类型描述支持 `Class<T>`、`com.fasterxml.jackson.core.type.TypeReference<T>`、`java.lang.reflect.Type` 三种，共 9 个重载。
- `convert(...)`：把一个已有对象转换为另一种类型的对象，目标类型描述支持 `Class<T>`、`TypeReference<T>`、`com.fasterxml.jackson.databind.JavaType` 三种，共 3 个重载。

`toJson`/`toObj`/`convert` 在序列化或反序列化失败时 SHALL 抛出运行时异常（不吞异常、不静默返回 `null` 或空值），由调用方自行决定是否捕获并降级处理。

#### Scenario: 序列化对象时排除 null 字段
- **WHEN** 调用 `JacksonUtils.toJson(obj)`，其中 `obj` 的某个字段值为 `null`
- **THEN** 返回的 JSON 字符串中不包含该字段

#### Scenario: 反序列化时忽略目标类不存在的字段
- **WHEN** 调用 `JacksonUtils.toObj(json, SomeClass.class)`，其中 `json` 包含 `SomeClass` 未定义的字段
- **THEN** 反序列化正常完成，多余字段被忽略，不抛出异常

#### Scenario: 反序列化 String 为指定 Class
- **WHEN** 调用 `JacksonUtils.toObj(jsonString, SomeClass.class)`
- **THEN** 返回一个 `SomeClass` 实例，字段值与 `jsonString` 内容对应

#### Scenario: 反序列化 String 为带泛型的集合类型
- **WHEN** 调用 `JacksonUtils.toObj(jsonString, new TypeReference<List<SomeClass>>() {})`
- **THEN** 返回一个 `List<SomeClass>`，元素与 `jsonString` 中的 JSON 数组内容对应

#### Scenario: 反序列化 byte[] 与 InputStream
- **WHEN** 调用 `JacksonUtils.toObj(bytes, SomeClass.class)` 或 `JacksonUtils.toObj(inputStream, SomeClass.class)`，其中 `bytes`/`inputStream` 内容为合法 JSON
- **THEN** 均返回一个内容对应的 `SomeClass` 实例，行为与传入等价 JSON 字符串一致

#### Scenario: 对象间类型转换
- **WHEN** 调用 `JacksonUtils.convert(sourceObj, TargetClass.class)`，其中 `sourceObj` 的字段与 `TargetClass` 字段可对应
- **THEN** 返回一个 `TargetClass` 实例，字段值来自 `sourceObj`（等价于先 `toJson` 再 `toObj`，但不经过 JSON 字符串中转）

#### Scenario: 日期字段按统一格式序列化
- **WHEN** 调用 `JacksonUtils.toJson(obj)`，其中 `obj` 包含一个 `LocalDateTime` 字段
- **THEN** 该字段在输出 JSON 中被格式化为 `yyyy-MM-dd HH:mm:ss` 形式的字符串，而不是时间戳或默认的 ISO-8601 格式

#### Scenario: 序列化或反序列化失败时抛出异常
- **WHEN** 调用 `JacksonUtils.toObj(json, SomeClass.class)`，其中 `json` 不是合法 JSON 或与 `SomeClass` 结构不兼容
- **THEN** 方法抛出运行时异常，不返回 `null`，调用方可自行捕获并决定降级策略
