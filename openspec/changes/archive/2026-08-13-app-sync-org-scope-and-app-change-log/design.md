## Context

`app-sync-notify-pull-api`（已归档）设计时明确把"拉取接口的管辖组织范围校验"列为
Non-Goal：「拉取接口的管辖组织范围校验（`org-scope-data-permission`）——拉取面向外部
应用而非后台管理员，鉴权只走 AccessKey + 签名」。当时的变更记录表 `tab_app_data_change_log`
因此设计成完全不关联任何应用维度的全局表：一次变更只产生一条记录，靠"应用是否启用某
数据域同步"这一个开关控制可见性。

现在产品侧明确要在组织/用户/任职三个数据域上补齐按应用的组织范围限制，且要求变更记录表
本身携带应用关联（而不是在查询时用运行时 JOIN 计算"这条记录该不该给这个应用看"）。这
意味着上述 Non-Goal 被推翻，需要重新设计变更记录的落库与查询路径。

现有可复用资产：
- `cn.nihility.rbac.auth.service.OrgScopeService` + `cn.nihility.rbac.org.support.OrgDescendantExpander`：
  管理员侧"管辖组织范围"的解析实现，`Optional<Set<Long>> resolveAllowedOrgIds(...)`——
  `Optional.empty()` 表示不限制，非空 `Set` 是已展开子孙的完整允许组织 id 集合。
  `OrgDescendantExpander.expandWithDescendants(Set<Long> rootOrgIds)` 是唯一依赖
  `OrgMapper` 的独立组件，刻意不放在 `OrgService` 里以避免循环 bean 依赖，可以被新组件
  直接复用。
- `cn.nihility.rbac.admin.entity.AdminOrgScopeEntity` / `tab_admin_org_scope`：管理员
  维度的范围行存储，`(adminId, orgId, includeChildren)`，"零行=不限制，≥1 行=受限"，
  没有单独的模式开关列。整体替换语义（先删后插）在 `AdminServiceImpl.syncOrgScopes`。
- `UserPositionEntity`（`tab_user_position`）：`orgId` 列是用户与组织的唯一关联路径
  （`UserEntity` 本身没有 `orgId`），`org-scope-data-permission` 的 spec 当时明确把
  用户列表排除在管辖范围过滤之外，理由正是"用户与组织是通过 `tab_user_position` 的
  间接关系，留待后续独立能力处理"——本次就是这个"后续能力"，但范围限定在"应用同步"
  场景，不改动管理员侧的用户列表过滤（那是另一个独立能力，不在本次范围内）。

## Goals / Non-Goals

**Goals:**
- `tab_app_data_change_log` 携带应用维度关联，拉取接口按调用方应用 id 过滤。
- 组织/用户/任职三个数据域支持按应用配置"全部数据"或"指定组织范围（可选含子孙）"。
- 复用既有组织子孙展开算法，不重新实现一套。

**Non-Goals:**
- 不改动管理员侧的管辖组织范围能力（`org-scope-data-permission`）本身，两套范围配置
  物理隔离（不同表、不同解析入口），只共享 `OrgDescendantExpander` 这一个纯计算组件。
- 不改动应用（`APP`）、角色（`ROLE`）、字典（`DICT`）三个数据域——它们没有"归属组织"
  的概念，继续沿用"数据域是否启用"这一个开关。
- 不做"用户任职变更后，历史上已经判定过归属的变更记录重新评估归属"的回溯——组织范围
  判定发生在变更事件产生的那一刻，用当时的任职关系快照判断，之后任职关系变化不会让
  已经落库的历史记录"追溯性"改变其所属应用（见 Risks）。
- 不引入应用同步范围的运行时缓存——与 `OrgScopeService` 现有"不缓存，实时查库"的约定
  保持一致（这是变更事件处理路径，不是高频只读查询路径，性能不是本次关注点）。

## Decisions

### 1. 变更记录"按应用物化"而不是"落库后运行时 JOIN 过滤"

**决定**：`AppDataChangeLogService#record(DomainChangeEvent event)` 从"落库一条记录、
返回该记录"改为：

```java
List<AppDataChangeLogEntity> record(DomainChangeEvent event);
```

内部流程：
1. 查询"该 `dataType` 已启用同步（`sync_enabled=1`）且应用当前启用（`tab_app.status=2000`）"
   的候选应用列表（复用/改造原 `NotifyTargetMapper` 的联表查询，去掉 `sync_mode='NOTIFY'`
   这个条件——候选集现在是"该数据域订阅了同步"的应用，不区分通知/拉取两种方式，两种
   方式的应用都需要落库记录，只是后续只有 `NOTIFY` 的会额外触发一次 HTTP 通知）。
2. 若 `event.getDataType()` 是 `ORG`/`USER`/`POSITION` 之一，对候选列表中的每个应用，
   调用 `AppSyncOrgScopeResolver` 判断这次变更的业务对象是否落在该应用配置的组织范围
   内，不落在范围内的应用从候选列表中剔除。`APP`/`ROLE` 数据域跳过这一步，候选列表
   全部保留。
3. 为剩余候选列表中的每个应用各构建一条 `AppDataChangeLogEntity`（`appRefId` 设为该
   应用 id，其余字段与事件内容相同）并插入，返回全部插入后的实体列表。

**理由**：这是用户明确要求的方向（"表缺少应用 id，拉取接口需要通过应用 id 关联"），
而不是运行时 JOIN 方案的原因：
- 拉取查询天然简化为 `WHERE app_ref_id = ? AND data_type = ? AND id > ?`，不需要在
  每次拉取请求里重新计算组织范围交集（尤其 `by-sequence` 批量场景，逐条记录做范围判定
  的开销会比写时一次性判定更高，因为写时只需要判定一次，读时可能被多个不同 `fromSequence`
  游标的应用反复读到同一批历史记录）。
- 通知发送不再需要"落库后再查一遍匹配应用"，落库时已经知道这条记录属于哪个应用，
  通知逻辑直接简化为"这个应用现在是 NOTIFY 模式吗"的单一判断。
- 序列号的"游标"语义对调用方应用而言更直观：`fromSequence` 就是"我上次拉到的这张表里
  属于我的最大 `id`"，不需要额外解释"为什么两个不同应用看到的同一个 `id>1000` 查询
  结果不一样"（运行时过滤方案里，同一条全局记录对不同应用可能"存在"或"不存在"，游标
  语义会变得含糊）。

**代价**：同一次业务变更，如果被 N 个应用订阅，会产生 N 条物理记录（而不是 1 条全局
记录 + 运行时过滤），`tab_app_data_change_log` 的行数与"订阅应用数"成正比而不是与
"变更次数"成正比。给定这张表的用途就是"供拉取接口使用"（不是通用审计日志，通用审计
走 `OperationLogRecorder`/`tab_operation_log`），且 `data_snapshot` 列本身在
`fix-app-sync-pull-live-data` change 之后已经不被拉取接口读取（拉取接口现查业务表），
只是历史遗留字段，行数膨胀不影响拉取接口本身的正确性，可接受。

### 2. 新表 `tab_app_sync_org_scope`，按 `(appRefId, syncDomain)` 而不是按
`domainConfigId` 关联

**决定**：新建 `tab_app_sync_org_scope`：

| 列 | 说明 |
| --- | --- |
| `id` | 主键，自增 |
| `app_ref_id` | 关联 `tab_app.id`（与 `tab_app_sync_domain_config` 同名列语义一致） |
| `sync_domain` | `ORG`/`USER`/`POSITION` 之一（应用层校验，不允许 `APP`/`ROLE`/`DICT`） |
| `org_id` | 关联 `tab_org.id` |
| `include_children` | 是否连同子孙组织 |
| 四个审计字段 | 同仓库惯例 |

按 `(app_ref_id, sync_domain)` 直接关联（而不是外键到 `tab_app_sync_domain_config.id`），
与 `tab_app_sync_domain_config` 自身"按 `(app_ref_id, sync_domain)` 定位一行"的既有
惯例保持一致，避免多一层间接引用。**模式判定沿用 `tab_admin_org_scope` 的既有约定**：
某个 `(appRefId, syncDomain)` 组合零行 = "全部数据"，≥1 行 = "指定组织范围"，不额外
增加模式开关列——`AppSyncOrgScopeResolver` 的返回类型直接是 `Optional<Set<Long>>`，
语义与 `OrgScopeService.resolveAllowedOrgIds` 完全对齐。

**前端仍然展示一个显式的"全部数据/指定组织范围"单选**（而不是像管理员管辖范围页面
那样只用"列表是否为空"隐式表达），因为这里控制的是外部应用能拿到多少数据、误操作
代价更高，显式单选降低"不小心清空列表变成不限制"的风险；单选切到"指定组织范围"但
未添加任何组织时，保存前端校验拦截（至少 1 条），避免用户以为选了"指定范围"实际
保存后变成"全部数据"的语义落差。

### 3. `USER` 数据域的组织归属：存在任一落在范围内的任职记录即算命中

**决定**：`AppSyncOrgScopeResolver` 新增方法：

```java
boolean isUserWithinScope(Long appRefId, Long userId);
```

实现：先 `resolveAllowedOrgIds(appRefId, SyncDomain.USER)`，`Optional.empty()`（不限制）
直接返回 `true`；否则查询 `tab_user_position WHERE user_id = ? AND status <> DELETED`，
只要存在至少一条记录的 `org_id` 落在允许集合内就返回 `true`。

**理由**：一个用户可以同时在多个组织任职（`tab_user_position` 一对多），"只要有一个
任职落在应用允许范围内就该应用可见"是最贴近直觉的语义（类比"这个人只要在我管的部门
里挂过职，我就该看得到他"），而不是要求"用户的所有任职都落在范围内"（这个更严格的
语义会导致一个同时在范围内、范围外两个组织任职的用户对任何应用都不可见，不符合
"组织范围是准入条件而不是排除条件"的直觉）。

### 4. `ORG`/`POSITION` 数据域的组织归属判定

**决定**：
- `ORG` 数据域：变更事件的 `bizId` 就是组织自身 id，直接判断 `bizId` 是否落在
  `resolveAllowedOrgIds(appRefId, SyncDomain.ORG)` 返回的集合内。
- `POSITION` 数据域：变更事件的 `bizId` 是 `tab_user_position.id`，需要先查出该行的
  `orgId`（`UserPositionMapper.selectById(bizId).getOrgId()`），再判断该 `orgId` 是否
  落在 `resolveAllowedOrgIds(appRefId, SyncDomain.POSITION)` 返回的集合内；若该任职
  记录已经查不到（理论上不会发生，逻辑删除不会物理消失），保守处理为不匹配（不落库
  给该应用），与 `BizSnapshotResolver` 现有"查不到就跳过"的防御性风格一致。

三个数据域（含 `USER`）各自独立配置组织范围，互不共享——同一个应用完全可以给"组织"
配全部数据、给"用户"配指定范围。

### 5. `AppDataChangeLogService#record` 内部候选应用查询：复用并改造原
`NotifyTargetMapper`

**决定**：原 `NotifyTargetMapper.selectNotifyTargets(dataType)`（`WHERE sync_mode='NOTIFY'
AND sync_domain=#{dataType} AND sync_enabled=1 AND app.status=2000`）改造为新的候选
应用查询（去掉 `sync_mode='NOTIFY'` 条件，返回内容也从"通知所需字段"精简为"落库变更
记录所需的最小信息"：仅 `appRefId`）：

```sql
SELECT DISTINCT c.app_id AS appRefId
FROM tab_app_config c
INNER JOIN tab_app a ON a.id = c.app_id
INNER JOIN tab_app_sync_domain_config d ON d.app_ref_id = c.app_id
WHERE d.sync_domain = #{dataType}
AND d.sync_enabled = 1
AND a.status = 2000
```

原来专供通知使用的"通知发送所需字段"（`accessKey`/`secretKey`/`signAlgorithm`/
`needSign`/`notifyUrl`/`notifyParams`）查询——实现时没有保留 `NotifyTargetMapper` 上
原本承载这些字段的第二个查询方法，而是删除了对应的 `NotifyTargetRow` DTO，改为
`AppNotifyServiceImpl#notifyIfConfigured` 直接注入 `AppConfigMapper`、按
`changeLog.getAppRefId()` 用 `LambdaQueryWrapper` 单条查询 `AppConfigEntity`（该实体
本身已包含全部通知所需字段，不需要再单独定义一个只服务通知场景的行 DTO）。调用时机
后移到 `DomainChangeEventProcessor` 拿到某条已落库记录的 `appRefId` 后，`syncMode`
不是 `NOTIFY` 时直接返回，不再对"候选应用集合"整体查询这些通知专属字段（避免非
NOTIFY 应用的这些字段被无谓查出）。

### 6. `AppNotifyService` 接口调整：从"给一条记录找匹配应用列表"改为"给一条已知目标
应用的记录发一次通知"

**决定**：

```java
public interface AppNotifyService {
    /**
     * 若该记录归属的应用当前同步方式为 NOTIFY，则向其发起一次通知；否则不做任何事。
     */
    void notifyIfConfigured(AppDataChangeLogEntity changeLog);
}
```

内部按 `changeLog.getAppRefId()` 查询该应用当前 `AppConfigEntity`，`syncMode != NOTIFY`
时直接返回；是则复用现有 `notifyOneApp` 私有逻辑（签名参数构造、`HttpClientUtils`
发送、`tab_app_notify_record` 落库）。`DomainChangeEventProcessor.process` 改为遍历
`record(event)` 返回的记录列表，对每条记录调用一次 `notifyIfConfigured`；单条记录
通知异常继续保持"捕获后记录日志、不影响其余记录"的既有风格（现在"其余记录"就是
"其余应用各自的记录"，语义不变）。

### 7. 拉取接口新增按应用过滤

**决定**：`AppDataChangeLogMapper#selectLatestByBizIds`/`#selectBySequence` 均新增
`appRefId` 参数，SQL 新增 `AND app_ref_id = #{appRefId}` 条件（`selectLatestByBizIds`
现有的"自连接 + `GROUP BY biz_id, MAX(id)`"写法不变，只是子查询和外层都加上这个过滤
——同一个 `bizId` 现在对不同应用可能有各自独立的最新记录行，"取每个应用自己看到的
最新一条"是正确语义）。`SyncPullServiceImpl` 调用处从 `OpenApiCallerContext` 取
`appRefId` 传入，其余逻辑（数据域启用校验、`BizSnapshotResolver` 现查业务表、字段
映射转换）不变——按应用过滤只影响"能看到哪些变更记录的元信息（`sequence`/
`operationType`/`occurredAt`）"，`data` 字段仍然是现查业务表的当前状态（不受本次改动
影响，`fix-app-sync-pull-live-data` 的既有设计保留）。

### 8. Flyway 迁移：清空存量变更记录表

**决定**：新增迁移文件：

```sql
ALTER TABLE tab_app_data_change_log ADD COLUMN app_ref_id BIGINT NOT NULL COMMENT '目标应用 id（tab_app.id）' AFTER id;
TRUNCATE TABLE tab_app_data_change_log;
TRUNCATE TABLE tab_app_notify_record;

ALTER TABLE tab_app_data_change_log ADD INDEX idx_tab_app_data_change_log_app_type (app_ref_id, data_type, id);

CREATE TABLE tab_app_sync_org_scope ( ... );
```

（`TRUNCATE` 顺序：先清 `tab_app_notify_record`，因为它有 `change_log_id` 逻辑关联
`tab_app_data_change_log.id`，虽然没有物理外键，先清引用方再清被引用方是更保守的顺序
习惯，即使没有物理外键约束也这样做。）

存量数据没有可回填的应用归属信息（迁移前的记录是"全局共享"的，无法确定"这条记录
原本该属于哪个应用"），只能清空重建，`proposal.md` 的 `BREAKING` 段落已说明。

### 9. `DisruptorDomainEventPublisher` 补充：事务提交后才真正发布事件

**背景**：实现完成后按 tasks.md 9.4 做真实环境端到端验证（针对运行中的后端 + 真实
MySQL 5.7 库，登录管理端把应用"同步应用"（id=1）的 `USER` 域同步范围限定到单个组织，
分别新建范围内、范围外任职的用户）时首次复现：范围内用户也没有产生任何变更记录（预期
应有一条），说明落库路径本身出了问题，而不是范围判定逻辑写错了。

**根因**：Decision 3/4 引入的 `AppSyncOrgScopeResolver#isUserWithinScope`/
`#isOrgIdWithinScope` 是本次改动**第一次**在事件消费路径（Disruptor 消费者线程，独立
数据库连接）里现查业务表（`tab_user_position`、`tab_org`）。而 `DomainEventPublisher`
的既有实现 `DisruptorDomainEventPublisher#publish` 此前只是把事件放入 RingBuffer 就
立即返回，不关心调用方是否处于事务中——只要调用方法本身被 `@Transactional` 包裹（如
新建用户时同时插入 `tab_user_position`），消费者线程可能在发布方事务提交前就被唤醒去
查 `tab_user_position`，读不到同一事务里刚写入但还未提交的数据，产生假阴性（组织范围
判定查不到任职记录，误判"不在范围内"，一条记录都不落库）。这个问题在此之前的
`app-sync-notify-pull-api`/`fix-app-sync-pull-live-data` 两个 change 里不会暴露——
落库路径此前只是原样落一条全局记录，不查询任何业务表，不存在"读到未提交数据"的可能。

**决定**：`DisruptorDomainEventPublisher#publish` 增加事务感知：若调用时
`TransactionSynchronizationManager.isSynchronizationActive()` 为 `true`，不立即把事件
放入 RingBuffer，而是 `registerSynchronization(...)` 注册一个只实现 `afterCommit()` 的
`TransactionSynchronization`，把实际发布动作（原逻辑，抽成私有方法 `publishToRingBuffer`）
延迟到事务提交后才执行；若当前没有活动事务（如非 `@Transactional` 方法内的调用），行为
不变，立即发布。补充单元测试 `sync/event/support/DisruptorDomainEventPublisherTest`，
覆盖两种路径：无活动事务时立即投递给消费者；有活动事务时投递被推迟，直到手动触发
`afterCommit()` 才真正投递。

**理由 / 影响面**：这不是"组织范围过滤"这个业务特性本身的逻辑，而是
`DomainEventPublisher`（`app-sync-notify-pull-api` change 引入的共享事件发布基础设施）
的一个通用可靠性修复——影响组织/用户/任职/应用/角色**全部**数据域的事件发布，不只是本
change 新增的组织范围场景。只是因为本 change 第一次在消费路径里引入了"查询业务表做判定"
的逻辑，才让这个原本一直存在但从未触发过的时序问题暴露出来。放在本 change 里一并修复，
不单独开一个 change，因为不修复的话本 change 的组织范围功能本身在真实并发场景下就是
错的（验证过程中一次即复现）。

## Risks / Trade-offs

- **[Risk] 组织范围判定发生在变更事件产生的那一刻，用当时的 `tab_user_position` 快照
  判断，不追溯** → 例：某应用的用户同步范围限定组织 A，用户 U 当时在组织 B（范围外）
  任职，某次 U 的姓名变更事件产生时判定"不在范围内"，不物化给该应用；之后 U 被调to
  组织 A 任职，但"姓名变更"那条历史记录不会因此补发给该应用（该应用需要等 U 在组织 A
  下一次产生变更事件，或者已经在其组织 A 任职记录新增时收到那条 POSITION 域事件）。
  这是"事件驱动、不追溯"模型的固有特性，与 `app-sync-notify-pull-api` design.md 里
  "字段快照走事件发生时刻"的既有原则一致，不是本次新增的缺陷。
- **[Trade-off] 变更记录表行数与订阅应用数成正比** → 见 Decision 1 代价说明，可接受。
- **[Risk] `tab_app_data_change_log` 清空是破坏性操作** → 已在 proposal.md 标注
  `BREAKING`，给定当前无真实外部接入方，影响可控。

## Migration Plan

Flyway 增量迁移自动执行；存量变更记录清空（见 Decision 8），已配置的
`tab_app_sync_domain_config`/`tab_app_config` 数据不受影响；新表默认零行（等价于
"全部数据"，向后兼容——迁移后所有应用的组织/用户/任职同步范围仍是"全部数据"，行为
与迁移前一致，直到管理员显式配置"指定组织范围"）。回滚方式：新增迁移不删除旧列/表，
如需回退只需前端隐藏"同步范围"入口、后端候选应用查询恢复不做组织范围过滤即可，
`tab_app_sync_org_scope` 表可以保留不清理。
