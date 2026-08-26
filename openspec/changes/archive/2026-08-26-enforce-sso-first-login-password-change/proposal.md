## Why

CAS/OAuth2.0 未建立会话时会跳转到独立的 SSO 登录页，但该入口在密码校验成功后无条件签发会话并回跳，明确绕过了首次登录强制改密。新用户或密码被管理员重置的用户因此可以直接进入外部应用，与系统既有的首登安全策略不一致。

## What Changes

- SSO 登录成功响应增加 `firstLogin` 状态；待改密用户建立受限 SSO 会话后停留在 SSO 页面，不立即返回 CAS/OAuth2.0 协议请求。
- 新增基于 HttpOnly `sso_session` 鉴权的 SSO 首登改密接口，校验原密码并更新密码，成功后清除首登状态。
- SSO 登录页在凭证校验成功且 `firstLogin=true` 时原地切换为强制改密表单，改密成功后恢复原始 `redirect` 整页跳转。
- CAS 服务票据与 OAuth2.0 授权码签发前再次检查首登状态；即使复用已有 SSO 会话或管理员在会话期间重置密码，也不得绕过改密。
- 增加后端控制器及前端构建回归，覆盖首次登录、正常登录、已有会话被重置密码、改密成功与失败场景。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `app-sso-protocol-runtime`: SSO 专用登录、CAS 登录和 OAuth2.0 授权流程增加首次登录强制改密门禁及 SSO 专用改密交互。
- `password-login-auth`: 首次登录强制改密规则扩展到独立 SSO 会话，确保 CAS/OAuth2.0 协议不会绕过该状态。

## Impact

- 后端：SSO 登录控制器及 DTO、CAS/OAuth2.0 授权入口、协议重定向辅助方法和相关测试。
- 前端：SSO API/类型、`SsoLoginView.vue` 的登录/改密双状态交互。
- API：`POST /api/authn/sso/login` 成功数据由空值改为包含 `firstLogin`；新增 `POST /api/authn/sso/password`。
- 不修改数据库结构、不新增依赖、不复用或写入管理端 SPA 登录态。

## Implementation Result

- 已新增 `SsoLoginResponse` 和 SSO Cookie 首登改密接口；登录响应、有效会话改密、无会话、
  原密码错误及首登状态已清除场景均由控制器集成测试覆盖。
- CAS 登录与 OAuth2.0 授权在应用授权检查、票据/授权码签发前执行首登状态门禁，并通过
  `forcePasswordChange=true` 保留原协议请求后回到 SSO 页面。
- SSO 页面已实现登录/改密双状态，继续使用独立 axios 实例和 HttpOnly Cookie，不接入管理端
  auth store；前端 TypeScript 与生产构建通过。
- 验证结果：三组 SSO/CAS/OAuth2.0 聚焦测试通过，后端全量 `gradlew test` 通过，前端
  `npm run build` 通过，OpenSpec strict validation 通过。
