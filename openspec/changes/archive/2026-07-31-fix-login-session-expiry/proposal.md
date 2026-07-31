## Why

登录会话相关的两处行为存在安全隐患，需要修正：一是 refresh-key 默认有效期 7 天（`604800` 秒），实际使用场景下过长，一旦 refresh-key 泄露，攻击窗口期过大；二是前端把整个登录会话（含 accessKey、refreshKey）序列化后存进 `localStorage`，`localStorage` 在浏览器关闭重启后依然存在，导致用户关闭浏览器后重新打开，之前的登录态仍然有效（即便 access-key 已过期，还能用留存的 refresh-key 静默换发新的 access-key），效果上等同于"关闭浏览器也不退出登录"，不符合预期的会话生命周期。

## What Changes

- refresh-key 默认有效期由 `604800` 秒（7 天）调整为 `14400` 秒（4 小时）：同步修改 `RbacLoginProperties.refreshTokenExpireSeconds` 的默认值与 `application.yml` 里 `rbac.user.login.refresh-token-expire-seconds` 的配置值（两处保持一致），签发/续期逻辑（`TokenServiceImpl`）本身不改动，仍然从配置读取。
- 前端登录会话（`frontend/src/stores/auth.ts` 里的 `accessKey`、`accessExpireAt`、`refreshKey`、`refreshExpireAt`、`firstLogin`、`accountCode`）改为存放在 `sessionStorage` 而不是 `localStorage`：`accessKey`、`refreshKey` 必须一起迁移，不能只迁移 `accessKey`——否则 `refreshKey` 仍留在 `localStorage` 里，浏览器重启后前端请求拦截器依旧能用它静默换发新 `accessKey`，达不到"关闭浏览器后不能再访问"的目标。迁移后，浏览器（或最后一个使用该站点的标签页）关闭时登录态自动清空，重新打开需要重新登录。

## Capabilities

### New Capabilities
(无)

### Modified Capabilities
- `password-login-auth`：「前端登录态与自动重定向」需求补充场景，约束登录态存储范围限定在浏览器会话内，浏览器关闭后不再保留。

## Impact

- 后端：`backend/src/main/java/cn/nihility/rbac/auth/config/RbacLoginProperties.java`（默认值+注释）、`backend/src/main/resources/application.yml`（配置值+注释）。
- 前端：`frontend/src/stores/auth.ts`（`loadSession`/`persist`/`logout` 三处 storage 调用从 `localStorage` 改为 `sessionStorage`）。
- 不改变任何接口的请求/响应结构，不改变 access-key/refresh-key 签发、校验、静默刷新的业务逻辑本身。
- 不引入新的第三方依赖。
