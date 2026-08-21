## Why

用户提出 4 个已上线能力的缺口修复：

1. CAS/OAuth2.0 单点登录流程完全没有写入登录日志（`tab_login_log`），只有管理端口令登录（`password-login-auth`）会记录，导致通过 SSO 登录的行为在审计上是空白的。
2. 新增/编辑组织、用户、任职记录后，`app-access-authorization` 已有策略不会自动重新计算，新符合条件的用户要等管理员手动点"执行"才能拿到授权，存在授权滞后窗口。
3. `tab_login_log`/`tab_operation_log` 没有任何清理机制，会无限增长。
4. 新增/编辑策略规则时，"组织范围"与"用户属性条件"不能同时为空的校验过严——按当前实现，只配置了请求控制（浏览器/IP 白名单）而不配置身份圈定条件的策略无法保存，但这本应是一种合法配置（用请求控制本身作为唯一的准入条件）。

## What Changes

1. **SSO 登录记录登录日志**：`SsoLoginController` 的凭证校验路径（`/api/authn/sso/login`）复用 `LoginLogRecorder`，区分登录成功/密码不正确/账号不存在/账号已停用/账号已删除/账号解密失败六类场景并写入 `tab_login_log`，字段粒度与口令登录一致；同一 SSO 会话之后签发的 CAS 服务票据/OAuth2 授权码不重复记录（因为没有新的凭证校验发生）。
2. **组织/用户/任职变更后策略自动重新执行**：复用已有的 `DomainChangeEvent` 领域事件总线，在 `DomainChangeEventProcessor` 里新增一个处理分支——ORG/USER/POSITION 三类数据的任意操作类型（新增/编辑/启用/停用/删除）发生后，异步对全部当前启用状态的策略规则重新调用一次 `PolicyExecutionService#execute`；单个策略失败不影响其余策略，且不阻塞触发变更的原始写请求。
3. **登录日志、操作日志定期清理**：新增一个默认每天凌晨 1 点执行、默认保留最近 180 天（半年）数据的定时任务，删除 `tab_login_log`/`tab_operation_log` 中创建时间早于保留期的记录；执行时间与保留天数均可通过配置覆盖默认值。范围明确限定为这两张表，不包含 `tab_upstream_sync_record`/`tab_app_notify_record`/`tab_app_pull_record`（`tab_app_pull_record` 此前设计已明确排除在保留策略之外，其余两张表本次不纳入，如后续需要另开 change）。
4. **策略保存校验放宽为"组织范围/用户属性条件/请求控制条件三者不能同时为空"**：把现有"组织范围与用户属性条件不能同时为空"的校验，改为"组织范围、用户属性条件、请求控制条件（浏览器白名单或 IP 白名单任一非空）三者不能同时为空"，允许"仅配置请求控制"的策略保存。相应地，`PolicyExecutionService#execute` 在组织范围与用户属性条件都未配置时（此时策略必然配置了请求控制条件），命中范围调整为"系统内全部启用状态的用户"，不再是无法执行的空集——这一步是让"仅请求控制"的策略真正生效的必要前提，会反转 `app-access-authorization` 已归档 design.md 中"两者不能同时为空，否则退化成对全公司授权、语义含糊"的原决策（用户已确认接受这一反转）。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `app-sso-protocol-runtime`：新增"SSO 登录记录登录日志"需求。
- `login-log-management`：新增"登录日志定期清理"需求。
- `operation-log-management`：新增"操作日志定期清理"需求。
- `app-access-authorization`：
  - 修改"策略规则的定义与维护"需求：空值校验从"组织范围与用户属性条件不能同时为空"放宽为"组织范围、用户属性条件、请求控制条件三者不能同时为空"。
  - 修改"策略规则的手动执行"需求：组织范围与用户属性条件都未配置时的命中规则，从"不会出现这种情况"改为"命中全部启用状态用户"。
  - 新增"组织/人员/任职变更后策略自动重新执行"需求。

## Impact

- 后端：`cn.nihility.rbac.sso.controller.SsoLoginController`、`cn.nihility.rbac.loginlog.*`、`cn.nihility.rbac.appaccess.policy.*`、`cn.nihility.rbac.sync.event.support.DomainChangeEventProcessor`、新增一个日志清理定时任务组件与对应的 `@ConfigurationProperties` 配置类、`backend/src/main/resources/application.yml` 新增默认配置项。
- 前端：策略规则新增/编辑表单的前端校验提示文案需要同步调整（"组织范围与用户属性条件不能同时为空"→提示已放宽），具体交互由前端开发确认是否需要改动（校验最终以后端为准，前端可保持宽松或同步收紧提示）。
- 不涉及数据库表结构变更（不新增/修改任何 Flyway 迁移文件），策略执行、登录日志写入、日志清理均为已有表上的读写逻辑调整。
