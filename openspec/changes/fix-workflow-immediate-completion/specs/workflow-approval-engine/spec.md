## ADDED Requirements

### Requirement: 流程启动完成后同步直接结束的终态
系统 SHALL 在启动命令返回前检查实际引擎状态，对启动阶段即结束的实例同步通过或拒绝终态与结束时间，并清空当前节点。系统 SHALL 保留监听器已写入的系统终止状态及运行中节点信息；SHALL NOT 仅凭没有人工待办判定通过。

#### Scenario: 条件默认分支直接通过
- **WHEN** 流程启动后经条件默认分支直接到达 APPROVED 结束节点
- **THEN** 流程投影为 APPROVED，结束时间非空、当前节点为空，无人工待办

#### Scenario: 条件分支直接拒绝
- **WHEN** 流程启动后直接到达 REJECTED 结束节点
- **THEN** 流程投影为 REJECTED，不被默认通过逻辑覆盖

#### Scenario: 启动阶段系统终止
- **WHEN** 启动时监听器按空审批人策略将实例置为 TERMINATED
- **THEN** 回填引擎 ID 与终态检查保留 TERMINATED 及终止原因

#### Scenario: 流程仍在等待
- **WHEN** 启动后引擎仍有活动执行或审批任务
- **THEN** 系统保留 RUNNING 和实际节点信息，不提前结束实例
