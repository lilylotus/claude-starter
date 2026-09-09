## 1. 图形验证码——配置与生成

- [x] 1.1 新增 `cn.nihility.rbac.captcha.config.RbacCaptchaProperties`（`@ConfigurationProperties(prefix = "rbac.captcha")`）：
      验证码类型（字符/算式）、字符个数（默认 4）、图片宽高、有效期秒数（默认 300）等配置项，
      并在 `application.yml` 补充 `rbac.captcha.*` 默认值。
- [x] 1.2 新增 `cn.nihility.rbac.captcha.constant.CaptchaType` 枚举（`CHARACTER`/`ARITHMETIC`）。
- [x] 1.3 新增 `cn.nihility.rbac.captcha.support.CaptchaContentGenerator`：按配置生成验证码内容
      与答案——字符模式候选字符集固定剔除易混淆字符（数字 `0`、`1`，字母 `O`/`o`、`I`/`i`、
      `l`，大小写一并剔除）后再随机生成指定长度的字符串；算式模式生成加减乘除算式文本与整数
      答案（除法需先随机结果与除数、反推被除数保证整除，操作数范围乘除 1-9、加减 1-50）。
- [x] 1.4 新增 `cn.nihility.rbac.captcha.support.CaptchaImageRenderer`：基于 `java.awt`
      （`BufferedImage`/`Graphics2D`）把验证码文本渲染成图片并输出为 base64 字符串，附加基础
      干扰线/噪点。
- [x] 1.5 新增 `cn.nihility.rbac.captcha.dto.CaptchaImageVO`（`captchaId`、`imageBase64`）。
- [x] 1.6 新增 `cn.nihility.rbac.captcha.service.ImageCaptchaService` 接口与
      `impl.ImageCaptchaServiceImpl`：`generate()` 生成验证码内容+图片，UUID 作为 `captchaId`，
      用 `RedisUtils.setObject(key, payload, expireSeconds, TimeUnit.SECONDS)` 写入
      `captcha:image:{captchaId}`（payload 含类型与答案）。答案+类型的内部存储载荷落在
      `cn.nihility.rbac.captcha.support.CaptchaAnswerPayload`（record），任务描述未指定具体
      包名，按"内部实现细节、不对外暴露"归入 `support` 包。
- [x] 1.7 新增 `cn.nihility.rbac.captcha.controller.ImageCaptchaController`：
      `GET /api/captcha/image` 调用 `generate()` 返回 `CaptchaImageVO`，加 `@Tag`/`@Operation`
      注解。额外把该路径补进 `IdentityAuthFilter.FULL_WHITELIST`，满足 spec"无需身份校验即可
      访问"的要求（任务描述未显式提及，但属于该接口可用的必要前提）。

## 2. 图形验证码——校验

- [x] 2.1 在 `ImageCaptchaService` 增加 `verify(String captchaId, String answer)` 方法：按
      `captchaId` 从 Redis 读取 payload，不存在直接返回 `false`；存在则按类型比较（字符模式
      忽略大小写，算式模式按数值精确匹配），无论结果如何都立即 `RedisUtils.delete` 该 key。
- [x] 2.2 补充单元测试 `ImageCaptchaServiceImplTest`：覆盖生成默认/自定义长度、生成内容不含
      易混淆字符、算式除法整除、校验成功/失败/不存在/重复校验（一次性）等场景（对应 spec 中
      image-captcha 的全部 Scenario）。共 10 个用例，全部通过。

## 3. 接口限流——注解与配置

- [x] 3.1 新增 `cn.nihility.rbac.ratelimit.config.RbacRateLimitProperties`
      （`@ConfigurationProperties(prefix = "rbac.rate-limit")`）：全局默认 `windowSeconds`
      （默认 60）、`maxRequests`（默认 60），并在 `application.yml` 补充默认值。
- [x] 3.2 新增注解 `cn.nihility.rbac.ratelimit.annotation.RateLimit`：`windowSeconds`（默认
      -1，表示使用全局默认）、`maxRequests`（默认 -1，同上），标注在 Controller 方法上。
- [x] 3.3 新增 `cn.nihility.rbac.ratelimit.exception.RateLimitExceededException extends
      BusinessException`，确认与现有 `GlobalExceptionHandler` 能正确转换为
      `{code,message,data}` 响应（不需要新增异常处理器分支，走已有的 `BusinessException`
      处理链路）。

## 4. 接口限流——AOP 切面与 Redis 计数

- [x] 4.1 新增 `cn.nihility.rbac.ratelimit.support.RateLimitAspect`：按字面意义实现为
      `@Aspect` + `@Component`，用
      `@Around("@annotation(cn.nihility.rbac.ratelimit.annotation.RateLimit)")` 环绕标注了
      `@RateLimit` 的方法。首次实现时项目未引入 `org.aspectj:aspectjweaver`
      （`@Aspect`/`@Around` 注解定义在该依赖里，缺失时无法编译），曾临时改为纯 Spring AOP
      原生的 `MethodInterceptor` + 手动注册 `Advisor`（`RateLimitAopConfig`）等价实现；现已
      经用户确认在 `build.gradle` 新增 `spring-boot-starter-aop` 依赖（连带引入
      `aspectjweaver`），改回本条描述的字面 `@Aspect` 写法，`RateLimitAopConfig` 随之删除
      （Spring Boot 检测到 classpath 有 `org.aspectj.weaver.Advice` 后，
      `AopAutoConfiguration` 自动开启 AspectJ 自动代理，不再需要手动注册 `Advisor`）。用
      "目标类全限定名 + # + 方法名"拼接 `routeKey`，用
      `ClientRequestUtils.resolveClientIp(request)` 取客户端 IP，组合成 Redis key（如
      `rate-limit:{routeKey}:{ip}`）。
- [x] 4.2 用 `RedisUtils.increment(key, windowSeconds, TimeUnit.SECONDS)` 原子自增计数，超过
      `maxRequests`（方法级覆盖优先，否则取全局默认）时抛出 `RateLimitExceededException`，
      未超过时放行原方法调用。
- [x] 4.3 补充单元测试 `RateLimitAspectTest`（或等价的切面/Redis 交互测试）：覆盖方法级覆盖
      参数生效、未覆盖时使用全局默认、不同 IP 互不影响、不同接口互不影响、达到上限后拒绝、
      窗口过期后计数重置六类场景（对应 spec 中的 6 个 Scenario）。共 6 个用例，全部通过；用
      `org.springframework.aop.aspectj.annotation.AspectJProxyFactory` 把 `RateLimitAspect`
      织入测试目标 bean（不启动 Spring 容器）来驱动 `@Around` 环绕通知。

## 5. 收尾

- [ ] 5.1 在项目 Swagger UI 中确认 `GET /api/captcha/image` 展示正常、返回结构符合预期
      （手动验证，不要求新增额外接口测试脚本）——需要人工在浏览器打开 Swagger UI 确认，
      本次实现未执行，留给用户验收。
- [x] 5.2 `./gradlew build` 确认新增代码通过编译与全部测试：编译通过；新增的
      `ImageCaptchaServiceImplTest`（10 用例）与 `RateLimitAspectTest`（6 用例）全部通过。
      全量 `./gradlew build` 有 23 个既有 `workflow` 模块集成测试失败（`TaskReturnScope`/
      `WorkflowV2*`/`MultiInstanceApproval`/`TaskOperations`/`WorkflowTerminate`/
      `WorkflowTaskServiceImpl` 等，均因 `ApprovalRecordMapper` 手写 SQL 使用驼峰列名
      `processInstanceId` 而非 `process_instance_id` 导致 `Unknown column` 语法错误），
      经确认属于本次改动之前就存在于代码库的既有缺陷（`git log` 定位到更早的
      "工作流引擎"提交，且失败集中在 `cn.nihility.rbac.workflow.*` 包，与本次新增的
      `captcha`/`ratelimit` 代码及改动的 `IdentityAuthFilter`/`application.yml` 均无关联），
      不在本次任务范围内修复。
- [x] 5.3 实现完成后按 `openspec-doc-sync` 约定，对照真实 diff 校对本 change 的
      `proposal.md`/`design.md`/`tasks.md` 与实际实现是否一致：核对了
      `git status`/`git diff`（`captcha`/`ratelimit` 两个包下的全部新增文件、
      `build.gradle`、`IdentityAuthFilter`、`application.yml` 的改动）与
      `specs/image-captcha/spec.md`、`specs/api-rate-limiting/spec.md` 的条款，均与
      `proposal.md`/`design.md`/`tasks.md` 已记录的内容一致，未发现需要改写的出入；
      另跑了 `ImageCaptchaServiceImplTest`（10 用例）与 `RateLimitAspectTest`（6 用例）
      确认仍全部通过。
