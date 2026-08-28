## Why

`app-sync-notify-pull` 现有机制（通知走单次 HTTP 回调无重试、增量拉取按 `updateTime` 游标）已经覆盖了基础的推/拉能力，但结合 `4A项目数据推拉同步架构设计.md` 与 `codex-springboot-4a-conversation-summary.md` 两份设计参考，可以看到几个会在实际接入场景下逐渐暴露的问题：（1）`updateTime` 游标存在"同一毫秒多条变更""恰好等于游标值的记录跨页重复"等已知边界行为，缺少严格递增、不重复、可断点续传的游标语义；（2）通知发送失败即终态，没有退避重试和死信兜底；（3）**"离开范围"没有信号**——组织被移动到另一个分支、任职记录的所属组织被改到范围外，会让该实体从某个应用的可见范围里"悄悄消失"，现有机制只按当前状态做正向过滤，不产生任何"应用需要把这条本地缓存删掉"的信号，应用没有办法感知，只能永远保留一份过期数据；（4）重复/乱序投递（网络重试、批量拉取分页重叠）没有实体级别的版本号兜底，纯粹依赖游标去重不够健壮。两份参考文档一致建议的核心方案是"全局单调递增序列号的变更流水表，作为推/拉两条通道共用的数据出口"，并补充了实体版本号、离开范围的 tombstone 语义、事务性发件箱等配套原则，本次据此为现有机制补齐这些能力。

**与 `app-sync-drop-changelog`（已归档）的关系**：该 change 移除了当时的 `tab_app_data_change_log`，原因是那张表**按应用物化**——一次变更命中 N 个订阅应用就物理插入 N 条记录，导致"表行数 = 变更数 × 应用数"，且应用同步配置一开一关会让物化记录与真实数据状态逐渐脱节。本次重新引入的变更流水表刻意避开这个错误：**全局只存一条物理记录**（不按应用复制），按应用/组织范围的过滤全部在查询时计算（`WHERE` 条件下推，不提前物化）。现有的 `GET /open/api/sync/pull`（直接查业务表当前数据的全量/条件拉取接口）完全保留不变，继续承担"全量拉取兜底"与"变更详情二次查询"的角色。

**已验证不存在的风险**：两份参考文档都强调"数据库事务 + 变更记录写入必须同一个事务，不能在 `@Transactional` 方法内直接同步调用外部 HTTP"（Transactional Outbox 原则）。现有 `DisruptorDomainEventPublisher.publish()` 已经通过 `TransactionSynchronizationManager` 把事件实际入队延迟到调用方事务 `afterCommit` 之后（`app-sync-notify-pull-api` change design.md Decision 9 补充说明记录过一次因为时序问题导致组织范围判定假阴性的真实故障与修复），本次新增的变更流水表写入同样发生在这条已经修好的路径上（`DomainChangeEventProcessor` 处理时，业务事务必然已提交），不需要额外修复。

## What Changes

- 新增 `tab_app_data_change_log` 变更流水表：全局唯一一份，自增主键本身即全局单调递增的 `change_seq`；组织/用户/任职/应用/角色任一变更事件产生的同时（在既有 `DomainChangeEventProcessor` 里，业务事务已提交之后）写入一条记录（`entity_type`/`entity_id`/`operation_type`/`entity_version`/`change_time`）。**不含字典（DICT）**——现有变更事件本身不覆盖字典，与现状一致。
- **ORG/POSITION 两个数据域的变更记录额外冗余"变更前/变更后"两个组织路径**（`org_scope_path_before`/`org_scope_path_after`，依赖 `org-path-fields` change 新增的 `org_path`）：新增/普通字段更新时两者相同；上级组织变更（含级联到子孙组织）、任职记录的所属组织变更时两者不同。增量拉取查询按"前后任一落在应用配置范围内"匹配（`WHERE org_scope_path_before LIKE :prefix OR org_scope_path_after LIKE :prefix`），保证一个组织/任职被移出某应用范围时，该应用仍然能在变更流水里看到这条记录——应用随后调用现有 `pull?ids=` 复核时，若该 id 不再出现在返回结果里（因为不再落在其配置范围内），即视为"该实体已离开我的范围，应删除本地缓存"，不需要额外发明一种"伪删除"操作码，复用现有 `pull` 接口"范围外数据不返回"的既有行为。
- `tab_org`/`tab_user`/`tab_user_position` 三张核心表各新增一个 `version` 整型列（乐观锁风格，每次写操作自增 1，从 1 开始），变更流水记录与拉取/通知的响应体都携带该值，供外部应用做"本地版本 ≥ 收到的版本则忽略"的乱序/重复保护，不依赖游标顺序作为唯一防线。
- 新增增量游标拉取接口 `GET /open/api/sync/changes?sinceSeq=&entityType=&pageSize=`：返回本次匹配的变更指针列表（`entityType`/`entityId`/`operationType`/`entityVersion`/`changeSeq`/`changeTime`）与 `nextSeq`/`hasMore`，按调用方应用当前的数据域启用状态、同步总开关、组织范围过滤，过滤全部在查询时计算，不做任何物化。**只返回指针，不返回业务字段**——需要详情时用返回的 `entityId` 列表调用现有 `GET /open/api/sync/pull`（传 `ids`），两个接口共用同一份权限判定与字段映射逻辑；`USER` 数据域沿用现有"任一任职落在范围内即命中"的运行时判定方式，不引入 `org_scope_path_before/after`（见 Non-Goals 说明）。
- `sinceSeq` 早于变更流水表当前保留窗口（默认 90 天，超期清理）时，接口返回明确的业务错误码，提示调用方改走全量拉取（`GET /open/api/sync/pull`）重建，并可从对账摘要接口拿到的当前水位号重新开始增量。
- 通知请求体（`NotifyPayload`）新增 `changeSeq`/`entityVersion` 两个字段，把推送和拉取绑定到同一个变更流水表出口；通知发送失败时不再是终态：新增退避重试（有限次数的指数退避）+ 重试耗尽后进入"失败"终态，由定时任务批量重推处于"待重试"状态的记录，也支持管理端手动触发重推。
- 新增对账摘要接口 `GET /open/api/sync/digest?entityType=&...`：返回调用方当前可见范围内该数据类型的记录数与内容摘要（hash），并携带**当前变更流水表的最大 `changeSeq`（水位号）**——供外部应用完成一次全量拉取（走现有 `pull` 接口）后，直接从这个水位号切入增量模式，不需要额外一个"开始全量同步"的接口。
- 新增应用消费游标可见性表 `tab_app_sync_cursor`：记录每个应用每个数据类型最近一次成功调用增量拉取接口时的 `sinceSeq`，仅用于管理端展示"该应用当前落后多少"，不作为查询过滤的依据（游标本身由调用方各自持有并传入，服务端不替它做决定）。
- 变更流水表容量控制：保留时间窗口（默认 90 天）+ 定时清理任务，复用项目里日志清理任务（`log-cleanup`）已有的 cron 配置模式。
- 拉取类接口（`pull`/`changes`/`digest`）增加基于应用维度的简单限流（令牌桶，进程内实现）。

**Non-Goals（本次不做，及理由）**：
- 不改动现有 `GET /open/api/sync/pull` 的请求/响应契约与内部实现——它已经是"直接查业务表当前状态"的正确形态，继续承担全量兜底与详情复核角色。
- `USER` 数据域的"离开范围"信号本次不做（一个用户可能同时持有多条任职，"变更前/变更后组织路径"退化成路径集合才能表达，编码与前缀匹配复杂度显著上升）：当用户的全部任职都离开某应用范围时，该应用不会在增量流水里主动收到信号，只能依赖对账摘要接口的定期比对发现并清理——这正是两份参考文档反复强调的"通知/增量可以有盲区，但定期全量对账是最终兜底"原则的直接体现，不是设计缺陷，是有意识的复杂度取舍。
- 不引入"快照 id + 基于快照的分页读取"这种强一致性全量同步协议（参考文档 13.7 建议）：现有 `pull` 接口按 `updateTime ASC, id ASC` 稳定排序分页，已经能保证"翻页期间被修改的行会被移到更靠后的页里重新出现，不会产生页内丢失"，代价是并发写入量很大时理论上可能重复看到同一行两次（调用方按 `bizId` 幂等处理即可，现状如此）；真正的时间点快照隔离需要额外的快照表或数据库层支持，成本与本次收益不成比例，用"全量 + 对账摘要及时发现残留漂移"这个更轻量的组合替代。
- 不提供可分发的客户端 SDK 代码——这是接入方自己技术栈范围内的事，本 change 只保证服务端接口契约清晰、错误码明确。
- 不新增"服务端主动推送完整业务数据"的重量级 PUSH 模式（参考文档 13.11 里给"旧应用"保留的选项）：现有 `NOTIFY` 模式（轻量通知 + 应用回调拉详情）已经是两份文档都优先推荐的默认方案，重量级 PUSH 模式留给确有需求时再单独立项。
- 不引入外部消息队列（Kafka/RocketMQ）替换现有的进程内 Disruptor 环形缓冲区——更大范围的基础设施变更，超出本次范围。
- 不做分布式/跨实例的限流与游标一致性保证（现有部署形态是单实例）。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `app-sync-notify-pull`：新增"变更流水表""实体版本号""增量游标拉取变更指针""对账摘要接口""推送失败重试与死信"五个 Requirement；「通知模式下的变更通知」Requirement 修改为通知请求体携带 `changeSeq`/`entityVersion`；「组织/用户/任职/应用/角色数据变更产生同步事件」Requirement 修改为同时写入变更流水表一条记录。
- `org-management`：新增一条独立的"组织记录版本号维护"Requirement。
- `user-management`：新增一条独立的"用户记录版本号维护"Requirement。
- `position-management`：新增一条独立的"任职记录版本号维护"Requirement。

## Impact

- **依赖**：依赖 `org-path-fields` change 已落地的 `tab_org.org_path` 字段。若 `org-path-fields` 尚未合并，本 change 无法开始实现。
- **数据库**：新增 `tab_app_data_change_log`、`tab_app_sync_cursor` 两张表；`tab_org`/`tab_user`/`tab_user_position` 各新增 `version` 列；`tab_app_notify_record` 补充重试相关列（重试次数、下次重试时间、终态标记）。
- **后端**：`OrgServiceImpl`/`UserServiceImpl`/`PositionServiceImpl`（维护 `version`，`OrgServiceImpl.update` 额外在上级组织变更时计算变更前后路径）、`DomainChangeEvent`（新增 `entityVersion`/`orgScopePathBefore`/`orgScopePathAfter` 字段）、`DomainChangeEventProcessor`（写入变更流水表）、新增 `AppDataChangeLogService`/`Mapper`（全局单表）、`SyncNotifyPullController`（新增 `/changes`、`/digest` 两个接口）、`AppNotifyServiceImpl`（重试逻辑）、新增定时任务（重推失败通知、清理过期变更流水）、新增限流组件。
- **不涉及前端**：`/changes`、`/digest`、重试机制均为对外 API，不改动任何管理端页面；`tab_app_sync_cursor` 的管理端展示是可选任务，不阻塞核心能力交付。
