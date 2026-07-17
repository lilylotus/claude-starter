## 1. 数据库

- [x] 1.1 新建 `backend/src/main/resources/db/migration/V12__init_tab_admin.sql`：
  按 `design.md` 决策 1 建 `tab_admin`、`tab_admin_role`、`tab_admin_org_scope`
  三张表（已用本地 MySQL 验证 Flyway 迁移应用成功，3 张表存在）

## 2. 后端：管理员模块

- [x] 2.1 `cn.nihility.rbac.admin.constant.AdminStatus`（2000/3000/-1000）
- [x] 2.2 `cn.nihility.rbac.admin.entity`：`AdminEntity`、`AdminRoleEntity`、
  `AdminOrgScopeEntity`
- [x] 2.3 `cn.nihility.rbac.admin.mapper`：`AdminMapper`、`AdminRoleMapper`、
  `AdminOrgScopeMapper`（均为 `BaseMapper` 接口）
- [x] 2.4 `resources/mybatis/mapper/AdminMapper.xml`（`selectAdminPage`/
  `selectAdminDetail`，JOIN `tab_user` 回填 `userName`）、
  `AdminRoleMapper.xml`（`selectRolesByAdminId`，JOIN `tab_role`）、
  `AdminOrgScopeMapper.xml`（`selectOrgScopesByAdminId`，JOIN `tab_org`）
- [x] 2.5 `cn.nihility.rbac.admin.dto`：`AdminVO`（含 `userName`、
  `roles: List<AdminRoleVO>`、`orgScopes: List<AdminOrgScopeVO>`）、
  `AdminRoleVO`（`roleId`/`roleName`）、`AdminOrgScopeVO`（`orgId`/`orgName`/
  `includeChildren`）、`AdminCreateRequest`/`AdminUpdateRequest`（含
  `roleIds: List<Long>`、`orgScopes: List<AdminOrgScopeRequest>`）、
  `AdminOrgScopeRequest`（`orgId`/`includeChildren`，均 `@NotNull`）
- [x] 2.6 `cn.nihility.rbac.admin.mapstruct.AdminConvert`（静态单例，非 Spring
  bean，参照 `OrgConvert`/`RoleConvert` 写法）
- [x] 2.7 `cn.nihility.rbac.admin.service.AdminService` +
  `impl.AdminServiceImpl`：分页查询、详情（含角色/组织管辖范围回填）、创建、
  更新、启用、停用、逻辑删除；`code`/`userId` 唯一性校验；角色/组织管辖范围
  按"整体同步"（先删后插）维护，参照 `design.md` 决策 1、`UserServiceImpl` 的
  实现风格；新增 `AdminServiceImplTest`（13 个用例，覆盖分页、唯一性校验、
  角色/组织管辖范围同步、启停用/删除不影响关联关系等场景）
- [x] 2.8 `cn.nihility.rbac.admin.controller.AdminController`：
  `GET/POST /api/admins`、`GET/PUT/DELETE /api/admins/{id}`、
  `PUT /api/admins/{id}/enable`、`PUT /api/admins/{id}/disable`，加
  springdoc-openapi 注解（`@Tag`、`@Operation`）

## 3. 后端：角色选项接口

- [x] 3.1 `RoleService` 新增 `getEnabledOptions()`；`RoleController` 新增
  `GET /api/roles/options`，返回 `List<RoleOptionVO>`（`id`/`name`/`code`），
  仅含未删除且启用的角色，`showOrder` 降序、`id` 升序；`RoleServiceImplTest`
  新增对应用例

## 4. 前端：管理员管理页面

- [x] 4.1 `types/admin.ts`：`AdminRow`、`AdminRoleRow`、`AdminOrgScopeRow`、
  `AdminOrgScopeFormItem`、`AdminFormRequest`、
  `ADMIN_STATUS_ENABLED`/`ADMIN_STATUS_DISABLED` 常量，字段与后端 DTO 对齐
- [x] 4.2 `api/admin.ts`：分页查询、详情、新增、编辑、启用、停用、删除的 axios
  封装
- [x] 4.3 `types/role.ts` 补充 `RoleOption` 类型；`api/role.ts` 新增
  `getRoleOptions()` 封装（`GET /api/roles/options`）
- [x] 4.4 `views/permission/admin/AdminManagementView.vue`：分页表格（列：管理员
  名称、管理员编码、关联用户、显示序号、状态、操作，`showOrder` 降序展示）+
  新增/编辑弹窗（关联用户远程搜索单选、管理员角色多选下拉、管辖组织范围动态
  多行子表单）+ 只读详情弹窗 + 启用/停用/删除行操作；组织树延迟到打开弹窗时
  才请求（参照 `AppManagementView.vue` 现有写法）
  - 额外新增 `stores/admin.ts`（Pinia store，`useAdminStore`），任务拆分时
    未预先列出：因为本页面的列表分页交互沿用了角色/权限点等模块"store 承载
    列表状态"的既有模式（`stores/role.ts` 同款），View 直接依赖 store 而不是
    在组件内部自管分页状态，属于实现阶段对既有约定的合理补充，不是偏离设计

## 5. 前端：挂载新页面

- [x] 5.1 `router/menu.ts`：`permission` 分组下新增
  `{ title: '管理员管理', path: '/permission/admins', permissionKey:
  'permission:admin:view' }`
- [x] 5.2 `router/index.ts`：`implementedComponents` 补充
  `'/permission/admins': () => import('@/views/permission/admin/AdminManagementView.vue')`

## 6. 权限资源清单与菜单种子数据（实现完成后处理，不委托给子 agent）

- [x] 6.1 更新仓库根目录 `权限资源.txt`，新增
  `AdminManagement:admin:view/add/edit/detail/enable/disable/delete` 七条编码
- [x] 6.2 新增 `backend/src/main/resources/db/migration/V13__seed_admin_menu_resource_data.sql`，
  把上面 7 条编码写入 `tab_menu`（挂在 `permission` 一级分组下，`AdminManagement:
  admin:view` 的 `showOrder=5`，低于同分组下既有的 `RoleManagement:role:view`
  （20）、`PermissionManagement:permission:view`（10），使管理员管理排在角色/
  权限点管理之后，与 `router/menu.ts` 里 `permission` 分组 children 数组的声明
  顺序一致）；已用本地 MySQL 验证 Flyway 迁移应用成功（`flyway_schema_history`
  `version=13`），7 行正确挂在 `permission` 一级分组节点下

## 7. 验证

- [x] 7.1 `./gradlew build`（含 `AdminServiceImplTest`、`RoleServiceImplTest`
  等全部测试类，Flyway 迁移随之执行）通过，V12/V13 迁移已在本地 `rbac` 库验证
  应用成功
- [x] 7.2 `npx vue-tsc --noEmit`（`frontend/`）通过，无类型错误
- [x] 7.3 真实浏览器验证：启动 `bootRun`（48080）+ `vite --host 127.0.0.1`
  （5173），用 Playwright（本地已缓存 chromium，临时装到 scratchpad 目录，未写
  入项目依赖）登录后驱动管理员管理页面全流程——①进入页面时只请求
  `/api/admins` 分页接口，未触发 `GET /api/orgs/tree`；②点击"新增"后才唯一一次
  请求 `GET /api/orgs/tree`；③新增弹窗内完整走完关联用户远程搜索单选、管理员
  角色多选、管辖组织范围动态行（组织树单选 + 含子组织勾选）并提交成功，新行
  出现在列表中；④详情弹窗正确展示角色名称与组织名称；⑤停用/启用切换正常，
  状态标签同步刷新；⑥删除后行从列表消失。全部步骤通过（`ALL STEPS PASSED`）。
  另外直接用 curl 对 `/api/admins`、`/api/roles/options` 做了一轮独立的接口级
  验证（创建、分页列表不含 roles/orgScopes、详情包含、更新时角色/组织范围整体
  替换、启停用不影响关联、`userId` 重复关联被拒绝、逻辑删除后详情报业务错误），
  行为与 `design.md`/spec 完全一致。验证完毕后已停止临时启动的两个 dev server
  进程，清理了测试期间创建的管理员数据，scratchpad 里的验证脚本与临时 npm
  依赖未写入项目仓库
