## MODIFIED Requirements

### Requirement: CAS 单点登录
`GET /api/authn/cas/{appId}/login` SHALL 校验 `appId` 对应应用的协议类型为 CAS 且
`service` 参数匹配该应用配置的回跳地址匹配列表（`servicePatterns`）中的至少一条规则，
不匹配时 SHALL 拒绝且不发生重定向。校验通过后，若当前浏览器持有有效 SSO 会话，系统
SHALL 签发一次性的服务票据（ST）并重定向到 `service`（附带 `ticket` 参数）；若无有效
SSO 会话，系统 SHALL 重定向到 SSO 登录页，登录成功后能够回到本次请求继续完成票据签发。

#### Scenario: service 未匹配任何规则被拒绝
- **WHEN** 调用方携带的 `service` 不匹配该应用配置的任何 ANT 匹配规则
- **THEN** 系统拒绝该请求，不重定向到该 `service`

#### Scenario: 未登录时先跳转 SSO 登录页
- **WHEN** 浏览器没有有效 SSO 会话地访问 CAS 单点登录接口
- **THEN** 系统重定向到 SSO 登录页；登录成功后系统签发服务票据并重定向回原始 `service`

#### Scenario: 已登录时直接签发票据
- **WHEN** 浏览器持有有效 SSO 会话地访问 CAS 单点登录接口，且 `service` 校验通过
- **THEN** 系统直接签发服务票据并重定向到 `service`，不再展示登录页

### Requirement: CAS 单点登出
`GET /api/authn/cas/{appId}/logout` SHALL 校验 `appId` 对应应用的协议类型为 CAS 且
`service` 参数匹配该应用配置的回跳地址匹配列表（`servicePatterns`）中的至少一条规则，
不匹配时 SHALL 拒绝且不发生重定向。校验通过后，系统 SHALL 依次执行：清除当前浏览器持有
的 SSO 会话（使其失效）、清除 `sso_session` Cookie、触发一次"单点登出后端回调通知"
（通知本次会话在其他应用建立的登录态失效），最终 302 重定向到 `service`。后端回调通知
的执行结果（成功/部分失败/超时）SHALL NOT 影响本次登出主流程与 302 重定向的正常完成。

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

### Requirement: OAuth2 授权
`GET /api/authn/oauth/authorize` SHALL 校验 `response_type=code`、`client_id` 对应
应用的协议类型为 OAuth2.0，且 `redirect_uri` 匹配该应用配置的回跳地址匹配列表
（`servicePatterns`）中的至少一条规则，不匹配时 SHALL 拒绝且不发生重定向。校验通过后，
若当前浏览器持有有效 SSO 会话，系统 SHALL 签发一次性授权码并重定向到 `redirect_uri`
（附带 `code` 与原样返回的 `state`）；若无有效 SSO 会话，系统 SHALL 重定向到 SSO 登录页。

#### Scenario: redirect_uri 未匹配任何规则被拒绝
- **WHEN** 调用方携带的 `redirect_uri` 不匹配该应用配置的任何 ANT 匹配规则
- **THEN** 系统拒绝该请求，不重定向到该 `redirect_uri`

#### Scenario: state 原样返回
- **WHEN** 调用方在授权请求中携带了 `state` 参数
- **THEN** 系统签发授权码并重定向时，在回跳 URL 上原样携带同一个 `state` 值

### Requirement: 全局单点登出接口
系统 SHALL 提供 `GET /api/authn/{appId}/logout?service={callBackServiceUrl}` 接口。
系统 SHALL 按 `appId` 查找对应应用及其单点登录协议配置，应用不存在或协议类型为"无"时
SHALL 拒绝该次请求；协议类型为 CAS 或 OAuth2.0 时，`service` SHALL 匹配该应用配置的
回跳地址匹配列表（`servicePatterns`）中的至少一条规则；不匹配时 SHALL 拒绝且不发生
重定向、不清除会话、不触发回调通知。校验通过后，系统 SHALL 执行与 CAS 单点登出接口一致
的登出逻辑：清除当前浏览器持有的 SSO 会话、清除 `sso_session` Cookie、触发一次"单点登出
后端回调通知"（通知范围为本次会话实际登录过的全部应用，不局限于路径上的 `appId`），最终
302 重定向到 `service`。该接口可供不经过 CAS 票据流程的登出入口（如 OAuth2.0 接入方、
前端直接触发的"退出登录"）统一调用。

#### Scenario: 全局登出接口清除会话并跳回 service
- **WHEN** 前端调用某 CAS 协议应用的全局登出接口，携带的 `service` 匹配该应用已配置的
  `servicePatterns` 中的至少一条规则
- **THEN** 当前浏览器持有的 SSO 会话失效、`sso_session` Cookie 被清除，系统触发登出
  回调通知后 302 重定向到该 `service`

#### Scenario: OAuth2.0 协议应用的全局登出
- **WHEN** 前端调用某 OAuth2.0 协议应用的全局登出接口，携带的 `service` 匹配该应用已
  配置的 `servicePatterns` 中的至少一条规则
- **THEN** 当前浏览器持有的 SSO 会话失效、`sso_session` Cookie 被清除，系统触发登出
  回调通知后 302 重定向到该 `service`

#### Scenario: appId 不存在或协议类型为无时被拒绝
- **WHEN** 调用方携带的 `appId` 不存在，或该应用的单点登录协议类型为"无"
- **THEN** 系统拒绝该请求，不清除会话，不发生重定向，不触发回调通知

#### Scenario: service 未匹配该应用规则时被拒绝
- **WHEN** 调用方携带的 `service` 不匹配 `appId` 对应应用配置的 `servicePatterns`
- **THEN** 系统拒绝该请求，不清除会话，不发生重定向，不触发回调通知

#### Scenario: 未持有有效会话时仍正常响应
- **WHEN** 调用方在没有有效 `sso_session` Cookie 的情况下访问全局登出接口，且 `appId`/
  `service` 校验通过
- **THEN** 系统不报错，直接 302 重定向到 `service`，不触发任何回调通知
