## Purpose

CAS 与 OAuth2.0 单点登录协议的运行时能力：外部应用按 `app-auth-protocol-config`
已配置的协议类型与回跳地址匹配规则，通过标准的 CAS/OAuth2.0 协议端点让用户免登录接入，
本系统作为身份提供方（IdP），复用现有用户账号体系签发票据/令牌。

## Requirements

### Requirement: SSO 专用登录与浏览器会话
系统 SHALL 提供独立于管理端登录的 SSO 登录能力：账号密码校验通过后，系统 SHALL 签发一个
HttpOnly 的浏览器 SSO 会话（Cookie），该会话与管理端 SPA 的登录态相互独立。SSO 会话
SHALL 有过期时间，过期后需要重新登录。

#### Scenario: SSO 登录成功签发会话
- **WHEN** 用户在 SSO 登录页提交正确的账号密码
- **THEN** 系统建立一个 SSO 会话，并通过 HttpOnly Cookie 下发给浏览器

#### Scenario: SSO 登录与管理端登录互不影响
- **WHEN** 用户已持有管理端 SPA 的登录态，但从未做过 SSO 登录
- **THEN** 该用户访问 CAS/OAuth2 协议端点时仍被判定为未建立 SSO 会话，需要走 SSO 登录

### Requirement: CAS 单点登录
`GET /api/authn/cas/{appId}/login` SHALL 校验 `appId` 对应应用的协议类型为 CAS 且
`service` 参数匹配该应用配置的 service ANT 匹配列表中的至少一条规则，不匹配时 SHALL
拒绝且不发生重定向。校验通过后，若当前浏览器持有有效 SSO 会话，系统 SHALL 签发一次性的
服务票据（ST）并重定向到 `service`（附带 `ticket` 参数）；若无有效 SSO 会话，系统 SHALL
重定向到 SSO 登录页，登录成功后能够回到本次请求继续完成票据签发。

#### Scenario: service 未匹配任何规则被拒绝
- **WHEN** 调用方携带的 `service` 不匹配该应用配置的任何 ANT 匹配规则
- **THEN** 系统拒绝该请求，不重定向到该 `service`

#### Scenario: 未登录时先跳转 SSO 登录页
- **WHEN** 浏览器没有有效 SSO 会话地访问 CAS 单点登录接口
- **THEN** 系统重定向到 SSO 登录页；登录成功后系统签发服务票据并重定向回原始 `service`

#### Scenario: 已登录时直接签发票据
- **WHEN** 浏览器持有有效 SSO 会话地访问 CAS 单点登录接口，且 `service` 校验通过
- **THEN** 系统直接签发服务票据并重定向到 `service`，不再展示登录页

### Requirement: CAS 票据验证
`GET /api/authn/cas/{appId}/p3/serviceValidate` SHALL 校验 `ticket` 存在、未过期、
未被消费过，且签发时绑定的 `service` 与本次请求的 `service` 一致；校验通过后 SHALL
将该票据标记为已消费（一次性），并返回 CAS 3.0 格式的认证成功 XML 响应（含用户标识）；
校验不通过时 SHALL 返回 CAS 格式的认证失败 XML 响应。

#### Scenario: 合法票据校验成功且不可重复使用
- **WHEN** 调用方使用一个刚签发、`service` 匹配的合法票据发起验证请求
- **THEN** 系统返回认证成功响应；调用方用同一票据再次发起验证请求时，系统返回认证失败响应

#### Scenario: service 不一致时校验失败
- **WHEN** 调用方使用的票据是为另一个 `service` 签发的
- **THEN** 系统返回认证失败响应，不消费该票据的有效性判定结果泄露给非授权调用方

### Requirement: CAS 单点登出
`GET /api/authn/cas/{appId}/logout` SHALL 清除当前浏览器持有的 SSO 会话。

#### Scenario: 登出后原会话失效
- **WHEN** 用户访问 CAS 单点登出接口
- **THEN** 该浏览器持有的 SSO 会话失效，后续访问 CAS/OAuth2 协议端点被判定为未登录

### Requirement: OAuth2 授权
`GET /api/authn/oauth/authorize` SHALL 校验 `response_type=code`、`client_id` 对应
应用的协议类型为 OAuth2.0，且 `redirect_uri` 匹配该应用配置的 redirect_uri ANT 匹配
列表中的至少一条规则，不匹配时 SHALL 拒绝且不发生重定向。校验通过后，若当前浏览器持有
有效 SSO 会话，系统 SHALL 签发一次性授权码并重定向到 `redirect_uri`（附带 `code` 与
原样返回的 `state`）；若无有效 SSO 会话，系统 SHALL 重定向到 SSO 登录页。

#### Scenario: redirect_uri 未匹配任何规则被拒绝
- **WHEN** 调用方携带的 `redirect_uri` 不匹配该应用配置的任何 ANT 匹配规则
- **THEN** 系统拒绝该请求，不重定向到该 `redirect_uri`

#### Scenario: state 原样返回
- **WHEN** 调用方在授权请求中携带了 `state` 参数
- **THEN** 系统签发授权码并重定向时，在回跳 URL 上原样携带同一个 `state` 值

### Requirement: OAuth2 令牌签发
`POST /api/authn/oauth/token` 在 `grant_type=authorization_code` 时 SHALL 校验
`client_id` 与 `client_secret` 匹配该应用的凭证、`code` 存在且未过期未被消费、
`redirect_uri` 与签发该授权码时使用的 `redirect_uri` 一致；校验通过后 SHALL 将该
授权码标记为已消费（一次性），签发一个具有有效期的 access token 与一个具有更长有效期
的 refresh token，返回标准 OAuth2 JSON 响应（`access_token`/`token_type`/
`expires_in`/`refresh_token`）。

#### Scenario: 合法授权码换取令牌成功且不可重复使用
- **WHEN** 调用方使用一个刚签发、参数匹配的合法授权码请求令牌
- **THEN** 系统返回 access token；调用方用同一授权码再次请求令牌时，系统拒绝

#### Scenario: client_secret 不匹配时拒绝签发
- **WHEN** 调用方提供的 `client_secret` 与该应用的凭证不一致
- **THEN** 系统拒绝签发令牌

#### Scenario: redirect_uri 与签发授权码时不一致时拒绝
- **WHEN** 调用方请求令牌时提供的 `redirect_uri` 与获取该授权码时使用的 `redirect_uri` 不同
- **THEN** 系统拒绝签发令牌

### Requirement: OAuth2 令牌刷新
`POST /api/authn/oauth/token` 在 `grant_type=refresh_token` 时 SHALL 校验
`refresh_token` 参数对应的凭证存在且未过期；校验通过后 SHALL 签发一个新的 access
token，返回标准 OAuth2 JSON 响应（`access_token`/`token_type`/`expires_in`）；
`refresh_token` 本身不因本次刷新而失效或改变，在其自身有效期内可重复用于换取新的
access token。

#### Scenario: 合法 refresh_token 刷新成功
- **WHEN** 调用方使用一个未过期的 `refresh_token` 请求刷新
- **THEN** 系统签发一个新的 access token 并返回

#### Scenario: refresh_token 不存在或已过期时拒绝
- **WHEN** 调用方使用的 `refresh_token` 不存在或已过期
- **THEN** 系统拒绝签发新的 access token

### Requirement: OAuth2 用户信息查询
`GET /api/authn/oauth/userinfo` SHALL 校验请求头 `Authorization: Bearer <access_token>`
携带的令牌存在且未过期，校验通过后 SHALL 返回该令牌绑定用户的基本身份信息；令牌缺失、
格式不正确或已过期时 SHALL 拒绝并返回 401。

#### Scenario: 合法令牌查询用户信息成功
- **WHEN** 调用方携带一个有效的 access token 请求用户信息接口
- **THEN** 系统返回该令牌签发时绑定用户的基本身份信息

#### Scenario: 令牌过期或不存在时拒绝
- **WHEN** 调用方携带的 access token 已过期或不存在
- **THEN** 系统拒绝该请求，返回 401
