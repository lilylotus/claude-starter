## Why

`tab_app_auth_config` 目前为每种单点登录协议单独开一列存回跳地址匹配规则
（`cas_service_patterns` 服务 CAS 协议、`oauth2_redirect_uri_patterns` 服务 OAuth2.0
协议），但两列在存储结构（ANT 匹配规则的 JSON 字符串数组）、校验逻辑（协议启用时至少
一条规则、ANT 匹配）、使用方式（登录/登出时按 `service`/`redirect_uri` 做前缀匹配）上
完全一致，唯一区别只是"当前协议类型用哪一列"。后续每新增一种单点登录协议（如
OIDC、SAML）都要重复开一列、重复一遍两处（entity/VO/UpdateRequest/MapStruct/校验/
`AppProtocolGuard` 分支）几乎相同的代码，维护成本随协议种类线性增长。合并为协议无关的
单一匹配列表字段，能一次性消除这种重复，后续新增协议不再需要动表结构。

## What Changes

- `tab_app_auth_config` **BREAKING**：删除 `cas_service_patterns`、
  `oauth2_redirect_uri_patterns` 两列，新增单一列 `service_patterns`（JSON 字符串数组
  文本，语义为"当前协议下允许的回跳地址 ANT 匹配规则列表"，CAS/OAuth2.0 及未来新增协议
  共用同一份存储）。迁移脚本按当前 `auth_protocol` 取值把两列中非空的那一列数据迁移进
  新列（协议类型为"无"时新列为空数组）。
- 后端 `AppAuthConfigEntity`/`AppAuthConfigVO`/`AppAuthConfigUpdateRequest`
  **BREAKING**：`casServicePatterns`、`oauth2RedirectUriPatterns` 两个字段合并为单一
  `servicePatterns` 字段。
- `AppProtocolGuard` 内 CAS/OAuth2 两套校验方法（`assertCasServiceAllowed`/
  `assertOAuthRedirectUriAllowed`/`assertLogoutServiceAllowed`）原先"按协议类型选择读
  哪一列"的分支逻辑简化为统一读 `servicePatterns`，只保留"协议类型是否匹配预期"这一层
  校验，不再有按协议分叉的匹配列表读取代码。
- `AppAuthConfigServiceImpl` 的"协议类型与匹配列表的关联校验"规则从两条（CAS 校验
  `casServicePatterns`、OAuth2 校验 `oauth2RedirectUriPatterns`）合并为一条：协议类型
  非"无"时 `servicePatterns` 至少一条规则，协议类型为"无"时清空。
- 前端应用认证管理配置表单 **BREAKING**：CAS 的"service 匹配列表"、OAuth2 的
  "redirect_uri 匹配列表"两个独立编辑区合并为一个协议无关的"回跳地址匹配列表"编辑区，
  随协议类型切换时不再是两套独立的行数组状态，而是同一份 `servicePatterns` 列表。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `app-auth-protocol-config`: "应用创建时自动初始化认证配置"（默认值从两个空列表改为
  一个空列表）、"查询应用认证配置"（返回字段合并）、"修改应用认证配置"（请求字段合并）、
  "协议类型与匹配列表的关联校验"（两条规则合并为一条）需求变更。
- `app-sso-protocol-runtime`: "CAS 单点登录"、"CAS 单点登出"、"OAuth2 授权"、"全局单点
  登出接口" 四个需求中"匹配 CAS service 匹配列表/OAuth2 redirect_uri 匹配列表"的表述统一
  改为"匹配 `servicePatterns` 匹配列表"，校验行为本身（ANT 匹配、不匹配即拒绝）不变。

## Impact

- 后端：`cn.nihility.rbac.app.authconfig.entity.AppAuthConfigEntity`、
  `dto.AppAuthConfigVO`/`AppAuthConfigUpdateRequest`、
  `mapstruct.AppAuthConfigConvert`、
  `service.impl.AppAuthConfigServiceImpl`（校验/保存逻辑）、
  `cn.nihility.rbac.sso.support.AppProtocolGuard`（三个校验方法）、Flyway 迁移脚本
  `V4__*.sql`（列合并 + 数据迁移，MySQL 5.7 兼容写法）。
- 前端：`frontend/src/views/application/app/AppConfigView.vue` 认证管理表单的匹配列表
  编辑区、`frontend/src/types/app.ts`、`frontend/src/api/app.ts` 涉及字段的类型/调用。
- 文档：`SSO单点登录接入规范.md` 中涉及"CAS service 匹配列表"/"OAuth2 redirect_uri 匹配
  列表"两个独立字段名的描述需同步改为 `servicePatterns`。
- 数据库：`tab_app_auth_config` 列结构变更（迁移脚本），历史数据按当前协议类型迁移到新列。
- 无新增第三方依赖。
