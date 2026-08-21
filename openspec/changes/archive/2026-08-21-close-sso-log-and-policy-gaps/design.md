## Context

四个独立缺口的修复打包为一个 change，因为都是"已上线能力的行为补齐"，不引入新表、不改变对外协议形状。经过对代码库的调研（三路并行研究，均为只读调查，未改代码），关键事实：

- **登录日志**：`cn.nihility.rbac.loginlog.service.LoginLogRecorder`（`recordSuccess(loginAccount, userId, userName)` / `recordFailure(loginAccount, userId, userName, failReason)`，IP/UA 等字段由实现内部从 `RequestContextHolder` 取，调用方不用传）目前只被 `cn.nihility.rbac.auth.service.impl.AuthServiceImpl#login` 调用。CAS（`cn.nihility.rbac.sso.cas.controller.CasController`）与 OAuth2（`cn.nihility.rbac.sso.oauth.controller.OAuthController`）都只做 `ssoSessionService.verify(...)`，从不重新校验密码；真正做密码校验、也是唯一需要接入登录日志的地方是 `cn.nihility.rbac.sso.controller.SsoLoginController#login`（`/api/authn/sso/login`）——CAS/OAuth2 未持有有效会话时统一重定向到这个端点。该方法目前把"账号不存在"与"密码不正确"合并成同一个通用异常，要按六类场景分别记录失败原因，需要仿照 `AuthServiceImpl#login` 的分支结构重写，复用 `cn.nihility.rbac.loginlog.constant.LoginFailReason` 常量。
- **策略自动重算**：`cn.nihility.rbac.appaccess.policy.service.PolicyExecutionService#execute(Long policyId)`（impl 同包 `impl.PolicyExecutionServiceImpl`）是全量重建（先删后插），可安全重复调用。组织/用户/任职的增改启停删已经统一发布到 `cn.nihility.rbac.sync.event.DomainChangeEvent`，由 `cn.nihility.rbac.sync.event.support.DomainChangeEventHandler`（LMAX Disruptor 单消费者）转发给 `DomainChangeEventProcessor#process`，目前该方法只处理应用同步通知这一种下游副作用，每个副作用各自 try/catch 互不影响——新增策略重算副作用直接复用这个既有管道，不需要新增 Disruptor handler。
- **日志清理**：项目里已有 `@EnableScheduling`（`RbacApplication`），但目前只有 `fixedRate`/`fixedDelay` 轮询式任务（如 `UpstreamSyncScheduler`），没有 cron 表达式任务，也没有分布式锁工具（`RedisUtils` 无 `tryLock`），现有定时任务均假设单实例部署——本次不引入锁，与现状保持一致。
- **策略校验放宽**：`cn.nihility.rbac.appaccess.policy.service.impl.PolicyServiceImpl#assertScopeAndAttrNotBothEmpty`（`create`/`update` 均调用）是当前"两者不能同时为空"校验的唯一位置，`assertTargetAppsNotEmpty` 是风格上应当模仿的相邻校验方法。但研究同时发现：即使放宽校验，`PolicyExecutionServiceImpl#execute` 在两者都为空时目前的交集计算会产出空集（`intersect(null, null)` 为空），`AppAccessEffectivePermissionServiceImpl` 的候选策略来源又完全依赖 `tab_app_access_policy_grant` 里已有的授权记录——所以只放宽保存校验、不改 `execute()` 的匹配语义，会做出一个"能保存但永远不生效"的策略，不满足用户诉求。用户已确认接受反转 `app-access-authorization` 归档 design.md Decision 1（"两者不能同时为空，避免退化为对全公司授权的歧义"），因此本次 `execute()` 的匹配语义也要同步调整。

## Goals / Non-Goals

**Goals:**
- CAS/OAuth2 登录路径的登录日志覆盖率与管理端口令登录一致（六类场景）。
- 组织/用户/任职变更后，受影响的启用中策略在合理短的时间内（异步、不阻塞原写请求）自动收敛到正确的授权状态，不需要管理员手动介入。
- 登录日志、操作日志有默认的定期清理机制，防止无限增长，同时保持可配置。
- "仅配置请求控制"的策略是一个货真价实、能生效的合法配置，不是一个能保存但永远不生效的死配置。

**Non-Goals:**
- 不新增/修改任何数据库表结构（不涉及 Flyway 迁移）。
- 不处理 `tab_upstream_sync_record`/`tab_app_notify_record`/`tab_app_pull_record` 的清理（用户已明确本次范围仅限登录日志+操作日志）。
- 不引入分布式锁/多实例调度协调（现状本就是单实例假设，本次不改变这个假设）。
- 不改变 CAS/OAuth2 协议本身的对外接口形状、错误提示文案、状态码。
- 不改变人工例外（`tab_app_access_manual_override`）的任何行为。

## Decisions

### 1. SSO 登录日志接入点唯一收敛在 `SsoLoginController#login`

CAS/OAuth2 控制器本身不做密码校验，只在没有有效 SSO 会话时重定向到这一个端点；一次会话建立后签发给多个应用的票据/令牌不构成新的登录尝试，不重复记录。这样只需要改一个方法，不需要在 `CasController`/`OAuthController` 里各开一处。

**实现要点**：把 `SsoLoginController#login` 现有的"解密失败/账号不存在/账号停用/账号删除/密码不匹配"合并异常路径拆开，比照 `AuthServiceImpl#login` 的分支粒度分别调用 `LoginLogRecorder#recordFailure`（复用 `LoginFailReason` 常量）；成功路径在 `ssoSessionService.issue(...)` 之前或之后调用 `recordSuccess`。对外返回的异常/错误提示文案保持不变（仍是统一的"账号或密码不正确"），只是内部多了日志记录分支，不额外泄露信息。

### 2. 策略自动重算挂在 `DomainChangeEventProcessor`，广范围触发（ORG/USER/POSITION 任意操作类型）

复用现有事件总线而不是在 Org/User/Position 的 Service 层各自显式调用策略执行，理由：① 已有的 try/catch-per-effect 模式天然满足"单条策略失败不影响其余"和"不阻塞原始写请求"（事件是异步转发的）；② 不需要在业务 Service 里侵入式地感知"应用访问授权"这个下游模块，保持模块边界清晰（业务 Service 只管发布领域事件）。

触发范围采用用户确认的广范围方案：ORG/USER/POSITION 三种 `dataType`、任意 `operationType`（CREATE/UPDATE/ENABLE/DISABLE/DELETE）都触发全部启用中策略的重新执行。因为 `execute()` 本身是全量重建、幂等、无副作用，重复调用的唯一成本是数据库查询与 DELETE+INSERT 的开销，换来的是覆盖"编辑用户属性导致重新匹配""调整任职把用户挪到新组织"等场景，不需要为每种场景单独判断是否要触发。

**实现要点**：在 `DomainChangeEventProcessor#process` 内新增一段独立的 try/catch 逻辑（不影响现有的通知候选解析逻辑）：`dataType` 属于 `{ORG, USER, POSITION}` 时，查询全部 `status=启用` 的策略 id 列表，逐条调用 `PolicyExecutionService#execute(policyId)`，单条抛异常仅记录日志（`log.error` 或等价）并继续处理下一条，不重新抛出。

### 3. `PolicyServiceImpl` 校验放宽为"三者不能同时为空"，`PolicyExecutionServiceImpl` 补一个"两者皆空→全部启用用户"分支

`assertScopeAndAttrNotBothEmpty` 改造为接收 `browserRules`/`ipRules`（或直接接收整个 request 对象），仅当组织范围、用户属性条件、浏览器白名单、IP 白名单四者全部为空时才抛出校验异常；异常提示文案同步更新为"组织范围、用户属性条件、请求控制条件不能同时为空，请至少配置一类"。

`PolicyExecutionServiceImpl#execute` 现有的"集合 A ∩ 集合 B，只配置一类时取该类结果"逻辑，补一个第三分支：org scope 与 user attr 都未配置时，命中集合为 `tab_user` 中 `status=2000`（启用）的全部用户 id（不要求存在任职记录——因为此时策略本就没有组织维度的圈定意图，纯粹依赖请求控制收窄，比"只圈定在职用户"更符合"完全不做身份限定"的语义；这一点与组织范围匹配路径要求"存在未删除任职记录"不同，是有意的不一致，因为触发条件不同：一个是"属于某组织"，一个是"不限定任何身份维度"）。

### 4. 日志清理任务：单个 `@Scheduled(cron=...)` 组件，清两张表，配置走新的 `@ConfigurationProperties`

新增一个 `@ConfigurationProperties(prefix = "rbac.log-cleanup")` 配置类（字段：`cron` 默认 `"0 0 1 * * ?"`，`retentionDays` 默认 `180`），一个 `@Scheduled(cron = "#{logCleanupProperties.cron}")`（或用 `@Scheduled(cron="${rbac.log-cleanup.cron:0 0 1 * * ?}")` 直接读配置，二选一，实现时按项目里 `RbacSsoProperties` 的既有写法保持一致）的任务组件，任务体内分别对 `tab_login_log`/`tab_operation_log` 执行 `DELETE WHERE create_time < :cutoff`（MyBatis-Plus `LambdaQueryWrapper` 单表删除即可，不需要 XML——不是多表 JOIN）。两张表共用同一份 cron/保留天数配置，不做到"每张表可以配不同保留期"这种灵活度（用户没有提出这个需求，YAGNI）。

失败处理：单张表删除失败不影响另一张表（各自 try/catch），失败记录到应用日志，不重新抛出（定时任务框架对未捕获异常的处理不应该被依赖）。

### 5. 不引入分布式锁

现有定时任务（`UpstreamSyncScheduler` 等）均未使用锁，`RedisUtils` 也没有现成的锁方法。日志清理是幂等操作（重复执行只是多做几次没有行可删的 DELETE，没有副作用），即使未来多实例部署导致同一时刻多个实例都触发清理，也不会产生错误结果，只是有一点重复开销。保持与现状一致，不额外引入复杂度。

### 6. 修复：策略自动重新执行必须显式传入执行人，不能依赖 `CurrentOperatorService`

实现完成、跑通全量测试后，用户报告"新增用户同时关联任职，没有自动添加到应用访问授权策略中"——排查发现 Decision 2 里新增的 `DomainChangeEventProcessor#reExecutePoliciesIfNeeded` 调用的是 `PolicyExecutionService#execute(Long)`，该方法内部通过 `CurrentOperatorService#resolveUserId()` 解析当前登录用户来填充 `create_by`/`update_by`；而这个新分支运行在 Disruptor 消费者线程上，从不处于任何 HTTP 请求上下文中，`resolveUserId()`（按 `backend-common-utilities` spec"统一解析当前登录操作人账号编码"需求的既定约定）必然抛出 `IllegalStateException`，并被本方法自身"单条策略失败仅记录日志、不中断其余策略"的 try/catch 悄悄吞掉——导致自动重新执行**表面接入成功、实际每次调用都失败**，策略授权记录从未被自动更新过。这个 bug 在自动化测试里没暴露，是因为集成测试为了让被测方法本身能跑通，在 `@BeforeEach` 里手动 `CurrentUserContext.setUserId(1L)` 模拟了一个登录态——这掩盖了"真实 Disruptor 线程上没有这个登录态"的事实。

**修复**：给 `PolicyExecutionService` 新增一个重载 `execute(Long policyId, String operator)`，执行人由调用方显式传入，不再内部解析；原 `execute(Long policyId)` 改为委托给新重载，`operator` 参数用 `CurrentOperatorService#resolveUserId()` 解析（管理员点击"执行"按钮的 HTTP 路径继续保持原有的"取不到登录态就抛异常"的强约束，不受影响）。`DomainChangeEventProcessor#reExecutePoliciesIfNeeded` 改为调用新重载，`operator` 直接取 `DomainChangeEvent#getOperator()`——即触发本次组织/用户/任职变更的原始操作人，语义上比"随便指定一个占位符"更准确（审计上能看出这条策略授权是因为谁的操作被自动带出来的），且允许为 `null`（`tab_app_access_policy_grant`/`tab_app_access_policy` 的相关审计列本就可空），不会像 `CurrentOperatorService` 那样在拿不到值时抛异常——因为这个场景本就不存在"应该有登录态但没取到"的编程错误语义，单纯是"这条自动化路径不是由某个当下的 HTTP 会话触发的"。

同步更新了 `DomainChangeEventProcessorTest` 里对 `policyExecutionService.execute(...)` 的 mock 校验，从单参数改为双参数（`execute(10L, event.getOperator())`），使其能真正校验到"传入的是事件携带的操作人"，而不是像修复前那样即使参数不对也测不出来。

## Risks / Trade-offs

- [风险] 策略自动重算是"任意 ORG/USER/POSITION 事件都全量重跑全部启用策略"，如果策略数量或用户规模变大，可能有性能/事件处理延迟的隐忧 → **缓解**：`app-access-authorization` 设计假设本身就是"策略结果是有限集合"（见归档 design.md），且是异步处理、不阻塞原始请求；如果未来规模增长到需要优化，届时可以在 `DomainChangeEventProcessor` 里加节流/去重（如短时间内多个事件合并成一次重算），本次不做过度设计。
- [风险] "仅请求控制"策略执行后会把所有启用用户都写入 `tab_app_access_policy_grant`，当启用用户数很大时这条策略产生的授权记录行数会明显多于其他策略 → **缓解**：这是用户明确要的语义（反转 Decision 1 已获确认），且授权记录表本身没有对"单策略行数"的约束或索引瓶颈（唯一约束是 `policy_id, user_id, app_id`，批量插入没有特殊性能问题）。
- [风险] SSO 登录路径重构分支逻辑（拆开合并异常）可能引入行为回归（如误判某个失败场景的判定顺序）→ **缓解**：直接复用 `AuthServiceImpl#login` 已验证过的分支顺序与 `LoginFailReason` 常量，不新造判定逻辑；补充/新增 `SsoLoginControllerTest`（当前完全没有测试覆盖这个类）。
- [风险] 日志清理任务默认凌晨 1 点执行，如果应用重启恰好跳过了当天的触发窗口，当天就不会清理 → **接受**：这是 cron 定时任务的固有特性，不属于本次要解决的问题范围，用户也未要求"补偿执行"这种更复杂的语义。

## Migration Plan

不涉及数据迁移。发布顺序：随常规发布上线即可，无需数据库停机或迁移脚本。若需要回滚，`git revert` 对应提交即可，回滚后策略自动重算/登录日志/清理任务恢复为不存在，不影响已产生的数据（清理任务回滚后不会找回已删除的日志行——这是预期行为，删除本身不可逆，与"日志清理"这个功能的本质一致）。

## Open Questions

（无——三处关键设计分歧均已通过用户确认解决：仅请求控制策略的执行语义、自动重算的触发范围、清理表范围）
