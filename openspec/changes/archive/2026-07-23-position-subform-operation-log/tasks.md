## 1. 抽取共享的任职记录日志快照组件

- [x] 1.1 新增 `cn.nihility.rbac.user.service.support.PositionLogSnapshotSupport`（`@Component`），把 `PositionServiceImpl` 现有私有方法 `targetName(UserPositionEntity)`、`toLogSnapshot(UserPositionEntity)`（对外暴露为 `snapshot`）、`extValues(UserPositionEntity)`、`statusLabel(Integer)` 原样迁移过去，依赖 `UserMapper`、`OrgMapper`、`FormFieldDefinitionService`
- [x] 1.2 `PositionServiceImpl` 注入 `PositionLogSnapshotSupport`，删除原来的私有实现，改为调用共享组件；确认 `create`/`update`/`delete`/`enable`/`disable` 行为不变

## 2. `UserServiceImpl.syncPositions` 补齐操作日志记录

- [x] 2.1 `UserServiceImpl` 注入 `PositionLogSnapshotSupport`
- [x] 2.2 更新分支：在 `UserConvert.INSTANCE.updatePositionEntity(request, entity)` 修改内存实体之前拍摄 `beforeSnapshot`；`updateById` 落库后拍摄 `afterSnapshot`，调用 `operationLogRecorder.recordUpdate(OperationLogResourceType.POSITION, entity.getId(), targetName, beforeSnapshot, afterSnapshot)`
- [x] 2.3 新增分支：`insert` 落库、拿到生成 id 后，调用 `operationLogRecorder.recordCreate(OperationLogResourceType.POSITION, entity.getId(), targetName, snapshot)`
- [x] 2.4 物理删除分支：在 `userPositionMapper.deleteByIds(idsToDelete)` 之前，对每个待删除 id 从已查询到的 `existingById` 取出实体，调用 `operationLogRecorder.recordDelete(OperationLogResourceType.POSITION, id, targetName, snapshot)`

## 3. 验证

- [x] 3.1 后端：`cd backend && ./gradlew build`
- [x] 3.2 手工验证（复用已有 dev 环境）：通过用户管理新增一个带任职信息的用户，检查该任职记录详情页操作历史出现"新增"记录；编辑该用户修改任职字段，检查历史出现"编辑"记录；编辑该用户移除该任职记录，检查历史出现"删除"记录（删除后该任职记录详情页本身会因记录不存在而不可访问，改为通过 `GET /api/operation-logs?resourceType=position&targetId=...` 直接查询确认日志已写入）；确认用户自身详情页的操作历史里不出现这些任职记录条目
