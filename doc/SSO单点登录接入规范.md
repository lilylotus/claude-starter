# SSO 单点登录接入规范

本文档面向需要接入本系统（RBAC 权限管理系统）做单点登录（SSO）的外部应用，说明本系统作为
身份提供方（IdP）对外提供的 CAS、OAuth2.0 两种协议的运行时端点，作为应用接入的统一规范。

对应实现代码位于 `backend/src/main/java/cn/nihility/rbac/sso/` 包下，本文档与实现保持一致，
如有出入以代码为准。协议类型、`service`/`redirect_uri` 白名单规则、登出通知回调地址的配置
管理见管理端「应用管理 - 认证管理」功能（`AppAuthConfigController`）。

## 1. 前置条件

外部应用接入前，需要先由管理员在管理端「应用配置 - 认证管理」里为该应用完成配置：

| 配置项 | 说明 |
| --- | --- |
| `authProtocol` | 协议类型，`CAS` 或 `OAUTH2`，本文档只覆盖这两种 |
| `servicePatterns` | 回跳地址 ANT 匹配规则列表，CAS 的 `service` 参数、OAuth2 的 `redirect_uri` 参数共用同一份存储（`authProtocol=CAS`/`OAUTH2` 时均按该列表校验） |
| `logoutNotifyUrl` | 登出通知回调地址，可选；配置后单点登出时系统会向该地址发起后端回调通知（见第 6 节），非空时必须是合法的 `http`/`https` URL |

CAS 场景下，外部应用发起请求时携带的 `service` 必须匹配 `servicePatterns` 中至少一条 ANT
规则；OAuth2 场景下的 `redirect_uri` 同理必须匹配 `servicePatterns`。不匹配时系统直接拒绝，
**不会发生任何重定向**（防开放重定向）。全局单点登出接口（第 5 节）的 `service` 参数同样
复用 `servicePatterns` 这套匹配规则。

OAuth2 场景下的 `client_id` 即应用对外标识（`open_app_id`），`client_secret` **复用**应用
对外接口的 SecretKey（与《接口调用签名规范.md》中签名场景使用的是同一个密钥）——见第 7 节
安全注意事项。

## 2. SSO 登录会话

本系统提供一套独立于管理端登录的 SSO 专用登录页面/接口：用户在 CAS/OAuth2 协议端点被判定
为未登录时，会被重定向到 `/sso/login?redirect=<原始请求 URL>`，登录成功后系统签发一个
HttpOnly 的浏览器级 SSO 会话 Cookie（`sso_session`），随后浏览器会被整页跳转回
`redirect` 指向的原始协议端点，继续完成票据/授权码签发。

该会话与管理端 SPA 的登录态相互独立，默认有效期 8 小时（固定过期，不做滑动续期）。外部
应用不需要、也不应该直接调用 SSO 登录接口本身——它是给浏览器跳转用的页面，不是给应用后端
调用的 API。

## 3. CAS 协议

### 3.1 单点登录：`GET /api/authn/cas/{appId}/login`

| 参数 | 位置 | 说明 |
| --- | --- | --- |
| `appId` | Path | 应用对外标识 |
| `service` | Query | 登录成功后回跳的地址，必须匹配该应用配置的 `servicePatterns` |

行为：

1. 校验 `appId` 对应应用协议类型为 `CAS` 且 `service` 匹配白名单，不通过则返回
   `400` 纯文本错误（不重定向）。
2. 校验通过后，若浏览器已持有有效 `sso_session`，签发一次性服务票据（ST）并重定向到
   `service`（追加 `ticket` 参数，若 `service` 已带 query 则用 `&` 连接，否则用 `?`）。
3. 若未登录，重定向到 `/sso/login?redirect=<当前完整请求 URL 的 URL 编码>`，登录成功后
   浏览器会自动跳回本端点继续完成第 2 步。

```http
GET /api/authn/cas/APP12345.../login?service=https%3A%2F%2Fyour-app.example.com%2Fcallback
```

已登录成功响应（302）：

```http
HTTP/1.1 302 Found
Location: https://your-app.example.com/callback?ticket=ST-3f2a1b9c4e7d4f9a8b6c1d2e3f4a5b6c
```

### 3.2 票据验证：`GET /api/authn/cas/{appId}/p3/serviceValidate`

| 参数 | 位置 | 说明 |
| --- | --- | --- |
| `appId` | Path | 应用对外标识 |
| `service` | Query | 必须与签发该票据时使用的 `service` 完全一致 |
| `ticket` | Query | 待验证的服务票据，**一次性**，验证后立即失效（无论成功与否） |

外部应用后端拿到 `ticket` 后，应在自己的服务端（而非浏览器端）发起本请求完成验证。

成功响应（CAS 3.0 XML）：

```xml
<cas:serviceResponse xmlns:cas="http://www.yale.edu/tp/cas">
  <cas:authenticationSuccess>
    <cas:user>zhangsan</cas:user>
    <cas:attributes><cas:name>张三</cas:name></cas:attributes>
  </cas:authenticationSuccess>
</cas:serviceResponse>
```

失败响应（票据不存在/已过期/已被使用/`service` 不一致，统一提示，不区分具体原因）：

```xml
<cas:serviceResponse xmlns:cas="http://www.yale.edu/tp/cas">
  <cas:authenticationFailure code="INVALID_TICKET">Ticket 不存在、已过期或已被使用</cas:authenticationFailure>
</cas:serviceResponse>
```

### 3.3 单点登出：`GET /api/authn/cas/{appId}/logout`

> **BREAKING**：本接口新增必填 `service` 参数，且不再直接返回"已登出"纯文本，改为 302
> 重定向到 `service`；接入方需相应调整调用方式（例如浏览器直接跳转本接口，而不是用
> `fetch`/`XMLHttpRequest` 发起再解析文本响应）。

| 参数 | 位置 | 说明 |
| --- | --- | --- |
| `appId` | Path | 应用对外标识 |
| `service` | Query | **必填**，登出后回跳的地址，必须匹配该应用配置的 `servicePatterns` |

行为：

1. 校验 `service` 匹配白名单（与 3.1 节登录接口相同的校验逻辑），不通过则返回 `400`
   纯文本错误（不重定向、不清除会话、不触发通知）。
2. 校验通过后依次执行：撤销当前浏览器持有的 `sso_session`（使其失效，幂等，未登录时
   同样视为成功）、清除 `sso_session` Cookie、触发一次登出后端回调通知（fire-and-forget，
   不等待通知结果，见第 6 节），最终 302 重定向到 `service`。

```http
GET /api/authn/cas/APP12345.../logout?service=https%3A%2F%2Fyour-app.example.com%2Fcallback
```

```http
HTTP/1.1 302 Found
Location: https://your-app.example.com/callback
```

## 4. OAuth2.0 协议

只支持 `authorization_code`（含 `refresh_token` 刷新）授权类型，不支持
implicit/client_credentials/password；不做真正的 `scope` 权限范围过滤。

### 4.1 授权：`GET /api/authn/oauth/authorize`

| 参数 | 位置 | 说明 |
| --- | --- | --- |
| `response_type` | Query | 必须为 `code` |
| `client_id` | Query | 即应用对外标识（`open_app_id`） |
| `redirect_uri` | Query | **必填**（本实现收紧了 OAuth2 规范里该参数"可选"的定义，见第 7 节），必须匹配该应用配置的 `servicePatterns` |
| `scope` | Query | 可选，原样透传，不做过滤 |
| `state` | Query | 可选，原样回传 |

行为（注意校验顺序）：

1. **先**校验 `redirect_uri` 是否匹配白名单——只有通过白名单校验的地址，才能安全地把
   任何错误信息重定向回去；不匹配时返回 `400` 纯文本错误（不重定向）。
2. `redirect_uri` 校验通过后，若 `response_type` 不是 `code`，重定向到 `redirect_uri`
   并追加 `error=unsupported_response_type`（及原样透传的 `state`）。
3. 若浏览器已持有有效 `sso_session`，签发一次性授权码并重定向到 `redirect_uri`（追加
   `code` 与非空的 `state`）；未登录则重定向到 `/sso/login?redirect=...`。

```http
GET /api/authn/oauth/authorize?response_type=code&client_id=APP12345...&redirect_uri=https%3A%2F%2Fyour-app.example.com%2Fcallback&state=xyz
```

```http
HTTP/1.1 302 Found
Location: https://your-app.example.com/callback?code=3f2a1b9c4e7d4f9a8b6c1d2e3f4a5b6c&state=xyz
```

### 4.2 令牌签发/刷新：`POST /api/authn/oauth/token`

统一端点，通过 `grant_type` 区分两种授权类型，参数以 `application/x-www-form-urlencoded`
表单方式传递（不放 URL query，避免 `client_secret` 出现在日志中）。

**`grant_type=authorization_code`**

| 参数 | 说明 |
| --- | --- |
| `grant_type` | 固定 `authorization_code` |
| `client_id` | 应用对外标识 |
| `client_secret` | 应用对外接口 SecretKey（明文，见第 7 节） |
| `redirect_uri` | 必须与签发该授权码时使用的值完全一致 |
| `code` | 授权码，一次性，验证后立即失效 |

成功响应（`200`）：

```json
{
  "access_token": "6f5e4d3c2b1a0f9e8d7c6b5a4f3e2d1c",
  "token_type": "Bearer",
  "expires_in": 7200,
  "refresh_token": "1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d"
}
```

失败响应：`client_secret` 不匹配返回 `401` `{"error":"invalid_client"}`；`code` 不存在/
已过期/已被使用/`redirect_uri` 不一致返回 `400` `{"error":"invalid_grant"}`。

**`grant_type=refresh_token`**

> **BREAKING**：`refresh_token` 刷新行为由"只签发新 access token、refresh_token 本身不
> 变"调整为**轮转**——每次刷新成功后旧 `refresh_token` 立即一次性消费失效（不可再用于任何
> 后续刷新请求），同时签发一个新的 `refresh_token`（响应体新增该字段），接入方**必须**用
> 新值替换本地保存的旧值，且**必须保证至少每天刷新一次**（有效期由 14 天缩短为 1 天，
> 见下方说明与第 8 节配置），否则旧值/新值均会过期失效，届时需要用户重新走一遍完整的
> 登录 + 授权流程才能拿到新的 `refresh_token`。

| 参数 | 说明 |
| --- | --- |
| `grant_type` | 固定 `refresh_token` |
| `refresh_token` | 上一次签发的 refresh token（首次为 `authorization_code` 响应中的值，此后为上一次刷新响应中的新值） |

> 本分支**只需要** `grant_type`/`refresh_token` 两个参数，**不要求** `client_id`/
> `client_secret`（见第 7 节权衡说明）。

成功响应（`200`，新增 `refresh_token` 字段）：

```json
{
  "access_token": "新签发的 access token",
  "token_type": "Bearer",
  "expires_in": 7200,
  "refresh_token": "新签发的 refresh token，需替换本地保存的旧值"
}
```

失败响应：`refresh_token` 不存在/已过期/已被使用过返回 `400` `{"error":"invalid_grant"}`。

`grant_type` 不是以上两个取值之一，返回 `400` `{"error":"unsupported_grant_type"}`。

### 4.3 用户信息查询：`GET /api/authn/oauth/userinfo`

请求头携带 `Authorization: Bearer <access_token>`。

成功响应（`200`）：

```json
{
  "sub": "10086",
  "username": "zhangsan",
  "name": "张三"
}
```

令牌缺失、格式不正确或已过期，返回 `401` 并附带 `WWW-Authenticate: Bearer` 响应头：

```json
{ "error": "invalid_token" }
```

不做 OIDC `id_token`/JWT，字段固定为以上三个基本身份字段，不随 `scope` 变化。

## 5. 全局单点登出：`GET /api/authn/{appId}/logout`

不依赖 CAS 票据流程的统一登出入口，供 OAuth2.0 接入方、前端直接触发的"退出登录"等场景
调用（不必先走一遍 CAS 协议）。

| 参数 | 位置 | 说明 |
| --- | --- | --- |
| `appId` | Path | 应用对外标识 |
| `service` | Query | **必填**，登出后回跳的地址；复用第 1 节的 `servicePatterns` 匹配规则，CAS/OAuth2.0 协议应用均按同一份存储校验 |

行为：

1. 按 `appId` 查找应用及其单点登录协议配置，应用不存在或协议类型为"无"时返回 `400`
   纯文本错误（不重定向、不清除会话、不触发通知）。
2. 校验 `service` 是否匹配该应用配置的 `servicePatterns`，不匹配同样返回 `400`
   （不重定向、不清除会话、不触发通知）。
3. 校验通过后，执行与 3.3 节 CAS 单点登出完全一致的登出逻辑：撤销当前浏览器持有的
   `sso_session`、清除 Cookie、触发一次登出后端回调通知（通知范围为本次会话实际登录过的
   **全部**应用，不局限于路径上的 `appId`，见第 6 节），最终 302 重定向到 `service`。
   未持有有效会话时同样正常返回 302，不报错。

```http
GET /api/authn/APP12345.../logout?service=https%3A%2F%2Fyour-app.example.com%2Fcallback
```

```http
HTTP/1.1 302 Found
Location: https://your-app.example.com/callback
```

## 6. 单点登出后端回调通知（back-channel SLO）

> 此前版本的接入规范中"单点登出不做 back-channel 通知"的说明已过期，现已支持；仍**不是**
> 标准 CAS SAML 格式的 `LogoutRequest` 报文，而是简化的表单字段协议，见下文。

触发时机：3.3 节 CAS 单点登出、第 5 节全局单点登出的登出主流程中，均会触发一次本节描述的
回调通知。

通知范围：本次 `sso_session` 会话期间，实际通过 CAS/OAuth2.0 端点为**哪些应用**签发过
票据/令牌，就通知哪些应用（且仅限这些应用同时配置了 `logoutNotifyUrl`，见第 1 节）；未
配置 `logoutNotifyUrl` 的应用、本次会话未登录过的应用均不会收到通知。

请求方式：系统以 `POST`、`Content-Type: application/x-www-form-urlencoded` 方式回调应用
配置的 `logoutNotifyUrl`：

| 应用协议类型 | 表单字段 | 取值 |
| --- | --- | --- |
| `CAS` | `ticket` | 该应用在本次会话最后一次签发的服务票据 |
| `OAUTH2` | `access_token` | 该应用在本次会话签发的 access token（若刷新过，为最初签发的值，刷新不重新登记映射） |

签名：请求头附带与《接口调用签名规范.md》相同的签名参数（复用该应用对外接口配置的
`accessKey`/`secretKey`/`signAlgorithm`/`needSign`），接入方应按同一套规则校验请求来源
合法性，拒绝签名不合法的回调请求。

可靠性：**不做重试**，通知为一次性 fire-and-forget（并发提交、不等待 HTTP 响应），单个
应用通知失败（超时、连接失败、返回非成功状态码）不影响其他应用的通知、也不影响登出主流程
本身的 302 响应。接入方应把该回调理解为"尽力而为"的提示，不应假设其 100% 送达；如需强
一致性，建议接入方自行给会话设置较短的本地有效期兜底。

## 7. 安全注意事项

- **`service`/`redirect_uri` 白名单**：两者都必须提前在管理端登记 ANT 匹配规则，未匹配的
  地址会被直接拒绝且不发生重定向，外部应用应确保登记的规则尽量精确，避免过于宽泛的通配符
  规则削弱防护效果；单点登出、全局登出的 `service` 复用同一套规则。
- **`redirect_uri` 必填**：与 OAuth2 规范"可选"的定义不同，本系统按 ANT 规则而非精确 URL
  登记回调地址，规则可能匹配多个具体地址，无法确定唯一默认值，因此实现上收紧为必填，
  缺省时直接拒绝。
- **`client_secret` 复用应用 SecretKey**：与《接口调用签名规范.md》中签名/验签场景使用的
  是同一个密钥，请求 body 传参而不放 URL query；请勿把 `client_secret` 写入日志或提交到
  代码仓库，遗失后需通过管理端「重置 SecretKey」重新生成（旧值同时失效，会影响该应用同时
  使用的签名验签场景）。
- **`refresh_token` 授权类型不校验 `client_id`/`client_secret`**：只要拿到一个有效的
  `refresh_token` 明文就能换取新的 access token 与新的 refresh token（一次性消费轮转，见
  4.2 节）。`refresh_token` 是 32 位随机十六进制的高熵不可猜测值，且要求以 `POST` body
  传参（不出现在 URL/日志里），外部应用应妥善保存，不要暴露在前端可读的存储中（如浏览器
  `localStorage`），建议只在后端持有；**必须**在每次刷新成功后用响应体返回的新值替换本地
  保存的旧值，且**必须保证至少每天调用一次刷新**（有效期默认 1 天），否则会因 refresh
  token 过期需要用户重新走一遍完整的登录 + 授权流程。
- **CAS 票据/OAuth2 授权码均为一次性凭证**：验证/兑换后立即失效，重复使用会被拒绝；两者
  有效期都很短（默认 120 秒 / 5 分钟），外部应用应在签发后立即完成下一步交换，不要缓存
  复用。
- 建议所有对接接口统一使用 HTTPS 传输；生产环境部署应将 `rbac.sso.cookie-secure` 配置为
  `true`，使 SSO 会话 Cookie 追加 `Secure` 属性。
- 单点登出（CAS 登出、全局登出）会触发第 6 节描述的后端回调通知，但通知本身不保证送达
  （fire-and-forget、不重试），外部应用**不应**仅依赖该通知作为登出的唯一信号，仍应结合
  自身会话的合理有效期兜底。

## 8. 相关配置

后端 `application.yml` 中 `rbac.sso` 配置节（默认值）：

```yaml
rbac:
  sso:
    cookie-secure: false                    # 生产环境（https）应设为 true
    session-expire-seconds: 28800           # SSO 会话有效期，8 小时
    cas-ticket-expire-seconds: 120          # CAS 服务票据有效期
    oauth-code-expire-seconds: 300          # OAuth2 授权码有效期，5 分钟
    oauth-token-expire-seconds: 7200        # OAuth2 access token 有效期，2 小时
    oauth-refresh-token-expire-seconds: 86400  # OAuth2 refresh token 有效期，1 天（每次刷新轮转签发新值，接入方须保证至少每天刷新一次）
```
