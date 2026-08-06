## ADDED Requirements

### Requirement: 统一解析当前登录操作人账号编码
系统 SHALL 提供一个位于 `cn.nihility.rbac.auth.service` 包下的服务 `CurrentOperatorService`，基于 `CurrentUserContext.getUserId()`（`IdentityAuthFilter` 校验通过的已登录会话中已设置）解析出当前请求发起者的登录账号编码（`tab_user.code`），供各业务模块的新增/编辑/启停用/删除等写操作复用，填充其 `create_by`/`update_by` 审计字段，以及操作日志的 `create_by` 字段。各业务模块 SHALL NOT 再使用与登录会话无关的固定字符串常量填充这些字段。

`CurrentUserContext.getUserId()` 取不到值（当前线程不处于已认证的 HTTP 请求上下文中）时，SHALL 视为调用方用法错误而不是静默降级为某个固定占位符——业务写操作的正常调用路径均发生在 `IdentityAuthFilter` 校验通过之后的同一线程内，取不到值意味着调用方脱离了预期的调用上下文（如遗漏在测试中设置登录态）。

#### Scenario: 已登录会话下解析出真实操作人账号编码
- **WHEN** 某个已登录账号（`tab_user.code` 为某个具体值，如 "ops01"）发起一次新增/编辑等写操作
- **THEN** 该次写操作落库的 `create_by`/`update_by`（或产生的操作日志 `create_by`）等于该账号的 `code`，而不是固定字符串

#### Scenario: 不同账号发起的操作各自归属到本人
- **WHEN** 账号 A 和账号 B 先后各自发起一次写操作
- **THEN** A 产生的记录 `create_by` 为 A 的账号编码，B 产生的记录 `create_by` 为 B 的账号编码，两者不同且都不是同一个固定值

#### Scenario: 脱离已登录上下文调用时不静默使用固定占位符
- **WHEN** 调用方在没有先设置 `CurrentUserContext` 当前用户 id 的情况下调用 `CurrentOperatorService` 解析操作人
- **THEN** 系统抛出运行时异常，不返回任何固定字符串（如 "admin"/"system"）作为兜底账号编码
