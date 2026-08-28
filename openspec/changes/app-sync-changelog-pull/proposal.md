## Why

`app-sync-notify-pull` 现有机制（通知走单次 HTTP 回调无重试、增量拉取按 `updateTime` 游标）已经覆盖了基础的推/拉能力，但结合 `4A项目数据推拉同步架构设计.md` 与 `codex-springboot-4a-conversation-summary.md` 两份设计参考，可以看到几个会在实际接入场景下逐渐暴露的问题：（1）`updateTime` 游标存在"同一毫秒多条变更""恰好等于游标值的记录跨页重复"等已知边界行为，缺少严格递增、不重复、可断点续传的游标语义；（2）通知发送失败即终态，没有退避重试和死信兜底；（3）**"离开范围"没有信号**——组织被移动到另一个分支、任职记录的所属组织被改到范围外，会让该实体从某个应用的可见范围里"悄悄消失"，现有机制只按当前状态做正向过滤，不产生任何"应用需要把这条本地缓存删掉"的信号，应用没有办法感知，只能永远保留一份过期数据；（4）重复/乱序投递（网络重试、批量拉取分页重叠）没有实体级别的版本号兜底，纯粹依赖游标去重不够健壮。两份参考文档一致建议的核心方案是"全局单调递增序列号的变更流水表，作为推/拉两条通道共用的数据出口"，并补充了实体版本号、离开范围的 tombstone 语义、事务性发件箱等配套原则，本次据此为现有机制补齐这些能力。

**与 `app-sync-drop-changelog`（已归档）的关系**：本次变更流水全局只存一条物理记录，不按应用复制，应用与组织范围在查询时过滤。现有 `GET /open/api/sync/pull` 的地址、请求参数及当前状态查询语义保持兼容，继续承担全量拉取与详情复核；响应固定字段新增十进制字符串 `version`，内部查询和行转换相应扩展。

**阶段性可靠性边界**：本阶段继续使用现有 `DisruptorDomainEventPublisher`，通过事务 `afterCommit` 后入队避免读取未提交业务数据，但它仍是进程内队列，**不等价于 Transactional Outbox**。业务事务提交后、事件入队前或消费者持久化变更流水前发生进程崩溃，理论上仍可能丢失事件。因此本 change 的可靠性目标限定为“单实例正常运行与优雅停机条件下可靠处理，异常崩溃后的少量漂移由 digest/全量对账发现并修复”。事件发布接口、事件 DTO 与消费者保持消息中间件无关，流程验证完成后再单独立项适配 RabbitMQ 或 RocketMQ；届时应采用“业务事务内写 Outbox + MQ 投递确认/重试”的完整生产级方案。

## What Changes

- 新增 `tab_app_data_change_log` 变更流水表：数据库自增主键 `change_seq` 只负责严格递增的拉取游标（允许空洞、不回收、不复用），雪花算法生成的 `event_id` 负责事件全局唯一标识与幂等追踪，不参与排序或游标计算。组织/用户/任职/应用/角色任一变更事件只写入一条全局记录。**不含字典（DICT）**——现有变更事件本身不覆盖字典，与现状一致。
- **ORG/POSITION 两个数据域额外冗余变更前后组织路径**：CREATE 为 `before=NULL/after=新路径`，普通更新与启停填写前后路径，跨组织移动填写不同路径，DELETE 为 `before=删除前路径/after=NULL`。范围查询使用边界安全的 `path = :prefix OR path LIKE CONCAT(:prefix, '/%')`，前后任一命中即可；详情复核查不到时，调用方清理本地缓存。
- `tab_org`/`tab_user`/`tab_user_position`/`tab_app`/`tab_role` 五张同步实体表各新增一个 `version` 整型列（每次写操作原子自增 1，从 1 开始），变更流水记录与拉取/通知的响应体都携带该值，供外部应用做"本地版本 ≥ 收到的版本则忽略"的乱序/重复保护，不依赖游标顺序作为唯一防线。
- 新增增量游标拉取接口 `GET /open/api/sync/changes?sinceSeq=&entityType=&pageSize=`：`entityType` 必填且仅支持 ORG/USER/POSITION/APP/ROLE；返回变更指针（`eventId`/`entityType`/`entityId`/`operationType`/`entityVersion`/`changeSeq`/`changeTime`）与 `nextSeq`/`hasMore`。对外所有 BIGINT 标识和水位均使用十进制字符串；其中 `changeSeq` 用于游标续传，`eventId` 用于幂等追踪。详情继续通过 `GET /open/api/sync/pull?ids=` 复核。
- `sinceSeq` 早于变更流水表当前保留窗口（默认 90 天，超期清理）时，接口返回明确的业务错误码，提示调用方改走全量拉取（`GET /open/api/sync/pull`）重建，并可从对账摘要接口拿到的当前水位号重新开始增量。
- 通知请求体（`NotifyPayload`）新增 `eventId`/`changeSeq`/`entityVersion` 三个字段：同一业务变更面向不同应用时复用同一个雪花 `eventId`，每个应用的通知任务以 `(appRefId, eventId)` 唯一，所有重试复用原 `eventId`。发送前先持久化通知任务，再按 `PENDING/PROCESSING/RETRY/SUCCESS/DEAD` 状态机发送。
- 新增对账摘要接口 `GET /open/api/sync/digest?entityType=&...`：返回调用方当前可见范围内该数据类型的记录数与内容摘要（hash），并携带**当前变更流水表的最大 `changeSeq`（水位号）**——供外部应用完成一次全量拉取（走现有 `pull` 接口）后，直接从这个水位号切入增量模式，不需要额外一个"开始全量同步"的接口。
- 新增服务端投递水位表 `tab_app_sync_cursor`：每次成功响应后将 `nextSeq` 写入 `last_delivered_seq`，仅表示服务端已返回到哪里，不代表客户端消费确认，也不参与查询过滤。
- 变更流水表容量控制：保留时间窗口（默认 90 天）+ 定时清理任务，复用项目里日志清理任务（`log-cleanup`）已有的 cron 配置模式。
- 拉取类接口（`pull`/`changes`/`digest`）增加基于应用维度的简单限流（令牌桶，进程内实现），超限时延续项目既有约定——HTTP 状态码仍为 200，业务码返回 `RATE_LIMITED`，`Retry-After` 作为响应头（可选同时在响应体携带）告知调用方建议重试间隔；同时限制 `pageSize`、`ids` 数量与范围根数量。

**Non-Goals（本次不做，及理由）**：
- 不改变现有 `GET /open/api/sync/pull` 的地址、请求参数与当前状态查询语义；为五类同步实体增加固定响应键 `version` 属于本 change 的兼容扩展，内部查询与转换会相应修改。
- `USER` 数据域的"离开范围"信号本次不做（一个用户可能同时持有多条任职，"变更前/变更后组织路径"退化成路径集合才能表达，编码与前缀匹配复杂度显著上升）：当用户的全部任职都离开某应用范围时，该应用不会在增量流水里主动收到信号，只能依赖对账摘要接口的定期比对发现并清理——这正是两份参考文档反复强调的"通知/增量可以有盲区，但定期全量对账是最终兜底"原则的直接体现，不是设计缺陷，是有意识的复杂度取舍。
- 不引入"快照 id + 基于快照的分页读取"这种强一致性全量同步协议（参考文档 13.7 建议）：现有 `pull` 接口按 `updateTime ASC, id ASC` 稳定排序分页，已经能保证"翻页期间被修改的行会被移到更靠后的页里重新出现，不会产生页内丢失"，代价是并发写入量很大时理论上可能重复看到同一行两次（调用方按 `bizId` 幂等处理即可，现状如此）；真正的时间点快照隔离需要额外的快照表或数据库层支持，成本与本次收益不成比例，用"全量 + 对账摘要及时发现残留漂移"这个更轻量的组合替代。
- 不提供可分发的客户端 SDK 代码——这是接入方自己技术栈范围内的事，本 change 只保证服务端接口契约清晰、错误码明确。
- 不新增"服务端主动推送完整业务数据"的重量级 PUSH 模式（参考文档 13.11 里给"旧应用"保留的选项）：现有 `NOTIFY` 模式（轻量通知 + 应用回调拉详情）已经是两份文档都优先推荐的默认方案，重量级 PUSH 模式留给确有需求时再单独立项。
- 不引入外部消息队列（Kafka/RocketMQ）替换现有的进程内 Disruptor 环形缓冲区——更大范围的基础设施变更，超出本次范围。
- 不承诺进程异常崩溃场景下的零事件丢失；该能力必须在后续 RabbitMQ/RocketMQ 适配 change 中通过事务 Outbox 补齐。本 change 通过 digest/全量对账作为异常漂移兜底。
- 不做分布式/跨实例的限流与游标一致性保证（现有部署形态是单实例）。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `app-sync-notify-pull`：新增"变更流水表""实体版本号""增量游标拉取变更指针""对账摘要接口""推送失败重试与死信"五个 Requirement；通知与增量指针携带雪花 `eventId`、数据库游标 `changeSeq` 和实体 `entityVersion`，三者职责互不替代。
- `org-management`：新增一条独立的"组织记录版本号维护"Requirement。
- `user-management`：新增一条独立的"用户记录版本号维护"Requirement。
- `position-management`：新增一条独立的"任职记录版本号维护"Requirement。
- `application-management`：新增一条独立的"应用记录版本号维护"Requirement。
- `role-management`：新增一条独立的"角色记录版本号维护"Requirement。

## Impact

- **依赖**：依赖 `org-path-fields` change 已落地的 `tab_org.org_path` 字段。若 `org-path-fields` 尚未合并，本 change 无法开始实现。
- **数据库**：新增 `tab_app_data_change_log`、`tab_app_sync_cursor`、`tab_app_sync_metadata` 三张表；五类同步实体增加 `version`；`tab_app_config` 增加应用级 `config_epoch`；通知记录扩展事件号、请求体快照、状态、重试与租约字段。
- **后端**：五类实体 Service 维护版本；组织迁移同时更新子孙路径、版本和更新时间；`DomainChangeEvent` 增加 `eventId/entityVersion/路径快照`；新增流水与同步元数据服务、变更查询与 digest、`SyncBizPageRow/Resolver` 版本映射、通知任务状态机、雪花组件、配置 epoch、清理与发送调度器、限流组件。
- **前端范围**：核心交付不改管理端页面；本 change 只提供手动重推后端接口。管理端按钮与消费进度展示另行立项，届时同步更新 `权限资源.txt`。
