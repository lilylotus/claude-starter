## Context

`GlobalExceptionHandler` 目前所有异常处理方法都不带 `@ResponseStatus`，对外 HTTP 状态码
统一为 200，真正的错误信息全部放在响应体 `Result.code`/`Result.message` 里——前端
`frontend/src/api/request.ts` 的 axios 响应拦截器就是按这个约定实现的：`response.interceptors.
use` 的**成功回调**里读 `body.code` 分流处理（401 静默刷新、4010 强制改密、403/其余非 0
提示错误），只有 axios 判定为"网络层错误"（HTTP 状态码非 2xx，或请求本身失败）才会落到
**错误回调**，那里只读 `error.message`（axios 自动生成的"Request failed with status code
XXX"），不解析 `error.response.data`，兜底提示"网络异常"。见 proposal.md - Why。

## Goals / Non-Goals

**Goals:**
- `NoResourceFoundException`（未匹配到任何 Controller 路由、也不是已注册的静态资源）返回
  真实的 HTTP 404 状态码，不再被兜底 `Exception.class` 处理器当成系统异常记 ERROR 日志、
  返回 500。
- 响应体仍然遵循 `{ code, message, data }` 结构（`GlobalResponseAdvice` 对 `Result` 类型
  直接透传，不受影响），保持与其余接口一致的响应体形状，即使 HTTP 状态码本身发生了变化。

**Non-Goals:**
- 不改动 axios 响应拦截器（`frontend/src/api/request.ts`）。404 走到拦截器的错误回调、
  展示"网络异常"这个通用提示，而不是后端 `Result.message` 里更具体的"请求的资源不存在：
  xxx"文案——这是本次改动的已知代价（见下方 Risks / Trade-offs），不在本次修复范围内解决。
- 不改动其余异常处理器的 HTTP 状态码（仍统一为 200 + body.code），本次是唯一例外，原因
  见下方 Decisions。
- 不新增/修改任何 Controller 路由或静态资源映射配置。

## Decisions

### 1. 用 `@ResponseStatus(HttpStatus.NOT_FOUND)` 让 HTTP 状态码本身变为 404

**为什么破例**：`NoResourceFoundException` 和其余异常类型的性质不同——业务异常/参数校验
失败/参数缺失/类型不匹配都发生在"请求确实命中了某个已知接口"之后，用 HTTP 200 + body.code
表达"接口存在、但这次调用有问题"是合理的；而 `NoResourceFoundException` 恰恰是"根本没有
命中任何接口"，继续用 HTTP 200 会让调用方（浏览器地址栏直接访问、监控探针、API 调试工具、
反向代理的健康检查规则）误判为"请求成功"，掩盖了真实情况。这是本次修复要解决的核心问题，
因此值得为这一种异常类型破例。

**替代方案考虑**：曾考虑维持 HTTP 200、只把 `Result.code` 改成 `404`——放弃，因为这治标不
治本，调用方（尤其是非本项目前端的调用方，如运维监控、第三方对接）大概率只看 HTTP 状态码，
不会解析响应体，问题依旧存在；这也正是 proposal.md 里用户明确要求"返回404"（而不是"返回
code=404"）的诉求。

### 2. 响应体错误信息带上具体请求路径

`Result.error(404, "请求的资源不存在：" + ex.getResourcePath())`，与现有
`handleMissingParam`/`handleTypeMismatch` 两个处理器"提示信息里带上具体出错的参数名"的
风格一致，方便排查是哪个路径被误判为不存在。

### 3. 不记录 ERROR 级别日志

`NoResourceFoundException` 触发时大概率是常规噪音（白名单路径下的探测/拼错文件名、springdoc
资源请求某个不存在的子路径等，具体可达范围见下方 Risks 第一条），不是"系统出了问题"，继续按
现有兜底 `Exception.class` 处理器那样记 ERROR 日志会持续污染服务端日志、掩盖真正的系统异常。
与 `handleMissingParam`/`handleTypeMismatch` 两个同样不打日志的处理器保持一致。

## Risks / Trade-offs

- **[实现阶段发现的重要限制]** `IdentityAuthFilter`（`backend/src/main/java/cn/nihility/rbac/
  auth/config/IdentityAuthFilterConfig.java`）注册在 `/*`、运行于 `DispatcherServlet` 之前，
  对不在其 `FULL_WHITELIST`（`/api/auth/public-key`、`/api/auth/login`、`/api/auth/refresh`、
  `/swagger-ui.html`、`/swagger-ui/**`、`/v3/api-docs`、`/v3/api-docs/**`、
  `/swagger-resources/**`、`/webjars/**`、`/open/api/sync/**`、`/api/authn/**`）内的一切请求，
  只要缺少合法 `identity-token`，就会直接返回 HTTP 200 + `{code:401}`，请求根本不会到达
  `DispatcherServlet`、更不会触发 `NoResourceFoundException`。这意味着本次修复对"未登录状态
  下访问一个拼错的 `/api/xxx` 业务接口路径"这一最常见的场景**不生效**——那类请求仍然是
  HTTP 200 + `{code:401}}`，不是 404。本次修复真正生效的范围是：①白名单内路径本身拼错/资源
  不存在（如 `/webjars/xxx.js` 文件不存在、`/swagger-ui/` 下的子资源不存在）；②已登录、
  `identity-token`/`menu` 头都合法，但请求的具体接口路径本身不存在（如前端代码手误拼错了
  接口路径）。已在 `NoResourceFoundIntegrationTest` 里用 `/webjars/does-not-exist-xyz.js`
  （命中场景①）验证行为符合预期。如果用户的诉求是"任何未登录状态下访问不存在的 `/api/**`
  路径都要返回 404"，需要改造 `IdentityAuthFilter` 本身（如先判断路径是否命中任何已注册
  `HandlerMapping` 再决定返回 401 还是放行），这是一个更大范围的改动，超出本次修复范围，
  留待用户确认是否需要再单独立项。
- [本项目前端 axios 拦截器不解析 HTTP 4xx/5xx 响应体，遇到 404 时只会展示通用的"网络异常"
  提示，丢失后端 `Result.message` 里"请求的资源不存在：具体路径"这条更明确的信息] →
  可接受：`NoResourceFoundException` 绝大多数触发场景（白名单路径下的探测/拼错文件名、
  已登录状态下接口路径本身写错导致的开发期问题）本来就不该在生产环境向真实用户展示业务
  提示文案，开发期可以直接看浏览器 Network 面板的响应体核实具体路径；若未来确有必要在
  前端展示更精确的 404 提示，属于独立的前端改动，留待有实际需求时再单独提出 change，
  不在本次范围内顺带处理。
- [该处理器是 `GlobalExceptionHandler` 里唯一一个改变 HTTP 状态码的方法，与其余处理器的
  约定不一致，后续维护者可能疑惑为什么单独这一个不遵循"统一 200 + body.code"的约定] →
  已在类注释、方法注释里说明原因（"根本没有命中任何接口"与"接口命中但调用有问题"的本质
  区别），降低后续误改风险。
