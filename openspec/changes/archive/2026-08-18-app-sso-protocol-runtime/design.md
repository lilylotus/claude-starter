## Context

`app-auth-protocol-config` 已落地：`tab_app_auth_config` 表存了每个应用的
`authProtocol`（NONE/CAS/OAUTH2）、`casServicePatterns`/`oauth2RedirectUriPatterns`
两个 ANT 匹配规则列表，`AppAuthConfigService.getByAppId` 已经按应用 AppId 计算出
6 个协议接口地址（`/api/authn/cas/{appId}/login` 等）展示给管理员，但这些路径背后
目前没有真正的 Controller。管理端现有登录（`AuthController`/`AuthServiceImpl`/
`TokenServiceImpl`/`LoginView.vue`/`stores/auth.ts`）用的是 Bearer Token
（`identity-token` 请求头），由前端 SPA 持有并在每次 axios 请求上附带，不适合"浏览器
顶层跳转 + 判断是否已登录"这种场景（详见上一轮已确认的决定：新增独立的 Cookie 会话，
且**不复用**管理端登录页面/接口，另建一套 SSO 专用登录页面与接口）。

密码校验复用现有 `PasswordService.verifyPassword` + `UserMapper`（按 `tab_user.code`
查账号）+ `RbacLoginProperties` 的 RSA 密钥对（只复用密钥材料，不复用登录接口/页面/
会话逻辑）。Redis 已是既有依赖，`TokenServiceImpl` 已有"短期凭证存 Redis、
`StringRedisTemplate` 操作、UUID 去横线做 opaque token"的既有模式，本次延续。

`GlobalResponseAdvice` 会把 `cn.nihility.rbac` 包下所有 Controller 的返回值包装成
`{code,message,data}`（二进制下载类响应除外）——但 CAS 要求 XML 响应、OAuth2 要求
标准 JSON 响应（`access_token`/`token_type`/`expires_in`，不是 `{code,message,data}`
外壳），且这一批端点大多数情况下是 302 重定向，不需要走响应体包装。

## Goals / Non-Goals

**Goals:**
- 定义 SSO 会话（Cookie）的签发、校验、清除机制，以及独立 SSO 登录页面/接口的边界。
- 定义 CAS 三个端点、OAuth2 三个端点的完整处理流程、参数校验规则、错误响应格式。
- 定义如何绕开 `GlobalResponseAdvice` 的响应包装，保证协议响应格式符合 CAS/OAuth2 标准。
- 定义票据/授权码/令牌/会话的 Redis 存储结构与有效期。

**Non-Goals:** 见 proposal.md Non-Goals（`authorization_code`/`refresh_token` 之外
的其它 OAuth2 授权类型、CAS 代理票据、back-channel SLO、scope 过滤）。

## Decisions

### 1. 后端模块划分

新增顶层模块 `cn.nihility.rbac.sso`（对齐 `cn.nihility.rbac.sync`/`cn.nihility.rbac.app`
的顶层模块粒度，因为 SSO 运行时横跨"独立登录"+"CAS"+"OAuth2"三块，不适合塞进
`cn.nihility.rbac.auth`——那是管理端登录专属，也不适合塞进 `cn.nihility.rbac.app`——
那是应用主数据/配置管理专属）：

```
cn.nihility.rbac.sso
├── config/RbacSsoProperties          # SSO 相关配置：Cookie 名称、各类凭证有效期
├── session/
│   ├── SsoSessionService             # 签发/校验/清除 SSO 会话（Redis），读写 Cookie
│   └── SsoSessionCookieUtils         # 手写 Set-Cookie 响应头（HttpOnly/SameSite/Path/Max-Age）
├── controller/SsoLoginController     # GET public-key、POST login（独立于 AuthController）
├── dto/SsoLoginRequest.java
├── support/
│   ├── AppProtocolGuard              # 按 appId 查 AppAuthConfigEntity + AppConfigEntity，
│   │                                  # 校验协议类型、用 AntPathMatcher 校验 service/redirect_uri，
│   │                                  # 另提供 resolveOAuthClientConfig 供 token 端点查
│   │                                  # client_secret 校验所需的 AppConfigEntity
│   └── ProtocolResponseWriter        # 直接用 HttpServletResponse 写 XML/JSON/redirect，
│                                      # 绕开 GlobalResponseAdvice（见 Decision 4）
├── cas/
│   ├── controller/CasController      # /api/authn/cas/{appId}/login|p3/serviceValidate|logout
│   ├── service/CasTicketService      # 签发/校验消费 ST（Redis）
│   └── support/CasXmlResponses       # CAS 3.0 XML 响应拼接
└── oauth/
    ├── controller/OAuthController    # /api/authn/oauth/authorize|token|userinfo
    └── service/OAuthTokenService     # 签发/校验消费授权码、签发/校验 access token（Redis）
```

`AppProtocolGuard` 复用已注入的 `AppAuthConfigMapper`/`AppConfigMapper`（不经过
`AppAuthConfigService`/`AppConfigService`——那两个服务层带管辖组织范围校验/操作日志等
管理端语义，这里是无登录态的公开端点，直接查 Mapper 更清晰，同 `NotifySignatureAppender`
不经 Service 层直连 Mapper 的既有模式）。

### 2. SSO 会话（Cookie）

- Cookie 名称：`sso_session`；值：32 位随机十六进制字符串（同现有 `newTokenValue()`
  模式，UUID 去横线）。
- 存储：Redis `sso:session:<token>` → `userId`（字符串），`EXPIRE` 设为会话有效期
  （`RbacSsoProperties.sessionExpireSeconds`，默认 28800 秒/8 小时），**固定过期**，
  不做滑动续期（YAGNI，多数 CAS/OAuth2 场景一次会话内完成多次跳转即可，不需要保活）。
- Set-Cookie 响应头手写（不用 `jakarta.servlet.http.Cookie`，避免不同 Servlet 容器/
  版本对 `SameSite` 属性支持不一致的问题）：
  `sso_session=<token>; Path=/; HttpOnly; SameSite=Lax; Max-Age=<seconds>`；
  `RbacSsoProperties.cookieSecure`（默认 `false`，本地 http 开发环境）为 `true` 时
  追加 `; Secure`。`SameSite=Lax` 足够——CAS/OAuth2 场景下浏览器总是以顶层导航
  （GET 跳转）访问本系统域名下的协议端点，Lax 策略允许顶层导航携带 Cookie。
- 登出（`GET /api/authn/cas/{appId}/logout`）：删除 Redis 记录 + 下发一个
  `Max-Age=0` 的同名 Cookie 覆盖清除浏览器端的 Cookie。

### 3. SSO 专用登录页面/接口

完全独立，不改动 `AuthController`/`AuthServiceImpl`/`LoginView.vue`/`stores/auth.ts`/
`api/request.ts`（用户已明确要求）：

- `GET /api/authn/sso/public-key`：直接返回 `RbacLoginProperties.publicKey`
  （复用同一份 RSA 密钥材料——只是复用密钥，不复用登录接口/会话逻辑；引入第二套密钥对
  没有实际收益，徒增配置项）。
- `POST /api/authn/sso/login`（`SsoLoginRequest`：`account`/`password`，均为 RSA
  密文，同管理端登录一致的加密方式）：用 `RbacLoginProperties.privateKey` 解密，
  `UserMapper` 按 `code` 查用户（状态需为 `ENABLED`，同管理端登录规则），
  `PasswordService.verifyPassword` 校验密码，通过后 `SsoSessionService.issue(userId)`
  并写 Set-Cookie，返回 `Result.success()`。失败统一提示"账号或密码不正确"
  （同管理端登录的既有防信息泄露模式，`AuthServiceImpl.LOGIN_FAILED_MESSAGE`
  的字面值复制一份，不做代码级复用——两边各自独立演进）。
  **不做**"首次登录强制改密"拦截——SSO 登录场景没有对应的强制改密页面，若账号处于
  待首登改密状态，仍允许 SSO 登录成功（该账号的密码修改仍只能通过管理端完成）。
- 前端 `views/sso/SsoLoginView.vue` + 路由 `path: '/sso/login'`（顶层路由，不挂在
  `AppLayout` 下，`meta` 不设 `requiresAuth`，不受现有 `router.beforeEach` 守卫影响）：
  表单提交到新建的 `api/sso.ts`（独立的 axios 实例或直接 `fetch`，不导入
  `api/request.ts`，避免复用管理端的 `identity-token`/`menu` 请求头拦截器与静默刷新
  逻辑——那套逻辑语义上完全不适用于这里）。登录成功后读取当前 URL 的 `redirect` query
  参数，执行 `window.location.href = redirect`（整页跳转，不能用 `router.push`——
  `redirect` 指向的是后端 `/api/authn/cas/**`/`/api/authn/oauth/**` 这样的原生 URL，
  不是一个 SPA 路由）。

### 4. 绕开 GlobalResponseAdvice 的响应包装

CAS/OAuth2 三类端点（重定向、XML、标准 OAuth2 JSON）一律不通过 Controller 方法返回值
走 Spring MVC 的消息转换器管线，而是注入 `HttpServletResponse`，方法签名声明为
`void`，直接调用 `response.sendRedirect(...)` / 手写
`response.setContentType(...)` + `response.getWriter().write(...)`。`void` 返回值
不会触发 `RequestResponseBodyMethodProcessor`/`HttpEntityMethodProcessor`，自然不会
经过 `GlobalResponseAdvice`，不需要对该 Advice 做任何特殊改动，也不依赖"`byte[]`
返回值被排除在包装之外"这类间接绕过手段——更直接、更不依赖框架内部行为的写法。
`support/ProtocolResponseWriter` 封装三种写法（`redirect`/`xml`/`json`），供
`CasController`/`OAuthController` 复用，避免每个端点重复写字符编码/Content-Type
样板代码。

`POST /api/authn/oauth/token`/`GET /api/authn/oauth/userinfo` 同样返回 `void`，
手写 JSON（用 `JacksonUtils.toJson` 序列化一个普通 Map/DTO 后整体写出，不经过
`Result` 包装）。

### 5. CAS 协议实现细节

- **service 校验**：`AppProtocolGuard.assertCasServiceAllowed(appId, service)`——
  按 `appId` 查 `AppAuthConfigEntity`，`authProtocol` 必须是 `CAS`，用
  `AntPathMatcher.match(pattern, service)` 遍历 `casServicePatterns`，任一命中即通过；
  均不命中或应用不存在/协议不是 CAS 时，`ProtocolResponseWriter` 直接写一个 400
  纯文本错误响应（不重定向——`service` 本身就是不可信输入，不能把浏览器重定向到一个
  未经校验的地址）。
- **ST 格式与存储**：`ST-` + 32 位随机十六进制（贴近真实 CAS 票据命名习惯，非协议
  强制要求）。Redis `cas:st:<ticket>` → JSON `{appId, service, userId}`，
  `EXPIRE` = `RbacSsoProperties.casTicketExpireSeconds`（默认 120 秒——CAS 票据
  本来就设计成"签发后几秒内立刻被 serviceValidate 消费掉"的一次性短期凭证）。
- **login 端点流程**：`assertCasServiceAllowed` → 读 `sso_session` Cookie 校验
  `SsoSessionService.verify` → 有效则签发 ST、`sendRedirect(service + (含?追加|不含
  追加) + "ticket=" + ST)`；无效则 `sendRedirect("/sso/login?redirect=" +
  URLEncoder.encode(当前完整请求 URI including query, UTF_8))`。
- **serviceValidate 端点流程**：读 Redis `cas:st:<ticket>`，不存在 → 失败 XML；存在则
  立即 `DELETE`（一次性消费，无论后续校验成功与否都不可能再被使用）；校验记录里的
  `service` 与请求参数 `service` 是否相等，不等 → 失败 XML；相等 → 按 `userId` 查
  `UserEntity` 取 `code`/`name`，成功 XML：
  ```xml
  <cas:serviceResponse xmlns:cas="http://www.yale.edu/tp/cas">
    <cas:authenticationSuccess>
      <cas:user>{code}</cas:user>
      <cas:attributes><cas:name>{name}</cas:name></cas:attributes>
    </cas:authenticationSuccess>
  </cas:serviceResponse>
  ```
  失败 XML：
  ```xml
  <cas:serviceResponse xmlns:cas="http://www.yale.edu/tp/cas">
    <cas:authenticationFailure code="INVALID_TICKET">Ticket 不存在、已过期或已被使用</cas:authenticationFailure>
  </cas:serviceResponse>
  ```
- **logout 端点流程**：读 `sso_session` Cookie，存在则 Redis `DELETE` +
  下发 `Max-Age=0` 覆盖 Cookie；不存在则视为已登出，同样返回成功提示（幂等）。
  返回一段极简的纯文本/HTML 提示"已登出"，不做花哨页面（proposal.md Non-Goals 已
  声明不做 back-channel SLO，这里也不重定向到任何第三方地址，避免开放重定向）。

### 6. OAuth2 协议实现细节

- **redirect_uri 校验**：与 CAS service 校验同构，`AppProtocolGuard.
  assertOAuthRedirectUriAllowed(clientId, redirectUri)`，`authProtocol` 必须是
  `OAUTH2`。**本实现要求 `redirect_uri` 必填**（虽然 OAuth2 规范里该参数标注"可选"，
  但规范的"可选"前提是服务端为每个 client 登记了唯一的默认回调地址，可以在参数缺省时
  兜底；本系统按 ANT 规则而非精确 URL 登记回调地址，规则可能匹配多个具体地址，无法
  确定唯一默认值，因此实现上收紧为必填，缺省时直接拒绝并返回错误——这是本次对协议
  "可选"字段的一处已知收紧，记录在此，不在 spec.md 内单独建一条需求，因为它是
  `redirect_uri 未匹配任何规则被拒绝`场景在"参数缺失"这个子情形下的自然延伸）。
- **授权码格式与存储**：32 位随机十六进制。Redis `oauth:code:<code>` → JSON
  `{clientId, redirectUri, userId, scope}`，`EXPIRE` =
  `RbacSsoProperties.oauthCodeExpireSeconds`（默认 300 秒/5 分钟，对齐 RFC 6749
  建议的授权码短有效期）。
- **authorize 端点流程**：`assertOAuthRedirectUriAllowed` → 不通过则
  `ProtocolResponseWriter.text` 400（`redirect_uri` 本身就是不可信输入，此时还没有
  经过白名单校验，不能把浏览器重定向到它）；通过后才校验 `response_type=code`
  （不等于时按 OAuth2 标准把 `error=unsupported_response_type` 重定向回
  `redirect_uri`，而不是纯错误页——因为走到这一步 `redirect_uri` 已经过白名单校验，
  可以安全地把错误回传给客户端）→ 校验 SSO 会话 → 有效则签发授权码，
  `sendRedirect(redirect_uri + "?code=" + code + (state 非空则追加 "&state=" +
  state))`；无效则跳 `/sso/login?redirect=...`（同 CAS）。**注意实现顺序**：
  `redirect_uri` 白名单校验必须先于 `response_type` 校验执行，否则
  `unsupported_response_type` 错误重定向本身就会构成一次开放重定向。
- **token 端点流程**：按 `grant_type` 分两支，均由同一个 `POST
  /api/authn/oauth/token` 端点处理：
  - `grant_type=authorization_code`：`AppProtocolGuard.resolveOAuthClientConfig
    (clientId)`（实现时在 `AppProtocolGuard` 上新增的第三个方法，与
    `assertCasServiceAllowed`/`assertOAuthRedirectUriAllowed` 同属"按对外应用
    标识查配置"这一族）按 `client_id` 查
    `AppConfigEntity`+`AppAuthConfigEntity`（协议须为 OAUTH2，不存在/协议不符
    统一提示不泄露具体区别）→ 解密存储的 `secretKey`（`Sm4JdkUtils` +
    `AppSecretProperties` 的 SM4 主密钥，同应用同步签名场景复用的解密方式）
    与请求 `client_secret` 做常量时间比较
    （`java.security.MessageDigest.isEqual`，避免时序攻击）→ 不等则
    `error=invalid_client`，HTTP 401 JSON → 读 Redis `oauth:code:<code>`，不存在/
    已过期 → `error=invalid_grant`，HTTP 400 JSON；存在则立即 `DELETE`
    （一次性消费）→ 校验记录里的 `clientId`/`redirectUri` 与请求参数一致，不一致 →
    `error=invalid_grant` → 一致则签发 access token **与 refresh token**：Redis
    `oauth:token:<token>` → JSON `{clientId, userId, scope}`，`EXPIRE` =
    `RbacSsoProperties.oauthTokenExpireSeconds`（默认 7200 秒/2 小时，对齐管理端
    access-key 有效期默认值）；`oauth:refresh:<refreshToken>` → JSON
    `{clientId, userId, scope}`，`EXPIRE` =
    `RbacSsoProperties.oauthRefreshTokenExpireSeconds`（默认 1209600 秒/14 天，
    明显长于 access token 有效期，这是 refresh token 存在的意义），返回：
    ```json
    {"access_token": "...", "token_type": "Bearer", "expires_in": 7200, "refresh_token": "..."}
    ```
  - `grant_type=refresh_token`：读 Redis `oauth:refresh:<refresh_token>`，不存在/
    已过期 → `error=invalid_grant`，HTTP 400 JSON；存在则**不删除**该记录（非轮转，
    见下方说明）取出其 `clientId`/`userId`/`scope`，签发一个新的 access token
    （同上写入 `oauth:token:<token>`），返回：
    ```json
    {"access_token": "...", "token_type": "Bearer", "expires_in": 7200}
    ```
    本分支按用户明确给出的参数列表实现，只要求 `refresh_token`/`grant_type` 两个
    参数，不再校验 `client_id`/`client_secret`（与 Risks 里记录的权衡对应）。
  - `grant_type` 不是以上两个取值之一 → `error=unsupported_grant_type`，HTTP 400
    JSON。
- **refresh token 不轮转**：一次刷新只签发新的 access token，`refresh_token` 本身
  不失效、不替换，在自身有效期内可反复使用（对齐现有管理端
  `TokenServiceImpl.refresh` 的既有先例——那里同样是"只换新 access-key，
  refresh-key 不变"，本次延续同一约定，不引入另一套刷新语义）。
- **userinfo 端点流程**：解析 `Authorization: Bearer <token>` 请求头（格式不对 →
  401 + `WWW-Authenticate: Bearer` 头）→ 读 Redis `oauth:token:<token>`，不存在/
  已过期 → 401 → 存在则按 `userId` 查 `UserEntity`，返回：
  ```json
  {"sub": "<userId>", "username": "<code>", "name": "<name>"}
  ```
  （不做 OIDC `id_token`/JWT，纯 OAuth2 + 一个约定俗成的 `/userinfo` JSON 端点，
  claim 集合限定为这三个基本身份字段，proposal.md Non-Goals 已声明不做 `scope`
  过滤——不管请求的 `scope` 是什么，返回的字段集合固定不变）。

### 7. `IdentityAuthFilter` 白名单

`FULL_WHITELIST` 新增一条 `"/api/authn/**"`（覆盖 `sso`/`cas`/`oauth` 三个子路径），
理由：这批端点面向浏览器/外部应用，不使用管理端 SPA 的 `identity-token`/`menu`
请求头鉴权（同现有 `/open/api/sync/**` 的既有先例——那批端点面向外部应用，鉴权走
AccessKey + 签名而不是这个 Filter；这批端点面向浏览器，鉴权走本 change 新增的
SSO Cookie 会话，同样不适用这个 Filter 的机制）。

## Risks / Trade-offs

- **[Risk] 开放重定向（Open Redirect）**：`service`/`redirect_uri` 若校验不严会被
  用作钓鱼跳转跳板 → **Mitigation**：两处都强制走 ANT 白名单匹配，不匹配直接拒绝
  （不重定向，纯文本/JSON 错误响应）；已在 spec.md 里作为独立场景登记，实现阶段必须
  用真实测试用例覆盖"不匹配时不发生任何重定向"。
- **[Risk] 单实例 Redis 依赖**：票据/授权码/令牌/会话全部依赖 Redis，Redis 故障时
  SSO 整体不可用 → **Mitigation**：与现有管理端登录（`TokenServiceImpl`）同样依赖
  Redis，风险敞口不是本次新增的，属于项目既有架构约束，不在本次解决范围。
- **[Risk] `client_secret` 是应用对外接口 SecretKey 的复用**：如果外部应用把
  `client_secret` 误用在签名验签场景之外的不安全传输方式上（如明文放 URL 参数）会
  暴露同一个密钥，影响面同时波及"应用同步签名"与"OAuth2 客户端认证"两个能力 →
  **Mitigation**：`POST /api/authn/oauth/token` 用 `application/x-www-form-urlencoded`
  或 JSON body 传参（不放 URL query），符合 OAuth2 规范本身的要求（`redirect_uri`
  等参数放 body 而非 query）；文档层面在接口调用签名规范.md 或后续对接文档里提醒
  外部应用不要把 `client_secret` 写进日志。
- **[Risk] `refresh_token` 授权类型不校验 `client_id`/`client_secret`**：只要拿到一个
  有效的 `refresh_token` 明文就能换取新的 access token，泄露 `refresh_token` 的
  影响等同于泄露一段时期内的账号访问能力，且不像 `authorization_code` 授权那样有
  客户端身份这一层校验兜底 → **Mitigation**：这是按用户明确给出的参数列表
  （只有 `refresh_token`/`grant_type` 两个参数）实现的结果，非本设计主动引入；
  `refresh_token` 本身是 32 位随机十六进制的高熵不可猜测值，且要求 `POST` body
  传参（不出现在 URL/日志里），有效期虽长（14 天）但仍是有界的；若后续需要收紧，
  可在 `refresh_token` 授权分支追加 `client_id`/`client_secret` 校验，是一个
  向后兼容的收紧（不破坏已签发的 `refresh_token`），可作为独立的后续调整。
- **[Trade-off] refresh token 不轮转**：同一个 `refresh_token` 可在有效期内重复使用
  换取新 access token（而不是每次刷新都换发新的 `refresh_token` 并让旧的失效）→
  对齐现有 `TokenServiceImpl.refresh` 的既有先例，实现更简单，代价是同一枚
  `refresh_token` 一旦泄露，在其 14 天有效期内持续有效直到自然过期，不会因为
  正常使用方也在刷新而被动失效。
