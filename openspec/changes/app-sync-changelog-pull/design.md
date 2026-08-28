## Context

- 现有推/拉管线：业务 Service 写库成功后调用 `DomainEventPublisher.publish(DomainChangeEvent)`；唯一实现 `DisruptorDomainEventPublisher` 在事务 `afterCommit` 后入队，保证消费者不会读取未提交业务数据。该机制不是事务性 Outbox：业务提交后、入队或流水持久化前发生进程崩溃仍可能丢事件。本阶段接受该边界，以 digest/全量对账兜底，并保持发布接口与消费者不依赖 Disruptor API，后续可替换为 RabbitMQ/RocketMQ 消费者。
- 唯一 Disruptor 消费者 `DomainChangeEventHandler` 单线程调用 `DomainChangeEventProcessor.process(event)`。消费者先持久化全局变更流水，再创建候选应用的持久化通知任务；HTTP 发送由独立通知调度器处理，避免慢回调阻塞全局流水。单消费者只保证“成功入队事件的消费顺序”，不声明它等于数据库事务提交顺序；`change_seq` 表示流水持久化顺序。
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
    event_id               BIGINT NOT NULL COMMENT '雪花算法生成的全局事件标识，不参与游标排序',
    entity_type             VARCHAR(16) NOT NULL COMMENT 'ORG/USER/POSITION/APP/ROLE',
    entity_id               BIGINT NOT NULL,
    operation_type          VARCHAR(16) NOT NULL COMMENT 'CREATE/UPDATE/ENABLE/DISABLE/DELETE',
    entity_version           BIGINT NOT NULL,
    org_scope_path_before    VARCHAR(255) NULL COMMENT '仅 ORG/POSITION 有值',
    org_scope_path_after     VARCHAR(255) NULL COMMENT '仅 ORG/POSITION 有值',
    change_time             DATETIME NOT NULL,
    create_by               VARCHAR(64) NOT NULL,
    create_time             DATETIME NOT NULL,
    update_by               VARCHAR(64) NOT NULL,
    update_time             DATETIME NOT NULL,
    PRIMARY KEY (change_seq),
    UNIQUE KEY uk_event_id (event_id),
    KEY idx_entity_type_seq (entity_type, change_seq),
    KEY idx_entity_type_id (entity_type, entity_id),
    KEY idx_change_time (change_time)
);
```
字段命名已避开 MySQL/PostgreSQL/Oracle/SQL Server 保留字（`change_seq`/`event_id`/`entity_type`/`entity_id`/`operation_type`/`entity_version`/`org_scope_path_before`/`org_scope_path_after`/`change_time` 均非保留字）。`org_scope_path_before`/`org_scope_path_after` 对 `USER`/`APP`/`ROLE` 三类恒为空，不额外拆表。

`change_seq` 与 `event_id` 职责严格分离：`change_seq` 使用数据库自增主键，保证持久化顺序下严格递增，允许事务回滚等原因产生空洞；`event_id` 在领域事件创建时由雪花算法生成，保证全局唯一，用于通知幂等、日志关联和未来 MQ 消息键。雪花 ID 只承诺唯一性和大致时间有序，系统不得依赖它严格递增。当前单实例配置唯一 `workerId`；后续多实例必须为各实例分配不重复的 `workerId`，并对时钟回拨采取等待或拒绝生成策略，禁止静默生成重复 ID。

### 2. ORG 上级组织变更的级联流水记录：复用 `org-path-fields` 已经算出的受影响子孙集合
`OrgServiceImpl.update` 在 `parentId` 变化时，于同一业务事务内先读取自身与全部子孙的旧路径和版本，再级联更新每个受影响组织的 `org_path`、`version = version + 1`、`update_time` 与审计更新人；随后读取新路径和新版本，为每个 id 发布独立 UPDATE 事件。子孙事件必须携带递增后的版本，避免客户端因版本未变化而忽略路径迁移。

**备选方案**：只为被直接操作的组织本身发一条事件，子孙组织的"路径变了但没人直接操作它"不发事件。未采用：这正是"离开范围"问题的根源——如果某个子孙组织因为祖先迁移而离开了某应用的配置范围，不给它单独发一条流水记录，该应用就永远不会知道要清理这条子孙组织的本地缓存。

### 3. ORG/POSITION 路径快照与删除 tombstone
任职记录的"所属组织"变更（`PositionServiceImpl.update`、或用户更新接口触发的任职记录整体同步中"更新既有记录"分支改了 `orgId`）不存在级联问题（任职记录不是树形结构），直接在写入前读一次旧 `orgId` 对应组织的当前 `orgPath` 作为 `orgScopePathBefore`，写入后用新 `orgId` 对应组织的 `orgPath` 作为 `orgScopePathAfter`，随事件一并发布。

路径字段统一采用以下语义，避免删除后因业务行不存在而无法做范围过滤：CREATE 为 `before=NULL, after=新路径`；普通 UPDATE/ENABLE/DISABLE 为前后均填写；跨组织移动填写不同的前后路径；DELETE 为 `before=删除前路径, after=NULL`。组织级联删除与用户接口物理删除任职时，也必须在删除前采集路径并为每个被删实体产生 tombstone 指针。

**实现落地澄清**：`OrgServiceImpl.delete` 现状是"存在未删除的下级组织即拒绝删除"的前置校验（`该组织下存在未删除的下级组织，无法删除`），结构上不存在"组织删除级联删除子孙"的场景——只有叶子组织（无未删除子组织）能被删除。因此组织 DELETE 只需为被删除的组织自身发布一条 tombstone（`before=删除前 orgPath, after=NULL`），不需要遍历子孙产生级联 tombstone；本段开头"组织级联删除...产生 tombstone 指针"描述的是一种一般性设计原则（如果未来允许级联删除组织，必须遵守），不代表当前代码路径存在该场景。用户更新接口触发的任职记录物理删除（`UserServiceImpl.syncPositions` 里"未出现在本次请求列表中的既有记录"分支）在 `userPositionMapper.deleteByIds` 之前，为每条待删除记录采集所属组织当前 `orgPath` 与"旧版本 + 1"的最终 tombstone 版本，随后发布 `DELETE` 事件（`before=采集到的路径, after=NULL`）。

### 4. `AppSyncOrgScopeResolver` 新增一个"原始范围前缀"解析方法，供 `/changes` 查询构建 SQL 条件
```java
public List<ScopePrefix> resolveScopePrefixes(Long appRefId, String syncDomain);
// ScopePrefix { String orgPath; boolean includeChildren; }
```
直接查 `tab_app_sync_org_scope` 原始行、逐行解析每个 `orgId` 当前的 `orgPath`（零行时返回空列表，调用方据此判断"不限制"）。`/changes` 在 MyBatis XML 中使用边界安全的条件：`path = #{prefix} OR path LIKE CONCAT(#{prefix}, '/%')`，before/after 两列分别应用；`includeChildren=false` 时只用等值匹配。`orgPath` 仅允许数字与 `/`，禁止 `%`、`_` 等 LIKE 通配字符，防止相邻编码前缀越权命中。这是一个新方法而不是复用 `resolveAllowedOrgIds`：后者返回展开后的 id 全集，适用于 `/pull` 的 `WHERE org_id IN (...)`。

**USER 数据域不适用**：一个用户可能同时持有多条落在不同组织的任职。`/changes` 按底层流水批量扫描，并批量查询候选用户任职后过滤，避免逐用户 N+1。响应的 `nextSeq` 表示“本轮已扫描到的最后一条底层流水”，即使全部候选均被过滤也必须前进；服务端循环扫描直至攒满 `pageSize` 条可见结果或底层流水耗尽，`hasMore` 依据底层尚未扫描记录计算。

**实现落地澄清**（tasks.md 4.1-4.4 完成范围）：`AppSyncOrgScopeResolver` 新增的批量方法命名为
`filterUsersWithinScope(Long appRefId, Set<Long> candidateUserIds)`，语义与单用户版本
`isUserWithinScope` 一致（任一未删除任职落在允许范围内即命中），一次 `IN` 查询完成。`/changes`
的循环扫描每轮请求的批大小固定为“当前还差多少条凑够 `pageSize`”（`remaining`），而不是一个
独立的、更大的扫描批常量：这样 ORG/POSITION（SQL 层已过滤，`filtered == batch`）天然一次查询
即可凑满一页；USER（应用层过滤）在耗尽 `remaining` 之前绝不会让 `visible` 超过 `pageSize`，
避免"一批过滤后剩余结果超发、又要回退 `nextSeq`"的复杂性。`sinceSeq`/`entityId`/`eventId`/
`entityVersion`/`changeSeq` 均通过 `Long.parseLong`/`String.valueOf` 手工转换（未引入
Jackson 自定义序列化器），格式非法或为负数时抛 `BusinessException`（400）。游标过期使用业务码
`410`（类比 HTTP 410 Gone 语义，实际 HTTP 状态码仍是 200，遵循项目"业务错误一律 200 +
`Result.code`"既有约定）。`tab_app_sync_cursor`/`tab_app_sync_metadata` 落地为独立的
`sync/cursor`、`sync/changelog`（`AppSyncMetadataEntity`）包，`AppSyncCursorService.advance`
内部调用的原子 `upsertLastDeliveredSeq` 用 `INSERT ... ON DUPLICATE KEY UPDATE
last_delivered_seq = GREATEST(...)` 实现，try/catch 包裹只记 WARN、不影响 `/changes` 响应。

### 5. `version` 列：五类同步实体统一维护
MyBatis-Plus 的 `@Version` 用于写冲突检测，本次 `version` 是外部消费者判断同一实体新旧的结果版本。ORG/USER/POSITION/APP/ROLE 创建时固定写 1，更新时使用原子 SQL `version = version + 1`，再取得变更后的值写入事件，避免 Java 先读后写造成并发丢增量；不引入 MyBatis-Plus 乐观锁插件。删除事件在删除前以原子更新或加锁读取方式取得“旧版本 + 1”作为最终 tombstone 版本。

### 6. 通知任务先落库，再按显式状态机发送
`DomainChangeEventProcessor` 使用一个本地数据库事务完成“一条变更流水 + 全部候选应用通知任务”的写入；候选解析或任一任务插入失败时整体回滚，不允许留下部分通知任务。策略重执行与 HTTP 发送在该事务提交后独立执行。通知任务保存稳定 `event_id`、`change_seq`、`entity_version`、回调地址和请求体快照，使用 `(app_ref_id, event_id)` 唯一键；同一变更的不同应用共享 `event_id`，重试不得重新生成。状态使用 `PENDING/PROCESSING/RETRY/SUCCESS/DEAD`，通过原子状态条件和 `lease_until` 抢占；网络异常、HTTP 408/429/5xx 退避重试，其他 4xx 默认进入 DEAD。

**实现阶段性现状**（app-sync-changelog-pull tasks.md 3.2 完成范围）：本 change 目前只落地了"写变更流水"这一半——`AppDataChangeLogServiceImpl.append` 独立声明 `@Transactional`，`DomainChangeEventProcessor.process` 先调用它写入一条流水，失败时记录 ERROR 日志并直接跳过通知分支（不发送没有 `changeSeq` 的通知）；候选应用解析与 `AppNotifyService.notifyIfConfigured` 仍是 app-sync-drop-changelog 遗留的旧实现（直接发 HTTP、事后各自记一条 SUCCESS/FAILURE 记录，未落地本节描述的 PENDING 状态机），因此暂时无法把"通知任务落库"并入同一个事务。"一条变更流水 + 全部候选应用 PENDING 通知任务同事务落库、候选解析或任一任务插入失败整体回滚"这一完整目标，留给本节其余部分（状态机、原子抢占、调度器）落地时一并完成。

**PENDING 任务的即时发送与调度器的关系**：事务提交后异步提交一次发送仅作为低延迟优化；调度器必须扫描到期 `PENDING`、到期 `RETRY` 和租约超时 `PROCESSING`，保证进程在“任务提交后、线程池提交前”崩溃时仍可恢复首次发送。

**具体数值**（新增配置节 `rbac.sync.notify-retry`，与 `SyncProperties`/`LogCleanupProperties` 同样风格，不硬编码在代码里）：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `initial-interval-seconds` | 30 | 第一次失败后的重试等待 |
| `multiplier` | 2.0 | 指数退避倍数 |
| `max-interval-seconds` | 3600 | 单次退避等待上限（避免最后几次重试间隔无限拉长） |
| `max-attempts` | 8 | 达到后转 `DEAD`，约合最长 30s+60s+...+3600s 累计几小时的重试窗口 |
| `lease-seconds` | 60 | `PROCESSING` 租约时长，需明显大于 `NOTIFY_RESPONSE_TIMEOUT_MILLIS`（现状 3s）以避免正常响应还没返回就被判定超时重复抢占 |
| `scheduler-poll-interval-seconds` | 10 | 独立调度器扫描到期 `RETRY`/超时 `PROCESSING` 的轮询间隔 |
| `scheduler-batch-size` | 100 | 单轮最多抢占的任务数，避免一次占满发送线程池 |

发送线程池独立于 Disruptor 消费者线程与 `log-cleanup` 的调度线程，使用固定大小线程池（初期与 `scheduler-batch-size` 同量级即可，不追加新的可配置项，后续如证明是瓶颈再单独调整）。

### 7. 限流：进程内令牌桶，不引入新依赖
新增轻量级 `RateLimiter` 组件（`ConcurrentHashMap<Long, TokenBucket>`，按 `appRefId` 维度隔离），作用于 `pull`/`changes`/`digest`。超限时 **HTTP 状态码仍为 200**，响应体为 `Result.error(RATE_LIMITED, ...)`，`Retry-After`（建议重试等待秒数）写入响应头。接口同时配置 `pageSize`、`ids` 数量和组织范围根数量硬上限，防止单请求放大。暂不引入新依赖。

**与 HTTP 状态码的关系**：`GlobalExceptionHandler` 现有约定是"业务性质的错误一律 HTTP 200 + `Result.code`"，只有 `NoResourceFoundException`（请求根本没有命中任何接口）才返回真实的 HTTP 404，注释里明确写了这是本类"唯一一个改变 HTTP 状态码的方法"。限流属于"接口命中了、但这次调用因为超限被拒绝"，与该约定的适用场景一致，因此不新增 HTTP 429 这个特例，直接复用 `BusinessException`/`Result.error` 现有机制；`Retry-After` 头通过在 Controller 或一个专门的 `HandlerInterceptor`/`ResponseBodyAdvice` 里直接操作 `HttpServletResponse` 设置，与 HTTP 状态码无关，不需要为此打破约定。

**具体数值**（新增配置节 `rbac.sync.rate-limit`）：三个接口按 `(appRefId, 接口)` 各自独立配额，不共享同一个桶——`digest` 单次调用开销明显高于 `pull`/`changes`（要流式扫描并计算摘要），如果三者共享一个桶，一次 `digest` 调用可能把 `pull`/`changes` 的配额顺带打满，因此分开配置：

| 配置项 | `pull`/`changes` 默认值 | `digest` 默认值 | 说明 |
| --- | --- | --- | --- |
| `tokens-per-second` | 10 | 1 | 令牌桶恒定填充速率 |
| `burst-capacity` | 30 | 3 | 桶容量，允许短时突发 |

硬上限（同一配置节，不区分接口）：`pageSize` 上限 500（`AppSyncDomainConfigEntity.pageSize` 目前无上限校验，一并补上）、`ids` 参数数量上限 200、组织范围根数量（`resolveScopePrefixes` 展开前的原始行数）上限 100，超出直接按参数校验失败处理（`BusinessException`，不占用令牌桶配额）。

### 8. 保留窗口清理任务：复用 `log-cleanup` 的配置模式
新增配置节 `rbac.sync.change-log-cleanup`（`cron`/`retention-days`/`batch-size`），并新增 `tab_app_sync_metadata(metadata_key VARCHAR(64) PRIMARY KEY, metadata_value VARCHAR(255), update_time DATETIME)`。键 `CHANGE_LOG_RETENTION_FLOOR_SEQ` 的值为十进制字符串：初始部署为 0；每批在同一事务中先删除确定范围内的过期流水，再把 floor 更新为“本批已删除最大 change_seq”；删除或 floor 更新任一步失败整体回滚。表为空时仍以该值判断过期，floor 不得提前推进。

### 9. `tab_app_sync_cursor`：尽力写入，不做事务强一致
```sql
CREATE TABLE tab_app_sync_cursor (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    app_ref_id BIGINT NOT NULL,
    entity_type VARCHAR(16) NOT NULL,
    last_delivered_seq BIGINT NOT NULL,
    update_time DATETIME NOT NULL,
    UNIQUE KEY uk_app_entity (app_ref_id, entity_type)
);
```
每次 `/changes` 成功返回后，以本次响应的 `nextSeq` 更新 `last_delivered_seq = GREATEST(last_delivered_seq, nextSeq)`。该值只表示“服务端已返回到哪里”，不代表调用方成功消费；字段和管理文案不得称为消费确认。若未来需要真实消费进度，应新增客户端 ACK 协议。

### 10. 全量、digest 与配置变更协议
标准衔接顺序固定为：先读取 digest 水位 `W`，再执行全量 pull，最后从 `sinceSeq=W` 拉增量；全量期间新增或变更的数据可能重复出现，客户端必须按 `bizId + version` 幂等。digest 固定使用 SHA-256，按 `bizId` 升序，对字段映射后的固定键与业务字段做 UTF-8 canonical JSON 编码后流式计算；键按字典序、null 显式保留，记录间使用长度前缀分隔。响应返回算法与摘要版本。

应用同步总开关、数据域开关、组织范围或字段映射发生变化时递增应用级 `config_epoch`。该 epoch 存在 `tab_app_config`，不区分数据域；调用方发现变化后必须对该应用全部已启用数据域重新全量同步并取得新水位。

**实现落地澄清**（tasks.md 4.6/4.7/5.1 完成范围）：`config_epoch` 递增写路径（本节前半段
描述的四处 `UPDATE`）留给后续阶段，本 change 只在 `/changes`、`/digest` 响应里只读透传
`tab_app_config.config_epoch` 当前值（十进制字符串），不做递增。`/pull` 响应记录新增的
`version` 固定键与 `/changes`/`/digest` 复用同一个 `cn.nihility.rbac.sync.transform.
SyncRecordAssembler` 组件组装（从原 `SyncPullServiceImpl.toRecord` 私有方法抽取为独立
Spring bean），保证"字段映射后的完整输出记录"在三个接口间是同一份实现，不会出现 `/pull` 与
`/digest` 对同一条记录算出不同摘要输入的分歧。摘要 canonical JSON 编码用独立于
`common.util.JacksonUtils` 的专用 `ObjectMapper`（`cn.nihility.rbac.sync.openapi.support.
SyncDigestCanonicalCodec`）：`SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS` 保证 `Map`
按 key 字典序输出且不受插入顺序影响，默认 `Include.ALWAYS` 保留显式 `null` 字段（不能复用
`JacksonUtils` 内部 `Include.NON_NULL` 的 `ObjectMapper`）；记录间分隔用 4 字节大端序长度
前缀。摘要按数据域游标式批量查询（`OrgMapper`/`UserMapper`/`UserPositionMapper`/`AppMapper`/
`RoleMapper`/`DictItemMapper` 各自新增 `selectDigestBatch(lastId, batchSize[, allowedOrgIds])`，
按 `id ASC` + `id > lastId` 翻页，不是 `OFFSET` 分页），每批 200 条。`currentMaxSeq` 在
`tab_app_data_change_log` 为空表时返回 `"0"`，与 `/changes` 的 `sinceSeq` 默认值语义一致，
可直接衔接。

### 11. 对外 BIGINT 序列化
数据库与 Java 内部继续使用 `BIGINT/Long`；对外 JSON 中 `eventId`、`changeSeq`、`nextSeq`、`currentMaxSeq`、`entityId`、`version/entityVersion` 均序列化为十进制字符串，Springdoc 声明为 `type=string`。请求中的 `sinceSeq` 与 ids 同样按十进制字符串解析并校验，签名规范使用原始字符串，避免 JavaScript 超过 `2^53-1` 后精度丢失。

**`config_epoch` 落地位置与并发写入**：新增列 `tab_app_config.config_epoch BIGINT NOT NULL DEFAULT 0`——`tab_app_config` 与 `tab_app` 一对一，是这四类配置共同的 `appRefId` 锚点（`AppNotifyServiceImpl` 已经按 `appRefId` 查询这张表），不新开一张表。触发递增的写路径共四处：

1. `AppConfigServiceImpl.updateSyncConfig`（总开关/`syncMode`/`notifyUrl`/`needSign`，写的就是 `tab_app_config` 自身）：与业务字段合并成同一条 `UPDATE tab_app_config SET ..., config_epoch = config_epoch + 1 WHERE app_ref_id = ?`，单语句原子完成，不需要额外事务边界。
2. `AppSyncConfigServiceImpl.updateDomainConfig`（写 `tab_app_sync_domain_config`）
3. `AppSyncConfigServiceImpl.replaceOrgScope`（写 `tab_app_sync_org_scope`，已有 `@Transactional`）
4. `AppSyncConfigServiceImpl.replaceFieldMappings`（写 `tab_app_sync_field_mapping`，已有 `@Transactional`）

2/3/4 三处目标表和 `tab_app_config` 不是同一张表，无法用单语句覆盖，改为在同一个事务内追加一次 `AppConfigMapper` 新增的原子自增方法（`UPDATE tab_app_config SET config_epoch = config_epoch + 1 WHERE app_ref_id = ?`，与 `version` 字段同样的"数据库原子自增、不先读后写"手法，天然避免并发丢增量）；`updateDomainConfig` 目前未加 `@Transactional`，需要一并补上，确保"写领域配置"和"epoch 自增"在同一个事务里要么都成功要么都回滚——如果只成功了前者、epoch 没递增，客户端会继续按旧范围拉取，出现"配置已经变了但客户端不知道要重新全量"的静默错误，比"epoch 空转多加了 1"的后果严重得多，所以宁可选择"先写业务表、同事务内立刻自增 epoch"而不是异步补偿。

## Risks / Trade-offs

- [组织子树迁移时级联发布事件数量与子孙数量成正比] → 环形缓冲区满时发布线程背压，限制单次迁移规模并监控队列积压；这只能避免正常运行时覆盖事件，不能消除进程崩溃造成的内存事件丢失。
- [本阶段 Disruptor 在业务提交后才入队，异常崩溃存在事件丢失窗口] → 明确为阶段性取舍，通过 digest/全量重建兜底；后续 RabbitMQ/RocketMQ change 必须采用业务事务内 Outbox、投递确认与幂等消费，不能只把 RingBuffer API 替换成 MQ send。
- [`USER` 数据域的"离开范围"问题本次不解决，依赖对账摘要接口兜底] → 已在 proposal.md Non-Goals 说明，接受这个取舍。
- [`/changes` 的 USER 范围过滤需要额外查询任职] → 对每批候选用户一次性批量查询任职并循环扫描底层流水，避免逐用户 N+1；用扫描批次数和候选数量硬上限防止单请求耗时失控。
- [`event_id`、`change_seq`、`entity_version` 容易被混淆] → Springdoc 明确：`eventId` 是雪花全局事件标识，用于幂等追踪；`changeSeq` 是数据库自增游标，用于排序续传；`entityVersion` 是同一实体的新旧版本。三者不可互换。
- [限流用进程内令牌桶，多实例部署时形同虚设（每个实例各自维护一份配额）] → 与现有 Disruptor 环形缓冲区、in-memory 组织范围解析等既有设计一样，明确只覆盖单实例部署场景，proposal.md Non-Goals 已声明，后续如果切换到多实例部署需要一并解决，不是本次的责任范围。

## Migration Plan

1. `org-path-fields` change 先落地并验证通过（本 change 的前置依赖）。
2. 新增流水、同步元数据、服务端投递进度表；五类同步实体加 `version`；通知记录扩展为任务状态机。
3. `DomainChangeEvent` 扩展字段；五类实体 Service 维护版本；组织迁移级联更新子孙路径、版本和更新时间。
4. 新增流水与元数据服务；`DomainChangeEventProcessor` 在同一事务写流水和全部通知任务。
5. `AppSyncOrgScopeResolver` 新增 `resolveScopePrefixes`；新增 `/changes`、`/digest` 两个接口与对应 Service/Mapper XML。
6. 独立调度器扫描 PENDING/RETRY/超时 PROCESSING；新增手动重推、限流和事务性分批清理。
7. `tab_app_sync_cursor` 写入与（可选）管理端展示。
8. 端到端验证：组织迁移触发级联流水记录、增量拉取游标续传、离开范围场景、通知失败重试到死信、对账摘要水位号衔接全量。
