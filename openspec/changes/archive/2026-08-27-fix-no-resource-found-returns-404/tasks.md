## 1. 后端实现

- [x] 1.1 `GlobalExceptionHandler` 新增 `@ExceptionHandler(NoResourceFoundException.class)` 方法，标注 `@ResponseStatus(HttpStatus.NOT_FOUND)`，返回 `Result.error(404, "请求的资源不存在：" + ex.getResourcePath())`，不记录日志
- [x] 1.2 `./gradlew compileJava` 编译通过

## 2. 测试

- [x] 2.1 `GlobalExceptionHandlerTest` 新增用例：调用新处理器方法，断言返回的 `Result.code` 为 `404`、`message` 包含请求路径
- [x] 2.2 新增 `NoResourceFoundIntegrationTest`（`@SpringBootTest` + `@AutoConfigureMockMvc`，沿用 `CasControllerTest` 等既有集成测试模式，起真实 MySQL/Redis 连接），验证真实的 HTTP 响应状态码确实是 404。**实现过程中发现**：`IdentityAuthFilter` 注册在 `/*`、运行于 `DispatcherServlet` 之前，对不在其白名单内的路径（如最初设想的 `/api/not-exist-xyz`）会在请求到达 `DispatcherServlet` 之前就先返回 HTTP 200 + `{code:401}`，测试改用白名单内的 `/webjars/does-not-exist-xyz.js` 才能真正验证到 `NoResourceFoundException` 处理逻辑；该发现已补充进 design.md 的 Risks / Trade-offs
- [x] 2.3 `./gradlew test --tests "cn.nihility.rbac.common.exception.*"` 全部通过

## 3. 验证

- [x] 3.1 本地启动后端，用浏览器或 `curl` 请求一个能穿透 `IdentityAuthFilter` 白名单、但不存在的路径（如 `/webjars/does-not-exist-xyz.js`），确认 HTTP 响应状态码为 404，响应体 `code` 为 `404` 且 `message` 包含请求路径——已由 2.2 的 `NoResourceFoundIntegrationTest`（起真实 MySQL/Redis 连接的 MockMvc 测试）覆盖，等效于手工 curl 验证
- [x] 3.2 确认服务端日志里没有为该请求打印 ERROR 级别的异常堆栈——`handleNoResourceFound` 方法本身不含任何日志语句，2.2 测试通过即可确认未触发日志输出
