## Why

`tab_app_data_change_log` 表的 `data_snapshot` 列存储变更时的实体字段快照（JSON），但该表已有 `biz_id` 关联具体业务表，且拉取接口（`SyncPullServiceImpl`）早已改为通过 `BizSnapshotResolver` 实时查询业务表当前数据，不再读取这份持久化快照（`fix-app-sync-pull-live-data` change）。`data_snapshot` 现在是没有任何读取方的冗余数据，继续维护它（构造、序列化、落库）没有价值，应当去掉。

## What Changes

- **BREAKING**：`tab_app_data_change_log` 表去掉 `data_snapshot` 列（编辑 `V2__app_sync_notify_pull.sql` 直接去掉该列定义——项目尚未发布，沿用仓库既有的 flyway 脚本合并惯例，不追加新的 alter 脚本）。
- `AppDataChangeLogEntity` 去掉 `dataSnapshot` 字段；`AppDataChangeLogMapper.xml` 的 `selectLatestByBizIds`/`selectBySequence` 去掉 `data_snapshot` 列；`AppDataChangeLogServiceImpl.record()` 去掉写入该字段的代码。
- 级联清理失去消费者的死代码：`DomainChangeEvent.snapshot` 字段，以及 `OrgServiceImpl`/`UserServiceImpl`/`PositionServiceImpl`/`RoleServiceImpl`/`AppServiceImpl` 构造 `DomainChangeEvent` 时调用 `.snapshot(DomainSnapshotSupport.snapshot(entity))` 的代码一并去掉；`DomainSnapshotSupport` 工具类本身保留（`BizSnapshotResolver` 仍在用它给拉取接口现查快照，见 design.md Decision 2 更正说明）。
- 同步事件的载荷不再携带字段快照；`app-sync-notify-pull` 能力的相关需求描述与场景同步更新。
- `SyncPullServiceImplTest.java` 里构造 mock `AppDataChangeLogEntity` 时用到的 `.dataSnapshot(...)` 去掉。

不受影响：拉取接口（按 id / 按序列号）返回的 `data` 字段行为不变，仍然是现查业务表得到的当前状态；`BizSnapshotResolver`、字段映射转换逻辑均不改动。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `app-sync-notify-pull`：「组织/用户/任职/应用/角色数据变更产生同步事件」需求不再要求同步事件携带字段快照；删除依赖该快照的「删除用户触发同步事件且快照为删除前数据」场景。

## Impact

- 数据库：`tab_app_data_change_log.data_snapshot` 列删除（Flyway `V2__app_sync_notify_pull.sql`）。
- 后端代码：`sync/changelog/entity/AppDataChangeLogEntity.java`、`sync/changelog/service/impl/AppDataChangeLogServiceImpl.java`、`resources/mybatis/mapper/AppDataChangeLogMapper.xml`、`sync/event/DomainChangeEvent.java`、`org/service/impl/OrgServiceImpl.java`、`user/service/impl/UserServiceImpl.java`、`user/service/impl/PositionServiceImpl.java`、`role/service/impl/RoleServiceImpl.java`、`app/service/impl/AppServiceImpl.java`。`sync/event/DomainSnapshotSupport.java` 保留不动（`BizSnapshotResolver` 仍依赖）。
- 测试：`sync/openapi/service/impl/SyncPullServiceImplTest.java`。
- 无对外 API 契约变化（拉取接口的请求/响应结构不变）。
