## 1. 数据库迁移

- [x] 1.1 新增 Flyway 增量迁移：`tab_app_data_change_log` 加列 `app_ref_id BIGINT NOT NULL`
- [x] 1.2 同一迁移：`TRUNCATE tab_app_notify_record`，再 `TRUNCATE tab_app_data_change_log`
      （存量数据无法回填应用归属，见 design.md Decision 8）
- [x] 1.3 同一迁移：`tab_app_data_change_log` 新增索引 `(app_ref_id, data_type, id)`
- [x] 1.4 同一迁移：新建 `tab_app_sync_org_scope`（`id`/`app_ref_id`/`sync_domain`/`org_id`/
      `include_children`/四个审计字段），核对表名列名不与 MySQL/PostgreSQL/Oracle/SQL
      Server 保留字冲突
- [x] 1.5 本地针对真实 MySQL 5.7 库跑一次迁移，确认无语法错误

## 2. `tab_app_sync_org_scope` 实体/Mapper

- [x] 2.1 新增 `cn.nihility.rbac.app.sync.entity.AppSyncOrgScopeEntity`
- [x] 2.2 新增 `cn.nihility.rbac.app.sync.mapper.AppSyncOrgScopeMapper`（`BaseMapper`，
      整体替换用 `delete`+批量 `insert`，参照 `AppSyncFieldMappingMapper`/
      `AdminOrgScopeMapper` 现有写法）

## 3. `AppSyncOrgScopeResolver`

- [x] 3.1 新增 `cn.nihility.rbac.sync.scope.AppSyncOrgScopeResolver`：
      `Optional<Set<Long>> resolveAllowedOrgIds(Long appRefId, String syncDomain)`，
      复用 `OrgDescendantExpander`，语义与 `OrgScopeService.resolveAllowedOrgIds` 对齐
- [x] 3.2 新增 `boolean isOrgIdWithinScope(Long appRefId, String syncDomain, Long orgId)`
- [x] 3.3 新增 `boolean isUserWithinScope(Long appRefId, Long userId)`：查
      `tab_user_position`（`status <> DELETED`），命中任一 `orgId` 落在允许集合内即为 true
      （design.md Decision 3）
- [x] 3.4 单元测试：全部数据（零行配置）恒为 true；指定范围命中/不命中；
      `include_children` 展开子孙；用户多任职场景下任一命中即为 true

## 4. 应用同步配置服务新增组织范围管理

- [x] 4.1 `AppSyncConfigService`/`AppSyncConfigServiceImpl` 新增
      `listOrgScope(appRefId, syncDomain)`/`replaceOrgScope(appRefId, syncDomain, List<...>)`，
      仅允许 `ORG`/`USER`/`POSITION` 三个数据域调用，其余数据域调用直接拒绝并返回业务错误
      （参照现有 `replaceFieldMappings` 的整体替换语义与 `FIELD_MAPPING_DOMAINS` 校验风格，
      新增 `SyncDomain.ORG_SCOPE_DOMAINS = {ORG, USER, POSITION}` 常量）
- [x] 4.2 `AppSyncConfigController` 新增
      `GET /api/apps/{id}/config/sync/domains/{syncDomain}/org-scope`、
      `PUT /api/apps/{id}/config/sync/domains/{syncDomain}/org-scope` 两个接口 +
      OpenAPI 注解
- [x] 4.3 单元测试：非法数据域（APP/ROLE/DICT）调用被拒绝；整体替换语义正确（先删后插）

## 5. 变更记录落库改为按应用物化

- [x] 5.1 候选应用查询：改造/新增 Mapper 方法，去掉原 `NotifyTargetMapper` 里的
      `sync_mode='NOTIFY'` 条件，返回该数据域已启用同步的候选应用 id 列表
      （design.md Decision 5）
- [x] 5.2 `AppDataChangeLogEntity` 新增 `appRefId` 字段（映射 `app_ref_id` 列）
- [x] 5.3 `AppDataChangeLogMapper.xml`/接口：`insert` 走 `BaseMapper` 自动带上新字段
      （无需改动 XML，MyBatis-Plus 按实体字段自动映射）；`selectLatestByBizIds`/
      `selectBySequence` 新增 `appRefId` 参数与 `AND app_ref_id = #{appRefId}` 条件
      （design.md Decision 7）
- [x] 5.4 `AppDataChangeLogService#record` 签名改为返回 `List<AppDataChangeLogEntity>`：
      查候选应用 → ORG/USER/POSITION 域按 `AppSyncOrgScopeResolver` 过滤 → 逐个候选应用
      落库一条记录 → 返回全部记录（design.md Decision 1/4）
- [x] 5.5 单元测试：候选应用为空时不落库任何记录；ORG/USER/POSITION 按范围过滤掉不匹配
      应用；APP/ROLE 不做范围过滤，候选应用全部落库

## 6. 通知发送逻辑简化

- [x] 6.1 `AppNotifyService` 接口从 `notifyMatchedApps(changeLog)` 改为
      `notifyIfConfigured(changeLog)`（design.md Decision 6）
- [x] 6.2 `AppNotifyServiceImpl` 按 `changeLog.getAppRefId()` 查该应用当前
      `AppConfigEntity`，`syncMode != NOTIFY` 时直接返回，否则复用现有签名/发送/落库逻辑
- [x] 6.3 `DomainChangeEventProcessor#process` 改为遍历 `record(event)` 返回的记录列表，
      对每条记录调用 `notifyIfConfigured`，单条异常不影响其余记录（沿用既有 catch 风格）
- [x] 6.4 更新/迁移现有 `AppNotifyServiceImplTest`/`DomainChangeEventProcessorTest` 用例
      适配新签名

## 7. 拉取接口按应用过滤

- [x] 7.1 `SyncPullServiceImpl#pullByBizIds`/`#pullBySequence` 调用
      `AppDataChangeLogService` 查询时传入 `OpenApiCallerContext` 里的 `appRefId`
- [x] 7.2 更新 `SyncPullServiceImplTest`/`AppDataChangeLogServiceImplTest` 适配新参数

## 8. 前端

- [x] 8.1 `types/app.ts` 新增组织范围相关类型（`AppSyncOrgScopeRow`/请求 DTO 等，参照
      `frontend/src/types/admin.ts` 里 `AdminOrgScopeRow`/`AdminOrgScopeFormItem` 的现有形状）
- [x] 8.2 `api/app.ts` 新增 `listAppSyncOrgScope`/`replaceAppSyncOrgScope`
- [x] 8.3 `AppConfigView.vue`：组织/用户/任职三个数据域 tab 内新增"同步范围"设置区块——
      单选"全部数据"/"指定组织范围"，选中后者时展示可增删的组织行列表（复用
      `AdminManagementView.vue` 里"管辖组织范围"的组织树选择 + "含子组织"勾选交互），
      独立保存入口；应用/角色/字典三个 tab 不展示该区块
- [x] 8.4 前端校验：切到"指定组织范围"但未添加任何组织时阻止保存并提示

## 9. 权限资源与收尾

- [x] 9.1 核对 `权限资源.txt`：不新增权限点，更新 `AppManagement:app:config:editSync`
      功能描述文字，提及新增的组织/用户/任职同步范围配置
- [x] 9.2 后端 `./gradlew build` 全量跑通
- [x] 9.3 前端 `npm run build` 类型检查通过
- [x] 9.4 本地针对运行中的后端服务手工验证：配置应用"同步应用"（id=1）"用户"域为
      "指定组织范围"（仅组织1），分别新建一个任职于组织1（范围内）与组织2（范围外）的
      用户，确认只有范围内用户产生了归属该应用的变更记录、且能通过 `pull/by-sequence`
      拉取到；范围外用户完全不产生该应用的变更记录。验证过程中发现并修复了两个问题
      （均已修好、已重新验证通过，测试数据已清理）：
      1. `DisruptorDomainEventPublisher#publish` 若在活动事务内被调用，实际投递到
         RingBuffer 的动作需要延迟到事务 `afterCommit` 才执行，否则 Disruptor 消费者线程
         可能在发布方事务提交前就查询 `tab_user_position` 等业务表做组织范围判定，读不到
         同一事务里刚写入但未提交的数据，产生假阴性（新建用户时一条变更记录都不落库）。
         已修复（该类新增 `TransactionSynchronizationManager` 判定与延迟发布逻辑）并补充
         单元测试 `DisruptorDomainEventPublisherTest`。
      2. 曾短暂尝试把 `tab_app_data_change_log.app_ref_id` 改名为 `app_id`，后确认不改，
         已完整回退（迁移文件、实体、Mapper XML 均恢复为 `app_ref_id`），不影响最终交付。
- [x] 9.5 实现完成后按 `openspec-doc-sync` 约定核对 `proposal.md`/`design.md`/`tasks.md`
      与实际 diff/测试结果是否一致（补充 design.md Decision 9：`DisruptorDomainEventPublisher`
      事务提交后延迟发布的修复；补充 Decision 5 关于 `NotifyTargetRow` 被删除、改为直接查
      `AppConfigMapper` 的实际实现细节）
