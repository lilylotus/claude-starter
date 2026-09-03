## ADDED Requirements

### Requirement: 角色记录版本号维护
系统 SHALL 为每个角色维护整型版本号 `version`，创建时为 1；更新、启用、停用、删除时使用数据库原子更新自增 1。删除事件 SHALL 携带旧版本加 1 后的最终 tombstone 版本。

#### Scenario: 角色删除携带最终版本
- **WHEN** 一个版本为 3 的角色被成功删除
- **THEN** 删除变更流水与通知中的 `entityVersion` 为 4

