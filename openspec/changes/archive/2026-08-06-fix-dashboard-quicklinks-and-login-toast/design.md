## Context

`request.ts` 的响应拦截器已经是全项目统一的错误提示出口：非 0 业务码（401/4010/403 除外）会在拦截器内 `ElMessage.error(body.message || '请求失败')` 后 `reject`；网络层错误（HTTP 层失败）也在拦截器里统一 `ElMessage.error`。`LoginView.vue` 的 `handleSubmit` 在 `catch` 块里又对同一个被 reject 的 error 调用了一次 `ElMessage.error((error as Error).message)`，导致登录失败时同一条文案弹两次。这违反了 `request.ts` 里已经写明的约定（"调用方不用重复写错误提示"）。

首页 `DashboardView.vue` 的"快速入口"直接引用 `MENU_GROUPS` 全量渲染，未经过任何权限过滤；而侧边栏 `SideNav.vue` 已经用 `filterMenuGroups(MENU_GROUPS, hasPermission)`（`router/menu.ts` 导出，`usePermission` composable 提供 `hasPermission`）实现了同样场景下的过滤，二者理应共享同一套过滤逻辑，不应该各自维护一份不一致的展示规则。

首页统计卡片当前的四个维度（用户/应用/角色/权限点）由 `DashboardStatsVO`（后端）驱动，`DashboardStatisticsServiceImpl` 分别对 `UserMapper`/`AppMapper`/`RoleMapper`/`PermissionMapper` 做 `selectCount` 并排除各自的 `DELETED` 状态。产品侧希望把"角色数量、权限点"换成"组织总数、管理员总数"，这两个维度分别有独立的 `OrgEntity`/`OrgMapper`/`OrgStatus`（`org` 包）与 `AdminEntity`/`AdminMapper`/`AdminStatus`（`admin` 包），字段结构与状态语义（`DELETED = -1000`）都和现有四个维度一致，可以用完全相同的方式接入。

## Goals / Non-Goals

**Goals:**
- 登录失败只弹一次错误提示。
- 首页快速入口与侧边栏保持一致的权限可见性：无权限的分组/子菜单不出现。
- 首页统计卡片展示"组织总数、身份总数、接入应用、管理员总数"四个维度的系统全局真实总数，统计口径（排除已删除、不做组织数据权限范围过滤）与现状保持一致。

**Non-Goals:**
- 不改动 `request.ts` 拦截器的错误处理逻辑本身（它是唯一提示来源，行为已符合预期）。
- 不改动 `filterMenuGroups`/`usePermission`/权限编码加载逻辑，仅在 `DashboardView.vue` 里复用。
- 不改动登录失败的后端业务逻辑或错误码含义。
- 不改动"最近操作"时间线的展示逻辑与数据来源。
- 不引入新的数据库表或字段（`tab_org`/`tab_admin` 均已存在）。

## Decisions

- **登录错误提示去重**：移除 `LoginView.vue` `catch` 块里的 `ElMessage.error` 调用，只保留 `submitting.value = false` 的收尾逻辑；错误提示完全交给 `request.ts` 拦截器展示。
  - 备选方案：给 `request.ts` 加一个"本次调用方自行处理提示"的选项（如自定义 header/config），登录页显式 opt-out 拦截器提示、自己展示。
  - 未采用原因：项目里所有其他调用方都依赖拦截器统一提示（无一例外），登录页没有特殊到需要自定义文案或展示方式的理由，引入 opt-out 机制是过度设计。

- **首页快速入口权限过滤**：`DashboardView.vue` 的 `quickLinks` 改为 `computed(() => filterMenuGroups(MENU_GROUPS, hasPermission))`，`hasPermission` 从 `usePermission()` 取得，与 `SideNav.vue` 完全同构。
  - 备选方案：在 `DashboardView.vue` 里单独写一套过滤逻辑，或把"快速入口"数据源改为服务端下发。
  - 未采用原因：`filterMenuGroups` 已是现成的、经过验证的过滤函数，快速入口本质上就是"侧边栏菜单的另一种展示形态"，复用能保证两处权限判断永远一致，不会出现改了一处忘了另一处的漂移。

- **统计卡片维度替换**：`DashboardStatsVO` 移除 `roleCount`/`permissionCount` 字段，新增 `orgCount`/`adminCount` 字段（`userCount`/`appCount` 不变）；`DashboardStatisticsServiceImpl.getStats()` 里对应的两行 `selectCount` 改为查询 `OrgMapper`（过滤 `OrgStatus.DELETED`）与 `AdminMapper`（过滤 `AdminStatus.DELETED`），写法与现有 `userMapper`/`appMapper` 两行完全一致（`LambdaQueryWrapper.ne(Entity::getStatus, XxxStatus.DELETED)`）。前端 `DashboardStats` 类型与 `DashboardView.vue` 的 `statsMeta` 同步改名/换序为 组织总数（`orgCount`）、身份总数（`userCount`）、接入应用（`appCount`）、管理员总数（`adminCount`），图标分别用 `OfficeBuilding`、`UserFilled`（不变）、`Grid`（不变）、`Avatar`（`@element-plus/icons-vue` 均已内置这两个新图标，无需新增依赖）。
  - 备选方案：保留 `roleCount`/`permissionCount`，新增两个字段变成六卡片；或把统计维度做成可配置。
  - 未采用原因：用户明确要求"改为"四个新维度而非新增卡片，产品需求就是四选四替换，不需要更复杂的可配置机制。

## Risks / Trade-offs

- [过滤后某个用户所有分组的子菜单都被过滤掉，快速入口区域可能完全空白] → `filterMenuGroups` 已内置"一级分组下子菜单全部被过滤则该分组本身不出现"的行为；快速入口区域在这种情况下会展示为空的 `quick-grid`（无卡片），属于预期行为，不在本次改动范围内额外处理空态提示（现有 UI 未对此设计空态文案，交由后续独立评估）。
- [`DashboardStatsVO` 字段变更是接口响应体的破坏性变更] → 该接口只服务于首页概览页这一个前端消费方（非通用列表/详情接口，不被其他模块复用），前后端在同一个 change 内一并发布，不存在独立的外部消费方需要兼容，风险可控。
