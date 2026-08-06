## 1. 公共基础设施

- [x] 1.1 `CurrentOperatorService`：`resolveCode(): String` 改为 `resolveUserId(): Long`，
      Javadoc 同步更新。
- [x] 1.2 `CurrentOperatorServiceImpl`：方法体简化为直接返回 `CurrentUserContext.getUserId()`
      （校验 null 抛 `IllegalStateException`，不再注入/查询 `UserMapper`）。
- [x] 1.3 `CurrentOperatorServiceImplTest`：断言改为按用户 id 校验，移除 mock
      `UserMapper.selectById` 相关的用例逻辑。
- [x] 1.4 在 `user` 模块新增批量展示名解析能力（如
      `cn.nihility.rbac.user.service.UserDisplayService` + `impl`）：
      `Map<String, String> resolveDisplayNames(Collection<String> userIdTexts)`——输入
      是 entity `createBy`/`updateBy` 原样的字符串值，内部过滤空白/非数字值后转 `Long`
      批量查 `userMapper.selectBatchIds`，按 `姓名（账号编码）` 格式拼接，返回 `Map` 的
      key 用原始输入字符串；查不到/无法解析的不放入返回结果。补充对应单测（空集合、
      非数字脏值、部分 id 不存在、正常批量四种场景）。

## 2. 组织管理（org）

- [x] 2.1 `OrgConvert.toVO`：新增 `@Mapping(target = "createBy", ignore = true)` 与
      `updateBy` 同理（entity/VO 字段类型都不变，仍是 `String`，但内容语义不同，需要
      阻止自动映射直接把 id 文本复制到 VO 上）。
- [x] 2.2 `OrgServiceImpl`：写操作调用点改用
      `String.valueOf(currentOperatorService.resolveUserId())`；详情/列表查询方法接入
      `UserDisplayService` 批量回填 `createBy`/`updateBy` 展示名（参照现有 `parentName`
      回填的写法）。
- [x] 2.3 `OrgServiceImplTest`：断言/mock 同步改为按用户 id 校验。

## 3. 字典管理（dict）

- [x] 3.1 `DictConvert`：`toVO`（类型/字典项两套）新增 `createBy`/`updateBy` 的
      `ignore = true`。
- [x] 3.2 `DictTypeServiceImpl`/`DictItemServiceImpl`：写操作调用点改用
      `String.valueOf(currentOperatorService.resolveUserId())`；详情/列表查询接入批量
      展示名回填。
- [x] 3.3 `DictTypeServiceImplTest`/`DictItemServiceImplTest`：断言/mock 同步。

## 4. 用户与任职（user / position）

- [x] 4.1 `UserConvert.toVO`：新增 `ignore`。
- [x] 4.2 `UserServiceImpl`：写操作调用点改用
      `String.valueOf(currentOperatorService.resolveUserId())`；详情/列表接入展示名回填。
- [x] 4.3 `UserServiceImplTest`：断言/mock 同步。
- [x] 4.4 `PositionConvert`：`PositionVO`/`UserPositionVO` 两套映射都新增 `ignore`。
- [x] 4.5 `PositionServiceImpl`：写操作调用点改用
      `String.valueOf(currentOperatorService.resolveUserId())`；详情/列表（含
      `UserDetailView` 内嵌的任职列表来源接口）接入展示名回填。
- [x] 4.6 `PositionServiceImplTest`：断言/mock 同步。
- [x] 4.7 `PasswordServiceImpl`（`tab_user_password`，无对外 VO）实现走查后确认**无需改动**：
      它没有走 `CurrentOperatorService`，而是自带私有 `currentOperator()` 方法，已经是
      `CurrentUserContext.getUserId() != null ? String.valueOf(userId) : "system"`——落库
      内容已经是"用户 id 的字符串形式"，只在脱离登录上下文时 (如单测) 兜底成 `"system"`
      这个非数字占位值。这个兜底是有意为之（`PasswordServiceImplTest` 显式
      `CurrentUserContext.clear()` 后调用并断言 `createBy`/`updateBy` 等于 `"system"`），
      与 `CurrentOperatorService` 遇到脱离上下文场景直接抛异常的哲学不同，不应该强行统一：
      该表无对外 VO，`"system"` 这个非数字占位值不会进入 `UserDisplayService` 的展示名
      解析（会被当非法数字忽略），不影响本次改造目标。**保持现状，不修改此文件。**
- [x] 4.8 `PasswordServiceImplTest`：无需改动（现状已覆盖 `"system"` 兜底场景）。

## 5. 应用管理（app）

- [x] 5.1 `AppConvert.toVO`：新增 `ignore`。
- [x] 5.2 `AppServiceImpl`：写操作调用点改用
      `String.valueOf(currentOperatorService.resolveUserId())`；详情/列表接入展示名回填。
- [x] 5.3 `AppServiceImplTest`：断言/mock 同步。

## 6. 角色与权限点（role / permission）

- [x] 6.1 `RoleConvert.toVO`：新增 `ignore`。
- [x] 6.2 `RoleServiceImpl`：写操作调用点（含角色-权限关联的新增/删除，`RolePermissionEntity`
      无对外 VO 但落库值同样要改）改用 `String.valueOf(currentOperatorService
      .resolveUserId())`；详情/列表接入展示名回填。
- [x] 6.3 `RoleServiceImplTest`：断言/mock 同步。
- [x] 6.4 `PermissionConvert.toVO`：新增 `ignore`。
- [x] 6.5 `PermissionServiceImpl`：写操作调用点改用 `String.valueOf(currentOperatorService
      .resolveUserId())`；详情/列表接入展示名回填。
- [x] 6.6 `PermissionServiceImplTest`：断言/mock 同步。

## 7. 菜单管理（menu）

- [x] 7.1 `MenuConvert.toVO`：新增 `ignore`。
- [x] 7.2 `MenuServiceImpl`：写操作调用点改用
      `String.valueOf(currentOperatorService.resolveUserId())`；详情/列表接入展示名回填。
- [x] 7.3 `MenuServiceImplTest`：断言/mock 同步。

## 8. 管理员管理（admin）

- [x] 8.1 `AdminConvert.toVO`：新增 `ignore`。
- [x] 8.2 `AdminServiceImpl`：写操作调用点（含管理员-角色 `AdminRoleEntity`、管理员-组织
      范围 `AdminOrgScopeEntity` 关联的新增/删除，两者无对外 VO 但落库值同样要改）改用
      `String.valueOf(currentOperatorService.resolveUserId())`；详情/列表接入展示名回填。
      实际实现与设想略有差异：`AdminServiceImpl.getPage`/`getExistingVO` 走的是
      `AdminMapper.xml` 里的 SQL JOIN 直接产出 `AdminVO`（不经过 `AdminConvert.toVO`），
      因此展示名回填改为新增 `backfillDisplayNames(List<AdminVO>)` 私有方法直接对查询
      结果的 `AdminVO` 列表就地覆盖，而非 role/permission 那种"实体列表→VO 列表"下标
      对应的写法；`AdminConvert.toVO` 上新增的 `ignore` 目前是防御性的（该方法当前未被
      任何调用点使用），保持接口一致性。
- [x] 8.3 `AdminServiceImplTest`：断言/mock 同步。

## 9. 元数据字段与表单/导入配置（metadata / form-field / excel-import）

- [x] 9.1 `MetadataFieldConvert` 新增 `ignore`；`MetadataFieldServiceImpl` 写操作调用点
      改用 `String.valueOf(currentOperatorService.resolveUserId())`，列表接入展示名
      回填；`MetadataFieldServiceImplTest` 同步。
- [x] 9.2 `FormFieldDefinitionConvert` 新增 `ignore`；`FormFieldDefinitionServiceImpl`
      写操作调用点改用 `String.valueOf(currentOperatorService.resolveUserId())`，
      详情/列表接入展示名回填；`FormFieldDefinitionServiceImplTest` 同步。
- [x] 9.3 `ImportFieldConfigConvert` 新增 `ignore`；`ImportFieldConfigServiceImpl` 写操作
      调用点改用 `String.valueOf(currentOperatorService.resolveUserId())`，列表接入
      展示名回填；`ImportFieldConfigServiceImplTest` 同步。

## 10. 操作日志（operation-log）

- [x] 10.1 `OperationLogRecorderImpl`：写入调用点改用
      `String.valueOf(currentOperatorService.resolveUserId())`。
- [x] 10.2 `OperationLogConvert`：`toVO`/`toDetailVO` 新增 `createBy`/`updateBy` 的
      `ignore = true`。
- [x] 10.3 `OperationLogMapper` 对应的 XML（`selectOperationLogPage`）：`create_by` 列
      比较方式从 `= #{createBy}` 改为按候选用户 id 文本列表 `IN <foreach>`（列类型/
      参数类型不变，仍是字符串比较）。
- [x] 10.4 `OperationLogQueryServiceImpl.getPage`：新增"输入文本 → 候选用户 id 列表"解析
      步骤（按 `tab_user.code`/`name` 精确匹配），无候选时直接返回空分页；分页结果接入
      `UserDisplayService` 批量回填 `createBy`/`updateBy` 展示名。
- [x] 10.5 `OperationLogQueryServiceImplTest`（如存在）/相关测试：断言/mock 同步。
- [x] 10.6 `OperationHistoryPanel.vue`/`OperationLogDetailDialog.vue` 展示逻辑走查确认
      无需改动（VO 字段名/类型未变）。

## 11. 数据库迁移

- [x] 11.1 逐表核对 `V1__init_schema.sql` 里 18 张表种子数据里出现过的全部占位字符串
      取值：只有 `'system'` 和 `'admin'` 两种（用 grep 全文核对过，没有第三种）。
- [x] 11.2 新增 `backend/src/main/resources/db/migration/V2__audit_fields_use_user_id.sql`：
      只对 18 张表种子数据里含占位字符串的行执行 `UPDATE`，把 `create_by`/`update_by`
      替换为种子超级管理员用户 `id` 的字符串形式（`CAST(id AS CHAR)`）；`tab_user`/
      `tab_user_password` 里种子超级管理员自身那一行改为指向自己 `id` 的字符串。不包含
      任何 `ALTER TABLE` 语句。
- [x] 11.3 本地清库重跑 `V1`+`V2`（临时库 `rbac_migration_test`，验证完已删除）验证通过：
      `tab_user`/`tab_user_password` 里种子超级管理员自引用正确（`create_by`/`update_by`
      均等于自身 `id`）；其余 16 张表共 383 条种子数据 `create_by`/`update_by` 全部变成
      合法的数字字符串，没有残留的 `'system'`/`'admin'` 占位符。

## 12. 收尾验证

- [x] 12.1 `cd backend && ./gradlew clean build`：全量编译 + 全部测试通过（顺带把
      `UserDisplayServiceImpl`/`UserDisplayServiceImplTest` 里用到的
      `BaseMapper.selectBatchIds` 换成项目里已经在用的非过时方法 `selectByIds`，
      消除了唯一的编译期 deprecation 警告）。
- [x] 12.2 未启动前端（本次没有任何前端文件改动，没有可供人工点击的新展示逻辑）；改为
      直接启动后端 + 用 curl 模拟真实登录（RSA/OAEP 加密账号密码）+ 携带
      `identity-token`/`menu` 请求头调用真实接口，对照真实 MySQL 数据验证：
      `GET /api/orgs/1` 返回 `createBy`/`updateBy` 均为 `系统管理员（admin）`（人可读
      展示名，而不是裸 id 或裸账号编码）；`GET /api/operation-logs` 分别用
      `createBy=admin`（账号编码）和 `createBy=系统管理员`（姓名）搜索，都正确命中 12
      条记录，且返回记录的 `createBy` 同样是解析后的展示名；用一个查不到任何用户的乱码
      文本搜索，返回 `total=0` 且没有报错（对应"无候选用户直接返回空分页"的设计）。
- [x] 12.3 确认本次未新增/删除任何前端页面菜单或按钮（`git status` 确认 `frontend/`
      目录零改动），`权限资源.txt` 无需同步更新。
