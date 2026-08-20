## 1. 数据库迁移

- [x] 1.1 新增 `backend/src/main/resources/db/migration/V8__add_app_access_authorization.sql`，包含五张表（均含 `create_by/create_time/update_by/update_time` 四个审计字段，`create_time`/`update_time` 用 `DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP`/`ON UPDATE CURRENT_TIMESTAMP`，不建物理外键，字段命名核对不与 MySQL/PG/Oracle/SQL Server 保留字冲突）：
  - `tab_app_access_policy`：`id/name/remark/status(2000启用/3000停用)/last_exec_time/last_exec_by/审计字段`
  - `tab_app_access_policy_org_scope`：`id/policy_id/org_id/include_children(TINYINT(1))/审计字段`，`UNIQUE KEY(policy_id, org_id)`（参考 `tab_admin_org_scope` 范式）
  - `tab_app_access_policy_user_attr`：`id/policy_id/metadata_field_id/operator(VARCHAR，EQ/NE/IN)/attr_value(VARCHAR(255)，IN 时逗号分隔多值)/审计字段`，`UNIQUE KEY(policy_id, metadata_field_id)`（关联 `tab_metadata_field`，不建物理外键）
  - `tab_app_access_policy_target_app`：`id/policy_id/app_id/审计字段`，`UNIQUE KEY(policy_id, app_id)`
  - `tab_app_access_policy_grant`：`id/policy_id/user_id/app_id/审计字段`，`UNIQUE KEY(policy_id, user_id, app_id)`，`KEY(user_id, app_id)`（供最终权限合并查询命中索引）
  - `tab_app_access_manual_override`：`id/user_id/app_id/override_type(VARCHAR，GRANT/DENY)/remark/审计字段`，`UNIQUE KEY(user_id, app_id)`

## 2. 后端：策略规则模块

- [x] 2.1 新建包 `backend/src/main/java/cn/nihility/rbac/appaccess/policy/`，按 controller/dto/entity/mapper/mapstruct/service+impl 分层，`PolicyEntity`/`PolicyOrgScopeEntity`/`PolicyUserAttrEntity`/`PolicyTargetAppEntity` 对应 1.1 的四张策略相关表
- [x] 2.2 `PolicyService`/`PolicyServiceImpl`：新增（组织范围+用户属性条件+目标应用整体写入）、编辑（整体替换语义，先删后插）、分页查询、启用/停用、删除（级联删除 `tab_app_access_policy_grant` 中 `policy_id` 匹配的记录，同一事务）；校验规则：组织范围与用户属性条件不能同时为空、目标应用不能为空、用户属性条件关联的元数据字段必须 `biz_type=USER` 且启用、同一策略内元数据字段不允许重复
- [x] 2.3 `PolicyController`：`page/detail/create/update/enable/disable/delete` 接口，补充 springdoc-openapi 注解
- [x] 2.4 DTO：`PolicyCreateRequest`/`PolicyUpdateRequest`（含组织范围列表、用户属性条件列表`[{metadataFieldId, operator, values}]`、目标应用 id 列表）、`PolicyVO`（含组织范围/用户属性条件/目标应用回显——属性条件回显时带上元数据字段名称/编码便于前端展示、`lastExecTime`/`lastExecBy`、"配置是否已变更待重新执行"标记——比较组织范围/用户属性条件/目标应用最新 `update_time` 是否晚于 `last_exec_time`，纯查询判断，不新增状态字段）

## 3. 后端：策略执行

- [x] 3.1 新增 `PolicyExecutionService`（或合并进 `PolicyServiceImpl`）：`execute(policyId)` 方法——① 若配置了组织范围，用 `OrgDescendantExpander.expandWithDescendants` 展开 `org_id` 集合（`include_children=1` 的展开子孙，`include_children=0` 的只含自身），查询 `tab_user_position`（`status <> -1000`）中 `org_id` 落在展开集合内的去重 `user_id` 得集合 A，未配置组织范围则跳过（不限定）；② 若配置了用户属性条件，逐条按 `metadata_field_id` 取 `column_name` 拼 `tab_user.<column_name>` 的 `EQ`/`NE`/`IN` 查询（`column_name` 需通过白名单/既有校验后才可拼接，见 design.md 风险缓解），多条条件间取交集得集合 B，未配置属性条件则跳过；③ 命中用户 = 都配置时 A∩B，只配置一类时取该类结果；④ 与该策略的目标应用集合做笛卡尔积得到 `(user_id, app_id)` 全集；⑤ 事务内 `DELETE FROM tab_app_access_policy_grant WHERE policy_id=:id` 后批量插入新集合，更新 `last_exec_time`/`last_exec_by`
- [x] 3.2 `PolicyController` 新增 `POST /api/app-access/policies/{id}/execute` 接口，补充 springdoc-openapi 注解

## 4. 后端：人工例外模块

- [x] 4.1 新建包 `backend/src/main/java/cn/nihility/rbac/appaccess/override/`，`ManualOverrideEntity` 对应 `tab_app_access_manual_override`
- [x] 4.2 `ManualOverrideService`/`ManualOverrideServiceImpl`：新增/更新（按 `user_id+app_id` upsert 语义，已存在则更新 `override_type`/`remark` 而不是新增一行）、分页查询（支持按 `userId`/`appId`/`overrideType` 过滤）、删除
- [x] 4.3 `ManualOverrideController`：`page/upsert/delete` 接口，补充 springdoc-openapi 注解，DTO：`ManualOverrideUpsertRequest`/`ManualOverrideVO`

## 5. 后端：最终生效权限计算与查询（唯一实现，供管理端与 SSO 共用）

- [x] 5.1 新建 `AppAccessEffectivePermissionService`（放在 `appaccess` 包顶层或 `appaccess/support/`）：`isAuthorized(userId, appId)` 单点判定方法——① 查 `tab_app_access_manual_override` 是否存在 `DENY`，存在则 `false`；② 否则查是否存在 `GRANT`，存在则 `true`；③ 否则查 `tab_app_access_policy_grant JOIN tab_app_access_policy ON policy_id=id AND status=2000` 是否存在该 `user_id+app_id` 的记录，存在则 `true`；④ 否则 `false`
- [x] 5.2 同一 Service 新增 `listEffectiveByUser(userId, includeRevoked)` 与 `listEffectiveByApp(appId)` 分页查询方法，返回结果标明判定依据（`MANUAL_GRANT`/`POLICY`，命中策略时列出策略名称列表；`includeRevoked=true` 时额外返回被 `DENY` 收回但本应命中策略/GRANT 的应用，标记为"已收回"）
- [x] 5.3 新增对应 Mapper 方法/XML（放在 `backend/src/main/resources/mybatis/mapper/`），标准可移植 SQL（不用窗口函数/CTE），JOIN 查询 `tab_app_access_policy_grant`/`tab_app_access_policy`/`tab_app_access_manual_override`/`tab_app`/`tab_user` 取展示所需的应用名称/用户名称等
- [x] 5.4 `AppAccessQueryController`：`GET /api/app-access/effective/by-user/{userId}`、`GET /api/app-access/effective/by-app/{appId}` 两个查询接口，补充 springdoc-openapi 注解

## 6. 后端：SSO 登录拦截接入

- [x] 6.1 新增 `AppAccessAuthorizationChecker`（放在 `backend/src/main/java/cn/nihility/rbac/sso/support/`，与 `AppProtocolGuard` 同级），依赖 5.1 的 `AppAccessEffectivePermissionService`，提供 `assertAuthorized(Long userId, Long appId)`：未授权时 `throw new SsoProtocolException(...)`（复用现有异常类型与提示文案风格）
- [x] 6.2 `CasController.login`（`backend/src/main/java/cn/nihility/rbac/sso/cas/controller/CasController.java`）：在拿到 `userId`（`SsoSessionService.verify` 结果）与路径变量 `appId` 之后、`casTicketService.issue(...)` 调用之前，插入 `appAccessAuthorizationChecker.assertAuthorized(userId, appId)` 调用；不改动现有 `catch (SsoProtocolException e)` 分支
- [x] 6.3 `OAuthController.authorize`（`backend/src/main/java/cn/nihility/rbac/sso/oauth/controller/OAuthController.java`）：在拿到 `userId` 与 `client_id` 反查得到的 `appId` 之后、`oAuthTokenService.issueCode(...)` 调用之前，插入同样的校验调用

## 7. 前端

- [x] 7.1 `frontend/src/router/menu.ts`：权限管理分组 `children` 新增一项"应用访问授权"（`path: '/permission/app-access'`，`permissionKey: 'AppAccessManagement:appAccess:view'`）
- [x] 7.2 `frontend/src/router/index.ts` 新增对应路由项，指向新页面组件
- [x] 7.3 `frontend/src/types/appAccess.ts`：策略规则、人工例外、最终生效权限查询结果的类型定义，字段命名与后端 DTO 对齐
- [x] 7.4 `frontend/src/api/appAccess.ts`：策略规则 CRUD/执行、人工例外 CRUD、最终生效权限查询的 axios 封装
- [x] 7.5 新增 `frontend/src/views/permission/app-access/AppAccessView.vue`（外层 `el-tabs` 三个板块容器）
- [x] 7.6 新增策略规则子页面/组件（`PolicyRulePanel.vue`）：列表（含"待重新执行"提示、启用/停用、执行、删除按钮）+ 新建/编辑表单（组织范围多选支持"含子组织"勾选；用户属性条件多行可增删，每行选元数据字段（来自 `biz_type=USER` 目录，接口复用 `metadataFieldApi.getMetadataFieldPageForSyncDomain` 已有的元数据字段选择接口）+ 运算符（等于/不等于/属于多值）+ 比较值（`IN` 时为可增删的多值输入）；组织范围与属性条件均可留空但校验至少填一类；目标应用多选）
- [x] 7.7 新增人工例外子页面/组件（`ManualOverridePanel.vue`）：列表（按用户/应用/类型过滤）+ 新建/编辑表单（选用户、选应用、选 GRANT/DENY、备注）+ 删除
- [x] 7.8 新增最终生效权限查询子页面/组件（`EffectiveQueryPanel.vue`）：切换"按用户查询"/"按应用查询"，结果表格展示判定依据（人工授权/策略名称列表/已收回)，"包含已收回"开关

## 8. 测试

- [x] 8.1 `PolicyServiceImplTest`：覆盖新增/编辑整体替换语义、组织范围与用户属性条件同时为空校验拒绝、目标应用为空校验拒绝、属性条件关联非 USER 域/已停用元数据字段被拒绝、同一策略内属性字段重复被拒绝、删除级联清理授权记录
- [x] 8.2 `PolicyExecutionServiceTest`（或合并测试类）：覆盖仅组织范围、仅属性条件、组织范围+属性条件取交集三种组合、组织范围展开含子组织/不含子组织、`EQ`/`NE`/`IN` 三种运算符匹配、命中用户去重、笛卡尔积生成、重新执行整体替换、执行不影响人工例外
- [x] 8.3 `ManualOverrideServiceImplTest`：覆盖新增 GRANT/DENY、重复提交同一用户应用组合触发更新而非新增、删除后退回策略判定
- [x] 8.4 `AppAccessEffectivePermissionServiceTest`：覆盖优先级规则四种场景（DENY 最高、GRANT 次之、策略再次之、都无则不可访问）、停用中的策略不计入、策略重新启用后立即计入（无需重新执行）
- [x] 8.5 `AppAccessAuthorizationCheckerTest` 或在 `CasControllerTest`/`OAuthControllerTest` 中新增用例：覆盖未授权用户被拒绝签发票据/授权码、已授权用户正常签发
- [x] 8.6 `./gradlew test`（在 `backend/` 目录下）通过

## 9. 文档

- [x] 9.1 更新根目录 `权限资源.txt`，新增 `AppAccessManagement` 模块下页面级 `appAccess:view`（含"最终生效权限查询"板块的只读查询，不单独设按钮权限点）、策略规则 `policy:add/edit/enable/disable/execute/delete`、人工例外 `override:add/delete`（新增与编辑复用同一个 `override:add` 权限点，不单独拆 `edit`）等权限点编码，与前端按钮实际实现保持一致
- [x] 9.2 `npm run build`（在 `frontend/` 目录下）通过

## 10. OpenSpec 收尾

- [x] 10.1 实现完成后运行 `openspec-doc-sync` 对齐 `proposal.md`/`design.md`/`tasks.md` 与实际改动
- [ ] 10.2 视用户指示决定是否执行 `openspec-sync-specs` 把本变更的 delta spec 应用到 `openspec/specs/app-access-authorization/spec.md`（新建）与 `openspec/specs/app-sso-protocol-runtime/spec.md`（归档仍为用户手动触发，不自动执行）

## 11. 修正：默认管理员菜单/权限点种子数据（实现后补）

- [x] 11.1 发现 V8 迁移只建表、没有补 `tab_menu`/`tab_permission`/`tab_role_permission` 种子数据，导致默认超级管理员（`SUPER_ADMIN` 角色，其权限点集合是 V1 迁移时一次性 `INSERT...SELECT` 全量 `tab_permission` 生成的，新增权限点不会自动补授权）看不到"应用访问授权"菜单；新增 `backend/src/main/resources/db/migration/V9__add_app_access_authorization_menu.sql`：插入 1 个页面级菜单节点（挂在"权限管理"一级分组下）+ 8 个按钮级资源共 9 条 `tab_menu` 记录、对应的 9 条 `tab_permission` 记录，并显式 `INSERT INTO tab_role_permission` 授予 `SUPER_ADMIN` 角色
- [x] 11.2 `./gradlew test`（在 `backend/` 目录下）确认新增迁移脚本应用无误，全部测试通过
