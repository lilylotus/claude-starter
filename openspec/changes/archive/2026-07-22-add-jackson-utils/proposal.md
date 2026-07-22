## Why

调查确认：项目目前有 3 处直接注入 `com.fasterxml.jackson.databind.ObjectMapper` 手写序列化/反序列化逻辑（`GlobalResponseAdvice` 手动序列化 `Result`、`OperationLogRecorderImpl` 把字段变更 diff 序列化为 JSON 存库、`OperationLogQueryServiceImpl` 反序列化 `change_detail` 列），三处各自处理异常、各自拼 `TypeFactory`，没有统一封装；且都吃 Spring Boot 默认自动装配的 `ObjectMapper`（未做任何定制，如未排除 null 字段、未针对 `LocalDateTime`/`LocalDate` 等日期类型配置格式，全仓库也没有任何 `@JsonFormat` 注解）。后续新增业务（尤其是需要手动 JSON 序列化的场景，如更多历史快照/缓存/第三方对接）会继续重复这套样板代码。本次改动新建一个统一的 `JacksonUtils` 静态工具类，把 JSON 初始化配置、常用序列化/反序列化/类型转换方法收敛到一处，并把现有 3 处直接使用 `ObjectMapper` 的代码改为调用它。

## What Changes

- 新增 `cn.nihility.rbac.common.util.JacksonUtils`：内部维护一个独立的、私有静态 `ObjectMapper` 实例（仅供本工具类自身使用，不替换/不影响 Spring 自动装配、供 HTTP 响应真正序列化用的那个 `ObjectMapper` Bean——详见下方"配置影响范围"决策），提供：
  - 初始化配置：序列化时排除值为 `null` 的字段（`JsonInclude.Include.NON_NULL`）；反序列化时遇到目标类不存在的字段不报错、直接忽略（关闭 `FAIL_ON_UNKNOWN_PROPERTIES`）。
  - `toObj(...)`：把 JSON 内容（来源可以是 `String`、`byte[]`、`InputStream` 三种）反序列化为对象，目标类型支持 `Class<T>`、`TypeReference<T>`、`java.lang.reflect.Type` 三种描述方式，共 9 个重载。
  - `toJson(Object obj)`：把任意对象序列化为 JSON 字符串。
  - `convert(...)`：把一个已有对象（如 `Map`、另一个 DTO）转换为另一种类型的对象，目标类型支持 `Class<T>`、`TypeReference<T>`、`com.fasterxml.jackson.databind.JavaType` 三种描述方式，共 3 个重载。
  - 日期类型序列化/反序列化配置：为 `LocalDateTime`（`yyyy-MM-dd HH:mm:ss`）、`LocalDate`（`yyyy-MM-dd`）、`LocalTime`（`HH:mm:ss`）注册自定义格式的序列化器/反序列化器（基于 `JavaTimeModule`），为 `java.util.Date` 配置统一的 `DateFormat`（`yyyy-MM-dd HH:mm:ss`），并关闭"日期序列化为时间戳"（`WRITE_DATES_AS_TIMESTAMPS`）。
- 把现有 3 处直接注入/手写 `ObjectMapper` 的代码，改为调用 `JacksonUtils` 的对应静态方法：
  - `GlobalResponseAdvice.writeAsJsonString`：`objectMapper.writeValueAsString(...)` 改为 `JacksonUtils.toJson(...)`。
  - `OperationLogRecorderImpl.toJson`：同上。
  - `OperationLogQueryServiceImpl.parseChangeDetail`：`objectMapper.readValue(...)` + 手拼 `CollectionType` 改为 `JacksonUtils.toObj(changeDetail, new TypeReference<List<OperationLogFieldChangeVO>>() {})`。
  - 上述三个类不再注入 `ObjectMapper`（移除该构造参数/字段），对应的两个单元测试（`OperationLogRecorderImplTest`、`OperationLogQueryServiceImplTest`）里 `new XxxImpl(mapper, new ObjectMapper())` 的两参构造调用同步改为单参。

## Capabilities

### New Capabilities
- `backend-common-utilities`：新增统一的 JSON 序列化/反序列化/类型转换工具类，作为后端公共基础设施能力的一部分（对齐 CLAUDE.md 里 `common/` 目录已有 `Result`/`GlobalResponseAdvice`/`BusinessException`/`GlobalExceptionHandler` 这类跨模块复用组件的定位）。

### Modified Capabilities
（无——本次改动不涉及任何已有业务 capability 的对外行为变化；三处调用点重构后行为等价，且已确认当前没有任何 Controller 方法直接返回裸 `String`，`GlobalResponseAdvice` 里手动序列化的分支在生产环境下暂无实际触发路径。）

## Impact

- 新增文件：`backend/src/main/java/cn/nihility/rbac/common/util/JacksonUtils.java`。
- 修改文件：`GlobalResponseAdvice.java`、`OperationLogRecorderImpl.java`、`OperationLogQueryServiceImpl.java`（移除 `ObjectMapper` 依赖，改调用 `JacksonUtils`）、`OperationLogRecorderImplTest.java`、`OperationLogQueryServiceImplTest.java`（构造函数调用同步更新为单参）。
- 不新增任何 Gradle 依赖：`jackson-databind`、`jackson-datatype-jsr310`（支持 `LocalDateTime`/`LocalDate` 等）均已通过 `spring-boot-starter-web` 传递引入，已在 classpath 上。
- **明确不做的事**（已与用户确认，选择"仅工具类内部生效"方案）：不新增全局 `Jackson2ObjectMapperBuilderCustomizer`、不修改 `application.yml`，`JacksonUtils` 内部的 `ObjectMapper` 与 Spring 自动装配、真正用于 HTTP 响应序列化的 `ObjectMapper` Bean 是两个独立实例、互不影响。这意味着：本次改动完成后，普通 `@RestController` 接口返回的 JSON（响应体里的 `null` 字段是否保留、日期字段的实际输出格式）**不会**发生任何变化，只有显式调用 `JacksonUtils` 的代码路径才会应用新配置。
