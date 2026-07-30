## 1. 后端：暴露当前用户权限编码

- [x] 1.1 `AuthorizationService` 新增 `Set<String> getGrantedPermissionCodes(Long userId)`；`AuthorizationServiceImpl` 内部委托给 `permissionMapper.selectGrantedPermissionCodesByUserId(userId)`，`hasPermission` 改为调用该新方法再 `contains`，避免两处查询入口。
- [x] 1.2 新增响应 DTO（如 `auth/dto/PermissionCodesVO`，字段 `Set<String> codes`）。
- [x] 1.3 `AuthController` 注入 `AuthorizationService`，新增 `GET /api/auth/permissions`，从 `CurrentUserContext.getUserId()` 取当前用户 id，加 `@Operation`/`@Tag` 注解说明。
- [x] 1.4 `IdentityAuthFilter` 的 `FIRST_LOGIN_WHITELIST` 加入 `/api/auth/permissions`，确认类上注释同步说明该路径新增的用途（"查询自身权限，自助操作，不受角色权限点约束"）。
- [x] 1.5 手动验证（归档后补做，直接对运行中的后端发请求验证，未通过浏览器）：`admin` 账号登录后携带合法 `identity-token`+`menu` 头调用，返回该账号当前拥有的全量权限编码集合（管理员角色下 ~90+ 条编码）；不携带 `menu` 头返回 `401 缺少合法的操作资源标识`；携带一个伪造/过期的 `identity-token` 返回 `401 登录状态已失效`；完全不携带 `identity-token` 返回 `401 未登录`。未验证"仅分配少量权限点的受限角色账号"这一档（当前种子数据只有管理员账号，需要先建一个受限角色测试账号才能补齐这一半）。

## 2. 前端：权限编码 store 与复用能力

- [x] 2.1 `src/types/auth.ts` 新增 `PermissionCodesResult` 类型（对应后端 DTO）；`src/api/auth.ts` 新增 `getMyPermissions(): Promise<PermissionCodesResult>`（`GET /api/auth/permissions`）。
- [x] 2.2 新增 `src/stores/currentUserPermission.ts`，导出 `useCurrentUserPermissionStore`：持有 `codes: Ref<Set<string>>`、`loaded: Ref<boolean>`，动作 `loadCodes()`（调用 2.1 的接口，写入 `codes`，`loaded = true`）与 `reset()`（logout 时清空，`loaded = false`）；不写入 `localStorage`。
- [x] 2.3 新增 `src/composables/usePermission.ts`，导出 `usePermission()`，暴露 `hasPermission(code?: string): boolean`（`code` 为空时视为无需权限校验，返回 `true`）。
- [x] 2.4 `stores/auth.ts` 的 `logout()` 调用 `useCurrentUserPermissionStore().reset()`，避免退出登录后残留上一个用户的权限编码。
- [x] 2.5（联调中发现并修复的问题，原任务清单未预见）`api/request.ts` 的请求拦截器原先无条件用 `router.currentRoute.value.meta.permissionKey` 覆盖 `menu` 请求头；`getMyPermissions()` 在 `router.beforeEach` 里、导航尚未完成时就被调用（此时 `currentRoute` 还是旧路由），或被非业务路由触发时，会导致 `menu` 头缺失/无效，后端因此返回 401，前端响应拦截器误判为"access-key 过期"触发静默刷新，刷新后用同样缺失 `menu` 头的请求重试，与 `/api/auth/refresh` 形成死循环。修复：拦截器改为"调用方已显式设置 `menu` 头时不再用当前路由覆盖"，`getMyPermissions()` 显式传入固定值 `Auth:permission:my` 作为 `menu` 头（格式合法、不依赖当前路由状态）。

## 3. 前端：加载时机与路由守卫

- [x] 3.1 `router/index.ts` 的 `beforeEach` 里，在现有登录态判断（步骤 1）、首登改密判断（步骤 2）之后新增：若 `authStore.isLoggedIn && authStore.firstLogin === false && !currentUserPermissionStore.loaded`，`await currentUserPermissionStore.loadCodes()`。
- [x] 3.2 `beforeEach` 里新增权限校验步骤：若 `to.meta.permissionKey` 存在且 `!hasPermission(to.meta.permissionKey)`，`ElMessage.warning('没有权限访问该页面')` 并重定向到 `/dashboard`（不放行本次导航）。
- [x] 3.4（归档后发现并修复的问题，原任务清单未预见）用户反馈"偶尔点击菜单会刷新整个页面重新加载"。根因：`beforeEach` 里 `await currentUserPermissionStore.loadCodes()` 是一次真实网络请求，当 access-key/refresh-key 均已失效（例如标签页闲置很久后才点菜单）时该请求会失败——`axios` 响应拦截器内部的静默刷新也会失败并调用 `redirectToLogin()`（清空登录态、另外发起一次跳转登录页的导航），但原始的失败还会继续沿 `loadCodes()` → `beforeEach` 往上抛；`<el-menu>`（Element Plus `menu.mjs`）内部触发导航时用的是 `router.push(route).then(...)`，没有写 `.catch()`，异常逃逸出去变成未处理的 Promise rejection，与 `redirectToLogin()` 发起的登录页跳转互相竞争，表现为页面状态被整个刷新重置。修复两处：① `stores/currentUserPermission.ts` 的 `loadCodes()` 改为单飞（与 `request.ts` 里 `triggerRefresh()` 的 `refreshingPromise` 同一模式），避免连续快速点击在首次加载完成前触发多个并发请求；② `router/index.ts` 的 `beforeEach` 给 `await loadCodes()` 包一层 `try/catch`，失败时 `return false` 放弃本次导航，不再让异常向外抛。已用 Node 脚本直接对运行中的后端复现"过期 token/过期 refresh-key 均返回 401"这条根因链路，未能用真实浏览器复现"页面视觉上被重置"这一表现（本环境未安装 Chrome 扩展）。
- [ ] 3.3 手动验证：用一个只分配了部分权限点的测试角色账号登录后，直接在地址栏输入一个未授权页面的 URL，确认被拦截跳转且看不到目标页面内容；浏览器刷新页面后再次直接访问业务页面，确认权限编码被重新加载（而不是因为内存状态清空而误判无权限）。（未执行——后端 `GET /api/auth/permissions` 接口与本轮前后端并行开发，本次实现时尚未联调验证；已通过代码走查确认逻辑符合设计，待后端接口就绪后需要一次真实登录验证）

## 4. 前端：侧边栏菜单过滤

- [x] 4.1 `router/menu.ts` 新增导出的纯函数 `filterMenuGroups(groups, hasPermission)`（design.md Decision 5 给出的实现）。
- [x] 4.2 `SideNav.vue` 引入 `usePermission()`，用 `computed` 得到 `visibleGroups = filterMenuGroups(MENU_GROUPS, hasPermission)`，模板里的 `v-for` 从 `MENU_GROUPS` 换成 `visibleGroups`。
- [ ] 4.3 手动验证：测试角色账号登录后，侧边栏只展示其拥有 `:view` 权限的二级菜单；某个一级分组下的二级菜单全部无权限时，该一级分组标题也不出现。（未执行，原因同 3.3）

## 5. 前端：业务页面按钮级权限门控（按 `权限资源.txt` 逐个视图核对编码）

- [x] 5.1 `identity/org/OrgManagementView.vue`：`下载导入模板`→`OrgManagement:org:importTemplate`、`批量导入`→`OrgManagement:org:import`、`新增`→`OrgManagement:org:add`、`详情`→`OrgManagement:org:detail`、`编辑`→`OrgManagement:org:edit`、状态切换按钮按当前 `row.status` 分别对应 `OrgManagement:org:disable`/`OrgManagement:org:enable`、`删除`→`OrgManagement:org:delete`。
- [x] 5.2 `identity/user/UserManagementView.vue`：`下载导入模板`→`UserManagement:user:importTemplate`、`批量导入`→`UserManagement:user:import`、`新增`→`UserManagement:user:add`、`详情`→`UserManagement:user:detail`、`编辑`→`UserManagement:user:edit`、状态切换按钮→`UserManagement:user:disable`/`UserManagement:user:enable`、`重置密码`→`UserManagement:user:resetPassword`、`删除`→`UserManagement:user:delete`。
- [x] 5.3 `identity/position/PositionManagementView.vue`：`下载导入模板`→`PositionManagement:position:importTemplate`、`批量导入`→`PositionManagement:position:import`、`新增`→`PositionManagement:position:add`、`详情`→`PositionManagement:position:detail`、`编辑`→`PositionManagement:position:edit`、状态切换按钮→`PositionManagement:position:disable`/`PositionManagement:position:enable`、`删除`→`PositionManagement:position:delete`。
- [x] 5.4 `application/app/AppManagementView.vue`：`下载导入模板`→`AppManagement:app:importTemplate`、`批量导入`→`AppManagement:app:import`、`新增`→`AppManagement:app:add`、`详情`→`AppManagement:app:detail`、`编辑`→`AppManagement:app:edit`、状态切换按钮→`AppManagement:app:disable`/`AppManagement:app:enable`、`删除`→`AppManagement:app:delete`。
- [x] 5.5 `permission/role/RoleManagementView.vue`：`新增`→`RoleManagement:role:add`、`详情`→`RoleManagement:role:detail`、`编辑`→`RoleManagement:role:edit`、状态切换按钮→`RoleManagement:role:disable`/`RoleManagement:role:enable`、`删除`→`RoleManagement:role:delete`。
- [x] 5.6 `permission/permission/PermissionManagementView.vue`：`新增`→`PermissionManagement:permission:add`、`详情`→`PermissionManagement:permission:detail`、`编辑`→`PermissionManagement:permission:edit`、状态切换按钮→`PermissionManagement:permission:disable`/`PermissionManagement:permission:enable`、`删除`→`PermissionManagement:permission:delete`（`全部展开`/`全部收起`是纯前端交互，不门控）。
- [x] 5.7 `permission/admin/AdminManagementView.vue`：`新增`→`AdminManagement:admin:add`、`详情`→`AdminManagement:admin:detail`、`编辑`→`AdminManagement:admin:edit`、状态切换按钮→`AdminManagement:admin:disable`/`AdminManagement:admin:enable`、`删除`→`AdminManagement:admin:delete`。
- [x] 5.8 `system/menu/MenuManagementView.vue`：`新增`→`MenuManagement:menu:add`、`详情`→`MenuManagement:menu:detail`、`编辑`→`MenuManagement:menu:edit`、状态切换按钮→`MenuManagement:menu:disable`/`MenuManagement:menu:enable`、`删除`→`MenuManagement:menu:delete`。
- [x] 5.9 `system/dict/DictManagementView.vue`：字典类型区`新增`→`DictManagement:dictType:add`、`详情`→`DictManagement:dictType:detail`、`编辑`→`DictManagement:dictType:edit`、状态切换按钮→`DictManagement:dictType:disable`/`DictManagement:dictType:enable`、`删除`→`DictManagement:dictType:delete`；字典项区`新增`→`DictManagement:dictItem:add`、`详情`→`DictManagement:dictItem:detail`、`编辑`→`DictManagement:dictItem:edit`、状态切换按钮→`DictManagement:dictItem:disable`/`DictManagement:dictItem:enable`、`删除`→`DictManagement:dictItem:delete`。
- [x] 5.10 `system/metadatafields/MetadataFieldListView.vue`：`详情`→`MetadataFieldManagement:metadataField:detail`、`编辑`→`MetadataFieldManagement:metadataField:edit`、状态切换按钮→`MetadataFieldManagement:metadataField:disable`/`MetadataFieldManagement:metadataField:enable`（该模块没有新增/删除按钮，无需处理）。
- [x] 5.11 `system/formfields/FormFieldDefinitionPanel.vue`：`新增`→`FormFieldManagement:formField:add`、`编辑`→`FormFieldManagement:formField:edit`、状态切换按钮→`FormFieldManagement:formField:disable`/`FormFieldManagement:formField:enable`、`删除`→`FormFieldManagement:formField:delete`；对已按承重字段规则隐藏删除入口/禁用开关的既有逻辑保持不变，权限门控在此基础上叠加（两者都满足才展示）。
- [x] 5.12 `system/formfields/ImportFieldConfigPanel.vue`：`新增`→`FormFieldManagement:importFieldConfig:add`、`编辑`→`FormFieldManagement:importFieldConfig:edit`、删除按钮→`FormFieldManagement:importFieldConfig:delete`；系统保护配置（人员编号/组织编码）既有的隐藏删除入口逻辑保持不变。
- [x] 5.13 `system/log/OperationLogManagementView.vue`：`详情`→`OperationLogManagement:log:view`（该模块只有页面级 `:view`，没有独立按钮级编码，详情按钮复用页面访问权限，即页面能进入就能看详情，此项可确认现状已满足，无需改动或仅需确认）。
- [ ] 5.14 每个视图改完后，分别用超级管理员账号（拥有全部权限）和一个新建的、只分配少量权限点的测试角色账号登录，逐页目测核对按钮显隐是否符合预期。

## 6. 文档同步

- [x] 6.1 全部实现完成后，调用 `openspec-doc-sync` 对齐本 change 的 `proposal.md`/`design.md`/`tasks.md` 与实际实现结果（如果实现中发现某些视图的按钮编码与本清单不一致，以实际代码为准更新本文档）。实现中发现并修复了一处 design.md 未记录的偏差（路由守卫需豁免 `change-password` 的 `permissionKey` 校验，避免首登无限重定向），已补充进 Decision 6 与 Risks/Trade-offs。
- [x] 6.2（change 归档后补的两轮修复，同步文档）第一轮：`getMyPermissions()` 未显式携带 `menu` 头导致 `/api/auth/refresh` 与 `/api/auth/permissions` 互相触发死循环，已修复并补充进 Decision 2；第二轮：见 3.4，`beforeEach` 未捕获 `loadCodes()` 失败导致偶发"整页刷新重置"，已修复并补充进 Decision 6。两轮修复也已同步进 `openspec/specs/permission-driven-visibility/spec.md`（主 spec，非本 change 目录下的 delta spec，因为 change 已归档，delta spec 已经完成历史使命）。
