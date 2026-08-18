## Why

`app-auth-protocol-config` change 已经落地了应用维度的单点登录协议配置（协议类型选择、
CAS service / OAuth2 redirect_uri 的 ANT 匹配列表维护），但明确把协议运行时端点留给
后续 change（proposal.md Non-Goals）。现在需要把这些配置真正用起来：实现 CAS 协议
（`/login`、`/p3/serviceValidate`、`/logout`）与 OAuth2.0 协议（`/authorize`、`/token`、
`/userinfo`）的运行时端点，外部应用才能真正接入本系统做单点登录。

## What Changes

- 新增一套独立的 SSO 专用登录页面与接口（不复用管理端 `/login` 页面与 `AuthController`/
  `AuthServiceImpl`）：`GET /api/authn/sso/public-key`、`POST /api/authn/sso/login`，
  登录成功后签发一个 HttpOnly、Redis 存储的浏览器 SSO 会话 Cookie；前端新增独立路由
  `/sso/login` 与 `SsoLoginView.vue`，不接入现有 `stores/auth.ts`/`api/request.ts`。
- 新增 CAS 协议运行时端点：
  - `GET /api/authn/cas/{appId}/login?service=xxx` — 校验 `service` 匹配已配置的 ANT
    规则，已登录（有效 SSO 会话）则签发服务票据（ST）并重定向回 `service`；未登录则
    重定向到 `/sso/login?redirect=...`。
  - `GET /api/authn/cas/{appId}/p3/serviceValidate?service=xxx&ticket=xxx` — 校验并
    消费（一次性）票据，返回 CAS 3.0 格式的 XML 响应。
  - `GET /api/authn/cas/{appId}/logout` — 清除当前浏览器的 SSO 会话。
- 新增 OAuth2.0 协议运行时端点：
  - `GET /api/authn/oauth/authorize?response_type=code&client_id=xxx&redirect_uri=xxx&scope=xxx&state=xxx`
    — 校验 `client_id` 对应应用协议类型为 OAuth2.0 且 `redirect_uri` 匹配已配置的 ANT
    规则，已登录则签发授权码并重定向回 `redirect_uri`；未登录则重定向到
    `/sso/login?redirect=...`。
  - `POST /api/authn/oauth/token`，支持两种 `grant_type`：
    - `authorization_code`（`client_id`/`client_secret`/`redirect_uri`/`code`）—
      校验通过后签发 access token **与 refresh token**，返回 OAuth2 标准 JSON 响应
      （`access_token`/`token_type`/`expires_in`/`refresh_token`）。
    - `refresh_token`（`refresh_token`）— 校验通过后签发新的 access token，返回
      `access_token`/`token_type`/`expires_in`（不要求携带 `client_id`/
      `client_secret`，按用户明确给出的参数列表实现，见 design.md Risks 的说明）。
  - `GET /api/authn/oauth/userinfo`（`Authorization: Bearer <access_token>`）— 返回
    当前令牌对应的用户基本信息。
- CAS 票据（ST）、OAuth2 授权码、OAuth2 access token、SSO 会话均为短期凭证，存 Redis，
  不落 MySQL（对齐现有 `TokenServiceImpl` 的既有模式）。
- `IdentityAuthFilter` 白名单新增 `/api/authn/**`（这些端点面向外部浏览器/应用，不使用
  管理端 SPA 的 `identity-token`/`menu` 请求头鉴权机制）。

## Capabilities

### New Capabilities

- `app-sso-protocol-runtime`：CAS 与 OAuth2.0 单点登录协议的运行时实现——协议端点、
  票据/授权码/令牌签发与校验、浏览器 SSO 会话、独立的 SSO 登录页面。

### Modified Capabilities

（无——`app-auth-protocol-config` 的配置管理需求本身不变，本次只是让已存储的配置产生
真正的运行时行为，不改变该 change 已落地的需求文本）

## Impact

- 代码：新增后端顶层模块 `cn.nihility.rbac.sso`（含 `session`/`cas`/`oauth` 子模块）；
  `IdentityAuthFilter` 白名单调整；前端新增 `views/sso/SsoLoginView.vue`、
  `router/index.ts` 新增 `/sso/login` 路由、新增独立的 `api/sso.ts`（不复用
  `api/request.ts`）。
- 依赖：无新增第三方依赖（HttpOnly Cookie 用 Servlet 原生 API 手写响应头，CAS XML
  用字符串拼接或 JAXB 均可，Redis 已是既有依赖）。
- 数据库：无新增/修改表（凭证均是 Redis 短期数据）。
- 安全面：新增一组完全公开（无需管理端登录态）的端点，需要重点做好 `service`/
  `redirect_uri` 的白名单匹配校验（防开放重定向）、票据/授权码一次性消费、
  `client_secret` 校验。

## Non-Goals

- 除 `authorization_code`/`refresh_token` 外的其它 OAuth2 授权类型（implicit/
  client_credentials/password）。
- CAS 代理票据（PGT/PT）、CAS 2.0 之前版本的 `/serviceValidate`（只做 3.0 的
  `/p3/serviceValidate`）。
- 单点登出向其它已登录的第三方应用做后端回调通知（back-channel SLO）——本次
  `/logout` 只清除浏览器自身的 SSO 会话。
- OAuth2 `scope` 参数不做真正的权限范围过滤，`/userinfo` 固定返回同一组基本身份字段。
