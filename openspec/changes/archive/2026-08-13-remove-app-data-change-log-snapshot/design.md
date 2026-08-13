## Context

`data_snapshot` 由 `AppDataChangeLogServiceImpl.record()` 在落库时写入，数据来源是 `DomainChangeEvent.snapshot`（`DomainSnapshotSupport.snapshot(entity)` 在 `OrgServiceImpl`/`UserServiceImpl`/`PositionServiceImpl`/`RoleServiceImpl`/`AppServiceImpl` 的增删改方法里构造事件时调用产生）。拉取接口 `SyncPullServiceImpl` 在更早的 `fix-app-sync-pull-live-data` change 里已经改为通过 `BizSnapshotResolver` 现查业务表返回 `data` 字段，不再读取 `changeLog.getDataSnapshot()`。该列及其上游整条构造链路目前没有任何读取方。项目尚未发布上线，`backend/src/main/resources/db/migration/` 下的脚本此前已经有过一次合并整理（见 `910fd1b feat(flyway): flyway脚本合并`），本次沿用同样的处理方式。

## Goals / Non-Goals

**Goals:**
- 去掉 `tab_app_data_change_log.data_snapshot` 列及其读写代码。
- 顺带去掉因此变成死代码的 `DomainChangeEvent.snapshot` 字段、`DomainSnapshotSupport` 工具类，以及五个 ServiceImpl 里构造该字段的调用。

**Non-Goals:**
- 不改变拉取接口（按 id / 按序列号）对外的请求/响应结构与行为——`data` 字段仍然是现查业务表得到的当前状态。
- 不改变 `BizSnapshotResolver`、字段映射转换逻辑。
- 不引入新的 Flyway 版本号（见 Decision 1）。

## Decisions

### Decision 1：直接编辑 `V2__app_sync_notify_pull.sql`，不追加新的 alter 脚本
`tab_app_data_change_log` 表由 `V2` 建表，项目尚处开发阶段、脚本未在生产环境执行过。仓库已有先例（`910fd1b`）把多个未发布的 flyway 变更合并整理为一份连贯脚本，而不是层层叠加 alter。本次同样直接修改 `V2` 里的建表语句删除 `data_snapshot` 列定义及相关注释，保持脚本自身可读、一次性描述最终表结构。
- **备选方案**：新增 `V4__drop_app_data_change_log_snapshot.sql` 执行 `ALTER TABLE ... DROP COLUMN`。若该表已经在某个已上线环境跑过 `V2`，必须用这种方式；但当前仓库/项目状态确认为未发布，故不采用，避免徒增一份很快就要被下一次整理合并掉的迁移脚本。

### Decision 2：删除 `DomainChangeEvent.snapshot` 及其构造调用，但保留 `DomainSnapshotSupport` 工具类
**实施时发现原判断有误，已更正**：`DomainSnapshotSupport.snapshot(entity)` 并非只被 `AppDataChangeLogServiceImpl.record()` 消费——更早的 `fix-app-sync-pull-live-data` change 已经让 `sync/transform/BizSnapshotResolver.resolve()` 直接复用这个纯函数把业务表现查结果转成 `Map<String, Object>`，供拉取接口的 `data` 字段使用（`BizSnapshotResolver` 类头注释亦提到这点）。因此只删除 `DomainChangeEvent.snapshot` 字段和五个 ServiceImpl 里 `.snapshot(DomainSnapshotSupport.snapshot(entity))` 的构造调用（这部分确实除 `record()` 外无人读取，属于死代码）；`DomainSnapshotSupport` 类本身继续保留，作为 `BizSnapshotResolver` 依赖的共享工具类，不删除。
- **备选方案**：整体删除 `DomainSnapshotSupport`。已否决——会导致 `BizSnapshotResolver` 编译失败，且违反 proposal.md 中「`BizSnapshotResolver`...均不改动」的不变量承诺。

### Decision 3：spec 更新范围
`app-sync-notify-pull` 能力里「组织/用户/任职/应用/角色数据变更产生同步事件」需求原文把"变更后的字段快照"列为同步事件必须携带的内容之一，且有一条专门验证删除场景快照的 scenario。这两处需要随实现同步修改/删除，其余需求（拉取接口返回现查数据、字段映射转换等）本来就已经和"事件是否携带快照"无关，不需要改动。

## Risks / Trade-offs

- [风险] 若该 change 落地前 `V2` 脚本已经在某个环境执行过，直接编辑 `V2` 会导致 Flyway 校验和/历史不一致 → 缓解：实施前确认目标环境未跑过该迁移（当前仓库状态为纯开发环境，未部署），若发现已执行过则改为新增 `V4` alter 脚本。
