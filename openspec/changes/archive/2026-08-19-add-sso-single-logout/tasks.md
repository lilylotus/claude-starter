## 1. 数据库与配置字段

- [x] 1.1 新增 Flyway 迁移脚本 `V3__add_app_auth_logout_notify_url.sql`：为
  `tab_app_auth_config` 新增列 `logout_notify_url VARCHAR(255) NULL`（确认非 MySQL
  5.7 保留字，遵循现有迁移脚本的注释/字符集约定）。
- [x] 1.2 `AppAuthConfigEntity` 新增 `logoutNotifyUrl` 字段。
- [x] 1.3 `AppAuthConfigVO`、`AppAuthConfigUpdateRequest` 新增 `logoutNotifyUrl` 字段
  （更新请求上加 URL 格式校验，允许为空）；`AppAuthConfigConvert`（MapStruct）同步映射。
- [x] 1.4 `AppAuthConfigServiceImpl` 修改保存逻辑：`logoutNotifyUrl` 随协议类型/匹配列表
  一并落库；非空时校验为合法 HTTP/HTTPS URL，不合法时拒绝并给出提示。

## 2. 会话-应用凭证映射（Redis）

- [x] 2.1 `SsoSessionService`（或新增同包工具方法）新增读写 `sso:session:<token>:apps`
  Hash 的方法：`recordAppCredential(token, appId, protocol, credential)` /
  `listAppCredentials(token)`；TTL 对齐 `sessionExpireSeconds`，登出/会话撤销时一并删除
  该 Hash。
- [x] 2.2 `CasTicketService.issue` 签发服务票据时，调用 2.1 写入映射（`protocol=CAS`,
  `credential=<ticket>`）。
- [x] 2.3 `OAuthCodePayload` 新增字段 `ssoSessionToken`；`/oauth2/authorize` 签发授权码
  时写入当前浏览器的 `sso_session` token。
- [x] 2.4 `OAuthTokenService` 签发 AccessToken（`authorization_code` 授予类型）时，从
  授权码 payload 取出 `ssoSessionToken`，调用 2.1 写入映射（`protocol=OAUTH2`,
  `credential=<access token>`）；`ssoSessionToken` 缺失时跳过映射写入，不影响令牌签发。

## 3. 登出通知服务

- [x] 3.1 `AppProtocolGuard` 新增方法：按 `authProtocol != NONE` 查询全部已启用 SSO 协议
  的应用（`AppAuthConfigEntity` + 关联的 `AppConfigEntity`，用于取
  `accessKey`/`secretKey`/`logoutNotifyUrl`）。
- [x] 3.2 `AppProtocolGuard` 新增 `assertLogoutServiceAllowed(String appId, String service)`：
  按 `appId` 解析应用的 `AppAuthConfigEntity`，按其 `authProtocol` 分派校验（`CAS` 复用
  `casServicePatterns` 匹配逻辑，`OAUTH2` 复用 `oauth2RedirectUriPatterns` 匹配逻辑），
  应用不存在、协议类型为"无"或不匹配任何规则时抛出与现有校验方法一致的拒绝异常。
- [x] 3.3 新增 `SsoLogoutNotifyService.notifyLogout(String ssoSessionToken)`：读取 2.1 的
  会话-应用映射，逐应用查 `logoutNotifyUrl`（为空跳过），用 `ThreadPoolUtils.submit`
  并发提交通知任务；每个任务内部构造表单字段（CAS→`ticket`，OAuth2→`access_token`），
  用 `NotifySignatureAppender` 计算签名，调用 `HttpClientUtils.postForm` 发起请求；
  任务内 try/catch 吞掉异常并记录 WARN 日志，不向外抛出。
- [x] 3.4 登出流程侧对 `ThreadPoolUtils.submit` 可能抛出的 `RejectedExecutionException`
  做 try/catch 隔离，不影响登出主流程继续执行。

## 4. 登出接口改造与新增

- [x] 4.1 `CasController.logout` 方法签名新增 `@RequestParam String service`，登出前先
  调用 `AppProtocolGuard.assertCasServiceAllowed(appId, service)`（同 `login` 方法的
  校验逻辑），不匹配时拒绝且不重定向。
- [x] 4.2 `CasController.logout` 校验通过后：撤销 `sso_session`（`SsoSessionService`）→
  清除 Cookie（`SsoSessionCookieUtils`）→ 调用 `SsoLogoutNotifyService.notifyLogout`
  （fire-and-forget，不等待结果）→ 用 `ProtocolResponseWriter.redirect` 302 到
  `service`，替换原有的 `text("已登出")` 响应。
- [x] 4.3 新增全局登出 Controller（如 `SsoLogoutController`），提供
  `GET /api/authn/{appId}/logout?service={callBackServiceUrl}`：先调用
  `AppProtocolGuard.assertLogoutServiceAllowed(appId, service)`，通过后执行与 4.2 相同的
  撤销会话/清 Cookie/触发通知（通知本次会话登录过的全部应用，不局限于路径上的
  `appId`）/302 重定向逻辑（可抽取公共方法给 4.2 和该接口复用）。
- [x] 4.4 为新增接口补充 springdoc-openapi 注解（`@Operation` 等），与现有 CAS/OAuth2
  端点风格一致。

## 5. 前端

- [x] 5.1 应用认证管理配置表单新增"登出通知回调地址"输入项（对应后端 `logoutNotifyUrl`
  字段），复用现有认证管理表单的校验/保存逻辑与 `AppManagement:app:config:editAuth`
  权限控制。
- [x] 5.2 `src/types/app.ts`（或对应类型文件）同步新增 `logoutNotifyUrl` 字段类型。

## 6. 文档同步

- [x] 6.1 更新根目录 `SSO单点登录接入规范.md`：删除"不支持 back-channel SLO"的描述，
  补充登出通知回调协议说明（触发时机、请求方式、表单字段 `ticket`/`access_token`、
  签名方式、失败不重试的行为）；补充全局登出接口
  `/api/authn/{appId}/logout?service=` 的使用说明；补充 refresh token 有效期从 14 天
  调整为 1 天、且每次刷新重置有效期（滑动过期）的说明，提醒接入方需保证至少每天刷新
  一次。
- [x] 6.2 检查 `权限资源.txt` 是否需要更新（预期无需新增权限码，登出通知回调地址复用
  `AppManagement:app:config:editAuth`）。

## 7. OAuth2 RefreshToken 有效期调整与轮转

- [x] 7.1 `RbacSsoProperties.oauthRefreshTokenExpireSeconds` 默认值从 `1209600` 改为
  `86400`。
- [x] 7.2 `OAuthTokenService` 新增/改造刷新方法（如将 `issueAccessTokenOnly` 改名/替换
  为 `rotateAccessAndRefreshToken(String oldRefreshToken, String clientId, Long userId,
  String scope)`，返回 `IssuedToken`）：先删除旧 `refresh_token` 对应的 Redis key
  （一次性消费），再复用 `writeAccessToken` 签发新 access token，并签发一个新的
  `refresh_token`（新随机十六进制值，TTL 为完整的
  `ssoProperties.getOauthRefreshTokenExpireSeconds()`）。
- [x] 7.3 `OAuthController` 处理 `grant_type=refresh_token` 分支的方法改为调用 7.2 的新
  方法，响应体新增 `refresh_token` 字段（新值），同现有 `authorization_code` 分支的响应
  拼装方式保持一致。
- [x] 7.4 更新 `OAuthTokenService`/`OAuthController` 相关 Javadoc 与 `@Operation`
  描述（原注释声明"refresh token 不轮转……只读不删"，需改为"每次刷新旧值一次性消费、
  签发新值"）。

## 8. 测试

- [x] 8.1 CAS 登出：service 不匹配被拒绝、合法 service 触发 302 且会话失效、通知目标应用
  回调失败不阻塞登出响应。
- [x] 8.2 全局登出：CAS 协议应用与 OAuth2.0 协议应用的 service 匹配放行、appId 不存在或
  协议类型为"无"时被拒绝、service 不匹配该应用规则时被拒绝、未持有会话时仍正常 302。
- [x] 8.3 登出通知内容：CAS 应用收到最后一次 ticket，OAuth2 应用收到最后一次 AccessToken，
  未登录过的应用不收到通知，未配置回调地址的应用被跳过。
- [x] 8.4 认证配置保存：`logoutNotifyUrl` 格式校验（非法 URL 被拒绝、留空可保存）。
- [x] 8.5 OAuth2 令牌刷新：刷新成功后响应返回新的 `access_token` 与新的 `refresh_token`；
  旧 `refresh_token` 立即失效，再次用旧值请求刷新被拒绝；新 `refresh_token` 拥有完整的
  配置有效期（可查询 Redis TTL 验证）；refresh token 不存在或已过期时拒绝刷新。
