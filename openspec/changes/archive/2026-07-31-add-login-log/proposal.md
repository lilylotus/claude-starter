## Why

登录接口目前没有任何审计留痕：谁在什么时间、用什么设备/IP 尝试登录、是成功还是失败、失败的具体原因，一概无从查起。一旦出现异常登录（撞库、暴力破解尝试、账号被停用后仍有人反复尝试登录等），管理员没有任何数据可以复盘。需要给登录接口补上登录日志记录能力，作为安全审计的基础能力。

## What Changes

- 新增独立的登录日志能力（新模块 `cn.nihility.rbac.loginlog`），记录每一次登录尝试（成功与失败均记录），不复用面向"CRUD 实体变更"设计的既有 `operationlog` 模块（`OperationLogRecorder`/`tab_operation_log`）——登录事件没有字段级 diff 快照，失败时也往往没有已认证身份可作为操作人，语义上不匹配，但复用其 `UserAgentParser` 工具类解析终端类型/操作系统/浏览器。
- `AuthServiceImpl.login()` 内部改造：把当前"账号不存在/密码不匹配/账号停用/账号已删除统一 `throw` 同一个异常"的逻辑，改为先分别判断、各自记录对应的失败原因到登录日志，再抛出同一个对外统一的业务异常（对外提示文案 `账号或密码不正确` 不变，不向登录失败的客户端泄露具体原因）；解密阶段失败（密文格式错误/密钥不匹配）也记录一条"账号解密失败"的日志，此时不记录明文账号（本来就解不出来）。
- 新增只读查询接口 `GET /api/login-logs`：分页查询，支持按登录账号（精确匹配）、登录结果、登录时间范围筛选，按登录时间降序排列。
- 新增前端页面「登录日志」（`/system/login-logs`），挂在"系统管理"一级菜单下，与既有「操作日志」并列；新增对应权限点 `LoginLogManagement:loginLog:view`（只读，无新增/编辑/删除）。
- 新增 Flyway 迁移 `V8`：建表 `tab_login_log`；新增 `tab_menu`/`tab_permission` 各一条记录；给"超级管理员"角色（`SUPER_ADMIN`）追加一条 `tab_role_permission`，确保默认管理员账号迁移后立即能看到这个新菜单（`V6` 里超级管理员的全量授权是一次性 `SELECT` 快照，不会自动覆盖 `V6` 之后新增的权限点）。

## Capabilities

### New Capabilities
- `login-log-management`：登录尝试审计日志的记录规则（覆盖成功、密码错误、账号不存在、账号停用、账号已删除、解密失败六类场景各自的记录内容）与只读查询能力。

### Modified Capabilities
- `password-login-auth`：「口令登录」需求补充场景，约束登录失败/成功时同步记录登录日志；对外提示文案与信息泄露约束不变，只是内部审计记录允许保留更细的失败原因。

## Impact

- 后端：新增 `cn.nihility.rbac.loginlog` 模块（entity/mapper/service/controller/dto/constant），新增 Flyway `V8__add_login_log.sql`；`AuthServiceImpl.login()` 改造失败分支判断逻辑（对外行为、返回的错误信息不变）。
- 前端：新增 `views/system/log/LoginLogManagementView.vue`、`api/loginLog.ts`、对应 `types/` 定义；`router/menu.ts`、`router/index.ts` 各新增一处菜单/路由注册。
- `权限资源.txt` 新增 `LoginLogManagement` 模块条目。
- 不改变任何已有接口的请求/响应结构；`GET /api/auth/login` 的返回值和对外错误提示保持不变。
- 不引入新的第三方依赖。
