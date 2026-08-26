## 1. 批量持久化实现

- [x] 1.1 在 `RolePermissionMapper` 声明角色权限关联批量插入方法，并在 `RolePermissionMapper.xml` 使用兼容 MySQL 5.7 的多值 `INSERT ... VALUES` 实现；通过后端测试启动验证 Mapper XML 可解析
- [x] 1.2 重构 `RoleServiceImpl#syncPermissions`，一次构造全部关联实体并仅调用一次批量 Mapper，保留空集合短路和审计字段；通过角色服务单元测试验证权限 id、角色 id 和审计字段完整
- [x] 1.3 为角色新增和编辑方法增加事务边界，保证角色主表、旧关联删除和新关联批量插入失败时整体回滚；通过事务注解检查及相关测试验证

## 2. 测试与回归

- [x] 2.1 更新 `RoleServiceImplTest` 的新增、编辑和空权限集合用例，断言多个权限只触发一次批量插入且不再调用逐条 `insert`；运行 `./gradlew test --tests "cn.nihility.rbac.role.service.impl.RoleServiceImplTest"`
- [x] 2.2 运行 `./gradlew test` 完成后端全量回归，确认角色新增/编辑返回结构、操作日志和同步事件相关测试无回归

## 3. OpenSpec 实现后同步

- [x] 3.1 基于最终代码 diff 和测试结果回填 `proposal.md`、`design.md`、`tasks.md` 的实际实现状态，并核对 delta spec 与最终行为一致

## 4. 显式事务策略调整

- [x] 4.1 将角色新增、编辑的事务注解调整为 `Propagation.REQUIRED` 且 `rollbackFor = Exception.class`，不绑定特定事务管理器；通过编译及注解反射测试验证配置值
- [x] 4.2 更新 `RoleServiceImplTest`，断言新增、编辑方法的传播行为和回滚异常范围；运行角色服务聚焦测试验证
- [x] 4.3 运行 `./gradlew test` 完成后端全量回归，并基于最终结果再次同步 OpenSpec 实现记录
