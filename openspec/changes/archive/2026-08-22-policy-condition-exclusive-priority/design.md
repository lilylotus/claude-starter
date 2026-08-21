## Context

策略规则（`appaccess.policy`）目前的条件建模是四张独立子表（组织范围、用户属性条件、浏览器白名单、IP白名单），新增/编辑时只校验"四类不能同时为空"，允许任意组合；`PolicyExecutionServiceImpl` 对组织范围与用户属性条件取交集算出身份命中集合，写入 `tab_app_access_policy_grant`；运行时 `AppAccessEffectivePermissionServiceImpl#isAuthorized(userId, appId, clientIp, userAgent)` 遍历该用户命中身份的候选策略，只要任一策略的请求控制条件满足当前请求就放行（并集语义），策略之间没有任何顺序/优先级概念。

本次改动：(1) "用户属性条件"改名为"用户属性"（仅用户可见文案）；(2) 新增策略"显示序号"字段，运行时判定改为"按序号升序取第一条命中身份的策略，只由它决定放行或拒绝"；(3) CAS/OAuth2 登录票据签发被应用访问授权策略拒绝时，记录拒绝来源的策略 id。

> 本 change 最初还包含"组织范围/用户属性/请求控制三选一互斥"这一项，已实现并落地过一版（含数据库校验、前端单选切换 UI、对应单元测试），随后用户反馈希望保持"可以同时配置多类，至少配置一类"的原有交互，该项已整体撤回：服务层校验恢复为"三类不能同时为空"（不要求互斥），前端表单恢复为三个区块一直可见、可同时填写，不做单选切换。以下 Goals/Non-Goals/Decisions 已按撤回后的最终状态更新。

## Goals / Non-Goals

**Goals:**
- 策略新增"显示序号"字段，可在新增/编辑时设置，列表页展示并默认按其升序排列。
- 运行时最终生效权限判定改为"按序号升序取第一条命中身份的策略，由它单独决定结果"，替换现有并集语义。
- "用户属性条件"在用户可见文案中改为"用户属性"。
- CAS/OAuth2 登录票据签发因"排在最前的候选策略请求控制不满足"被拒绝时，`tab_sso_protocol_log` 对应失败记录能追溯到具体是哪条策略（策略 id）造成的拒绝。

**Non-Goals:**
- 不改变组织范围/用户属性/请求控制三者可以同时配置的既有能力，仍维持"三类不能同时为空"（至少一类非空）的校验语义，不引入互斥。
- 拒绝策略 id 的记录范围仅限"运行时最终生效权限判定被拒绝"这一具体分支（CAS/OAuth2 登录票据签发场景），不扩展到其它失败原因（`service`/`redirect_uri` 白名单不匹配、票据/令牌失效等）——这些失败原因本来就没有"具体策略"这个概念，`deniedPolicyId` 字段对它们始终为空，不强行凑一个值。
- 不改动 `isAuthorized(userId, appId)` 无请求上下文重载对应的管理端审计查询路径——该路径不涉及 CAS/OAuth2 运行时拒绝，不产生 `tab_sso_protocol_log` 记录，因此不需要携带拒绝策略 id。
- 不改动 `PolicyExecutionServiceImpl` 的身份命中计算算法本身（组织范围∩用户属性条件的 `intersect` 逻辑）——组织范围与用户属性条件本来就可以同时配置，该逻辑继续按原样计算交集，无需改写。
- 不改动 `isAuthorized(userId, appId)` 无请求上下文重载（`existsActiveGrant`）——它本来就不判断请求控制条件，是"身份命中即可能有效"的粗粒度判断，供不掌握请求上下文的调用方使用，本次不引入优先级语义，维持现状。
- 不改动人工例外（GRANT/DENY override）优先级最高的规则，仍在策略循环之前直接返回。
- 不做 `tab_app_access_policy_grant`/身份命中集合的重新计算触发（序号变更不影响身份命中结果，只影响运行时取候选策略的排序，无需"待重新执行"标记）。

## Decisions

- **~~互斥范围~~（已撤回）**：曾实现"组织范围/用户属性/请求控制三选一互斥"（请求控制整体算一类），`PolicyServiceImpl` 校验方法一度改名为 `assertConditionsExclusive`；用户反馈需要保持可同时配置多类后，已改回 `assertConditionsNotAllEmpty`，语义恢复为"三类（组织范围/用户属性/请求控制）中至少一类非空即可保存"，抛错文案恢复为"组织范围、用户属性、请求控制条件不能同时为空，请至少配置一类"。
- **不处理存量数据的顾虑已不适用**：因为互斥已撤回，不存在"存量数据是否符合新规则"的问题——组织范围/用户属性/请求控制在任何时间点保存的策略都可以同时配置多类，行为始终一致。
- **显示序号字段**：新增 `tab_app_access_policy.show_order INT NOT NULL DEFAULT 0`，语义为"数值越小优先级越高，运行时按升序排列后取第一条"——与项目里 `tab_role.show_order`（数值越大越靠前，用于展示排序）语义方向不同，因此在列注释与 Java 注释里都要显式写清楚排序方向，避免与 `tab_role` 的既有约定混淆。新增/编辑允许指定该值，默认 `0`；相同序号的策略按 `id` 升序作为稳定的 tie-break（数据库/Java 侧统一约定，避免排序结果不确定）。此项保留，不受互斥撤回影响。
- **候选策略排序落点**：`PolicyGrantMapper.selectActivePolicyIds` 对应的 SQL（`resources/mybatis/mapper/PolicyGrantMapper.xml`）在原有 JOIN `tab_app_access_policy` 校验启用状态的基础上，新增 `ORDER BY p.show_order ASC, p.id ASC`，让候选策略天然按优先级排好序返回，`AppAccessEffectivePermissionServiceImpl` 侧不需要自己再排序。此项保留。
- **运行时判定改为"取第一条，非黑即白"**：`isAuthorized(userId, appId, clientIp, userAgent)` 原来的 `for` 循环改为只取 `candidatePolicyIds` 的第一个元素，计算其 `browserSatisfied && ipSatisfied`，结果直接作为返回值（不再遍历其余候选）。`candidatePolicyIds` 为空时行为不变（直接 `false`）。此项保留，与是否互斥无关——这是"多条策略之间"的排序取舍，不是"单条策略内部条件如何组合"。
- **用户属性条件改名范围**：只改前端展示文案（`PolicyRulePanel.vue` 的 label/提示语）；`openspec/specs/app-access-authorization/spec.md` 中"策略规则的用户属性条件"这条 Requirement 的标题保留不变，只改正文行文里的措辞（如提到"用户属性"而非生硬的"用户属性条件"）。后端 Java 类名/字段名/表名（`PolicyUserAttrEntity`/`userAttrs`/`tab_app_access_policy_user_attr` 等）不改。此项保留。
- **前端表单交互（互斥撤回后的最终状态）**：`PolicyRulePanel.vue` 不再有"条件类型"单选，组织范围/用户属性/请求控制三个区块始终同时可见、可同时填写；提交时原样发送三类的当前内容（不做任何清空）；`validateConditions()` 只校验"三类中至少一类非空"及各已填写类型内部的行数据合法性，不再有互斥判断。编辑任意策略（无论新旧）都直接回显其已有的全部条件数据，不需要"默认选中哪个类型"的逻辑。
- **拒绝策略 id 的结构化返回**：新增 `AppAccessAuthorizationDecision`（record，含 `boolean authorized`、`Long deniedByPolicyId`）作为 `AppAccessEffectivePermissionService` 新增方法 `checkAuthorization(userId, appId, clientIp, userAgent)` 的返回类型；原有 `isAuthorized(userId, appId, clientIp, userAgent)` 改为内部委托给 `checkAuthorization(...).authorized()`，保持对外行为不变、签名不变（避免影响未来可能出现的其它调用方）。`checkAuthorization` 只在"存在候选策略、排在最前的一条身份命中但请求控制不满足"这一具体分支上填充 `deniedByPolicyId`；候选为空、或被 DENY/GRANT 人工例外决定的分支 `deniedByPolicyId` 均为 `null`。此项保留。
- **`SsoProtocolException` 携带拒绝策略 id**：新增一个 `SsoProtocolException(String message, Long deniedByPolicyId)` 构造函数（原有单参构造函数保留、`deniedByPolicyId` 默认为 `null`，供其余非策略拒绝场景继续使用），新增 `getDeniedByPolicyId()`。`AppAccessAuthorizationChecker#assertAuthorized` 改为调用 `checkAuthorization(...)`，未授权时把 `deniedByPolicyId`（可能为 `null`）一并传入异常。此项保留。
- **`recordFailure` 新增末尾参数 `deniedPolicyId`**：与既有 `sessionId` 参数的引入方式一致（app-sso-protocol-access-log change 的既有模式），新增参数追加在方法签名末尾，`SsoProtocolLogRecorderImpl` 直接落库到新列；`CasController.login`、`OAuthController.authorize` 里"授权校验被拒绝"的 catch 分支从 `SsoProtocolException#getDeniedByPolicyId()` 取值传入，其余全部 `recordFailure` 调用点（白名单校验失败、票据/令牌失效等约 10 处）一律传 `null`，不推断、不猜测。此项保留。

## Risks / Trade-offs

- [运行时判定语义从"并集/任一满足"改为"取第一条、非黑即白"，会真实改变部分现有多策略叠加场景下的授权结果——例如原本靠一条宽松策略兜底放行的用户，如果被一条序号更小、身份也命中、但请求控制更严格的策略"抢先"判定，会变成直接拒绝] → 这是本次改动明确要达成的行为（用户已确认"严格优先级"选项），风险主要在于上线前需要人工核对现有策略配置、合理设置序号，建议上线前给管理员一次性提示/文档说明新语义，而不是静默切换。
- [`show_order` 相同值时排序不确定] → 统一按 `id` 升序做稳定 tie-break，数据库排序与内存排序都遵循这个约定。

## Migration Plan

新增一个 Flyway 迁移脚本（在现有 `V4__...`/最新版本号之后顺延），包含两处 `ALTER TABLE`：
1. `tab_app_access_policy ADD COLUMN show_order INT NOT NULL DEFAULT 0 COMMENT '显示序号，数值越小优先级越高，运行时按升序取第一条命中身份的策略计算结果'`，不需要回填已有数据（默认值 `0` 即代表"未特别设置优先级"，多条默认值为 0 的策略之间按 `id` 升序 tie-break，行为等价于按创建顺序排列，是合理的默认）。
2. `tab_sso_protocol_log ADD COLUMN denied_policy_id BIGINT UNSIGNED NULL COMMENT '被应用访问授权策略拒绝时，拒绝来源的策略 id（tab_app_access_policy.id），仅该失败原因下非空'`，历史记录该列一律为 `NULL`，不回填（历史失败记录本来就无法反推是被哪条策略拒绝的）。

无需数据回滚脚本，若需要回滚只需回退代码 + 保留这两个新列（多余列不影响旧代码运行）。

## Open Questions

无。
