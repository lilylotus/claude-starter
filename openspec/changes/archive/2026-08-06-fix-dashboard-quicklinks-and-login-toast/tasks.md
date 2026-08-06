## 1. 登录错误提示去重

- [x] 1.1 `frontend/src/views/login/LoginView.vue`：移除 `handleSubmit` 的 `catch` 块中 `ElMessage.error((error as Error).message)` 调用，`catch` 块只保留必要的收尾（`finally` 里的 `submitting.value = false` 不变，`catch` 可留空或仅做必要处理，不再重复弹提示）。
- [ ] 1.2 手动验证：使用错误密码登录，确认错误提示只弹出一次；使用正确密码登录，确认正常跳转不受影响。（浏览器自动化工具当前无法连接本地开发服务器，未完成人工点击验证，代码改动已过 `npm run build` 类型检查）

## 2. 首页快速入口按权限过滤

- [x] 2.1 `frontend/src/views/dashboard/DashboardView.vue`：引入 `filterMenuGroups`（来自 `@/router/menu`）与 `usePermission`（来自现有 composable），将 `quickLinks` 从 `computed(() => MENU_GROUPS)` 改为 `computed(() => filterMenuGroups(MENU_GROUPS, hasPermission))`。
- [ ] 2.2 手动验证：分别用拥有全部权限的账号和权限受限的账号登录，确认快速入口展示的分组/卡片与侧边栏 `SideNav.vue` 展示的菜单项权限口径一致（同一账号下两处应展示相同的可见菜单集合）。（同上，未完成人工点击验证）

## 3. 首页统计卡片维度调整（后端）

- [x] 3.1 `backend/.../dashboard/dto/DashboardStatsVO.java`：移除 `roleCount`/`permissionCount` 字段，新增 `orgCount`/`adminCount` 字段（`Long`，`@Schema` 描述同步更新），`userCount`/`appCount` 保持不变。
- [x] 3.2 `backend/.../dashboard/service/impl/DashboardStatisticsServiceImpl.java`：注入 `OrgMapper`/`AdminMapper` 替换 `RoleMapper`/`PermissionMapper`，`getStats()` 里对应两行 `selectCount` 改为查询组织总数（排除 `OrgStatus.DELETED`）与管理员总数（排除 `AdminStatus.DELETED`），`DashboardStatsVO.builder()` 同步改字段。
- [x] 3.3 `backend/.../dashboard/controller/DashboardController.java`：`stats()` 方法的 `@Operation`/`description` 文案由"用户/应用/角色/权限点"改为"组织/用户/应用/管理员"。

## 4. 首页统计卡片维度调整（前端）

- [x] 4.1 `frontend/src/types/dashboard.ts`：`DashboardStats` 接口字段由 `{ userCount, appCount, roleCount, permissionCount }` 改为 `{ orgCount, userCount, appCount, adminCount }`，注释同步更新。
- [x] 4.2 `frontend/src/views/dashboard/DashboardView.vue`：`statsMeta` 改为 组织总数（`orgCount`，图标 `OfficeBuilding`）、身份总数（`userCount`，图标 `UserFilled` 不变）、接入应用（`appCount`，图标 `Grid` 不变）、管理员总数（`adminCount`，图标 `Avatar`），从 `@element-plus/icons-vue` 引入 `OfficeBuilding`/`Avatar`，移除不再使用的 `Lock`/`Setting` 引入。
- [ ] 4.3 手动验证：首页统计卡片按新顺序展示"组织总数、身份总数、接入应用、管理员总数"四个真实总数，接口失败时仍展示原有失败态。（`./gradlew test --tests "cn.nihility.rbac.dashboard.*"` 已通过；本地调试时发现的"组织总数/管理员总数无数据"是端口 48080 被改动前的旧后端进程占用导致，不是代码缺陷——`bootRun` 因端口冲突启动失败，浏览器实际打到的是未包含本次改动的旧进程；未完成人工点击验证）

## 5. 文档同步

- [x] 5.1 实现完成后，按 `openspec-doc-sync` 约定核对 `proposal.md`/`design.md`/`tasks.md` 与实际代码改动是否一致，如有偏差据实更新。
