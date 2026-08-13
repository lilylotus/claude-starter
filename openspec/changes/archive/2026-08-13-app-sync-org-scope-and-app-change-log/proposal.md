## Why

`app-sync-notify-pull-api`/`fix-app-sync-pull-live-data` 两个 change 落地的变更记录表
`tab_app_data_change_log` 是全局共享的：一次组织/用户/任职/应用/角色的写操作只产生**一条**
记录，任何"该数据域启用同步"的应用调用拉取接口都能读到**同一条**记录，记录本身不关联
任何具体应用。这带来两个问题：

1. **拉取接口无法按应用维度关联数据**：记录表缺少应用 id 列，`SyncPullServiceImpl` 只能
   按"调用方应用是否启用了该数据域"这个粗粒度开关过滤，无法表达"这条应用 A 能看，那条
   应用 B 不能看"这种更细的按应用关联；序列号游标（`fromSequence`）语义上也应该是"应用
   自己见过的最大序列号"，而不是"全局共享的序列号里应用恰好能看到的那些"。
2. **组织/用户/任职三个数据域没有同步范围限制**：目前只有"该数据域是否允许同步"这个
   应用级总开关，一旦对组织域打开同步，该应用能拿到**全部**组织（连带用户、任职）的
   变更/当前数据，无法像后台管理员的"管辖组织范围"（`org-scope-data-permission`）那样
   限定某个应用只能同步指定组织（及可选的子孙组织）范围内的数据。应用/角色两个数据域
   不涉及"归属组织"的概念，不需要此限制。

## What Changes

- `tab_app_data_change_log` 新增 `app_ref_id` 列：变更记录从"全局一条"改为"按目标应用
  各自一条"——一次数据变更事件落库时，系统解析出当前"该数据域已启用同步的应用"集合，
  对组织/用户/任职三个数据域再按每个应用配置的同步范围过滤一遍，为每个最终匹配的应用
  各插入一条变更记录（`id` 全局自增，天然对每个应用而言仍是单调递增的私有序列号）。
- 新增应用同步范围配置：组织（`ORG`）、用户（`USER`）、任职（`POSITION`）三个数据域
  各自新增"同步范围"设置，二选一——"全部数据"（默认）或"指定组织范围"（可勾选多个
  组织，每个组织可选是否连同子孙组织）；应用（`APP`）、角色（`ROLE`）、字典（`DICT`）
  三个数据域不提供该设置。范围解析算法复用既有 `OrgDescendantExpander`（组织子孙展开
  工具组件），与后台管理员"管辖组织范围"完全对齐的展开语义，但物理上是两张独立的表
  （按应用维度 vs. 按管理员维度），互不影响。
- 用户（`USER`）数据域的组织归属通过 `tab_user_position`（任职关系）间接判断：一个用户
  只要存在至少一条未删除的任职记录、其 `orgId` 落在应用允许的组织集合内，即视为该用户
  在该应用的同步范围内。
- 通知发送逻辑简化：由于变更记录落库时已经完成"哪些应用该收到这条记录"的判定（即
  `app_ref_id` 已经是具体某个应用），原先"落库后再查一遍匹配的通知目标应用列表"的
  `AppNotifyService#notifyMatchedApps` 改为对已经确定好目标应用的单条记录直接判断
  该应用当前 `syncMode` 是否为 `NOTIFY`，是则发起一次通知，不再需要联表查询"匹配应用
  列表"。
- 拉取接口（`pull/by-id`、`pull/by-sequence`）查询变更记录时新增 `app_ref_id = 调用方
  应用 id` 过滤条件。
- **BREAKING**：`tab_app_data_change_log` 现存数据在迁移时清空重建（新增 `app_ref_id`
  为 `NOT NULL` 列，存量数据没有可回填的应用归属信息，无法安全迁移）；已经开始拉取的
  外部应用需要重新从序列号 0 开始拉取。给定这是刚落地不久、尚无真实外部接入方的能力，
  影响面很小。

## Capabilities

### Modified Capabilities
- `app-sync-notify-pull`：变更记录表新增应用维度关联，通知/拉取的"哪些应用能看到这条
  记录"判定时机从"发送/拉取时再查询"提前到"变更落库时一次性判定并物化"；组织/用户/
  任职三个数据域新增按应用配置的组织范围过滤。
- `app-api-credentials`：新增组织/用户/任职三个数据域的"同步范围"配置（全部数据/指定
  组织范围）的查询、整体替换接口与管理端页面。

## Impact

- 后端 · 新增：
  - `cn.nihility.rbac.app.sync.entity.AppSyncOrgScopeEntity` / `mapper.AppSyncOrgScopeMapper`
    （`tab_app_sync_org_scope`，字段对齐 `AdminOrgScopeEntity`：`appRefId`/`syncDomain`/
    `orgId`/`includeChildren` + 审计字段）。
  - `cn.nihility.rbac.sync.scope.AppSyncOrgScopeResolver`：按 `(appRefId, syncDomain)`
    解析允许的组织 id 集合（`Optional<Set<Long>>`，空集合含义与 `OrgScopeService` 一致
    ——`Optional.empty()` 表示不限制），复用 `OrgDescendantExpander`；另提供
    "给定用户 id，是否落在允许组织集合内"的判断（联查 `tab_user_position`）。
  - `cn.nihility.rbac.sync.notify.mapper` 下新增/调整候选应用查询：不再区分"通知目标"，
    改为"该数据域已启用同步的候选应用"查询（去掉原 `sync_mode='NOTIFY'` 过滤条件），
    供变更落库时判定"物化给哪些应用"使用。
- 后端 · 修改：
  - `AppDataChangeLogEntity`/`AppDataChangeLogMapper`/`AppDataChangeLogMapper.xml`：
    新增 `appRefId` 字段/列，查询新增按应用过滤。
  - `AppDataChangeLogService#record`：签名从"落库一条记录，返回该记录"改为"按候选应用
    +组织范围过滤后，为每个匹配应用各落库一条记录，返回记录列表"。
  - `DomainChangeEventProcessor`：改为遍历 `record` 返回的记录列表，对每条记录按其
    `appRefId` 查出该应用当前 `syncMode`，为 `NOTIFY` 时调用通知。
  - `AppNotifyService`：`notifyMatchedApps(changeLog)` 改为单应用语义的方法（具体命名
    见 design.md）。
  - `SyncPullServiceImpl`：查询新增按调用方 `appRefId` 过滤。
  - `app.sync` 模块：`AppSyncConfigService`/`AppSyncConfigServiceImpl`/
    `AppSyncConfigController` 新增组织范围查询/整体替换的方法与接口。
  - `DisruptorDomainEventPublisher#publish`：改为事务感知，处于活动事务中时延迟到
    `afterCommit` 才真正发布，修复组织范围判定现查业务表时可能读到调用方事务里未提交
    数据而产生假阴性的问题（见 design.md Decision 9）——这是共享事件发布基础设施的通用
    修复，影响全部数据域，不只是本次新增的组织范围场景，但是本次落地验证时发现并顺带
    修复的。
- 数据库：新增 Flyway 增量迁移——`tab_app_data_change_log` 加列 `app_ref_id`（先清空
  存量数据）、新建 `tab_app_sync_org_scope` 表。
- 前端：`AppConfigView.vue` 组织/用户/任职三个数据域 tab 内新增"同步范围"设置区块
  （单选"全部数据"/"指定组织范围" + 指定时的组织多选列表，复用管理员管辖组织范围页面
  的组织树选择 + "含子组织"勾选交互）；`api/app.ts`、`types/app.ts` 新增对应方法/类型。
- 权限资源：复用既有 `AppManagement:app:config:editSync`，不新增权限点；同步更新
  `权限资源.txt` 的功能描述文字。
