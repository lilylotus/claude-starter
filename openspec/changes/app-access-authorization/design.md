## Context

参考 proposal.md - Why/What Changes。本设计基于以下已确认的现有约定与代码：

- `tab_admin_org_scope`（`backend/.../V1__init_schema.sql:493`）是本仓库现成的"组织范围"表范式：`admin_id + org_id + include_children`，`UNIQUE KEY(admin_id, org_id)`，无独立 `status`，整体随所属对象重建、物理删除，无物理外键（注释显式声明）。
- `tab_user_position`（`V1__init_schema.sql:137`）**有独立 `status` 字段**（2000 启用/3000 停用/-1000 已删除），逻辑删除；判断"用户是否落在某组织范围"要用 `status <> -1000` 过滤且不限定 2000/3000（参考 `app-sync-notify-pull` 能力里"任一未删除任职落在范围内即命中"的既有语义，停用不等于删除）。
- `OrgDescendantExpander.expandWithDescendants(Set<Long> rootOrgIds)`（`backend/.../org/support/OrgDescendantExpander.java:45`）已经实现"给一批根组织 id，展开为含全部未删除子孙组织的 id 集合"，直接复用，不重新实现递归查询。
- SSO 登录鉴权现状（已探明）：`CasController.login`（`sso/cas/controller/CasController.java`）与 `OAuthController.authorize`（`sso/oauth/controller/OAuthController.java`）是仅有的两处"同时持有 userId 与 appId、且处于凭证签发源头"的位置；两者现有的"协议参数校验不通过"分支统一走 `AppProtocolGuard` 查库校验 → 抛 `SsoProtocolException` → 各自 `catch` 后 `ProtocolResponseWriter.text(response, 400, e.getMessage())`。`serviceValidate`/`token`/`userinfo` 是消费已签发凭证的环节，本设计不在这些点重复查权限表。
- 应用当前是否启用（`tab_app.status`）目前完全不参与 SSO 登录流程的任何校验——这是登录流程既有的、与本次改动无关的缺口，本设计不顺带修复（避免扩大改动范围），仅在下方 Non-Goals 中记录。

## Goals / Non-Goals

**Goals:**
- 策略规则（组织范围 → 目标应用）的定义、编辑、手动执行（整体重建该策略产生的授权记录，不影响人工例外）。
- 人工例外（对具体"用户+应用"手动追加授权 / 手动收回授权）的独立维护，优先级高于策略结果。
- 统一的"最终生效权限"计算规则与查询能力，支持按用户或按应用查看，且能追溯每条最终结果的来源。
- 在 CAS 服务票据签发、OAuth2 授权码签发两个源头位置接入这一校验，未授权时复用现有 `SsoProtocolException` 拒绝机制。

**Non-Goals:**
- 不引入"应用分组"概念（策略目标直接是应用 id 列表）。
- 策略条件维度限定为"组织范围"与"用户属性条件"两类；用户属性条件的字段来源限定为"元数据字段管理"目录中 `biz_type=USER` 的字段（即 `tab_user` 表的列），不支持基于角色、任职类型等其他维度的条件，也不支持跨表（如 `tab_user_position` 的字段）的属性条件。
- 不监听用户/组织变更事件自动触发策略重算（本期仅手动"执行"按钮触发）。
- 不修复"应用被停用后 SSO 登录流程不感知"这个既有缺口（不在本次改动范围内）。
- 不在 `serviceValidate`/`token`/`userinfo` 等消费凭证的环节重复校验授权（即"收回权限后已签发的 token/票据"在其有效期内仍可被消费到，属于已知的滞后窗口，票据/授权码本身有效期很短，token 也有过期时间，可接受）。

## Decisions

### Decision 1：用户属性条件复用元数据字段管理目录，不新增专用字段

策略的"用户属性条件"（如"性别=男"）不新增 `tab_user` 表字段（如年龄），也不新增一套独立的属性目录，而是直接引用现有 `tab_metadata_field`（`biz_type='USER'`）里已登记的字段：新表 `tab_app_access_policy_user_attr` 存 `policy_id + metadata_field_id + operator + value`，`metadata_field_id` 是外键式引用（不建物理外键，与仓库既有约定一致），保存时校验该字段确实存在于 `tab_metadata_field` 且 `biz_type='USER'`、`status=2000`（启用）。执行阶段读取该字段的 `column_name`（如 `gender`）拼出 `tab_user.<column_name>` 的查询条件，`column_name` 只能来自这张受控目录（管理员在"元数据字段管理"模块里维护），不接受调用方直接传原始列名字符串，避免拼 SQL 时引入注入面。

执行阶段动态列名拼接的合法性校验方式见下方 Risks 一节（实际实现在 Service 层做，不在 Mapper 层）。

`operator` 取值限定 `EQ`/`NE`/`IN` 三种（元数据字段目前清一色 `VARCHAR`，`show_order` 是 `INT` 但语义上不适合做访问条件，不为它单独开数值比较运算符；如未来确有数值型属性条件需求，再扩展 `operator` 枚举，不影响现有存量数据）：`EQ`/`NE` 时 `value` 存单个值；`IN` 时 `value` 存逗号分隔的多个值（沿用仓库里"简单列表用逗号分隔字符串落库"的既有做法，不为此单独建一张多值子表）。同一策略内，同一个 `metadata_field_id` 不允许重复配置（参考 `app-api-credentials` 字段映射"同一数据域内源字段不允许重复"的既有约束）。

组织范围条件与用户属性条件在同一策略内 SHALL 均为可选，但两者不能同时为空——策略至少要圈定"是哪些人"，不允许一条不限定任何人群的策略（否则退化成"对全公司所有人授权"，应该用更明确的方式表达，而不是留空产生歧义）；两者都配置时执行阶段取交集（见 Decision 4）。

### Decision 2：策略产出的授权记录与人工例外记录用两张物理表，不用同一张表 + `grant_source` 枚举

- **策略结果表 `tab_app_access_policy_grant`**：只存策略计算出的授权行，`policy_id + user_id + app_id`，来源恒为"策略"，不需要 `grant_source` 字段本身。整体重建时 `DELETE FROM tab_app_access_policy_grant WHERE policy_id = :id` 后批量插入，天然不触碰其他策略或人工例外的行。
- **人工例外表 `tab_app_access_manual_override`**：只存人工操作，`user_id + app_id + override_type`（`GRANT`/`DENY`），`UNIQUE KEY(user_id, app_id)`（一个用户对一个应用同一时刻只有一条例外：要么手动加开，要么手动收回，编辑已有例外是改 `override_type`/`remark`，不是叠加新行）。
- **为什么不用一张表 + 枚举**：如果合并成一张 `tab_user_app_auth(user_id, app_id, grant_source)`，策略重建（`DELETE ... WHERE policy_id=X`）与人工例外的增删会写在同一张表里，任何一次策略重建的 `DELETE`/批量 `INSERT` 都必须小心地加 `AND grant_source='POLICY'` 才不会误删人工例外行，一旦某次维护漏加这个条件就会静默吞掉管理员的手动操作——这正是用户明确要求"不要耦合在同一张表"要规避的风险。拆成两张表后，策略执行的 SQL 天然只触碰策略表，人工例外的增删天然只触碰例外表，物理上不可能互相污染，`UNIQUE KEY` 约束的语义也更清晰（策略表允许同一 user+app 出现在多条不同 `policy_id` 下——多个策略都命中同一人同一应用是正常场景；例外表每个 user+app 组合全局唯一）。
- **考虑过的替代方案**：单表 + `grant_source` 枚举——优点是查询"某用户某应用的所有来源"只需一张表；缺点是删除/更新语义必须靠应用层严格约束 `WHERE grant_source=...`，且策略表的 `(policy_id,user_id,app_id)` 主键语义和例外表的 `(user_id,app_id)` 唯一语义强行塞进一张表会导致索引设计尴尬（要么允许重复 user+app 要么不允许，两种来源的唯一性诉求互斥）。放弃。

### Decision 3：最终生效权限不落库，查询时实时合并计算

`最终生效(user, app) = 存在 DENY 例外 ? 拒绝 : (存在 GRANT 例外 ? 授权 : (存在启用中策略产生的记录 ? 授权 : 拒绝))`。

- 不新增第三张"最终结果"表做物化，理由：
  1. 例外表和策略表本身都不大（例外是人工操作，量级有限；策略结果是"组织范围×目标应用"的笛卡尔积，单次执行后也是有限集合），实时 `LEFT JOIN`/`EXISTS` 查询在管理端分页查询和 SSO 登录单次点查场景下都足够快，命中 `UNIQUE KEY(user_id, app_id)` 索引。
  2. 物化第三张表要解决"策略禁用/删除、例外变更"时如何增量刷新物化结果的一致性问题，复杂度远高于收益。
  3. 策略是否计入还要看 `tab_app_access_policy.status` 是否为"启用"——禁用一条策略应当立即让它产生的记录不再计入最终结果，且不需要管理员重新点"执行"去清空；查询时 `JOIN tab_app_access_policy ON status=2000` 即可天然实现，物化表则做不到"禁用即时生效"。
- SSO 登录路径的单次点查（"这个 user 能不能访问这个 app"）用同一套合并逻辑收窄成单条 `EXISTS`/`CASE WHEN` SQL，索引命中，性能可接受；不引入缓存层（本期非目标，如后续有性能问题再评估）。

### Decision 4：策略执行是"整体重建"而不是增量 diff，组织范围与属性条件取交集

点击"执行"时：① 若配置了组织范围，用 `OrgDescendantExpander.expandWithDescendants` 展开（`tab_app_access_policy_org_scope` 各行的 `org_id`，`include_children=1` 的展开子孙），按展开后的 org id 集合查 `tab_user_position`（`status <> -1000`）得到去重 `user_id` 集合 A；未配置组织范围时跳过这一步（不限定）。② 若配置了用户属性条件，对每一条条件按 `metadata_field_id` 取出 `column_name`，拼出 `tab_user.<column_name> = / <> / IN (...)` 查询得到该条件命中的 `user_id` 集合，多条属性条件之间取交集得到集合 B；未配置属性条件时跳过这一步。③ 命中用户 = 若①②都配置了，取 A∩B；只配置了其中一类，直接用那一类的结果（Decision 1 已保证至少配置一类，不会出现两者都跳过的情况）。④ 命中用户与该策略的目标应用集合（`tab_app_access_policy_target_app`）做笛卡尔积得到本次应产生的 `(user_id, app_id)` 全集；⑤ 事务内 `DELETE FROM tab_app_access_policy_grant WHERE policy_id=:id` 后批量插入新集合，同时更新策略的 `last_exec_time`/`last_exec_by`。选择整体重建而非"diff 出新增/失效行分别插入/删除"，因为策略每次执行的输入（条件×目标应用×当前用户数据）本身就是无状态的全量计算，diff 不会减少多少 SQL 开销却显著增加实现复杂度和出错面（时间换正确性简单性）。

策略的组织范围/目标应用被编辑后，不自动重新执行；管理端列表通过比较 `tab_app_access_policy_org_scope`/`tab_app_access_policy_target_app` 的 `update_time` 是否晚于该策略 `last_exec_time` 来展示"配置已变更，待重新执行"提示，纯查询判断，不新增状态字段。

### Decision 5：SSO 登录拦截点的接入方式

在 `sso/support/` 下新增一个只读校验组件（如 `AppAccessAuthorizationChecker`，与现有 `AppProtocolGuard` 同级、职责相邻），提供 `assertAuthorized(Long userId, Long appId)` 方法：执行 Decision 2 的合并判定 SQL，未授权时 `throw new SsoProtocolException(...)`（复用现有异常类型，不新增异常体系）。`CasController.login`、`OAuthController.authorize` 在拿到 `userId`（来自 `SsoSessionService.verify`）与 `appId`（路径变量/`client_id`）之后、调用 `casTicketService.issue(...)`/`oAuthTokenService.issueCode(...)` 之前各插入一行调用。

【实现后修正】未授权时的响应格式与现有"协议参数不匹配"场景（`service`/`redirect_uri` 白名单不匹配）**不一致**：`CasController`/`OAuthController` 里专门捕获 `assertAuthorized` 抛出的 `SsoProtocolException` 的分支单独处理，返回 HTTP 403（而不是协议参数校验错误统一使用的 400）+ 标准 `{code, message, data}` JSON 响应体（`code=403`，复用 `ProtocolResponseWriter.json` + 项目通用的 `Result.error(...)`），不是纯文本——这是用户在实现完成后明确要求的调整（先改成 JSON 格式，再改状态码/`code` 为 403），最终未沿用最初设想的"与协议参数校验错误完全一致的 400 文本响应"方案。协议参数校验错误（`service`/`redirect_uri` 不匹配）本身的响应格式不受影响，仍是 400 文本。

不做成 Spring `HandlerInterceptor`/AOP 切面的原因：只有两个调用点，且两处上下文（从哪里取 userId/appId）不完全一致（CAS 是路径变量 `appId` + session 校验结果；OAuth2 是 `client_id` 查 `tab_app_auth_config` 反查 `appId`），显式方法调用比切面更直观、改动面更小，符合"最小侵入"的要求。

## Risks / Trade-offs

- [风险] 策略执行是同步阻塞操作，若某条策略的组织范围极大（命中用户数很多），"执行"按钮点击后接口耗时可能较长 → 缓解：目标应用集合与组织范围在管理场景下通常有限（内部系统管理员数/组织规模可控），暂不做异步化；后续如遇到实际性能问题，可在不改变对外行为（仍是"点击执行→完成"的同步语义）的前提下改为内部异步+轮询，不影响本次的 spec。
- [权衡] 收回权限（新增 `DENY` 例外或禁用/删除策略）后，SSO 层面已经签发但尚在有效期内的票据/授权码/access token 不会被主动失效——见 Non-Goals 说明，属于本期明确接受的滞后窗口。
- [风险] 两张表的方案要求任何"最终权限"查询代码都必须同时查两张表并应用同一套合并规则，如果后续有新的调用方（如另一个模块也要判断某用户对某应用的权限）各自重复实现合并逻辑容易出现优先级判断不一致 → 缓解：合并判定逻辑封装成唯一一个 Service 方法（供管理端查询接口和 `AppAccessAuthorizationChecker` 共同调用），不允许调用方各自拼 SQL。
- [风险] 用户属性条件执行时按 `metadata_field_id` 现查 `column_name` 再拼接 `tab_user.<column_name>` 查询条件，属于"数据驱动的动态列名"写法，若 `tab_metadata_field` 目录本身允许非法字符的 `column_name`（正常情况下由"元数据字段管理"模块的既有校验保证只能是真实存在的表列名，不接受任意字符串）会有拼 SQL 风险 → 缓解（实际实现，见 `PolicyExecutionServiceImpl.resolveTrustedColumnName`）：在 Service 层（而非最初设想的 Mapper 层）对现查到的字段做双重校验后才允许拼入动态 SQL——① 字段的 `tableName` 必须等于硬编码常量 `tab_user`（拒绝其他表的字段被跨表引用）；② `columnName` 必须匹配硬编码正则 `^[a-zA-Z_][a-zA-Z0-9_]*$`（合法标识符白名单，而不是枚举一份具体的合法列名集合，也没有复用"元数据字段管理"模块的既有校验能力——该模块本身未暴露可复用的列名合法性校验方法）。两项校验均不通过时抛 `BusinessException` 直接拒绝执行，不做任何字符转义式的兜底拼接。
