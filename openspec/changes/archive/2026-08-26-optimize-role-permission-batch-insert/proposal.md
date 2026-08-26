## Why

角色新增和编辑会把每个 `permissionId` 分别执行一次 `INSERT INTO tab_role_permission`。权限点较多时，数据库往返次数随权限数量线性增长，`POST /api/roles` 和 `PUT /api/roles/{id}` 容易超过请求超时时间。

## What Changes

- 保持角色权限“先删除旧关联、再按请求完整重建”的既有覆盖语义不变。
- 将非空权限集合由逐条 Mapper `insert` 改为单次多值批量插入，显著减少 SQL 执行和数据库往返次数。
- 保持每条角色权限关联的创建人、创建时间、更新人、更新时间审计字段一致写入。
- 为角色新增、编辑显式配置事务传播行为及回滚异常范围，确保受检异常也会触发整体回滚。
- 增加 Mapper SQL 与服务单元测试，验证新增、编辑仅调用一次批量插入，空权限集合只删除不插入。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `role-management`: 明确角色新增、编辑在重建多个权限关联时必须采用批量持久化，避免逐权限执行插入导致请求超时，同时维持既有覆盖语义与审计字段。

## Impact

- 后端角色模块：`RoleServiceImpl`、`RolePermissionMapper`、`RolePermissionMapper.xml` 及相关单元测试。
- API 路径、请求体、响应体和数据库表结构均不变化。
- 不新增依赖，不修改 Flyway 迁移，不影响前端。

## Implementation Result

- 已按计划将逐权限 `BaseMapper#insert` 替换为一次 `RolePermissionMapper#insertBatch` 多值插入。
- 已为角色新增、编辑显式配置 `Propagation.REQUIRED` 和 `rollbackFor = Exception.class`，并保持默认事务管理器选择。
- 已更新单元测试覆盖批量参数、空集合、事务传播行为、回滚异常范围及未绑定事务管理器；聚焦测试及后端全量测试均通过，当前全量测试共执行 766 项，失败 0、错误 0、跳过 0。
