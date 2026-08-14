## Context

见 proposal.md - Why。相关代码：
- `CurrentUserContext`（`ThreadLocal<Long>`）只由 `IdentityAuthFilter` 在已认证 HTTP 请求线程上 `setUserId`。
- `CurrentOperatorServiceImpl.resolveUserId()` 读取 `CurrentUserContext.getUserId()`，为 `null` 时直接抛 `IllegalStateException`（有意设计：脱离登录上下文调用属于编程错误，不做静默兜底）。
- `OrgServiceImpl.create/update`、`UserServiceImpl.create/update`、`PositionServiceImpl.create/update` 都会调用 `resolveUserId()` 填充 `createBy`/`updateBy`；`OrgServiceImpl.create/update` 还会通过 `orgScopeService.resolveAllowedOrgIds(CurrentUserContext.getUserId())`/`isOrgIdAllowed` 做管辖组织范围校验。
- `OrgScopeServiceImpl.resolveAllowedOrgIds(userId)`：查询 `tab_admin_org_scope` 按 `userId` 过滤，查不到任何行时返回 `Optional.empty()`，调用方把"空"解释为"无限制"（`isOrgIdAllowed` 里 `allowedOrgIds.isEmpty() || ...`）。也就是说，一个从未在 `tab_admin_org_scope` 里出现过的 userId，天然不受任何组织范围限制。
- `UpstreamSyncExecutor.syncSource` 是定时轮询（`UpstreamSyncScheduler.tick`，后台线程）与手动触发（`UpstreamSourceServiceImpl.manualSync`，HTTP 请求线程）唯一共用的入口方法。

## Goals / Non-Goals

**Goals:**
- 定时轮询触发的同步能够真正创建/更新组织、用户、任职记录，不再撞上登录上下文异常。
- 修复不引入新的组织范围越权风险——同步操作本身就应该不受任何单个管理员的管辖范围限制（它是系统级别的批量数据落地，不是某个管理员的个人操作）。
- 修复范围保持最小，不改动 `CurrentOperatorService`/`CurrentUserContext` 的既有契约，不改动 `OrgService`/`UserService`/`PositionService` 的方法签名。

**Non-Goals:**
- 不引入新的数据库表/字段来表示"系统账号"——哨兵 id 是一个约定值，不是真实存在的用户记录。
- 不处理"如何在 `tab_org`/`tab_user`/`tab_user_position` 列表页把这个哨兵 id 展示成更友好的文案（如'系统同步'）"这类前端展示优化——`createBy`/`updateBy` 当前就是原样展示的裸字符串，本次不新增展示层的特殊映射逻辑，保持改动聚焦。
- 不改变手动触发路径原本"运行在已认证请求线程上"这一事实，只是让 `syncSource` 内部统一改写 `CurrentUserContext` 的值。

## Decisions

### Decision 1：引入固定保留哨兵用户 id `0L`，代表"系统/后台同步"操作人
选 `0`：`tab_admin`/`tab_user` 用 `IdType.AUTO` 自增主键，从 `1` 开始，`0` 永远不会是真实用户 id；与本仓库既有约定一致（如 `parentId=0` 表示顶级组织）。`0` 在 `tab_admin_org_scope` 里天然查不到任何管辖范围行，`OrgScopeServiceImpl.resolveAllowedOrgIds(0L)` 返回 `Optional.empty()` = 不受限，`create`/`update` 里的范围校验不会误伤同步流程。

- **备选方案 A**：复用种子数据里的"系统管理员"账号（`id=1`）。未采纳——那是一个真实存在、可被登录使用的管理员账号，把后台同步的所有写操作都归因于它，会让审计记录产生误导（"系统管理员在凌晨 3 点创建了 200 条组织记录"实际上是定时任务干的，不是这个人手动操作的）。
- **备选方案 B**：像 `UpstreamSyncExecutor.saveSyncRecord`/`AppNotifyServiceImpl` 那样，直接写入字面量字符串 `"SYSTEM"` 而不经过 `CurrentUserContext`。未采纳——那两处都是直接 `.builder()` 构造自己独有的实体（`UpstreamSyncRecordEntity`/`AppNotifyRecordEntity`），可以绕开 `CurrentOperatorService`；但 `UpstreamRowUpserter` 调用的是 `OrgService`/`UserService`/`PositionService` 这些被全应用共用的既有 create/update 方法，它们的 `createBy`/`updateBy` 赋值逻辑硬编码调用 `currentOperatorService.resolveUserId()`，无法从外部注入一个字符串，除非改这三个服务的方法签名——这会波及 UI 直接 CRUD、Excel 导入等所有既有调用方，风险和改动面远超"修复一个后台线程上下文 bug"应有的范围。

### Decision 2：在 `UpstreamSyncExecutor.syncSource` 顶层设置/恢复 `CurrentUserContext`，而不是在 `UpstreamRowUpserter` 内部
`syncSource` 是定时/手动两条触发路径唯一的公共入口，`UpstreamRowUpserter.upsertRow` 与 `syncSource` 运行在同一个线程上（`@Transactional(propagation = REQUIRES_NEW)` 只是开新事务，不切线程），在入口处设置一次即可覆盖该次同步涉及的全部数据域、全部行，不需要在每个数据域或每一行都重复设置。用 `try/finally` 包裹：进入前记录 `CurrentUserContext.getUserId()` 的原值，`finally` 里如果原值非空则恢复原值（HTTP 线程上还给真实登录用户，避免影响调用方后续逻辑，虽然目前 `manualSync` 返回后controller 也没有后续逻辑依赖它，但恢复原值比无条件清空更安全、更不依赖"后面没人用"这个假设），原值为空则 `clear()`（后台调度线程场景，避免线程池复用给下一次不相关的调度残留标记）。

### Decision 3：手动触发与定时触发统一套用同一哨兵值，不做区分
不判断 `triggerType` 来决定"手动时用真实登录用户、定时时用哨兵值"——理由见 proposal.md What Changes：与 `saveSyncRecord` 已经固定用 `"SYSTEM"` 不区分触发方式的既有设计保持一致，也避免"同一批数据因为触发方式不同，产生两种不同 createBy 语义"的不一致体感。

## Risks / Trade-offs

- [风险] 手动触发的同步，其新增/更新记录的 `createBy`/`updateBy` 不再是点击按钮的管理员本人，如果将来有审计需求要追溯"是谁手动点了同步"，光看组织/用户/任职记录的 `createBy` 字段看不出来 → 缓解：`tab_upstream_sync_record` 本身已经记录了 `triggerType`（`MANUAL`/`SCHEDULE`），需要追溯"这批数据是不是同步产生的、什么时候同步的"时查同步记录表即可；"具体是哪个管理员点的手动同步"目前系统里没有更细粒度的操作日志需求，本次不新增。
- [风险] 哨兵 id `0` 依赖"自增主键从 1 开始"这一假设，如果将来任何地方改了 id 生成策略或允许手工插入 id=0 的记录，会产生 id 冲突 → 缓解：`IdType.AUTO` 是 MyBatis-Plus/MySQL 的标准自增行为，`0` 作为保留值在本仓库已有先例（`parentId=0`），风险极低；即使真的冲突，`createBy`/`updateBy` 也只是无约束的展示性字段，不会导致数据损坏或功能性错误。
