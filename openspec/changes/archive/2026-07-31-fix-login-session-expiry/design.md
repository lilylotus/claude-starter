## Context

`RbacLoginProperties`（`rbac.user.login` 前缀）的 `refreshTokenExpireSeconds` 默认值 `604800`（7 天）由 `TokenServiceImpl` 在签发登录会话时用于设置 Redis 里 refresh-key 记录及其 TTL 的过期时间（`TokenServiceImpl.java:67/77/82`）；该值目前既是 Java 侧的字段默认值，又在 `application.yml` 里被显式覆盖为同一个 `604800`，两处需要同步修改，否则 `application.yml` 的显式配置会继续生效，字段默认值的修改不会体现在实际运行的应用里。

前端 `frontend/src/stores/auth.ts` 用一个 Pinia store 持有 `accessKey`/`accessExpireAt`/`refreshKey`/`refreshExpireAt`/`firstLogin`/`accountCode`，`loadSession()`（初始化时读取）、`persist()`（每次 `login`/`refreshAccess`/`setFirstLogin` 后写入）、`logout()`（清空时删除）三个函数分别对应 `localStorage.getItem`/`setItem`/`removeItem`，key 固定为 `rbac_auth_session`。`localStorage` 没有过期概念，写入后会一直保留到被显式删除或用户手动清理浏览器数据，与浏览器进程的生命周期无关；`sessionStorage` 的语义是"每个浏览器标签页/窗口一份，标签页关闭即清空"（多个标签页打开同一站点，各自有独立的 `sessionStorage`，不共享）。`frontend/src/stores/currentUserPermission.ts` 里的权限编码集合已经是纯内存持有（不落任何 storage），不受本次改动影响。

## Goals / Non-Goals

**Goals:**
- refresh-key 默认有效期收紧为 4 小时，减小 refresh-key 一旦泄露后的可利用窗口。
- 浏览器关闭后，之前登录产生的 access-key/refresh-key 均不再可用（不能通过重新打开浏览器继续访问业务接口，也不能被静默刷新逻辑续期）。

**Non-Goals:**
- 不改变 access-key 默认有效期（仍为 2 小时，`accessTokenExpireSeconds` 不在本次改动范围）。
- 不引入"记住我"这类允许用户主动选择跨浏览器进程保留登录态的功能。
- 不改变后端 access-key/refresh-key 的签发、校验、静默刷新接口的请求/响应结构或业务逻辑，只改配置默认值。
- 不处理"多标签页登录态是否共享"的问题——`sessionStorage` 天然是标签页级隔离，每个标签页各自登录，这是本次改动引入的新副作用，但在预期范围内接受（前端登录页/静默刷新逻辑本身没有对"多标签页共享登录态"做任何保证或依赖，改动前后行为一致地各自独立）。

## Decisions

### Decision 1：refresh-key 默认有效期改为 `14400` 秒（4 小时），只改配置值不改签发逻辑
`TokenServiceImpl` 已经是从注入的 `RbacLoginProperties` 读取 `getRefreshTokenExpireSeconds()` 来设置 Redis TTL 和响应体里的过期时间戳，不需要改动这部分代码；只需要同步修改两处配置源：
- `RbacLoginProperties.refreshTokenExpireSeconds` 字段默认值 `604800` → `14400`，Javadoc 注释同步更新为"默认 14400 秒（4 小时）"。
- `application.yml` 里 `rbac.user.login.refresh-token-expire-seconds: 604800` → `14400`，行内注释同步更新。

两处都要改是因为 `application.yml` 里的显式配置值优先级高于 Java 字段默认值，只改一处会导致实际生效值与预期不符（`RbacLoginProperties` 的默认值只在 `application.yml` 未显式配置该项时才生效）。

### Decision 2：前端登录会话整体从 `localStorage` 迁移到 `sessionStorage`，`accessKey`/`refreshKey` 必须一起迁移
只把 `accessKey` 迁移到 `sessionStorage`、`refreshKey` 仍留在 `localStorage` 是一个容易踩的坑：`request.ts` 的静默刷新拦截器在检测到 `accessKey` 过期/失效时，会自动用 `refreshKey` 换发新的 `accessKey`（`auth.ts` 的 `refreshAccess()`）；如果 `refreshKey` 仍然能在浏览器重启后从 `localStorage` 读到，即便 `accessKey` 因为 `sessionStorage` 被清空而"消失"，前端也会自动静默刷新出一个新的 `accessKey`，用户观感上仍然是"关闭浏览器重新打开还是登录状态"，没有达到目标行为。因此 `AuthSession` 的六个字段（`accessKey`、`accessExpireAt`、`refreshKey`、`refreshExpireAt`、`firstLogin`、`accountCode`）作为一个整体一起从 `localStorage` 迁移到 `sessionStorage`，`STORAGE_KEY` 常量值不变（`rbac_auth_session`），`loadSession`/`persist`/`logout` 三处调用点分别把 `localStorage.getItem`/`setItem`/`removeItem` 替换为 `sessionStorage.getItem`/`setItem`/`removeItem`，不改变序列化格式（仍是 `JSON.stringify`/`JSON.parse`）和其余业务逻辑（`isAccessValid`/`isRefreshValid`/`isLoggedIn` 等计算属性、`login`/`refreshAccess`/`setFirstLogin`/`logout` 的方法体不变）。

考虑过的替代方案：只缩短 `refreshExpireAt` 的本地判定但仍用 `localStorage`——这不能解决问题，因为 refresh-key 有效期缩短到 4 小时后，浏览器在 4 小时内关闭重启仍然能继续用未过期的 refresh-key 静默续期，只是把攻击窗口从 7 天缩短到 4 小时，没有满足"浏览器关闭后不能再访问"这个独立于有效期长短的诉求；两个改动是互补关系（有效期收紧防"长期泄露被滥用"，storage 迁移防"关闭浏览器不等于退出登录"），不能相互替代。

## Risks / Trade-offs

- **[风险/副作用] 迁移到 `sessionStorage` 后，同一用户在多个标签页打开该站点将不再共享登录态**：每个标签页各自独立登录、各自的会话独立过期；用户在标签页 A 登录后，新开标签页 B 访问会被当作未登录重定向到登录页，需要在 B 里重新登录。
  → **接受**：这是 `sessionStorage` 的固有语义，属于本次改动为了达成"浏览器关闭后不能访问"这个明确诉求而接受的副作用；如果未来需要"多标签页共享登录态但浏览器关闭后仍然清空"，需要引入 `BroadcastChannel`/`storage` 事件同步等更复杂的机制，超出本次修复范围，留给后续按需处理。
- **[风险] 部分浏览器的"恢复上次会话"功能（如 Chrome/Firefox 的"重新打开关闭的标签页/窗口"）会连带恢复 `sessionStorage`**：这种情况下登录态可能仍然被找回，不是本次改动能完全防住的场景。
  → **缓解**：Decision 1 的 refresh-key 4 小时有效期收紧是针对这类边缘场景的纵深防御——即便 `sessionStorage` 被浏览器的"恢复会话"功能意外找回，refresh-key 本身最多 4 小时后也会自然失效，不会无限期有效。
