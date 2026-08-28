## Context

- 现有推/拉管线：业务 Service（`OrgServiceImpl`/`UserServiceImpl`/`PositionServiceImpl`/`AppServiceImpl`/角色相关 Service）写库成功后调用 `DomainEventPublisher.publish(DomainChangeEvent)`；唯一实现 `DisruptorDomainEventPublisher` 若检测到当前有活动事务，把入队动作注册为 `TransactionSynchronization#afterCommit`，否则立即入队——这保证了消费者线程处理事件时，触发该事件的业务事务必然已经提交（`app-sync-notify-pull-api` change 曾经复现过一次时序 bug 并修复），本次新增的变更流水表写入天然继承这个正确性保证，不需要额外处理。
- 唯一 Disruptor 消费者 `DomainChangeEventHandler` 单线程调用 `DomainChangeEventProcessor.process(event)`，内部先判定通知候选应用并逐个触发 HTTP 通知，再触发策略重新执行（与本次改动无关的另一个副作用）。单线程消费保证了事件处理顺序与业务事务提交顺序一致，变更流水表用自增主键当 `change_seq`，天然单调递增、不重复，不需要额外的序列号生成器。
- 现有拉取接口 `GET /open/api/sync/pull`（`SyncPullServiceImpl`）直接查业务表当前状态，按 `updateTime ASC, id ASC` 稳定分页，`ORG`/`USER`/`POSITION` 三个数据域的组织范围过滤经 `AppSyncOrgScopeResolver.resolveAllowedOrgIds(appRefId, syncDomain)` 解析为一个"允许组织 id 全集"（`Optional<Set<Long>>`，空 `Optional` 表示不限制），下推到业务表查询的 `WHERE org_id IN (...)` 条件里；`USER` 数据域额外有 `isUserWithinScope`（任一未删除任职落在范围内即命中）。这套解析在 `org-path-fields` change 落地后会改为按 `org_path` 前缀查询实现，但对外方法签名/语义不变，本 change 直接复用其现有产出（一个 id 全集），不需要关心它内部是 BFS 还是前缀查询。
- `tab_app_sync_org_scope` 按 `(appRefId, syncDomain)` 存储"根组织 id + 是否含子孙"的配置行，零行表示不限制。这是本次变更流水表查询需要"应用配置的组织范围前缀集合"这一新形态数据的原始来源——现有 `AppSyncOrgScopeResolver` 只暴露"展开后的 id 全集"，不暴露"原始根组织的路径前缀列表"，本次需要新增一个方法。
- `app-sync-drop-changelog` 的教训：`tab_app_data_change_log` 曾经按候选应用物化多份物理记录，导致行数与订阅应用数成正比、且应用配置变化后物化记录与真实状态脱节。本次的核心设计约束就是**绝不重蹈这一步**：变更流水表全局一份，应用/组织范围过滤全部下推到查询时的 `WHERE` 条件。

## Goals / Non-Goals

**Goals:**
- 变更流水表全局单表，行数只随变更次数增长。
- 增量拉取提供严格递增、断点续传的游标语义，替代/补充 `updateTime` 游标的模糊边界行为。
- 组织/任职"离开应用配置范围"时，应用能通过增量拉取感知到（不引入伪删除操作码，靠"指针存在但详情查不到"这一约定语义）。
- 通知发送具备退避重试与死信兜底，不再是单次尝试即终态。
- 提供对账摘要接口，给客户端一个"发现漂移→触发针对性补偿"的正式入口，并顺带解决"全量拉取后怎么无缝切到增量"的水位号衔接问题。

**Non-Goals:**（详见 proposal.md Non-Goals，此处不重复）

## Decisions

### 1. `tab_app_data_change_log` 表结构
```sql
CREATE TABLE tab_app_data_change_log (
    change_seq             BIGINT NOT NULL AUTO_INCREMENT COMMENT '全局单调递增序列号，即主键',
    entity_type             VARCHAR(16) NOT NULL COMMENT 'ORG/USER/POSITION/APP/ROLE',
    entity_id               BIGINT NOT NULL,
    operation_type          VARCHAR(16) NOT NULL COMMENT 'CREATE/UPDATE/ENABLE/DISABLE/DELETE',
    entity_version           BIGINT NOT NULL,
    org_scope_path_before    VARCHAR(255) NULL COMMENT '仅 ORG/POSITION 有值',
    org_scope_path_after     VARCHAR(255) NULL COMMENT '仅 ORG/POSITION 有值',
    change_time             DATETIME NOT NULL,
    create_by/create_time/update_by/update_time  -- 仓库惯例四个审计字段，change_log 本身不会被更新，仍保留以统一风格
    PRIMARY KEY (change_seq),
    KEY idx_entity_type_seq (entity_type, change_seq),
    KEY idx_entity_type_id (entity_type, entity_id)
);
```
字段命名已避开 MySQL/PostgreSQL/Oracle/SQL Server 保留字（`change_seq`/`entity_type`/`entity_id`/`operation_type`/`entity_version`/`org_scope_path_before`/`org_scope_path_after`/`change_time` 均非保留字）。`org_scope_path_before`/`org_scope_path_after` 对 `USER`/`APP`/`ROLE` 三类恒为空，不额外拆表（空字符串代价可接受，符合"变更流水表不按数据域拆分"的整体设计取向）。

**备选方案**：`change_seq` 用独立的序列号生成器（如 Redis `INCR` 或专门的序号表）而不是自增主键。未采用：自增主键本身就是全局单调递增且与写入顺序一致（单 Disruptor 消费者线程串行写入），额外引入序列号生成器只会增加一次网络往返和一致性风险，没有收益。

### 2. ORG 上级组织变更的级联流水记录：复用 `org-path-fields` 已经算出的受影响子孙集合
`OrgServiceImpl.update` 在 `parentId` 变化时，已经需要按 `org-path-fields` change 的实现调用一次"级联更新子孙 `org_path`"的 SQL（`cascadeUpdateOrgPath`）。本 change 在同一个事务内，**先于**级联 UPDATE 执行一次 `SELECT id, org_path FROM tab_org WHERE org_path = :oldPath OR org_path LIKE CONCAT(:oldPath, '/%')` 拿到"自身 + 全部子孙"的变更前 `org_path` 快照，级联 UPDATE 执行后再按同样的 id 集合查一次变更后的 `org_path`，两次快照按 id 一一配对，为每个 id 各自发布一条携带 `orgScopePathBefore`/`orgScopePathAfter` 的 `DomainChangeEvent`（`operationType=UPDATE`）。子孙组织数量正常情况下是几到几十个，两次查询 + 循环发布事件的开销可以接受；如果子孙规模异常大（人为构造的超大子树整体迁移），这是一次管理员主动触发的低频操作，不是需要优化的热路径。

**备选方案**：只为被直接操作的组织本身发一条事件，子孙组织的"路径变了但没人直接操作它"不发事件。未采用：这正是"离开范围"问题的根源——如果某个子孙组织因为祖先迁移而离开了某应用的配置范围，不给它单独发一条流水记录，该应用就永远不会知道要清理这条子孙组织的本地缓存。

### 3. POSITION 所属组织变更：同一事务内直接产出 before/after
任职记录的"所属组织"变更（`PositionServiceImpl.update`、或用户更新接口触发的任职记录整体同步中"更新既有记录"分支改了 `orgId`）不存在级联问题（任职记录不是树形结构），直接在写入前读一次旧 `orgId` 对应组织的当前 `orgPath` 作为 `orgScopePathBefore`，写入后用新 `orgId` 对应组织的 `orgPath` 作为 `orgScopePathAfter`，随事件一并发布。

### 4. `AppSyncOrgScopeResolver` 新增一个"原始范围前缀"解析方法，供 `/changes` 查询构建 SQL 条件
```java
public List<ScopePrefix> resolveScopePrefixes(Long appRefId, String syncDomain);
// ScopePrefix { String orgPath; boolean includeChildren; }
```
直接查 `tab_app_sync_org_scope` 原始行、逐行解析每个 `orgId` 当前的 `orgPath`（零行时返回空列表，调用方据此判断"不限制"）。`/changes` 接口按这个列表在 MyBatis XML 里用 `<foreach>` 拼出 `(org_scope_path_before LIKE CONCAT(#{prefix}, '%') OR org_scope_path_before = #{prefix} OR org_scope_path_after LIKE CONCAT(#{prefix}, '%') OR org_scope_path_after = #{prefix})` 的 `OR` 组合（`includeChildren=false` 时只用等值匹配，不拼 `LIKE`）。这是一个新方法而不是复用 `resolveAllowedOrgIds`：后者返回的是"展开后的 id 全集"，用于 `/pull` 现有的 `WHERE org_id IN (...)` 场景；`/changes` 需要的是"原始前缀列表"，用于 `LIKE` 前缀匹配，两种查询形态不同，没有共同的中间表示可以复用，保持两个方法各自服务各自的查询模式，不强行统一。

**USER 数据域不适用**：一个用户可能同时持有多条落在不同组织的任职，"变更前后单一路径"的模型表达不了，`/changes` 对 `entityType=USER` 的过滤沿用现有 `isUserWithinScope` 的运行时判定方式（对本页候选结果逐条二次校验，不下推到 SQL），因为增量拉取本身已经用 `pageSize` 限制了单次候选量级，逐条校验的开销可控。

### 5. `version` 列：应用层维护，不用 MyBatis-Plus 乐观锁注解
MyBatis-Plus 的 `@Version` 是为"更新时校验版本号、不匹配则更新 0 行"这种并发写冲突检测设计的，语义是"前置条件"。本次的 `version` 纯粹是"变更结果的递增标记"，供外部消费者事后判断新旧，不是本系统内部的并发控制手段（本系统内部的乐观锁诉求由审批流程的"申请状态从待审批 CAS 到已通过"等既有机制各自解决，两者不是一回事）。因此 `version` 字段就是一个普通整型列，写操作时 Java 侧读出当前值 `+1` 后随其余字段一起 `UPDATE`，创建时固定写 1，与仓库里 `showOrder`/`status` 等字段的维护方式一致，不引入 MyBatis-Plus 乐观锁插件。

### 6. 通知重试：状态复用 `NotifyStatus.FAILURE`，新增重试计数与下次重试时间列，不新增状态常量
`tab_app_notify_record` 新增 `retry_count`（默认 0）、`next_retry_time`（可空）两列。失败时：`notify_status=FAILURE`、`retry_count+1`、按指数退避（如 `min(2^retryCount 分钟, 上限)`）计算 `next_retry_time`。定时任务扫描 `WHERE notify_status=FAILURE AND retry_count < :maxRetry AND next_retry_time <= NOW()`，逐条重推。达到 `maxRetry` 后该记录自然不再被这条 `WHERE` 命中（`retry_count >= maxRetry`），等效于死信状态，不需要单独的"DEAD"常量——管理端"失败通知"列表按 `notify_status=FAILURE` 查询即可全部看到（含仍在重试与已死信两种，前端可选按 `retry_count` 是否达到上限再细分展示文案，不强制）。

**备选方案**：新增 `RETRYING`/`DEAD` 两个状态常量，状态机严格流转。未采用：现有查询/展示逻辑（`按应用 id 分页查询该应用的通知日志，支持按通知状态过滤`）已经是二态（成功/失败）设计，插入更细的状态机需要同步改动查询条件与前端展示，收益（更精确的状态语义）小于改动面；重试次数/下次重试时间两列已经能完整表达"还在重试中"还是"已经放弃"，不需要额外状态位。

### 7. 限流：进程内令牌桶，不引入新依赖
新增一个轻量级 `RateLimiter` 组件（`ConcurrentHashMap<Long, TokenBucket>`，按 `appRefId` 维度隔离），令牌桶容量与填充速率通过配置项暴露，作用于 `pull`/`changes`/`digest` 三个对外接口的入口处（`OpenApiSignInterceptor` 之后、Controller 方法执行之前，复用现有拦截器扩展点或新增一个同级拦截器）。不引入 Bucket4j/Resilience4j 等新依赖，手写实现足够覆盖"单实例、按应用限流"这个简单诉求。超限时返回明确的业务错误码（`RATE_LIMITED`），与游标失效错误码风格一致（均通过 `BusinessException` + `Result.code` 表达，不是 HTTP 429）。

### 8. 保留窗口清理任务：复用 `log-cleanup` 的配置模式
新增配置节 `rbac.sync.change-log-cleanup`（`cron`/`retention-days`，默认值与现有 `rbac.log-cleanup` 一致的每天凌晨执行、180 天可以按变更流水表的数据量级调低到 90 天），定时任务按 `change_time` 早于阈值的记录批量删除，与登录日志/操作日志清理任务的实现风格保持一致（同一批已有的 Quartz/Spring `@Scheduled` 机制，不引入新的调度框架）。

### 9. `tab_app_sync_cursor`：尽力写入，不做事务强一致
```sql
CREATE TABLE tab_app_sync_cursor (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    app_ref_id BIGINT NOT NULL,
    entity_type VARCHAR(16) NOT NULL,
    last_seq BIGINT NOT NULL,
    update_time DATETIME NOT NULL,
    UNIQUE KEY uk_app_entity (app_ref_id, entity_type)
);
```
每次 `/changes` 成功返回后，用 `INSERT ... ON DUPLICATE KEY UPDATE last_seq = GREATEST(last_seq, VALUES(last_seq)), update_time = NOW()` 更新（`GREATEST` 防止并发/乱序请求把游标往回写），写入失败只记警告日志，不影响 `/changes` 本身的响应——与现有 `tab_app_pull_record`/`tab_app_notify_record` 的"尽力而为、不阻断主流程"风格完全一致。仅供管理端展示，不参与任何查询过滤逻辑。

## Risks / Trade-offs

- [组织子树迁移时级联发布事件数量与子孙数量成正比，极端情况下（人为构造的超大扁平子树）可能让 Disruptor 环形缓冲区短时间内涌入大量事件] → 组织树规模在企业级场景通常有限（同 `org-path-fields` change 的风险评估），且这是低频管理操作不是热路径；环形缓冲区已有的 `BlockingWaitStrategy` 会自然背压，不会丢事件，只是处理会短暂排队。
- [`USER` 数据域的"离开范围"问题本次不解决，依赖对账摘要接口兜底] → 已在 proposal.md Non-Goals 说明，接受这个取舍。
- [`/changes` 接口对 `USER` 数据域的范围过滤是"查出候选后逐条 Java 侧校验"而不是 SQL 下推，极端情况下一页里大量记录被过滤掉导致有效数据稀疏] → 可以接受：`pageSize` 本身限制了单次候选量级上限，且用户变更频率通常低于组织架构调整，真正出现"一页全被过滤掉"的情况极其罕见；如果后续证明是真实瓶颈，可以在过滤后循环调用下一页直到攒够 `pageSize` 条有效记录或耗尽候选，属于纯优化，不改变接口契约。
- [`entity_version` 与 `change_seq` 是两套独立的递增计数，外部应用如果混淆两者语义（把 `entityVersion` 当游标用，或把 `changeSeq` 当乐观锁版本用）会产生错误预期] → 在对外接口文档（Springdoc `@Schema` 描述）里明确区分两者用途："changeSeq 用于游标续传，entityVersion 用于同一实体的重复/乱序判断，两者不可互换"。
- [限流用进程内令牌桶，多实例部署时形同虚设（每个实例各自维护一份配额）] → 与现有 Disruptor 环形缓冲区、in-memory 组织范围解析等既有设计一样，明确只覆盖单实例部署场景，proposal.md Non-Goals 已声明，后续如果切换到多实例部署需要一并解决，不是本次的责任范围。

## Migration Plan

1. `org-path-fields` change 先落地并验证通过（本 change 的前置依赖）。
2. 新增 Flyway 迁移脚本：`tab_app_data_change_log`、`tab_app_sync_cursor` 建表；`tab_org`/`tab_user`/`tab_user_position` 加 `version` 列（存量数据回填为 1）；`tab_app_notify_record` 加 `retry_count`/`next_retry_time` 两列。
3. `DomainChangeEvent` 扩展字段；`OrgServiceImpl`/`UserServiceImpl`/`PositionServiceImpl` 维护 `version` 与（仅 `OrgServiceImpl`/`PositionServiceImpl`）`orgScopePathBefore`/`orgScopePathAfter`。
4. `AppDataChangeLogService`/`Mapper`/`Entity` 新增；`DomainChangeEventProcessor` 接入写入变更流水表这一步。
5. `AppSyncOrgScopeResolver` 新增 `resolveScopePrefixes`；新增 `/changes`、`/digest` 两个接口与对应 Service/Mapper XML。
6. `AppNotifyServiceImpl` 接入重试计数/退避计算；新增定时重推任务、限流组件、变更流水清理任务。
7. `tab_app_sync_cursor` 写入与（可选）管理端展示。
8. 端到端验证：组织迁移触发级联流水记录、增量拉取游标续传、离开范围场景、通知失败重试到死信、对账摘要水位号衔接全量。
