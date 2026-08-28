## Why

目前 `tab_app` 里的每个应用一旦完成 SSO/OAuth2 协议对接，任何账号密码校验通过的用户都能登录并被签发访问该应用的凭证（CAS 服务票据 / OAuth2 授权码），没有"用户是否被允许访问这个应用"这一层控制。管理员需要按组织批量开通权限（如"财务部门用户默认能访问财务类应用"），同时还要能对个别用户做例外处理（临时加开一个不在批量范围内的应用，或临时收回某个本该有权限的用户的访问权），且这两类操作不能互相覆盖丢失——批量策略重新执行时不能把管理员手动做的例外抹掉，管理员的例外也要能明确压过策略结果。

## What Changes

- 权限管理菜单下新增"应用访问授权"二级菜单（`/permission/app-access`），提供策略规则管理、人工例外维护、最终生效权限查询三块界面。
- 新增"策略规则"能力：管理员配置"条件 → 目标应用（多选）"的规则，条件支持组织范围（含子组织可选）与用户属性条件两类，均可选配、同时配置时取交集，至少配置一类；用户属性条件直接复用"元数据字段管理"目录（`biz_type=USER`）选字段，运算符支持等于/不等于/属于多值。保存后需要管理员手动点击"执行"，系统按当前条件重新计算命中用户，整体重建该条策略产生的 `POLICY` 来源授权记录（不影响人工例外记录）。
- 新增"人工例外"能力：管理员可对具体的"用户 + 应用"组合手动追加授权（`MANUAL_GRANT`）或手动收回授权（`MANUAL_DENY`），后者是黑名单式覆盖，优先级最高。
- 新增"最终生效权限"计算与查询：`最终 = (策略授权 ∪ 手动追加授权) - 手动收回`，提供按用户或按应用查询最终生效权限的界面，且能看清每条最终授权的来源（策略/人工追加）以及是否被某条人工收回记录覆盖。
- **接入登录拦截**：CAS 服务票据签发（`CasController.login`）与 OAuth2 授权码签发（`OAuthController.authorize`）这两个"同时持有 userId + appId"的源头位置，新增一次最终生效权限校验，未授权时返回 HTTP 403 + 标准 `{code, message, data}` JSON 响应体（`code=403`，实现完成后按用户要求从最初的"复用协议参数校验的 400 文本响应"调整为此格式）。仅在凭证签发源头校验一次，`serviceValidate`/`token`/`userinfo` 等消费已签发凭证的环节不重复查权限表。

## Capabilities

### New Capabilities
- `app-access-authorization`：策略规则的定义与执行、人工例外（手动授权/手动收回）的维护、策略授权与人工例外的合并计算得到最终生效权限、按用户/按应用的授权查询与来源追溯、对应的管理端菜单与页面。

### Modified Capabilities
- `app-sso-protocol-runtime`：CAS 服务票据签发与 OAuth2 授权码签发流程新增一步"目标应用最终生效授权"校验，未授权的登录请求 SHALL 被拒绝。

## Impact

- 新增数据库表（`backend/src/main/resources/db/migration/V8__add_app_access_authorization.sql`，五张表）：`tab_app_access_policy`（策略规则）、`tab_app_access_policy_org_scope`（策略组织范围条件）、`tab_app_access_policy_user_attr`（策略用户属性条件，关联 `tab_metadata_field` 元数据字段目录）、`tab_app_access_policy_target_app`（策略目标应用）、`tab_app_access_policy_grant`（策略计算结果，POLICY 来源授权记录）——最终按 `design.md` Decision 2 拆成独立的人工例外表 `tab_app_access_manual_override`（`override_type` 为 GRANT/DENY），未采用"合并为同一张表 + `grant_source` 枚举区分"的方案，与策略计算结果表物理隔离。
- 新增后端模块，包名 `cn.nihility.rbac.appaccess`（按子域再分 `policy/`、`override/`、`support/` 三个子包，各自 controller/service/entity/mapper/mapstruct/dto 分层；`AppAccessEffectivePermissionService` 放在 `appaccess/support/` 下，供管理端查询接口与 SSO 拦截共用，见下）。
- 修改 `backend/src/main/java/cn/nihility/rbac/sso/cas/controller/CasController.java`（`login` 方法）、`backend/src/main/java/cn/nihility/rbac/sso/oauth/controller/OAuthController.java`（`authorize` 方法）：各新增一次授权校验调用，建议新校验方法与现有 `AppProtocolGuard` 同风格（放在 `sso/support/` 下或新增一个校验组件，供两处复用）。
- 新增前端路由/页面：`frontend/src/router/menu.ts` 权限管理分组新增子菜单项；新增 `frontend/src/views/permission/app-access/` 下的策略规则、人工例外、最终生效权限查询页面；`frontend/src/api/`、`frontend/src/types/` 新增对应模块。
- 更新根目录 `权限资源.txt`，补充 `AppAccessManagement:*` 一组权限点编码。
- 新增 `backend/src/main/resources/db/migration/V9__add_app_access_authorization_menu.sql`：补齐 `tab_menu`/`tab_permission` 种子数据并显式授予 `SUPER_ADMIN` 角色（`V8` 只建表未补种子数据，导致默认管理员看不到新菜单，实现后发现并修正）。
