> 本 tasks.md 是二次设计后的版本：第 1-5 节相对首次实现有较大调整（一次性批量操作 →
> 持久规则 + 事件驱动自动重算），标注了"删除重做"与"在已有基础上修改"的区分，避免重复
> 劳动。首次实现已完成的代码（`tab_user_role` 表、`UserRoleEntity`/`UserRoleMapper`/
> `UserRoleService`、角色管理页面的一次性弹窗等）按本版本要求删除或改写。

## 1. 数据库迁移

- [x] 1.1 修改 `V4__add_user_role.sql`（首次实现已创建，尚未提交/发布，直接改写内容，
      不做兼容性迁移）：删除 `tab_user_role` 建表语句，改为建 `tab_user_role_rule`、
      `tab_user_role_rule_org_scope`、`tab_user_role_rule_user_attr`、
      `tab_user_role_rule_grant` 四张表（见 design.md Decision 1 完整 DDL）。
- [x] 1.2 新增迁移脚本 `V6__add_admin_auto_created_role_id.sql`（`V5` 是权限点种子数据，
      保留不变）：`ALTER TABLE tab_admin ADD COLUMN auto_created_role_id BIGINT NULL`。
- [x] 1.3 本地起库验证两个迁移脚本可正常执行（含修复本地开发库里旧版 `V4`/`V5` 已执行
      记录与 schema 不一致导致的 Flyway 校验和冲突，纯数据库状态修复，未改动任何脚本/
      代码内容）。

## 2. 后端：删除首次实现的一次性批量操作代码

- [x] 2.1 删除 `UserRoleEntity`、`UserRoleMapper`（含 XML）、`UserRoleService`/
      `UserRoleServiceImpl`、`UserRoleController`、`UserRoleBatchAssignRequest`/
      `UserRoleBatchAssignResponse`/`UserRoleMatchedUserVO`（一次性版本）等仅服务于一次性
      批量操作的类；`UserMatchConditionResolver`（含其依赖的 `UserPositionMapper#
      selectIdsByAttrCondition`、`UserRoleOrgScopeCondition`、`UserRoleUserAttrCondition`、
      `UserRoleAttrOperator`）**保留**，规则执行引擎直接复用（design.md Decision 2）。
- [x] 2.2 删除对应的 `UserRoleServiceImplTest`（保留 `UserMatchConditionResolverTest`）。

## 3. 后端：用户角色规则 CRUD 与执行引擎（`cn.nihility.rbac.userrole` 包扩展）

- [x] 3.1 新增 `UserRoleRuleEntity`（对齐 `tab_user_role_rule`）、
      `UserRoleRuleOrgScopeEntity`、`UserRoleRuleUserAttrEntity`、
      `UserRoleRuleGrantEntity`，及各自 Mapper。
- [x] 3.2 新增 `UserRoleRuleService`/`UserRoleRuleServiceImpl`：`listByRoleId`（返回轻量
      摘要，`orgScopes`/`userAttrs` 为空数组，不内嵌条件明细，与 `GET /admins` 列表不带
      子集合的既有约定一致）、`getById`（详情接口，含完整条件明细，供编辑表单回填）、
      `preview`、`create`（保存即执行）、`update`（整体替换条件子表后重新执行）、
      `delete`（先收回关联再物理删除）。
- [x] 3.3 新增 `UserRoleRuleExecutionService`/`UserRoleRuleExecutionServiceImpl`：
      `execute(ruleId, operator)` 完整流程（计算命中集合 → 与既有 grant 差集 → 批量增删
      → 对 `toRemove` 逐用户检查管理员联动停用 → 更新 `lastExecTime`/`lastExecBy`），
      `operator` 为显式参数，服务实现不依赖 `CurrentOperatorService`。
- [x] 3.4 新增 `UserRoleRuleController`：`GET /api/user-role-rules?roleId=`（列表，轻量）、
      `GET /api/user-role-rules/{id}`（详情）、`POST /api/user-role-rules`、
      `PUT /api/user-role-rules/{id}`、`DELETE /api/user-role-rules/{id}`、
      `POST /api/user-role-rules/preview`；springdoc 注解完整；权限点通过 `menu` 请求头
      + `IdentityAuthFilter`/`AuthorizationService` 数据驱动校验，Controller 无需额外
      注解（与仓库既有 Controller 权限接入方式一致）。
- [x] 3.5 单元测试：`UserRoleRuleExecutionServiceTest`、`UserRoleRuleServiceImplTest`
      覆盖新增/收回/多规则不误删/级联收回/条件校验等场景。

## 4. 后端：领域变更事件接入自动重算

- [x] 4.1 `DomainChangeEventProcessor#process`：新增并列的
      `reExecuteUserRoleRulesIfNeeded(event)`，判断 `dataType` 属于 ORG/USER/POSITION 后
      查出全部用户角色规则，逐条 `execute(rule.getId(), event.getOperator())`，单条失败
      仅记日志、不影响其余规则和原始写请求，写法与既有 `reExecutePoliciesIfNeeded` 一致。
- [x] 4.2 `DomainChangeEventProcessorTest` 补充用例，验证新分支被正确调用且 `operator`
      严格来自 `event.getOperator()`。

## 5. 后端：管理员按角色批量设置管理员——数据来源切换 + 联动停用标记

- [x] 5.1 `AdminService#previewBatchPromoteByRole`/`batchPromoteByRole`：查询"持有该角色
      的用户"的数据来源从已删除的 `tab_user_role` 改为 `tab_user_role_rule_grant`。
- [x] 5.2 "将新建管理员"分组执行创建时设置 `auto_created_role_id`；"将补充角色"分组的
      `appendRoleIfMissing` 路径不设置该字段。
- [x] 5.3 单元测试更新：新建管理员断言 `auto_created_role_id` 已正确赋值；补充角色断言
      该字段保持 `NULL`。

## 6. 前端：角色管理"批量规则"入口重做

- [x] 6.1 `RoleManagementView.vue`：把"批量加用户"一次性弹窗改为"规则列表"（名称、备注、
      最近执行时间、当前命中人数，新增/编辑/删除）+"新增/编辑规则"表单（复用组织范围/
      用户属性条件动态子表单，新增规则名称必填输入框），删除前二次确认提示收回关联；
      编辑表单打开时单独调用规则详情接口回填完整条件（不复用列表行数据）。
- [x] 6.2 `frontend/src/api/role.ts`：新增 `listUserRoleRules`、`getUserRoleRuleById`、
      `previewUserRoleRule`、`createUserRoleRule`、`updateUserRoleRule`、
      `deleteUserRoleRule`；对接时发现并修复了后端响应字段命名差异（`hitCount` vs 前端
      `matchedUserCount`），已在 API 封装层做字段名转换，不影响组件代码。
- [x] 6.3 `frontend/src/types/role.ts`：新增规则列表行、规则详情、规则表单请求体、保存
      响应对应的类型定义（`UserRoleRuleRow`/`UserRoleRuleDetail`/`UserRoleRuleFormRequest`/
      `UserRoleRuleSaveResult`），移除一次性批量操作专用类型。
- [x] 6.4 权限点接入方式不变（`hasPermission('RoleManagement:role:batchAssignUser')`）。

## 7. 前端：管理员管理按角色批量设置管理员——无需改动

- [x] 7.1 确认 `AdminManagementView.vue`、`api/admin.ts`、`types/admin.ts` 无需调整
      （接口路径、请求/响应字段形状均不变），`npm run build` 确认无类型错误。

## 8. 权限资源编码文档

- [x] 8.1 `权限资源.txt` 两条权限点描述文案已更新（"批量添加用户角色"→"维护批量添加
      用户角色的规则"；"按角色批量设置管理员"补充自动创建标记与联动停用说明），编码本身
      不变，未新增/删除权限点。

## 9. OpenSpec spec 同步

- [x] 9.1 重写 delta spec `specs/user-role-assignment/spec.md`：MODIFIED"用户角色关联的
      写入语义"、REMOVED"预览与批量执行共用同一套匹配逻辑"（含 Reason/Migration）、
      ADDED 四条新 Requirement（规则持久化管理、保存即执行、事件驱动自动重算、删除级联
      收回）。
- [x] 9.2 `specs/role-management/spec.md`：MODIFIED"角色管理页面批量添加用户角色"，改为
      规则列表 + 新增/编辑/删除交互描述。
- [x] 9.3 `specs/admin-management/spec.md`：MODIFIED"按角色批量设置管理员"（补充自动创建
      标记语义），ADDED"角色收回联动停用自动创建的管理员"。
- [x] 9.4 已同步进 `openspec/specs/`（`openspec validate --specs` 36 项全部通过）。

## 10. 回归验证

- [x] 10.1 组织范围/用户属性条件命中：`UserMatchConditionResolverTest` 覆盖。
- [x] 10.2 规则保存即执行：`UserRoleRuleServiceImplTest`/`UserRoleRuleExecutionServiceTest`
      覆盖，新增规则后立即产生对应的 `tab_user_role_rule_grant` 记录。
- [x] 10.3 事件驱动自动重算：`DomainChangeEventProcessorTest` 覆盖 ORG/USER/POSITION 触发
      场景（对应用户最初反馈的问题场景，单元测试层面验证）。
- [x] 10.4 收回场景：`UserRoleRuleExecutionServiceTest` 覆盖"用户不再命中时收回"、"仍被
      其他规则命中时不误收回"。
- [x] 10.5 规则删除级联收回：`UserRoleRuleExecutionServiceTest`/`UserRoleRuleServiceImplTest`
      覆盖。
- [x] 10.6 管理员联动停用：`UserRoleRuleExecutionServiceTest`（收回触发停用）、
      `AdminServiceImplTest`（自动创建标记正确性）覆盖。
- [x] 10.7 按角色批量设置管理员三种分组、编码冲突场景：`AdminServiceImplTest` 覆盖，数据
      来源切换后结果不变。
- [x] 10.8 无权限用户看不到/无法触发两个入口：权限点接入代码走查确认（`hasPermission`
      指令 + 后端数据驱动鉴权）。
- [ ] 10.9 浏览器交互式实测：**未做**（本次同样未起 `npm run dev` 做真实点击验证），是
      持续存在的已知缺口，正式发布前建议手工过一遍规则列表新增/编辑/删除的完整交互流程，
      以及事件自动重算（比如实际新增一条任职记录后观察角色是否自动生效）。

## 附：全量测试结果

`./gradlew build`（编译 + 全量测试）：`BUILD SUCCESSFUL`，1100 个测试全部通过，0 失败。
`npm run build`（vue-tsc 类型检查 + vite build）：通过，0 类型错误。
