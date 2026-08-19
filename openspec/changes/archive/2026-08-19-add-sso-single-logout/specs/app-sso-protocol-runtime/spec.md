## MODIFIED Requirements

### Requirement: OAuth2 令牌刷新
`POST /api/authn/oauth/token` 在 `grant_type=refresh_token` 时 SHALL 校验
`refresh_token` 参数对应的凭证存在且未过期；校验通过后 SHALL 将该旧
`refresh_token` 标记为已消费（一次性，立即失效，不可再被用于任何后续刷新请求），
同时签发一个新的 access token 与一个新的 `refresh_token`（拥有完整的配置有效期），
返回标准 OAuth2 JSON 响应（`access_token`/`token_type`/`expires_in`/`refresh_token`，
其中 `refresh_token` 为本次新签发的值）。

#### Scenario: 合法 refresh_token 刷新成功且旧值被消费
- **WHEN** 调用方使用一个未过期的 `refresh_token` 请求刷新
- **THEN** 系统返回新的 access token 与新的 `refresh_token`；调用方再次使用同一个
  旧 `refresh_token` 请求刷新时，系统拒绝

#### Scenario: refresh_token 不存在或已过期时拒绝
- **WHEN** 调用方使用的 `refresh_token` 不存在或已过期
- **THEN** 系统拒绝签发新的 access token，也不签发新的 refresh_token

#### Scenario: 新签发的 refresh_token 拥有完整有效期
- **WHEN** 调用方使用一个即将到期的 `refresh_token` 成功请求刷新
- **THEN** 本次响应返回的新 `refresh_token` 拥有完整的配置有效期（而不是延续旧值剩余
  的有效期），可在其自身有效期内继续用于换取新的 access token 与下一次轮转

### Requirement: CAS 单点登出
`GET /api/authn/cas/{appId}/logout` SHALL 校验 `appId` 对应应用的协议类型为 CAS 且
`service` 参数匹配该应用配置的 service ANT 匹配列表中的至少一条规则，不匹配时 SHALL
拒绝且不发生重定向。校验通过后，系统 SHALL 依次执行：清除当前浏览器持有的 SSO 会话
（使其失效）、清除 `sso_session` Cookie、触发一次"单点登出后端回调通知"（通知本次
会话在其他应用建立的登录态失效），最终 302 重定向到 `service`。后端回调通知的执行
结果（成功/部分失败/超时）SHALL NOT 影响本次登出主流程与 302 重定向的正常完成。

#### Scenario: service 未匹配任何规则被拒绝
- **WHEN** 调用方携带的 `service` 不匹配该应用配置的任何 ANT 匹配规则
- **THEN** 系统拒绝该请求，不清除会话，不发生重定向

#### Scenario: 登出后原会话失效并跳回 service
- **WHEN** 用户携带匹配的 `service` 参数访问 CAS 单点登出接口
- **THEN** 该浏览器持有的 SSO 会话失效、`sso_session` Cookie 被清除，且系统 302
  重定向到 `service`

#### Scenario: 回调通知失败不阻塞登出流程
- **WHEN** 用户访问 CAS 单点登出接口，且本次会话登录过的某个应用的登出回调通知
  请求失败或超时
- **THEN** 系统仍完成会话失效、Cookie 清除，并正常 302 重定向到 `service`，不因该
  应用的通知失败而报错或延迟响应

## ADDED Requirements

### Requirement: 全局单点登出接口
系统 SHALL 提供 `GET /api/authn/{appId}/logout?service={callBackServiceUrl}` 接口。
系统 SHALL 按 `appId` 查找对应应用及其单点登录协议配置，应用不存在或协议类型为"无"时
SHALL 拒绝该次请求；协议类型为 CAS 时，`service` SHALL 匹配该应用配置的 CAS service
匹配列表中的至少一条规则；协议类型为 OAuth2.0 时，`service` SHALL 匹配该应用配置的
OAuth2 redirect_uri 匹配列表中的至少一条规则；不匹配时 SHALL 拒绝且不发生重定向、
不清除会话、不触发回调通知。校验通过后，系统 SHALL 执行与 CAS 单点登出接口一致的登出
逻辑：清除当前浏览器持有的 SSO 会话、清除 `sso_session` Cookie、触发一次"单点登出后端
回调通知"（通知范围为本次会话实际登录过的全部应用，不局限于路径上的 `appId`），最终
302 重定向到 `service`。该接口可供不经过 CAS 票据流程的登出入口（如 OAuth2.0 接入方、
前端直接触发的"退出登录"）统一调用。

#### Scenario: 全局登出接口清除会话并跳回 service
- **WHEN** 前端调用某 CAS 协议应用的全局登出接口，携带的 `service` 匹配该应用已配置的
  CAS service 匹配列表中的至少一条规则
- **THEN** 当前浏览器持有的 SSO 会话失效、`sso_session` Cookie 被清除，系统触发登出
  回调通知后 302 重定向到该 `service`

#### Scenario: OAuth2.0 协议应用的全局登出
- **WHEN** 前端调用某 OAuth2.0 协议应用的全局登出接口，携带的 `service` 匹配该应用已
  配置的 redirect_uri 匹配列表中的至少一条规则
- **THEN** 当前浏览器持有的 SSO 会话失效、`sso_session` Cookie 被清除，系统触发登出
  回调通知后 302 重定向到该 `service`

#### Scenario: appId 不存在或协议类型为无时被拒绝
- **WHEN** 调用方携带的 `appId` 不存在，或该应用的单点登录协议类型为"无"
- **THEN** 系统拒绝该请求，不清除会话，不发生重定向，不触发回调通知

#### Scenario: service 未匹配该应用规则时被拒绝
- **WHEN** 调用方携带的 `service` 不匹配 `appId` 对应应用（按其协议类型）配置的匹配规则
- **THEN** 系统拒绝该请求，不清除会话，不发生重定向，不触发回调通知

#### Scenario: 未持有有效会话时仍正常响应
- **WHEN** 调用方在没有有效 `sso_session` Cookie 的情况下访问全局登出接口，且 `appId`/
  `service` 校验通过
- **THEN** 系统不报错，直接 302 重定向到 `service`，不触发任何回调通知

### Requirement: 单点登出后端回调通知
CAS 单点登出与全局登出触发登出主流程时，系统 SHALL 对本次 `sso_session` 会话实际
登录过的每一个应用（即本次会话期间曾为该应用签发过 CAS 服务票据或 OAuth2.0
AccessToken 的应用），逐一以 `POST`、`Content-Type: application/x-www-form-urlencoded`
方式回调该应用配置的登出通知回调地址（`logoutNotifyUrl`）：协议类型为 CAS 的应用，
回调表单 SHALL 携带该应用在本次会话中最后一次签发的服务票据（字段名 `ticket`）；
协议类型为 OAuth2.0 的应用，回调表单 SHALL 携带该应用在本次会话中签发的 AccessToken
（字段名 `access_token`）。回调请求 SHALL 附带签名信息（复用应用凭证配置的
accessKey/secretKey 计算），供接收方校验请求来源合法性。

未配置 `logoutNotifyUrl` 的应用 SHALL 被跳过，不发起回调；单个应用的回调请求失败、
超时或返回非成功状态 SHALL 被独立捕获，不影响其余应用的回调通知，也不影响登出主流程。

#### Scenario: 会话登录过的多个应用均收到登出通知
- **WHEN** 某次 SSO 会话期间，用户先后通过 CAS 登录了应用 A（配置了
  `logoutNotifyUrl`）、通过 OAuth2.0 登录了应用 B（配置了 `logoutNotifyUrl`），随后
  该会话触发登出
- **THEN** 系统向应用 A 的回调地址 POST 表单（含字段 `ticket`，值为应用 A 在本次会话
  最后签发的服务票据），向应用 B 的回调地址 POST 表单（含字段 `access_token`，值为
  应用 B 在本次会话签发的 AccessToken），两次回调均携带签名信息

#### Scenario: 未配置回调地址的应用被跳过
- **WHEN** 本次会话登录过的某个应用未配置 `logoutNotifyUrl`
- **THEN** 系统不向该应用发起回调请求，不因缺少地址而报错

#### Scenario: 未登录过的应用不收到通知
- **WHEN** 某应用已配置 `logoutNotifyUrl`，但本次会话从未通过该应用的 CAS/OAuth2.0
  端点签发过任何票据或令牌
- **THEN** 系统不向该应用发起登出回调通知

#### Scenario: 单个应用回调失败不影响其他应用
- **WHEN** 本次会话登录过应用 A 与应用 B，向应用 A 的回调请求超时失败
- **THEN** 系统仍正常完成向应用 B 的回调通知，且登出主流程不受影响
