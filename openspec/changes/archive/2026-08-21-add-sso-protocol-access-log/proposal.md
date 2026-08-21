## Why

`close-sso-log-and-policy-gaps` change只解决了"SSO 登录页凭证校验"这一个点的日志缺口（写入 `tab_login_log`）。用户进一步指出：CAS/OAuth2.0 协议的全部运行时端点——CAS 单点登录（票据签发）、CAS 票据验证、CAS 单点登出、OAuth2 授权、OAuth2 令牌签发/刷新、OAuth2 用户信息查询、公共登出接口——目前完全没有任何调用记录，问题排查（如某个应用的用户反馈"登录不了"）时无法知道是哪一步、因为什么原因失败的。用户已确认：

1. 三个浏览器直接访问的端点（CAS 登录、CAS/公共登出、OAuth2 授权）与三个应用后端服务器调用的端点（CAS 票据验证、OAuth2 令牌签发、OAuth2 用户信息查询）性质不同——后者没有"用户输入账号密码"这个动作，不适合塞进 `tab_login_log` 的账号/密码语义列；
2. 需要新建一张专门的 SSO 协议调用记录表，与登录日志物理隔离；
3. 浏览器直接访问的三个端点里，即使当时已持有有效 SSO 会话（未重新输入账号密码）、直接签发票据/授权码，也要单独记一条——即"每次签发票据/授权码都算一次访问"，不是"每次重新输入密码才算一次"。

## What Changes

- 新增 `tab_sso_protocol_log` 表：记录 CAS/OAuth2.0 协议全部 6 个运行时端点（含公共登出接口共 7 个路由，登出场景合并计 1 种事件类型）的每一次调用，成功/失败均记录，字段包括协议类型、事件类型、应用标识、用户 id（可解析时）、结果、失败原因、客户端 IP、User-Agent。
- 新增 `SsoProtocolLogRecorder` 组件，与 `LoginLogRecorder` 同构（内部走 `RequestContextHolder` 取 IP/UA，不需要改动没有 `HttpServletRequest` 参数的控制器方法签名）。
- 在 `CasController`（login/serviceValidate/logout）、`OAuthController`（authorize/token/userinfo）、`SsoLogoutController`（公共登出）、`SsoLogoutExecutor`（登出主流程，供 CAS 登出与公共登出共用）的每一个成功/失败分支接入该记录组件。
- 新增一个只读分页查询接口（`GET /api/sso-protocol-logs`），复用 `login-log-management`/`operation-log-management` 的查询接口设计风格，供问题排查使用。
- 把新表纳入 `close-sso-log-and-policy-gaps` change 已实现的日志定期清理任务（`rbac.log-cleanup`，默认每天凌晨 1 点、保留 180 天）范围内，与登录日志、操作日志共用同一份配置。
- （用户反馈后补充）新增 `V3__add_sso_protocol_log_menu.sql` 补齐菜单/权限点种子数据，前端"日志管理"分组下新增"SSO协议调用记录"子菜单与对应管理页面，默认超级管理员可见，见 design.md Decision 7。

## Capabilities

### New Capabilities

- `sso-protocol-access-log`：CAS/OAuth2.0 协议运行时端点的调用记录能力（新表 + 记录组件 + 只读查询接口 + 纳入定期清理）。

### Modified Capabilities

- `app-sso-protocol-runtime`：CAS 单点登录/CAS 票据验证/CAS 单点登出/OAuth2 授权/OAuth2 令牌签发/OAuth2 用户信息查询/全局单点登出 七个需求（对应 `openspec/specs/app-sso-protocol-runtime/spec.md` 里的既有需求）各自追加"调用本端点 SHALL 触发一次 `sso-protocol-access-log` 记录"的行为约束。
- `login-log-management`（间接）：不修改其需求文本，但明确"登录日志"与"SSO 协议调用记录"是两张物理隔离的表，`tab_login_log` 继续只对应真正发生凭证校验的那一次调用（`close-sso-log-and-policy-gaps` change 已实现），本次新表不重复覆盖同一份数据。

## Impact

- 后端：新增一个 Flyway 增量迁移文件（`V2__add_sso_protocol_log.sql`，在已完成的 `consolidate-flyway-migrations-v4` 基线之后追加）、新增 `cn.nihility.rbac.ssoprotocollog` 模块（entity/mapper/service/controller/dto）、修改 `CasController`/`OAuthController`/`SsoLogoutController`/`SsoLogoutExecutor`、扩展 `close-sso-log-and-policy-gaps` change 里新增的 `LogCleanupScheduler`/`LogCleanupProperties`。
- 前端：新增"SSO协议调用记录"管理页面（`/log/sso-protocol-logs`），挂在"日志管理"一级分组下，结构对齐既有的操作日志/登录日志页面（design.md Decision 7）。
- 不改变任何 CAS/OAuth2 协议对外的响应形状、状态码、错误提示文案——纯粹是旁路记录，不影响主流程行为。
