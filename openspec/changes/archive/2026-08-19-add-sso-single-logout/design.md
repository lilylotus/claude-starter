## Context

见 proposal.md - Why。现状约束（来自现有实现，`cn.nihility.rbac.sso.*`）：

- `sso_session` Cookie 只存 `userId`（`SsoSessionService`，Redis key `sso:session:<token>`），不记录本次会话在哪些应用签发过票据/令牌。
- CAS 服务票据（`cas:st:<ticket>` → `CasTicketPayload(appId, service, userId)`）、OAuth2 授权码/AccessToken/RefreshToken（`oauth:code|token|refresh:<value>`）均只绑定 `appId`/`clientId`/`userId`，不绑定签发时使用的 `sso_session` token。
- `AppProtocolGuard` 是 CAS/OAuth2 端点统一查询 `AppAuthConfigEntity`/`AppConfigEntity` 的入口（绕过 Service 层，直接查 Mapper，因为这些是无鉴权的协议端点）。
- 已有可复用的"POST 通知应用"基础设施：`HttpClientUtils.postForm`（表单 POST）、`NotifySignatureAppender` + `tab_app_config.accessKey/secretKey`（签名，数据同步通知场景已用）、`ThreadPoolUtils`（4 线程固定池，拒绝时抛异常不静默丢弃）。

## Goals / Non-Goals

**Goals:**
- CAS 登出、全局登出统一改造为"撤销会话 + 清 Cookie + 触发后端回调通知 + 302 跳转"。
- 后端回调通知能准确回传"这个应用在本次会话最后一次签发的 ticket/AccessToken"，而不是随意一个历史值。
- 单个应用通知失败要能被隔离，不拖慢或打断登出主流程与 302 响应。

**Non-Goals:**
- 不实现标准 CAS SAML 格式的 LogoutRequest 报文（沿用需求描述的简化表单字段 `ticket`/`access_token`），不追求与官方 CAS 协议逐字节兼容。
- 不引入通知重试/补偿机制（失败即放弃，同步一次尝试），也不落地通知审计表——如后续需要审计，另开 change。
- 登出不主动吊销 RefreshToken（登出只影响 AccessToken 通知内容），RefreshToken 的有效期调整（滑动过期）是与登出无关的独立配置变更（见 Decision 6），二者不互相影响。

## Decisions

### 1. 会话 → 应用凭证映射：新增 Redis Hash `sso:session:<token>:apps`

签发 CAS 服务票据（`CasTicketService.issue`）与签发 OAuth2 AccessToken（`OAuthTokenService`
签发 token 时）时，除写入各自原有的 Redis key 外，额外对 `sso:session:<token>:apps` 这个
Hash 做一次 `HSET appId '{"protocol":"CAS","credential":"<ticket>"}'`（OAuth2 场景
`protocol":"OAUTH2"`、`credential` 为 access token）。同一 `appId` 重复签发时后写覆盖前写，
天然保证"最后一次"语义。该 Hash 的 TTL 跟随 `sso_session` 主 key 的过期时间对齐（登出/过期时
一并清理，避免残留）。

登出时，读取该 Hash 得到"本次会话登录过的应用列表 + 各自最后一次的 ticket/token"，
即可直接构造回调表单，无需再反查 `cas:st:*`/`oauth:token:*`（那些 key 可能已消费/即将
过期，且不保证仍存在）。

*备选方案：登出时遍历所有配置了 CAS/OAuth2 协议的应用，逐个查最近一次 ticket/token。*
放弃原因：现有存储没有"按 appId 反查最近票据"的索引，需要额外维护类似的映射，复杂度与
本方案相当，但语义上不如"会话自己记录访问过的应用"直接，且能避免通知从未登录过的应用。

*OAuth2 场景的会话归属问题*：`/oauth2/token` 通常由业务方后端服务端调用（换取
AccessToken 的请求本身不一定携带浏览器 `sso_session` Cookie）。为把 AccessToken 关联回
签发时的 SSO 会话，`OAuthCodePayload`（授权码，签发于 `/oauth2/authorize`，此时浏览器
Cookie 可用）新增一个 `ssoSessionToken` 字段，随授权码一起落库；`/oauth2/token` 用
`code` 换取 AccessToken 时，从授权码 payload 里取出该字段，写入
`sso:session:<ssoSessionToken>:apps`。若授权码本身没有关联到 SSO 会话（理论上不会发生，
因为 `/oauth2/authorize` 签发授权码的前提就是持有有效 SSO 会话），则跳过该次映射写入，
不影响令牌签发主流程。

### 2. 登出通知服务：新增 `SsoLogoutNotifyService`，同步分发（`ThreadPoolUtils`）+ 每应用隔离

新增一个不注册为特定分层但独立的领域服务（类似 `AppNotifyServiceImpl` 的定位），
方法签名类似 `notifyLogout(String ssoSessionToken)`：

1. 读取 `sso:session:<token>:apps` Hash，得到 `{appId: {protocol, credential}}`。
2. 对每个 appId 查 `AppAuthConfigEntity.logoutNotifyUrl`（`AppProtocolGuard` 新增查询方法），
   为空则跳过。
3. 对每个待通知应用，用 `ThreadPoolUtils.submit` 提交一个任务：构造表单字段
   （CAS → `ticket=<credential>`；OAuth2 → `access_token=<credential>`），用
   `NotifySignatureAppender` 计算签名并加到表单/请求头，调用 `HttpClientUtils.postForm`；
   任务内部 try/catch 吞掉异常并记录 WARN 日志，不向上抛出。
4. 登出主流程（CAS/全局登出 Controller）在提交完所有通知任务后立即继续走 Cookie 清除 +
   302 重定向，SHALL NOT 等待通知任务的 HTTP 响应返回（fire-and-forget），避免登出接口
   的响应时间受制于最慢的第三方回调。
5. `ThreadPoolUtils` 队列已满导致 `RejectedExecutionException` 时，同样在登出 Controller
   侧 try/catch 吞掉，不影响登出主流程完成。

*备选方案：同步阻塞逐个 POST 通知完毕后再重定向。* 放弃原因：登出接口的调用方
（浏览器/前端）在等待这个响应完成页面跳转，若被一个响应慢的第三方应用拖慢，用户体验明显
劣化，且与需求"不影响其他应用及登出主流程"的表述冲突。

### 3. `logoutNotifyUrl` 落地位置：`tab_app_auth_config` 新增列

字段名 `logout_notify_url`（VARCHAR(255)，允许 NULL），随 `auth_protocol`、
`cas_service_patterns`、`oauth2_redirect_uri_patterns` 一起维护在同一张表、同一个
Entity/VO/UpdateRequest/MapStruct 转换里，迁移脚本 `V3__add_app_auth_logout_notify_url.sql`
新增列（先检查是否为 MySQL 保留字——不是，无需转义）。

### 4. CAS 登出 `service` 校验复用登录接口逻辑

`CasController.logout` 新增 `@RequestParam String service`，复用
`AppProtocolGuard.assertCasServiceAllowed(appId, service)`（与 `login` 方法完全一致的
白名单校验），避免开放重定向。

### 5. 全局登出接口改为 `{appId}` 路径参数，`service` 按该应用自身协议类型校验

全局登出接口改为 `GET /api/authn/{appId}/logout?service={callBackServiceUrl}`，与 CAS
登出接口同构（都带 `appId` 路径参数）。`AppProtocolGuard` 新增
`assertLogoutServiceAllowed(String appId, String service)`：按 `appId` 解析该应用的
`AppAuthConfigEntity`，依据其 `authProtocol` 分派校验——`CAS` 时复用
`assertCasServiceAllowed` 的匹配逻辑（对 `casServicePatterns` 做 ANT 匹配）；`OAUTH2`
时复用 `assertOAuthRedirectUriAllowed` 的匹配逻辑（对 `oauth2RedirectUriPatterns` 做
ANT 匹配）；`authProtocol=NONE` 或应用不存在时直接拒绝。校验不通过时 SHALL 拒绝该次
请求，不发生重定向、不清除会话、不发通知。

此前 Decision 5 讨论过的"跨应用遍历所有应用匹配列表"方案已废弃（用户明确要求接口带
`appId`，天然按应用归属校验，无需再遍历系统内全部应用）。

### 6. OAuth2 RefreshToken 默认有效期缩短为 1 天，且每次刷新轮转（一次性消费）

`RbacSsoProperties.oauthRefreshTokenExpireSeconds` 默认值从 1209600（14 天）改为
86400（1 天）。`OAuthTokenService` 处理 `grant_type=refresh_token` 时改为"轮转"模式：

1. 校验旧 `refresh_token` 存在且未过期（同现有 `verifyRefreshToken`）。
2. 立即删除旧 `refresh_token` 对应的 Redis key（`oauth:refresh:<旧值>`），使其一次性
   消费、不可重放。
3. 签发一个新的 `refresh_token`（新的随机十六进制值），写入新 key，TTL 为完整的
   `oauthRefreshTokenExpireSeconds`。
4. 签发新的 access token（沿用现有 `writeAccessToken` 逻辑）。
5. 响应体除 `access_token`/`token_type`/`expires_in` 外，新增返回 `refresh_token`
   字段（步骤 3 的新值），调用方 SHALL 用该新值替换本地保存的旧值，后续刷新使用新值。

*备选方案 A：仅重置同一个 refresh token 的 TTL，不换发新值（滑动过期，不轮转）。*
放弃原因：用户明确要求"刷新后重新生成新的 refresh token"，且该方案无法防止"token 一旦
泄露被攻击者反复使用"——只要攻击者和合法用户交替使用同一个值，两者都能续期成功，无法
区分或阻断。

*备选方案 B：轮转 + 重放检测联动撤销整个令牌家族（reuse detection revokes token
family，OAuth Security BCP 推荐做法）。* 放弃原因：需要额外维护"令牌家族"谱系
（记录每个 refresh token 由哪个上一代轮转而来），超出本次需求范围；当前方案已能满足
"旧值不可重复使用"的核心诉求（谁先用旧值谁能拿到新值，另一方后续请求会因该旧值已被
删除而直接失败），如后续需要更强的失窃检测与自动撤销，可另开 change 引入。

*并发刷新的行为*：若同一个 refresh token 被并发发起两次刷新请求，Redis 的
"读取旧值是否存在 + 删除"这组操作不是原子的，理论上存在两个请求都读到旧值仍存在、都
成功换发的极小概率窗口（无锁竞态）。这属于已知的可接受行为，不在本次范围内引入分布式锁
或 Lua 原子脚本；如后续对该并发场景有强一致性要求，可另开 change 处理。

## Risks / Trade-offs

- [会话-应用映射 Hash 增大存储写入次数] → 每次签发 ticket/token 多一次 `HSET`，量级与
  现有票据/令牌签发本身一致，可忽略。
- [Fire-and-forget 通知无法保证送达] → 与 proposal 要求一致（不引入重试），如后续需要
  可靠投递，交由独立 change 引入审计表 + 重试队列（参考 `app-sync-notify-pull` 的
  `tab_app_notify_record` 模式）。
- [OAuth2 AccessToken 换取发生在无 Cookie 的服务端到服务端调用场景] → 通过在授权码
  payload 中携带 `ssoSessionToken` 桥接，若未来 `/oauth2/token` 支持除
  `authorization_code` 外的其他授予类型直接签发（如 client_credentials，当前不存在），
  该类令牌不会关联到任何 SSO 会话，登出通知天然不会覆盖它们（属预期行为，非会话绑定的
  令牌不受用户登出影响）。
- [全局登出接口通知范围与被校验的 `appId` 无强绑定] → 校验只用来确认调用方持有该应用
  合法配置的 `service`/`redirect_uri`（防开放重定向），登出后仍按 Decision 1 的会话-
  应用映射通知"本次会话实际登录过的所有应用"，不局限于路径上的 `appId`，与 CAS 登出的
  通知范围保持一致，符合"通知所有单点登录应用登出"的需求表述。
- [RefreshToken 有效期从 14 天缩短为 1 天且改为轮转，属行为破坏性变更] → 已有存量
  refresh token（迁移前签发、TTL 仍按旧配置计算）不受影响，仍按原 TTL 到期，但下次刷新
  时即按新逻辑轮转并换发新值；接入方若未按刷新响应更新本地保存的 `refresh_token`（仍
  沿用旧值发起下一次刷新），会在旧值被消费后收到刷新失败，需在
  `SSO单点登录接入规范.md` 中明确提醒接入方"刷新响应会返回新的 `refresh_token`，必须
  用新值替换本地保存的旧值，且需保证至少每天调用一次刷新，否则用户需重新走一遍完整
  授权流程"，这是本次变更里对接入方影响最大的一点，需重点标注为 BREAKING。
- [并发刷新竞态] → 见 Decision 6"并发刷新的行为"，已知可接受的小概率窗口，本次不引入
  分布式锁/原子脚本。
