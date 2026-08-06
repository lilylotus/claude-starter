## Context

`org-scope-data-permission` 能力已经给组织树/列表、任职列表、应用列表、用户列表四类查询接口都装上了"按当前登录管理员的管辖组织范围（`tab_admin_org_scope`）收紧结果"的逻辑，统一走 `OrgScopeService.resolveAllowedOrgIds(CurrentUserContext.getUserId())`：返回空 `Optional` 表示不受限制（无管理员身份或未配置管辖范围），返回非空 `Optional<Set<Long>>` 表示受限，`Set` 是已展开 `include_children` 子孙的允许组织 id 全集。

首页概览统计接口 `GET /api/dashboard/stats` 当初（`dashboard-real-data` change）刻意没有接入这套过滤——`design.md` 的 Decision 3 明确写了"统计口径为系统全局总数，不做组织数据权限范围过滤"，理由是接口对所有登录账号开放、不与查看权限绑定，怕不同管辖范围的账号看到不一致的总数、违背"整体概览"定位。现在产品决定反转这个取舍：管辖范围不同的管理员应该看到与自己管辖范围一致的统计数字，与列表页体验对齐，"整体概览"改为"我管辖范围内的整体概览"。

四个统计维度里，组织（`tab_org`）、应用（`tab_app`，有直接的 `orgId` 列）已经有现成的按组织过滤方式；用户（`tab_user`）、管理员（`tab_admin`）都没有到组织的直接外键，只能像 `UserServiceImpl.getPage`/`UserMapper.xml#selectUserPage` 那样，通过 `tab_user_position`（用户的任职记录，含 `orgId`）用 `EXISTS` 子查询判断"是否存在至少一条落在管辖范围内的任职记录"。管理员的组织归属沿用同一条链路——`tab_admin.user_id` 关联 `tab_user.id`，再关联 `tab_user_position`，因为管理员本质上也是一个用户，其组织归属看的是这个用户自己的任职记录，而不是 `tab_admin_org_scope`（那是"该管理员管辖哪些组织"，是另一件事，不能混用）。

## Goals / Non-Goals

**Goals:**
- `GET /api/dashboard/stats` 未配置管辖组织范围时行为完全不变（系统全局总数）。
- 配置了管辖组织范围时，四个统计维度都收紧到范围内的真实总数，口径与对应业务列表页（组织树、应用列表、用户列表）的"范围内能查到多少条"保持一致。
- 接口豁免查看权限点校验的现状不变，只改数据口径，不改访问门槛。

**Non-Goals:**
- 不改动 `OrgScopeService`/`AdminOrgScopeMapper`/`OrgDescendantExpander` 等已有的管辖组织范围解析基础设施。
- 不给"管理员管理"业务列表页（`AdminServiceImpl.getPage`）本身加管辖组织范围过滤——那是一个独立的、更大范围的能力改动（决定管理员列表该不该受限，涉及"看不到自己管辖范围外的管理员"这类新语义），本次只解决首页统计数字的口径问题，只新增一个专用于计数的 Mapper 方法。
- 不改动前端：`DashboardStatsVO`/`DashboardStats` 的字段结构不变，纯粹是后端返回的数值口径变化。

## Decisions

**`DashboardStatisticsServiceImpl` 直接注入 `OrgScopeService`，`getStats()` 内部按"受限/不受限"分两条路径，而不是让四个子查询各自独立判断。**
先统一调用一次 `orgScopeService.resolveAllowedOrgIds(CurrentUserContext.getUserId())` 解析出 `Optional<Set<Long>>`，不受限时四条 `selectCount` 保持现状（`ne(status, DELETED)`，不带组织过滤）；受限时四条查询各自追加组织范围过滤条件。这样只解析一次管辖范围，而不是四个维度各自重复调用 `resolveAllowedOrgIds`（该方法每次都实时查库、不缓存），避免一次请求里对 `tab_admin_org_scope`/`tab_org` 发起四次重复查询。

**组织、应用维度复用现有 `OrgMapper`/`AppMapper` 的 `selectCount` + `LambdaQueryWrapper`，追加 `.in(id/orgId, allowed)`；用户、管理员维度各新增一个专用的计数 Mapper 方法，SQL 写在对应的 XML 里。**
- 组织：`orgMapper.selectCount(new LambdaQueryWrapper<OrgEntity>().ne(status, DELETED).in(OrgEntity::getId, allowed))`。
- 应用：`appMapper.selectCount(new LambdaQueryWrapper<AppEntity>().ne(status, DELETED).in(AppEntity::getOrgId, allowed))`，与 `AppServiceImpl.getPage` 现有写法一致。
- 用户：`UserMapper` 新增 `int countUsersInScope(@Param("allowedOrgIds") Set<Long> allowedOrgIds, @Param("deletedStatus") int deletedStatus, @Param("positionDeletedStatus") int positionDeletedStatus)`，SQL 是 `selectUserPage` 里已有的 `EXISTS (SELECT 1 FROM tab_user_position up WHERE up.user_id = u.id AND up.status != #{positionDeletedStatus} AND up.org_id IN (...))` 部分单独抽成一条 `SELECT COUNT(*) FROM tab_user u WHERE u.status != #{deletedStatus} AND EXISTS (...)`，不复用/不改造 `selectUserPage`（分页方法要兼顾模糊搜索参数和排序，硬塞一个"只算总数"的调用方式会让方法语义变复杂）。
- 管理员：`AdminMapper` 新增 `int countAdminsInScope(@Param("allowedOrgIds") Set<Long> allowedOrgIds, @Param("deletedStatus") int deletedStatus, @Param("positionDeletedStatus") int positionDeletedStatus)`，SQL 结构与用户计数几乎一样，只是主表换成 `tab_admin a`，`EXISTS` 子查询里 `up.user_id = a.user_id`。
- 备选方案：用一条更通用的"任意实体按 userId 关联任职范围计数"的公共 SQL/Mapper，用 `${tableName}` 之类的方式复用。未采用原因：项目约定动态拼表名/列名有 SQL 注入面（`countByColumnValue` 的注释已经强调"只接受白名单列名"），为了省两条几乎一样的 SQL 引入这种风险不划算，两条独立、静态、可读的 SQL 更符合仓库现有风格。

**允许集合为空集合时，四个维度统一使用"哨兵条件"兜底，而不是让 MyBatis-Plus 对空 `.in()` 的默认行为决定结果。**
组织、应用维度用 `LambdaQueryWrapper.eq(Entity::getId, -1L)`（复用 `AppServiceImpl.getPage` 已有写法）；用户、管理员维度在 XML 里对空集合分支用 `AND up.org_id = -1` 兜底（`selectUserPage` 已有的同款写法），保证"配置了管辖范围但范围解析出来是空集合"这种边界情况下统计数字是 0 而不是全量或 SQL 报错。

## Risks / Trade-offs

- [不同管辖组织范围的管理员登录后会在首页看到不同的统计数字，"整体概览"从系统级概览变成"我管辖范围内"的概览] → 这正是本次变更的产品意图，不是缺陷；`dashboard-overview` 主 spec 的 Purpose/Requirement 文案需要同步更新，避免和 `dashboard-real-data` 时期的旧描述冲突。
- [管理员的组织归属查的是 `tab_admin.user_id` 关联的用户任职记录，如果一个管理员账号本身没有任何任职记录（`tab_user_position` 里没有该用户的任何行），受限时该管理员会被排除在"管理员总数"之外，即使这个管理员本身在管辖范围的组织里工作] → 这与 `UserServiceImpl.getPage` 现有对普通用户列表的处理方式完全一致（没有任职记录的用户在受限时同样查不到），口径统一，不额外特殊处理管理员维度，避免用户和管理员两个维度出现不一致的边界行为。
- [`getStats()` 从"四条互相独立、任何一条失败不影响其他"退化为"先解析一次管辖范围，再决定四条查询各自的过滤条件"，如果 `resolveAllowedOrgIds` 抛异常会导致四个维度全部失败而不是各自独立失败] → 可接受：`resolveAllowedOrgIds` 本身只是简单的 Mapper 查询 + 内存展开，历史上在其余四个已接入的业务列表页里从未出现过独立失败的场景，不需要为这一次改动引入额外的异常隔离逻辑。
