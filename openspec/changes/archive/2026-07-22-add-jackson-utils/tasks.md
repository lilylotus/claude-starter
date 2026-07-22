## 1. 新增 `JacksonUtils`

- [x] 1.1 新建 `backend/src/main/java/cn/nihility/rbac/common/util/JacksonUtils.java`：私有静态 `ObjectMapper MAPPER`，私有构造函数禁止实例化
- [x] 1.2 初始化配置：`NON_NULL`（序列化排除 null）、关闭 `FAIL_ON_UNKNOWN_PROPERTIES`（反序列化忽略未知字段）、关闭 `WRITE_DATES_AS_TIMESTAMPS`
- [x] 1.3 日期类型配置：`LocalDateTime`（`yyyy-MM-dd HH:mm:ss`）、`LocalDate`（`yyyy-MM-dd`）、`LocalTime`（`HH:mm:ss`）通过 `JavaTimeModule` 注册自定义序列化器/反序列化器；`java.util.Date` 通过 `setDateFormat` 配置 `yyyy-MM-dd HH:mm:ss`
- [x] 1.4 `toJson(Object obj)`：序列化为 JSON 字符串，失败时包装为 `IllegalStateException` 抛出
- [x] 1.5 `toObj`：9 个重载（来源 `String`/`byte[]`/`InputStream` × 目标类型描述 `Class`/`TypeReference`/`Type`），失败时包装为 `IllegalStateException` 抛出
- [x] 1.6 `convert`：3 个重载（目标类型描述 `Class`/`TypeReference`/`JavaType`），失败时包装为 `IllegalStateException` 抛出
- [x] 1.7 类注释/方法 Javadoc 遵循 `java-code-style` skill 规范；类注释明确说明"仅用于内部序列化场景，不接管 Spring HTTP 响应序列化"

## 2. 迁移现有 3 处 `ObjectMapper` 使用点

- [x] 2.1 `GlobalResponseAdvice.writeAsJsonString`：改用 `JacksonUtils.toJson(...)`，移除自身的 `try/catch JsonProcessingException`（`JacksonUtils` 已统一转换为 `IllegalStateException`），移除构造参数 `ObjectMapper objectMapper` 字段与 `@RequiredArgsConstructor`（类内已无 final 字段，注解一并移除）
- [x] 2.2 `OperationLogRecorderImpl.toJson`：内部实现改为 `JacksonUtils.toJson(changes)`，保留自身 `try/catch` 降级返回 `"[]"` 的行为不变，移除 `ObjectMapper objectMapper` 字段
- [x] 2.3 `OperationLogQueryServiceImpl.parseChangeDetail`：内部实现改为 `JacksonUtils.toObj(changeDetail, new TypeReference<List<OperationLogFieldChangeVO>>() {})`，保留自身 `try/catch` 降级返回 `List.of()` 的行为不变，移除 `ObjectMapper objectMapper` 字段

## 3. 同步更新单元测试

- [x] 3.1 `OperationLogRecorderImplTest`：构造调用由 `new OperationLogRecorderImpl(operationLogMapper, new ObjectMapper())` 改为 `new OperationLogRecorderImpl(operationLogMapper)`，移除不再需要的 `ObjectMapper` import；另外 `recordCreate_shouldTreatBeforeAsAllNull`/`recordDelete_shouldTreatAfterAsAllNull` 两个用例原本断言持久化 JSON 字符串里字面出现 `"oldValue":null`/`"newValue":null`，因 `JacksonUtils` 的 `NON_NULL` 配置生效后该键会被直接省略，同步把断言改为 `doesNotContain("\"oldValue\"")`/`doesNotContain("\"newValue\"")`（反序列化回结构化对象时该字段仍会正确还原为 `null`，只是持久化的原始 JSON 字符串形态变化，属于需求 1 的预期行为，非缺陷）
- [x] 3.2 `OperationLogQueryServiceImplTest`：同上，`new OperationLogQueryServiceImpl(operationLogMapper, new ObjectMapper())` 改为 `new OperationLogQueryServiceImpl(operationLogMapper)`

## 4. 验证

- [x] 4.1 `backend/gradlew compileJava` 编译通过
- [x] 4.2 `backend/gradlew test` 全量测试通过（122 个测试全绿，含 `OperationLogRecorderImplTest`、`OperationLogQueryServiceImplTest`）
- [x] 4.3 确认未新增任何 Gradle 依赖（`build.gradle` 无改动）

## 5. 实施过程记录

- [x] 5.1 `JacksonUtils` 最终包名调整为 `cn.nihility.rbac.common.util`（而非设计阶段最初写的 `cn.nihility.rbac.common`），已同步更新 proposal.md、本文件与 spec delta 中的包名/路径引用，design.md 未硬编码具体包路径，无需改动
