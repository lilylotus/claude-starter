## 0. 前置依赖

- [ ] 0.1 确认 `org-path-fields` change 已完成实现并通过验证（`tab_org.org_path` 字段可用），否则本 change 暂不开始

## 1. 数据库迁移

- [ ] 1.1 新增 Flyway 迁移脚本：`tab_app_data_change_log` 建表（`change_seq`/`entity_type`/`entity_id`/`operation_type`/`entity_version`/`org_scope_path_before`/`org_scope_path_after`/`change_time` + 四个审计字段，design.md Decision 1），字段命名检查与数据库关键字无冲突；索引 `(entity_type, change_seq)`、`(entity_type, entity_id)`；验证：`DESCRIBE tab_app_data_change_log;` 字段与索引齐全
- [ ] 1.2 同一或另一条迁移脚本：`tab_org`/`tab_user`/`tab_user_position` 各新增 `version` 列（`BIGINT NOT NULL DEFAULT 1`），存量数据回填为 1；验证：`SELECT COUNT(*) FROM tab_org WHERE version IS NULL OR version < 1;` 为 0（`tab_user`/`tab_user_position` 同理）
- [ ] 1.3 `tab_app_notify_record` 新增 `retry_count`（`INT NOT NULL DEFAULT 0`）、`next_retry_time`（`DATETIME NULL`）两列；验证：`DESCRIBE tab_app_notify_record;` 两列齐全，存量记录 `retry_count` 为 0
- [ ] 1.4 新增 `tab_app_sync_cursor` 建表（design.md Decision 9），唯一键 `(app_ref_id, entity_type)`；验证：`DESCRIBE tab_app_sync_cursor;` 字段与唯一键齐全

## 2. 后端：实体版本号与组织路径快照

- [ ] 2.1 `OrgEntity`/`UserEntity`/`UserPositionEntity` 新增 `version` 字段；`OrgServiceImpl`/`UserServiceImpl`/`PositionServiceImpl` 的 `create` 写入 `version=1`，`update`/`enable`/`disable`/`delete`（及用户更新接口触发的任职记录整体同步中"更新既有记录"分支）在写入前读出当前 `version` 并 `+1` 一并保存；验证：单元测试覆盖创建初始值为 1、每次写操作递增 1（含用户任职整体同步的新增/更新两个分支）
- [ ] 2.2 `DomainChangeEvent` 新增 `entityVersion`/`orgScopePathBefore`/`orgScopePathAfter` 三个字段（后两者仅 ORG/POSITION 数据类型使用，其余为 null）；验证：编译通过
- [ ] 2.3 `OrgServiceImpl.update` 在 `parentId` 变化时，按 design.md Decision 2：级联 UPDATE 前查一次自身+全部子孙的旧 `orgPath`，级联 UPDATE 后查一次新 `orgPath`，为每个受影响 id（含自身）各发布一条携带 `orgScopePathBefore`/`orgScopePathAfter` 的 `DomainChangeEvent`；验证：单元测试覆盖"变更上级组织后自身与多层子孙各自产生一条事件、before/after 路径正确"
- [ ] 2.4 `PositionServiceImpl.update`（及用户更新接口任职记录整体同步里所属组织变更的分支）在所属组织变更时，读取新旧组织的 `orgPath` 作为事件的 `orgScopePathBefore`/`orgScopePathAfter`；验证：单元测试覆盖任职记录所属组织变更时事件携带正确的前后路径

## 3. 后端：变更流水表写入

- [ ] 3.1 创建 `AppDataChangeLogEntity`/`Mapper`/`Service`/`ServiceImpl`（`record(DomainChangeEvent event)` 方法：插入一条全局记录，不按应用物化，`change_seq` 由自增主键产生）；验证：单元测试覆盖插入后能按 `entityType`/`changeSeq` 范围查询到
- [ ] 3.2 `DomainChangeEventProcessor.process` 在通知候选判定之前，先调用 `AppDataChangeLogService.record(event)` 写入变更流水表，写入异常不影响后续通知/策略重执行逻辑（各自独立 try/catch，沿用既有风格）；验证：单元测试覆盖"事件处理后变更流水表恰好新增一条记录，无论本次是否匹配到任何通知候选应用"
- [ ] 3.3 新增变更流水表容量清理定时任务（design.md Decision 8），配置项 `rbac.sync.change-log-cleanup.cron`/`retention-days`；验证：单元测试覆盖清理任务按 `change_time` 阈值删除过期记录、不影响清理后新记录的序列号连续性

## 4. 后端：增量游标拉取接口

- [ ] 4.1 `AppSyncOrgScopeResolver` 新增 `resolveScopePrefixes(appRefId, syncDomain)` 方法（design.md Decision 4），返回原始范围前缀列表；验证：单元测试覆盖零行配置返回空列表、含子孙配置正确解析出对应组织当前 `orgPath`
- [ ] 4.2 `AppDataChangeLogMapper` 新增按 `entityType`/`changeSeq` 范围 + 组织范围前缀过滤的分页查询方法，SQL 写在 XML（ORG/POSITION 用 `LIKE`/等值前缀条件，`<foreach>` 拼多个前缀的 `OR` 组合；APP/ROLE 不加范围条件）；验证：单元测试覆盖前缀过滤命中/不命中、多前缀 OR 组合
- [ ] 4.3 新增 `GET /open/api/sync/changes` 接口（`sinceSeq`/`entityType`/`pageSize` 参数，`SyncNotifyPullController` 或新增同级 Controller）：按 design.md Decision 4 完成 ORG/POSITION/APP/ROLE 的查询时过滤；`entityType=USER` 时查出候选记录后对每条记录调用 `AppSyncOrgScopeResolver.isUserWithinScope` 做二次过滤；`sinceSeq` 早于当前保留窗口最早记录时返回明确业务错误；响应包含 `nextSeq`/`hasMore`；补充 springdoc 注解；验证：集成测试覆盖 ORG/USER/POSITION/APP/ROLE 五种数据类型的增量拉取、游标续传、离开范围场景（组织被移出应用范围后指针仍可见，`pull?ids=` 复核查不到）、游标过期错误
- [ ] 4.4 每次 `/changes` 成功响应后尽力更新 `tab_app_sync_cursor`（design.md Decision 9），写入失败不影响响应；验证：单元测试覆盖成功更新、并发/乱序请求不回退游标（`GREATEST` 语义）、写入异常不影响主响应

## 5. 后端：对账摘要接口

- [ ] 5.1 新增 `GET /open/api/sync/digest` 接口：按调用方当前可见范围统计该数据类型记录数与内容摘要（hash 算法选型与摘要计算范围在实现时确定，需覆盖组织范围过滤后的实际可见集合），响应携带 `currentMaxSeq`（当前变更流水表最大序列号）；补充 springdoc 注解；验证：集成测试覆盖记录数与摘要值随数据变化而变化、`currentMaxSeq` 与变更流水表实际最大值一致

## 6. 后端：通知重试与死信

- [ ] 6.1 `AppNotifyServiceImpl.notifyOneApp` 失败时按 design.md Decision 6 设置 `retryCount+1`/`nextRetryTime`（指数退避，上限可配置）；通知请求体（`NotifyPayload`）新增 `changeSeq`/`entityVersion` 字段并透传；验证：单元测试覆盖失败后 `retryCount`/`nextRetryTime` 正确计算，通知请求体正确携带新增字段
- [ ] 6.2 新增定时重推任务：扫描 `notify_status=FAILURE AND retry_count < maxRetry AND next_retry_time <= NOW()` 的记录逐条重推，成功转 `SUCCESS`，失败按退避重新计算；验证：单元测试覆盖到期记录被重推、未到期记录不被处理、达到最大重试次数后不再被任务捞取
- [ ] 6.3 新增管理端手动重推单条通知记录的接口（复用现有通知记录管理端查询页面所在模块，补充一个操作按钮/接口）；验证：集成测试覆盖手动重推后状态与重试信息正确更新

## 7. 后端：限流

- [ ] 7.1 新增进程内令牌桶限流组件（design.md Decision 7），配置项暴露容量/填充速率；接入 `pull`/`changes`/`digest` 三个接口入口，超限返回明确业务错误码；验证：单元测试覆盖正常速率放行、超限拒绝、不同应用互不影响

## 8. 端到端验证

- [ ] 8.1 本地启动前后端（依赖 `org-path-fields` change 已实现），创建组织/用户/任职记录，确认 `version` 从 1 开始正确递增
- [ ] 8.2 配置一个测试应用的组织范围，触发一次组织迁移（把该应用范围内的一个中间层组织移动到范围外），确认：变更流水表为该组织及其全部子孙各生成一条记录（`orgScopePathBefore`/`orgScopePathAfter` 正确）；该应用调用 `/changes` 能看到这些记录的指针；调用 `/pull?ids=` 复核时查不到（因为已不在范围内）
- [ ] 8.3 验证增量拉取的游标续传：多次调用 `/changes` 传入上一次响应的 `nextSeq`，确认不重复、不遗漏
- [ ] 8.4 验证游标过期场景：手动清理变更流水表模拟保留窗口过期，确认 `/changes` 返回明确的业务错误
- [ ] 8.5 验证对账摘要接口：调用 `/digest` 拿到 `currentMaxSeq`，随后以该值为 `sinceSeq` 调用 `/changes`，确认能正确衔接后续新产生的变更
- [ ] 8.6 验证通知重试：让通知回调地址短暂不可达，确认失败通知进入待重试、定时任务到期后自动重推、恢复可达后成功
- [ ] 8.7 运行完整回归：`./gradlew test --tests "cn.nihility.rbac.sync.*" --tests "cn.nihility.rbac.org.*" --tests "cn.nihility.rbac.user.*"` 全部通过，确认本次改造未破坏现有 `/pull` 接口与通知既有行为
