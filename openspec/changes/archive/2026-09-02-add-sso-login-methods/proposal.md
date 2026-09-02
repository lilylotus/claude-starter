## Why

SSO 登录页（`SsoLoginView.vue`，由 `CasController`/`OAuthController` 在浏览器无有效 SSO 会话时
跳转过去）目前只支持口令登录一种方式。业务上需要为接入 CAS/OAuth2.0 单点登录的应用提供更贴近
终端用户习惯的登录方式——短信验证码登录、扫码登录，并允许每个应用的管理员按需选择该应用 SSO
登录时对外展示哪些认证方式，而不是所有应用被迫使用同一套。

## What Changes

- 新增短信验证码登录：SSO 登录页可切换到"短信验证码"标签页，输入手机号获取验证码（Redis
  存储验证码与发送频次/校验失败次数限制防刷）、输入验证码完成登录；短信发送封装为可插拔
  接口，当前提供仅用于非生产环境的 Mock 实现（验证码写日志/接口透传，不接入真实短信厂商），
  真实厂商对接留待后续 change。
- 新增扫码登录：SSO 登录页可切换到"扫码登录"标签页，展示由后端签发的一次性二维码会话；
  用户使用手机浏览器扫码后跳转到独立的响应式确认页（未登录本系统则先走口令登录），确认后
  PC 端登录页轮询到"已确认"状态即完成登录；扫码登录成功后复用现有 `SsoSessionService`
  签发同一套 SSO 会话 Cookie，与口令、短信登录产出完全一致的登录态。
- 应用认证配置（`app/authconfig` 模块，应用配置页"认证管理"标签页）新增"允许的登录认证
  方式"勾选项：口令固定必选且不可关闭，短信、扫码可按应用单独启用/停用；SSO 登录页根据
  当前请求所属应用的这份配置，只展示该应用允许的认证方式标签页。
- 管理端直接登录页（`LoginView.vue`）不受影响，固定只使用口令登录。
- 短信/扫码开关的保存复用现有"修改认证管理配置"权限点（`AppManagement:app:config:editAuth`），
  不新增权限点；同步更新仓库根目录 `权限资源.txt` 中该权限点的描述文案，覆盖新增的配置项。

## Capabilities

### New Capabilities
- `sso-login-methods`：SSO 登录页短信验证码登录、扫码登录两种认证方式的端到端能力（验证码
  签发/校验/限流、二维码会话签发/扫码确认/轮询、登录成功后签发 SSO 会话），以及登录页按
  应用配置展示可用认证方式标签页的行为。

### Modified Capabilities
- `app-auth-protocol-config`：应用认证配置新增"允许的登录认证方式"字段（口令/短信/扫码），
  查询、修改接口返回值与入参相应扩展；"修改认证管理配置"权限点的校验范围覆盖该新字段。

## Impact

- 后端：`app/authconfig` 模块（entity/dto/service/controller）扩展；新增短信验证码、二维码
  会话相关的 entity/dto/service/controller（具体包路径见 design.md）；`sso/controller` 新增
  短信登录、扫码登录相关接口；`sso/session` 复用 `SsoSessionService`；数据库新增/修改
  Flyway 迁移脚本（`tab_app_auth_config` 加字段，短信/二维码会话状态存 Redis 不新增表，
  具体见 design.md）。
- 前端：`views/sso/SsoLoginView.vue` 改为多标签页登录（口令/短信/扫码），新增扫码确认页
  （移动端响应式），`views/application` 下应用配置页"认证管理"标签页新增勾选项，
  `api/sso.ts`、`api/app*` 相关请求封装扩展。
- 权限资源编码：`权限资源.txt` 同步调整 `AppManagement:app:config:editAuth` 的描述文案。
- 无破坏性变更：现有口令登录、CAS/OAuth2.0 协议运行时行为不变，新增能力默认对已有应用
  关闭（沿用"口令必选、短信/扫码默认不启用"的初始状态），不影响存量应用的登录体验。
