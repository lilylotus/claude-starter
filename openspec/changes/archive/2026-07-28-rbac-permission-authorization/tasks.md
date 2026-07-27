## 1. 数据库迁移：角色权限点关联表

- [x] 1.1 新增 `backend/src/main/resources/db/migration/V5__add_role_permission_table.sql`：创建 `tab_role_permission` 表（`id`、`role_id`、`permission_id`、审计字段），`(role_id, permission_id)` 唯一索引，模式对齐既有 `tab_admin_role`
- [x] 1.2 核对 `role_id`/`permission_id` 列名与 MySQL/PostgreSQL/Oracle/SQL Server 保留字无冲突

## 2. 数据库迁移：权限点种子数据与默认账号引导

- [x] 2.1 写一个一次性脚本，解析仓库根目录 `权限资源.txt` 中全部 `模块:资源:操作` 三段式编码行（编码 + 编码后的中文描述），生成 `tab_permission` 的批量 INSERT SQL 片段（按模块分段、加注释），**不要手工逐条转抄**——上次手工转写 RSA 密钥导致过一次真实 bug，94 条数据的手工转抄同样容易出错
- [x] 2.2 新增 `backend/src/main/resources/db/migration/V6__seed_permissions_and_super_admin.sql`：用 2.1 生成的内容种子化全部权限点记录（`status=2000`）；新增一条"超级管理员"角色记录（`tab_role`）；在 `tab_role_permission` 里把该角色与全部种子权限点关联
- [x] 2.3 同一份迁移（或紧接的下一个版本）里，给默认账号 `admin`（`tab_user.code='admin'`，`password-login-auth` change 已种子化）新增一条 `tab_admin` 记录（`status=2000`），并在 `tab_admin_role` 里关联到 2.2 新增的"超级管理员"角色，写法参照既有 `V3__seed_default_admin_user.sql` 用 `SELECT ... INTO @变量` 拿关联 id 的模式

## 3. 后端：角色模块新增权限点分配能力

- [x] 3.1 新增 `RolePermissionEntity`/`RolePermissionMapper`（`extends BaseMapper`），新增自定义方法 `selectPermissionsByRoleId(roleId)`（JOIN `tab_permission` 回填名称/编码），SQL 写在 `resources/mybatis/mapper/RolePermissionMapper.xml`，风格对齐 `AdminRoleMapper`/`AdminRoleMapper.xml`
- [x] 3.2 `RoleCreateRequest`/`RoleUpdateRequest` 新增 `permissionIds: List<Long>`（可选）；`RoleVO` 新增 `permissions: List<PermissionOptionVO>`（`id`/`name`/`code`）
- [x] 3.3 `RoleServiceImpl` 新增/更新逻辑：创建时按 `permissionIds` 批量插入 `tab_role_permission`；更新时整体同步（先按 `role_id` 物理删除既有关联，再按新 `permissionIds` 重新插入），风格对齐 `AdminServiceImpl#syncRoles`；详情查询组装 `permissions` 列表
- [x] 3.4 补充/调整单元测试，覆盖"创建角色时同时分配权限点""更新角色整体覆盖权限点分配""更新角色清空权限点分配""角色详情返回已分配权限点"等场景（对应 spec.md 中的 Scenario）

## 4. 后端：权限点模块新增选项查询接口

- [x] 4.1 `PermissionController` 新增 `GET /api/permissions/options`，加 springdoc `@Operation` 注解
- [x] 4.2 `PermissionServiceImpl` 实现：仅返回未删除且 `status=2000` 的权限点，按 `showOrder` 降序、`id` 升序排列，返回 `id`/`name`/`code`
- [x] 4.3 单元测试覆盖"只返回启用状态权限点""按显示序号排序"

## 5. 后端：运行时鉴权引擎

- [x] 5.1 `PermissionMapper` 新增自定义方法 `selectGrantedPermissionCodesByUserId(userId)`，SQL 写在 `PermissionMapper.xml`：`JOIN tab_admin → tab_admin_role → tab_role(status=2000) → tab_role_permission → tab_permission(status=2000)`，按 `tab_admin.user_id` 过滤，`DISTINCT` 返回权限编码集合
- [x] 5.2 `AuthErrorCode` 新增 `FORBIDDEN = 403` 常量，注释说明与 `UNAUTHORIZED`/`FIRST_LOGIN_REQUIRED` 的区分
- [x] 5.3 新增鉴权判断服务（如 `cn.nihility.rbac.auth.service.AuthorizationService`，方法 `hasPermission(Long userId, String menuCode)`），内部调用 5.1 的查询做 `Set.contains` 判断，不引入缓存（design.md Decision 4 已定）
- [x] 5.4 `IdentityAuthFilter` 在现有 `identity-token` 校验 → `menu` 头格式校验 → 首登拦截全部通过后，追加调用 5.3 的判断，不通过则用既有的"直接手写 JSON 响应"方式返回 `FORBIDDEN`；修改密码接口白名单（`FIRST_LOGIN_WHITELIST`）同时豁免这一步权限判断（详见 design.md 补充说明——该接口是不区分权限点的自助操作，其资源编码未被种子化进权限点数据，若不豁免会导致包括默认账号在内的一切用户在首登改密这一步被自己引入的鉴权机制锁死）
- [x] 5.5 补充/调整 `IdentityAuthFilterTest`，覆盖"有权限放行""无权限拦截"场景；新增 `AuthorizationServiceImplTest` 覆盖"无管理员身份用户零权限""每次实时查库、不缓存（等价于权限点被停用/角色被移除权限点后立即生效）"等场景（对应 spec.md 中的 Scenario）

## 6. 前端：权限点 API 封装

- [x] 6.1 `src/api/permission.ts` 新增 `getPermissionOptions()` 调用 `GET /api/permissions/options`
- [x] 6.2 `src/types/permission.ts`（如需要）新增权限点选项类型

## 7. 前端：角色管理新增权限点分配交互

- [x] 7.1 `src/types/role.ts` 更新：`RoleFormRequest` 新增 `permissionIds: number[]`，角色详情类型新增 `permissions`
- [x] 7.2 `RoleManagementView.vue` 新增/编辑弹窗内嵌权限点勾选控件：用 `el-tree`（`show-checkbox`、`node-key`）构造按编码模块段（冒号分隔第一段）分组的两层树，打开弹窗时按需请求 `getPermissionOptions()`，页面进入/翻页/搜索不触发该请求；编辑时回填已分配权限点为选中状态；提交时把选中的叶子节点 id 收集为 `permissionIds`
- [x] 7.3 `RoleDetailView.vue` 按同样的模块分组展示已分配权限点，只读，不提供勾选交互
- [ ] 7.4 补充前端交互的手动验证：新增角色时勾选权限点、编辑角色时回填并调整、详情页正确展示分组后的权限点——本次未在浏览器里实际点击验证（无浏览器自动化工具、也无可用的真实后端环境核对回填数据），仅完成 `npm run build` 类型检查 + 打包验证；留待用户按第 9 节用真实后端做端到端核对时一并完成

## 8. 前端：无权限错误码处理

- [x] 8.1 `src/api/request.ts` 响应拦截器新增对 `code === 403` 的识别：不跳转、不清空登录态，`ElMessage.error(body.message || '无权限访问')` 并 reject

## 9. 联调与验证

- [x] 9.1 后端：`./gradlew build` 全量通过（编译 + 单元测试 + `check`）
- [x] 9.2 前端：`npm run build`（vue-tsc 类型检查 + vite build）通过
- [x] 9.3 本地联调（真实后端 + 真实本地 MySQL/Redis，非 mock，用 Python 脚本模拟浏览器端 RSA-OAEP 加密对运行中的接口做完整真实调用）：种子迁移执行后核对 `tab_permission`=94 条、`SUPER_ADMIN` 角色关联全部 94 条权限点、默认账号 `admin` 的 `tab_admin`/`tab_admin_role` 正确建立；用默认账号 `admin` 登录、完成首登改密后验证可以正常访问已授权业务接口（`RoleManagement:role:view` 成功）、访问未注册编码（`Fake:notreal:code`）返回 `403`；通过真实 `POST /api/roles`/`POST /api/users`/`POST /api/admins` 接口新建一个只分配 `UserManagement:user:view` 一个权限点的角色 + 关联该角色的管理员，验证该管理员登录后能访问被授权的编码（成功）、不能访问未授权编码（`RoleManagement:role:view` 返回 `403`）；验证把该角色的权限点整体清空（`PUT /api/roles/{id}` 传空 `permissionIds`）后，持有旧 access-key 的该用户**无需重新登录**、下一次请求立即被拦截为 `403`（验证了 design.md Decision 4"不缓存、每次实时查库"的效果）；验证一个有 `tab_user` 但没有 `tab_admin` 记录的普通登录用户对任意业务接口都返回 `403`（零权限校验）。13 项断言全部通过，未发现新缺陷。测试数据已清理。**前端 UI 未做浏览器点击验证**（`claude-in-chrome` 未安装，用户本次选择不安装）——已验证前端 `npm run build` 类型检查通过、且前端消费的接口契约（`RoleVO.permissions`、`PermissionOptionVO`、`permissionIds` 等）已在上面的真实后端联调中逐一验证，但 `el-tree` 权限点勾选控件的实际渲染/交互效果、详情页分组展示的实际观感未做可视化验证，如实记录，不声称已完成
- [x] 9.4 本次未新增/删除任何菜单或按钮，`权限资源.txt` 无需变更；已核对当前文件内容与实际实现一致
