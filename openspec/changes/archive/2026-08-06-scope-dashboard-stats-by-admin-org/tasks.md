## 1. 用户维度计数方法

- [x] 1.1 `backend/src/main/java/cn/nihility/rbac/user/mapper/UserMapper.java`：新增 `int countUsersInScope(@Param("allowedOrgIds") Set<Long> allowedOrgIds, @Param("deletedStatus") int deletedStatus, @Param("positionDeletedStatus") int positionDeletedStatus)`，Javadoc 说明其与 `selectUserPage` 共享 `EXISTS tab_user_position` 判断逻辑但各自独立维护 SQL。
- [x] 1.2 `backend/src/main/resources/mybatis/mapper/UserMapper.xml`：新增 `countUsersInScope` 语句，`SELECT COUNT(*) FROM tab_user u WHERE u.status != #{deletedStatus} AND EXISTS (SELECT 1 FROM tab_user_position up WHERE up.user_id = u.id AND up.status != #{positionDeletedStatus} AND up.org_id IN (...))`，`allowedOrgIds` 为空集合时 `EXISTS` 子查询用 `up.org_id = -1` 兜底（参考同文件 `selectUserPage` 的写法）。

## 2. 管理员维度计数方法

- [x] 2.1 `backend/src/main/java/cn/nihility/rbac/admin/mapper/AdminMapper.java`：新增 `int countAdminsInScope(@Param("allowedOrgIds") Set<Long> allowedOrgIds, @Param("deletedStatus") int deletedStatus, @Param("positionDeletedStatus") int positionDeletedStatus)`。
- [x] 2.2 `backend/src/main/resources/mybatis/mapper/AdminMapper.xml`：新增 `countAdminsInScope` 语句，结构与 `countUsersInScope` 一致，主表换成 `tab_admin a`，`EXISTS` 子查询里改为 `up.user_id = a.user_id`。

## 3. 统计服务接入管辖组织范围

- [x] 3.1 `backend/src/main/java/cn/nihility/rbac/dashboard/service/impl/DashboardStatisticsServiceImpl.java`：注入 `OrgScopeService`，`getStats()` 开头调用一次 `orgScopeService.resolveAllowedOrgIds(CurrentUserContext.getUserId())`。
- [x] 3.2 不受限（`Optional` 为空）时四条查询保持现状不变。
- [x] 3.3 受限时：组织总数在现有 `LambdaQueryWrapper` 上追加 `.in(OrgEntity::getId, allowed)`（`allowed` 为空集合时改用 `.eq(OrgEntity::getId, -1L)` 哨兵条件）；应用总数同样追加 `.in(AppEntity::getOrgId, allowed)`（空集合同样用 `-1L` 哨兵）；用户、管理员总数分别改为调用新增的 `userMapper.countUsersInScope(...)`/`adminMapper.countAdminsInScope(...)`。

## 4. 单元测试

- [x] 4.1 `backend/src/test/java/cn/nihility/rbac/dashboard/service/impl/DashboardStatisticsServiceImplTest.java`：为 `DashboardStatisticsServiceImpl` 新增 `OrgScopeService` mock 依赖，更新构造函数调用。
- [x] 4.2 新增用例：`resolveAllowedOrgIds` 返回空 `Optional`（不受限）时，四个 Mapper 的调用条件与现有行为一致（不追加组织范围过滤），断言现有两个用例仍然通过。
- [x] 4.3 新增用例：`resolveAllowedOrgIds` 返回非空 `Optional`（受限）时，验证 `orgMapper`/`appMapper` 的 `LambdaQueryWrapper` SQL 片段包含组织范围过滤条件，且 `userMapper.countUsersInScope`/`adminMapper.countAdminsInScope` 被以正确的 `allowedOrgIds` 参数调用。另补充一条"允许集合为空集合时使用哨兵条件"的用例（超出原计划，实现时发现值得单独覆盖）。

## 5. 验证与文档同步

- [x] 5.1 `./gradlew test --tests "cn.nihility.rbac.dashboard.*"` 通过（含新增 4 个用例），另跑过全量 `./gradlew test` 确认未破坏其他模块。
- [x] 5.2 手动验证：实际启动后端并通过真实登录接口验证（未走浏览器 UI，走 HTTP API 直连）——`tsx`/`admin123`（关联 `admin_id=2`，配置了 `org_id=1, include_children=1` 的管辖组织范围，展开后为 `{1,3}`）登录后 `GET /api/dashboard/stats` 返回 `orgCount=2, userCount=2, appCount=2, adminCount=0`，与手工在数据库里对同一范围计算的结果完全一致；默认 `admin`/`admin123`（无管辖组织范围配置）登录后返回 `orgCount=4, userCount=5, appCount=3, adminCount=2`，与系统整体总数一致、行为不变。
- [x] 5.3 实现完成后核对 proposal.md/design.md/tasks.md 与实际代码改动一致：新增了一条"空集合哨兵条件"单测（design.md 已覆盖该场景，tasks.md 4.3 已补充说明），其余按计划实现，无其他偏差。
