## 1. 后端：refresh-key 有效期改为 4 小时

- [x] 1.1 `backend/src/main/java/cn/nihility/rbac/auth/config/RbacLoginProperties.java`：`refreshTokenExpireSeconds` 默认值由 `604800` 改为 `14400`，Javadoc 注释同步更新为"默认 14400 秒（4 小时）"。
- [x] 1.2 `backend/src/main/resources/application.yml`：`rbac.user.login.refresh-token-expire-seconds` 由 `604800` 改为 `14400`，行内注释同步更新（design.md Decision 1：两处必须同步改，`application.yml` 的显式值优先级高于字段默认值）。

## 2. 前端：登录会话从 localStorage 迁移到 sessionStorage

- [x] 2.1 `frontend/src/stores/auth.ts` 的 `loadSession()` 里 `localStorage.getItem(STORAGE_KEY)` 改为 `sessionStorage.getItem(STORAGE_KEY)`。
- [x] 2.2 `persist()` 里 `localStorage.setItem(STORAGE_KEY, ...)` 改为 `sessionStorage.setItem(STORAGE_KEY, ...)`。
- [x] 2.3 `logout()` 里 `localStorage.removeItem(STORAGE_KEY)` 改为 `sessionStorage.removeItem(STORAGE_KEY)`。
- [x] 2.4 确认 `accessKey`/`refreshKey` 两个字段在同一个 `AuthSession` 对象里一起完成迁移（design.md Decision 2：不能只迁移 accessKey，否则 refreshKey 留在 localStorage 会被静默刷新逻辑找回继续续期）；`frontend/src/stores/currentUserPermission.ts` 保持纯内存持有，不涉及改动。顺带把 `AuthSession` 接口上方"本地持久化的登录态快照"注释更新为准确描述 sessionStorage 的会话级生命周期。

## 3. 验证

- [x] 3.1 后端：`./gradlew test`（在 `backend/` 目录下）确认编译与既有测试通过：`BUILD SUCCESSFUL`，未发现直接断言 `604800`/7 天字样的测试（`TokenServiceImplTest` 是显式 `setRefreshTokenExpireSeconds(604800)` 自行打桩，与生产默认值无关，未改动）。
- [x] 3.2 前端：`npm run build`（在 `frontend/` 目录下）确认类型检查与构建通过：`✓ built in 1.33s`。
- [ ] 3.3 手动验证（若开发环境可行）：登录后在浏览器开发者工具里确认 `rbac_auth_session` 出现在 Session Storage 而不是 Local Storage；关闭浏览器窗口后重新打开访问业务页面，确认被重定向到登录页，此前的 access-key/refresh-key 均不能继续访问业务接口。
  - 未在真实浏览器里手动操作验证，仅通过代码走查确认 `loadSession`/`persist`/`logout` 三处调用点已全部改为 `sessionStorage`；建议使用方在部署前按本条描述手动跑一遍。

## 4. 文档同步

- [x] 4.1 实现完成后基于实际 diff/测试结果对齐 `proposal.md`/`design.md`/`tasks.md`：`proposal.md`/`design.md` 措辞已经足够准确且与实际实现一致，无需改动；`tasks.md` 本次已勾选完成项并标注 3.3 未做真实浏览器手动验证的说明。
