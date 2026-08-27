## Why

请求一个不存在的路由/静态资源时（如拼错的接口路径、不存在的静态文件），Spring MVC 抛出的
`NoResourceFoundException` 目前没有被 `GlobalExceptionHandler` 单独识别，会落入兜底的
`Exception.class` 处理器，被当成"未预期的系统异常"处理：记录一条 ERROR 级别日志、对外返回
HTTP 200 + `{code: 500, message: "服务器内部错误"}`。这既污染服务端错误日志（找不到资源不是
系统异常），也让调用方（浏览器、监控探针、API 调试工具）拿到一个语义错误的响应——明明是"资源
不存在"，HTTP 状态码却是 200，业务码却是"服务器内部错误"，不符合调用方对 404 的预期。

## What Changes

- `GlobalExceptionHandler` 新增专门处理 `org.springframework.web.servlet.resource.NoResourceFoundException`
  的方法，标注 `@ResponseStatus(HttpStatus.NOT_FOUND)`，使响应的 HTTP 状态码为真实的 404
  （本项目现有其余异常处理器均只在响应体 `Result.code` 里携带错误码、HTTP 状态码统一为
  200，本次是唯一一处让 HTTP 状态码本身也变为非 200 的例外，原因见 design.md）。
- 响应体内容：`Result.error(404, "请求的资源不存在：" + 请求路径)`，与现有"参数缺失/类型
  不匹配"两个处理器"错误信息里带上具体出错内容"的风格保持一致。
- 不再对 `NoResourceFoundException` 记录 ERROR 级别日志（沿用现有"参数缺失/类型不匹配"两个
  处理器的做法，不打日志；与兜底 `Exception.class` 处理器的行为区分开）。
- 不改变其余异常类型的处理逻辑（业务异常、参数校验失败、必填参数缺失、参数类型不匹配、
  兜底未预期异常）。
- **已知限制**（实现阶段发现，详见 design.md Risks / Trade-offs）：本项目的
  `IdentityAuthFilter` 运行在 `DispatcherServlet` 之前、拦截几乎全部 `/api/**` 路径，未登录
  时会直接返回 HTTP 200 + `{code:401}`，请求根本不会走到本次新增的处理器。因此本次修复
  实际生效范围是"白名单路径下的资源不存在"与"已登录状态下接口路径本身写错"两类场景，不
  覆盖"未登录状态下访问一个拼错的业务接口路径"这一最常见场景。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `backend-common-utilities`：全局异常处理器新增对 `NoResourceFoundException` 的识别，
  返回真实的 HTTP 404 状态码，而不是落入兜底逻辑返回 500。

## Impact

- 后端：`GlobalExceptionHandler`（`backend/src/main/java/cn/nihility/rbac/common/exception/GlobalExceptionHandler.java`）
  新增一个 `@ExceptionHandler` 方法。不涉及 Controller、DTO、数据库变更。
- 前端：axios 响应拦截器（`frontend/src/api/request.ts`）目前依赖响应体里的业务 `code` 做
  统一错误提示，本次改动后 HTTP 状态码本身也会变为 404，需要确认拦截器/浏览器开发者工具下
  该场景的表现符合预期（不会被拦截器当成网络错误弹出误导性提示），具体确认方式见
  design.md/tasks.md。
- OpenSpec：需要为 `backend-common-utilities` 编写 delta spec，新增一条需求描述该行为。
