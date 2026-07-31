## Context

`OrgScopeService.resolveAllowedOrgIds(Long userId)`（`auth` 模块，`org-scope-data-permission` change 已交付）解析当前登录用户对应的启用状态管理员身份的管辖组织范围：空 `Optional` 表示不受限制，非空 `Optional<Set<Long>>` 表示受限时的允许组织 id 全集（已展开 `include_children` 子孙）。`PositionServiceImpl.getPage` 已经示范了标准调用方式：注入 `OrgScopeService`，用 `CurrentUserContext.getUserId()` 取当前用户 id 调用 `resolveAllowedOrgIds`。

`UserServiceImpl.getPage` 当前用 `LambdaQueryWrapper<UserEntity>` 单表查询 `tab_user`（未删除 + 姓名/手机号/身份证号模糊搜索），`userMapper.selectPage` 走 MyBatis-Plus 自动分页。用户与组织没有直接外键，只能通过 `tab_user_position`（`user_id`、`org_id`、`status`）间接关联，一个用户可能有多条跨组织的任职记录。这是本次要新增过滤的跨表条件，按项目既有约定（`UserMapper.xml`/`UserPositionMapper.xml` 的说明注释、`CLAUDE.md`）多表关联查询写在 MyBatis XML 里，不在 Java 端拼接。

用户已确认可见性语义：**任一任职落在管辖范围内即可见**（不要求全部任职都落在范围内）；没有任何未删除任职记录的用户，受限时视为不可见。

## Goals / Non-Goals

**Goals:**
- `GET /api/users` 分页查询在管理员配置了管辖组织范围时，按"用户存在至少一条未删除、所属组织落在管辖范围内的任职记录"过滤；未配置管辖范围时行为完全不变。
- 过滤逻辑与既有姓名/手机号/身份证号模糊搜索、未删除过滤组合生效（"与"关系）。
- 不引入缓存，每次请求实时解析管辖范围（与 `OrgScopeService`、`AuthorizationService` 现有设计保持一致）。

**Non-Goals:**
- 用户详情查询（`GET /api/users/{id}`）、新增/更新/启停用/逻辑删除/重置密码等写操作接口的数据权限拦截——延续 `org-scope-data-permission` 只收紧列表/树查询、不触及详情和写操作的先例。
- 不改变 `tab_user_position` 的数据结构或用户管理内嵌任职子表单的既有维护流程。
- 不做管辖范围变更后的实时推送/缓存失效（管辖范围本身每次请求实时查库，天然没有缓存失效问题）。
- 不改变任何接口的请求/响应 DTO 字段形状，只改变返回哪些数据行。

## Decisions

### Decision 1：`UserServiceImpl.getPage` 注入 `OrgScopeService`，解析结果透传给 `UserMapper` 新增方法
类比 `PositionServiceImpl.getPage` 的既有写法：`getPage` 内调用
`orgScopeService.resolveAllowedOrgIds(CurrentUserContext.getUserId())`，不受限
（`Optional.empty()`）时 `allowedOrgIds` 传 `null` 给 Mapper 方法；受限时传解析出的
`Set<Long>`（非空，若为空集合按 Decision 3 的哨兵处理）。不新增单独的"是否受限"布尔参数，
Mapper 方法用 `allowedOrgIds == null` 判断是否要拼接过滤条件。

### Decision 2：过滤条件用 `EXISTS` 子查询而不是 `JOIN + DISTINCT`
```sql
SELECT ... FROM tab_user u
WHERE u.status != #{deletedStatus}
  AND (姓名/手机号/身份证号模糊搜索条件)
  <if test="allowedOrgIds != null">
  AND EXISTS (
      SELECT 1 FROM tab_user_position up
      WHERE up.user_id = u.id
        AND up.status != #{positionDeletedStatus}
        AND up.org_id IN
        <foreach collection="allowedOrgIds" item="orgId" open="(" separator="," close=")">#{orgId}</foreach>
  )
  </if>
ORDER BY u.show_order DESC, u.id ASC
```
选择 `EXISTS` 而不是 `INNER JOIN tab_user_position + DISTINCT`：`tab_user_position` 是
`tab_user` 的多对多关联表，一个用户可能有多条落在管辖范围内的任职记录，`JOIN` 会产生
重复的用户行，需要额外 `DISTINCT`/`GROUP BY` 才能配合 MyBatis-Plus 分页插件正确计算
`total`（`DISTINCT` 会让分页插件的 `COUNT` 包装 SQL 变复杂、容易在 `ORDER BY` 引用
未选中列时出错）；`EXISTS` 天然不产生重复行，语义上"是否存在至少一条满足条件的任职记录"
与目标语义（任一任职落在范围内即可见）直接对应，也不需要更改现有 `ORDER BY u.show_order,
u.id` 的排序列（不涉及 `up` 表的列）。

### Decision 3：`allowedOrgIds` 为空集合时使用恒不匹配的哨兵条件
与 `org-scope-data-permission` change design.md Decision 6（`AppServiceImpl` 的处理方式）
保持一致：`resolveAllowedOrgIds` 返回非空 `Optional` 时其 `Set` 按现有实现必然非空，但
`UserMapper.xml` 的 `<foreach>` 仍加一道防御——`allowedOrgIds` 非 `null` 但为空集合时，
`EXISTS` 子查询的 `IN ()` 会被替换成恒假条件（`up.org_id = -1`），不依赖"这个集合当前
恰好不会为空"的隐含前提，避免不同场景下空 `IN ()` 的 SQL 生成行为不一致。

### Decision 4：新增 `UserMapper` 方法名为 `selectUserPage`，返回 `IPage<UserVO>`
```java
IPage<UserVO> selectUserPage(IPage<?> page, @Param("name") String name, @Param("mobile") String mobile,
        @Param("idCard") String idCard, @Param("allowedOrgIds") Set<Long> allowedOrgIds,
        @Param("deletedStatus") int deletedStatus, @Param("positionDeletedStatus") int positionDeletedStatus);
```
类比 `UserPositionMapper.selectPositionPage` 的既有签名风格（`IPage<?> page` + 具体查询
参数 + 显式传入的状态字面量参数，不在 XML 里硬编码 magic number）。`UserServiceImpl.getPage`
改为调用这个新方法而不是原来的 `userMapper.selectPage(queryPage, wrapper)`；原有的
`LambdaQueryWrapper` 拼接逻辑整体移除，模糊搜索的 `LIKE` 条件也一并迁移到 XML（三个搜索
参数均可选，`<if test="...  != null and ... != ''">` 判断，和 `PositionServiceImpl`/
`OrgServiceImpl` 现有 XML 里可选条件的写法保持一致）。返回的 `UserEntity` 列到 `UserVO`
的映射沿用 `UserEntity` 现有全部列（`resultType="cn.nihility.rbac.user.dto.UserVO"`，
`SELECT u.*`——`tab_user` 单表列已经和 `UserVO` 字段基本一一对应，不需要像
`UserPositionMapper` 那样逐列 `AS` 重命名，因为没有跨表回填的列需要重命名）。

## Risks / Trade-offs

- **[风险] `EXISTS` 子查询在 `tab_user_position` 数据量增长后可能成为性能瓶颈**：
  `up.user_id`、`up.org_id` 目前是否有索引取决于现有 Flyway 迁移脚本。
  → **接受**：`tab_user_position` 现有查询（`selectPositionPage` 按 `org_id` 过滤）已经
  隐含依赖 `org_id` 上有可用索引；本次不新增索引评估工作，如果实测存在性能问题，应作为
  独立的索引优化 change 处理，不在本次范围内解决。
- **[权衡] 用户列表的"任一任职落在范围内即可见"是较宽松的语义**：管理员如果只想看到
  "完全属于自己管辖范围"的用户（不希望看到那些同时在管辖范围外也有任职的用户），本次
  实现不满足这种更严格的诉求。
  → 已在 proposal.md 说明这是用户确认过的语义选择，与既有任职列表的可见性口径保持一致
  （能在任职列表看到某用户在管辖范围内的任职记录，用户列表就应该能看到这个用户），不是
  被忽略的疏漏。
