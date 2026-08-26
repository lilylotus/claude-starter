## Context

见 `proposal.md`。当前管理端登录由 `AuthServiceImpl` 返回 `firstLogin`，前端管理端路由据此进入 `/change-password`；SSO 则由独立的 `SsoLoginController`、HttpOnly `sso_session` 和 `SsoLoginView.vue` 组成，明确不接入管理端登录 store。现有 SSO 登录接口成功后返回空数据并无条件签发会话，CAS/OAuth2.0 入口只校验会话存在性，因此首登状态被绕过。

## Goals / Non-Goals

**Goals:**

- CAS 与 OAuth2.0 共用一套 SSO 首登强制改密流程。
- 改密前禁止签发 CAS 服务票据和 OAuth2.0 授权码，包括复用已有会话的场景。
- 保留原协议请求及其查询参数，改密成功后无损恢复。
- 保持 SSO 会话与管理端 access-key/refresh-key 完全独立。

**Non-Goals:**

- 不改变管理端登录与 `/change-password` 流程。
- 不撤销首登状态设置前已经签发的 CAS 票据或 OAuth2.0 token。
- 不修改密码复杂度规则、数据库结构、登录日志粒度或 SSO Cookie 配置。
- 不处理现有 `redirect` 参数的开放重定向风险，该地址仍由协议入口按现有方式生成和恢复。

## Decisions

### 1. SSO 登录成功后返回 firstLogin，并始终建立可鉴权但受限的会话

新增 `SsoLoginResponse`，`POST /api/authn/sso/login` 返回 `{ firstLogin }`。凭证校验成功后仍签发 HttpOnly `sso_session`：该会话仅用于识别改密用户，CAS/OAuth2.0 入口会在发放协议凭证前执行第二层首登检查，因此它不是可绕过门禁的完整授权。

备选方案是首登时不签发任何会话、返回一次性改密 token。放弃原因是需要新增 token 存储、有效期和消费语义；现有 SSO 会话已经是 HttpOnly、服务端 Redis 可验证的短期身份凭据，复用后再在协议入口做状态门禁更简单，也能覆盖管理员在已有会话期间重置密码的场景。

### 2. 新增只接受 SSO Cookie 的首登改密接口

新增 `POST /api/authn/sso/password`，请求体复用 `ChangePasswordRequest` 的旧密码/新密码规则。控制器从 `sso_session` Cookie 解析用户 id，要求会话有效且用户当前仍处于首登待改密状态，随后校验旧密码并调用 `PasswordService#updatePassword`。接口不读取管理端 `identity-token`，也不向前端暴露用户 id 或会话 token。

错误场景保持业务错误响应：会话无效、状态已清除、旧密码错误均不更新密码。改密成功后保留当前 SSO 会话，前端可直接恢复原协议请求。

### 3. CAS/OAuth2.0 在协议凭证签发前统一二次门禁

`CasController#login` 与 `OAuthController#authorize` 在 `ssoSessionService.verify` 成功后、应用授权检查和凭证签发前调用 `PasswordService#isFirstLogin`。若为待改密，重定向到 SSO 登录页，并在现有 `redirect` 参数外增加 `forcePasswordChange=true`。这既阻止用户手工跳过前端步骤，也覆盖“SSO 会话建立后管理员重置密码”。

`ProtocolResponseWriter` 增加可表达强制改密的重定向构造方法，继续对原完整协议 URL 做 URL 编码，不复制 CAS/OAuth2.0 参数拼装逻辑。

### 4. SSO 登录页采用同页双状态，不进入管理端路由体系

`SsoLoginView.vue` 保留现有左右双栏、品牌蓝、链式连接图和响应式布局；右侧面板在 `LOGIN` 与 `CHANGE_PASSWORD` 两个状态间切换：

- 正常进入显示账号密码表单。
- 登录响应 `firstLogin=true`，或 URL 带 `forcePasswordChange=true` 时，标题切换为“首次登录，请修改密码”，展示旧密码、新密码、确认密码。
- 改密成功后显示成功提示，并执行原有 `window.location.href = redirect || '/'`。

视觉签名继续是左侧“身份→授权→凭证→应用”的链路；改密状态只调整右侧标题、说明和表单，不添加额外装饰或新路由，避免同一认证过程出现两个互不相干的界面。移动端维持当前单面板布局，键盘回车与 loading 状态分别绑定当前表单动作。

### 5. 测试覆盖响应、Cookie 鉴权和协议门禁

- `SsoLoginControllerTest`：普通用户返回 `firstLogin=false`；首登用户返回 `true` 且有 Cookie；有效 Cookie 改密成功；无 Cookie、旧密码错误不更新。
- `CasControllerTest` / `OAuthControllerTest`：有效会话但首登状态为真时重定向到强制改密页且不签发凭证；状态为假保持现有流程。
- 前端以 `npm run build` 验证 TypeScript、模板和样式，必要时对双状态逻辑增加单元覆盖（项目存在对应测试基础时）。

## Risks / Trade-offs

- [Risk] 首登用户已经拿到有效 SSO Cookie → CAS/OAuth2.0 后端二次检查首登状态，Cookie 只能用于改密鉴权，不能直接换取协议凭证。
- [Risk] 管理员重置密码时用户停留在外部应用 → 本次仅阻止后续新票据/授权码签发，不主动撤销已签发凭证，符合 Non-Goals。
- [Risk] 用户直接访问带 `forcePasswordChange=true` 的页面但无有效 Cookie → 改密接口明确拒绝，不泄露用户信息；用户可重新走原 SSO 登录流程。
- [Trade-off] SSO 登录页同时承担登录与首登改密 → 两者属于同一次外部应用认证旅程，同页切换能可靠保留 `redirect`，比接入管理端改密路由更少耦合。

## Migration Plan

无需数据迁移。后端与前端应同批发布，因为旧前端会忽略新的 `firstLogin` 数据并继续回跳；后端协议入口虽能阻止票据签发，但会造成旧页面反复登录。回滚时同时恢复后端接口/门禁与前端双状态实现。

## Implementation Verification

实现与上述 Decisions 一致：SSO 登录先签发受限会话并返回 `firstLogin`；改密接口仅从
`sso_session` 解析身份；CAS/OAuth2.0 在协议凭证签发前二次检查；前端在同一页面切换表单并
恢复原始 `redirect`。最终没有新增依赖、数据库迁移、路由或管理端认证状态耦合。

已执行以下验证：

- `gradlew test --tests "cn.nihility.rbac.sso.controller.SsoLoginControllerTest" --tests "cn.nihility.rbac.sso.cas.controller.CasControllerTest" --tests "cn.nihility.rbac.sso.oauth.controller.OAuthControllerTest"`：通过。
- `gradlew test`：通过。
- `npm run build`：通过。
- `openspec validate enforce-sso-first-login-password-change --strict`：通过。
