# sso-protocol-access-log Specification

## Purpose

CAS/OAuth2.0 协议运行时端点的调用记录能力：为 `app-sso-protocol-runtime` 能力的全部 6 类
运行时端点（CAS 单点登录、CAS 票据验证、CAS 单点登出、OAuth2 授权、OAuth2 令牌签发/刷新、
OAuth2 用户信息查询）以及全局单点登出接口的每一次调用（无论成功或失败）留痕，供问题排查
（如某个应用的用户反馈"登录不了"时定位是哪一步、因为什么原因失败）。与 `login-log-management`
能力的登录尝试记录物理隔离——后者只对应"账号密码校验"这一个动作本身，本能力覆盖的是
凭证校验通过、SSO 会话建立之后，该会话驱动的全部协议层活动（票据签发/验证、令牌签发/刷新/
校验、登出等），二者通过 SSO 会话标识（`sessionId`）关联，供登录日志页面查看某次登录之后
关联的协议调用记录。

## Requirements

### Requirement: SSO 协议调用记录
系统 SHALL 为 CAS/OAuth2.0 协议的全部 6 类运行时端点（CAS 单点登录、CAS 票据验证、CAS
单点登出、OAuth2 授权、OAuth2 令牌签发/刷新、OAuth2 用户信息查询）以及全局单点登出接口的
每一次调用（无论成功或失败）写入一条 `tab_sso_protocol_log` 记录，与 `login-log-management`
能力的 `tab_login_log` 物理隔离，互不影响、不共用数据表。每条记录 SHALL 包含：协议类型
（CAS/OAuth2.0）、事件类型（`LOGIN`/`SERVICE_VALIDATE`/`LOGOUT`/`AUTHORIZE`/`TOKEN`/
`USERINFO`）、应用标识（能解析到具体应用时含内部应用 id，解析不到时仅保留原始 appId/
client_id 文本）、关联用户 id、关联的 SSO 会话标识、调用结果（成功/失败）、失败原因
（仅失败时填充）、客户端 IP、User-Agent、调用发生时间。关联用户 id SHALL 尽量填充：只要本次
调用的处理链路里已经解析出了用户 id（无论来自 SSO 会话校验，还是 CAS 票据/OAuth2 授权码/
AccessToken/RefreshToken 携带的用户标识），即使该分支最终判定为失败（失败发生在拿到用户 id 之后
的后续校验步骤），记录时也 SHALL 使用这个已经解析到的用户 id，不因调用失败就丢弃已经掌握的身份
信息；只有处理链路在拿到用户 id 之前就已经失败的分支（如白名单校验、票据/授权码/令牌本身不存在
或已失效导致连身份信息都拿不到）才允许该字段为空。SSO 会话标识 SHALL 采用与用户 id 相同的"能拿到
就填、不因失败丢弃"原则，取值为该次调用所属 SSO 会话令牌的 SHA-256 摘要（不落存原始令牌，避免
在只读查询接口里暴露一个仍在有效期内、可直接冒充该用户完成 SSO 登录的 bearer 凭据）；OAuth2
access token/refresh token 每次轮转刷新后，新签发的令牌 SHALL 延续同一个 SSO 会话标识，不因刷新
而产生新的会话标识、导致关联链路断裂。浏览器直接访问的三类事件（`LOGIN`/`AUTHORIZE`/`LOGOUT`）
即使复用此前已建立的 SSO 会话、本次未重新输入账号密码，只要签发了票据/授权码、或完成了登出，也
SHALL 各记一条，不因为凭证未被重新校验而跳过；重定向到 SSO 登录页这一步本身（尚未发生任何协议
语义上的动作）SHALL NOT 触发记录。

#### Scenario: 浏览器复用已有会话签发票据仍记录
- **WHEN** 浏览器已持有有效 SSO 会话，直接访问 CAS 单点登录接口并成功签发服务票据（本次未重新输入账号密码）
- **THEN** 系统记录一条成功的调用记录（事件类型 `LOGIN`），包含签发票据的目标应用与用户 id

#### Scenario: 应用后端服务器调用的接口同样记录成功与失败
- **WHEN** 某应用的后端服务器调用 CAS 票据验证接口，先后使用一个合法票据（成功）与一个已被消费过的票据（失败）各请求一次
- **THEN** 系统各记录一条对应结果的调用记录，失败的一条包含具体失败原因（票据不存在/已过期/已被使用），且该条因为票据本身已失效、无法解析出任何用户标识，用户 id 为空

#### Scenario: 身份已解析但后续判定失败时仍记录用户 id
- **WHEN** 浏览器持有有效 SSO 会话访问 CAS 单点登录接口，`service` 校验通过、会话对应的用户 id 已解析出来，但该用户不具备访问目标应用的最终生效授权，签发票据被拒绝
- **THEN** 系统记录一条失败的调用记录，用户 id 字段填充为该已解析出的用户 id，而不是留空——即使本次调用最终失败，已经掌握的身份信息也不应丢弃

#### Scenario: 票据/令牌绑定的用户实体查不到时仍记录票据携带的用户 id
- **WHEN** 某票据/令牌校验本身通过（票据/令牌未过期、未被消费），但其绑定的用户 id 在 `tab_user` 中已查不到对应记录（如账号被物理删除）
- **THEN** 系统记录该条调用记录时，用户 id 使用票据/令牌 payload 里携带的值，不因为用户实体查询失败就置空

#### Scenario: 未能解析到应用时仍保留原始标识
- **WHEN** 某次调用携带的 appId/client_id 在系统中不存在对应应用
- **THEN** 系统仍记录一条失败的调用记录，应用内部 id 为空，但保留原始 appId/client_id 文本供排查

#### Scenario: 令牌刷新后新令牌延续同一会话标识
- **WHEN** 某 OAuth2 应用使用 `grant_type=refresh_token` 刷新令牌，成功签发新的 access token 与 refresh token
- **THEN** 用新签发的 access token 调用用户信息接口时，产生的调用记录与本次登录最初签发授权码时的调用记录具有相同的 SSO 会话标识

### Requirement: SSO 协议调用记录分页查询
系统 SHALL 提供 SSO 协议调用记录的只读分页查询接口，支持按应用、协议类型、事件类型、调用结果、
SSO 会话标识、调用时间范围筛选，均为可选参数；分页参数 `page`（默认 `1`）、`pageSize`（默认 `10`）
均为可选；结果按调用发生时间降序排列。该接口不提供新增、编辑、删除能力。按 SSO 会话标识筛选主要
供"登录日志"页面查看某次成功 SSO 登录之后关联的协议调用记录使用（见 `login-log-management` 能力
"登录日志页面查看关联的 SSO 协议调用记录"需求）。

#### Scenario: 查询调用记录分页列表
- **WHEN** 客户端调用 `GET /api/sso-protocol-logs?page={page}&pageSize={pageSize}`
- **THEN** 系统返回 SSO 协议调用记录的分页结果，按调用发生时间降序排列

#### Scenario: 按应用与事件类型筛选
- **WHEN** 客户端调用 `GET /api/sso-protocol-logs?appRefId={appRefId}&eventType={eventType}`
- **THEN** 系统返回指定应用、指定事件类型的调用记录分页结果

#### Scenario: 按 SSO 会话标识查询某次登录之后的全部协议调用
- **WHEN** 客户端调用 `GET /api/sso-protocol-logs?sessionId={sessionId}`，`sessionId` 取自某条登录日志记录
- **THEN** 系统返回该 SSO 会话产生的全部 CAS/OAuth2 协议调用记录（成功+失败），按调用发生时间排列

### Requirement: SSO 协议调用记录定期清理
`tab_sso_protocol_log` SHALL 纳入系统既有的日志定期清理任务范围（`login-log-management`
能力"登录日志定期清理"需求引入的同一个定时任务与配置），与登录日志、操作日志共用同一份
cron 表达式与保留天数配置，不单独提供开关或配置项。

#### Scenario: 与登录日志、操作日志同批次清理
- **WHEN** 日志定期清理任务按配置的 cron 表达式触发
- **THEN** 系统在同一次任务执行中，分别清理 `tab_login_log`、`tab_operation_log`、
  `tab_sso_protocol_log` 三张表中创建时间早于保留期的记录
