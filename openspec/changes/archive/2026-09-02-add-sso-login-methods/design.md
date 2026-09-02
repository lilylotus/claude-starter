## Context

SSO 登录页 `SsoLoginView.vue` 是外部应用发起 CAS（`/api/authn/cas/{appId}/login`）或
OAuth2.0（`/api/authn/oauth/authorize?client_id=...`）单点登录、浏览器又没有有效 SSO 会话
Cookie 时唯一的落地页；`ProtocolResponseWriter.ssoLoginRedirectLocation` 把原始请求的完整
URL（含 CAS 路径里的 `appId` 或 OAuth2 查询串里的 `client_id`）编码进 `redirect` 参数带过去，
登录页目前只用它做"登录成功后 `window.location.href = redirect` 整页跳回"，从不解析里面的应用
信息。登录本身完全走 `SsoLoginController`（口令 + RSA 公钥加密），成功后由 `SsoSessionService`
在 Redis 签发 `sso:session:<token>` 并通过 HttpOnly Cookie 下发，这是 CAS 票据签发、OAuth2
授权码签发共同依赖的"当前浏览器是否已登录"判断依据。

管理端直接登录页 `LoginView.vue` 走另一套完全独立的口令登录（`AuthController`/
`AuthServiceImpl`），本次不涉及。

应用侧的认证相关配置集中在 `app/authconfig` 模块：`tab_app_auth_config` 一对一挂在每个应用
下，`AppAuthConfigController` 的查询/修改接口已经用权限点 `AppManagement:app:config:editAuth`
做写操作管控（前端按钮层面控制）。新增"允许的登录认证方式"天然属于这份配置的一部分。

`tab_user.mobile` 字段已存在但显式不做唯一性约束（`UserCreateRequest`/`UserUpdateRequest`
校验注释），短信登录需要在运行时自行判断"这个手机号当前能不能唯一定位到一个可登录账号"。

项目里 Redis 已经是 SSO 会话、票据类短生命周期状态的既定选型（`SsoSessionService`、
`CasTicketService`、`OAuthTokenService` 均只用 Redis，不落库），短信验证码、二维码登录会话
延续同一模式。项目当前没有 `@Profile`/多环境切换的既有用法，也没有任何验证码/二维码相关依赖。

## Goals / Non-Goals

**Goals:**
- SSO 登录页新增短信验证码、扫码两种登录方式，与口令登录产出完全一致的 SSO 会话（同一套
  `SsoSessionService.issue` + Cookie 下发），CAS/OAuth2 协议运行时代码不感知登录方式差异。
- 应用维度可配置该应用 SSO 登录时展示哪些认证方式（口令恒定展示，短信/扫码可选开启）。
- 短信发送做成可替换的抽象，当前只提供一个不对接真实厂商的占位实现。
- 扫码登录复用现有响应式前端能力，通过手机浏览器确认页完成"扫码端"身份确认，不引入原生
  App/长连接依赖。

**Non-Goals:**
- 不对接任何真实短信厂商（阿里云/腾讯云等），`SmsSender` 只有一个占位实现。
- 不做设备指纹、扫码风控、登录二次强验证（如"口令+短信"组合 MFA）。
- 不改动管理端直接登录页 `LoginView.vue`、`AuthController` 口令登录链路。
- 不做原生 App 扫码，"扫码端"就是普通手机浏览器打开的响应式确认页。
- 不改变 CAS/OAuth2 协议本身的报文格式，登录方式差异只发生在"浏览器换取 SSO 会话 Cookie"
  这一步之前。

## Decisions

### Decision 1：应用允许的登录方式随 `AppAuthConfigEntity` 存储，格式对齐 `servicePatterns`
`tab_app_auth_config` 新增列 `login_methods`（JSON 字符串数组文本，读写模式与
`servicePatterns` 完全一致，经 `JacksonUtils` 与 `List<String>` 互转）。取值只能是
`PASSWORD`/`SMS`/`QRCODE`；`PASSWORD` 恒定包含且服务端不允许移除（提交的列表里没有
`PASSWORD` 时由服务端自动补齐，而不是拒绝请求——避免管理员误操作导致某个应用彻底无法登录）；
`SMS`/`QRCODE` 是否出现代表是否为该应用启用。新建应用时默认值为 `["PASSWORD"]`，
存量应用通过 Flyway 迁移一次性回填同样的默认值，不影响现有登录行为。

**备选方案**：按协议类型（NONE/CAS/OAUTH2）分别维护登录方式列表——不采用，登录方式是"这个
应用的用户怎么证明自己是谁"，与"这个应用接的是哪种 SSO 协议"是两个独立维度，`app-auth-
protocol-config` change 已经把 `servicePatterns` 从"按协议分别维护"改成协议共用一份，本次
延续同一思路，不引入新的按协议区分。

### Decision 2：新增 `SsoLoginContextResolver`，从 `redirect` 参数反解出目标应用
CAS 场景 `redirect` 解出的原始 URL 形如 `.../api/authn/cas/{appId}/login?service=...`，OAuth2
场景形如 `.../api/authn/oauth/authorize?client_id=...&...`。新增一个无状态解析工具
`SsoLoginContextResolver`（`sso/support` 包），按路径正则匹配 CAS 分支取路径变量、按查询串取
`client_id` 匹配 OAuth2 分支，解析失败或解析出的 `appId` 查不到 `AppAuthConfigEntity` 时统一
返回"仅允许 PASSWORD"的保守结果，不抛错——直接访问登录页（无 `redirect`）、`redirect` 被篡改
成无法识别的地址等异常输入都不应该意外放开短信/扫码入口。短信发送、二维码会话创建/确认这几个
新增接口全部复用同一个解析器再次校验一遍允许的方式，不能只在前端"要不要显示对应 Tab"这一层
把关，防止绕过前端直接调用接口。

**备选方案**：登录页把 `appId`/`client_id` 作为独立查询参数直接带给前端——不采用，
`ProtocolResponseWriter` 已经把完整原始 URL 编码进 `redirect`，重复携带一份 `appId` 会造成
两份数据可能不一致（篡改其中一个），且要改动两处协议入口的重定向拼接逻辑；反解 `redirect`
是唯一数据源，天然一致。

### Decision 3：新增公开接口 `GET /api/authn/sso/login-methods?redirect=`
比照现有 `GET /api/authn/sso/public-key`（无需身份校验），新增一个同样公开的接口，内部调用
`SsoLoginContextResolver` + `AppAuthConfigService` 返回该次登录允许的方式列表
（`List<String>`，如 `["PASSWORD","SMS"]`）。登录页首屏据此决定展示哪些 Tab；未携带
`redirect`（如开发时直接打开登录页调试）时返回 `["PASSWORD"]`，页面退化为当前"只有口令表单"
的样式，不出现空 Tab 或异常。

### Decision 4：短信验证码登录——防枚举优先于"提前告知手机号无效"
- 发送接口 `POST /api/authn/sso/sms/code`（body: `redirect`、`mobile`）：无论手机号是否能
  唯一定位到一个启用状态用户，只要通过基础格式校验与限流检查，一律返回相同的成功响应；只有
  内部查到"`tab_user.mobile` 精确匹配且 `status=ENABLED` 且未删除"的记录数恰好为 1 条时，
  才真正生成验证码并调用 `SmsSender` 发送，0 条或多条都静默跳过发送。这与口令登录"不泄露
  账号不存在与密码错误的具体区别"是同一防枚举原则的延伸，避免攻击者通过"是否收到验证码"或
  接口返回差异探测手机号是否已注册、是否重复绑定。
- 验证码：6 位数字，Redis key `sso:sms:code:<mobile>`，TTL 300 秒；每次成功发送覆盖旧值。
- 发送冷却：`sso:sms:cooldown:<mobile>`，TTL 60 秒内的重复发送请求直接拒绝（提示"请求过于
  频繁"，这一层提示不涉及账号信息，可以精确)。
- 每日发送上限：`sso:sms:daily:<mobile>:<yyyyMMdd>` 计数器，超过阈值（默认 10）拒绝发送；
  用 `RedisUtils` 新增一个基于 `StringRedisTemplate.opsForValue().increment` 的原子自增
  helper（`RedisUtils.increment(key, ttl, unit)`，首次自增后设置过期时间）实现，不用"读-改-写"
  避免并发计数不准。
- 校验接口 `POST /api/authn/sso/sms/login`（body: `redirect`、`mobile`、`code`）：验证码不
  存在/已过期/不匹配统一返回与口令登录一致文案的通用失败（不区分"验证码错误"与"手机号未找到
  匹配账号"，原因同上）；连续失败次数用 `sso:sms:attempts:<mobile>` 计数，达到阈值（默认 5）
  后立即使当前验证码失效（删除 `sso:sms:code:<mobile>`），要求重新获取验证码。校验通过后按
  该手机号此刻查到的唯一用户调用 `SsoSessionService.issue`，与口令登录共用同一套后续逻辑
  （首登强制改密状态判断、Cookie 下发、登录日志记录）。

**备选方案**：要求手机号在用户表全局唯一（加唯一索引）——不采用，`UserManagement` 现有约定
明确"不做唯一性约束"，改动会影响用户管理既有能力且超出本次范围；改为登录时动态判断"唯一
匹配"把这个约束收敛在登录场景内，代价是"一号多绑定"的用户无法通过短信登录，这是可接受的
产品行为（提示用户改用口令登录）。

### Decision 5：扫码登录——PC 生成会话，手机浏览器确认页完成身份确认，PC 端轮询取得会话
- PC 端登录页展示扫码 Tab 时调用 `POST /api/authn/sso/qrcode/session`（body: `redirect`），
  后端生成一次性 token（UUID 去横线，同项目既有令牌风格），写入 Redis
  `sso:qrcode:<token>` = `{status: PENDING, appId, redirect}`，TTL 300 秒（与二维码"过期
  需刷新"的 UI 提示对齐）；响应（`QrcodeSessionVO`）返回 `token` 与确认页**相对路径**
  `confirmPath`（如 `/sso/qrcode/confirm?token=xxx`，前端路由）——后端不猜测前端部署的
  origin，只返回相对路径；前端拿到后自行用 `window.location.origin + confirmPath` 拼出
  完整地址，再用新增的 `qrcode` npm 包在浏览器端渲染二维码图片，不需要后端生成图片、不
  新增后端依赖。
- PC 端登录页拿到 token 后开始轮询 `GET /api/authn/sso/qrcode/{token}/status`（间隔 2 秒），
  返回当前状态：`PENDING`/`SCANNED`/`CONFIRMED`/`EXPIRED`。
- 手机浏览器扫码后打开确认页，确认页调用一个"标记已扫码"的接口把状态从 `PENDING` 置为
  `SCANNED`（仅用于 PC 端 UI 提示"已扫码，请在手机上确认"，不代表登录）。若手机浏览器当前
  没有有效的 SSO 会话 Cookie，确认页复用现有 `SsoLoginController` 口令登录表单要求先登录
  （与 `SsoLoginView.vue` 的口令 Tab 是同一套组件/接口，登录成功后留在确认页而不是走
  `redirect` 整页跳转）。
- 手机端登录后点击"确认登录"按钮，调用 `POST /api/authn/sso/qrcode/{token}/confirm`（携带
  手机浏览器自己的 SSO 会话 Cookie 标识身份），后端校验 token 未过期、状态为
  `PENDING`/`SCANNED`（未被消费），把状态置为 `CONFIRMED` 并记录该手机端会话对应的
  `userId`，此步骤*不*为 PC 端签发会话——手机端和 PC 端是两个独立浏览器，Cookie 无法跨端
  下发。
- PC 端下一次轮询命中 `CONFIRMED` 状态时，服务端在这次响应里为 PC 浏览器调用
  `SsoSessionService.issue(userId)` 签发一个新的 SSO 会话并通过 `Set-Cookie` 下发（与口令/
  短信登录最终产出一致），同时立即把 Redis 状态改为 `CONSUMED` 并保留极短 TTL（如 5 秒）
  后自然过期，保证"CONFIRMED → 签发会话"只发生一次，即使前端因网络重试重复调用一次状态
  接口也不会重复签发或状态错乱。
- 手机端与 PC 端的 SSO 会话是各自独立的两条 `sso:session:<token>` 记录，互不影响、互不
  提前失效。

**备选方案 A（原生 App 扫码）**：需要一个配套移动端 App 持有已登录身份去调用扫码接口——
不采用，项目目前没有移动端 App，超出本次范围（design.md Non-Goals）。
**备选方案 B（手机端确认后由手机直接把凭证回传给 PC，如通过 WebSocket 推送替代轮询）**：
项目已有 Netty 聊天网关的 WebSocket 基础设施，理论可复用做"服务端主动推送"替代轮询，但会
让登录这一基础能力依赖聊天网关的可用性，增加故障面；轮询实现简单、状态可预测，5 分钟超时
场景下 2 秒轮询间隔的开销可忽略，采用轮询。

### Decision 6：`SmsSender` 可插拔接口，当前只有一个日志占位实现
新增 `cn.nihility.rbac.sso.sms.SmsSender` 接口（单方法 `send(mobile, code)`），当前唯一实现
`LogSmsSender` 把验证码写入应用日志（`log.info`），不做真实网络调用。项目里没有
`@Profile`/多环境隔离的既有用法，这里也不引入——等真正接入某个厂商时，新增一个实现类并把
Spring 装配切到那个实现即可，`LogSmsSender` 到时候可以保留作为本地开发默认值或删除，留给
那次 change 决定，不在本次预先设计"生产/非生产"开关。

### Decision 7：登录日志新增 `login_method` 列，区分口令/短信/扫码
`tab_login_log` 新增列 `login_method`，取值 `PASSWORD`/`SMS`/`QRCODE`，默认 `PASSWORD`
（Flyway 迁移里对存量数据一次性回填，新列 `NOT NULL DEFAULT 'PASSWORD'`）。
`LoginLogRecorder.recordSuccess`/`recordFailure` 增加一个重载或新增参数传入登录方式；
`SsoLoginController` 口令登录固定传 `PASSWORD`，新增的短信/扫码登录入口分别传对应值。
`login-log-management` 能力的查询接口、`LoginLogVO`、管理页筛选下拉同步新增该字段的展示/
筛选（复用现有下拉筛选的既有模式，不单独设计）。登录方式取值集中定义在新增的
`cn.nihility.rbac.loginlog.constant.LoginMethod`（`PASSWORD`/`SMS`/`QRCODE` 常量 +
`ALL_VALUES` 校验集合 + 中文文案 `label()`），`login_methods` 应用配置字段与
`login_method` 登录日志字段复用同一套取值常量，不各自重复定义。

短信登录失败时新增两个内部失败原因常量（`LoginFailReason.SMS_CODE_MISMATCH`：验证码
不正确或已过期；`LoginFailReason.MOBILE_NOT_MATCHED`：验证码正确但按提交手机号此刻查询
不到恰好一个启用状态账号，属于验证码发出后账号状态发生变化的边界情况），登录失败日志按
实际命中的原因分别记录，不合并成一个笼统的失败原因，便于后续排查区分"验证码问题"与
"账号状态变化"两类失败。

### Decision 8：权限点复用，不新增
短信/扫码开关的保存入口仍是"应用配置页 → 认证管理"，沿用现有 `updateAuthConfig` 接口与
`AppManagement:app:config:editAuth` 权限点即可覆盖，不新增权限点。查看态（认证管理 Tab 的
访问）继续复用 `AppManagement:app:config`。

### Decision 9：前端 `qrcode` 包为唯一新增前端依赖
`frontend/package.json` 目前没有任何二维码相关依赖。新增 `qrcode`（生成二维码 dataURL/canvas
的轻量库，纯前端渲染，不需要后端出图）。这是本次唯一新增的第三方依赖（后端不新增任何
`build.gradle` 依赖——短信/扫码/限流全部基于既有的 Redis + JSON 工具实现）。

## Risks / Trade-offs

- [手机号一号多绑定用户无法用短信登录] → 明确产品行为：提示改用口令登录；后续如需支持可
  在确认唯一后再补充"选择具体账号"的交互，本次不做。
- [二维码确认页需要手机端先完成口令登录，体验上比"扫码即登录"多一步] → 符合"复用现有能力、
  不引入新依赖"的既定取舍；用户已在手机浏览器登录过一次后，其 SSO 会话在有效期内可直接点
  确认，不需要每次都重新输入口令。
- [轮询而非推送，短暂状态感知延迟（≤2 秒）] → 登录场景对延迟不敏感，可接受。
- [短信发送限流基于单 Redis key 的 TTL/计数器，非分布式强一致，理论上存在极小概率的
  竞态（如两个并发请求都通过了冷却检查）] → 影响范围仅限于"该手机号可能多收到一条短信"，
  不影响资金/权限类安全属性，接受该风险；如未来接入真实厂商且厂商本身有限流，可进一步收紧。
- [`SsoLoginContextResolver` 需要同时理解 CAS 与 OAuth2 两种 URL 形状，新协议接入时容易
  漏改] → 与协议相关的解析逻辑集中在这一个类里并附详细注释，后续新增协议时的检查点在
  design.md 里留痕（本 Decision 2），降低遗漏概率。

## Migration Plan

1. Flyway 新增迁移脚本（版本号续接现有 `V2__create_chat_tables.sql` 之后，即
   `V3__add_sso_login_methods.sql`）：
   - `tab_app_auth_config` 新增列 `login_methods VARCHAR(500) NOT NULL DEFAULT '["PASSWORD"]'`。
   - `tab_login_log` 新增列 `login_method VARCHAR(20) NOT NULL DEFAULT 'PASSWORD'`。
   - 均为新增可默认列，不改动既有列、不需要数据回填脚本之外的额外处理，存量数据自动满足
     默认值语义（存量应用视为只允许口令登录、存量日志视为口令登录产生）。
2. 后端新增代码：`app/authconfig`（entity/dto/service 扩展）、`sso/support`
   （`SsoLoginContextResolver`）、`sso/sms`（`SmsSender`/`LogSmsSender`/验证码 service/
   controller）、`sso/qrcode`（会话 service/controller）、`loginlog`（`login_method`
   贯穿 entity/dto/mapstruct/query）。
3. 前端：新增 `qrcode` 依赖；`SsoLoginView.vue` 改造为按 `login-methods` 接口结果动态展示
   Tab；新增扫码确认页路由与组件；应用配置页"认证管理" Tab 新增短信/扫码开关勾选项。
4. 回滚：新增列均带默认值，回滚 Flyway 脚本（`DROP COLUMN`）与代码一起回退即可，不影响
   既有口令登录/CAS/OAuth2 协议运行时数据。

**已知问题（超出本次 change 范围）**：仓库配置的共享开发数据库（10.10.88.31）上，
`V3__add_sso_login_methods.sql` 未能走正常的 Flyway migrate 流程跑过——该库的
`flyway_schema_history` 还停留在 squash 提交（`refactor(flyway): 合并V1至V14迁移脚本`）
之前的旧版本号体系，与本地仓库现在只有 `V1`/`V2` 两个迁移文件的情况不一致，属于那台共享库
自身的历史遗留问题，不是本次 change 引入的缺陷。实际是手工执行等价的 `ALTER TABLE`
语句 + `flyway repair` 让该库的 schema 状态和本地迁移文件对齐后，才具备继续验证的条件。
本地/全新环境走正常 `flyway migrate` 不受影响；那台共享库本身的历史遗留版本号体系问题留给
后续独立的 housekeeping 处理，不在本次 change 范围内解决。

## Open Questions

- 短信发送每日上限、冷却时长、验证码有效期、二维码会话有效期等具体数值当前采用本设计里给出
  的默认值（10 次/天、60 秒冷却、5 分钟验证码有效期、5 分钟二维码有效期），如产品侧有明确
  的合规/风控要求，可在 `tasks.md` 落地时调整为可配置项（`RbacSsoProperties` 或独立
  `RbacSmsProperties`/`RbacQrcodeProperties`），本设计默认先写成配置项而非硬编码常量，方便
  后续调参不用改代码。
