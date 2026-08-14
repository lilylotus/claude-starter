## Why

`identity-upstream-data-sync` 已归档的规范里，"定时调度触发"需求已经承诺"到期的数据源 SHALL 自动触发一次同步"，但当前实现里定时轮询（`UpstreamSyncScheduler.tick()`，`@Scheduled` 后台线程，不在任何 HTTP 请求上下文中）触发同步时，每一行组织/用户/任职数据的新增或更新都会失败：`OrgService`/`UserService`/`PositionService` 的 `create`/`update` 内部通过 `CurrentOperatorService.resolveUserId()` 读取 `CurrentUserContext`（一个只由 `IdentityAuthFilter` 在已认证 HTTP 请求线程上设置的 `ThreadLocal`）来填充 `createBy`/`updateBy`，后台调度线程上该值恒为 `null`，`resolveUserId()` 按其既有设计（不做静默兜底）直接抛出 `IllegalStateException`（"当前线程不处于已登录上下文中，无法解析操作人用户 id"）。这是一个纯粹的实现缺陷——已写好的定时调度功能实际上从未在真实数据库上成功创建/更新过一条记录，只是此前没有被验证过。该异常还会连带产生误导性的下游症状：如果某一行本应先创建出被后续行引用为"上级组织编码"（`parentCode`）的组织，因为这次创建本身失败回滚，后续行按该编码匹配上级组织时会报"无法匹配到已有组织记录"，看起来像是两个独立问题，实际是同一根因的连锁反应。手动点击"立即同步一次"不受影响，因为它运行在触发该请求的已认证 HTTP 线程上，`CurrentUserContext` 已经被设置。

## What Changes

- `UpstreamSyncExecutor.syncSource` 在处理任何数据域之前，把 `CurrentUserContext` 设置为一个固定的保留哨兵用户 id（表示"系统/后台同步"这一操作人），处理完成后（无论成功/异常）恢复为进入前的原值（HTTP 线程上恢复为真实登录用户，避免影响调用方后续逻辑；后台调度线程上清空，避免线程池复用时把该标记残留给下一次不相关的调度）。定时触发与手动触发统一套用同一个哨兵值，不再区分——这与 `UpstreamSyncExecutor` 自身写入 `tab_upstream_sync_record` 时已经固定使用字面量 `"SYSTEM"` 作为审计字段、不区分触发方式的既有设计保持一致。
- 不修改 `CurrentOperatorServiceImpl`"脱离登录上下文即抛异常"的既有行为——那是给普通业务调用方的编程错误保护，本次改动的范围是"让上游同步在调用这些服务前，正确地在其专属的后台线程上准备好一个有效的操作人上下文"，而不是放宽保护本身。

## Capabilities

（无——这是恢复"定时调度触发"需求既有承诺行为的纯 bug 修复，不引入新的可观测行为契约，也不改变任何已发布需求的措辞；`.openspec.yaml` 已设置 `skip_specs: true`。）

## Impact

- 后端：`UpstreamSyncExecutor` 新增一个保留哨兵用户 id 常量与 try/finally 包裹逻辑；不改动 `UpstreamRowUpserter`、`OrgService`/`UserService`/`PositionService`、`CurrentOperatorService`/`CurrentUserContext` 本身。
- 数据库：不新增迁移——哨兵 id 只是一个从未被真实 `IdType.AUTO` 自增主键使用过的固定数字（如 `0`），不需要在 `tab_admin`/`tab_user` 里插入一条真实记录；`tab_org`/`tab_user`/`tab_user_position` 的 `create_by`/`update_by` 是无外键约束的 `VARCHAR`，写入该哨兵 id 的字符串形式不会破坏引用完整性。
- 副作用（预期且可接受）：手动"立即同步一次"触发的新增/更新记录，其 `createBy`/`updateBy` 会从"实际点击按钮的管理员 id"变为固定哨兵值——这是有意的一致性选择（见 What Changes），而不是遗漏。
- 测试：`UpstreamSyncExecutor` 需要补充/更新单测，验证在没有预先设置 `CurrentUserContext` 的情况下（模拟后台调度线程）调用 `syncSource` 不再抛出登录上下文异常，且处理完成后 `CurrentUserContext` 被正确清空（不污染线程池后续复用）。
