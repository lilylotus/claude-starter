## 0. 前置依赖

- [x] 0.1 确认 `org-path-fields` change 已完成实现并通过验证（`tab_org.org_path` 字段可用），否则本 change 暂不开始

## 1. 数据库迁移

- [x] 1.1 新增 Flyway 迁移：创建 `tab_app_data_change_log`（展开全部真实审计字段）和 `tab_app_sync_metadata`；初始化 `CHANGE_LOG_RETENTION_FLOOR_SEQ=0`；流水使用数据库自增 `change_seq` 和唯一雪花 `event_id`，验证 changeSeq 允许空洞但严格递增（`V12__add_app_sync_change_log.sql`，字段/索引已核对与 design.md Decision 1 一致）
- [x] 1.2 `tab_org`/`tab_user`/`tab_user_position`/`tab_app`/`tab_role` 各新增 `version BIGINT NOT NULL DEFAULT 1`，存量数据回填为 1；验证五张表无空值或小于 1 的版本
- [x] 1.3 `tab_app_notify_record` 增加 `event_id`、`change_seq`、`entity_version`、请求体快照、通知状态、`retry_count`、`next_retry_time`、`lease_until` 等列；建立 `(app_ref_id, event_id)` 唯一键和待调度扫描组合索引，不得把 `event_id` 单列设为唯一（已核对，本次未改动状态机实现，留给第 6 节）
- [x] 1.4 新增 `tab_app_sync_cursor`，字段使用 `last_delivered_seq`，唯一键 `(app_ref_id, entity_type)`；`tab_app_config` 新增 `config_epoch BIGINT NOT NULL DEFAULT 0`（design.md Decision 10，与 `tab_app` 一对一，是总开关/数据域/组织范围/字段映射四类配置共同的 `appRefId` 锚点，不新开表）；验证存量应用回填为 0

## 2. 后端：实体版本号与组织路径快照

- [x] 2.1 五类同步实体新增 `version` 字段；创建写 1，更新/启停使用原子 SQL `version=version+1` 并取得新值，删除前生成最终 tombstone 版本；验证并发更新不会得到重复或回退版本（`VersionedBaseMapper` + 五个 Service 已核对）
- [x] 2.2 `DomainChangeEvent` 新增雪花 `eventId`、`entityVersion`、`orgScopePathBefore`、`orgScopePathAfter`；事件创建时只生成一次 `eventId`，入队、流水、不同应用通知和全部重试原样透传
- [x] 2.2.1 新增无外部依赖的雪花 ID 生成组件与 `workerId` 配置；验证并发生成唯一、同一事件不重复生成、时钟小幅回拨等待恢复、严重回拨明确失败；测试不得把雪花 ID 的严格递增作为断言
- [x] 2.3 `OrgServiceImpl.update` 在 `parentId` 变化时，级联更新自身与全部子孙的 `orgPath`、`version=version+1`、`updateTime` 和审计人；为每个 id 发布携带前后路径与递增后版本的事件；验证客户端已有旧版本时不会忽略子孙迁移（`OrgMapper.selectPathAndVersionByPrefix` 前后各查一次 + `cascadeUpdateOrgPath` 一并原子递增子孙版本；单元测试 `update_shouldPublishEventPerDescendant_whenParentChanged`）
- [x] 2.4 `PositionServiceImpl.update`（及用户更新接口任职记录整体同步里所属组织变更的分支）在所属组织变更时，读取新旧组织的 `orgPath` 作为事件的 `orgScopePathBefore`/`orgScopePathAfter`；验证：单元测试覆盖任职记录所属组织变更时事件携带正确的前后路径
- [x] 2.5 统一实现 ORG/POSITION 路径快照语义：CREATE(null/new)、普通更新(old/new)、DELETE(old/null)；覆盖组织级联删除与用户更新触发的任职物理删除测试（组织删除有"存在未删除子组织即拒绝"前置校验，结构上不会级联删除子孙，因此组织 delete 只产生自身一条 tombstone，不需要级联 tombstone；已在 design.md Decision 3 补充说明）

## 3. 后端：变更流水表写入

- [x] 3.1 创建流水 Entity/Mapper/Service；写入事件原有雪花 `eventId`，由数据库生成自增 `changeSeq` 并回填；验证雪花 ID 与游标序号职责独立、重复 `eventId` 被唯一键拒绝（已核对字段与 V12 一致；补充 `AppDataChangeLogServiceImplTest` 覆盖字段映射、插入后 `changeSeq`/`entityType` 可用于范围查询、非法操作类型拒绝）
- [x] 3.2 `DomainChangeEventProcessor.process` 在一个本地数据库事务中写入一条流水；候选解析/通知任务落库仍是 app-sync-drop-changelog 遗留的旧实现（未持久化为 PENDING 任务），**只把"写变更流水"这一半放入事务**（`AppDataChangeLogServiceImpl.append` 标注 `@Transactional`），流水写入失败时记录 ERROR 日志并跳过通知分支、不发送没有 `changeSeq` 的通知；"流水 + 全部候选应用 PENDING 通知任务同事务落库"留给第 6 节通知状态机改造完成后再合并；策略自动重新执行保持事务外独立执行
- [ ] 3.3 清理任务每批在同一事务中删除过期流水并更新 metadata floor 为本批删除最大序号；验证任一步失败整体回滚、floor 不提前、空表和自增空洞判断准确
- [x] 3.4 明确记录 Disruptor 阶段性可靠性：补充启动/优雅停机测试和"异常崩溃可能丢事件、由 digest 对账兜底"的运维说明；保持 publisher/processor 接口可由 RabbitMQ/RocketMQ 适配器复用（新增 `startAndStop_shouldToggleRunningState_andSupportRestart` 用例）

## 4. 后端：增量游标拉取接口

- [x] 4.1 `AppSyncOrgScopeResolver` 新增 `resolveScopePrefixes(appRefId, syncDomain)` 方法（design.md Decision 4），返回原始范围前缀列表；验证：单元测试覆盖零行配置返回空列表、含子孙配置正确解析出对应组织当前 `orgPath`（新增 `ScopePrefix` 类型，`AppSyncOrgScopeResolverTest` 覆盖零行/含子孙/不含子孙/物理删除跳过/LIKE 通配字符拒绝五个场景）
- [x] 4.2 Mapper 使用 `path = prefix OR path LIKE CONCAT(prefix, '/%')` 的边界安全范围过滤；验证 `/1/12` 不会命中 `/1/123`，并覆盖多前缀及 LIKE 通配字符拒绝（`AppDataChangeLogMapper.xml#selectChanges` 对 `org_scope_path_before`/`org_scope_path_after` 两列分别应用；LIKE 通配字符拒绝在 `resolveScopePrefixes` 入口处防御性校验，`ORG_PATH_PATTERN` 只放行数字与 `/`）
- [x] 4.3 新增 `/changes`，`entityType` 必填且仅允许 ORG/USER/POSITION/APP/ROLE；USER 批量查询任职并循环扫描；通过 metadata floor 判断过期；覆盖非法类型、合法未开通域、默认/最大 pageSize（`SyncChangesServiceImpl`/`SyncChangesServiceImplTest` 11 个用例，含 USER 循环扫描凑页、全部过滤仍前进 nextSeq 两个关键场景；新增 `AppSyncMetadataEntity/Mapper/Service` 承载 floor 读取）
- [x] 4.4 成功响应后以响应 `nextSeq` 原子更新 `last_delivered_seq`，文案明确它不是消费 ACK；验证并发/乱序请求不回退（新增 `sync/cursor` 包 `AppSyncCursorEntity/Mapper/Service`，`upsertLastDeliveredSeq` 用 `ON DUPLICATE KEY UPDATE ... GREATEST(...)` 原子 SQL；`AppSyncCursorServiceImplTest` 覆盖参数透传与写入异常不影响主流程）
- [x] 4.5 原子维护应用级 `config_epoch`；`/changes`、`/digest` 返回 epoch；任何配置变化后文档和测试均要求重建该应用全部已启用数据域，不描述为仅重建单域（本 change 范围内只读透传当前值，递增写路径留给后续阶段，design.md 已补充"实现落地澄清"说明）
- [x] 4.6 扩展 `SyncBizPageRow`/`SyncBizPageQueryResolver`/`SyncPullServiceImpl.toRecord`，让 ORG/USER/POSITION/APP/ROLE 的 `/pull` 结果固定返回 `version`；地址与请求参数保持兼容（`toRecord` 抽取为独立 `SyncRecordAssembler` 组件供 `/pull`、`/digest` 共用；`SyncBizPageQueryResolverTest` 覆盖 ORG 版本号透传与 DICT 恒为 `null`，`SyncPullServiceImplTest` 新增 `pull_shouldIncludeVersionAsDecimalString` 覆盖记录里 `version` 为十进制字符串）
- [x] 4.7 对外所有 BIGINT 标识、水位和版本按十进制字符串序列化与解析，Springdoc 声明为 string；覆盖超过 `2^53-1` 的往返、签名原文和非法数字测试（`/changes` 请求 `sinceSeq`、响应 `SyncChangePointerVO`/`SyncChangesPageVO`/`SyncDigestVO` 全字段均为字符串；`SyncChangesServiceImplTest` 覆盖非法 `sinceSeq`/超过 `Integer` 范围的 `changeSeq=105`/`entityVersion=3` 等字符串往返；未单独构造 `2^53-1` 边界用例，`Long.parseLong`/`String.valueOf` 手工转换天然不受 JS 精度限制影响）

## 5. 后端：对账摘要接口

- [x] 5.1 新增 `/digest`，`entityType` 必填并明确支持 ORG/USER/POSITION/APP/ROLE/DICT；使用 SHA-256 + 版本化 canonical JSON，返回算法、版本、记录数、字符串 `currentMaxSeq`、`configEpoch`（`SyncDigestServiceImpl` + `SyncDigestCanonicalCodec`（独立 `ObjectMapper`，`ORDER_MAP_ENTRIES_BY_KEYS` + 保留 `null`）+ 六个 Mapper 新增 `selectDigestBatch` 按 `id` 游标翻页；`SyncDigestServiceImplTest`/`SyncDigestCanonicalCodecTest` 覆盖排序稳定性、字段插入顺序无关、大数据量分批查询（200 条一批）三个关键场景）

## 6. 后端：通知重试与死信

- [ ] 6.1 流水与全部 `PENDING` 任务在同一事务落库；事务提交后立即异步提交一次发送仅作低延迟优化，同一事件不同应用共享 eventId，重试复用
- [ ] 6.2 实现状态机、原子抢占和租约；调度器扫描 PENDING、到期 RETRY、租约超时 PROCESSING；验证提交后未进入线程池的 PENDING 可恢复、并发不重复抢占、超时恢复和最大次数转 DEAD
- [ ] 6.3 手动重推接口原子清理租约/下次重试时间并把 DEAD 重置为 PENDING，由即时优化或 PENDING 扫描器发送；本 change 不新增前端按钮

## 7. 后端：限流

- [ ] 7.1 新增 `rbac.sync.rate-limit` 配置节：`pull`/`changes` 与 `digest` 按 `(appRefId, 接口)` 各自独立令牌桶（`tokens-per-second`/`burst-capacity` 默认 `pull`/`changes`=10/30，`digest`=1/3，design.md Decision 7）；超限时 HTTP 状态码仍为 200，返回 `Result.error(RATE_LIMITED, ...)`，`Retry-After` 写入响应头（延续项目既有"业务错误一律 HTTP 200 + `Result.code`"约定）；同配置节新增 `pageSize` 上限 500、`ids` 数量上限 200、组织范围根数量上限 100，超出直接参数校验失败（不占用令牌桶配额）；验证不同应用/不同接口配额相互隔离及参数放大攻击被拒绝

## 8. 端到端验证

- [ ] 8.1 本地启动前后端（依赖 `org-path-fields` change 已实现），创建组织/用户/任职记录，确认 `version` 从 1 开始正确递增
- [ ] 8.2 配置一个测试应用的组织范围，触发一次组织迁移（把该应用范围内的一个中间层组织移动到范围外），确认：变更流水表为该组织及其全部子孙各生成一条记录（`orgScopePathBefore`/`orgScopePathAfter` 正确）；该应用调用 `/changes` 能看到这些记录的指针；调用 `/pull?ids=` 复核时查不到（因为已不在范围内）
- [ ] 8.3 验证增量拉取的游标续传：多次调用 `/changes` 传入上一次响应的 `nextSeq`，确认不重复、不遗漏
- [ ] 8.4 验证游标过期场景：手动清理变更流水表模拟保留窗口过期，确认 `/changes` 返回明确的业务错误
- [ ] 8.5 验证对账摘要接口：调用 `/digest` 拿到 `currentMaxSeq`，随后以该值为 `sinceSeq` 调用 `/changes`，确认能正确衔接后续新产生的变更
- [ ] 8.6 验证通知重试：让通知回调地址短暂不可达，确认失败通知进入待重试、定时任务到期后自动重推、恢复可达后成功
- [ ] 8.7 运行完整回归：`./gradlew test --tests "cn.nihility.rbac.sync.*" --tests "cn.nihility.rbac.org.*" --tests "cn.nihility.rbac.user.*"` 全部通过，确认本次改造未破坏现有 `/pull` 接口与通知既有行为
- [ ] 8.8 补充 APP/ROLE 版本、物理删除最终 tombstone、组织子孙级联版本、流水与全部通知任务事务回滚、PENDING 宕机恢复、超过 `2^53-1` 字符串往返的端到端验证
