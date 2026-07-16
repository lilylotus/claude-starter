## 1. 后端：MyBatis XML 多表查询

- [x] 1.1 `cn.nihility.rbac.user.mapper.UserPositionMapper` 新增两个方法声明：
  - `IPage<PositionVO> selectPositionPage(IPage<?> page, @Param("orgId") Long orgId, @Param("deletedStatus") int deletedStatus)`
  - `PositionVO selectPositionDetail(@Param("id") Long id, @Param("deletedStatus") int deletedStatus)`
- [x] 1.2 新增 `backend/src/main/resources/mybatis/mapper/UserPositionMapper.xml`（`namespace` 为 `cn.nihility.rbac.user.mapper.UserPositionMapper`）：
  - `selectPositionPage`：`tab_user_position up LEFT JOIN tab_user u ON u.id = up.user_id LEFT JOIN tab_org o ON o.id = up.org_id`，`WHERE up.org_id = #{orgId} AND up.status != #{deletedStatus}`，`ORDER BY up.show_order DESC, up.id ASC`，列别名对齐 `PositionVO` 字段（`user_name`/`org_name` 等），`resultType` 直接用 `cn.nihility.rbac.user.dto.PositionVO`（依赖 `mapUnderscoreToCamelCase`，不写 `resultMap`）
  - `selectPositionDetail`：同样的 JOIN 结构，`WHERE up.id = #{id} AND up.status != #{deletedStatus}`
  - 两条语句共用 `<sql id="positionJoinColumns">`/`<sql id="positionJoinFrom">` 片段，避免列清单/JOIN 结构重复
- [x] 1.3 `PositionServiceImpl` 改造：
  - `getPage` 改为直接调用 `userPositionMapper.selectPositionPage(new Page<>(page, pageSize), orgId, PositionStatus.DELETED)`，用返回的 `IPage` 构造 `PageResult`
  - `getById` 改为直接调用 `userPositionMapper.selectPositionDetail(id, PositionStatus.DELETED)`，查询结果为 `null` 时抛 `BusinessException("任职记录不存在")`（与 `getExistingEntity` 原报错文案保持一致）
  - 删除 `toVOListWithNames` 方法及其依赖的 `UserMapper`/`OrgMapper` 字段与相关 import（`OrgEntity`/`OrgMapper`/`UserEntity`/`UserMapper`/`LambdaQueryWrapper`/`Map`/`Collectors`）；`create`/`update`/`enable`/`disable`/`delete` 等写路径继续使用 `userPositionMapper`（`BaseMapper` 原生方法 `selectById`/`insert`/`updateById`）不变
- [x] 1.4 `PositionServiceImplTest` 已同步调整：移除 `UserMapper`/`OrgMapper` 相关 mock，改为 mock `selectPositionPage`/`selectPositionDetail`；新增的分页测试断言直接校验返回记录已包含 `userName`/`orgName`（9 个测试用例，全部通过）

## 2. 验证

- [x] 2.1 `./gradlew test`（`backend/` 目录）：`PositionServiceImplTest`（9 个用例）及全部现有测试通过
- [x] 2.2 API 级验证（`curl` 直接调用，针对真实 MySQL）：
  - 新增任职记录（返回结果含 `userName`="姓名1"、`orgName`="研发部"）
  - 按 `orgId` 分页查询（`records[0]` 含同样的 `userName`/`orgName`，`total`/`page`/`pageSize` 正确）
  - 详情查询（含 `userName`/`orgName`）、未携带 `orgId` 时分页查询被拒绝（`{"code":400,"message":"所属组织不能为空"}`）
  - 停用/启用、逻辑删除全流程正常；测试数据已清理（任职记录与临时组织均逻辑删除）
  - 额外验证了关联组织被逻辑删除后（`status=-1000` 但行仍存在）任职记录详情仍正常返回、`orgName` 仍能通过 `LEFT JOIN` 按 id 解析到（与原 Java 实现"不过滤关联表状态"的行为完全一致）
- [x] 2.3 应用启动日志确认无 `Invalid bound statement`、无 XML 解析报错，`UserPositionMapper.xml` 被正确加载
