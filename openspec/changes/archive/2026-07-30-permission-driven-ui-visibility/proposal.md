## Why

当前登录用户看不到权限的菜单和按钮仍然会展示出来，点击后才由后端 `IdentityAuthFilter` 返回 403「无权限访问该资源」提示。这对用户不友好——权限模型的意义是"看不到就不该出现"，而不是"出现了但点了才告诉你不行"。后端运行时鉴权（`AuthorizationServiceImpl.hasPermission`）已经具备判断"用户是否拥有某个三段式权限编码"的能力，但前端从未拿到过当前用户的权限编码集合，导致侧边栏 `MENU_GROUPS` 是静态渲染、路由守卫不做权限判断、所有业务页面的按钮也都无条件展示。

## What Changes

- 后端新增一个查询接口，返回当前登录用户（从 `identity-token` 解出的 userId）当前拥有的全量权限编码集合，直接复用 `PermissionMapper.selectGrantedPermissionCodesByUserId`，不重新实现权限计算逻辑。
- 前端登录成功后（以及静默刷新会话后应用重新初始化时）调用该接口，把权限编码集合存入新增的权限 store，作为后续菜单过滤、路由守卫、按钮门控的唯一数据源。
- 提供一个可复用的前端权限判断能力（组合式函数，供 `v-if`/计算属性场景直接调用），替代"各页面各写一套判断"。
- 侧边栏菜单（`SideNav.vue` + `menu.ts`）按当前用户权限编码集合过滤二级菜单项；一级分组下所有二级菜单都被过滤掉时，整个一级分组也不展示。
- 路由守卫（`router/index.ts` `beforeEach`）对带 `permissionKey` 的路由做权限校验：直接输入 URL 访问无权限页面时拦截并给出提示，不能只依赖"入口不展示"这一层防线。
- 已实现业务逻辑的页面（`identity/org`、`identity/user`、`identity/position`、`application/app`、`permission/role`、`permission/permission`、`permission/admin`、`system/menu`、`system/dict`、`system/metadatafields`、`system/formfields`、`system/log`，共 12 个视图，`权限资源.txt` 里登记的按钮级操作编码为准）逐个补齐按钮级权限门控：无权限的新增/编辑/详情/启用/停用/删除/导入等按钮不渲染；仍未实现业务逻辑的 `PlaceholderView` 页面本轮不处理按钮级门控（本来就没有真实按钮）。

## Capabilities

### New Capabilities
- `permission-driven-visibility`: 当前用户已授权权限编码集合的获取（后端接口 + 前端 store）、可复用的前端权限判断能力、路由守卫的权限拦截、业务页面按钮级权限门控这套完整机制。

### Modified Capabilities
- `navigation`: 侧边导航菜单渲染新增"按当前用户权限过滤"的要求——无权限的二级菜单项、以及子菜单全部无权限的一级分组，都不应该出现在侧边栏里。

## Impact

- 后端：新增 `auth` 模块下的一个 Controller 方法/接口（或复用 `PermissionController`，具体位置在 design.md 决定），不改变现有鉴权过滤器逻辑。
- 前端：新增权限 store、权限判断组合式函数；改动 `router/menu.ts`、`layout/components/SideNav.vue`、`router/index.ts`；改动 12 个已实现业务页面的按钮渲染逻辑；同步更新仓库根目录 `权限资源.txt`（若门控逻辑需要新增/调整编码，目前判断不需要新增编码，只是消费已有编码）。
- 不引入新的第三方依赖。
