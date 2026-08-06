## 1. 新增 CurrentOperatorService

- [x] 1.1 新建 `backend/src/main/java/cn/nihility/rbac/auth/service/CurrentOperatorService.java`：接口，方法 `String resolveCode()`。
- [x] 1.2 新建 `backend/src/main/java/cn/nihility/rbac/auth/service/impl/CurrentOperatorServiceImpl.java`：注入 `UserMapper`，`resolveCode()` 取 `CurrentUserContext.getUserId()`，为 `null` 时抛 `IllegalStateException`；否则 `userMapper.selectById(userId)`，实体为 `null` 时同样抛 `IllegalStateException`，否则返回 `.getCode()`。
- [x] 1.3 新建对应单元测试 `CurrentOperatorServiceImplTest`：覆盖"已登录会话正常解析"、"`CurrentUserContext` 未设置时抛异常"、"`userId` 查不到对应用户时抛异常"三种场景（用 `try/finally` 包裹测试方法内对 `CurrentUserContext` 的 `setUserId`/`clear`，避免污染其他测试）。

## 2. 逐模块接入（删除 DEFAULT_OPERATOR，改为注入调用 CurrentOperatorService）

以下每个模块的改法一致：删除 `DEFAULT_OPERATOR` 常量，构造函数注入 `CurrentOperatorService`，每个写方法（create/update/enable/disable/delete 等原来引用 `DEFAULT_OPERATOR` 的地方）开头调用一次 `resolveCode()` 存入局部变量并复用给 `createBy`/`updateBy`；同步更新对应单元测试（mock `CurrentOperatorService`，断言从 `"admin"` 改为 mock 返回值）。

- [x] 2.1 `admin/service/impl/AdminServiceImpl.java` + `AdminServiceImplTest.java`
- [x] 2.2 `app/service/impl/AppServiceImpl.java` + `AppServiceImplTest.java`
- [x] 2.3 `dict/service/impl/DictItemServiceImpl.java` + `DictItemServiceImplTest.java`
- [x] 2.4 `dict/service/impl/DictTypeServiceImpl.java` + `DictTypeServiceImplTest.java`
- [x] 2.5 `excelimport/service/impl/ImportFieldConfigServiceImpl.java` + `ImportFieldConfigServiceImplTest.java`
- [x] 2.6 `formfield/service/impl/FormFieldDefinitionServiceImpl.java` + `FormFieldDefinitionServiceImplTest.java`
- [x] 2.7 `menu/service/impl/MenuServiceImpl.java` + `MenuServiceImplTest.java`
- [x] 2.8 `metadata/service/impl/MetadataFieldServiceImpl.java` + `MetadataFieldServiceImplTest.java`
- [x] 2.9 `operationlog/service/impl/OperationLogRecorderImpl.java` + `OperationLogRecorderImplTest.java`（这是修复"当前用户最近操作接口对非 admin 账号返回空列表"问题的关键改动）
- [x] 2.10 `org/service/impl/OrgServiceImpl.java` + `OrgServiceImplTest.java`
- [x] 2.11 `permission/service/impl/PermissionServiceImpl.java` + `PermissionServiceImplTest.java`
- [x] 2.12 `role/service/impl/RoleServiceImpl.java` + `RoleServiceImplTest.java`
- [x] 2.13 `user/service/impl/PositionServiceImpl.java` + `PositionServiceImplTest.java`
- [x] 2.14 `user/service/impl/UserServiceImpl.java` + `UserServiceImplTest.java`（注意 `create()` 同时创建用户和若干条任职记录，只解析一次操作人、复用给全部实体，不要在循环内重复调用 `resolveCode()`）

## 3. 编译与单测验证

- [x] 3.1 `./gradlew build`（`backend/` 目录下）全量通过，确认 14 个模块 + 新增 `CurrentOperatorService` 的改动都编译通过、单测通过。
- [x] 3.2 全仓库检索确认没有遗漏：`grep -rn "DEFAULT_OPERATOR" backend/src/main/java` 应该没有匹配（`PasswordServiceImpl`/`LoginLogRecorderImpl` 本来就不含这个常量，不需要特殊排除）。

## 4. 端到端验证

- [x] 4.1 启动后端，用非 `admin` 的测试账号登录，执行一次任意写操作（如编辑一次应用备注），确认该操作对应的实体 `update_by`（可通过详情接口或直接查库确认）等于该账号的登录 code，而不是 "admin"。（实测：用 `test`/`admin123` 登录后 `PUT /api/apps/1` 编辑备注，响应 `updateBy` 从 `"admin"` 变为 `"test"`，确认写死已被替换为真实解析。）
- [x] 4.2 用同一个非 `admin` 账号调用 `GET /api/dashboard/recent-operations`，确认能查到刚才那条操作记录（验证 `dashboard-real-data` change 里那个"当前用户最近操作对非 admin 账号永远为空"的问题已解决）。（实测：`test` 账号调用该接口返回了刚才那条编辑记录，`createBy: "test"`。）
- [x] 4.3 用 `admin` 账号验证行为不受影响：写操作后 `create_by`/`update_by` 仍正确记录为 "admin"（因为 admin 账号自己的 code 就是 "admin"，不是因为写死）。（实测：`admin` 账号编辑同一应用后 `updateBy` 仍为 `"admin"`，其 `recent-operations` 也正常按 `createBy` 过滤出自己的操作记录。）
