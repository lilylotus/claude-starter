## 1. 配置与基础设施

- [x] 1.1 新增 `cn.nihility.rbac.sso.config.RbacSsoProperties`（`@ConfigurationProperties`
      前缀 `rbac.sso`）：`cookieSecure`（默认 `false`）、`sessionExpireSeconds`（默认
      28800）、`casTicketExpireSeconds`（默认 120）、`oauthCodeExpireSeconds`（默认
      300）、`oauthTokenExpireSeconds`（默认 7200）、`oauthRefreshTokenExpireSeconds`
      （默认 1209600），`application.yml` 补充对应配置节（显式列出默认值，同现有
      `rbac.http-client`/`rbac.sync` 的写法习惯）。
- [x] 1.2 `IdentityAuthFilter.FULL_WHITELIST` 新增 `"/api/authn/**"`（design.md
      Decision 7）。

## 2. SSO 会话

- [x] 2.1 新增 `cn.nihility.rbac.sso.session.SsoSessionCookieUtils`：构造/解析
      Set-Cookie 响应头字符串（`sso_session=<token>; Path=/; HttpOnly; SameSite=Lax`
      + 按 `cookieSecure` 追加 `; Secure` + `Max-Age`），以及从
      `HttpServletRequest.getCookies()` 提取 `sso_session` 值的辅助方法。
- [x] 2.2 新增 `cn.nihility.rbac.sso.session.SsoSessionService`：
      `issue(Long userId): String`（生成 token，写 Redis `sso:session:<token>` →
      userId，`EXPIRE` = `sessionExpireSeconds`，返回 token）、
      `Optional<Long> verify(String token)`、`void revoke(String token)`。

## 3. SSO 专用登录接口（后端）

- [x] 3.1 新增 `cn.nihility.rbac.sso.dto.SsoLoginRequest`（`account`/`password`，
      均 `@NotBlank`，RSA 密文）。
- [x] 3.2 新增 `cn.nihility.rbac.sso.controller.SsoLoginController`：
      - `GET /api/authn/sso/public-key` 返回 `RbacLoginProperties.publicKey`
        （复用同一 `PublicKeyVO` DTO 或新建同形状的 DTO，二选一，实现时按代码整洁度
        决定）。
      - `POST /api/authn/sso/login`：`RsaJdkUtils.decrypt` 解密账号密码 →
        `UserMapper` 按 `code` 查用户（状态需 `ENABLED`）→
        `PasswordService.verifyPassword` → 均通过则
        `SsoSessionService.issue(userId)` + `SsoSessionCookieUtils` 写
        Set-Cookie，返回 `Result.success()`；任一环节失败统一返回业务异常
        "账号或密码不正确"（design.md Decision 3，不复用
        `AuthServiceImpl.LOGIN_FAILED_MESSAGE` 常量，各自独立字面量）。

## 4. 协议共享支撑

- [x] 4.1 新增 `cn.nihility.rbac.sso.support.AppProtocolGuard`：
      - `AppAuthConfigEntity assertCasServiceAllowed(String appId, String service)`：
        按 `open_app_id` 查 `AppConfigEntity` 得 `appRefId`，查
        `AppAuthConfigEntity`，`authProtocol` 须为 `CAS`，`AntPathMatcher` 遍历
        `casServicePatterns`（JSON 文本先用 `JacksonUtils` 解析）匹配 `service`，
        均不通过时抛出专用异常（供 controller 层捕获后走"不重定向的错误响应"分支，
        不是 `BusinessException`/`GlobalExceptionHandler` 的 `{code,message,data}`
        包装路径——这批端点整体绕开 `GlobalResponseAdvice`，见 design.md Decision 4）。
      - `AppAuthConfigEntity assertOAuthRedirectUriAllowed(String clientId, String
        redirectUri)`：同构，`authProtocol` 须为 `OAUTH2`，`redirectUri` 为空时
        直接判不通过（design.md Decision 6"收紧为必填"）。
      - `AppConfigEntity resolveOAuthClientConfig(String clientId)`：实现时新增
        的第三个方法，供 `OAuthController` 的 `token` 端点 `authorization_code`
        分支按 `client_id` 查询 `AppConfigEntity`（校验存在且协议为 `OAUTH2`），
        用于后续解密 `secretKey` 与请求 `client_secret` 比较；应用不存在与协议
        不是 OAuth2.0 两种情况复用同一提示信息，不向调用方泄露具体区别。
- [x] 4.2 新增 `cn.nihility.rbac.sso.support.ProtocolResponseWriter`：
      `redirect(HttpServletResponse, String location)`、
      `xml(HttpServletResponse, String xmlBody)`、
      `json(HttpServletResponse, int httpStatus, Object body)`、
      `text(HttpServletResponse, int httpStatus, String message)` 四个方法，统一
      设置 Content-Type/字符编码后写出，供 `CasController`/`OAuthController` 复用
      （design.md Decision 4）。

## 5. CAS 协议

- [x] 5.1 新增 `cn.nihility.rbac.sso.cas.service.CasTicketService`：
      `String issue(String appId, String service, Long userId)`（Redis
      `cas:st:<ticket>` → JSON，`EXPIRE` = `casTicketExpireSeconds`，ticket 格式
      `ST-` + 32 位随机十六进制）、`Optional<CasTicketPayload> consume(String
      ticket)`（读后立即 `DELETE`，一次性消费）。
- [x] 5.2 新增 `cn.nihility.rbac.sso.cas.support.CasXmlResponses`：拼接成功/失败两种
      CAS 3.0 XML 响应字符串（design.md Decision 5 给出的具体 XML 结构）。
- [x] 5.3 新增 `cn.nihility.rbac.sso.cas.controller.CasController`：
      - `GET /api/authn/cas/{appId}/login`（`service` 必填）：
        `assertCasServiceAllowed` → 读 `sso_session` Cookie → 校验通过则
        `CasTicketService.issue` + `ProtocolResponseWriter.redirect` 到
        `service` 追加 `ticket` 参数；未登录则 `redirect` 到
        `/sso/login?redirect=<urlencode(当前请求完整 URI + query)>`；
        `assertCasServiceAllowed` 校验不通过则 `ProtocolResponseWriter.text`
        400 错误。
      - `GET /api/authn/cas/{appId}/p3/serviceValidate`（`service`/`ticket` 必填）：
        `CasTicketService.consume` → 不存在/`service` 不一致 → 失败 XML；一致 →
        查 `UserMapper` → 成功 XML。
      - `GET /api/authn/cas/{appId}/logout`：读 Cookie → 存在则
        `SsoSessionService.revoke` + 下发 `Max-Age=0` 覆盖 Cookie；
        `ProtocolResponseWriter.text` 200 "已登出"。

## 6. OAuth2 协议

- [x] 6.1 新增 `cn.nihility.rbac.sso.oauth.service.OAuthTokenService`：
      `String issueCode(String clientId, String redirectUri, Long userId, String
      scope)`（Redis `oauth:code:<code>`，`EXPIRE` = `oauthCodeExpireSeconds`）、
      `Optional<OAuthCodePayload> consumeCode(String code)`（一次性消费）、
      `IssuedToken issueAccessTokenWithRefresh(String clientId, Long userId,
      String scope)`（同时写 `oauth:token:<token>`，`EXPIRE` =
      `oauthTokenExpireSeconds`，与 `oauth:refresh:<refreshToken>`，`EXPIRE` =
      `oauthRefreshTokenExpireSeconds`，返回值含 accessToken + refreshToken +
      accessToken 的 `expires_in`）、`String issueAccessTokenOnly(String clientId,
      Long userId, String scope)`（`grant_type=refresh_token` 场景只签发新
      access token，不动 refresh token 记录）、`Optional<OAuthTokenPayload>
      verifyAccessToken(String token)`、`Optional<OAuthRefreshPayload>
      verifyRefreshToken(String refreshToken)`（design.md"refresh token 不轮转"，
      本方法只读不删）。
- [x] 6.2 新增 `cn.nihility.rbac.sso.oauth.dto.OAuthTokenRequest`（`client_id`/
      `client_secret`/`redirect_uri`/`grant_type`/`code`/`refresh_token`，均可
      为空，具体哪些必填按 `grant_type` 分支在 service/controller 层校验）。
      实际未用 `@ModelAttribute` 自动绑定——OAuth2 标准参数名是 snake_case
      （如 `client_id`/`redirect_uri`），Spring `@ModelAttribute` 默认按驼峰
      属性名绑定对不上；改为 controller 方法签名上逐个 `@RequestParam`
      （snake_case 名称）接收后手动 `OAuthTokenRequest.builder()` 构造。
- [x] 6.3 新增 `cn.nihility.rbac.sso.oauth.controller.OAuthController`：
      - `GET /api/authn/oauth/authorize`（`response_type`/`client_id`/
        `redirect_uri`/`scope`/`state`）：`assertOAuthRedirectUriAllowed` 不通过
        → `ProtocolResponseWriter.text` 400（须先于 `response_type` 校验执行，
        否则 `unsupported_response_type` 错误重定向本身就是开放重定向，见
        design.md Decision 6）；通过后 `response_type` 非 `code` → 重定向携带
        `error=unsupported_response_type`；读 Cookie 校验会话 → 通过则
        `issueCode` + `redirect` 到 `redirect_uri` 追加 `code`（与非空 `state`）；
        未登录则 `redirect` 到 `/sso/login?redirect=...`。
      - `POST /api/authn/oauth/token`（`OAuthTokenRequest`）按 `grant_type` 分支：
        - `authorization_code`：按 `client_id` 查
          `AppConfigEntity`+`AppAuthConfigEntity`（协议须 OAUTH2）解密
          `secretKey` 与请求 `client_secret` 用 `MessageDigest.isEqual` 比较，
          不等 → `json` 401 `{error:"invalid_client"}`；`consumeCode` 不存在/
          `clientId`/`redirectUri` 不匹配 → `json` 400
          `{error:"invalid_grant"}`；均通过 → `issueAccessTokenWithRefresh` →
          `json` 200
          `{access_token,token_type:"Bearer",expires_in,refresh_token}`。
        - `refresh_token`：`verifyRefreshToken` 不存在/已过期 → `json` 400
          `{error:"invalid_grant"}`；命中则 `issueAccessTokenOnly` → `json` 200
          `{access_token,token_type:"Bearer",expires_in}`（不要求
          `client_id`/`client_secret`，design.md Decision 6/Risks 已说明）。
        - 其它 `grant_type` 取值 → `json` 400 `{error:"unsupported_grant_type"}`。
      - `GET /api/authn/oauth/userinfo`（读 `Authorization` 请求头）：格式不对/
        `verifyAccessToken` 未命中 → `json` 401 且设置
        `WWW-Authenticate: Bearer` 响应头；命中则查 `UserEntity` →
        `json` 200 `{sub,username,name}`。

## 7. 后端测试

- [x] 7.1 `SsoSessionServiceTest`：签发/校验/清除/过期后校验失败。
- [x] 7.2 `CasTicketServiceTest`：签发/一次性消费/重复消费失败/过期失败。
- [x] 7.3 `OAuthTokenServiceTest`：授权码签发/一次性消费/access token 签发校验/过期；
      refresh token 签发/校验/过期，以及"刷新后 refresh token 记录仍存在（不轮转）"。
- [x] 7.4 `AppProtocolGuardTest`：service/redirect_uri 匹配通过/不匹配拒绝/协议类型
      不符拒绝/应用不存在拒绝。
- [x] 7.5 `CasControllerTest`（`@SpringBootTest` + `MockMvc`，Redis/MySQL 用真实
      连接，同现有 `AppNotifyServiceImplTest` 起真实依赖的风格，不做重量级 mock）：
      覆盖 spec.md 全部 CAS 场景，断言 `Location` 响应头与 XML 响应体内容。
- [x] 7.6 `OAuthControllerTest`（同上风格）：覆盖 spec.md 全部 OAuth2 场景（含
      `grant_type=refresh_token` 刷新成功、`refresh_token` 不存在/过期时拒绝），
      断言 `Location` 响应头、JSON 响应体、错误状态码。
- [x] 7.7 `./gradlew test --tests "cn.nihility.rbac.sso.*"` 确认新增测试通过；
      `./gradlew build` 确认全量编译 + 测试通过。

## 8. 前端：SSO 登录页面

- [x] 8.1 新增 `frontend/src/api/sso.ts`：独立于 `api/request.ts` 的最小 axios
      实例（或直接用 `fetch`），封装 `getSsoPublicKey`/`ssoLogin` 两个调用
      （design.md Decision 3）。
- [x] 8.2 新增 `frontend/src/views/sso/SsoLoginView.vue`：账号密码表单（RSA 加密
      提交，复用与管理端登录相同的加密工具函数，若已有可复用的加密封装
      则直接复用，否则新增一份不依赖 `stores/auth.ts`），提交成功后
      `window.location.href = (route.query.redirect as string) || '/'`。
- [x] 8.3 `frontend/src/router/index.ts` 新增顶层路由 `path: '/sso/login'`，
      `name: 'sso-login'`，不挂载 `AppLayout` 下，`meta` 不设 `requiresAuth`。
- [x] 8.4 `npm run build`（`frontend/` 目录下）确认 vue-tsc 类型检查 + vite build
      通过。

## 9. 端到端验证

- [x] 9.1 用真实浏览器/脚本驱动跑通完整 CAS 流程：访问
      `/api/authn/cas/{appId}/login?service=xxx`（`xxx` 匹配已配置规则）→ 未登录
      重定向到 SSO 登录页 → 登录成功 → 重定向回并带 `ticket` → 用该 `ticket` 调
      `p3/serviceValidate` 拿到成功 XML → 用同一 `ticket` 再调一次确认返回失败 XML。
      已用脚本（`node` + 原生 `fetch`/`crypto`，起真实本地 `./gradlew bootRun` +
      现有 MySQL/Redis）驱动真实 HTTP 请求跑通，全部断言通过。
- [x] 9.2 用真实浏览器/脚本驱动跑通完整 OAuth2 流程：访问
      `/api/authn/oauth/authorize?...` → 未登录重定向 SSO 登录页 → 登录成功 →
      重定向回并带 `code`（`state` 原样透传）→ 用该 `code` 调 `token`（
      `grant_type=authorization_code`）换取 `access_token`/`refresh_token` →
      用同一 `code` 再调一次确认 `invalid_grant` → 用 `access_token` 调
      `userinfo` 拿到用户信息 → 用一个明显错误/过期的 token 调 `userinfo` 确认
      401 → 用上一步拿到的 `refresh_token` 调 `token`（`grant_type=refresh_token`，
      只传 `refresh_token`/`grant_type` 两个参数）换取新的 `access_token` 并确认
      新 token 能正常调通 `userinfo` → 用一个不存在的 `refresh_token` 调用确认
      `invalid_grant`。同上脚本跑通，全部断言通过（含 `client_secret` 不匹配拒绝、
      `redirect_uri` 不一致拒绝、`response_type` 非 `code` 重定向携带
      `error=unsupported_response_type` 等分支）。
- [x] 9.3 验证开放重定向防护：`service`/`redirect_uri` 传一个不匹配任何已配置规则
      的地址，确认系统直接返回错误响应，浏览器网络面板里不出现对该地址的重定向。
      脚本对 CAS/OAuth2 两个协议各验证一次：响应为 `400` 纯文本/无 `Location`
      响应头，未发生任何重定向。
- [x] 9.4 CAS 登出：登出后浏览器再次访问 `/api/authn/cas/{appId}/login?service=xxx`
      确认又被重定向到 SSO 登录页（会话已失效）。脚本验证通过。
      端到端验证脚本：`sso-e2e.mjs`（会话级临时文件，未提交仓库；验证过程中通过
      管理端接口把测试应用 `tab_app.id=1` 的认证配置临时切到 CAS 再切到 OAuth2 并
      重置了其 SecretKey，验证结束后已将协议类型/回跳地址规则还原为验证前的
      `OAUTH2` + `http://app.example.com/callback`，SecretKey 重置不可逆但该应用
      是本地开发库中的测试数据，不影响其它功能）。

## 10. 文档

- [x] 10.1 更新 `接口调用签名规范.md` 或另建一份 SSO 对接文档（视实现时判断，若
      `接口调用签名规范.md` 语境不合适承载 CAS/OAuth2 协议说明，可新建
      `SSO单点登录接入规范.md`），补充 CAS/OAuth2 端点的实际请求/响应示例，
      特别是 `client_secret` 复用应用 SecretKey 这一点需要明确告知外部应用。
      已新建仓库根目录 `SSO单点登录接入规范.md`（`接口调用签名规范.md` 语境是应用同步
      签名/验签，与 CAS/OAuth2 协议无关，不适合承载本次内容）。
