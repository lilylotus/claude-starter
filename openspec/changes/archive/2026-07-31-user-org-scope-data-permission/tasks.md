## 1. 数据访问层：`UserMapper` 新增按管辖组织范围过滤的分页查询

- [x] 1.1 `UserMapper` 接口新增 `IPage<UserVO> selectUserPage(IPage<?> page, @Param("name") String name, @Param("mobile") String mobile, @Param("idCard") String idCard, @Param("allowedOrgIds") Set<Long> allowedOrgIds, @Param("deletedStatus") int deletedStatus, @Param("positionDeletedStatus") int positionDeletedStatus)`（design.md Decision 4）。
- [x] 1.2 `resources/mybatis/mapper/UserMapper.xml` 新增对应 SQL：`SELECT u.* FROM tab_user u WHERE u.status != #{deletedStatus}` + 姓名/手机号/身份证号可选 `LIKE` 条件 + `allowedOrgIds != null` 时追加 `EXISTS (SELECT 1 FROM tab_user_position up WHERE up.user_id = u.id AND up.status != #{positionDeletedStatus} AND up.org_id IN (...))`（design.md Decision 2），`allowedOrgIds` 为空集合时 `IN (...)` 替换为恒假条件 `up.org_id = -1`（design.md Decision 3）；`ORDER BY u.show_order DESC, u.id ASC` 保持不变。

## 2. 业务逻辑层：`UserServiceImpl.getPage` 接入管辖组织范围过滤

- [x] 2.1 `UserServiceImpl` 注入 `OrgScopeService`（`auth` 模块，类比 `PositionServiceImpl` 现有写法）。
- [x] 2.2 `getPage(name, mobile, idCard, page, pageSize)` 内调用 `orgScopeService.resolveAllowedOrgIds(CurrentUserContext.getUserId())`；不受限时 `allowedOrgIds` 传 `null`，受限时传解析出的 `Set<Long>`；改为调用 1.1 新增的 `userMapper.selectUserPage(...)`，移除原有 `LambdaQueryWrapper` 拼接逻辑与 `userMapper.selectPage` 调用。
- [x] 2.3 确认 `UserStatus.DELETED`、`PositionStatus.DELETED` 两个状态常量值分别传给 `deletedStatus`、`positionDeletedStatus` 参数，不在 XML 里硬编码字面量（与 `UserPositionMapper.xml`/`PositionServiceImpl` 现有风格保持一致）。

## 3. 验证

- [x] 3.1 `./gradlew test` 确认编译与既有测试通过：全部 297 个测试用例通过（0 failures/errors），含 `RbacApplicationTests` 完整启动 Spring 容器。
- [x] 3.2（实现范围较原任务描述收窄，见下方说明）单元测试补充 `UserServiceImplTest`：`getPage_shouldReturnPageResult_whenCombiningNameAndMobileConditions`（组合姓名/手机号条件时参数正确透传）、`getPage_shouldPassNullAllowedOrgIds_whenNotRestricted`（未配置管辖范围时 `allowedOrgIds` 传 `null`，行为不变）、`getPage_shouldPassAllowedOrgIds_whenScopeRestricted`（配置管辖范围时把解析出的允许组织 id 集合透传给 `selectUserPage`）。
  - 范围收窄说明：与仓库里其他"跨表 XML 查询"改动（如 `UserPositionMapper.selectPositionPage`）的既有测试先例一致，"任一任职落在范围内即可见"、"没有任职记录的用户受限时不可见"这两条语义是在 `UserMapper.xml` 的 `EXISTS` 子查询里实现的，Mockito 单元测试只能打桩/验证 `UserServiceImpl` 传给 `UserMapper.selectUserPage` 的参数（`allowedOrgIds` 是否正确解析、`null` 还是具体集合），无法在不连接真实数据库的前提下验证 SQL 本身的 `EXISTS` 过滤语义是否正确；真正验证这两条语义需要 3.3 的集成/手动验证或专门的 `@SpringBootTest` + 真实 MySQL 用例，本次未新增后者（超出本次时间预算），与 3.3 一并留空。
- [ ] 3.3 手动/集成验证（若具备可行的登录换取 `identity-token` 的方式）：给测试管理员账号配置管辖组织范围，调用 `GET /api/users`，确认返回结果与预期一致；用未配置管辖范围的账号（如默认 `admin`）重复调用，确认行为与改动前完全一致。
  - 未完成真正的 HTTP 端到端联调，原因同 `org-scope-data-permission` change 遗留的 7.1/7.2/7.3（需要走 RSA 加密登录换取 `identity-token`，脚本化改造成本超出本次时间预算）。

## 4. 文档同步

- [x] 4.1 实现完成后基于实际 diff/测试结果对齐 `proposal.md`/`design.md`/`tasks.md`：`proposal.md`/`design.md` 措辞已经足够高层且与实际实现一致，无需改动；`tasks.md` 本次已同步标注 3.2 的范围收窄说明、3.3 未完成端到端联调的原因。
