## MODIFIED Requirements

### Requirement: 操作日志分页查询
系统 SHALL 提供操作日志分页查询接口，支持按模块、资源类型、操作类型、操作人、操作时间范围、被操作对象 id（`targetId`）筛选，全部筛选参数均为可选；分页参数 `page`（默认 `1`）、`pageSize`（默认 `10`）均为可选；结果按操作时间降序排列；每条记录包含模块、资源类型、操作类型、被操作对象名称、操作人、操作发起 IP、操作发起时间，以及该次操作的字段级变更详情（`changeDetail`，结构与详情接口一致：字段名、旧值、新值组成的数组）。`targetId` 与 `resourceType` 组合使用时可精确查询某一个具体资源实例的操作历史；`targetId` 单独出现（不携带 `resourceType`）时按 `target_id` 精确匹配，系统不做强制关联校验（不同资源类型的 `target_id` 取值可能重叠，由调用方负责同时传入 `resourceType` 以避免跨资源类型误匹配）。

#### Scenario: 不带筛选条件查询操作日志
- **WHEN** 客户端调用 `GET /api/operation-logs?page=1&pageSize=10`
- **THEN** 系统返回按操作时间降序排列的第一页操作日志，不做任何筛选

#### Scenario: 按资源类型与操作类型筛选
- **WHEN** 客户端调用 `GET /api/operation-logs?resourceType=role&operationType=5`
- **THEN** 系统仅返回资源类型为"角色"且操作类型为"删除"的操作日志

#### Scenario: 按操作时间范围筛选
- **WHEN** 客户端调用 `GET /api/operation-logs?startTime=2026-07-01T00:00:00&endTime=2026-07-31T23:59:59`
- **THEN** 系统仅返回操作时间落在该范围内（含边界）的操作日志

#### Scenario: 按资源类型与被操作对象 id 查询单个资源实例的操作历史
- **WHEN** 客户端调用 `GET /api/operation-logs?resourceType=org&targetId=5`
- **THEN** 系统仅返回资源类型为"组织"且被操作对象 id 为 `5` 的操作日志，按操作时间降序排列

#### Scenario: 分页结果携带字段级变更详情
- **WHEN** 客户端调用 `GET /api/operation-logs?resourceType=user&targetId=5`
- **THEN** 返回的每一条记录都包含 `changeDetail` 数组，无需再对每条记录单独调用详情接口即可获得该次操作的字段级变更（旧值→新值）
