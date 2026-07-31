## Context

`AuthServiceImpl.login(LoginRequest request)`（`backend/src/main/java/cn/nihility/rbac/auth/service/impl/AuthServiceImpl.java:64-85`）当前逻辑：解密账号/密码密文失败直接 `throw`；解密成功后用一条查询 `userMapper.selectOne(...eq(code, account).ne(status, DELETED))` 按账号查用户，**查询本身就用 `ne(status, DELETED)` 排除了已删除账号**，所以"账号不存在"与"账号已被逻辑删除"在当前实现里查出来的结果都是 `null`，代码上无法区分；随后 `user == null || 状态非启用 || 密码不匹配` 三个条件合并成一个 `if`，统一 `throw new BusinessException(LOGIN_FAILED_MESSAGE)`，对外提示文案统一为"账号或密码不正确"，不区分具体原因（`password-login-auth` spec.md "账号不存在时登录失败" Scenario 明确要求"不泄露账号不存在与密码错误的具体区别信息"——这条对外约束本次不变）。

`cn.nihility.rbac.operationlog` 模块已有 `UserAgentParser`（无状态工具类，`parseBrowser`/`parseOs`/`parseTerminal` 三个 `public static` 方法）可直接复用；IP 解析（优先取 `X-Forwarded-For` 请求头第一段，否则取 `request.getRemoteAddr()`）目前是 `OperationLogRecorderImpl` 里的私有方法，未抽成公共工具，本次在新模块里按同样逻辑另写一份（几行代码，不值得为此提前重构成公共组件）。

`tab_operation_log`（`operationlog` 模块）是面向"CRUD 实体变更"设计的：`module`/`resourceType`/`targetId`/`changeDetail`（字段级 diff）这些概念登录事件都不具备；且 `OperationLogRecorderImpl` 的 `createBy` 目前固定写死 `"admin"`（`DEFAULT_OPERATOR`，全仓库所有写操作审计字段都是这个已知的历史简化，非本次改动范围），登录日志需要的"操作人"其实是"本次尝试登录的账号"，语义上和这个固定值也不匹配。因此本次新建独立模块 `cn.nihility.rbac.loginlog`，不复用 `OperationLogRecorder`/`OperationLogEntity`/`tab_operation_log`。

## Goals / Non-Goals

**Goals:**
- 记录每一次登录尝试（成功 + 失败），覆盖六类场景：登录成功、密码不正确、账号不存在、账号已停用、账号已删除、账号密文解密失败。
- 登录日志作为内部审计数据，允许保留比对外提示更细的失败原因；不改变现有对外错误提示文案、不改变"不泄露账号是否存在"这条既有安全约束。
- 提供只读分页查询接口，支持按登录账号、登录结果、登录时间范围筛选。
- 新增页面/权限点挂载到既有"系统管理"菜单分组，风格与既有「操作日志」一致。

**Non-Goals:**
- 不记录 token 刷新（`POST /api/auth/refresh`）、登出、修改密码这几个动作——本次范围严格限定在"登录尝试"本身。
- 不做登录失败次数限制、账号锁定、验证码等主动防护机制，只做审计记录，属于纯粹的"事后可查"能力。
- 不改变 `tab_operation_log`/`OperationLogRecorder` 的既有实现，不做任何重构合并。
- 不对登录日志接口做管辖组织范围（`OrgScopeService`）过滤——登录事件不属于任何组织维度的业务数据，与 `org-scope-data-permission`/`user-org-scope-data-permission` 两次改动过滤的资源类型（组织树、任职、应用、用户列表）不是同一范畴，`operationlog` 模块本身现有的查询接口也没有做这层过滤，保持一致。

## Decisions

### Decision 1：`AuthServiceImpl.login()` 改为显式区分失败分支，查询本身也要跟着调整
把当前的合并查询+合并判断拆开：

```java
UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
        .eq(UserEntity::getCode, account));   // 不再 .ne(status, DELETED)，交给下面按状态分支判断

if (user == null) {
    recordFailure(account, null, LoginFailReason.ACCOUNT_NOT_FOUND);
    throw new BusinessException(LOGIN_FAILED_MESSAGE);
}
if (Objects.equals(user.getStatus(), UserStatus.DELETED)) {
    recordFailure(account, user, LoginFailReason.ACCOUNT_DELETED);
    throw new BusinessException(LOGIN_FAILED_MESSAGE);
}
if (!Objects.equals(user.getStatus(), UserStatus.ENABLED)) {
    recordFailure(account, user, LoginFailReason.ACCOUNT_DISABLED);
    throw new BusinessException(LOGIN_FAILED_MESSAGE);
}
if (!passwordService.verifyPassword(user.getId(), password)) {
    recordFailure(account, user, LoginFailReason.PASSWORD_MISMATCH);
    throw new BusinessException(LOGIN_FAILED_MESSAGE);
}
// 成功分支：签发 token 前记录成功日志
```

查询去掉 `.ne(status, DELETED)` 是必须的——否则"账号不存在"和"账号已删除"两种情况在 SQL 层面就已经被合并成同一个 `null` 结果，Java 代码无论怎么写都区分不出来。去掉之后查询语义变成"按账号查任意状态的用户"，由 Java 侧显式分支处理三种状态（`DELETED`/非 `ENABLED` 即 `DISABLED`/`ENABLED`），对外行为完全不变（四个失败分支最终都是同一个异常、同一条提示文案），只是内部多了记日志这一步。

解密阶段（`decrypt()` 私有方法）失败时同样记录一条失败日志，`loginAccount` 记 `null`（解密失败根本拿不到明文账号，不记录密文本身——密文不是登录日志该保留的信息，也没有审计价值），`failReason` 记"账号解密失败"。

考虑过的替代方案：不改查询，仍然把"不存在"和"已删除"合并成同一个 `ACCOUNT_NOT_FOUND_OR_DELETED` 原因——这样能保留原查询不动，但会丢失审计价值（无法区分"从未注册过的账号在被扫描"和"离职员工在用旧密码尝试"这两种性质不同的安全事件），且改动量差异很小（只是把 `.ne(status, DELETED)` 从查询条件挪到 Java 分支判断），综合下来选择保留区分粒度。

### Decision 2：新增 `cn.nihility.rbac.loginlog` 模块，独立于 `operationlog`
模块结构对齐项目既有分层约定：
- `loginlog.entity.LoginLogEntity`（`tab_login_log`）
- `loginlog.mapper.LoginLogMapper`（`BaseMapper`，分页查询直接用 `LambdaQueryWrapper`，单表查询不需要 XML）
- `loginlog.constant.LoginResult`（`SUCCESS = 1`、`FAILED = 2`，风格对齐 `OperationType`）
- `loginlog.constant.LoginFailReason`（失败原因的中文文案常量：`ACCOUNT_NOT_FOUND = "账号不存在"`、`ACCOUNT_DELETED = "账号已删除"`、`ACCOUNT_DISABLED = "账号已停用"`、`PASSWORD_MISMATCH = "密码不正确"`、`DECRYPT_FAILED = "账号解密失败"`，直接存字符串文案而不是再加一层码值->文案映射——失败原因只在登录日志详情里内部展示，不参与前端筛选下拉，没有必要走字典化）
- `loginlog.service.LoginLogRecorder`（+ `impl`）：供 `AuthServiceImpl` 调用的写入入口，两个方法 `recordSuccess(String loginAccount, Long userId, String userName)`、`recordFailure(String loginAccount, Long userId, String userName, String failReason)`，内部自行解析 IP/User-Agent、写库，调用方不关心这些细节（接口设计风格对齐 `OperationLogRecorder`）。
- `loginlog.service.LoginLogQueryService`（+ `impl`）+ `loginlog.controller.LoginLogController`：只读分页查询。
- `loginlog.dto.LoginLogQueryRequest`/`LoginLogVO`。

`AuthServiceImpl` 新增依赖 `LoginLogRecorder`（`auth` 模块依赖 `loginlog` 模块，单向，符合"登录动作触发日志记录"的自然方向，`loginlog` 模块不反向依赖 `auth`）。

### Decision 3：新表 `tab_login_log`，字段设计与保留字规避
```sql
CREATE TABLE tab_login_log (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    login_account     VARCHAR(64)  NULL COMMENT '本次登录尝试提交的账号，解密失败时为 NULL',
    user_id           BIGINT       NULL COMMENT '关联的 tab_user.id，账号不存在/解密失败时为 NULL',
    user_name         VARCHAR(64)  NULL COMMENT '用户姓名快照，账号不存在/解密失败时为 NULL',
    login_result      TINYINT      NOT NULL COMMENT '1=成功，2=失败',
    fail_reason       VARCHAR(64)  NULL COMMENT '失败原因文案，登录成功时为 NULL',
    login_ip          VARCHAR(64)  NULL,
    login_terminal    VARCHAR(32)  NULL,
    login_os          VARCHAR(32)  NULL,
    login_browser     VARCHAR(32)  NULL,
    login_user_agent  VARCHAR(512) NULL,
    create_by         VARCHAR(64)  NOT NULL,
    create_time       DATETIME     NOT NULL,
    update_by         VARCHAR(64)  NOT NULL,
    update_time       DATETIME     NOT NULL
);
```
字段名逐一过了 MySQL/PostgreSQL/Oracle/SQL Server 保留字表：`login_account`/`user_id`/`user_name`/`login_result`/`fail_reason`/`login_ip`/`login_terminal`/`login_os`/`login_browser`/`login_user_agent` 均非保留字（裸词 `account`/`result` 有歧义但已加 `login_`/未使用裸词，规避）。不单独设计 `login_time` 列——复用 `create_time` 表达"本次登录尝试发生时间"，与 `tab_operation_log` 用 `create_time` 表达操作发起时间的既有约定一致；`update_by`/`update_time` 恒等于 `create_by`/`create_time`（这条记录只追加不更新），`create_by` 存 `login_account`，为 `NULL`（解密失败场景）时存固定值 `'unknown'`（`create_by` 列本身沿用项目"必须有创建人"的约定，不允许为 `NULL`，但 `login_account` 列允许为 `NULL` 以准确表达"这次尝试连账号是什么都不知道"）。

### Decision 4：只读查询接口不拆分列表/详情，`GET /api/login-logs` 一个接口够用
`OperationLogController` 之所以拆成列表 `page` + 详情 `getById` 两个接口，是因为 `changeDetail` 字段级变更列表可能很长，列表 VO 故意不带这个字段避免响应体过大。登录日志没有这种"可能很大的结构化字段"，`login_user_agent` 最长也就几百字符，直接放进列表 VO（`LoginLogVO`）即可，不新增 `GET /api/login-logs/{id}` 详情接口。

### Decision 5：登录日志接口的权限点与菜单——独立模块，不复用操作日志的权限点
新增 `LoginLogManagement:loginLog:view`（而不是复用 `OperationLogManagement:log:view`），因为两者是不同的资源目录条目，管理员可能只被授予其中一个查看权限（如只想让某些管理员看登录审计、不看业务操作审计，反之亦然）；菜单路径 `/system/login-logs`，`tab_menu` 记录 `parent_id` 取 `system` 一级分组 id（与「操作日志」`OperationLogManagement:log:view` 同一个 `parent_id`），`show_order` 取比「操作日志」现有值（`5`）更小的值（如 `4`），排在「操作日志」下方（`show_order` 降序排列，既有约定见 `org-management`/`user-management` 等模块）。

### Decision 6：Flyway `V8` 必须显式给超级管理员角色追加权限
`V6__seed_permissions_and_super_admin.sql` 的超级管理员授权是一次性 `INSERT INTO tab_role_permission SELECT ... FROM tab_permission`（历史快照，只覆盖 `V6` 执行时已存在的权限点）。`V8` 新插入的 `LoginLogManagement:loginLog:view` 不会被那条历史 SQL 自动覆盖，必须在 `V8` 里显式追加一条 `INSERT INTO tab_role_permission` 关联 `SUPER_ADMIN` 角色 id 和新权限点 id，否则迁移执行完默认管理员账号反而看不到这个新菜单（`V7` 处理"补齐 `tab_menu` 遗漏记录"时不需要这一步，是因为 `V7` 补的那 8 条权限编码在 `tab_permission`/`tab_role_permission` 里其实已经在 `V6` 就配好了，只是 `tab_menu` 这份"菜单目录"漏刻了，本次 `V8` 是从 0 开始新增一个之前完全不存在的权限点，情况不同）。

## Risks / Trade-offs

- **[风险] `AuthServiceImpl.login()` 查询去掉 `.ne(status, DELETED)` 后，返回的 `UserEntity` 可能是已删除用户**：需要确保后续分支严格按状态判断顺序处理（先查 `null`，再查 `DELETED`，再查非 `ENABLED`，最后才是密码校验），不能让已删除用户的记录意外流入密码校验或签发 token 的逻辑。
  → **缓解**：Decision 1 的分支顺序已经把 `DELETED` 判断放在密码校验之前，且每个分支各自 `throw`、不会继续往下执行；后续会补单元测试覆盖"账号已删除时既不校验密码也不签发 token"这条路径。
- **[风险] 登录日志本身可能被用作枚举有效账号的信息源**：如果登录日志查询接口权限配置不当（比如误发给了低权限角色），`failReason` 里的"账号不存在" vs "密码不正确"区别就会变成一个新的信息泄露面，等价于绕过了对外接口本来要防的"不泄露账号是否存在"。
  → **接受**：这本来就是"审计日志"这类功能的固有性质（能查看审计日志的人本来就应该是高信任角色），`LoginLogManagement:loginLog:view` 是独立权限点，默认只授予超级管理员，只要按最小权限原则分配角色权限，风险可控；不在本次范围内做"登录日志详情本身也脱敏"这种更保守的设计。
- **[权衡] 不做登录失败次数限制/账号锁定**：本次只做审计记录，不做主动防护，如果后续需要基于登录日志实现锁定策略，需要在独立 change 里另行设计（如失败次数统计、锁定时长、解锁流程），本次的表结构和记录粒度（每次尝试一行）足够支撑后续统计类查询，不需要为此改动本次设计。
