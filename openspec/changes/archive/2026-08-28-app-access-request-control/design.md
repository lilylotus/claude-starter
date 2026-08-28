## Context

参考 proposal.md - Why/What Changes。本设计基于以下已确认的现有代码与约定：

- `UserAgentParser`（`backend/.../operationlog/util/UserAgentParser.java`）已实现按正则识别浏览器（`parseBrowser` 返回如 `"Chrome 120"`/`"Edge 120"`，识别顺序 Edge→Opera→Firefox→Chrome→Safari→IE，刻意把 Edge/Opera 排在 Chrome 之前——两者的 User-Agent 也含 "Chrome"/"Safari" 关键字，直接复用这个识别顺序，不重新写一遍正则），只需取浏览器名称前缀（去掉版本号）即可映射到本次新增的浏览器枚举。
- `LoginLogRecorderImpl` 里已有"客户端 IP 解析"私有逻辑：优先取 `X-Forwarded-For` 请求头的第一个 IP，否则 `request.getRemoteAddr()`。现在第二处（SSO 拦截）也需要同样的逻辑，提炼为共享静态工具方法。
- `app-access-authorization` 能力已确立的既有范式：策略的"条件"（组织范围、用户属性）都是可选多行子表、整体替换语义、执行时批量计算命中用户写入 `tab_app_access_policy_grant`；最终生效权限 = `DENY 例外 > GRANT 例外 > 启用中策略产生的记录 > 不可访问`，判定逻辑封装在唯一的 `AppAccessEffectivePermissionService`，供管理端查询接口与 SSO 拦截（`AppAccessAuthorizationChecker`）共用。
- 仓库没有现成的 CIDR/IP 匹配工具，也没有引入相关第三方库依赖（`backend/build.gradle` 现有依赖不含此类库）；本设计用标准 `java.net.InetAddress` 手写字节比较实现，不新增依赖。

## Goals / Non-Goals

**Goals:**
- 策略规则新增可选的浏览器白名单与 IP/网段白名单两类"请求控制"条件，与组织范围/用户属性条件平级、独立配置。
- 请求控制只在请求发生时（SSO 登录拦截）校验，不参与策略"执行"的批量计算。
- 最终生效权限计算规则扩展到能表达"策略身份命中但请求不满足该策略的请求控制"这种情况，且多策略命中时按"存在至少一条身份命中且请求满足其请求控制的策略"判定通过。
- 人工追加授权（`GRANT`）不受请求控制约束。

**Non-Goals:**
- 不支持"IP 段" 之外的起止范围写法（如 `10.0.0.1-10.0.0.50`），只支持单 IP 与 CIDR。
- 不支持自定义 User-Agent 正则/关键字匹配，浏览器白名单只能从 `UserAgentParser` 已识别的固定枚举里多选。
- 管理端"最终生效权限查询"审计接口不模拟"若某个 IP/浏览器访问是否会通过"，只做身份维度判定 + 提示"该策略配置了请求控制"，不做假设性的请求上下文推演。
- 不处理 IPv6（`UserAgentParser`/现有登录日志模块本身也未特别处理 IPv6，`X-Forwarded-For`/`getRemoteAddr()` 拿到的字符串是什么就按什么处理；IP/CIDR 匹配工具按 IPv4 实现，IPv6 地址在匹配时视为不匹配任何配置的 IPv4 CIDR，等同该请求在配置了 IP 限制的策略下被拒绝——本期不特别适配，如未来有 IPv6 环境需求再扩展）。

## Decisions

### Decision 1：请求控制建两张独立子表，与组织范围/目标应用同构

- `tab_app_access_policy_browser_rule`：`id/policy_id/browser_code/审计字段`，`UNIQUE KEY(policy_id, browser_code)`。`browser_code` 取值枚举 `CHROME`/`FIREFOX`/`SAFARI`/`EDGE`/`OPERA`/`IE`（与 `UserAgentParser.parseBrowser` 能识别的浏览器一一对应）。
- `tab_app_access_policy_ip_rule`：`id/policy_id/ip_cidr/审计字段`，`UNIQUE KEY(policy_id, ip_cidr)`。`ip_cidr` 存原始字符串（单 IP 如 `192.168.1.100` 或 CIDR 如 `192.168.1.0/24`），保存前校验格式合法。
- 两张表都是"零条或多条，整体替换语义（先删后插）"，与已有 `tab_app_access_policy_org_scope`/`tab_app_access_policy_target_app` 完全同构，复用同一套增删模式，不引入新的建模范式。
- 不把浏览器/IP 规则塞进 `tab_app_access_policy` 主表的某个 JSON/逗号分隔字段：一是与仓库"简单列表用逗号分隔字符串"的既有做法（如 `tab_app_access_policy_user_attr.attr_value` 的 `IN` 多值）相比，这里每行还需要独立的审计字段与可能的后续扩展（如未来给某条 IP 规则加备注），子表更合适；二是浏览器/IP 是两个独立维度，混在一个字段里还要发明分隔符规则，不如两张表直观。

### Decision 2：请求控制只在请求时校验，`tab_app_access_policy_grant` 语义不变

`tab_app_access_policy_grant` 继续只表达"身份命中"（策略执行时按组织范围/用户属性算出的用户集合），不感知请求控制——浏览器/IP 是每次请求都可能不同的运行时上下文，无法像身份属性那样离线批量算出"谁能访问"，只能在请求发生的那一刻现查现判断。这意味着"策略执行"（`PolicyExecutionService.execute`）本身不需要任何改动，请求控制的校验逻辑完全新增在"最终生效权限判定"这一层。

### Decision 3：`AppAccessEffectivePermissionService` 新增带请求上下文的判定入口，原入口语义调整为"仅身份维度"

新增方法：

```java
boolean isAuthorized(Long userId, Long appId, String clientIp, String userAgent);
```

判定逻辑：① 存在 `DENY` 人工例外 → 拒绝；② 存在 `GRANT` 人工例外 → 授权（不看 `clientIp`/`userAgent`，即 Decision 里"人工追加不受请求控制约束"）；③ 否则查出该 `user_id+app_id` 在**启用中**策略下的全部 `tab_app_access_policy_grant` 记录，取其 `policy_id` 去重集合；对集合中每个策略，批量查出其 `tab_app_access_policy_browser_rule`/`tab_app_access_policy_ip_rule`（一次 `IN` 查询批量拿全部候选策略的规则，避免逐个策略查询的 N+1），逐个判断"该策略的请求控制条件是否被当前请求满足"（浏览器白名单非空时需要 `UserAgentParser.parseBrowser(userAgent)` 识别出的浏览器命中白名单之一，IP 白名单非空时需要 `clientIp` 命中任一 CIDR/单 IP，两个维度都配置时需都满足，都未配置时视为满足）；只要存在一条满足的策略 → 授权；全部不满足（或集合为空，即没有任何策略身份命中）→ 拒绝。

原有方法 `isAuthorized(Long userId, Long appId)`（两参数版本，已有测试覆盖）**保留不变**，实现上不复用新方法（不是"传 `clientIp=null, userAgent=null` 简化调用"），而是继续沿用原来的单条 `existsActiveGrant` EXISTS 查询——只要存在启用中策略的身份命中记录就算授权，不看该策略是否配置了请求控制。这个"仅身份维度"结果供管理端"最终生效权限查询"审计接口使用：审计场景没有真实的请求上下文，展示"身份层面是否够格"比较合理，额外在查询结果里标注该策略是否配置了请求控制（见 Decision 5），提示这不是"任意请求都能通过"的保证。

两个方法各自独立实现"取候选授权依据"这一步（旧方法是单条 `existsActiveGrant` EXISTS 查询判存在性；新方法是 `selectActivePolicyIds` 取候选 `policy_id` 集合后再批量查请求控制规则逐条比较），两者写法不同、没有共用这段查询逻辑；唯一共享的是人工例外优先级判定，抽成私有辅助方法 `resolveOverrideDecision(userId, appId)`（返回 `Boolean`：`true`/`false` 表示人工例外已给出最终判定，`null` 表示不存在人工例外、需继续按策略授权记录判定），供两个方法开头调用，避免这段逻辑重复实现两遍。

### Decision 4：IP/网段匹配工具手写实现，不新增依赖

新增 `IpCidrMatcher`（放在 `appaccess/support/` 下），与 `ClientRequestUtils` 同样的风格——不接入 Spring 容器，不可实例化，全部方法为无状态静态方法，调用方直接 `IpCidrMatcher.matches(...)`：

```java
public static boolean matches(String clientIp, String ipCidrRule);
public static boolean isValidRule(String ipCidrRule);
```

- 单 IP（不含 `/`）：字符串精确比较（或都解析成 `InetAddress` 后比较，兼容大小写/前导零等书写差异）。
- CIDR（含 `/`）：用 `java.net.InetAddress.getByName` 解析出网络地址与客户端地址的字节数组，按前缀长度逐字节/逐位比较，判断客户端地址是否落在网段内。只处理 IPv4（4 字节地址），`InetAddress` 解析结果字节数不是 4 的（如 IPv6）直接判定不匹配（见 Non-Goals）。
- 保存策略的 IP 规则时（Service 层）复用同一个工具做格式合法性校验（`InetAddress.getByName` 解析失败或前缀长度超出 0-32 范围时拒绝保存），不单独再写一套格式校验正则。

### Decision 5：管理端展示——策略回显请求控制配置，最终生效权限查询标注"该结果来自哪些策略、是否配置了请求控制"

- `PolicyVO` 新增 `browserRules: [{browserCode, browserLabel}]`、`ipRules: [{ipCidr}]`，与 `orgScopes`/`userAttrs`/`targetApps` 并列，管理端表单据此回显/编辑。
- `AppAccessEffectiveItemVO`（"最终生效权限查询"结果项）在已有 `policyNames` 基础上，新增一个布尔标记（如 `hasRequestControl`），只要 `policyNames` 里任意一条对应的策略配置了浏览器或 IP 规则就为 `true`，前端据此展示一个"部分策略配置了请求控制，实际访问是否放行还需满足浏览器/IP 限制"的提示图标/文案，不展开列出具体规则内容（避免审计页面信息过载，需要看具体规则去策略详情页）。

### Decision 6：SSO 拦截改动位置与既有客户端 IP 解析逻辑的复用

`AppAccessAuthorizationChecker.assertAuthorized` 签名调整为 `assertAuthorized(Long userId, Long appId, String clientIp, String userAgent)`，内部改调用 Decision 3 的新入口。`CasController.login`、`OAuthController.authorize` 在调用处补上 `request.getHeader("User-Agent")` 与客户端 IP 解析。

客户端 IP 解析逻辑与 `LoginLogRecorderImpl` 现有私有方法完全一致（`X-Forwarded-For` 第一个值优先，否则 `getRemoteAddr()`），提炼为共享静态工具 `ClientRequestUtils.resolveClientIp(HttpServletRequest)`（放在 `common/util/` 下，与仓库其它无状态工具类同级），`LoginLogRecorderImpl` 同步改为调用这个共享方法、删除自己的私有实现，避免两处维护同一段逻辑后续漂移。这是本次改动唯一顺带触碰"既有模块内部实现"的地方，仅做无行为变化的提取，不改变 `LoginLogRecorderImpl` 对外行为，其现有测试不需要跟着改断言。

## Risks / Trade-offs

- [风险] 策略执行时不校验请求控制配置的合法性以外的语义一致性（比如管理员给一个组织范围很大的策略同时配了很严格的 IP 限制，导致大部分命中身份的用户实际都会被请求控制拦下）——这是管理员自己的配置选择，系统不做"配置合理性"层面的提示或阻止，属于预期内的管理员责任范围，不在本次改动处理。
- [权衡] 判定"是否授权"从原来的一条 `EXISTS` 查询变成"查候选策略 id → 批量查规则 → Java 侧逐条比较"，多了几次数据库往返，但候选策略数量在实际场景下很小（一个用户对一个应用通常只命中个位数策略），可接受；不引入缓存（与原设计一致，非本期目标）。
- [风险] `LoginLogRecorderImpl` 的客户端 IP 解析逻辑被提取为共享工具，理论上有改错导致登录日志模块回归的风险 → 缓解：提取是纯粹的"复制方法体、原地替换调用"，不改变任何分支逻辑，且 `LoginLogRecorderImpl` 现有测试覆盖了这条路径，提取后重跑其测试即可验证行为未变。
- [风险] `UserAgentParser.parseBrowser` 识别不出的 User-Agent（返回 `null`，如爬虫、旧版浏览器、伪造的 UA）在配置了浏览器白名单的策略下会被判定为不满足——这是刻意的 fail-closed 行为（宁可拒绝也不放行未知客户端），如果管理员的策略配置了浏览器限制、又需要放行某个 `UserAgentParser` 识别不出的客户端，目前无法绕过，只能不配置浏览器限制或用人工例外单独处理该用户。
