## Why

补建 change：本 change 记录的实现已经在 `b2a94f0 feat(redis): 添加redis全局工具类` 提交中完成
并合入，当时未先创建 OpenSpec 文档就直接编码，违反了本仓库 CLAUDE.md 里"编码前必须先建
proposal/design/tasks"的约定。本 change 是基于该提交的真实 diff 事后补齐文档，不改动任何
已合入的代码。

在补建之前，后端 SSO/认证相关模块（`TokenServiceImpl`、`CasTicketService`、
`OAuthTokenService`、`SsoSessionService`）各自直接注入 `StringRedisTemplate`、手写
`opsForValue()`/`opsForHash()` 样板代码，涉及对象存取时还要各自调用 `JacksonUtils`
做序列化/反序列化，缺少统一封装。

## What Changes

- 新增 `common/util/RedisUtils.java`：基于 `StringRedisTemplate` 的字符串/Hash 操作工具类，
  并内置基于 `JacksonUtils` 的对象存取能力（`setObject`/`getObject`/`putHashObject`/
  `getHashObject`，写入前序列化为 JSON 字符串、读取后反序列化）。
- 新增 `common/util/RedisObjectUtils.java`：基于专用 `objectRedisTemplate`
  （`RedisTemplate<String, Object>`，value/hashValue 用 `Jackson2JsonRedisSerializer`
  包装）的对象存取工具类，Redis 客户端层面完成序列化，不需要手动转 JSON 字符串。
- 新增 `common/config/RedisObjectTemplateConfig.java`：定义 `objectRedisTemplate` Bean，
  与 Spring Boot 自动装配的默认 `redisTemplate` Bean 共存、不冲突。
- 新增 `common/config/RedisUtilsInitializer.java` / `RedisObjectUtilsInitializer.java`：
  在 Spring 容器启动阶段分别把 `StringRedisTemplate`/`objectRedisTemplate` 推送给
  `RedisUtils`/`RedisObjectUtils` 的静态字段。
- 存量代码迁移到 `RedisUtils`：`TokenServiceImpl`、`CasTicketService`、
  `OAuthTokenService`、`SsoSessionService` 不再各自直接注入 `StringRedisTemplate`，
  改为静态调用 `RedisUtils`。

## Capabilities

### New Capabilities
（无，归入下方已有的 `backend-common-utilities` 能力）

### Modified Capabilities
- `backend-common-utilities`: 新增统一的全局 Redis 操作工具类（字符串/Hash 读写、
  对象序列化存取、过期时间管理），供业务代码替代各自直接注入
  `StringRedisTemplate`/`RedisTemplate` 的做法。
(无)

## Impact

- 新增文件：`common/util/RedisUtils.java`、`common/util/RedisObjectUtils.java`、
  `common/config/RedisObjectTemplateConfig.java`、
  `common/config/RedisUtilsInitializer.java`、
  `common/config/RedisObjectUtilsInitializer.java`，以及对应的
  `RedisUtilsTest.java`/`RedisObjectUtilsTest.java`。
- 修改文件：`TokenServiceImpl`、`CasTicketService`、`OAuthTokenService`、
  `SsoSessionService`（及各自的单元测试）迁移到 `RedisUtils`。
- 不涉及数据库表结构、API 契约、前端改动。
- 不新增第三方依赖（`spring-boot-starter-data-redis` 已在项目中，具体引入时间早于本次
  改动，不在本 change 范围内）。
