## ADDED Requirements

### Requirement: 应用记录版本号维护
系统 SHALL 为每个应用维护整型版本号 `version`，创建时为 1；更新、启用、停用、删除时使用数据库原子更新自增 1。删除事件 SHALL 携带旧版本加 1 后的最终 tombstone 版本。

#### Scenario: 应用并发更新版本不重复
- **WHEN** 同一应用发生两次成功的并发更新
- **THEN** 两次变更取得不同且严格递增的版本号

