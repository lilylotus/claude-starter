## Why

三个首页/登录相关的体验缺陷与调整需要一并处理：登录失败时同一条错误提示会重复弹出两次，干扰用户；首页"快速入口"未按当前登录用户的权限过滤，会展示用户实际无权访问的功能入口，与本项目"看不到就进不去"的权限可见性原则（`permission-driven-visibility`）不一致；首页统计卡片当前展示的"角色数量、权限点"两个维度对管理员日常概览的参考价值不如"组织总数、管理员总数"，需要调整统计维度。

## What Changes

- 登录失败（账号密码错误、网络异常等）时，`LoginView.vue` 不再自行调用 `ElMessage.error` 重复提示——错误提示统一由 `request.ts` 响应拦截器负责展示一次。
- 首页"快速入口"卡片改为按当前登录用户的权限编码集合过滤后展示：复用 `router/menu.ts` 中已有的 `filterMenuGroups` + `usePermission().hasPermission`（与侧边栏 `SideNav.vue` 一致的过滤逻辑），无权限的分组/子菜单不再出现在快速入口里；若过滤后一级分组下没有任何可访问的子菜单，则该分组本身也不展示。
- 首页统计卡片四个维度由"身份总数、接入应用、角色数量、权限点"调整为"组织总数、身份总数、接入应用、管理员总数"：后端 `DashboardStatsVO` 用 `orgCount`/`adminCount` 替换 `roleCount`/`permissionCount`，`userCount`/`appCount` 保持不变；口径与现有统计一致——均为排除已逻辑删除记录后的系统全局总数，不做组织数据权限范围过滤。**BREAKING**（`GET /api/dashboard/stats` 响应体字段变化：移除 `roleCount`/`permissionCount`，新增 `orgCount`/`adminCount`）。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `dashboard-overview`: 新增"快速入口按当前用户权限过滤展示"的需求（此前该能力的 spec 未覆盖快速入口区域的行为）；修改"统计卡片展示真实总数"需求，统计维度由 用户/应用/角色/权限点 调整为 组织/用户/应用/管理员。

## Impact

- `frontend/src/views/login/LoginView.vue`：移除 `catch` 块中重复的 `ElMessage.error` 调用。
- `frontend/src/views/dashboard/DashboardView.vue`：`quickLinks` 改为基于 `filterMenuGroups(MENU_GROUPS, hasPermission)` 计算，而非直接使用 `MENU_GROUPS`；`statsMeta` 四个维度的 key/label/icon 调整为 组织总数/身份总数/接入应用/管理员总数。
- `frontend/src/types/dashboard.ts`：`DashboardStats` 接口字段由 `{ userCount, appCount, roleCount, permissionCount }` 改为 `{ orgCount, userCount, appCount, adminCount }`。
- `backend/.../dashboard/dto/DashboardStatsVO.java`：字段由 `roleCount`/`permissionCount` 改为 `orgCount`/`adminCount`。
- `backend/.../dashboard/service/impl/DashboardStatisticsServiceImpl.java`：统计来源由 `RoleMapper`/`PermissionMapper` 改为 `OrgMapper`/`AdminMapper`，过滤条件对齐各自的 `DELETED` 状态常量。
- `backend/.../dashboard/controller/DashboardController.java`：接口 Swagger 描述文案同步更新。
- 无数据库结构变更（`tab_org`/`tab_admin` 表已存在）。
