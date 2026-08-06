## Why

全项目 14 个业务模块的 Service 实现类（`admin`/`app`/`dict`（类型+项）/`excelimport`/`formfield`/`menu`/`metadata`/`operationlog`/`org`/`permission`/`role`/`user`（用户+任职））在新增/编辑/启停用/删除等写操作时，`create_by`/`update_by` 审计字段以及操作日志的 `create_by` 字段全部写死为常量字符串 `"admin"`，与实际发起操作的登录账号无关——这是登录鉴权功能上线前遗留的占位实现，鉴权体系（`identity-token`/`CurrentUserContext`）早已就绪但一直未接回来。这个问题目前已经造成一个可观察的用户可见故障：`dashboard-real-data` change 新增的"当前用户最近操作"接口按登录账号的 `code` 过滤操作日志，只要不是用种子账号 `admin` 登录，该接口永远查不到数据，因为所有操作日志的 `create_by` 都被写死成了 `"admin"`。修复根因（而不是绕开）需要把这 14 个模块统一接回真实的当前登录用户。

## What Changes

- 新增一个统一的"解析当前登录操作人账号编码"能力：基于已有的 `CurrentUserContext.getUserId()`（`IdentityAuthFilter` 校验通过后设置）查出对应的 `tab_user.code`，供各业务模块的写操作复用，替代各自硬编码的 `DEFAULT_OPERATOR = "admin"` 常量。
- 14 个 Service 实现类（`AdminServiceImpl`、`AppServiceImpl`、`DictItemServiceImpl`、`DictTypeServiceImpl`、`ImportFieldConfigServiceImpl`、`FormFieldDefinitionServiceImpl`、`MenuServiceImpl`、`MetadataFieldServiceImpl`、`OperationLogRecorderImpl`、`OrgServiceImpl`、`PermissionServiceImpl`、`RoleServiceImpl`、`PositionServiceImpl`、`UserServiceImpl`）改为注入并调用这个统一能力，删除各自的 `DEFAULT_OPERATOR` 常量。
- **BREAKING（审计数据口径）**：修改后，新写入的 `create_by`/`update_by`（以及新产生的操作日志 `create_by`）会是发起操作的真实登录账号编码，不再是固定的 `"admin"`；历史上已经写入的记录保持不变，不做数据回填/迁移（无法逆向还原"当年到底是谁做的"，回填没有依据）。
- 不改动 `auth/service/impl/PasswordServiceImpl.java`（`tab_user_password` 审计字段）与 `loginlog/service/impl/LoginLogRecorderImpl.java`（登录日志）——这两处目前的实现方式不是硬编码 `"admin"`：`PasswordServiceImpl` 已经动态解析 `CurrentUserContext` 得到的用户 id（受限于该模块刻意不依赖 `user` 模块，暂用 id 而非账号编码，是既有的独立设计取舍）；`LoginLogRecorderImpl` 记录的是登录尝试本身携带的账号，本来就是动态值。这两处不属于本次要修的"写死 admin"问题。

## Capabilities

### New Capabilities
（无——不引入面向前端可见的新能力）

### Modified Capabilities
- `backend-common-utilities`：新增一条"解析当前登录操作人账号编码"的公共基础设施能力需求，供各业务模块复用。

## Impact

- 受影响代码：`backend/src/main/java/cn/nihility/rbac/auth/service/`（新增 `CurrentOperatorService` 接口 + 实现，以及新增单元测试 `CurrentOperatorServiceImplTest`，覆盖正常解析/未登录/用户查不到三种场景）；上述 14 个模块的 Service 实现类（删除 `DEFAULT_OPERATOR` 常量，改为注入调用新能力）；对应的单元测试（凡是断言 `createBy`/`updateBy` 等于 `"admin"` 的用例需要改为通过 mock 新依赖来断言真实操作人编码）。
- 不涉及数据库表结构变更，不新增依赖。
- 历史数据不做迁移/回填。
