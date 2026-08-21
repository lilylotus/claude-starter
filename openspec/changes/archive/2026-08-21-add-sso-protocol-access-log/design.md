## Context

`close-sso-log-and-policy-gaps` change只覆盖了 `SsoLoginController#login`（SSO 登录页的凭证校验动作），这是`tab_login_log` 的职责范围。用户进一步要求覆盖以下 7 个路由（CAS 登出与公共登出都归为"登出"这一种事件类型）：

| 路由 | 控制器方法 | 性质 | 事件类型 |
|---|---|---|---|
| `GET /api/authn/cas/{appId}/login` | `CasController#login` | 浏览器直接访问 | `LOGIN`（CAS 服务票据签发） |
| `GET /api/authn/cas/{appId}/p3/serviceValidate` | `CasController#serviceValidate` | 应用后端服务器调用 | `SERVICE_VALIDATE` |
| `GET /api/authn/cas/{appId}/logout` | `CasController#logout` → `SsoLogoutExecutor#execute` | 浏览器直接访问 | `LOGOUT` |
| `GET /api/authn/oauth/authorize` | `OAuthController#authorize` | 浏览器直接访问 | `AUTHORIZE`（OAuth2 授权码签发） |
| `POST /api/authn/oauth/token` | `OAuthController#token`（内部 `handleAuthorizationCodeGrant`/`handleRefreshTokenGrant`） | 应用后端服务器调用 | `TOKEN` |
| `GET /api/authn/oauth/userinfo` | `OAuthController#userinfo` | 应用后端服务器调用 | `USERINFO` |
| `GET /api/authn/{appId}/logout` | `SsoLogoutController#logout` → `SsoLogoutExecutor#execute` | 浏览器直接访问 | `LOGOUT` |

用户已确认三点关键设计：① 应用后端服务器调用的三个端点（`SERVICE_VALIDATE`/`TOKEN`/`USERINFO`）不写入 `tab_login_log`，改用新表；② 浏览器直接访问的三类事件（`LOGIN`/`AUTHORIZE`/`LOGOUT`）即使复用已有 SSO 会话、未重新输入账号密码，只要签发了票据/授权码/完成了登出，也要各记一条；③ 新表与登录日志物理隔离。

## Goals / Non-Goals

**Goals:**
- 7 个路由的每一次调用（无论成功失败）都在新表里留下一条可追溯记录，字段足以回答"哪个应用、什么时候、谁、成功还是失败、失败原因是什么"。
- 不改变任何现有端点的对外响应形状、状态码、文案——纯旁路记录。
- 新表纳入已有的日志定期清理任务，不需要运维额外配置。

**Non-Goals（已随用户两轮后续反馈调整两次，见 Decision 6/7）：**
- 不新建独立的顶层菜单/管理页面（第一轮反馈里曾短暂反转过这条决定、新建过独立菜单，第二轮反馈里用户又推翻了独立菜单的方案，改为在"登录日志"页面内嵌展示，最终仍然是"不单独开一个顶层菜单"，只是查看入口变了，见 Decision 7）。
- 不记录"重定向到 SSO 登录页"这一步本身（因为这一步还没有发生任何 CAS/OAuth2 协议语义上的动作，用户还没有输入凭据，也没有签发任何票据/令牌；真正的凭证校验已经由 `tab_login_log` 覆盖，见 `close-sso-log-and-policy-gaps` change）。
- 不记录 `AppProtocolGuard`/`AppAccessAuthorizationChecker` 的中间校验细节到独立字段——校验失败时的具体原因用 `fail_reason` 文本描述即可，不做结构化拆分。
- 不引入速率限制/异常访问告警——纯记录，不做实时风控判断。

## Decisions

### 1. 新表 `tab_sso_protocol_log`，独立于 `tab_login_log`

字段设计（均可空的列对应"该场景下确实解析不到"，不是遗漏）：

```
id             BIGINT PK 自增
app_ref_id     BIGINT NULL      -- 解析出的应用 id（tab_app.id），appId/client_id 解析不到应用时为空
app_id         VARCHAR(64) NULL -- 原始 appId/client_id 参数值，即使解析不到 app_ref_id 也保留，便于排查
protocol       VARCHAR(16) NOT NULL  -- CAS / OAUTH2
event_type     VARCHAR(20) NOT NULL  -- LOGIN / SERVICE_VALIDATE / LOGOUT / AUTHORIZE / TOKEN / USERINFO
user_id        BIGINT NULL      -- 能解析到用户时填充
session_id     VARCHAR(64) NULL -- 本次调用所属 SSO 会话的标识（SSO 会话令牌的 SHA-256 摘要，见 Decision 6），处理链路尚未解析出会话时为空
result         TINYINT NOT NULL -- 1=成功，2=失败（复用 login-log-management 的 LoginResult 数值约定）
fail_reason    VARCHAR(255) NULL
client_ip      VARCHAR(64) NULL
user_agent     VARCHAR(512) NULL
create_by/create_time/update_by/update_time  -- 标准审计字段，本表只追加不更新，update_* 恒等于 create_*
```

索引：`(app_ref_id, create_time)`（按应用查最近调用）、`(event_type, create_time)`（按事件类型查）、`(user_id)`（按用户查）、`(session_id)`（按会话查，供"登录日志"页面按 `session_id` 拉取本次登录之后的协议活动，见 Decision 7）。列名逐一核对 MySQL/PostgreSQL/Oracle/SQL Server 保留字，均非保留字。

### 2. `SsoProtocolLogRecorder` 内部走 `RequestContextHolder`，不改动无 `HttpServletRequest` 参数的控制器方法签名

与 `LoginLogRecorderImpl` 同构：接口只需要业务语义参数（`protocol`/`eventType`/`appId`/`appRefId`/`userId`/`success`/`failReason`），IP/User-Agent 由实现内部通过 `RequestContextHolder.getRequestAttributes()` 解析，复用 `ClientRequestUtils.resolveClientIp`。这样 `OAuthController#token`/`#userinfo`（当前方法签名没有 `HttpServletRequest` 参数）不需要新增该参数就能记录。

### 3. `SsoLogoutExecutor#execute` 新增 `appId`（原始标识）与 `protocol` 两个参数，登出记录收在这里而不是分散在两个调用方各写一遍

`CasController#logout` 与 `SsoLogoutController#logout` 都是"校验 service/redirect_uri → 调 `SsoLogoutExecutor#execute`"，登出记录的公共部分（撤销会话前先 `verify` 拿到 userId、执行成功后记录）放在 `SsoLogoutExecutor` 内部只写一次，避免两个调用方重复代码。`CasController` 传入固定的 `protocol=CAS`；`SsoLogoutController` 按 `AppProtocolGuard` 解析出的该应用当前实际协议类型（CAS 或 OAUTH2）传入——因为公共登出接口本身就是"不依赖 CAS 票据流程，按应用当前协议类型校验"，事件记录也应该反映真实协议，而不是笼统写"未知"。

`SsoLogoutExecutor#execute` 内部在撤销会话（`ssoSessionService.revoke`）之前先 `ssoSessionService.verify(sessionToken)` 取出 `userId`（取不到时为 `null`，对应"未持有会话时登出接口仍应正常...不报错（幂等）"场景），登出流程本身没有失败分支（`service`/`redirect_uri` 校验失败发生在调用 `execute` 之前，由各自调用方在校验失败处直接记录 `FAILED` 后 `return`，不进入 `execute`），所以 `execute` 内部记录的登出事件恒为 `SUCCESS`。

### 4. 各端点/分支的记录点与 `result`/`fail_reason` 映射

**通用原则（补充）**：`user_id` SHALL 尽量填充——只要本次请求处理链路里已经解析出了用户 id（无论来自
`ssoSessionService.verify` 的会话校验结果，还是 CAS 票据/OAuth2 授权码/AccessToken/RefreshToken
payload 里携带的 `userId()`），即使当前分支最终判定为失败（如失败发生在拿到 userId 之后的下一步校验），
记录时也 SHALL 使用这个已经拿到的 userId，不因为本次调用失败就丢弃已经掌握的信息、留空
`user_id`。只有在处理链路走到"拿到 userId"这一步之前就已经失败的分支（如 `service`/
`redirect_uri` 白名单校验、票据/授权码/refresh_token 本身不存在或已失效导致连 payload 都拿不到）
才允许 `user_id` 为空——这些分支客观上确实无法得知是谁在调用。下面每个分支末尾用【userId：有/无】
标注该分支实际能否拿到 userId。

- **CAS `login`**：`service` 未匹配白名单 → `FAILED`，`fail_reason="service 未匹配任何回跳地址匹配规则"`【userId：无，此时还没查会话】；未登录重定向到 SSO 登录页 → **不记录**（Non-Goal 2）；最终生效权限校验未通过（含请求控制不满足）→ `FAILED`，`fail_reason` 取 `SsoProtocolException#getMessage()`（如"当前用户无权访问该应用"）【userId：有，取自已经 `verify` 成功的 `userIdOpt.get()`，即使本次因未授权而失败也要带上，方便追溯"是谁在什么时候被拒绝访问了哪个应用"】；票据签发成功 → `SUCCESS`【userId：有】。
- **CAS `serviceValidate`**：票据不存在/已过期/已使用/`service` 不一致 → `FAILED`，`fail_reason="Ticket 不存在、已过期或已被使用"`【userId：无，`casTicketService.consume(ticket)` 返回空，压根没有 payload 可取】；票据消费成功但绑定的用户已不存在（`userMapper.selectById` 返回 `null`）→ `FAILED`，`fail_reason="用户不存在"`【userId：有，取自已经拿到的 `payload.userId()`——`payload` 在这一步已经成功解析，只是对应的 `UserEntity` 查不到了，不代表 userId 本身未知】；成功 → `SUCCESS`，`user_id` 取自 `CasTicketPayload#userId()`。
- **CAS `logout`/公共 `logout`**：`service`/`redirect_uri` 未匹配 → `FAILED`（在各自 controller 里、调用 `SsoLogoutExecutor#execute` 之前记录）【userId：无，还没查会话】；其余情况见 Decision 3，恒为 `SUCCESS`，`user_id` 取自撤销前 `verify` 的结果（无会话时为 `null`）。
- **OAuth2 `authorize`**：`redirect_uri` 未匹配 → `FAILED`【userId：无】；`response_type` 非 `code` → `FAILED`，`fail_reason="response_type 不支持"`【userId：无，这一步同样在查会话之前】；未登录重定向 → 不记录；最终生效权限校验未通过 → `FAILED`【userId：有，同 CAS `login`】；授权码签发成功 → `SUCCESS`【userId：有】。
- **OAuth2 `token`**：`grant_type` 不支持 → `FAILED`，`fail_reason="grant_type 不支持"`【userId：无】；`authorization_code` 分支：`invalid_request`（参数缺失）→ `FAILED`【userId：无，还没到解析授权码这一步】；`invalid_client`（client_id/client_secret 不匹配）→ `FAILED`【userId：无，授权码还没被消费，不知道绑定谁】；`invalid_grant`（`consumeCode` 返回空，或返回了 payload 但 `clientId`/`redirectUri` 与请求不一致）→ `FAILED`【userId：**视情况而定**——`consumeCode` 返回空时无 payload、userId 为空；返回了 payload 但 client_id/redirect_uri 校验不通过时，`payloadOpt.get().userId()` 是已知的，SHALL 一并记录，不要因为整体判定失败就丢弃】；成功 → `SUCCESS`，`user_id` 取自 `OAuthCodePayload#userId()`；`refresh_token` 分支：参数缺失 → `FAILED`【userId：无】；`verifyRefreshToken` 返回空 → `FAILED`【userId：无，同样没有 payload】；成功 → `SUCCESS`，`user_id` 取自 `OAuthRefreshPayload#userId()`。
- **OAuth2 `userinfo`**：`Authorization` 头缺失/`token` 为空 → `FAILED`，`fail_reason="access_token 无效或缺失"`【userId：无】；`verifyAccessToken` 返回空（令牌不存在/已过期）→ `FAILED`，同一 `fail_reason`【userId：无，没有 payload】；令牌校验通过 → `SUCCESS`，`user_id` 取自 `OAuthTokenPayload#userId()`——注意现有代码这一步之后即使 `userMapper.selectById(userId)` 查不到用户实体，接口仍然正常返回 `sub` 字段（design.md 未改变这个行为），日志记录同样按 `SUCCESS` 处理、`user_id` 用 payload 里已经拿到的值，不受实体查询结果影响。

`session_id`（Decision 6/7 新增）SHALL 沿用与 `user_id` 完全一致的"能拿到就填、不因失败丢弃"原则，取值来源见 Decision 7 的 payload 改造：CAS `login`/OAuth2 `authorize`/CAS 与公共 `logout` 直接来自本次请求 Cookie 里的 SSO 会话令牌（`SsoSessionCookieUtils.extractSessionToken`，经 `ssoSessionService.verify` 确认存在后才记录，未持有/未验证通过的分支为空）；CAS `serviceValidate` 来自 `CasTicketPayload#sessionToken()`；OAuth2 `token`（含刷新）来自 `OAuthCodePayload#ssoSessionToken()`/`OAuthRefreshPayload#sessionToken()`；OAuth2 `userinfo` 来自 `OAuthTokenPayload#sessionToken()`。

### 5. 新表纳入既有的日志清理任务

`close-sso-log-and-policy-gaps` change 已实现的 `LogCleanupScheduler`/`LogCleanupProperties`（`rbac.log-cleanup`，默认每天凌晨 1 点、保留 180 天）直接扩展第三个清理目标 `tab_sso_protocol_log`，共用同一份 cron/保留天数配置，不单独开关。

### 6. 新增 `session_id` 字段，贯穿 `tab_login_log` 与 `tab_sso_protocol_log`，取 SSO 会话令牌的哈希值而不是原始令牌

第一轮反馈里曾短暂决定"新建独立菜单+权限点直接暴露这张表"，第二轮反馈推翻了这个方案：用户希望"登录"和"登录之后这个会话里发生的 CAS/OAuth2 协议动作"体现为同一条主线上的父子关系，而不是两个互不关联、需要分别打开查看的独立页面/菜单——即在"登录日志"页面里，针对某一次成功的 SSO 登录，能直接看到这次登录之后该会话产生的全部 CAS/OAuth2 协议调用。要做到这一点，`tab_login_log`（登录这一步）与 `tab_sso_protocol_log`（后续协议调用）必须有一个共同的关联键。

- `tab_login_log` 新增 `session_id` 列：仅在 SSO 登录成功（`SsoLoginController#login` 的 `recordSuccess` 分支）时填充，取 `ssoSessionService.issue(...)` 返回的会话令牌的哈希值；登录失败、或走管理端口令登录（`AuthServiceImpl#login`，不产生 SSO 会话）时为空。
- `tab_sso_protocol_log` 新增 `session_id` 列，取值规则见 Decision 4 末尾补充的"session_id 取值来源"。
- **不落存原始 SSO 会话令牌，落存其 SHA-256 摘要**：SSO 会话令牌本质是一个持有期内可直接冒充该用户完成 SSO 免密登录的 bearer 凭据（`SsoSessionCookieUtils`/`ssoSessionService.verify` 只看这个令牌本身，不做二次身份校验）。这两张日志表都通过只读查询接口暴露给管理员查看，如果直接落存明文令牌，等于把一个仍在有效期内的可用凭据摆在了日志页面上，任何能看到登录日志的管理员（或该查询接口一旦被越权访问）都能拿这个值在有效期内冒充该用户完成 SSO 登录——这是不必要地扩大凭据暴露面。摘要值不可逆，但作为"同一个原始令牌产生相同摘要"的关联键完全够用，不影响关联查询的正确性。新增一个无盐的确定性哈希工具方法（`cn.nihility.rbac.sso.session.SsoSessionIdHasher#hash(String sessionToken)`，SHA-256 十六进制小写，`null`/空输入返回 `null`）——不能像 `PasswordDigestUtils` 那样加随机盐，因为加盐后同一个令牌每次哈希结果都不同，会话/日志关联查询就废了；令牌本身是高熵随机值（32 位随机十六进制或等价长度），不加盐的 SHA-256 摘要已经足够抵御彩虹表反查。

### 7. 前端在"登录日志"页面内嵌展示，不新建独立菜单/页面

延续 Decision 6 的关联键设计，前端不新增顶层菜单，而是在既有 `LoginLogManagementView.vue`（`/log/login-logs`）里补一个查看入口：

- 后端 `LoginLogVO`/`LoginLogQueryService` 补充返回 `sessionId` 字段（管理员不需要看到这个哈希值本身，前端只用它作为查询参数，不在表格列里展示）。
- `SsoProtocolLogController`/`SsoProtocolLogQueryRequest` 补充 `sessionId` 筛选参数（精确匹配）。
- 登录日志列表每一行，`loginResult=成功` 且 `sessionId` 非空（即通过 SSO 登录页登录成功产生的记录，区别于管理端口令登录）时，展示一个"查看 CAS/OAuth2 操作"入口（按钮或链接）；点击后以弹窗/抽屉展示 `GET /api/sso-protocol-logs?sessionId={sessionId}` 的分页结果（复用查询接口既有的分页/排序），列出该次登录之后，这个 SSO 会话触发的全部 CAS/OAuth2 协议调用（时间、协议、事件类型、应用标识、结果、失败原因等）。管理端口令登录的行、或 SSO 登录失败的行不展示这个入口（没有 `sessionId` 可关联）。
- 不新增菜单、不新增权限点、不改动 `权限资源.txt`——复用登录日志页面已有的 `LoginLogManagement:loginLog:view` 权限点，查看入口只是该页面内的一个交互元素，不是独立的可路由页面。

## Risks / Trade-offs

- [风险] 6 个端点近 15 处调用点分散在 3 个类里，容易遗漏个别分支 → **缓解**：design.md Decision 4 已经逐分支列出映射表，实现时按清单逐条核对；补充覆盖每个分支的测试。
- [风险] `SsoLogoutExecutor#execute` 签名变更（新增参数）会影响其全部现有调用方 → **缓解**：只有 `CasController#logout`、`SsoLogoutController#logout` 两个调用方，改动范围可控，实现时一并更新。
- [风险] 新表可能因为高频的 `serviceValidate`/`token`/`userinfo`（应用后端轮询式调用）迅速增长 → **缓解**：已纳入日志清理任务（Decision 5），与登录日志、操作日志同等对待。

## Migration Plan

1. 新增 Flyway 增量文件 `V2__add_sso_protocol_log.sql`（在 `consolidate-flyway-migrations-v4` 基线之后追加，这是该基线成型后的第一个增量，不修改 `V1__init_schema.sql` 本身），已完成。
2. 按 Decision 4 的映射表逐个端点接入 `SsoProtocolLogRecorder`，已完成。
3. 扩展 `LogCleanupScheduler` 覆盖新表，已完成。
4. 新增 Flyway 增量文件 `V3__add_login_and_sso_protocol_log_session_id.sql`：`tab_login_log`/`tab_sso_protocol_log` 各新增 `session_id` 列 + 索引（Decision 6）。
5. 新增 `SsoSessionIdHasher` 工具类；`CasTicketPayload`/`OAuthTokenPayload`/`OAuthRefreshPayload` 三个 record 各新增 `sessionToken`（或对应命名）字段，在各自的签发点（`CasTicketService#issue`、`OAuthTokenService#writeAccessToken`/`issueAccessTokenWithRefresh`、`rotateAccessAndRefreshToken`）写入，`rotateAccessAndRefreshToken` 需要新增一个 `sessionToken` 入参（从调用方已持有的 `OAuthRefreshPayload#sessionToken()` 传入），使刷新之后的新 access/refresh token 依然携带同一个会话标识。
6. `SsoLoginController#login` 成功分支、`CasController#login`/`serviceValidate`、`OAuthController#authorize`/`token`/`userinfo`、`SsoLogoutExecutor#execute` 按 Decision 4 末尾补充的取值规则填充 `session_id`（`LoginLogRecorder#recordSuccess` 与 `SsoProtocolLogRecorder` 的方法签名均需新增 `sessionId` 参数）。
7. `LoginLogVO`/`LoginLogQueryService`、`SsoProtocolLogQueryRequest`/`SsoProtocolLogController` 按 Decision 7 补充 `sessionId` 字段/筛选参数。
8. 前端 `LoginLogManagementView.vue` 按 Decision 7 补充查看入口与弹窗/抽屉展示。
9. `./gradlew test` 全量通过，人工核对：SSO 登录成功后，登录日志该条记录能关联查到本次会话后续全部 CAS/OAuth2 协议调用记录；管理端口令登录、SSO 登录失败的记录不展示查看入口；刷新 OAuth2 令牌后新签发的 access/refresh token 产生的后续调用记录仍能关联回同一个 `session_id`。

## Open Questions

（无——用户已就三个关键分歧点给出明确答案，见 Context）
