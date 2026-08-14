## 1. 后端修复

- [x] 1.1 `UpstreamSyncExecutor` 新增常量 `SYSTEM_USER_ID = 0L`（保留哨兵用户 id，代表"系统/后台同步"操作人，见 design.md Decision 1）
- [x] 1.2 `UpstreamSyncExecutor.syncSource` 方法体最外层用 try/finally 包裹：进入前记录 `CurrentUserContext.getUserId()` 原值并 `setUserId(SYSTEM_USER_ID)`；`finally` 里原值非空则恢复原值，为空则 `clear()`（design.md Decision 2）
- [x] 1.3 补充 Javadoc 说明这一处理的原因（后台调度线程无登录上下文、哨兵 id 选择理由、与 `saveSyncRecord` 固定 `SYSTEM_OPERATOR` 字面量的一致性）

## 2. 测试

- [x] 2.1 `UpstreamSyncExecutor` 新增/更新单测：模拟未预先设置 `CurrentUserContext`（后台调度场景）调用 `syncSource`，验证不再抛出 `IllegalStateException`，且方法返回后 `CurrentUserContext.getUserId()` 恢复为 `null`（不污染线程池）
- [x] 2.2 补充一个用例：调用前先 `CurrentUserContext.setUserId(某真实用户 id)`（模拟手动触发的 HTTP 线程），验证 `syncSource` 执行完成后该值被正确恢复为原来的真实用户 id（而不是被清空或残留哨兵值）
- [x] 2.3 `backend/` 目录执行 `./gradlew test --tests "cn.nihility.rbac.identity.upstream.*"` 全部通过（另跑了 `cn.nihility.rbac.auth.*`/`cn.nihility.rbac.org.*` 确认无回归）

## 3. 文档同步

- [x] 3.1 实现完成后核对 `proposal.md`/`design.md`/`tasks.md` 与实际改动一致，如实现时有调整需回写（实现与文档一致，无需回写调整）
