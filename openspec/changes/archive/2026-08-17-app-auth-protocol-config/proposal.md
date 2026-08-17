## Why

外部应用要接入本系统做单点登录（CAS 或 OAuth2.0 协议），需要先在管理端为该应用选定使用哪种
协议、并配置协议允许接受的回跳地址（CAS 的 `service` 参数 / OAuth2 的 `redirect_uri`）白名单，
否则协议端点无法校验回跳地址合法性、也无从得知该按哪种协议处理请求。这是接入 CAS/OAuth2
单点登录能力的前置配置能力，先落地。

真正的协议运行时逻辑（CAS 票据签发/校验、OAuth2 授权码/令牌/用户信息端点、浏览器 SSO 会话）
工作量大、涉及新的会话模型，按用户明确决定分阶段实现，作为后续独立 change，本次不做（见
Non-Goals）。

## What Changes

- `应用管理 → 应用配置` 页面新增「认证管理」标签页（与现有「基础信息」「同步配置」并列）：
  - 协议下拉选择：无 / CAS / OAuth2.0。
  - 选择 CAS 时：维护 `service` 参数 ANT 表达式匹配列表（增删改查），只读展示 3 个 CAS
    协议接口地址（登录、票据验证、登出，均含该应用的 `appId`）。
  - 选择 OAuth2.0 时：维护 `redirect_uri` ANT 表达式匹配列表（增删改查），只读展示协议
    接口定义（授权接口及其参数说明、Access Token 接口、用户信息接口）。
- 后端新增 `tab_app_auth_config` 表（与 `tab_app` 一对一，新建应用时随 `tab_app_config` 一并
  创建默认行），新增查询/修改该配置的接口。
- 新增权限点 `AppManagement:app:config:editAuth`，同步更新 `权限资源.txt`。

## Capabilities

### New Capabilities

- `app-auth-protocol-config`：应用维度的单点登录协议配置能力——协议类型选择（无/CAS/
  OAuth2.0）、CAS service 匹配列表与 OAuth2 redirect_uri 匹配列表的维护、协议接口地址的
  只读展示。不包含协议运行时鉴权逻辑。

### Modified Capabilities

（无）

## Impact

- 代码：新增后端包 `cn.nihility.rbac.app.authconfig`（entity/mapper/service/impl/dto/
  controller/mapstruct/constant），复用 `cn.nihility.rbac.app.support.AppScopeGuard`、
  `OperationLogRecorder`；`AppConfigServiceImpl.createDefaultConfig` 增加一次调用创建默认
  认证配置行（对齐现有 `AppSyncConfigService.createDefaultDomainConfigs` 的接线方式）。
  数据库：`backend/src/main/resources/db/migration/V1__init_schema.sql` 新增
  `tab_app_auth_config` 表（项目当前处于单一 V1 初始化脚本阶段，新表直接并入 V1，不新开
  V2，与近期 `identity-upstream-data-sync` 等变更的做法一致）。前端：
  `frontend/src/views/application/app/AppConfigView.vue` 新增第三个 tab；新增
  `frontend/src/api/appAuthConfig.ts`、`frontend/src/types/appAuthConfig.ts`（或并入现有
  `types/app.ts`，视 design.md 决策）。文档：`权限资源.txt` 新增一行权限点。

## Non-Goals（本次不做，留给后续 change）

- CAS 协议运行时端点：`/api/authn/cas/{appId}/login`（含"未登录跳转登录页"判断）、
  `/api/authn/cas/{appId}/p3/serviceValidate`、`/api/authn/cas/{appId}/logout`。
- OAuth2.0 协议运行时端点：`/api/authn/oauth/authorize`（含 `response_type`/`client_id`/
  `redirect_uri`/`scope`/`state` 参数处理）、Access Token 签发端点、用户信息端点。
- 支撑上述运行时端点所需的、独立于现有管理端 SPA Bearer Token 之外的浏览器 Cookie SSO
  会话机制（已与用户确认采用"新增基于 Cookie 的浏览器 SSO 会话"方案，留待运行时端点那次
  change 的 design.md 落地）。
