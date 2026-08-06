## 1. 后端：新增统计接口

- [x] 1.1 新建 `cn.nihility.rbac.dashboard` 包，按项目分层约定创建 `controller`/`dto`/`service`/`service/impl` 子包（无需 `entity`/`mapper`/`mapstruct`，不持有自己的表）。
- [x] 1.2 新建 `dto/DashboardStatsVO`：`userCount`/`appCount`/`roleCount`/`permissionCount`（均 `Long`），补 springdoc `@Schema` 注解。
- [x] 1.3 新建 `service/DashboardStatisticsService` 接口 + `impl/DashboardStatisticsServiceImpl`：注入 `UserMapper`/`AppMapper`/`RoleMapper`/`PermissionMapper`，分别用 `LambdaQueryWrapper<XxxEntity>().ne(XxxEntity::getStatus, XxxStatus.DELETED)` 做 `selectCount`，四条统计逻辑互相独立，不调用/不依赖各模块现有的 `getPage` 方法。
- [x] 1.4 新建 `controller/DashboardController`：`GET /api/dashboard/stats`，返回 `DashboardStatsVO`，补 `@Tag`/`@Operation` 注解，走全局统一响应包装。
- [x] 1.5 修改 `backend/.../auth/filter/IdentityAuthFilter.java` 的 `FIRST_LOGIN_WHITELIST` 常量，追加 `/api/dashboard/stats`，使其豁免角色权限点校验（仍需合法 `identity-token` + 格式合法的 `menu` 头）。
- [x] 1.6 后端编译验证：`./gradlew build --tests "cn.nihility.rbac.RbacApplicationTests"` 或至少 `./gradlew build` 确认无编译错误（如已有针对该模块的测试基建，按现有风格补充一个 controller/service 的简单测试；不新增测试框架）。

## 2. 前端：API 封装

- [x] 2.1 新建 `frontend/src/api/dashboard.ts`：封装 `getDashboardStats()`，请求 `GET /dashboard/stats`，显式传 `headers: { menu: 'Dashboard:stats:view' }`，返回类型对齐后端 `DashboardStatsVO`（新建 `frontend/src/types/dashboard.ts` 或就近放在 api 文件里）。
- [x] 2.2 检查 `frontend/src/api/operationLog.ts` 的 `getOperationLogPage` 调用是否需要显式传 `menu` 头（`OperationLogManagement:log:view`）——`/dashboard` 路由无 `permissionKey`，需要在调用处显式传，而不是依赖路由回退。

## 3. 前端：DashboardView.vue 数据接入

- [x] 3.1 移除写死的 `stats` 数组，改为响应式状态：四个数值 + 统计区整体的 loading/error 状态。
- [x] 3.2 移除写死的 `activity` 数组，改为响应式状态：记录列表 + 时间线区域独立的 loading/error 状态。
- [x] 3.3 在 `onMounted` 中用 `Promise.allSettled` 并发发起两路请求：`getDashboardStats()` 与 `getOperationLogPage({page:1,pageSize:4}, ...)`。
- [x] 3.4 统计请求 fulfilled 时用返回的四个 count 字段填充四个卡片；rejected 时统计区整体标记失败态，不影响"最近操作"区域。
- [x] 3.5 操作日志请求 fulfilled 且 `records` 非空时，用 `module`/`targetName`/`operationTypeLabel`/`createBy`/`createTime` 拼出每条时间线文案（`resourceName` 未使用，实现时判断 `module`+`targetName`+`operationTypeLabel` 已足以拼出通顺文案）；`records` 为空时展示"暂无操作记录"空态；rejected 时展示加载失败提示，不影响统计卡片区域。
- [x] 3.6 统计区与时间线区在对应请求返回前展示加载态（如 `v-loading` 或文案），不展示数值 0 / 空列表作为占位。

## 4. 移除环比展示

- [x] 4.1 从统计卡片模板与样式中移除 `delta`/`up` 字段的展示（徽标 DOM、`.stat-card__delta` 相关 class 绑定），若样式类 `.is-up`/`.is-flat` 因此不再被引用则一并清理。

## 5. 验证

- [x] 5.1 后端启动 `./gradlew bootRun`，用登录后拿到的 `identity-token` + `menu: Dashboard:stats:view` 直接调用 `GET /api/dashboard/stats`（脚本化调用，未走 Swagger UI），返回 `{"userCount":1,"appCount":0,"roleCount":1,"permissionCount":95}`，与同账号下 `/api/users`、`/api/apps`、`/api/roles`、`/api/permissions`（`pageSize=1`）返回的 `total` 逐一比对完全一致。
- [x] 5.2 代码走查确认：`IdentityAuthFilter.FIRST_LOGIN_WHITELIST` 对 `/api/dashboard/stats` 无条件跳过 `authorizationService.hasPermission` 判断（不区分调用账号拥有哪些权限点），逻辑上等价于"任意已登录账号均可查看"；未额外创建无权限测试账号做登录态验证。
- [~] 5.3 前端启动 `npm run dev`（已确认无编译/运行时报错），但本会话没有可用的浏览器自动化工具，未能实际打开页面肉眼核对卡片渲染；数据正确性已通过 5.1 的接口级比对覆盖。
- [~] 5.4 操作日志接口当时返回 `records: []`（库里当时还没有操作日志数据），已通过代码走查确认 `DashboardView.vue` 在该场景下会走空态分支（`activityItems.length === 0` → 展示"暂无操作记录"），而不是展示假数据或空白骨架；当时因为没有真实的历史操作记录，未能核对"非空场景下时间线文案与操作日志页面一致"。**该缺口后来被 8.1 补上**：数据库里积累出操作记录后，8.1 直接在接口层比对了 `/api/dashboard/recent-operations` 与 `/api/operation-logs` 返回的记录字段完全一致，且两者复用同一段 `OperationLogVO` 渲染逻辑（见 3.5），可确认非空场景下时间线文案与操作日志页面一致；仍未做的只是浏览器里的肉眼渲染核对（见 5.3）。
- [~] 5.5 空态已被真实数据触发并通过代码走查确认（见 5.4）；失败态未做人为断网/改错 URL 的实测，通过代码走查确认 `loadStats`/`loadActivity` 的 try/catch 分别只置各自的 `xxxError`，互不影响。
- [x] 5.6 `npm run build`（vue-tsc 类型检查 + vite build）通过（前端 agent 已验证）。

## 6. 后端：新增"当前用户最近操作"接口（替代复用 /api/operation-logs 的方案）

- [x] 6.1 在 `DashboardController` 新增 `GET /api/dashboard/recent-operations`：从 `CurrentUserContext.getUserId()` 取当前用户 id，用 `UserMapper.selectById` 查其 `code`（登录账号），构造 `OperationLogQueryRequest{ createBy = code, page = 1, pageSize = 4 }`，调用已有的 `OperationLogQueryService.getPage(...)`，返回 `records`（`List<OperationLogVO>`）。不新增可配置的 `limit` 参数。
- [x] 6.2 修改 `IdentityAuthFilter.FIRST_LOGIN_WHITELIST`，追加 `/api/dashboard/recent-operations`（自助操作，不做权限点校验）。
- [x] 6.3 按现有测试风格为新方法补一个 service/controller 层的简单测试（可参考 `DashboardStatisticsServiceImplTest` 的写法）。
- [x] 6.4 `./gradlew build` 确认编译和全部测试通过。

## 7. 前端：迁移最近操作到新接口

- [x] 7.1 在 `frontend/src/api/dashboard.ts` 新增 `getRecentOperations()`：请求 `GET /dashboard/recent-operations`，显式传 `headers: { menu: 'Dashboard:recentOperations:view' }`，返回类型复用/对齐 `OperationLogRow`。
- [x] 7.2 `DashboardView.vue` 的 `loadActivity()` 改为调用 `getRecentOperations()`，不再调用 `getOperationLogPage`；其余 loading/error/空态处理逻辑不变。
- [x] 7.3 `npm run build` 确认通过。

## 8. 验证（本次追加）

- [x] 8.1 重启后端加载最新代码后，脚本化登录 admin 并调用 `GET /api/dashboard/recent-operations`（带合法 `identity-token` + `menu: Dashboard:recentOperations:view`），返回的 4 条记录与同账号调用 `/api/operation-logs?page=1&pageSize=4`（`OperationLogManagement:log:view`）返回的 `records` 逐字段完全一致（当前库里所有操作日志的 `createBy` 都是 `admin`，与登录账号一致，符合 design.md Context 里记录的 `createBy` 系统性写死为 `"admin"` 的现存限制）；只用一个账号验证了"不包含其他账号记录"这一半——当前数据库只有这一个会产生操作日志的账号，未能构造出第二个不同 `createBy` 的场景做交叉验证。
- [x] 8.2 用同一个已登录 `identity-token`，把 `menu` 头故意换成一个格式合法但不存在于权限点体系里的值（`Nonexistent:fake:permission`）调用 `GET /api/dashboard/recent-operations`，返回 HTTP 200 + 正常数据（不是 403），证实该接口确实跳过了权限点校验，不依赖调用账号是否拥有"操作日志管理"查看权限。
- [x] 8.3 `npm run build`（前端 agent 已验证）与 `./gradlew build`（后端 agent 已验证，含新增单测）均通过。
