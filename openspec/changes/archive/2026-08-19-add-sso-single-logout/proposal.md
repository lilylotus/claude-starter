## Why

当前 CAS/OAuth2.0 单点登录运行时（`app-sso-protocol-runtime`）只支持"退出时清除 `sso_session` 会话 Cookie"，既不会把登出事件后端回调通知给其他已通过该会话登录的应用（back-channel SLO），CAS 登出接口也没有 `service` 回跳参数、不会 302 跳回业务方页面，系统里也没有一个不依赖 CAS 协议、可供业务方或前端直接调用的全局登出入口。这导致用户在一个应用登出后，其余已登录应用仍持有有效会话，不符合单点登录"一处登出、处处登出"的基本预期。

## What Changes

- 应用认证管理配置（`tab_app_auth_config`）新增"登出通知回调地址"字段（`logoutNotifyUrl`），随 CAS/OAuth2.0 协议一起配置；管理端 VO/更新请求/前端表单同步新增该字段。
- 新增单点登出后端回调通知机制：登出发生时，对本次会话（`sso_session`）实际登录过的每个应用，以 `POST` + `Content-Type: application/x-www-form-urlencoded` 方式回调其 `logoutNotifyUrl`：CAS 协议应用回传该应用本次会话最后签发的 service ticket（表单字段 `ticket`），OAuth2.0 协议应用回传其 AccessToken（表单字段 `access_token`）；回调请求复用现有数据同步通知（`app-sync-notify-pull`）的签名机制（`NotifySignatureAppender`，基于 `tab_app_config` 的 accessKey/secretKey），单个应用通知失败不影响其他应用及本次登出主流程。
- 为支持"最后一次 ticket / AccessToken"回传，SSO 会话存储需新增"会话 → 各应用最近一次签发的 ticket/AccessToken"的映射（Redis），在 CAS 签发 ST、OAuth2.0 签发 AccessToken 时同步写入。
- CAS 登出接口 `GET /api/authn/cas/{appId}/logout` **BREAKING**：新增必填 `service` 请求参数（复用登录接口既有的 ANT 白名单校验 `AppProtocolGuard.assertCasServiceAllowed`，防开放重定向），登出逻辑变为"撤销会话 + 清除 Cookie + 触发后端回调通知 + 302 重定向到 `service`"，不再直接返回登出成功文本。
- 新增全局登出接口 `GET /api/authn/{appId}/logout?service={callBackServiceUrl}`：按 `appId` 查找应用及其单点登录协议配置，校验 `service` 匹配该应用配置的匹配规则（CAS 协议校验 service 匹配列表，OAuth2.0 协议校验 redirect_uri 匹配列表），执行与 CAS 登出一致的逻辑（撤销 `sso_session`、清除 Cookie、触发后端回调通知、302 重定向到 `service`），供不经过 CAS 协议票据流程的场景（如 OAuth2.0 接入方、前端直接触发的"退出登录"）统一调用。
- 更新根目录 `SSO单点登录接入规范.md` 中关于"不支持 back-channel SLO"的描述，改为记录新的登出通知协议（回调方式、字段、签名方式）供接入方参考。
- OAuth2.0 refresh token 有效期配置 `oauthRefreshTokenExpireSeconds` 默认值从 14 天（1209600 秒）调整为 1 天（86400 秒）；**BREAKING**（行为变更）：刷新（`grant_type=refresh_token`）成功时，SHALL 签发一个新的 refresh token（取值轮转）并让旧 refresh token 立即失效（一次性消费，同一个旧值不能被使用第二次），新 refresh token 拥有完整的 `oauthRefreshTokenExpireSeconds` 有效期；刷新响应体 SHALL 新增返回 `refresh_token` 字段（新值），供调用方替换本地保存的旧值。此举防止 refresh token 一旦泄露后可被攻击者长期反复使用。

## Capabilities

### New Capabilities

（无新增能力域，登出通知机制归入现有 `app-sso-protocol-runtime` 能力域下的新增需求）

### Modified Capabilities

- `app-sso-protocol-runtime`: CAS 登出需求变更为"接收 service 参数 + 触发后端回调通知 + 302 重定向"；新增"全局登出接口"需求；新增"单点登出后端回调通知（back-channel SLO）"需求，覆盖 CAS ticket / OAuth2.0 AccessToken 两种回传形态及签名、失败隔离行为；"OAuth2 令牌刷新"需求变更为刷新时轮转 refresh token（旧值一次性消费失效、签发新值并返回）。
- `app-auth-protocol-config`: 新增 `logoutNotifyUrl`（登出通知回调地址）配置字段的需求，随认证协议配置一并读写。

## Impact

- 后端：`cn.nihility.rbac.sso.cas.controller.CasController`（登出方法签名、行为变更）、新增全局登出 Controller（如 `SsoLogoutController`）、`cn.nihility.rbac.sso.session.SsoSessionService`（新增会话-应用-凭证映射的读写）、`cn.nihility.rbac.sso.cas.service.CasTicketService` / `cn.nihility.rbac.sso.oauth.service.OAuthTokenService`（签发时同步登记映射；`OAuthTokenService` 刷新逻辑新增重置 refresh token TTL）、`cn.nihility.rbac.sso.config.RbacSsoProperties`（`oauthRefreshTokenExpireSeconds` 默认值调整为 86400）、`cn.nihility.rbac.sso.support.AppProtocolGuard`（新增"查询会话内已登录应用列表"方法、`assertLogoutServiceAllowed`）、新增登出通知服务（复用 `HttpClientUtils.postForm`、`NotifySignatureAppender`、`ThreadPoolUtils`）、`cn.nihility.rbac.app.authconfig.*`（entity/VO/UpdateRequest/MapStruct 新增字段）、Flyway 迁移脚本 `V3__*.sql`（`tab_app_auth_config` 新增列）。
- 前端：应用认证管理配置表单新增"登出通知回调地址"输入项（对齐后端 `logoutNotifyUrl` 字段）。
- 文档：根目录 `SSO单点登录接入规范.md`、`权限资源.txt`（如登出通知配置字段需要权限点，沿用现有 `AppManagement:app:config:editAuth`，不新增权限码）。
- 数据库：`tab_app_auth_config` 新增列（迁移脚本，MySQL 5.7 兼容写法）。
- 无新增第三方依赖。
