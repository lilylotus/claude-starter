## Why

用户管理里"新增/编辑用户"弹窗内嵌的"任职信息"子表单（`UserServiceImpl.syncPositions`）新增、更新、移除任职记录时，从未调用操作日志记录组件，导致这些任职记录的写操作在任职详情页的"操作历史"里完全没有痕迹；而通过独立的"任职管理"入口（`PositionServiceImpl`）做同样的操作则会正常留痕。这与 `user-management` 现有 spec 中"任职记录的操作历史在任职管理模块自己的详情页面中展示"这一预期不符——预期是无论从哪个入口改动任职记录，历史都应该能在任职详情页看到，但内嵌入口这条路径从未被实现。

## What Changes

- `UserServiceImpl.syncPositions` 在新增、更新每条任职记录，以及物理删除不再保留的任职记录时，调用 `OperationLogRecorder` 记录对应的 `POSITION` 资源操作日志（新增/编辑/删除三种操作类型），字段快照沿用现有 `PositionServiceImpl.toLogSnapshot` 的口径（含 `ext1`~`ext10`）。
- 抽取 `PositionServiceImpl` 中已有的被操作对象名称快照（`targetName`）与字段快照（`toLogSnapshot`）逻辑为共享组件，供 `PositionServiceImpl` 与 `UserServiceImpl.syncPositions` 共用，避免重复实现。
- 不改变现有"用户详情操作历史展示"的范围——用户详情页的操作历史依旧只展示用户主数据自身的变更，不展示任职记录变更；任职记录变更统一在任职详情页的操作历史里查看，无论该记录是通过内嵌子表单还是独立任职管理入口创建/编辑的。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `position-management`：新增一条需求，明确任职记录的操作日志覆盖范围包含通过用户管理内嵌任职子表单产生的新增/编辑/删除写操作，不局限于独立任职管理入口。

## Impact

- 后端：`backend/src/main/java/cn/nihility/rbac/user/service/impl/UserServiceImpl.java`（`syncPositions`）、`backend/src/main/java/cn/nihility/rbac/user/service/impl/PositionServiceImpl.java`（抽取共享快照逻辑）、新增一个共享组件类（如 `cn.nihility.rbac.user.service.support` 包下的 `PositionLogSnapshotSupport`）。
- 不涉及前端改动——`OperationHistoryPanel.vue` 与任职详情页已经是通用组件，只要后端补上日志记录调用，历史列表会自然展示出来。
- 不涉及数据库迁移，不新增依赖。
