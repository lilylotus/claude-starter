## Why

登录相关接口（口令登录、SSO 短信/扫码登录）目前没有任何"人机识别"和"调用频率控制"手段，
存在被暴力破解密码、被脚本批量刷短信验证码/刷登录尝试的风险。在真正把这两类防护接入具体登录
接口之前，需要先把两个可复用的通用能力独立建好：图形验证码（生成 + 校验）与接口调用频率限制，
后续（另一个 change）再分别标注到口令登录、SSO 短信发送/登录、SSO 扫码登录等具体接口上。

## What Changes

- 新增图形验证码能力：
  - 生成接口：随机生成验证码内容，支持两种模式——大小写字母+数字混合字符（个数可配置，默认
    4 个）、简单加减乘除算式（如 "3 + 5 = ?"，结果作为答案），渲染成图片，返回图片（base64）
    与验证码 id；同一 id 的答案连同过期时间存入 Redis。
  - 校验能力：按验证码 id 查找 Redis 中的答案，与用户提交值做不区分大小写（字符模式）/精确
    数值（算式模式）比较；无论校验成功或失败，都立即使该 id 失效（一次性，不可重复提交同一
    id 校验），避免同一张图片被重复尝试。
  - 配置项：字符个数、图片有效期、图片尺寸/字体等可渲染参数放到 `application.yml` 对应的
    `@ConfigurationProperties` 中，允许运维按环境调整。
  - 本次只提供生成/校验两个能力（生成接口对外暴露，校验以 service 方法形式暴露供其他模块
    Java 内调用），不修改任何现有登录接口的业务逻辑。
- 新增接口调用频率限制（防刷）能力：
  - 提供一个可复用的限流组件：以"N 秒时间窗口内最多 M 次请求"为语义，按调用方 IP 作为限流
    维度（预留后续扩展为按用户 id / 自定义 key 维度的可能，但本次只实现 IP 维度），使用 Redis
    计数实现（复用 `RedisUtils.increment` 风格的固定窗口计数，不使用进程内令牌桶），支持多
    实例部署下限流状态共享。
  - 提供声明式接入方式：一个可标注在 Controller 方法上的注解（如 `@RateLimit`），可在注解上
    覆盖默认的时间窗口秒数与最大请求次数；同时提供默认的全局配置（`application.yml` 里的
    `@ConfigurationProperties`）作为未显式指定时的兜底值。
  - 触发限流时返回统一的失败响应（复用 `common/` 下的全局响应结构 `{ code, message, data }`），
    不抛出未分类异常。
  - 本次只提供组件本身与示例接入方式，不在任何现有登录接口上标注该注解。

## Capabilities

### New Capabilities
- `image-captcha`: 图形验证码的生成（字符混合 / 算式两种模式，可配置字符个数）与一次性校验能力。
- `api-rate-limiting`: 基于 Redis 固定窗口计数、按 IP 维度、可声明式标注在接口方法上的调用频率
  限制能力。

### Modified Capabilities
（无——本次不修改任何已有登录/SSO 相关 spec 的需求条款，图形验证码与接口限流均未接入现有登录
接口。）

## Impact

- 新增后端代码：`backend/src/main/java/cn/nihility/rbac/captcha/`（图形验证码模块：controller/
  service/dto/config/exception）、`backend/src/main/java/cn/nihility/rbac/ratelimit/`（接口限流
  模块：注解、AOP 切面、Redis 计数实现、配置类）。
- 新增/修改配置：`application.yml` 增加 `rbac.captcha.*`、`rbac.rate-limit.*` 配置节。
- 依赖：图形验证码渲染需要 Java 内置 `java.awt`（`BufferedImage`/`Graphics2D`），不引入新的
  第三方依赖；限流基于已有的 `spring-boot-starter-data-redis` 与 `RedisUtils`，同样不新增依赖。
- 不涉及前端改动、不涉及数据库表结构变更、不影响任何现有登录接口的当前行为。
