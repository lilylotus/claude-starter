## Context

任职记录（`tab_user_position`）有两个写入口：独立的"任职管理"页面（`PositionServiceImpl`，`PositionCreateRequest`/`PositionUpdateRequest`）与用户管理弹窗里内嵌的"任职信息"子表单（`UserServiceImpl.syncPositions`，随 `UserCreateRequest`/`UserUpdateRequest.positions[]` 一并提交）。`PositionServiceImpl` 的 `create`/`update`/`delete`/`enable`/`disable` 都会调用 `OperationLogRecorder` 记录一条 `resourceType=position` 的操作日志，任职详情页（`/identity/positions/:id`）的操作历史面板正是查询这些日志。但 `syncPositions` 只做了 `insert`/`updateById`/`deleteByIds`，从未调用 `OperationLogRecorder`，导致通过用户管理创建/编辑/移除的任职记录在任职详情页看不到任何历史（若该记录后续还被独立任职管理入口编辑过，则只有那一次编辑会出现在历史里，此前经由用户管理产生的变更全部缺失）。

`OperationLogResourceType.module(POSITION)` 固定返回"任职管理"、`resourceName` 固定返回"任职"，这两个值由 `resourceType` 决定、与调用方无关，所以无论从哪个入口触发，记录下来的日志在展示上是无差别的——不需要为"来自内嵌子表单"这一事实单独打标记。

## Goals / Non-Goals

**Goals:**
- `syncPositions` 新增、更新每条任职记录，以及物理删除不再保留的任职记录时，都调用 `OperationLogRecorder` 记录对应操作类型（新增/编辑/删除）的 `POSITION` 资源日志，字段快照口径（含 `ext1`~`ext10`）与 `PositionServiceImpl.toLogSnapshot` 保持一致。
- 消除 `PositionServiceImpl` 与 `UserServiceImpl` 之间重复的 `targetName`/`toLogSnapshot`/`extValues` 实现，抽成一个共享组件。

**Non-Goals:**
- 不改变用户详情页操作历史的展示范围（依旧只展示用户主数据自身变更，不展示任职记录变更）——用户与其任职记录仍是两条独立的历史线，本次只是把任职这条线补完整，不合并到用户详情页。
- 不改变 `syncPositions` 现有的 diff/物理删除语义本身（这是 `user-management` 里"用户任职记录的整体更新"这条既有需求，本次不触碰）；只是在既有的三种结果（新增/更新/物理删除）分支上各自追加一次日志记录调用。
- 不改变独立任职管理入口（`PositionServiceImpl`）现有的对外行为——重构只是把它内部的私有方法挪到共享组件里，调用方式不变。

## Decisions

### 抽取共享组件 `PositionLogSnapshotSupport`
在 `cn.nihility.rbac.user.service.support`（与已有的 `PositionDynamicFieldSupport` 同包）新增一个 Spring `@Component`：`PositionLogSnapshotSupport`，把 `PositionServiceImpl` 现有的私有方法 `targetName(UserPositionEntity)`、`toLogSnapshot(UserPositionEntity)`、`extValues(UserPositionEntity)`、`statusLabel(Integer)` 原样搬过去（依赖 `UserMapper`、`OrgMapper`、`FormFieldDefinitionService`，均已是这两个 Service 现有的注入项）。`PositionServiceImpl` 与 `UserServiceImpl` 都注入这个组件并调用 `targetName(entity)`/`snapshot(entity)`，替换掉各自原来的私有实现。这与本仓库处理"内嵌任职子表单与独立任职管理入口共用同一份动态字段校验逻辑"（`PositionDynamicFieldSupport`，见 `dynamic-form-field-coverage-fix` change）时采用的模式一致，不引入新的设计风格。

### `syncPositions` 三个分支各自追加一次日志记录
- **更新分支**（`request.getId() != null`）：在调用 `UserConvert.INSTANCE.updatePositionEntity(request, entity)` 修改内存中的 `entity` 之前，先用 `PositionLogSnapshotSupport.snapshot(entity)` 拍下 `beforeSnapshot`；`updateById` 落库后，用同一个组件对更新后的 `entity` 再拍一次快照，调用 `operationLogRecorder.recordUpdate(POSITION, entity.getId(), targetName(entity), beforeSnapshot, afterSnapshot)`。
- **新增分支**（`request.getId() == null`）：`insert` 落库、`entity` 拿到生成的 id 之后，调用 `operationLogRecorder.recordCreate(POSITION, entity.getId(), targetName(entity), snapshot(entity))`。
- **物理删除分支**（`idsToDelete`）：这些记录的完整实体在 `existingById`（diff 前查询到的既有任职记录）里已经有了，不需要再查一次库；在调用 `userPositionMapper.deleteByIds(idsToDelete)` 之前，对 `idsToDelete` 里每个 id 从 `existingById` 取出对应实体，调用 `operationLogRecorder.recordDelete(POSITION, id, targetName(entity), snapshot(entity))`。这里必须在物理删除之前完成快照与日志记录调用（`targetName` 依赖 `userId`/`orgId` 回查用户名/组织名，删除后实体虽仍在内存 map 里、字段本身没丢，但保持"先记录、后删除"的顺序更贴近 `PositionServiceImpl.delete` 现有的写法习惯，也避免任何时序上的疑虑）。

### 日志记录顺序：任职记录 diff 完成后，用户自身的新增/编辑日志之前还是之后都可以
`UserServiceImpl.create`/`update` 目前是先 `syncPositions(...)` 再 `operationLogRecorder.recordCreate/recordUpdate(USER, ...)`。任职记录的日志调用发生在 `syncPositions` 内部，因此会先于用户自身的日志写入。两条日志记录彼此独立（`resourceType` 不同、`targetId` 不同），顺序对结果没有影响，不需要额外调整现有的调用顺序。

## Risks / Trade-offs

- [批量新增/编辑多条任职记录时会产生多条独立的操作日志] → 这是预期行为，与逐条通过独立任职管理入口操作产生的日志条数一致，不做合并；调用方（前端任职详情页的历史列表）本来就是按单条任职记录的 `targetId` 查询，不受用户一次提交多条任职变更的影响。
- [物理删除分支在删除前先记录日志，若日志记录失败会阻断整个 `syncPositions`（进而阻断用户新增/更新）] → 与现有 `PositionServiceImpl.delete` 等方法在同一事务边界内的行为一致（本项目未见对 `OperationLogRecorder` 做失败降级处理），不引入新的失败模式，本次不额外处理。

## Migration Plan

无数据库迁移，纯代码改动。后端独立发布即可生效，不需要前端配合发布（前端任职详情页的操作历史面板已经是通用组件，后端补上日志记录调用后历史会自然出现）。
