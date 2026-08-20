## MODIFIED Requirements

### Requirement: CAS 单点登录
`GET /api/authn/cas/{appId}/login` SHALL 校验 `appId` 对应应用的协议类型为 CAS 且
`service` 参数匹配该应用配置的回跳地址匹配列表（`servicePatterns`）中的至少一条规则，不匹配时 SHALL
拒绝且不发生重定向。校验通过后，若当前浏览器持有有效 SSO 会话，系统 SHALL 在签发服务票据之前，
读取当前请求的客户端 IP（`X-Forwarded-For` 请求头第一个值优先，否则取连接的远端地址）与
`User-Agent` 请求头，连同会话对应的用户、`appId` 对应应用一并校验最终生效授权（见
`app-access-authorization` 能力"考虑请求上下文"的「最终生效权限计算规则」）；不具备授权时 SHALL
返回 HTTP 403，响应体为标准 `{code, message, data}` JSON 结构（`code=403`），不发生重定向、不签发
票据。授权校验通过后系统 SHALL 签发一次性的服务票据（ST）并重定向到 `service`（附带 `ticket`
参数）；若无有效 SSO 会话，系统 SHALL 重定向到 SSO 登录页，登录成功后能够回到本次请求继续完成
授权校验与票据签发。

#### Scenario: service 未匹配任何规则被拒绝
- **WHEN** 调用方携带的 `service` 不匹配该应用配置的任何 ANT 匹配规则
- **THEN** 系统拒绝该请求，不重定向到该 `service`

#### Scenario: 未登录时先跳转 SSO 登录页
- **WHEN** 浏览器没有有效 SSO 会话地访问 CAS 单点登录接口
- **THEN** 系统重定向到 SSO 登录页；登录成功后系统签发服务票据并重定向回原始 `service`

#### Scenario: 已登录且请求满足最终生效授权（含请求控制）时直接签发票据
- **WHEN** 浏览器持有有效 SSO 会话地访问 CAS 单点登录接口，`service` 校验通过，且当前用户具备访问该应用的最终生效授权，当前请求的浏览器/IP 满足命中策略配置的请求控制条件（或命中的策略/例外未配置请求控制）
- **THEN** 系统直接签发服务票据并重定向到 `service`，不再展示登录页

#### Scenario: 用户无最终生效授权时拒绝签发票据
- **WHEN** 浏览器持有有效 SSO 会话地访问 CAS 单点登录接口，`service` 校验通过，但当前用户不具备访问 `appId` 对应应用的最终生效授权（不存在人工例外授权，也不存在任何身份命中的启用中策略）
- **THEN** 系统返回 HTTP 403，响应体为 `{code:403, message:"当前用户无权访问该应用", data:null}`，不签发服务票据，不发生重定向

#### Scenario: 身份命中但当前请求不满足命中策略的请求控制时拒绝签发票据
- **WHEN** 浏览器持有有效 SSO 会话地访问 CAS 单点登录接口，`service` 校验通过，当前用户身份命中某启用中策略（该策略配置了"仅允许 Chrome 浏览器访问"），但当前请求的 `User-Agent` 不是 Chrome，且该用户不存在能覆盖此次访问的 `GRANT` 人工例外
- **THEN** 系统返回 HTTP 403，响应体为 `{code:403, message:"当前用户无权访问该应用", data:null}`，不签发服务票据，不发生重定向

### Requirement: OAuth2 授权
`GET /api/authn/oauth/authorize` SHALL 校验 `response_type=code`、`client_id` 对应
应用的协议类型为 OAuth2.0，且 `redirect_uri` 匹配该应用配置的回跳地址匹配列表
（`servicePatterns`）中的至少一条规则，不匹配时 SHALL 拒绝且不发生重定向。校验通过后，若当前浏览器持有
有效 SSO 会话，系统 SHALL 在签发授权码之前，读取当前请求的客户端 IP（`X-Forwarded-For` 请求头第一个
值优先，否则取连接的远端地址）与 `User-Agent` 请求头，连同会话对应的用户、`client_id` 对应应用一并
校验最终生效授权（见 `app-access-authorization` 能力"考虑请求上下文"的「最终生效权限计算规则」）；
不具备授权时 SHALL 返回 HTTP 403，响应体为标准 `{code, message, data}` JSON 结构（`code=403`），不
发生重定向、不签发授权码。授权校验通过后系统 SHALL 签发一次性授权码并重定向到 `redirect_uri`（附带
`code` 与原样返回的 `state`）；若无有效 SSO 会话，系统 SHALL 重定向到 SSO 登录页。

#### Scenario: redirect_uri 未匹配任何规则被拒绝
- **WHEN** 调用方携带的 `redirect_uri` 不匹配该应用配置的任何 ANT 匹配规则
- **THEN** 系统拒绝该请求，不重定向到该 `redirect_uri`

#### Scenario: state 原样返回
- **WHEN** 调用方在授权请求中携带了 `state` 参数
- **THEN** 系统签发授权码并重定向时，在回跳 URL 上原样携带同一个 `state` 值

#### Scenario: 用户无最终生效授权时拒绝签发授权码
- **WHEN** 浏览器持有有效 SSO 会话，`redirect_uri` 校验通过，但当前用户不具备访问 `client_id` 对应应用的最终生效授权
- **THEN** 系统返回 HTTP 403，响应体为 `{code:403, message:"当前用户无权访问该应用", data:null}`，不签发授权码，不发生重定向

#### Scenario: 身份命中但当前请求不满足命中策略的请求控制时拒绝签发授权码
- **WHEN** 浏览器持有有效 SSO 会话，`redirect_uri` 校验通过，当前用户身份命中某启用中策略（该策略配置了 IP 白名单），但当前请求的客户端 IP 不在该白名单内，且该用户不存在能覆盖此次访问的 `GRANT` 人工例外
- **THEN** 系统返回 HTTP 403，响应体为 `{code:403, message:"当前用户无权访问该应用", data:null}`，不签发授权码，不发生重定向
