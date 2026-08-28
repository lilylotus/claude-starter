## 1. 数据库迁移

- [x] 1.1 新增 `backend/src/main/resources/db/migration/V7__drop_app_data_change_log.sql`：`DROP TABLE tab_app_data_change_log`；`ALTER TABLE tab_app_notify_record DROP INDEX idx_tab_app_notify_record_change_log_id, DROP COLUMN change_log_id`；`ALTER TABLE tab_app_pull_record DROP COLUMN pull_mode`（标准可移植 SQL，不用 MySQL 8.0+ 专属语法；不写 DOWN 脚本，沿用仓库既有迁移脚本风格）

## 2. 删除变更记录模块

- [x] 2.1 删除整包 `backend/src/main/java/cn/nihility/rbac/sync/changelog/`（`AppDataChangeLogEntity`/`AppDataChangeLogMapper`/`AppDataChangeLogService`/`AppDataChangeLogServiceImpl`）
- [x] 2.2 删除 `backend/src/main/resources/mybatis/mapper/AppDataChangeLogMapper.xml`
- [x] 2.3 删除 `backend/src/main/java/cn/nihility/rbac/sync/pull/record/constant/PullMode.java`
- [x] 2.4 删除对应的测试文件 `AppDataChangeLogServiceImplTest.java`（若存在其他仅测该模块的测试类一并删除）

## 3. 通知候选判定与触发改造

- [x] 3.1 `NotifyTargetMapper`/`NotifyTargetMapper.xml`：`selectCandidateAppRefIds` 查询 SQL 增加 `sync_mode = 'NOTIFY'` 条件（PULL 模式应用不再需要参与候选匹配），更新类/XML 头部注释说明语义变化
- [x] 3.2 新增一个组织范围过滤组件（如 `NotifyCandidateResolver`，放在 `sync.notify` 或 `sync.event.support` 包下），把原 `AppDataChangeLogServiceImpl.filterByOrgScope` 的逻辑原样迁移过来（ORG/POSITION/USER 三个数据域各自的组织范围校验，APP/ROLE 不过滤）
- [x] 3.3 `DomainChangeEventProcessor.process(event)` 改造：不再调用变更记录服务，直接 ① 查候选应用（3.1）② 组织范围过滤（3.2）③ 对每个匹配应用调用 `AppNotifyService`，改造后的通知入口签名从"传入一条 `AppDataChangeLogEntity`"改为"传入 `DomainChangeEvent` + 目标应用 id"
- [x] 3.4 新增一个"按 dataType+bizId 现查业务编码字段"的小方法（复用 `BizSnapshotResolver` 现有的按 id 现查 dispatch 逻辑，取其 `code` 属性；POSITION 数据域没有 `code` 字段，返回 `null`），供通知 payload 使用
- [x] 3.5 `NotifyPayload`：去掉 `sequence` 字段，新增 `bizCode` 字段（可为空）
- [x] 3.6 `AppNotifyService`/`AppNotifyServiceImpl`：改造方法签名与实现，从 `DomainChangeEvent` + 目标应用直接构建通知（不再依赖变更记录实体），`occurredAt` 取事件本身的发生时间，`operationType` 取事件本身携带的值（无需改动取值逻辑本身）
- [x] 3.7 `AppNotifyRecordEntity`：去掉 `changeLogId` 字段；`AppNotifyRecordConvert`/`AppNotifyRecordVO` 同步去掉该字段引用（`dataType`/`bizId` 已经是该表的独立字段，去掉 `changeLogId` 后仍能正常定位）

## 4. 新的分页拉取查询能力

- [x] 4.1 设计并新增一个业务表分页查询组件（如 `SyncBizPageQueryResolver`，与 `BizSnapshotResolver` 同级、职责相邻但功能不同：`BizSnapshotResolver` 是单 id 现查，这个是条件化分页查询），按 `dataType` dispatch 到组织/用户/任职/应用/角色五个数据域各自的分页查询方法
- [x] 4.2 新增对应的 MyBatis Mapper 方法/XML（放在 `backend/src/main/resources/mybatis/mapper/`，标准可移植 SQL，不用 MySQL 8.0+ 专属语法如窗口函数/CTE），每个数据域的查询需要支持：
  - `page`/`pageSize` 分页，`ORDER BY update_time ASC, id ASC`
  - `updateTimeFrom`/`updateTimeTo` 范围过滤（可选）
  - `ids` 列表过滤（可选）
  - `codes` 列表过滤（可选，POSITION 域无此过滤，见 4.3）
  - `mobile` 单值过滤（仅 USER 域，见 4.3）
  - 组织范围过滤下推到 SQL：ORG 域 `id IN (allowedOrgIds)`；POSITION 域 `org_id IN (allowedOrgIds)`；USER 域 `id IN (SELECT DISTINCT user_id FROM tab_user_position WHERE status <> -1000 AND org_id IN (allowedOrgIds))`；APP/ROLE 域不过滤（`allowedOrgIds` 为空/不限制时不加这个条件，与 `AppSyncOrgScopeResolver.resolveAllowedOrgIds` 返回 `Optional.empty()` 的语义对应）
- [x] 4.3 控制器/服务层参数校验：`codes` 参数在 `dataType=POSITION` 时忽略（不报错）；`mobile` 参数在 `dataType != USER` 时忽略（不报错）——具体在 Service 层还是 Mapper 层做这个忽略逻辑，由实现时判断代码整洁度决定，但不能报业务错误
- [x] 4.4 `pageSize` 未传或非正数时，回退到该应用该数据域配置的 `AppSyncDomainConfigEntity.pageSize`；`page` 未传时默认 1

## 5. 拉取接口/DTO 改造

- [x] 5.1 `SyncPullService`/`SyncPullServiceImpl`：把 `pullByBizIds`/`pullBySequence` 两个方法合并为一个统一的分页拉取方法（如 `pull(SyncPullRequest request)`），内部：校验 `dataType` 合法（`SyncDomain.CHANGE_LOG_DOMAINS` 建议重命名为语义更贴切的常量名，如 `SYNC_PULL_DOMAINS`，同步更新引用处）→ 校验总开关/数据域启用（沿用现有 `isSyncMasterEnabled`/数据域 `syncEnabled` 判断逻辑）→ 调用 4.1 的分页查询组件（附带组织范围过滤）→ 结果按字段映射转换（`FieldMappingTransformer`，不变）→ 组装 `SyncPullRecordVO` 列表
- [x] 5.2 新增请求参数载体（如 `SyncPullRequest` DTO：`dataType`/`page`/`pageSize`/`updateTimeFrom`/`updateTimeTo`/`ids`/`codes`/`mobile`），Controller 用 `@ModelAttribute` 或等价方式绑定 GET 查询参数
- [x] 5.3（初版，已被 5.5 取代）~~`SyncPullRecordVO`：去掉 `sequence`/`operationType` 字段，新增 `bizCode`（可为空）、`updateTime`，保留 `dataType`/`bizId`/`data`~~
- [x] 5.4 `SyncPullController`（或 `SyncNotifyPullController`）：删除 `GET /open/api/sync/pull/by-id`、`GET /open/api/sync/pull/by-sequence` 两个方法，新增 `GET /open/api/sync/pull`，补充 springdoc-openapi 注解（`@Operation`/`@Parameter` 描述新参数含义）
- [x] 5.5【实现后修正】响应结构改为整页对象（新建如 `SyncPullPageVO`：`dataType`/`page`/`pageSize`/`records`），废弃按记录展开的 `SyncPullRecordVO`；`records` 元素类型改为 `Map<String, Object>`（或等价的动态结构），每个元素是字段映射转换后的业务字段，额外合并 `bizId`/`bizCode`/`updateTime` 三个固定键（`bizCode` 为空时键仍保留，值为 `null`），不再单独携带 `dataType`（见 design.md Decision 2 修订版、`specs/app-sync-notify-pull/spec.md` 对应 Scenario）
- [x] 5.6【实现后修正】`SyncPullService.pull(...)` 返回类型从 `List<SyncPullRecordVO>` 改为 `SyncPullPageVO`（或等价整页对象类型），`page`/`pageSize` 取本次请求实际生效的值（含默认值回退后的最终值）一并放入响应
- [x] 5.7【实现后修正】相应调整 `SyncNotifyPullController.pull(...)` 返回类型、`AppPullRecordServiceImpl`/`recordPull` 里"返回记录条数"的取值来源（从整页对象的 `records.size()` 取，不受结构调整影响）
- [x] 5.8【二次实现后修正】`SyncBizPageRow` 新增 `status`（或等价命名）字段，`SyncBizPageQueryResolver` 五个 `toRow(entity)` 私有方法都补上 `.status(entity.getStatus())`（五个实体都有 `status` 字段，直接取值即可，不需要改动查询 SQL——SQL 本来就是 `SELECT *`，实体本来就能拿到这个值，只是转换到 `SyncBizPageRow` 时之前没带上）
- [x] 5.9【二次实现后修正】`SyncPullServiceImpl` 组装每条记录时，在原有 `bizId`/`bizCode`/`updateTime` 三个固定键之后，追加第四个固定键 `bizStatus`（取值即 `SyncBizPageRow.getStatus()`），同样保证"固定键不被字段映射结果覆盖"（沿用 5.5 已经确立的合并顺序，把 `bizStatus` 一并放进"最后写入、不可覆盖"的那一步，不要在字段映射结果之前写入导致被覆盖）
- [x] 5.10【二次实现后修正】`SyncPullPageVO`/`toRecord(...)`（或实际类名/方法名，以当前代码为准）的 Javadoc 补充说明 `bizStatus` 的存在意义（配置了字段映射时原始 `status` 字段可能不在输出里，`bizStatus` 保证这项信息始终可得，不受字段映射配置影响）

## 6. 拉取日志改造

- [x] 6.1 `AppPullRecordEntity`：去掉 `pullMode` 字段
- [x] 6.2 `AppPullRecordService`/`AppPullRecordServiceImpl`/相关 DTO（`AppPullRecordVO`）：去掉 `pullMode` 相关的方法参数与字段
- [x] 6.3 `SyncPullServiceImpl` 调用 `recordPull` 时，`requestSummary` 内容改为反映新参数（页码、每页大小，以及传入的过滤条件摘要）

## 7. 前端改造

- [x] 7.1 `frontend/src/types/app.ts`：`AppPullRecordRow` 去掉 `pullMode` 字段，去掉 `PULL_MODE_LABELS` 常量及其类型
- [x] 7.2 `frontend/src/views/application/app/AppConfigView.vue`："拉取日志"子 tab 表格去掉"拉取方式"列（`<el-table-column label="拉取方式" ...>`），确认列宽/布局微调后视觉正常
- [x] 7.3 检查 `frontend/src/api/app.ts` 中 `getAppPullRecordPage` 等管理端查日志接口的请求/响应类型是否引用了 `pullMode`，同步移除

## 8. 测试

- [x] 8.1 `DomainChangeEventProcessorTest`：改造为验证"直接判定候选应用并触发通知"，不再验证变更记录落库
- [x] 8.2 `AppNotifyServiceImplTest`：改造为验证新的方法签名（`DomainChangeEvent` + 应用 → 通知），验证 `NotifyPayload` 携带 `bizCode`、不携带 `sequence`
- [x] 8.3 `SyncPullServiceImplTest`：改造为验证新的统一分页拉取方法——分页/排序、`updateTimeFrom`/`updateTimeTo` 过滤、`ids`/`codes`/`mobile` 过滤、组织范围下推过滤、总开关/数据域未开通返回空、停用/已删除记录仍返回、最后一页返回空列表
- [x] 8.4 新增分页查询组件（4.1/4.2）的单元测试，覆盖五个数据域各自的过滤条件组合
- [x] 8.5（初版）`./gradlew test`（在 `backend/` 目录下）通过
- [x] 8.6【实现后修正】`SyncPullServiceImplTest`/相关测试改为断言新的整页响应结构：顶层 `dataType`/`page`/`pageSize` 正确回显（含默认值回退场景），`records` 每个元素包含合并后的 `bizId`/`bizCode`/`updateTime`、不包含 `dataType`
- [x] 8.7【实现后修正】改动完成后重新运行 `./gradlew test`（在 `backend/` 目录下）确认通过
- [x] 8.8【二次实现后修正】新增/调整测试覆盖：① 未配置字段映射时记录里包含 `bizStatus`，值与业务表当前状态一致；② 配置了字段映射（映射结果不含 `status`）时记录里仍然包含 `bizStatus`；③ 停用/已删除记录的 `bizStatus` 分别反映 3000/-1000
- [x] 8.9【二次实现后修正】改动完成后重新运行 `./gradlew test`（在 `backend/` 目录下）确认通过

## 9. 文档

- [x] 9.1 检查 `权限资源.txt` 是否有需要更新的描述（预期不需要——本次改动不涉及权限点变化，只是拉取接口本身是对外开放接口，不受管理端权限点控制）
- [x] 9.2 `npm run build`（在 `frontend/` 目录下）通过

## 10. 新增字典拉取域与任职关联用户编码（三次实现后修正）

- [x] 10.1 `SyncDomain`：`SYNC_PULL_DOMAINS` 加入 `DICT`；确认 `ORG_SCOPE_DOMAINS`、`FIELD_MAPPING_DOMAINS` 保持不变（均不含 `DICT`）
- [x] 10.2 `SyncBizPageRow` 新增两个可选字段：`userCode`（仅 POSITION 使用）、`dictTypeCode`（仅 DICT 使用），其余数据域该字段恒为 `null`
- [x] 10.3 `SyncBizPageQueryResolver`：
  - `toRow(UserPositionEntity)` 改造为批量处理：查询到一页 `UserPositionEntity` 后，收集本页 `userId` 去重集合，一次 `UserMapper.selectByIds(...)` 批量查出 `userId -> code`，逐行回填到 `SyncBizPageRow.userCode`（不逐行单独查询，避免 N+1；改用 `selectByIds` 而非 `selectBatchIds`，后者在当前 MyBatis-Plus 版本已标记 `@Deprecated`，仓库既有代码统一用 `selectByIds`）
  - 新增 `dataType=DICT` 分支：注入 `DictItemMapper`/`DictTypeMapper`，新增 `toRow(DictItemEntity, Map<Long,String> dictTypeCodeById)` 分支，`code` 取 `DictItemEntity.getCode()`、`dictTypeCode` 从批量查询的 `dictTypeId -> code` 映射回填（同样批量查，不逐行查询）；`DICT` 不传 `allowedOrgIds`（字典无组织范围概念，与 APP/ROLE 一致）
- [x] 10.4 新增 `DictItemMapper.selectSyncPullPage(...)` 方法 + `backend/src/main/resources/mybatis/mapper/DictItemMapper.xml`：标准可移植 SQL，`SELECT * FROM tab_dict_item`，支持 `updateTimeFrom`/`updateTimeTo`/`ids`/`codes`（按 `code` 过滤）过滤，`ORDER BY update_time ASC, id ASC`，`LIMIT #{offset}, #{limit}`，不按 `status` 过滤（与其余数据域一致）；`DictTypeMapper` 未新增自定义方法，直接复用 MyBatis-Plus `BaseMapper.selectByIds`
- [x] 10.5 `SyncPullServiceImpl` 组装每条记录（`toRecord`）：在现有 `bizId`/`bizCode`/`bizStatus`/`updateTime` 四个通用固定键之后，追加条件写入——`row.getUserCode() != null` 时写入 `userCode`；`row.getDictTypeCode() != null` 时写入 `dictTypeCode`；两者都遵循"最后写入、不被字段映射结果覆盖"的规则，且仅在值非空时才写入这个键（不像四个通用固定键那样恒定出现，即使为 null 也要出现——`userCode`/`dictTypeCode` 是"不相关数据域完全不出现这个键"）
- [x] 10.6 `SyncNotifyPullController`：`@Parameter(description = "数据类型：ORG/USER/POSITION/APP/ROLE")` 之类的描述文案补充 `DICT`；`codes` 参数的 Javadoc/`@Parameter` 描述已补充提及"字典数据类型按字典项自身编码过滤（不是字典类型编码）"；`@Operation` 描述同步补充字典域说明
- [x] 10.7 `SyncPullPageVO`（类级/字段级 Javadoc 与 `@Schema`）、`SyncPullServiceImpl#toRecord` 的 Javadoc 已补充说明 `userCode`/`dictTypeCode` 这两个领域特定固定键的存在意义与出现范围（仅对应数据域出现，其余数据域不出现该键，区别于四个恒定出现的通用固定键）
- [x] 10.8 测试：`SyncBizPageQueryResolverTest` 新增 DICT 分支的分页/过滤测试（含同一 `code` 在不同 `dictTypeId` 下均返回、各自 `dictTypeCode` 不同的场景，且验证 `DictTypeMapper.selectByIds` 只调用一次）；POSITION 分支新增 `userCode` 批量回填的测试（含多条记录关联同一用户时只查询一次 `UserMapper.selectByIds` 的验证，避免 N+1 回归；原有未关联 `userId` 场景补充断言 `userCode` 为 `null` 且不触发批量查询）；`SyncPullServiceImplTest` 新增：请求 `dataType=DICT` 时能正常拉取并返回 `dictTypeCode`、组织数据域记录不包含 `dictTypeCode`/`userCode` 键；原 `pull_shouldRejectInvalidDataType` 用例的非法数据类型由 `"DICT"` 改为 `"NOT_A_DOMAIN"`（`DICT` 现已合法）
- [x] 10.9 改动完成后重新运行 `./gradlew test`（在 `backend/` 目录下）确认通过（全部通过）

## 11. 响应顶层新增 dataSize/latestUpdateTime（四次实现后修正）

- [x] 11.1 `SyncPullPageVO` 新增两个字段：`dataSize`（Integer，本页 `records` 实际条数）、`latestUpdateTime`（LocalDateTime，本页记录最大更新时间，`records` 为空时为 `null`），与 `dataType`/`page`/`pageSize` 同级，`@Schema` 补充描述
- [x] 11.2 `SyncPullServiceImpl`：组装响应时计算这两个字段——`records` 本来就已按 `updateTime ASC, id ASC` 排序，`latestUpdateTime` 直接取排序后最后一条记录（合并固定键之前的 `SyncBizPageRow.updateTime`，不是转换后 Map 里的值，避免依赖 Map 取值顺序）的更新时间；`records` 为空列表时 `latestUpdateTime` 为 `null`；`dataSize` 取 `records.size()`
- [x] 11.3 `SyncNotifyPullController`：`@Operation`/返回值 Javadoc 补充这两个新字段的说明
- [x] 11.4 测试：`SyncPullServiceImplTest` 新增/调整断言——多条记录时 `dataSize`/`latestUpdateTime` 正确；`records` 为空（最后一页/未开通/总开关关闭等场景）时 `dataSize` 为 0、`latestUpdateTime` 为 `null`
- [x] 11.5 改动完成后重新运行 `./gradlew test`（在 `backend/` 目录下）确认通过

## 12. 任职拉取数据补充 orgCode（五次实现后修正）

- [x] 12.1 `SyncBizPageRow` 新增可选字段 `orgCode`（仅 POSITION 使用，其余数据域恒为 `null`），与已有的 `userCode` 并列
- [x] 12.2 `SyncBizPageQueryResolver`：POSITION 分支在现有批量回填 `userCode` 的基础上，同时收集本页 `orgId` 去重集合，一次 `OrgMapper.selectByIds(...)`（`OrgMapper` 已经是该组件处理 ORG 数据域时注入的既有依赖，不需要新增字段/构造参数）批量查出 `orgId -> code` 映射，逐行回填到 `SyncBizPageRow.orgCode`（不逐行单独查询，避免 N+1，写法与 `userCode`/`dictTypeCode` 的批量回填模式保持一致）
- [x] 12.3 `SyncPullServiceImpl` 组装每条记录：在现有 `userCode`/`dictTypeCode` 条件写入逻辑旁，追加 `row.getOrgCode() != null` 时写入 `record.put("orgCode", ...)`，同样遵循"最后写入、不被字段映射结果覆盖"规则，仅在值非空时才写入这个键（不像四个通用固定键那样恒定出现）
- [x] 12.4 `SyncPullPageVO`（类级/字段级 Javadoc 与 `@Schema`）、`SyncNotifyPullController`（`@Operation`/相关 Javadoc）补充 `orgCode` 的说明——仅任职数据类型的记录出现，其余数据域不出现该键
- [x] 12.5 测试：`SyncBizPageQueryResolverTest` 的 POSITION 批量回填测试扩展为同时验证 `orgCode`（含多条记录关联同一组织时只查询一次 `OrgMapper.selectByIds` 的验证，避免 N+1 回归；未关联 `orgId` 场景——理论上不会发生，`orgId` 是 `tab_user_position` 必填字段，不需要专门覆盖）；`SyncPullServiceImplTest` 新增：请求 `dataType=POSITION` 时记录同时包含 `userCode` 与 `orgCode`，其余数据域记录不包含 `orgCode` 键
- [x] 12.6 改动完成后重新运行 `./gradlew test`（在 `backend/` 目录下）确认通过

## 13. OpenSpec 收尾

- [x] 13.1 实现完成后运行 `openspec-doc-sync` 对齐 `proposal.md`/`design.md`/`tasks.md` 与实际改动
- [x] 13.2 视用户指示决定是否执行 `openspec-sync-specs` 把本变更的 delta spec 应用到 `openspec/specs/app-sync-notify-pull/spec.md`、`openspec/specs/app-api-credentials/spec.md`——2026-08-28 归档前核对，主 spec 已通过后续功能提交手动同步到位（REMOVED 的三个变更记录相关需求已从主 spec 移除，ADDED/MODIFIED 的分页拉取需求已体现），用户确认无需再跑自动合并，直接按已同步状态归档
