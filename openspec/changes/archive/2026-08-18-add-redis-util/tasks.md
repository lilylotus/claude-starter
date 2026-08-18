## 1. 字符串 Redis 工具类

- [x] 1.1 新建 `backend/src/main/java/cn/nihility/rbac/common/util/RedisUtils.java`：
      静态持有 `StringRedisTemplate`，提供字符串 key 读写（`set`/`get`，含带过期时间
      重载）、Hash 字段读写（`putHash`/`putHashField`/`hashEntries`）、`delete`/
      `hasKey`/`expire`/`deleteHashField`。
- [x] 1.2 基于 `JacksonUtils` 补充对象存取方法：`setObject`/`getObject`（字符串 key）、
      `putHashObject`/`getHashObject`（Hash 字段），支持 `Class`/`TypeReference` 两种
      目标类型重载。
- [x] 1.3 新建 `backend/src/main/java/cn/nihility/rbac/common/config/
      RedisUtilsInitializer.java`：`@Component` + `@PostConstruct`，把 Spring Boot
      自动装配的 `StringRedisTemplate` 推送给 `RedisUtils`。

## 2. 对象 Redis 工具类

- [x] 2.1 新建 `backend/src/main/java/cn/nihility/rbac/common/config/
      RedisObjectTemplateConfig.java`：定义 `objectRedisTemplate` Bean
      （`RedisTemplate<String, Object>`，key/hashKey 用 `StringRedisSerializer`，
      value/hashValue 用 `Jackson2JsonRedisSerializer` 包装专用 `ObjectMapper`）。
- [x] 2.2 新建 `backend/src/main/java/cn/nihility/rbac/common/util/
      RedisObjectUtils.java`：静态持有 `objectRedisTemplate`，提供对象存取方法
      （`set`/`get`/`putHash`/`getHash`/`hashEntries`/`delete`/`hasKey`/`expire`/
      `deleteHashField`），`get`/`getHash` 支持按 `Class`/`TypeReference` 转换目标类型
      （内部用 `JacksonUtils.convert`）。
- [x] 2.3 新建 `backend/src/main/java/cn/nihility/rbac/common/config/
      RedisObjectUtilsInitializer.java`：`@Component` + `@PostConstruct`，按
      `@Qualifier("objectRedisTemplate")` 精确注入并推送给 `RedisObjectUtils`。

## 3. 存量代码迁移

- [x] 3.1 `TokenServiceImpl`：去掉 `StringRedisTemplate` 字段注入，改为静态调用
      `RedisUtils`（`putHash`/`expire`/`set`/`get`/`hashEntries`/`putHashField`）。
- [x] 3.2 `CasTicketService`：去掉 `StringRedisTemplate` 字段注入，票据签发/校验改用
      `RedisUtils.setObject`/`getObject`（`CasTicketPayload`）。
- [x] 3.3 `OAuthTokenService`：去掉 `StringRedisTemplate` 字段注入，code/token/refresh
      三类凭证的读写改用 `RedisUtils.setObject`/`getObject`。
- [x] 3.4 `SsoSessionService`：去掉 `StringRedisTemplate` 字段注入，会话签发/校验/失效
      改用 `RedisUtils.set`/`get`/`delete`。

## 4. 验证

- [x] 4.1 新增 `RedisUtilsTest`/`RedisObjectUtilsTest` 单元测试，覆盖读写、对象存取、
      未初始化时的 `IllegalStateException` 场景。
- [x] 4.2 同步更新 `TokenServiceImplTest`（改用 `RedisUtils.current()`/`configure()`
      保存/还原现场，避免污染同一 JVM 内其它测试）。
- [x] 4.3 `./gradlew build` 编译通过（已随 `b2a94f0` 提交合入）。

## 5. 文档补建（本 change 范围）

- [x] 5.1 基于 `b2a94f0` 的真实 diff 补建 proposal.md / design.md / specs / tasks.md，
      不改动已合入的代码。
