## Context

见 `proposal.md` 的问题说明。当前 `RoleServiceImpl#syncPermissions` 先按 `role_id` 删除旧关联，再遍历 `permissionIds` 调用 MyBatis-Plus `BaseMapper#insert`。因此 N 个权限点产生 N 条独立插入语句和 N 次数据库往返；现有 `RoleServiceImplTest` 也按逐条调用编写断言。项目已配置 MyBatis Mapper XML，目标数据库需兼容 MySQL 5.7，且本次不能新增依赖。

## Goals / Non-Goals

**Goals:**

- 将任意非空权限集合压缩为一次多值插入调用。
- 保留整体覆盖语义、审计字段和接口契约。
- 让角色主数据、旧关联删除和新关联插入具备原子性。
- 用单元测试锁定批量调用次数和数据内容。

**Non-Goals:**

- 不改为权限关联增量 diff。
- 不修改 API、DTO、前端、表结构或索引。
- 不引入 MyBatis 批处理插件、JDBC 手工连接或新依赖。
- 不优化其他模块的逐条插入逻辑。

## Decisions

### 1. 在现有 Mapper XML 中增加单条多值 INSERT

`RolePermissionMapper` 增加接收 `List<RolePermissionEntity>` 的批量插入方法，`RolePermissionMapper.xml` 使用 MyBatis `<foreach>` 生成：

```sql
INSERT INTO tab_role_permission
    (role_id, permission_id, create_by, create_time, update_by, update_time)
VALUES
    (?, ?, ?, ?, ?, ?),
    (?, ?, ?, ?, ?, ?)
```

服务层一次构造完整实体列表并调用一次 Mapper。该 SQL 只使用标准多值 `INSERT ... VALUES`，兼容 MySQL 5.7，也不依赖 MyBatis-Plus 版本特定的批处理 API。

备选方案是继续调用 `BaseMapper#insert` 并配置 JDBC batch executor。放弃原因是它依赖 SqlSession 执行器配置和 flush 时机，影响范围更广，也无法从调用结构上直观看出是否仍逐条执行。另一个备选是做新旧权限增量 diff；它会增加查询、集合差异和并发覆盖复杂度，不符合现有完整覆盖语义。

### 2. 空集合在服务层短路

删除旧关联后，`permissionIds` 为 `null` 或空集合时直接返回，不调用批量 Mapper。这样避免生成非法的 `INSERT ... VALUES` 空值 SQL，并保持创建时无权限、编辑时清空权限的既有行为。

### 3. 新增和编辑使用事务保证整体替换原子性

在角色新增、编辑服务方法上使用 Spring
`@Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)`：

- `Propagation.REQUIRED`：调用链已有事务时加入现有事务，没有事务时创建新事务，确保角色主表、旧关联删除、新关联批量插入、操作日志写入属于同一个提交单元。
- `rollbackFor = Exception.class`：除运行时异常外，受检异常也触发回滚，避免后续 Mapper、日志或协作组件引入受检异常后留下部分写入。
- 不指定 `transactionManager`：项目当前只有一个由 Spring Boot 基于数据源自动配置的事务管理器，使用默认选择可避免绑定并不存在的 Bean 名；只有未来引入多数据源时才需要显式限定。
- 不使用 `REQUIRES_NEW`：角色保存应参与调用方已有事务，而不是提前独立提交。

事务提交后再发布同步事件的行为与项目现有 `DomainEventPublisher` 事务感知机制一致。

备选方案是不增加事务，仅替换插入方式。放弃原因是批量 SQL 一旦失败，编辑请求可能已经删除旧权限，新增请求可能已经创建角色，造成接口报错但数据库留下不完整状态。

### 4. 测试验证一次调用及完整参数

调整 `RoleServiceImplTest`：非空权限集合捕获传给批量方法的实体列表，断言 Mapper 只调用一次、列表包含全部权限 id、角色 id 与审计字段正确；空集合断言批量方法从未调用。Mapper XML 由后端测试/构建验证可解析，必要时增加聚焦的 Mapper 集成覆盖。

## Risks / Trade-offs

- [Risk] 极端大的权限集合会使单条 SQL 和参数数量增长 → 当前权限点规模约百条，远低于常见数据库包大小和参数限制；本次不引入分片以保持一次数据库往返的目标。
- [Risk] 请求含重复权限 id 时唯一键会导致整批失败 → 维持数据库唯一约束的现有行为，并通过事务避免留下部分数据；本次不改变 DTO 校验语义。
- [Trade-off] `@Transactional` 会把日志写入和事件发布调用纳入事务作用域 → 事件发布器已支持提交后发布；原子性收益高于略微扩大的事务范围。

## Migration Plan

无需数据迁移。部署新后端即可；回滚时恢复服务与 Mapper 代码，不涉及数据库结构或存量数据回退。

## Verification Result

- `RolePermissionMapper.xml` 已实现单条 `INSERT INTO tab_role_permission (...) VALUES (...), (...)`，由一次 `<foreach>` 展开全部实体。
- `RoleServiceImplTest` 已验证新增和编辑提交多个权限点时只调用一次 `insertBatch`，同时验证权限 id、角色 id、审计字段及空集合短路行为。
- `RoleServiceImplTest` 已通过反射断言验证新增、编辑均使用 `Propagation.REQUIRED`、`rollbackFor = Exception.class`，并且未绑定特定事务管理器。
- 最终 `RoleServiceImplTest` 聚焦测试通过；最终 `./gradlew test` 全量执行 766 项测试，失败 0、错误 0、跳过 0，Spring 上下文成功加载 Mapper XML。
