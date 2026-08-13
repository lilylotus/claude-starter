## 1. 数据库

- [ ] 1.1 编辑 `backend/src/main/resources/db/migration/V2__app_sync_notify_pull.sql`：删除 `tab_app_data_change_log` 建表语句里的 `data_snapshot` 列定义，同步更新脚本头部注释（去掉对 `data_snapshot` 保留字核对的提及）

## 2. 变更记录落库链路

- [ ] 2.1 `AppDataChangeLogEntity` 去掉 `dataSnapshot` 字段（含字段注释）
- [ ] 2.2 `AppDataChangeLogMapper.xml` 的 `selectLatestByBizIds`、`selectBySequence` 两个 `SELECT` 语句去掉 `data_snapshot` 列
- [ ] 2.3 `AppDataChangeLogServiceImpl.record()` 去掉 `.dataSnapshot(JacksonUtils.toJson(event.getSnapshot()))`，检查 `JacksonUtils` import 是否因此变为未使用并清理

## 3. 事件负载与快照构造链路清理

- [ ] 3.1 `DomainChangeEvent` 去掉 `snapshot` 字段（含相关 Javadoc）
- [ ] 3.2 删除 `sync/event/DomainSnapshotSupport.java`
- [ ] 3.3 `OrgServiceImpl`、`UserServiceImpl`、`PositionServiceImpl`、`RoleServiceImpl`、`AppServiceImpl` 构造 `DomainChangeEvent` 时去掉 `.snapshot(DomainSnapshotSupport.snapshot(entity))`（含相应的 `beforeEventSnapshot` 局部变量与 `DomainSnapshotSupport` import），注意区分同名但用途不同的操作日志快照（`positionLogSnapshotSupport`、`FormFieldSnapshotSupport` 等）不在本次改动范围内

## 4. 测试

- [ ] 4.1 `SyncPullServiceImplTest.java` 里构造 mock `AppDataChangeLogEntity` 的 `.dataSnapshot("{\"code\":\"ORG001\"}")` 去掉
- [ ] 4.2 在 `backend/` 目录执行 `./gradlew test --tests "cn.nihility.rbac.sync.*"` 确认相关测试通过；若删除死代码后有编译错误按需修正引用

## 5. 文档同步

- [ ] 5.1 实现完成后调用 `openspec-doc-sync` 核对 `proposal.md`/`design.md`/`tasks.md` 与实际改动一致
- [ ] 5.2 检查仓库根目录 `权限资源.txt` 是否受影响（预期本次改动不涉及菜单/按钮资源，仅需确认无需更新）
