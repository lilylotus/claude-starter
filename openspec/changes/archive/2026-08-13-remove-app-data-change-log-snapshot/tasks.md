## 1. 数据库

- [x] 1.1 编辑 `backend/src/main/resources/db/migration/V2__app_sync_notify_pull.sql`：删除 `tab_app_data_change_log` 建表语句里的 `data_snapshot` 列定义，同步更新脚本头部注释（去掉对 `data_snapshot` 保留字核对的提及）

## 2. 变更记录落库链路

- [x] 2.1 `AppDataChangeLogEntity` 去掉 `dataSnapshot` 字段（含字段注释）
- [x] 2.2 `AppDataChangeLogMapper.xml` 的 `selectLatestByBizIds`、`selectBySequence` 两个 `SELECT` 语句去掉 `data_snapshot` 列
- [x] 2.3 `AppDataChangeLogServiceImpl.record()` 去掉 `.dataSnapshot(JacksonUtils.toJson(event.getSnapshot()))`，检查 `JacksonUtils` import 是否因此变为未使用并清理

## 3. 事件负载与快照构造链路清理

- [x] 3.1 `DomainChangeEvent` 去掉 `snapshot` 字段（含相关 Javadoc）
- [x] 3.2 ~~删除 `sync/event/DomainSnapshotSupport.java`~~ **已更正**：实施时发现 `sync/transform/BizSnapshotResolver.resolve()`（`fix-app-sync-pull-live-data` change 引入）仍在调用 `DomainSnapshotSupport.snapshot(entity)` 给拉取接口现查快照，删除会导致编译失败，故保留该类不动，仅同步更正 `design.md` Decision 2 与 `proposal.md`
- [x] 3.3 `OrgServiceImpl`、`UserServiceImpl`、`PositionServiceImpl`、`RoleServiceImpl`、`AppServiceImpl` 构造 `DomainChangeEvent` 时去掉 `.snapshot(DomainSnapshotSupport.snapshot(entity))`（含相应的 `beforeEventSnapshot` 局部变量与 `DomainSnapshotSupport` import），注意区分同名但用途不同的操作日志快照（`positionLogSnapshotSupport`、`FormFieldSnapshotSupport` 等）不在本次改动范围内

## 4. 测试

- [x] 4.1 `SyncPullServiceImplTest.java` 里构造 mock `AppDataChangeLogEntity` 的 `.dataSnapshot("{\"code\":\"ORG001\"}")` 去掉
- [x] 4.2 在 `backend/` 目录执行 `./gradlew test --tests "cn.nihility.rbac.sync.*"` 确认相关测试通过（另外补跑了同一提交里也改动过的 `OrgServiceImplTest`/`RoleServiceImplTest`，均通过），无编译错误

## 5. 文档同步

- [x] 5.1 实现完成后核对 `proposal.md`/`design.md`/`tasks.md` 与实际改动一致——发现并更正了 `DomainSnapshotSupport` 删除范围的错误判断（见任务 3.2 说明），`specs/app-sync-notify-pull/spec.md` 内容与实现一致，无需改动
- [x] 5.2 检查仓库根目录 `权限资源.txt` 是否受影响——本次改动只涉及 `sync` 模块的实体/Mapper/事件/ServiceImpl 内部字段，未新增/删除任何 Controller 接口或前端页面菜单/按钮，`权限资源.txt` 无需更新
