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
授权校验与票据签发。`service` 校验失败、最终生效权限校验失败、票据签发成功三种结果 SHALL 各自
触发一次 `sso-protocol-access-log` 能力的调用记录（事件类型 `LOGIN`）；重定向到 SSO 登录页这一步
本身 SHALL NOT 触发记录（尚未发生任何 CAS 协议语义上的动作）。

#### Scenario: service 未匹配任何规则被拒绝
- **WHEN** 调用方携带的 `service` 不匹配该应用配置的任何 ANT 匹配规则
- **THEN** 系统拒绝该请求，不重定向到该 `service`，记录一条失败的调用记录（失败原因为 service 未匹配）

#### Scenario: 未登录时先跳转 SSO 登录页
- **WHEN** 浏览器没有有效 SSO 会话地访问 CAS 单点登录接口
- **THEN** 系统重定向到 SSO 登录页；登录成功后系统签发服务票据并重定向回原始 `service`；重定向到
  登录页这一步不产生调用记录

#### Scenario: 已登录且请求满足最终生效授权（含请求控制）时直接签发票据
- **WHEN** 浏览器持有有效 SSO 会话地访问 CAS 单点登录接口，`service` 校验通过，且当前用户具备访问该应用的最终生效授权，当前请求的浏览器/IP 满足命中策略配置的请求控制条件（或命中的策略/例外未配置请求控制）
- **THEN** 系统直接签发服务票据并重定向到 `service`，不再展示登录页，记录一条成功的调用记录（含解析到的用户 id）；即使本次访问复用了此前已建立的 SSO 会话、未重新输入账号密码，本次票据签发依然单独记一条

#### Scenario: 用户无最终生效授权时拒绝签发票据
- **WHEN** 浏览器持有有效 SSO 会话地访问 CAS 单点登录接口，`service` 校验通过，但当前用户不具备访问 `appId` 对应应用的最终生效授权（不存在人工例外授权，也不存在任何身份命中的启用中策略）
- **THEN** 系统返回 HTTP 403，响应体为 `{code:403, message:"当前用户无权访问该应用", data:null}`，不签发服务票据，不发生重定向，记录一条失败的调用记录

#### Scenario: 身份命中但当前请求不满足命中策略的请求控制时拒绝签发票据
- **WHEN** 浏览器持有有效 SSO 会话地访问 CAS 单点登录接口，`service` 校验通过，当前用户身份命中某启用中策略（该策略配置了"仅允许 Chrome 浏览器访问"），但当前请求的 `User-Agent` 不是 Chrome，且该用户不存在能覆盖此次访问的 `GRANT` 人工例外
- **THEN** 系统返回 HTTP 403，响应体为 `{code:403, message:"当前用户无权访问该应用", data:null}`，不签发服务票据，不发生重定向，记录一条失败的调用记录

### Requirement: CAS 票据验证
`GET /api/authn/cas/{appId}/p3/serviceValidate` SHALL 校验 `ticket` 存在、未过期、
`service` 参数与签发该票据时使用的 `service` 一致，票据仅可被消费一次；校验通过后返回认证成功响应
（含用户属性），否则返回认证失败响应。本端点由应用后端服务器直接调用（不经过浏览器），不涉及
`tab_login_log` 的账号/密码语义，成功/失败均 SHALL 触发一次 `sso-protocol-access-log` 能力的调用
记录（事件类型 `SERVICE_VALIDATE`），失败时记录票据不存在/已过期/已使用或 `service` 不一致等具体
原因。

#### Scenario: 合法票据校验成功记录调用
- **WHEN** 应用后端服务器携带一个刚签发、未消费过的合法票据请求校验
- **THEN** 系统返回认证成功响应，记录一条成功的调用记录，包含票据绑定的用户 id

#### Scenario: 票据已被消费或过期时记录失败
- **WHEN** 应用后端服务器携带一个已经被消费过、或已过期的票据请求校验
- **THEN** 系统返回认证失败响应，记录一条失败的调用记录，失败原因为票据不存在/已过期/已被使用

### Requirement: CAS 单点登出
`GET /api/authn/cas/{appId}/logout` SHALL 校验 `appId` 对应应用的协议类型为 CAS 且
`service` 参数匹配该应用配置的回跳地址匹配列表（`servicePatterns`）中的至少一条规则，不匹配时 SHALL
拒绝且不发生重定向。校验通过后，系统 SHALL 依次执行：清除当前浏览器持有的 SSO 会话
（使其失效）、清除 `sso_session` Cookie、触发一次"单点登出后端回调通知"（通知本次
会话在其他应用建立的登录态失效），最终 302 重定向到 `service`。后端回调通知的执行
结果（成功/部分失败/超时）SHALL NOT 影响本次登出主流程与 302 重定向的正常完成。`service`
校验失败与登出执行成功两种结果 SHALL 各自触发一次 `sso-protocol-access-log` 能力的调用记录
（事件类型 `LOGOUT`），未持有有效会话时的登出仍视为一次成功的调用记录（用户 id 为空）。

#### Scenario: service 未匹配任何规则被拒绝
- **WHEN** 调用方携带的 `service` 不匹配该应用配置的任何 ANT 匹配规则
- **THEN** 系统拒绝该请求，不清除会话，不发生重定向，记录一条失败的调用记录

#### Scenario: 登出后原会话失效并跳回 service
- **WHEN** 用户携带匹配的 `service` 参数访问 CAS 单点登出接口
- **THEN** 该浏览器持有的 SSO 会话失效、`sso_session` Cookie 被清除，且系统 302
  重定向到 `service`，记录一条成功的调用记录（含被登出的用户 id）

#### Scenario: 回调通知失败不阻塞登出流程
- **WHEN** 用户访问 CAS 单点登出接口，且本次会话登录过的某个应用的登出回调通知
  请求失败或超时
- **THEN** 系统仍完成会话失效、Cookie 清除，并正常 302 重定向到 `service`，不因该
  应用的通知失败而报错或延迟响应，调用记录不受回调通知结果影响、仍记为成功

### Requirement: 全局单点登出接口
系统 SHALL 提供 `GET /api/authn/{appId}/logout?service={callBackServiceUrl}` 接口。
系统 SHALL 按 `appId` 查找对应应用及其单点登录协议配置，应用不存在或协议类型为"无"时
SHALL 拒绝该次请求；协议类型为 CAS 或 OAuth2.0 时，`service` SHALL 匹配该应用配置的
回跳地址匹配列表（`servicePatterns`）中的至少一条规则；不匹配时 SHALL 拒绝且不发生
重定向、不清除会话、不触发回调通知。校验通过后，系统 SHALL 执行与 CAS 单点登出接口一致的登出
逻辑：清除当前浏览器持有的 SSO 会话、清除 `sso_session` Cookie、触发一次"单点登出后端
回调通知"（通知范围为本次会话实际登录过的全部应用，不局限于路径上的 `appId`），最终
302 重定向到 `service`。该接口可供不经过 CAS 票据流程的登出入口（如 OAuth2.0 接入方、
前端直接触发的"退出登录"）统一调用。校验失败与登出执行成功两种结果 SHALL 各自触发一次
`sso-protocol-access-log` 能力的调用记录（事件类型 `LOGOUT`，协议类型取该应用当前实际配置的
协议类型，而不是固定为 CAS）。

#### Scenario: 全局登出接口清除会话并跳回 service
- **WHEN** 前端调用某 CAS 协议应用的全局登出接口，携带的 `service` 匹配该应用已配置的
  `servicePatterns` 中的至少一条规则
- **THEN** 当前浏览器持有的 SSO 会话失效、`sso_session` Cookie 被清除，系统触发登出
  回调通知后 302 重定向到该 `service`，记录一条协议类型为 CAS 的成功调用记录

#### Scenario: OAuth2.0 协议应用的全局登出
- **WHEN** 前端调用某 OAuth2.0 协议应用的全局登出接口，携带的 `service` 匹配该应用已
  配置的 `servicePatterns` 中的至少一条规则
- **THEN** 当前浏览器持有的 SSO 会话失效、`sso_session` Cookie 被清除，系统触发登出
  回调通知后 302 重定向到该 `service`，记录一条协议类型为 OAuth2.0 的成功调用记录

#### Scenario: appId 不存在或协议类型为无时被拒绝
- **WHEN** 调用方携带的 `appId` 不存在，或该应用的单点登录协议类型为"无"
- **THEN** 系统拒绝该请求，不清除会话，不发生重定向，不触发回调通知，记录一条失败的调用记录

#### Scenario: service 未匹配该应用规则时被拒绝
- **WHEN** 调用方携带的 `service` 不匹配 `appId` 对应应用配置的 `servicePatterns`
- **THEN** 系统拒绝该请求，不清除会话，不发生重定向，不触发回调通知，记录一条失败的调用记录

#### Scenario: 未持有有效会话时仍正常响应
- **WHEN** 调用方在没有有效 `sso_session` Cookie 的情况下访问全局登出接口，且 `appId`/
  `service` 校验通过
- **THEN** 系统不报错，直接 302 重定向到 `service`，不触发任何回调通知，仍记录一条成功的调用记录（用户 id 为空）

### Requirement: OAuth2 授权
`GET /api/authn/oauth/authorize` SHALL 校验 `response_type=code`、`client_id` 对应
应用的协议类型为 OAuth2.0，且 `redirect_uri` 匹配该应用配置的回跳地址匹配列表
（`servicePatterns`）中的至少一条规则，不匹配时 SHALL 拒绝且不发生重定向。校验通过后，若当前浏览器持有
有效 SSO 会话，系统 SHALL 在签发授权码之前，读取当前请求的客户端 IP（`X-Forwarded-For` 请求头第一个
值优先，否则取连接的远端地址）与 `User-Agent` 请求头，连同会话对应的用户、`client_id` 对应应用一并
校验最终生效授权（见 `app-access-authorization` 能力"考虑请求上下文"的「最终生效权限计算规则」）；
不具备授权时 SHALL 返回 HTTP 403，响应体为标准 `{code, message, data}` JSON 结构（`code=403`），不
发生重定向、不签发授权码。授权校验通过后系统 SHALL 签发一次性授权码并重定向到 `redirect_uri`（附带
`code` 与原样返回的 `state`）；若无有效 SSO 会话，系统 SHALL 重定向到 SSO 登录页。`redirect_uri`
校验失败、`response_type` 不支持、最终生效权限校验失败、授权码签发成功四种结果 SHALL 各自触发一次
`sso-protocol-access-log` 能力的调用记录（事件类型 `AUTHORIZE`）；重定向到 SSO 登录页这一步本身
SHALL NOT 触发记录。

#### Scenario: redirect_uri 未匹配任何规则被拒绝
- **WHEN** 调用方携带的 `redirect_uri` 不匹配该应用配置的任何 ANT 匹配规则
- **THEN** 系统拒绝该请求，不重定向到该 `redirect_uri`，记录一条失败的调用记录

#### Scenario: state 原样返回
- **WHEN** 调用方在授权请求中携带了 `state` 参数
- **THEN** 系统签发授权码并重定向时，在回跳 URL 上原样携带同一个 `state` 值

#### Scenario: 用户无最终生效授权时拒绝签发授权码
- **WHEN** 浏览器持有有效 SSO 会话，`redirect_uri` 校验通过，但当前用户不具备访问 `client_id` 对应应用的最终生效授权
- **THEN** 系统返回 HTTP 403，响应体为 `{code:403, message:"当前用户无权访问该应用", data:null}`，不签发授权码，不发生重定向，记录一条失败的调用记录

#### Scenario: 身份命中但当前请求不满足命中策略的请求控制时拒绝签发授权码
- **WHEN** 浏览器持有有效 SSO 会话，`redirect_uri` 校验通过，当前用户身份命中某启用中策略（该策略配置了 IP 白名单），但当前请求的客户端 IP 不在该白名单内，且该用户不存在能覆盖此次访问的 `GRANT` 人工例外
- **THEN** 系统返回 HTTP 403，响应体为 `{code:403, message:"当前用户无权访问该应用", data:null}`，不签发授权码，不发生重定向，记录一条失败的调用记录

#### Scenario: 复用已有会话签发授权码时同样记录
- **WHEN** 浏览器已持有有效 SSO 会话（此前已登录，本次未重新输入账号密码）访问授权接口，各项校验均通过
- **THEN** 系统签发授权码并重定向，记录一条成功的调用记录；即使没有发生新的凭证校验，本次授权码签发依然单独记一条

### Requirement: OAuth2 令牌签发
`POST /api/authn/oauth/token` 在 `grant_type=authorization_code` 时 SHALL 校验
`client_id` 与 `client_secret` 匹配该应用的凭证、`code` 存在且未过期未被消费、
`redirect_uri` 与签发该授权码时使用的 `redirect_uri` 一致；校验通过后 SHALL 将该
授权码标记为已消费（一次性），签发一个具有有效期的 access token 与一个具有更长有效期
的 refresh token，返回标准 OAuth2 JSON 响应（`access_token`/`token_type`/
`expires_in`/`refresh_token`）。本端点由应用后端服务器直接调用，不涉及 `tab_login_log` 的
账号/密码语义，`grant_type` 不支持、参数缺失、`client_secret` 不匹配、`code`/`redirect_uri`
校验失败、签发成功等结果 SHALL 各自触发一次 `sso-protocol-access-log` 能力的调用记录（事件类型
`TOKEN`）。

#### Scenario: 合法授权码换取令牌成功且不可重复使用
- **WHEN** 调用方使用一个刚签发、参数匹配的合法授权码请求令牌
- **THEN** 系统返回 access token，记录一条成功的调用记录（含授权码绑定的用户 id）；调用方用同一授权码再次请求令牌时，系统拒绝并记录一条失败的调用记录

#### Scenario: client_secret 不匹配时拒绝签发
- **WHEN** 调用方提供的 `client_secret` 与该应用的凭证不一致
- **THEN** 系统拒绝签发令牌，记录一条失败的调用记录

#### Scenario: redirect_uri 与签发授权码时不一致时拒绝
- **WHEN** 调用方请求令牌时提供的 `redirect_uri` 与获取该授权码时使用的 `redirect_uri` 不同
- **THEN** 系统拒绝签发令牌，记录一条失败的调用记录

### Requirement: OAuth2 令牌刷新
`POST /api/authn/oauth/token` 在 `grant_type=refresh_token` 时 SHALL 校验
`refresh_token` 参数对应的凭证存在且未过期；校验通过后 SHALL 将该旧
`refresh_token` 标记为已消费（一次性，立即失效，不可再被用于任何后续刷新请求），
同时签发一个新的 access token 与一个新的 `refresh_token`（拥有完整的配置有效期），
返回标准 OAuth2 JSON 响应（`access_token`/`token_type`/`expires_in`/`refresh_token`，
其中 `refresh_token` 为本次新签发的值）。参数缺失、`refresh_token` 校验失败、刷新成功等结果
SHALL 各自触发一次 `sso-protocol-access-log` 能力的调用记录（事件类型 `TOKEN`，与令牌签发共用
同一事件类型，均属于本端点）。

#### Scenario: 合法 refresh_token 刷新成功且旧值被消费
- **WHEN** 调用方使用一个未过期的 `refresh_token` 请求刷新
- **THEN** 系统返回新的 access token 与新的 `refresh_token`，记录一条成功的调用记录；调用方再次使用同一个
  旧 `refresh_token` 请求刷新时，系统拒绝并记录一条失败的调用记录

#### Scenario: refresh_token 不存在或已过期时拒绝
- **WHEN** 调用方使用的 `refresh_token` 不存在或已过期
- **THEN** 系统拒绝签发新的 access token，也不签发新的 refresh_token，记录一条失败的调用记录

#### Scenario: 新签发的 refresh_token 拥有完整有效期
- **WHEN** 调用方使用一个即将到期的 `refresh_token` 成功请求刷新
- **THEN** 本次响应返回的新 `refresh_token` 拥有完整的配置有效期（而不是延续旧值剩余
  的有效期），可在其自身有效期内继续用于换取新的 access token 与下一次轮转

### Requirement: OAuth2 用户信息查询
`GET /api/authn/oauth/userinfo` SHALL 校验请求头 `Authorization: Bearer <access_token>`
携带的令牌存在且未过期，校验通过后 SHALL 返回该令牌绑定用户的基本身份信息；令牌缺失、
格式不正确或已过期时 SHALL 拒绝并返回 401。

响应体 SHALL 始终包含固定字段 `sub`（取用户 id，不受字段映射配置影响）；除 `sub` 外的
其余字段 SHALL 按该应用配置的用户信息字段映射动态生成（未配置任何映射时使用默认的
"用户ID + 姓名"两个字段）；若某条映射配置的应用侧字段编码恰好为 `sub`，该行配置的值
不生效，最终响应仍以协议规定的固定 `sub` 值为准。本端点由应用后端服务器直接调用，不涉及
`tab_login_log` 的账号/密码语义，令牌校验成功/失败两种结果 SHALL 各自触发一次
`sso-protocol-access-log` 能力的调用记录（事件类型 `USERINFO`）。

#### Scenario: 合法令牌查询用户信息成功
- **WHEN** 调用方携带一个有效的 access token 请求用户信息接口
- **THEN** 系统返回该令牌签发时绑定用户的基本身份信息，响应体包含固定的 `sub` 字段，记录一条成功的调用记录

#### Scenario: 令牌过期或不存在时拒绝
- **WHEN** 调用方携带的 access token 已过期或不存在
- **THEN** 系统拒绝该请求，返回 401，记录一条失败的调用记录
