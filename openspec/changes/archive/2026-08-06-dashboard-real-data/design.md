## Context

见 proposal.md - Why。技术约束（研究已有代码得到，proposal 未展开）：

- 后端 `IdentityAuthFilter`（`backend/.../auth/filter/IdentityAuthFilter.java`）对每个非白名单请求都要求携带合法的 `menu` 请求头（三段式资源编码格式）。对不在 `FIRST_LOGIN_WHITELIST` 里的路径，还会额外调用 `authorizationService.hasPermission(userId, menu)` 做运行时鉴权，不满足则 403；`FIRST_LOGIN_WHITELIST`（当前含 `/api/auth/password`、`/api/auth/permissions`）里的路径豁免这一步权限点校验，只要求 `menu` 头格式合法，语义是"自助操作，不受角色权限点约束"。
- 前端 axios 请求拦截器（`frontend/src/api/request.ts`）在调用方显式传入 `headers.menu` 时直接使用；未传入时才回退到 `router.currentRoute.value.meta.permissionKey`。`/dashboard` 路由没有 `permissionKey`，因此新接口调用必须显式传 `menu` 头，不能依赖路由回退。
- 各业务模块（`user`/`app`/`role`/`permission`）现有的 `getPage` 查询条件并不只是简单的"未删除"：例如 `UserServiceImpl.getPage` 还会叠加 `orgScopeService.resolveAllowedOrgIds` 的数据权限范围过滤、`AppServiceImpl.getPage` 同样叠加了组织范围过滤，这些条件是为"业务列表页只展示当前用户有权限管理的那部分数据"设计的，会随需求持续调整。首页统计要展示的是系统整体总数（`DashboardView.vue` 问候语已有"这是身份与权限体系的整体概览"的表述），两者语义不同，不应该共用同一段查询条件拼装逻辑。
- 四个实体的软删除都是同一种模式：`status` 字段等于常量 `XxxStatus.DELETED`（值均为 `-1000`）即视为已删除；`RoleMapper`/`PermissionMapper` 直接继承 `BaseMapper`，`selectCount(wrapper)` 可以直接用。
- `OperationLogQueryService.getPage(OperationLogQueryRequest)` 已经支持按 `createBy`（精确匹配）筛选、按操作发起时间降序排列，具备直接复用的条件；限制权限的是 `OperationLogController` 这一层（HTTP 路径 + `menu` 头），不是查询服务本身，因此"新增一个不做权限校验的入口"可以只加一层新 controller，内部调用同一个查询服务，不需要重写查询逻辑。
- 登录账号（`LoginRequest.account`，对应 `tab_user.code`）和操作日志的 `create_by` 字段理论上应该是同一个值域（都代表"操作人"），但当前 `OrgServiceImpl`/`UserServiceImpl`/`AppServiceImpl`/`RoleServiceImpl`/`PermissionServiceImpl` 里 `createBy`/`updateBy` 全部写死为常量 `DEFAULT_OPERATOR = "admin"`，尚未接入真实登录用户（这是本项目现存的、跨越全部业务模块的统一遗留占位，不是本次改动引入的问题，修复它需要单独立项，不在本 change 范围内）。这意味着"当前用户最近操作"在数据库层面实际过滤的是 `create_by = <当前账号的 code>`，对种子管理员账号（`code = admin`）能查到数据，对其他账号在这个遗留问题修复前会一直是空列表——这是可预期的、非本次改动导致的空态，不是 bug。

## Goals / Non-Goals

**Goals:**
- 首页概览页的统计卡片与最近操作时间线改为真实数据。
- 新增的统计接口自己独立定义统计口径（不复用/不依赖业务列表接口的查询条件拼装逻辑），业务列表接口以后调整筛选条件不影响这里的统计结果。
- 统计接口对所有已登录用户开放，不与任何业务模块的查看权限点绑定。

**Non-Goals:**
- 不实现统计数值的历史快照/环比计算能力（proposal 已声明本次直接去掉 delta 展示）。
- 不改变 `/api/users` 等既有业务列表接口的行为、鉴权规则或响应结构。
- 不做统计数字的数据权限范围（组织范围）过滤——本次统计口径就是全局总数，见 Decision 3。
- 不改变侧边导航菜单的权限过滤逻辑（`filterMenuGroups`）。

## Decisions

**新增独立的 `dashboard` 后端模块，统计接口自己直接对各实体的 Mapper 做 `selectCount`，不调用/不依赖 `UserService.getPage` 等业务列表方法。**
新建 `cn.nihility.rbac.dashboard` 包，遵循项目现有分层约定：`controller/DashboardController`（`GET /api/dashboard/stats`）→ `service/DashboardStatisticsService` + `impl/DashboardStatisticsServiceImpl`（不需要 `dto/entity/mapper/mapstruct` 子包，因为不持有自己的表，只读取其他模块已有的 `UserMapper`/`AppMapper`/`RoleMapper`/`PermissionMapper`）→ `dto/DashboardStatsVO`（`userCount`/`appCount`/`roleCount`/`permissionCount`，均为 `Long`）。`DashboardStatisticsServiceImpl` 直接注入这四个 Mapper，各自用 `new LambdaQueryWrapper<XxxEntity>().ne(XxxEntity::getStatus, XxxStatus.DELETED)` 做 `selectCount`，四条查询逻辑上互相独立、与各业务模块自己的 `getPage` 方法完全解耦——这正是 proposal 里"业务接口调整不影响统计"的落地方式：即使某天 `UserServiceImpl.getPage` 改了筛选条件甚至改成跨表 JOIN，`DashboardStatisticsServiceImpl` 里这行 `selectCount` 不会被牵连。
取舍：四条 `selectCount` 是简单直接的实现，如果未来统计维度变多（如需要跨表统计），可以在 `DashboardStatisticsServiceImpl` 内部演进为自定义 Mapper XML，不影响对外的 `DashboardStatsVO` 契约。

**统计接口豁免角色权限点校验，归入 `IdentityAuthFilter.FIRST_LOGIN_WHITELIST`。**
用户已确认：四个统计数字是登录后落地页的整体概览信息，不应要求账号必须拥有 `UserManagement:user:view` 等具体业务查看权限才能看到对应数字（这也避免了"部分角色因缺少某个查看权限导致对应卡片长期失败"的体验问题）。实现上把 `/api/dashboard/stats` 加入 `FIRST_LOGIN_WHITELIST` 常量列表，与 `/api/auth/password`、`/api/auth/permissions` 同类处理：仍需要合法的 `identity-token` + 格式合法的 `menu` 头（前端固定传 `Dashboard:stats:view`），但跳过 `authorizationService.hasPermission` 这一步。`Dashboard:stats:view` 不需要登记进 `权限资源.txt`——该文件只收录驱动菜单/按钮显隐的权限点，`Auth:permission:my`（`/api/auth/permissions` 现有用法）就是先例，同样没有登记在这份文件里。

**统计口径为系统全局总数，不做组织数据权限范围过滤。**
`UserServiceImpl.getPage`/`AppServiceImpl.getPage` 现有的组织范围过滤（`orgScopeService.resolveAllowedOrgIds`）是为业务列表页"只管理自己范围内的数据"设计的数据权限边界；首页统计一旦决定对所有登录用户开放、不与查看权限绑定，就不再适合再叠加"当前用户能看到哪些组织"的范围限制，否则不同组织范围的账号会看到不一致的总数，反而和"整体概览"的定位矛盾。这是本次的一个可感知取舍，记入 Risks。

**两路请求（统计接口 1 个 + 最近操作接口 1 个）各自独立发起、用 `Promise.allSettled` 而非 `Promise.all` 汇总。**
统计只有一个接口调用，最近操作也只有一个接口调用（不再是原来的 `/api/operation-logs`，而是下面 Decision 新增的当前用户专属接口），因此只有两路请求：统计、最近操作。二者在 `onMounted` 里用 `Promise.allSettled` 并发发起，任一路失败只影响对应区域（统计卡片整体 vs 时间线），不需要为四个统计卡片分别处理独立失败态（它们共享同一次请求结果）。

**新增"当前用户最近操作"接口，复用 `OperationLogQueryService.getPage`，同样归入 `FIRST_LOGIN_WHITELIST`。**
在 `DashboardController` 里新增 `GET /api/dashboard/recent-operations`：从 `CurrentUserContext.getUserId()` 拿到当前用户 id，查 `UserMapper.selectById` 取其 `code`（登录账号），构造 `OperationLogQueryRequest{ createBy = code, page = 1, pageSize = 4 }` 调用已有的 `OperationLogQueryService.getPage`，直接返回 `records`（`List<OperationLogVO>`）。不新增 `limit` 之类的可配置参数——首页固定只需要"最近 4 条"，没有第二个调用方需要不同的条数，暂不引入用不到的灵活性（YAGNI）。同 `/api/dashboard/stats` 一样加入 `FIRST_LOGIN_WHITELIST`，`menu` 头固定传 `Dashboard:recentOperations:view`。
取舍：直接复用查询服务而不是另起一套独立的 SQL，好处是查询逻辑（`operationTypeLabel` 填充、时间排序等）只维护一份；代价是"当前用户最近操作"和"操作日志管理页面的分页查询"共享同一条查询路径，如果以后 `OperationLogQueryService.getPage` 的查询逻辑发生变化，两处都会受影响——这与"统计接口要和业务列表接口解耦"的诉求不同：那里解耦是因为统计口径需要独立于会持续演进的业务筛选条件；这里"当前用户最近操作"本质上就是"操作日志"的一个特例视图（只是换了个不需要权限点、按人过滤的入口），复用同一条查询语义是合理的，不属于同一类风险。

**加载态用简单的布尔标志位（`statsLoading`/`activityLoading`）而非骨架屏组件库。**
现有代码库看不到统一的骨架屏封装，引入一个骨架屏组件超出本次改动范围；用 Element Plus 已有的 `v-loading` 指令或简单的"加载中"文案即可满足"加载期间不展示误导性占位数据"的 spec 要求，视觉细节由 vue3-frontend-dev 在实现时按现有设计令牌（`variables.scss`）调整。

## Risks / Trade-offs

- [统计数字对所有已登录用户可见、且是全局总数而非该账号数据权限范围内的总数] → 已与用户确认为预期行为：首页概览定位就是系统整体信息，不是"我能管理的范围"；如果后续需要按组织范围区分统计口径，应作为单独的 change 提出，而不是默认叠加现有的数据权限范围逻辑。
- [新增两个不做权限点校验的接口，理论上扩大了"无需业务权限即可获取的信息"范围] → 统计接口只返回四个聚合计数；最近操作接口只返回**当前用户自己**的操作记录（`create_by` 精确匹配当前账号），不会让账号 A 看到账号 B 的操作记录，信息粒度和 `/api/auth/permissions` 返回"当前用户权限编码集合"类似，均属于低敏感度的自助信息，风险可接受。
- [`create_by` 字段目前系统性地写死为 `"admin"`（见 Context），"当前用户最近操作"对种子管理员账号之外的账号会一直是空列表，直到那个遗留问题被单独修复] → 这是已知的、跨越全部业务模块的现存限制，不是本次改动引入的缺陷；本次改动的职责是"正确定义当前用户最近操作的过滤条件"（按账号 `code` 过滤），而不是修复 `createBy` 的写入逻辑，后者影响面覆盖 org/user/app/role/permission 等全部模块，应作为独立 change 处理。
- [`Promise.allSettled` 对不支持的极旧浏览器不兼容] → 项目目标环境是现代浏览器，无需 polyfill。
