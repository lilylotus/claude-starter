# Design

## Context

见 proposal.md - Why/What Changes。关键既有事实：

- 运行时 RBAC 判定在 `auth/filter/IdentityAuthFilter.java` 完成：先做 `identity-token`/
  `menu` 请求头格式校验、首登改密拦截，再调用 `resolveRequiredPermission(method, path,
  menuHeader)` 得到本次请求"实际应校验的权限编码"（命中 `FIXED_PERMISSION_MAPPINGS` 时
  取映射表固定编码，忽略客户端 `menu` 头；未命中时直接用 `menu` 头值），最后调用
  `AuthorizationService.hasPermission(userId, requiredPermission)`，不通过则直接拒绝
  （`AuthErrorCode.FORBIDDEN`）。`FULL_WHITELIST`/`FIRST_LOGIN_WHITELIST`
  （`firstLoginExempt`）两类路径豁免这一步权限点校验。
- 项目里已有三套"按用户属性匹配用户"的独立实现，均复用"元数据字段管理"
  （`tab_metadata_field`，`biz_type`+`table_name`+`column_name`+启用状态）而不是另起一套
  属性元数据：`appaccess/policy`（面向应用访问，`PolicyUserAttrEntity`/
  `PolicyAttrOperator`，批量计算命中用户集合）、`userrole`（面向自动授予角色，
  `UserMatchConditionResolver`，同样批量计算）。两者都遵循同一种安全模式：取
  `metadata_field_id` 对应的 `MetadataFieldEntity`，三重校验（`status=ENABLED`、
  `bizType` 落在允许域内、`tableName` 与域匹配），再用列名白名单正则 + 动态 SQL 拼接
  把属性比较下推到数据库查询。这两套都是**批量/离线**计算（策略执行、角色规则执行），
  不是逐请求实时判定；本次新增的 ABAC 判定是**逐请求实时判定**（单个 userId + 当前
  时间 + 当前请求的资源编码），场景不同，复用它们的 Service 没有意义，但复用同一种
  校验与防注入模式是合理的、项目已确立的约定。
- MySQL 5.7 环境，手写 SQL 禁止窗口函数/CTE 等新版本特性（见项目 CLAUDE.md 后端约定）。

## Goals / Non-Goals

**Goals:**
- 新增独立顶级模块 `abac`，在 `IdentityAuthFilter` 现有 RBAC 判定通过之后，对命中了
  ABAC 策略的资源编码追加一次基于用户属性 + 时间窗口的实时限制判定。
- 对完全没有配置任何启用中 ABAC 策略的资源编码，行为与改动前完全一致（零侵入）。
- 管理端提供策略的增删改查、启用停用，复用"元数据字段管理"的 USER 域字段作为属性
  条件来源。

**Non-Goals:**
- 不支持"白名单式"的 `ALLOW` 效果——本次只实现限制模式：策略命中即拒绝，未命中任意
  策略即维持 RBAC 已放行的结果（已与用户确认，见下方 Decision 1）。
- 不支持资源实例级/行级属性比较（例如"只能编辑归属部门与本人相同的那一条记录"）。
  本次 ABAC 只做"接口/菜单/按钮入口级"的通过/拒绝判定，判定所需的"资源"维度是静态的
  权限编码（见 Decision 2），不解析请求体/路径参数里具体业务记录的字段值；这类行级
  数据权限继续由各业务 Service 自行按归属过滤（或是未来一个独立 change 的范围）。
- 不改造 `appaccess/policy`、`userrole` 现有代码，不与它们共享表或 Service。
- 不引入新的用户属性元数据体系，直接复用"元数据字段管理"已有的 USER 域字段。

## Decisions

### Decision 1：策略效果模型——仅限制（DENY-only），不支持 ALLOW

策略只有一种语义：命中即拒绝。多条候选策略之间用"任意一条命中即拒绝"的 OR 语义合并；
一条都不命中时放行（维持 RBAC 判定结果）；某资源编码完全没有绑定任何启用中策略时，
不受 ABAC 治理，直接放行。

**理由**：定位是"RBAC 已经放行，ABAC 在此基础上进一步收紧"，天然是限制语义。若同时
支持 `ALLOW`，资源一旦绑定了任意 `ALLOW` 策略就必须有人命中才能访问，配置失误会导致
该资源对所有人瞬间不可访问，风控风险高、心智负担重。已向用户确认选用仅限制模式
（放弃 `ALLOW`+`DENY` 双效果方案）。

**后续如需支持白名单式场景**：留作独立 change 评估，不在本次范围内，不在此处预留
"effect" 字段占位（YAGNI，待真实需求出现再加字段/迁移）。

### Decision 2："资源"维度 = 运行时权限编码，精确匹配

策略绑定一个或多个资源编码，取值与 `IdentityAuthFilter.resolveRequiredPermission()`
最终解析出的权限编码（三段式 `模块:资源:操作`）口径完全一致——即 ABAC 判定复用 RBAC
判定已经拿到的同一个 `requiredPermission` 字符串做精确匹配，不重新解析 HTTP 方法/
路径，也不支持通配符。

**理由**：
- 保证两层判定对"这次请求要保护的是什么资源"有同一个、不可被客户端伪造的认识（直接
  复用 `resolveRequiredPermission` 的返回值，天然继承它已经做好的"命中固定映射表时
  忽略客户端 `menu` 头"的防伪造能力）。
- 与 `appaccess` 目标应用、`userrole` 规则一样采用精确 id/编码绑定，是项目既有约定，
  不引入通配符匹配的额外复杂度和歧义（"模块:资源:*" 是否应该匹配同模块任意操作需要
  额外的产品决策，本次不展开）。

**备选方案**：直接用 HTTP 方法+路径做资源维度——被放弃，因为会与 RBAC 的权限编码
维度脱节，管理员需要同时理解两套资源标识，心智成本更高，且路径里的 Ant 风格通配符
语义与权限编码的语义不是一一对应的。

### Decision 3：用户属性条件——独立实现，复用元数据字段 + 相同的校验/防注入模式

新增 `AbacPolicyUserAttrEntity`（`tab_abac_policy_user_attr`：`policy_id`、
`metadata_field_id`、`operator`、`attr_value`），运算符复用同一套语义
（`EQ`/`NE`/`IN`，`abac` 模块内独立声明 `AbacAttrOperator` 常量类，不引用
`appaccess.policy.constant.PolicyAttrOperator`/`userrole.constant.UserRoleAttrOperator`）。
解析侧新增 `AbacUserAttrEvaluator`（`abac.support` 包下，不依赖
`UserMatchConditionResolver`），对单个 `userId` + 单条属性条件做校验：

1. 复用 `metadata` 模块的 `MetadataFieldMapper` 按 id 查询字段，三重校验：
   `status=ENABLED`、`bizType=USER`（本次不支持 `POSITION` 域，范围小于 `userrole`，
   因为运行时判定没有"当前任职记录"这个概念，只有当前登录用户本身）、`tableName=
   tab_user`，并用既有的列名白名单正则二次校验 `columnName`。
2. 直接注入 `user` 模块的 `UserMapper`，新增一个按 `userId` + 动态列名 + 运算符的
   单用户属性匹配查询（返回布尔），与 `UserMapper.selectIdsByAttrCondition` 的批量
   查询并存但用途不同（本次是"这一个用户是否满足"，不是"哪些用户满足"）。
3. 同一策略内多条属性条件之间取 AND（全部满足才算该策略的用户属性维度满足），与
   `appaccess`/`userrole` 的"交集"语义保持一致的使用体验。

**理由**：三处用户属性匹配实现（`appaccess`、`userrole`、本次 `abac`）彼此独立但
共享同一种"元数据字段 + 列名白名单 + 动态 SQL"模式，是项目已经确立的权衡——用真实
列而不是 EAV 表换取查询性能与简单性，代价是每个场景各自写一遍校验代码；延续这个
已验证的模式而不是强行抽出共享组件（现在只有三处用法、且两处是批量场景一处是实时
场景，抽象时机未到，过早抽象的维护成本更高）。

### Decision 4：环境属性条件——时间窗口，多条 OR 合并

新增 `tab_abac_policy_time_window`（`policy_id`、可选 `day_of_week`
[1=周一...7=周日，`NULL` 表示每天]、`start_time`、`end_time`，均 `TIME` 类型）。
同一策略下多条时间窗口之间取 OR（命中任意一条即视为该策略的环境维度满足）；该策略
未配置任何时间窗口时，视为环境维度不限制（恒满足）。判定使用应用服务器当前时间与
本地时区，不做用户时区换算（与项目现有时间处理方式一致，不引入新的时区层）。

### Decision 5：策略整体匹配与判定流程

一条策略在某次请求下"命中"，定义为：该策略启用 AND 资源编码命中（Decision 2）
AND 用户属性条件全部满足（Decision 3，未配置属性条件时该维度恒满足）AND 时间窗口
至少一条满足（Decision 4，未配置时间窗口时该维度恒满足）。

保存时校验：资源编码集合非空（至少一个）且用户属性条件、时间窗口至少配置一类
非空——不允许保存"零条件、仅凭资源编码绑定就拒绝一切请求"的策略（这类"整体停用
某资源"的需求应通过停用对应权限点或调整角色授权实现，不属于 ABAC 的职责）。

判定流程（`AbacAuthorizationService.isDenied(userId, resourceCode)`，供
`IdentityAuthFilter` 在 RBAC 判定通过后调用）：

1. 查询资源编码命中、状态为启用的候选策略；候选为空 → 不拒绝（直接返回，不做任何
   属性/时间查询，保证未配置 ABAC 的资源零额外开销）。
2. 逐条候选按 Decision 3/4 判断是否命中；存在至少一条命中 → 拒绝。
3. 全部候选均未命中 → 不拒绝。

每次请求实时查询，不引入缓存、不做批量物化授权记录（区别于 `appaccess` 的"执行"+
整体重建模式）——与现有 `AuthorizationServiceImpl`"不引入缓存，每次请求实时查库"的
既定取舍保持一致，优先保证正确性，后续如有性能问题再单独评估缓存策略。

`IdentityAuthFilter` 改动点：在 `!firstLoginExempt &&
!authorizationService.hasPermission(...)` 判定通过（即 RBAC 放行）之后，同样仅在
`!firstLoginExempt` 时追加一次 `abacAuthorizationService.isDenied(userId,
requiredPermission)` 判断，为真则按与 RBAC 拒绝同样的方式返回 `AuthErrorCode.
FORBIDDEN`/"无权限访问该资源"（复用同一错误码与提示文案，客户端无需区分两层拒绝
来源）。`FULL_WHITELIST`/`firstLoginExempt` 路径不受 ABAC 约束，与它们本就不受 RBAC
权限点约束的既有语义一致。

### Decision 6：管理端菜单与权限点

新增二级菜单"ABAC 策略管理"，路由 `/permission/abac-policy`，权限模块前缀
`AbacManagement`：`AbacManagement:policy:view`/`add`/`edit`/`enable`/`disable`/
`delete`，命名与 `AppAccessManagement` 系列保持同一风格。页面是单页列表+弹窗
（不需要 `appaccess` 那样的多 tab 布局——没有"人工例外""最终生效权限查询"这类
附加板块），新增/编辑弹窗内配置：名称、备注、启用状态、显示序号（仅用于列表展示
排序，不参与判定优先级——Decision 1 的 OR 语义不需要优先级）、目标资源编码（多选，
从当前系统权限点列表中选取——实际调用权限点管理页面复用的 `GET /api/permissions/
list` 全量列表接口，前端按返回的 `status` 字段过滤只展示当前启用的权限点，不展示
已停用的权限点，避免管理员把策略绑定到一个当前不可访问的资源编码上）、用户属性
条件（复用元数据字段选择器组件）、时间窗口（星期+起止时间，可多条）。

## Risks / Trade-offs

- **[风险] 每次请求新增一次 DB 查询** → **缓解**：候选策略为空时立即短路返回，对
  未配置 ABAC 的资源（预期是绝大多数现有接口）零额外查询；与现有 RBAC 判定同样
  "不引入缓存、实时查库"的取舍保持一致，不在本次引入缓存层增加复杂度。
- **[风险] 仅支持限制语义，无法表达"只允许 XX"的白名单场景** → **缓解**：已与用户
  确认的产品取舍；白名单场景可退化为"先用角色/权限点整体收窄访问面，再用 ABAC 排除
  不该访问的子集"，多数场景可组合覆盖；真正需要白名单语义时另开 change 评估。
- **[风险] 不支持资源实例级属性（行级数据权限）可能无法满足"只能编辑本部门数据"
  这类更细粒度的期望** → **缓解**：已在 Non-Goals 中明确范围边界，proposal.md
  Why 部分列举的例子里，"工作日时间窗"属于本次范围，"只能编辑本部门数据"这类行级
  需求需要业务 Service 自行按归属过滤，不属于本次入口级判定能解决的问题，会在
  管理端文案/帮助说明中向使用者说明边界，避免误用预期。
- **[风险] 策略保存校验（资源编码非空 + 至少一类条件非空）可能挡住"临时整体封禁
  某资源"的运维需求** → **缓解**：这类需求的正确入口是停用对应权限点/角色授权，
  ABAC 故意不提供这个能力，避免两套机制功能重叠、运维时判断成本增加。

## Migration Plan

- 新增 Flyway 迁移脚本建 4 张表（`tab_abac_policy`、`tab_abac_policy_resource`、
  `tab_abac_policy_user_attr`、`tab_abac_policy_time_window`，均含统一的创建人/
  创建时间/更新人/更新时间字段）+ 种子数据（新增菜单项与 `AbacManagement:*` 权限点，
  参照既有迁移脚本里新增菜单/权限点种子数据的写法）；实际落地的脚本为
  `V9__add_abac_policy.sql`（写本设计时 `db/migration` 下最新为 `V8__add_image.sql`，
  实现阶段开工前确认后顺接为 `V9`，未与并行开发产生冲突）。
- 回滚：改动集中在新增的 `abac` 模块代码、一个新迁移脚本、`IdentityAuthFilter` 里
  追加的一次方法调用；回退时移除该调用、删除新迁移脚本涉及的表与种子数据即可，不
  影响既有 RBAC 判定路径与其他模块。
