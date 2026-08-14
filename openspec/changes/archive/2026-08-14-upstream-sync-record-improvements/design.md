## Context

见 proposal.md - Why。相关现状：`UpstreamSyncExecutor.syncDomain` 当前对每个已启用数据域，取数成功后无论 `rawRows` 是否为空都会走完整个"逐行处理→汇总→`saveSyncRecord`"流程，`resolveStatus(total=0, success=0)` 恒定返回 `SUCCESS`，产生一条除时间戳外没有任何信息量的记录。`UpstreamSyncRecordService.listBySource`/`UpstreamSyncRecordController` 目前是无分页的 `List<UpstreamSyncRecordVO>`。`tab_upstream_sync_record` 只有汇总计数与截断到 5 条、500 字符的 `fail_summary` 文本，没有任何地方持久化"取数阶段实际拿到的每一行数据"。

## Goals / Non-Goals

**Goals:**
- 消除"空跑"记录对同步历史的噪音污染，同时不影响两类真正的失败判定（取数异常、未配置主键前置拦截）继续被记录。
- 同步记录列表与新增的行明细列表都走标准分页，复用仓库已有的 `PageResult`/`page`/`pageSize` 请求参数约定。
- 无论成功还是失败，每一行处理过的原始上游数据都可追溯。

**Non-Goals:**
- 不引入行级别的"重试"能力——proposal.md 已经明确执行记录/明细只用于展示排查，不驱动自动重试。
- 不对已经存在的历史 `tab_upstream_sync_record` 数据做回填明细的迁移——明细表是新表，只覆盖本次改动上线之后产生的执行记录，历史记录展示上仍然只有汇总计数，没有明细可看（属于可接受的一次性局限，见 Risks）。
- 不改变 `fail_summary` 截断到 5 条/500 字符的既有行为——它是"执行记录"这个粒度的快速概览，明细表是"看全部"的下钻入口，二者并存，不合并成一套。
- 不引入批量插入框架（如 MyBatis-Plus `saveBatch`/JDBC batch）——沿用仓库里 `UpstreamFieldMappingServiceImpl.replace` 已有的逐行 `insert` 风格，保持实现方式一致；本场景的数据量级（组织/用户/任职批量同步，通常几十到几千行）不足以让逐行 insert 成为性能瓶颈，真出现瓶颈时再单独优化，不在本次范围内。

## Decisions

### Decision 1：取数结果为空时提前返回，不写执行记录，但仍更新 `lastSyncTime`
`syncDomain` 在 `rawRows = fetchRawRows(...)` 成功返回后，立即判断 `rawRows.isEmpty()`：为空时跳过后续的逐行处理与 `saveSyncRecord`，直接更新 `domainConfig.setLastSyncTime(...)` 并 `return`——"上次同步时间"字段的语义是"最近一次尝试同步的时间"，跳过记录不代表这次同步没有发生，仍然要让调度到期判断（`UpstreamSyncScheduler.isDue`）拿到正确的时间基准。两类既有的"取数前/取数失败"判定失败场景（主键未配置的前置拦截、取数异常）在这次改动之前就已经各自 `return`，不受影响，继续记录。

### Decision 2：新增 `tab_upstream_sync_record_detail` 表，一行一条处理明细，冗余 `source_id` 便于级联删除
```sql
CREATE TABLE IF NOT EXISTS `tab_upstream_sync_record_detail`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `sync_record_id` BIGINT       NOT NULL COMMENT '所属同步执行记录 id，关联 tab_upstream_sync_record.id',
    `source_id`      BIGINT       NOT NULL COMMENT '所属上游数据源 id，冗余自所属执行记录，供按数据源级联删除，不需要联表',
    `row_no`         INT          NOT NULL COMMENT '本次执行内该行的序号，从 1 开始',
    `row_data`       TEXT         NOT NULL COMMENT '该行的原始上游数据（取数阶段的原始行，JSON 文本）',
    `status`         VARCHAR(16)  NOT NULL COMMENT '该行处理状态：SUCCESS=成功，FAILED=失败',
    `fail_reason`    VARCHAR(500) NULL COMMENT '失败原因，仅 status=FAILED 时有值',
    `create_by`      VARCHAR(64)  NULL COMMENT '创建人',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      VARCHAR(64)  NULL COMMENT '更新人',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_upstream_sync_record_detail_record` (`sync_record_id`, `id`),
    KEY `idx_tab_upstream_sync_record_detail_source` (`source_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '上游数据同步执行记录明细表，记录每行处理的原始数据与结果，成功/失败均记录';
```
`row_data` 存"取数阶段的原始上游行"（`rawRow`，转换前），不存转换后的系统字段行——这是管理员真正想复查的"上游到底给了什么数据"，转换后的行是内部处理产物，出问题时结合 `fail_reason` 里的字段编码已经能定位到具体是哪个映射/转换环节的问题，不需要额外再存一份。`source_id` 冗余是为了删除数据源时能直接 `DELETE ... WHERE source_id = ?`，不需要先查一遍该数据源名下全部 `sync_record_id` 再做子查询删除。

- **备选方案**：不新建表，把每行明细序列化成一个大 JSON 数组存进 `tab_upstream_sync_record` 新增的一列。未采纳——单条记录的行数上不封顶（一次同步几千行是可能的），塞进单个 TEXT/JSON 列既没法分页查询（只能整列读出来在应用层切片，失去数据库分页的意义），也让这一列的读写成本随行数线性增长，不如按行拆表、用索引支持分页查询自然。

### Decision 3：`UpstreamSyncExecutor` 收集明细列表，与汇总记录一起在同一个事务里写入
`syncDomain` 逐行处理时，除了累加 `success`/`failMessages`，同时往一个 `List<UpstreamSyncRecordDetailEntity>` 里追加当前行的明细（`rowNo` 从 1 递增，`rowData` 用 `JacksonUtils.toJson(rawRow)`，成功行 `status=SUCCESS`/`failReason=null`，失败行 `status=FAILED`/`failReason=` 异常消息，不受 `FAIL_SUMMARY_MAX_ITEMS` 截断限制——明细表本身就是"看全部"的入口，不应该像 `fail_summary` 文本那样只截前 5 条）。汇总记录 `saveSyncRecord` 先 `insert`（MyBatis-Plus `IdType.AUTO` 回填自增 id 到实体），拿到 `record.getId()` 后再把这个 id 灌进每条明细实体、逐行 `insert`。整个方法不需要新增 `@Transactional`——`UpstreamSyncExecutor` 类目前没有整体事务包裹（每行落库处理各自在 `UpstreamRowUpserter.upsertRow` 里开独立的 `REQUIRES_NEW` 事务），写执行记录与明细本身就是同步流程末尾的收尾动作，用不着回滚"已经落库成功的组织/用户/任职数据"，保持现状不额外加事务边界。

### Decision 4：同步记录列表接口签名从 `List<UpstreamSyncRecordVO>` 改为 `PageResult<UpstreamSyncRecordVO>`，是一次破坏性接口变更
`GET /api/identity/upstream-sources/{id}/sync-records` 直接改造为分页返回（新增 `page`/`pageSize` 查询参数，默认值比照 `OrgController.children` 的 `page=1`/`pageSize=10`），不做"新增一个 v2 分页接口、保留旧接口"的兼容并存——这是一个仍在开发阶段、没有外部系统依赖的内部管理页面接口，直接改造成本最低；前端 `UpstreamSourceConfigView.vue` 是这个接口唯一的调用方，本次改动会同步更新。新增的行明细查询接口 `GET /api/identity/upstream-sources/{id}/sync-records/{recordId}/details` 同时校验 `sourceId`/`recordId` 匹配（mapper 查询条件同时带 `source_id` 与 `sync_record_id`），避免管理员通过猜测 `recordId` 越权查看其他数据源的明细。

## Risks / Trade-offs

- [风险] 本次改动上线前已经产生的历史执行记录没有对应的行明细，管理员点开这些旧记录的"查看明细"会看到空列表 → 缓解：这是新表从零开始的自然限制，无法伪造出历史时刻并不存在的数据；空列表本身不会引起误解（不会显示"失败"或报错，只是"暂无明细数据"），可以接受。
- [风险] "取数结果为空不记录"这条规则，如果管理员依赖"看到一条 SUCCESS 记录"来确认定时任务确实在正常运转（哪怕没有新数据），会失去这个信号 → 缓解：`tab_upstream_domain_config.last_sync_time` 仍然会更新（Decision 1），后端 `UpstreamDomainConfigVO` 已经带这个字段，但前端"是否启用"分区目前并未渲染它——本次顺带把它展示出来（紧挨"是否启用"开关，只读文本），管理员由此依然有办法确认"最近一次尝试同步是什么时候"，只是不再需要翻一堆空记录去找。
