## Context

后端已经具备完整的运行时鉴权：`IdentityAuthFilter` 对每个业务请求校验 `identity-token`
与 `menu` 请求头，`AuthorizationServiceImpl.hasPermission(userId, menuCode)` 内部调用
`PermissionMapper.selectGrantedPermissionCodesByUserId(userId)`（SQL 见
`PermissionMapper.xml`，关联 `tab_admin`/`tab_admin_role`/`tab_role`/`tab_role_permission`，
只取启用状态的管理员身份、角色、权限点）拿到该用户当前的全量权限编码集合，再做
`Set#contains` 判断。这条查询逻辑已经存在且是唯一权威来源，本次改动不重新实现权限计算，
只是把它的结果通过一个新接口暴露给前端。

前端目前完全没有"当前用户拥有哪些权限编码"这个概念：`stores/auth.ts` 的登录态里没有
权限字段；`router/menu.ts` 的 `MENU_GROUPS` 是无条件的静态数据源；`SideNav.vue` 直接
`v-for` 渲染全部分组和子菜单；`router/index.ts` 的 `beforeEach` 只判断登录态和首登改密，
不做权限判断；12 个已落地业务逻辑的视图里的新增/编辑/删除等按钮全部无条件渲染。

## Goals / Non-Goals

**Goals:**
- 后端提供一个只读接口，返回当前登录用户的全量权限编码集合。
- 前端在能够确定"当前用户是谁"之后（登录成功、或带着本地会话刷新页面）尽早加载这份
  权限编码集合，作为菜单过滤、路由守卫、按钮门控三处消费方的唯一数据源。
- 提供一个可复用的判断方式，供模板 `v-if` 和路由守卫脚本逻辑共同使用，避免出现"菜单一套
  判断逻辑、按钮另一套判断逻辑"的分裂实现。
- 侧边栏菜单、路由跳转、12 个已实现业务页面的按钮三处同时按权限收敛展示，形成"看不到就
  进不去"的双重防线（前端不展示 + 后端仍然拦截，后端拦截逻辑本次不改动）。

**Non-Goals:**
- 不改动后端 `IdentityAuthFilter`/`AuthorizationServiceImpl` 现有的运行时拦截逻辑，
  它继续作为唯一的安全边界；前端展示层面的隐藏只是体验优化，不是安全机制。
- 不引入权限变更的实时推送（WebSocket/SSE）。管理员改了某用户的角色权限后，该用户
  当前已打开的会话不会立即刷新展示，需要重新登录或刷新页面才会生效——这与后端本身
  "每次请求实时查库、不缓存"的模型不完全一致，但前端展示层面接受这种滞后，因为后端
  仍会在实际请求时按最新权限拦截，滞后只影响"看得到但点了会 403"这一种边缘情况，
  不影响安全性。
- 不处理 `PlaceholderView` 复用的页面（尚未实现业务逻辑，没有真实按钮）。
- 不新增/调整 `权限资源.txt` 里的编码条目，本次改动只是"消费"已登记的编码。

## Decisions

### Decision 1：新接口挂在 `AuthController`，直接复用 `AuthorizationService`
新增 `GET /api/auth/permissions`，返回当前登录用户的权限编码集合（`Set<String>`，
外层包一层简单 DTO 如 `{ codes: string[] }`）。放在 `AuthController` 而不是
`PermissionController`：这是"查询当前会话身份关联的权限"，语义上和 `/api/auth/password`
（操作当前会话身份）同类，不是"权限点资源本身的管理型查询"（那属于
`PermissionController` 的 CRUD 语义）。

实现上给 `AuthorizationService` 新增一个方法：

```java
Set<String> getGrantedPermissionCodes(Long userId);
```

`AuthorizationServiceImpl` 直接委托给已有的
`permissionMapper.selectGrantedPermissionCodesByUserId(userId)`；原有的
`hasPermission(userId, menuCode)` 改为调用这个新方法再 `contains`，避免同一条查询逻辑
出现两处入口。`AuthController` 注入 `AuthorizationService`（在已注入的 `AuthService`
基础上追加一个依赖，controller 层多个 service 依赖在本项目内没有禁止先例），
用户 id 从 `CurrentUserContext.getUserId()` 取。

备选方案：把新接口放在 `PermissionController` 下（如
`GET /api/permissions/my`）。放弃：`PermissionController` 现有接口都是权限点资源的
管理型 CRUD（列表/详情/增改/启停/删除），语义上和"查当前会话的权限"不是一回事，
混进去会让这个 controller 的职责变得模糊。

### Decision 2：新接口豁免"权限编码校验"，复用 `FIRST_LOGIN_WHITELIST` 语义，但不豁免首登拦截
`IdentityAuthFilter` 对非白名单接口要求 `menu` 请求头格式合法，且请求头对应的编码必须在
`authorizationService.hasPermission(userId, menu)` 判定为真——但"查询我自己有哪些权限"
这个接口不应该要求调用方"必须已经拥有某个权限编码才能查询自己有哪些权限编码"，否则会
出现鸡生蛋悖论。因此把 `/api/auth/permissions` 加入现有的 `FIRST_LOGIN_WHITELIST`
（改名语义仍然成立："自助操作，不受角色权限点约束"），前端调用时仍需携带一个格式合法的
`menu` 头（如 `Auth:permission:my`，仅做格式校验），但不会被 403。

`FIRST_LOGIN_WHITELIST` 同时豁免"首登强制改密拦截"，意味着处于首登待改密状态的用户
理论上也能调用这个接口——这是无害的：前端设计上只在确认 `firstLogin === false` 之后才
会去加载权限编码集合（首登用户会被路由守卫拦到改密页，看不到任何菜单，没有加载的必要），
所以两个语义即使被同一个白名单一起豁免也不冲突，不需要为此单独拆一个新的白名单列表。

**联调中发现并已修复的问题：前端遗漏了"显式携带 `menu` 头"这一步，导致死循环。**
本 Decision 只规定了后端要豁免权限编码校验，但"前端调用时仍需携带一个格式合法的 `menu`
头"这一半在最初实现里被漏掉了——`api/request.ts` 的请求拦截器原本无条件用
`router.currentRoute.value.meta.permissionKey` 填充 `menu` 头，而 `getMyPermissions()`
本身没有指定任何 `menu` 头。`getMyPermissions()` 会在 `router.beforeEach` 里、导航尚未
完成时被调用（此时 `currentRoute` 还是上一个路由，可能没有 `permissionKey`，比如
`/dashboard`），于是请求实际发出时 `menu` 头缺失或不合法，后端在 Decision 2 描述的白名单
判断之前先做的"`menu` 头格式校验"（见 `IdentityAuthFilter.doFilterInternal` 里读取
`menu` 头之后的格式检查，白名单只豁免"格式合法之后的权限匹配"这一步，不豁免"格式本身
必须合法"这一步）会先一步返回 401。前端响应拦截器把任何 401 都当成"access-key 过期"
触发静默刷新（`/api/auth/refresh`），刷新成功后用同样缺失 `menu` 头的原始请求重试，
再次 401，再次刷新——`/api/auth/refresh` 与 `/api/auth/permissions` 由此互相触发，
形成死循环。

修复：`api/request.ts` 的拦截器改为"调用方已经显式设置了 `menu` 头时不再用当前路由覆盖"
（`if (!config.headers.get('menu')) { ... }`），`getMyPermissions()` 显式传入
`{ headers: { menu: 'Auth:permission:my' } }`——这个值本身不依赖当前路由状态，任何调用
时机下都合法。这也纠正了本 Decision 原文里"仅做格式校验"这句话背后隐含但没写清楚的
前提：格式校验是无条件的，不属于白名单豁免范围，豁免的只是"格式合法之后的权限点匹配"。

### Decision 3：前端新增独立的权限编码 store，而不是塞进 `stores/auth.ts` 或复用 `stores/permission.ts`
`stores/permission.ts` 现有的 `usePermissionStore` 是"权限点管理页面"的 CRUD 状态
（`PermissionRow[]` 列表），语义是"系统里全量的权限点资源"，和"当前登录用户拥有哪些
权限编码"完全是两回事，不能合并，也不能重名。新增
`stores/currentUserPermission.ts`，导出 `useCurrentUserPermissionStore`，只持有一个
`Set<string>` 编码集合 + `loaded` 标记 + `loadCodes()` 动作（调用新增的
`api/auth.ts` 里的 `getMyPermissions()`）。

不塞进 `stores/auth.ts`：`auth` store 职责是"登录态"（access-key/refresh-key/首登标识），
它的字段目前全部会被 `persist()` 写入 `localStorage`；权限编码集合刷新频率、生命周期
（每次应用启动/登录都应该重新拉取，而不是复用本地缓存的陈旧值）和登录态明显不同，
混在一起容易在 `persist()` 逻辑里意外把权限编码也持久化下来，造成"本地缓存的权限比
后端实际权限旧"的问题。独立 store 且不持久化，每次应用启动都强制重新拉取一次。

### Decision 4：前端复用方式选组合式函数 `usePermission()`，不做自定义指令
新增 `src/composables/usePermission.ts`，暴露：

```ts
function usePermission() {
  const store = useCurrentUserPermissionStore()
  function hasPermission(code?: string): boolean {
    if (!code) return true
    return store.codes.has(code)
  }
  return { hasPermission }
}
```

选组合式函数而非自定义指令（如 `v-permission="code"`）：
- 按钮级门控要做到"没权限完全不渲染"（对应用户"不能展示了点击才提示"的诉求），
  最直接可靠的方式是模板里 `v-if="hasPermission('Xxx:yyy:zzz')"`——`v-if` 保证元素
  根本不会出现在 DOM 里；自定义指令挂在元素上是"渲染之后再操作该元素"（常见实现是
  `el.style.display='none'` 或从父节点移除 `el`），时序上多一步、还要处理 SSR/keep-alive
  等边缘情况，实现复杂度高于直接 `v-if` 一个组合式函数调用。
- 路由守卫（`router/index.ts` 的 `beforeEach`，纯脚本逻辑，没有模板）也需要用同一套
  判断逻辑；组合式函数在 `<script setup>` 和普通 `.ts` 脚本里都能调用（`usePermission()`
  内部依赖的 `useCurrentUserPermissionStore()` 是 Pinia store，在 setup 外调用需要
  active pinia 实例，`router/index.ts` 里通过 `useCurrentUserPermissionStore()` 同样
  可以拿到单例，不需要额外包装），自定义指令做不到脚本侧复用，会导致路由守卫另写一套
  判断，违背"避免两套判断逻辑"的目标。
- 唯一的代价是每个按钮要显式写一次 `v-if`，比"贴个指令属性"多几个字符，但换来判断逻辑
  单一、行为可预测（不依赖指令的 mounted/updated 生命周期时机），综合评估更优。

### Decision 5：菜单过滤在 `menu.ts` 侧提供纯函数，`SideNav.vue` 消费
`menu.ts` 新增一个纯函数：

```ts
export function filterMenuGroups(groups: MenuGroup[], hasPermission: (code?: string) => boolean): MenuGroup[] {
  return groups
    .map((group) => ({
      ...group,
      children: group.children.filter((child) => hasPermission(child.permissionKey)),
    }))
    .filter((group) => group.children.length > 0)
}
```

`SideNav.vue` 用 `computed(() => filterMenuGroups(MENU_GROUPS, hasPermission))` 得到
`visibleGroups`，模板里把原来对 `MENU_GROUPS` 的 `v-for` 换成对 `visibleGroups` 的
`v-for`——一级分组下所有二级菜单都被过滤掉时，该分组自然从结果数组里消失，不需要额外
判断"是否整组隐藏"的分支逻辑。纯函数放在 `menu.ts` 而不是内联在 `SideNav.vue` 里，
是因为路由生成（`router/index.ts` 里 `MENU_GROUPS.flatMap(...)`）和侧边栏渲染共享
同一份 `MENU_GROUPS` 数据源，过滤函数未来如果被除 `SideNav.vue` 外的第二个消费方
（比如面包屑、首页快捷入口）需要，能直接从 `menu.ts` 导入，不用重复写。

### Decision 6：路由守卫在现有 `beforeEach` 里追加一步权限校验，并保证权限编码已加载
在现有 `router/index.ts` 的 `beforeEach` 里，登录态判断（步骤 1）和首登改密判断
（步骤 2）之后、放行之前，追加：

1. 若 `authStore.isLoggedIn` 且当前用户权限 store 尚未加载过（`!loaded`），
   `await` 调用一次 `loadCodes()`（覆盖"刷新页面后 Pinia 状态重置，但本地
   `localStorage` 里的登录态仍然有效"这种直接从浏览器地址栏输入 URL 进入的场景）。
2. 若目标路由 `to.meta.permissionKey` 存在且 `!hasPermission(to.meta.permissionKey)`，
   拦截导航：`ElMessage.warning('没有权限访问该页面')` 后重定向到 `/dashboard`
   （首页概览页本身没有 `permissionKey`，所有登录用户都能访问，选它作为兜底落点，
   不引入新的"403 页面"路由，保持改动面小）。

这一步是本次改动里唯一"必须"的路由层防线：菜单隐藏只解决了"入口不可见"，直接改地址栏
输入 URL 仍然可以导航过去，如果没有这一步，用户仍然能看到无权限页面的完整内容
（只是页面内的接口请求会被后端 403，体验上仍然是"进去了才发现不行"，不符合本次修复的
诉求）。

**实现中发现并已修复的边界情况：`/change-password` 需要豁免这项校验。**
`/change-password`（首登强制改密页）的 `meta.permissionKey` 设为
`'Auth:password:change'`，但这个编码从一开始就只是给 `IdentityAuthFilter` 的 `menu`
请求头格式校验用的占位值（详见 Decision 2 的豁免逻辑与首登拦截语义），**没有**在
`权限资源.txt` 或数据库 `tab_permission` 种子数据里登记为一个真实权限点，因此
`GET /api/auth/permissions` 返回的用户权限编码集合永远不会包含它。第 4 步的权限校验
如果不加区分地对所有带 `permissionKey` 的路由生效，会导致 `hasPermission('Auth:password:
change')` 恒为 `false`——而这条路由恰好是首登用户被步骤 2 强制重定向的目的地，于是形成
`/dashboard` → 权限校验拦截 → `/dashboard` → 步骤 2 又重定向回 `/change-password` →
权限校验再次拦截……的无限重定向循环，彻底卡死首登强制改密流程（含默认账号
admin/admin123，任何首次登录的账号都会触发）。

修复方式：第 4 步权限校验加一个 `to.name !== 'change-password'` 的豁免条件，即
`if (to.name !== 'change-password' && to.meta.permissionKey && !hasPermission(to.meta.
permissionKey))`。这不是削弱安全边界——`/change-password` 本身要求 `requiresAuth: true`
（未登录仍会被步骤 1 拦截），只是不对它做"是否拥有某个业务权限点"的判断，因为它压根
不是一个按角色分配的业务权限点，而是所有登录用户（无论角色）在首登状态下都必须能访问
的自助操作页面，语义上和 Decision 2 里 `/api/auth/permissions` 接口的豁免理由一致。

这也提示了一个通用结论：路由的 `permissionKey` 字段目前被复用于两种不同语义——
（1）真实登记在 `权限资源.txt` 里、按角色授予的业务权限点（多数路由属于此类，第 4 步
校验对它们生效）；（2）仅用于满足后端请求头格式校验、但从未打算被路由守卫做权限拦截的
占位值（目前只有 `change-password` 这一例）。本次改动通过硬编码豁免这一个特例解决问题；
如果未来出现更多此类"仅占位不代表真实权限点"的路由，需要重新考虑是否要在 `meta` 上
新增一个独立字段（如 `skipPermissionCheck: true`）来显式区分这两种语义，而不是继续在
`beforeEach` 里堆加按路由名判断的特例分支。

**归档后发现并已修复的第二处问题：`loadCodes()` 失败时 `beforeEach` 不能让异常逃逸出去。**
用户反馈"偶尔点击菜单会刷新整个页面重新加载"。根因：第 1 步 `await currentUserPermissionStore.
loadCodes()` 是一次真实网络请求，当 access-key 与 refresh-key **同时**已失效（最典型场景：
标签页闲置超过两者的有效期后才点击菜单）时，这个请求会失败——`api/request.ts` 的响应拦截器
在内部先尝试静默刷新（`triggerRefresh()`），刷新同样失败后会调用 `redirectToLogin()`
（清空登录态，另外发起一次 `router.push({ name: 'login' })`），但原始的失败依然会继续沿
`getMyPermissions()` → `loadCodes()` → `beforeEach` 往上抛出。触发这次导航的
`<el-menu>`（Element Plus `menu.mjs` 源码）内部是 `router.push(route).then((res) => {...})`，
**没有写 `.catch()`**，于是这个异常变成一次未处理的 Promise rejection，恰好和
`redirectToLogin()` 另外发起的登录页跳转互相竞争——两个导航同时发生，表现为页面状态被整个
刷新重置，用户感知为"点了菜单，整个页面刷新重新加载"。

已用 Node 脚本直接对运行中的后端复现这条根因链路上的每一环（详见 `tasks.md` 3.4）：
- 不携带 `menu` 头 → `401 缺少合法的操作资源标识`
- 携带过期/伪造的 `identity-token` → `401 登录状态已失效`
- 携带过期/伪造的 `refreshKey` 调 `/api/auth/refresh` → `401 刷新令牌无效或已过期`

三个响应码都印证了"两个 token 同时失效时，`loadCodes()` 必然失败"这一假设；受限于本环境未
安装 Chrome 扩展，没有用真实浏览器复现"页面被视觉上重置"这一表现本身，但网络层面的因果链条
已经闭环。

修复两处（不改变 Decision 6 第 1、2 步的行为语义，只是让失败路径不再向外抛异常）：
1. `stores/currentUserPermission.ts` 的 `loadCodes()` 改为单飞（复用 `api/request.ts` 里
   `triggerRefresh()`/`refreshingPromise` 的同一模式：用一个模块级 `loadingPromise` 变量
   去重，进行中的加载请求被并发的第二次导航复用同一个 Promise，而不是各打各的请求）——
   这本身不解决"失败仍会抛出"的问题，但收窄了并发窗口，减少连续快速点击时同时触发多个
   失败请求、多次竞争的概率。
2. `router/index.ts` 的 `beforeEach` 给 `await currentUserPermissionStore.loadCodes()`
   包一层 `try/catch`：加载失败时直接 `return false`（放弃本次导航），不再让异常继续往外
   传播。`redirectToLogin()` 已经在错误发生的当下把用户带去登录页，`beforeEach` 这里要做的
   只是"不要再添乱"——两个导航不再竞争，也不会出现未处理的 Promise rejection。

### Decision 7：按钮级门控只覆盖 12 个已实现业务页面，按 `权限资源.txt` 逐条对齐
范围：`OrgManagementView`、`UserManagementView`、`PositionManagementView`、
`AppManagementView`、`RoleManagementView`、`PermissionManagementView`、
`AdminManagementView`、`MenuManagementView`、`DictManagementView`、
`MetadataFieldListView`、`FormFieldListView`、`OperationLogManagementView`。
每个视图对照 `权限资源.txt` 里登记的、且不在"不纳入统计"名单内的按钮编码逐个补
`v-if="hasPermission('Module:resource:action')"`；搜索/重置/取消/表单内"添加一行/
删除一行"等纯前端交互按钮维持不变（`权限资源.txt` 本身已注明这些不对应后端资源，
不需要门控）。任务粒度拆分见 `tasks.md`。

## Risks / Trade-offs

- **[风险] 前端权限编码缓存滞后**：管理员改了某在线用户的角色/权限后，该用户当前会话
  展示的菜单/按钮不会立即变化，仍然按登录/刷新页面那一刻的权限展示。
  → **缓解**：后端每次请求仍然实时查库校验（本次不改动），滞后只造成"看得到、点了才
  403"这一种边缘情况，不产生越权后果；属于本次 Non-Goals 里已明确接受的取舍。
- **[风险] 12 个视图逐个补 `v-if` 是体力活，容易漏改或编码写错**：三段式编码是纯字符串，
  没有编译期校验，写错字符串编译不会报错，只会导致"按钮该显示时不显示"或"该隐藏时
  未隐藏"两种静默错误。
  → **缓解**：`tasks.md` 按视图逐条列出要绑定的编码，实现时逐条对照
  `权限资源.txt` 原文核对，而不是凭记忆；每个视图改完后用超级管理员账号（拥有全部
  权限）和一个新建的、只分配少量权限点的测试角色账号分别登录做一次目测核对。
- **[权衡] 路由守卫的"无权限"落点统一到 `/dashboard`**：没有单独的 403 页面，用户会看到
  "跳到了概览页 + 一条提示"而不是一个专门的"无权限"占位页。
  → 本次改动刻意选择这个更小的改动面；如果后续产品体验上需要专门的 403 页面，
  可以在独立的后续 change 里再加，不影响本次的核心诉求（菜单/按钮不展示）。
- **[风险，实现中已发现并修复] 带 `permissionKey` 但未登记为真实权限点的路由会被误拦截**：
  `/change-password` 的 `permissionKey` 只是请求头格式校验用的占位值（见 Decision 6 补充
  说明），Decision 6 的权限校验步骤最初实现时未区分这种情况，导致首登用户在 `/dashboard`
  与 `/change-password` 之间无限重定向，彻底破坏首登强制改密流程（含默认账号
  admin/admin123）。已在同一轮实现内修复：`beforeEach` 权限校验加 `to.name !== 'change-
  password'` 豁免条件。记录在此是为了提醒：`meta.permissionKey` 目前混用了"真实业务权限
  点"和"仅供请求头格式占位"两种语义，未来新增此类占位路由时需要同样处理，否则会复现
  同一类无限重定向问题。
- **[风险，归档后发现并修复] `loadCodes()` 网络请求失败会以未处理 Promise rejection 的形式
  逃逸，和 `redirectToLogin()` 竞争出"整页刷新重置"的观感**：`beforeEach` 第 1 步引入了本次
  改动之前从未存在过的"路由守卫内发起真实网络请求"这一新行为——此前 `beforeEach` 全是同步
  判断，从不会失败；本次改动后，一旦 access-key/refresh-key 同时失效（典型场景：标签页闲置
  超过两者有效期后才点菜单），`loadCodes()` 会失败，而 `<el-menu>`（Element Plus 内部）触发
  导航用的 `router.push(route).then(...)` 没有 `.catch()`，异常没人接住。已修复：`loadCodes()`
  加单飞去重，`beforeEach` 给这一步包 `try/catch` 并在失败时 `return false`（详见上方
  Decision 6 补充说明、`tasks.md` 3.4）。记录在此是为了提醒：以后如果在 `beforeEach` 里追加
  更多异步操作，必须同样确保失败路径不会向外抛异常——`vue-router` 的全局导航守卫和触发导航
  的第三方组件（这里是 Element Plus 的 `<el-menu router>`）之间，没有任何一方会替你兜底这个
  错误。
