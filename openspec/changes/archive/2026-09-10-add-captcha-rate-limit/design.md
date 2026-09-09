## Context

登录相关接口（口令登录 `password-login-auth`、SSO 短信/扫码登录 `sso-login-methods`）目前没有
人机识别与调用频率控制手段。本 change 先独立建好两个通用基础能力，不改动任何现有登录接口，
接入工作放到后续 change。项目已有 Redis 基础设施（`spring-boot-starter-data-redis` +
`common/util/RedisUtils`），SMS 验证码发送冷却/每日上限已经用 `RedisUtils.increment` 做过
固定窗口计数的先例（`sso/sms/service/impl/SmsCodeServiceImpl`），本次限流能力延续同一模式而
不是引入 sync 模块里那种进程内令牌桶（`sync/openapi/support/SyncRateLimiter`）——因为登录类
接口需要在多实例部署下限流状态一致，进程内状态做不到。

## Goals / Non-Goals

**Goals:**
- 图形验证码：生成图片（字符混合 / 算式两种模式，字符个数可配置）+ 一次性校验，答案存 Redis。
- 接口限流：声明式注解 + AOP 切面，按 IP 维度、Redis 固定窗口计数，方法级可覆盖全局默认配置。
- 两者都是独立可测试的通用能力，不依赖具体业务上下文即可编译通过、可单测覆盖。

**Non-Goals:**
- 不修改口令登录、SSO 短信登录、SSO 扫码登录任何一个现有接口的行为（不在这些接口上标注
  `@RateLimit`，不要求提交验证码）——留给后续 change。
- 不实现按用户 id / 自定义 key 维度的限流（预留扩展点，但本次只落地 IP 维度）。
- 图形验证码不引入新第三方依赖（`java.awt` 属于 JDK 内置，Redis 已是现有依赖）；接口限流
  切面最终经用户确认新增了 `spring-boot-starter-aop` 一个依赖，用于支持字面意义的
  `@Aspect`/`@Around` 写法（见 Decision 10），属于本次唯一新增的第三方依赖。
- 不做滑动窗口/漏桶等更精细的限流算法，固定窗口即可满足当前"防刷"目的。

## Decisions

### Decision 1：图形验证码渲染用 JDK 内置 `java.awt`，不引入第三方验证码库
业界常见做法是引入 `kaptcha`/`easy-captcha` 等库，但这些库年久失修、部分对 Spring Boot 3 /
Jakarta EE 命名空间兼容性存疑，且 `build.gradle` 新增依赖需要先跟用户确认（项目约定）。
`BufferedImage` + `Graphics2D` 完全够用（画随机字符/算式文本、干扰线、噪点），控制在
`captcha/support/CaptchaImageRenderer` 一个类里，避免过度设计。

### Decision 2：验证码 id 由后端生成（UUID），前端不感知具体存储 key
`GET /api/captcha/image` 直接返回 `{ captchaId, imageBase64 }`；Redis key 格式
`captcha:image:{captchaId}`，value 用 `RedisUtils.setObject` 存一个内部 payload（含类型 + 答案），
过期时间即校验有效期。校验方法 `ImageCaptchaService#verify(captchaId, answer)` 先 `getObject`
再无条件 `delete`（不区分成功失败都删，满足"一次性"要求），避免额外的"已使用"标记字段。

### Decision 3：字符模式的候选字符集固定剔除易混淆字符
候选字符集 = `A-Z` + `a-z` + `0-9` 去掉 `0`、`1`、`O`/`o`、`I`/`i`、`l`（大小写一并剔除，不做
"只留其中一个大小写"这类特例），在 `CaptchaContentGenerator` 内以一个常量数组维护最终候选
集合，而不是运行时每次过滤，避免生成阶段的额外开销和潜在遗漏。

### Decision 5：算式模式的除法只生成整除组合
先随机运算符，若为除法则先随机结果与除数、反推被除数（`被除数 = 结果 × 除数`），保证结果为
整数，避免向用户展示小数题目造成体验歧义。操作数范围限制在 1-9（乘除）/ 1-50（加减），保持
心算难度可控。

### Decision 6：限流用固定窗口计数，key 用 `INCR + 首次自增设过期`，直接复用现有 `RedisUtils.increment`
与 `SmsCodeServiceImpl` 每日上限校验完全同构：`rate-limit:{routeKey}:{ip}` 作为 key，
`RedisUtils.increment(key, windowSeconds, TimeUnit.SECONDS)` 原子自增，若返回值超过最大请求数
则判定限流。固定窗口在窗口边界附近允许短暂突发（如 60 秒窗口在第 59 秒和第 61 秒各发一批），
这是已知的固定窗口算法局限，考虑到这里的目标是"防止脚本类批量刷"而非精确限速，可接受，比
滑动窗口/令牌桶实现更简单、和现有代码风格一致。

### Decision 7：`routeKey` 由 AOP 切面基于"类名#方法名"自动生成，不要求业务方手动指定
`@RateLimit` 注解只暴露 `windowSeconds`（默认 -1 表示使用全局默认）与 `maxRequests`（默认 -1
同理）两个可选属性，`routeKey` 内部按 `目标类全限定名 + "#" + 方法名` 拼接，天然按接口维度
隔离、业务方零配置即可获得唯一 key，避免手写字符串 key 拼错导致不同接口共享计数。

### Decision 8：IP 获取直接复用已有的 `ClientRequestUtils.resolveClientIp`
仓库里 `common/util/ClientRequestUtils#resolveClientIp(HttpServletRequest)` 已经实现了解析
`X-Forwarded-For`/`X-Real-IP` 等请求头获取客户端真实 IP 的逻辑（登录日志、操作日志等模块已在
用），限流切面直接复用，不新增重复的 IP 解析工具类。

### Decision 9：限流触发时抛业务异常，由现有全局异常处理器统一转换为 `{code,message,data}`
新增 `RateLimitExceededException extends BusinessException`，AOP 切面判定超限后直接抛出，交由
已有的 `GlobalExceptionHandler`/`GlobalResponseAdvice` 统一包装，不在切面里手写
`HttpServletResponse` 写响应体，保持和项目里其它业务异常处理方式一致（如
`SyncRateLimiter.RateLimitedException` 的做法，但本次不需要 `Retry-After` 语义，登录场景不必
暴露精确重试时间，避免帮攻击者调参）。

### Decision 10：`RateLimitAspect` 采用字面意义的 `@Aspect` + `@Around`，新增
`spring-boot-starter-aop` 依赖
首次实现时项目 classpath 缺失 `org.aspectj:aspectjweaver`（`@Aspect`/`@Around` 注解定义
所在依赖），而新增依赖需要先跟用户确认，因此临时改为纯 Spring AOP 原生等价实现：
`RateLimitAspect` 实现 `org.aopalliance.intercept.MethodInterceptor`，由
`RateLimitAopConfig` 用 `AnnotationMatchingPointcut` + `DefaultPointcutAdvisor` 手动注册
为 `Advisor`。用户后续明确同意新增 `spring-boot-starter-aop` 依赖（连带引入
`aspectjweaver`），因此改回字面意义的 `@Aspect` + `@Component` 写法，用
`@Around("@annotation(cn.nihility.rbac.ratelimit.annotation.RateLimit)")` 环绕标注了
`@RateLimit` 的方法；`RateLimitAopConfig` 随之删除——Spring Boot 检测到 classpath 有
`org.aspectj.weaver.Advice` 后，`AopAutoConfiguration` 自动开启 AspectJ 自动代理，不再
需要手动注册 `Advisor`。运行时行为（"标注了 `@RateLimit` 的方法被拦截限流判断，方法级参数
覆盖优先于全局默认配置"）保持不变，仅实现写法从"AOP 联盟原生接口"切换为"AspectJ 注解语法"。

## Risks / Trade-offs

- [固定窗口边界突发] → 已在 Decision 6 说明，验收标准是"挡住持续性批量请求"而非"绝对速率控制"，
  可接受；后续如需更严格可换算法，不影响本次接口/注解形状。
- [Redis 不可用时限流/验证码能力整体不可用] → 与现有 SMS 验证码、二维码登录同样依赖 Redis，
  项目当前未对 Redis 做降级设计，本次保持一致，不额外处理（超出本次范围）。
- [验证码图片文本被 OCR 破解] → 干扰线/噪点只做基础混淆，不追求对抗高强度 OCR，符合"防止
  低成本脚本暴力破解"的定位，不过度设计。
- [IP 维度限流对 NAT/代理出口共享 IP 的误伤] → 已知局限，属于按 IP 限流的通用取舍，本次不
  实现更复杂的维度组合，记录在此供后续如需按用户维度扩展时参考。

## Migration Plan

纯新增能力，无需数据迁移、无需灰度开关；新增的 Redis key 前缀（`captcha:image:*`、
`rate-limit:*`）与现有 key 空间（`sms:*`、`qrcode:*`、锁 `lock:*`）不冲突。部署上没有顺序依赖，
随正常发布流程上线即可；因未接入任何现有接口，本次上线对现有登录行为零影响。

## Open Questions

- 图形验证码干扰线/噪点密度、字体、颜色等具体视觉参数留到实现阶段按观感调整，不在此处提前
  定死数值（design 只约束"要有基础干扰"这一行为，不锁死像素级细节）。
