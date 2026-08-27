## ADDED Requirements

### Requirement: 未匹配路由/静态资源返回真实的 404
系统的全局异常处理器 SHALL 识别 Spring MVC 因请求未命中任何已注册的 Controller 路由、也
不是已注册的静态资源而抛出的 `NoResourceFoundException`，返回真实的 HTTP 404 状态码（而
不是像其余异常类型那样统一返回 HTTP 200、仅在响应体 `code` 字段里携带错误码）；响应体仍
遵循 `{ code, message, data }` 结构，`code` 为 `404`，`message` 中 SHALL 明确指出具体是
哪一个请求路径未能匹配。该异常 SHALL NOT 被当作未预期的系统异常记录 ERROR 级别日志。

#### Scenario: 请求一个不存在的路由
- **WHEN** 客户端请求一个未注册任何 Controller 处理方法、也不是静态资源的路径
- **THEN** 系统返回 HTTP 404 状态码，响应体 `code` 为 `404`，`message` 中包含具体的请求路径

#### Scenario: 未匹配路由不记录为系统异常日志
- **WHEN** 触发一次 `NoResourceFoundException`
- **THEN** 系统不记录 ERROR 级别日志，与兜底处理未预期异常的行为区分开
