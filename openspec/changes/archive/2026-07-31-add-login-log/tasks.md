## 1. 数据库：新表 + 菜单 + 权限点

- [x] 1.1 新增 `backend/src/main/resources/db/migration/V8__add_login_log.sql`：建表 `tab_login_log`（design.md Decision 3 的字段清单），字段名逐一核对 MySQL/PostgreSQL/Oracle/SQL Server 保留字。
- [x] 1.2 同一迁移文件里新增 `tab_menu` 记录：`('登录日志', 'LoginLogManagement:loginLog:view', @system_id, 1, 4, '登录日志管理页面访问', 2000, 'admin', NOW(), 'admin', NOW())`（`show_order=4`，排在「操作日志」`show_order=5` 下方，`@system_id` 复用 `SELECT id FROM tab_menu WHERE code='system'` 取值方式，参照 `V1__init_schema.sql` 里 `OperationLogManagement:log:view` 那条记录的写法）。
- [x] 1.3 同一迁移文件里新增 `tab_permission` 记录：`('登录日志管理页面访问', 'LoginLogManagement:loginLog:view', 0, NULL, 2000, 'system', NOW(), 'system', NOW())`（参照 `V6__seed_permissions_and_super_admin.sql` 里 `OperationLogManagement` 那段写法）。
- [x] 1.4 同一迁移文件里追加 `INSERT INTO tab_role_permission`，把新权限点 id 关联到 `tab_role` 中 `code='SUPER_ADMIN'` 的角色 id（design.md Decision 6：`V6` 的历史全量授权 SQL 不会自动覆盖本次新增的权限点，必须显式补）。`RbacApplicationTests`（完整 Spring 容器 + Flyway 启动）已验证 V8 迁移可正常执行。

## 2. 后端：`loginlog` 模块

- [x] 2.1 `cn.nihility.rbac.loginlog.entity.LoginLogEntity`（对应 `tab_login_log`，含 `@TableId`/`@TableName`，Lombok `@Getter/@Setter/@Builder/@NoArgsConstructor/@AllArgsConstructor`）。
- [x] 2.2 `cn.nihility.rbac.loginlog.constant.LoginResult`（`SUCCESS=1`、`FAILED=2`，含 `label(Integer)` 中文文案方法，风格对齐 `OperationType`）。
- [x] 2.3 `cn.nihility.rbac.loginlog.constant.LoginFailReason`（`ACCOUNT_NOT_FOUND`/`ACCOUNT_DELETED`/`ACCOUNT_DISABLED`/`PASSWORD_MISMATCH`/`DECRYPT_FAILED` 五个中文文案常量字符串）。
- [x] 2.4 `cn.nihility.rbac.loginlog.mapper.LoginLogMapper`（`BaseMapper<LoginLogEntity>`，单表查询走 `LambdaQueryWrapper`，不需要 XML）。
- [x] 2.5 `cn.nihility.rbac.loginlog.service.LoginLogRecorder` + `impl.LoginLogRecorderImpl`：`recordSuccess(String loginAccount, Long userId, String userName)`、`recordFailure(String loginAccount, Long userId, String userName, String failReason)` 两个方法；内部解析 IP（复用 `OperationLogRecorderImpl` 里 `X-Forwarded-For` 优先、否则 `remoteAddr` 的逻辑，在本模块内新写一份）、User-Agent（直接调用 `cn.nihility.rbac.operationlog.util.UserAgentParser` 的三个静态方法），`createBy` 取 `loginAccount`，为空时取 `"unknown"`，`updateBy`/`updateTime` 恒等于 `createBy`/`createTime`（design.md Decision 3）。额外新增了 `loginlog.mapstruct.LoginLogConvert`（entity→VO 转换，风格对齐 `RoleConvert`，design.md 未列出但符合仓库既有分层约定）。
- [x] 2.6 `cn.nihility.rbac.loginlog.dto.LoginLogQueryRequest`（`loginAccount`/`loginResult`/`startTime`/`endTime`/`page`/`pageSize`）、`cn.nihility.rbac.loginlog.dto.LoginLogVO`（含 `loginResultLabel` 中文文案字段，参照 `OperationLogVO` 的 `operationTypeLabel` 风格）。
- [x] 2.7 `cn.nihility.rbac.loginlog.service.LoginLogQueryService` + `impl.LoginLogQueryServiceImpl`：分页查询，`LambdaQueryWrapper` 拼接可选筛选条件，按 `createTime` 降序。
- [x] 2.8 `cn.nihility.rbac.loginlog.controller.LoginLogController`：`GET /api/login-logs` 分页查询接口，参照 `OperationLogController.page` 的参数风格与 springdoc 注解（`@Tag`/`@Operation`/`@Parameter`），不新增详情接口（design.md Decision 4）。

## 3. 后端：`AuthServiceImpl.login()` 改造

- [x] 3.1 注入 `LoginLogRecorder`（`auth` 模块依赖 `loginlog` 模块，单向）。
- [x] 3.2 `decrypt()` 调用处捕获解密失败，记录一条 `recordFailure(null, null, null, LoginFailReason.DECRYPT_FAILED)`，再抛出原有的 `BusinessException(LOGIN_FAILED_MESSAGE)`（对外提示文案不变）。实现方式：`decrypt()` 私有方法本身未改动（仍捕获内部异常、抛出同一个 `BusinessException`），改为在 `login()` 内把两次 `decrypt(...)` 调用包一层 `try-catch(BusinessException e)`，catch 块里记录日志后 `throw e`，未改变 `decrypt()` 对外抛出的异常类型/消息。
- [x] 3.3 查询用户的 `LambdaQueryWrapper` 去掉 `.ne(UserEntity::getStatus, UserStatus.DELETED)` 条件（design.md Decision 1：不去掉就无法区分"不存在"与"已删除"）。
- [x] 3.4 按顺序显式判断并记录：`user == null` → `recordFailure(account, null, null, ACCOUNT_NOT_FOUND)`；`status == DELETED` → `recordFailure(account, user.getId(), user.getName(), ACCOUNT_DELETED)`；`status != ENABLED`（即 `DISABLED`）→ `recordFailure(account, user.getId(), user.getName(), ACCOUNT_DISABLED)`；密码不匹配 → `recordFailure(account, user.getId(), user.getName(), PASSWORD_MISMATCH)`；每个分支各自记录后仍 `throw new BusinessException(LOGIN_FAILED_MESSAGE)`，对外文案不变。
- [x] 3.5 全部校验通过、签发 token 前，调用 `recordSuccess(account, user.getId(), user.getName())`。

## 4. 前端

- [x] 4.1 `frontend/src/types/loginLog.ts`：`LoginLogRow`/`LoginLogQueryParams` 类型定义（参照 `types/operationLog.ts`）。实现方式：`PageResult<T>` 未重复定义，直接从 `types/operationLog.ts` re-export 复用，避免同一个通用分页结构在两个文件里各写一份。
- [x] 4.2 `frontend/src/api/loginLog.ts`：`getLoginLogPage(params)` 封装（参照 `api/operationLog.ts`）。
- [x] 4.3 `frontend/src/views/system/log/LoginLogManagementView.vue`：筛选表单（登录账号、登录结果下拉、登录时间范围）+ 表格（登录时间、登录账号、用户姓名、登录结果、失败原因、IP、设备信息），参照 `OperationLogManagementView.vue` 的布局风格。实现方式：终端类型/操作系统/浏览器三个字段合并展示为一列"设备信息"（如"Windows / Chrome"），过滤 null 后用 `/` 拼接；没有详情接口，不含"操作"列和详情弹窗。
- [x] 4.4 `frontend/src/router/menu.ts` 新增一条菜单项：`{ title: '登录日志', path: '/system/login-logs', permissionKey: 'LoginLogManagement:loginLog:view' }`，排在「操作日志」旁边。
- [x] 4.5 `frontend/src/router/index.ts` 新增路由映射（组件懒加载 `() => import('@/views/system/log/LoginLogManagementView.vue')`）及页面描述文案（参照 `/system/logs` 现有写法）。

## 5. 权限资源文档

- [x] 5.1 `权限资源.txt` 新增 `LoginLogManagement`（登录日志管理，`/system/login-logs`）模块条目，格式参照现有 `OperationLogManagement` 段落，只含 `LoginLogManagement:loginLog:view` 一条。

## 6. 验证

- [x] 6.1 后端单元测试：`AuthServiceImplTest` 补充 6 个用例覆盖六类场景（成功、密码错误、账号不存在、账号停用、账号删除、解密失败），各自用 Mockito `verify` 校验对应的 `LoginLogRecorder.recordSuccess`/`recordFailure` 调用参数；新增 `LoginLogQueryServiceImplTest` 覆盖 3 类筛选场景（全部参数为空、按 loginAccount 精确匹配、按 loginResult 精确匹配）。`LoginLogRecorderImpl` 落库细节未额外补单元测试，由 `RbacApplicationTests` 完整启动 Spring 容器 + 执行 V8 迁移间接验证表结构可用。
- [x] 6.2 `./gradlew test`（在 `backend/` 目录下）确认编译与测试通过：全部 305 个测试通过（含 `RbacApplicationTests` 完整启动 Spring 容器并执行 V8 迁移）。
- [x] 6.3 `npm run build`（在 `frontend/` 目录下）确认类型检查与构建通过：`vue-tsc` 类型检查与 `vite build` 均无报错，产物含 `LoginLogManagementView-*.js` chunk。
- [ ] 6.4 手动验证（若开发环境可行）：分别用正确账号密码、错误密码、不存在的账号、已停用账号登录一次，确认登录日志页面能查到对应的四条记录且失败原因正确；确认登录接口返回给客户端的错误提示文案没有变化。

## 7. 文档同步

- [x] 7.1 实现完成后基于实际 diff/测试结果对齐 `proposal.md`/`design.md`/`tasks.md`：`proposal.md` 措辞已经足够高层，无需改动；`design.md` 六个 Decision 均按原设计落地，仅两处非实质性实现细节偏差已在 3.2/2.5 勾选说明里记录（`decrypt()` 阶段改为 `login()` 内 try-catch 包裹而非改造 `decrypt()` 内部、新增了 design.md 未列出但符合仓库既有分层约定的 `LoginLogConvert` MapStruct 转换器），两者均不改变对外行为，`design.md` 正文不需要改写；`tasks.md` 本次已勾选全部完成项（6.4 手动登录验证需要真实数据库环境，留待部署后验证）。
