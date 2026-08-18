## Context

补建文档：以下内容基于 `b2a94f0` 提交的真实实现整理，不是实现前的规划——该 change 的
代码已经合入，本文档只是让 proposal/design/tasks 与已合入代码保持一致。

后端已有一批全局工具类放在 `common/util/` 下（`JacksonUtils`、`HttpClientUtils` 等），
均以静态方法/静态持有实例的方式对外暴露。Redis 相关工具类沿用同样的组织方式，但
`StringRedisTemplate`/`RedisTemplate` 必须由 Spring Boot 按 `spring.data.redis.*`
配置自动装配，不像 `HttpClientUtils` 那样有可脱离容器工作的合理默认值，因此需要一个
"Spring 组件在容器启动阶段把已装配好的模板推送给静态工具类"的初始化环节
（`RedisUtilsInitializer`/`RedisObjectUtilsInitializer`）。

## Goals / Non-Goals

**Goals:**
- 提供 `RedisUtils`：基于 `StringRedisTemplate` 的字符串/Hash 读写工具类，收敛
  `opsForValue()`/`opsForHash()` 样板代码，并内置基于 `JacksonUtils` 的对象存取能力
  （写入前序列化为 JSON 字符串、读取后反序列化）。
- 提供 `RedisObjectUtils`：基于专用 `objectRedisTemplate`
  （value/hashValue 用 `Jackson2JsonRedisSerializer` 包装）的对象存取工具类，Redis
  客户端层面完成序列化，调用方不需要手动转 JSON 字符串。
- 迁移现有 SSO/认证相关服务（`TokenServiceImpl`、`CasTicketService`、
  `OAuthTokenService`、`SsoSessionService`）到 `RedisUtils`，去掉各自直接注入
  `StringRedisTemplate` 的写法。

**Non-Goals:**
- 不覆盖/替换 Spring Boot 自动装配的默认 `redisTemplate` Bean
  （`RedisTemplate<Object, Object>`，本项目未使用），`objectRedisTemplate` 是独立
  新增的 Bean，二者共存不冲突。
- 不做分布式锁、限流、发布订阅等 Redis 高级特性封装，只做基础的 key/hash 读写 +
  过期时间管理。
- 不强制迁移全部 Redis 使用点，本次只迁移了 SSO/认证相关的四个服务类；其余（如有）
  后续按需迁移。

## Decisions

1. **静态字段持有实例 + Spring `@PostConstruct` 推送，而不是注册为 Spring Bean**
   - 与仓库现有工具类（`HttpClientUtils` 等）风格一致：`RedisUtils`/`RedisObjectUtils`
     本身不是 Spring Bean，不做构造器注入，调用方直接
     `RedisUtils.set(...)`/`RedisObjectUtils.set(...)` 静态调用。
   - 因为底层 `StringRedisTemplate`/`RedisTemplate` 必须由 Spring 自动装配，工具类
     无法在类加载时就拿到实例，改为单独的 `@Component`
     （`RedisUtilsInitializer`/`RedisObjectUtilsInitializer`）在 `@PostConstruct`
     阶段把已装配好的模板推送给工具类的静态字段（`volatile` 修饰，保证可见性）。
   - 未初始化时调用工具类方法会抛出 `IllegalStateException`（而不是让调用方拿到
     难以定位根因的 `NullPointerException`），提示"Spring 容器尚未启动"。

2. **两套工具类：`RedisUtils`（字符串）+ `RedisObjectUtils`（对象 Redis 模板）**
   - `RedisUtils` 基于 Spring Boot 自动装配的 `StringRedisTemplate`，值全部是字符串；
     对象存取通过 `JacksonUtils.toJson`/`toObj` 手动转 JSON 字符串中转
     （`setObject`/`getObject`/`putHashObject`/`getHashObject`）。
   - `RedisObjectUtils` 基于新增的 `objectRedisTemplate` Bean
     （`RedisObjectTemplateConfig` 定义，value/hashValue 用
     `Jackson2JsonRedisSerializer` 包装），可以直接 put/get 对象实例，序列化在
     Redis 客户端层面完成，不经过手动 JSON 字符串中转；反序列化统一按
     `Object.class` 处理（Jackson 处理未知目标类型的标准行为，复杂对象读回为
     `LinkedHashMap`），因此提供按 `Class`/`TypeReference` 转换目标类型的重载，
     内部用 `JacksonUtils.convert` 就地转换。
   - 两套工具类并存是因为二者定位不同：`RedisUtils` 面向"值本来就是字符串"的既有
     使用点（如令牌反查记录）；`RedisObjectUtils` 面向"想直接存取对象实例、不关心
     底层是 JSON 字符串"的新使用点。当前实现是最小可用版本，迁移只覆盖了
     `RedisUtils` 覆盖的既有 SSO/认证使用点，`RedisObjectUtils` 暂无消费方，留作
     后续对象存取场景使用。

3. **`objectRedisTemplate` 命名与序列化器配置**
   - key/hashKey 仍用 `StringRedisSerializer`（与 `StringRedisTemplate` 保持一致的
     key 可读性，便于用 `redis-cli` 直接查看 key）；value/hashValue 用
     `Jackson2JsonRedisSerializer` 包装的专用 `ObjectMapper`（日期格式、
     `NON_NULL` 序列化、忽略未知字段等配置对齐 `JacksonUtils` 的既有约定）。
   - Bean 名称为 `objectRedisTemplate`，不覆盖 Spring Boot 默认的 `redisTemplate`
     Bean，避免和框架默认行为产生冲突；`RedisObjectUtilsInitializer` 按 Bean 名称
     精确注入（构造器参数 `@Qualifier`，未用 Lombok
     `@RequiredArgsConstructor`——本仓库未配置 `lombok.config` 的
     `copyableAnnotations`，字段上的 `@Qualifier` 不会被 Lombok 复制到生成的构造器
     参数上，会导致两个同为 `RedisTemplate` 原始类型的候选 Bean 之间装配歧义）。

4. **既有服务迁移到 `RedisUtils`**
   - `TokenServiceImpl`/`CasTicketService`/`OAuthTokenService`/`SsoSessionService`
     去掉各自的 `StringRedisTemplate` 字段注入，改为静态调用 `RedisUtils`；涉及对象
     存取的（如 `CasTicketPayload`/`OAuthCodePayload` 等）改用 `RedisUtils.setObject`/
     `getObject`，去掉各自手写的 `JacksonUtils.toJson`/`toObj` 调用。
   - `RedisUtils` 额外提供 `current()` 方法，仅供不依赖 Spring 容器的纯 Mockito
     单元测试（如 `TokenServiceImplTest`）在改用 mock 打桩前保存现场、用例结束后
     还原，避免同一 JVM 内顺序执行的其它测试意外拿到前一个测试留下的 mock；生产
     代码不应依赖该方法。

## Risks / Trade-offs

- [风险] `RedisUtils`/`RedisObjectUtils` 是 JVM 级静态单例，脱离 Spring 容器（如未启动
  容器的纯单元测试）调用会抛 `IllegalStateException`
  → 缓解：`RedisUtils` 提供 `current()`/`configure()` 供测试保存/还原现场；
  `RedisObjectUtilsTest`/`RedisUtilsTest` 已覆盖相关用例。
- [风险] `RedisObjectUtils` 当前没有实际消费方（迁移只覆盖了 `RedisUtils`），存在
  "先造好但暂时没人用"的情况
  → 缓解：属于本次改动明确的一部分（提供两套工具类中的一套供后续对象存取场景使用），
  非本次引入的额外风险；后续若长期无消费方，可在后续 change 中评估是否精简。
- [风险] 本次实现未先走 OpenSpec 流程直接编码合入，文档与代码曾经不同步
  → 缓解：本 change 就是为解决该问题而补建的文档，后续同类改动应先建
  proposal/design/tasks 再编码。

## Open Questions

（无）
