## 1. 新表与基础组件

- [x] 1.1 新增 Flyway 增量文件 `V2__add_sso_protocol_log.sql`（`consolidate-flyway-migrations-v4` 基线之后的第一个增量），建表 `tab_sso_protocol_log`（design.md Decision 1 的完整列/索引定义）
- [x] 1.2 新增 `cn.nihility.rbac.ssoprotocollog` 模块：entity（`SsoProtocolLogEntity`）、mapper（`SsoProtocolLogMapper`）、常量类（事件类型 `LOGIN`/`SERVICE_VALIDATE`/`LOGOUT`/`AUTHORIZE`/`TOKEN`/`USERINFO`、结果 `SUCCESS`/`FAILED`，复用 `login-log-management` 的数值约定）。**实现调整**：协议类型未新建独立常量类，直接复用既有 `cn.nihility.rbac.app.authconfig.constant.AuthProtocol`（`CAS`/`OAUTH2`/`NONE`）——`NONE` 恰好用于全局登出接口在应用不存在/协议未配置时的兜底取值，避免重复定义
- [x] 1.3 新增 `SsoProtocolLogRecorder` 接口 + 实现，内部走 `RequestContextHolder` 解析 IP/User-Agent（design.md Decision 2，仿照 `LoginLogRecorderImpl`）

## 2. 接入 CAS 端点

- [x] 2.1 `CasController#login`：service 校验失败/最终生效权限校验失败/票据签发成功三个分支接入记录（design.md Decision 4）
- [x] 2.2 `CasController#serviceValidate`：票据校验失败（不存在/过期/已用/service 不一致/用户不存在）/成功两个分支接入记录
- [x] 2.3 `SsoLogoutExecutor#execute` 新增 `appId`/`protocol` 参数，内部在撤销会话前先 `verify` 拿 `userId`，执行完成后记录一条成功的调用记录（design.md Decision 3）；`CasController#logout` 的 service 校验失败分支单独记录失败，成功路径透传参数给 `execute`

## 3. 接入 OAuth2 端点

- [x] 3.1 `OAuthController#authorize`：redirect_uri 校验失败/response_type 不支持/最终生效权限校验失败/授权码签发成功四个分支接入记录
- [x] 3.2 `OAuthController#token`：`handleAuthorizationCodeGrant`（invalid_request/invalid_client/invalid_grant/成功）与 `handleRefreshTokenGrant`（invalid_request/invalid_grant/成功）、以及顶层 `grant_type` 不支持分支，均接入记录（事件类型统一为 `TOKEN`）
- [x] 3.3 `OAuthController#userinfo`：令牌缺失/无效（401）与成功两个分支接入记录

## 4. 接入公共登出接口

- [x] 4.1 `SsoLogoutController#logout`：service/redirect_uri 校验失败分支单独记录失败，成功路径透传参数给 `SsoLogoutExecutor#execute`（协议类型取 `AppProtocolGuard` 解析出的该应用当前实际协议类型）。**实现补充**：`AppProtocolGuard` 新增两个不抛异常的辅助方法 `resolveAppRefIdOrNull`/`tryResolveAuthConfig`，供各调用方在失败分支尽力回填 `appRefId`/实际协议类型，不因解析不到应用而中断主流程

## 5. 查询接口与日志清理接入

- [x] 5.1 新增 `SsoProtocolLogController`（`GET /api/sso-protocol-logs`）+ `SsoProtocolLogQueryService`，支持按 appRefId/protocol/eventType/result/时间范围筛选的分页查询，接口文档补 `@Tag`/`@Operation`（design.md Decision 6）
- [x] 5.2 扩展 `close-sso-log-and-policy-gaps` change 已实现的 `LogCleanupScheduler`，新增对 `tab_sso_protocol_log` 的清理（复用同一份 `LogCleanupProperties` 配置，design.md Decision 5）

## 6. 测试与验证

- [x] 6.1 扩展现有 `CasControllerTest`/`OAuthControllerTest`：每个已覆盖的成功/失败场景补充断言"对应产生了一条 `tab_sso_protocol_log` 记录，字段正确"
- [x] 6.2 新增登出场景测试：`CasController#logout`/`SsoLogoutController#logout` 的 service/redirect_uri 校验失败与成功登出均产生正确的调用记录；公共登出接口对 CAS 应用与 OAuth2 应用分别记录正确的协议类型（扩展已有 `SsoLogoutControllerTest`）
- [x] 6.3 新增 `SsoProtocolLogQueryServiceImplTest`（单元测试，mock mapper，覆盖分页 + 各筛选维度，风格对齐既有 `LoginLogQueryServiceImplTest`——该能力目前也只有服务层单元测试、没有独立的 controller 测试）
- [x] 6.4 扩展 `LogCleanupSchedulerTest`：验证 `tab_sso_protocol_log` 与另外两张表同批次清理
- [x] 6.5 `./gradlew test` 全量测试套件通过（758 个测试全部通过）

## 7.（用户反馈追加）session_id 会话关联字段——后端

- [x] 7.1 新增 Flyway 增量文件 `V3__add_login_and_sso_protocol_log_session_id.sql`：`tab_login_log`、`tab_sso_protocol_log` 各新增 `session_id VARCHAR(64) NULL` 列 + 索引（design.md Decision 6）
- [x] 7.2 新增 `cn.nihility.rbac.sso.session.SsoSessionIdHasher#hash(String)`：无盐 SHA-256 十六进制小写，`null`/空白输入返回 `null`
- [x] 7.3 `CasTicketPayload` 新增 `sessionToken` 字段，`CasTicketService#issue` 签发时写入（该方法已经接收 `sessionToken` 参数，只是目前没有存进 payload）
- [x] 7.4 `OAuthTokenPayload`/`OAuthRefreshPayload` 各新增 `sessionToken` 字段；`OAuthTokenService#writeAccessToken` 新增 `sessionToken` 入参并写入 `OAuthTokenPayload`；`issueAccessTokenWithRefresh` 把已持有的 `ssoSessionToken` 透传给 `writeAccessToken` 与新建的 `OAuthRefreshPayload`；`rotateAccessAndRefreshToken` 新增 `sessionToken` 入参（调用方从旧 `OAuthRefreshPayload#sessionToken()` 读取后传入），同样透传给新签发的 access token 与 refresh token，确保刷新后会话标识不丢失（design.md Migration Plan 步骤 5）
- [x] 7.5 `LoginLogRecorder#recordSuccess` 新增 `sessionId` 参数（`recordFailure` 不需要，失败不产生会话）；`LoginLogRecorderImpl` 落库该字段；`SsoLoginController#login` 成功分支计算 `SsoSessionIdHasher.hash(token)` 并传入
- [x] 7.6 `SsoProtocolLogRecorder` 的 `recordSuccess`/`recordFailure` 均新增 `sessionId` 参数；`SsoProtocolLogRecorderImpl` 落库该字段；`CasController#login`/`serviceValidate`、`OAuthController#authorize`/`token`（含刷新分支）/`userinfo`、`SsoLogoutExecutor#execute` 按 design.md Decision 4 末尾"session_id 取值来源"传入对应值（沿用与 `userId` 完全一致的"能拿到就填、不因失败丢弃"规则）
- [x] 7.7 `LoginLogVO`/`LoginLogQueryService` 补充返回 `sessionId` 字段；`SsoProtocolLogQueryRequest`/`SsoProtocolLogController` 补充 `sessionId` 筛选参数
- [x] 7.8 测试：`CasControllerTest`/`OAuthControllerTest`/`SsoLoginControllerTest`/`SsoLogoutControllerTest` 补充断言各分支产生的 `session_id` 符合 Decision 4 的取值规则；新增测试验证 OAuth2 令牌刷新后，用新令牌调用 userinfo 产生的记录与最初 authorize 产生的记录 `session_id` 一致
- [x] 7.9 `./gradlew test` 全量测试套件通过

## 8.（用户反馈追加）登录日志页面内嵌查看——前端

- [x] 8.1 `types/loginLog.ts` 补充 `sessionId` 字段；新增 `api/ssoProtocolLog.ts` + `types/ssoProtocolLog.ts`（按 `sessionId` 查询，含协议/事件类型中文标签映射）
- [x] 8.2 新增 `components/SsoProtocolLogDialog.vue`（弹窗，对齐 `OperationLogDetailDialog.vue` 的既有模式），`LoginLogManagementView.vue` 登录结果为成功且 `sessionId` 非空的行展示"查看SSO调用记录"入口；点击后弹窗展示该 `sessionId` 关联的分页结果（调用时间、协议、事件类型、应用标识、用户 id、结果、失败原因、客户端 IP；未展示 User-Agent 列，弹窗内容已较宽，判断该列非关键信息可省略）
- [x] 8.3 不新增菜单、不新增权限点、不改动 `权限资源.txt`（design.md Decision 7），已核实 `git status` 未改动该文件
- [x] 8.4 本地 `npm run build` 通过类型检查（独立复核一致）

## 9. 验证与文档收尾

- [ ] 9.1 人工核对：对已配置好的一个 CAS 应用与一个 OAuth2 应用分别走完整流程（登录/票据验证/登出，授权/令牌/用户信息/登出），确认 `tab_sso_protocol_log` 里出现全部预期记录且 `session_id` 正确关联；登录日志页面能通过查看入口看到这些关联记录；`tab_login_log` 行为不受影响（不重复、不遗漏）——**注**：自动化测试已覆盖全部分支的 `session_id` 取值规则（含刷新令牌链路），但真实浏览器端到端走查（含前端弹窗渲染效果）尚未执行，需要用户或后续会话在联调环境中确认
- [x] 9.2 确认根目录 `权限资源.txt` 未被改动（本次改动不涉及菜单/按钮增删）
- [ ] 9.3 实现完成后，若与 design.md 的假设有出入，更新 design.md 对应 Decision 与本 tasks.md 记录实际情况
- [ ] 9.4 归档本 change，同步 `app-sso-protocol-runtime`/`login-log-management` 主 spec 与新增的 `sso-protocol-access-log` 主 spec
