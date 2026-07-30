## Context

`tab_admin_org_scope`（`admin_id`、`org_id`、`include_children`、审计字段，`UNIQUE(admin_id, org_id)`）
已经存在，`AdminServiceImpl.syncOrgScopes`（`admin/service/impl/AdminServiceImpl.java:287-308`）
在管理员创建/编辑时做"整体删除再批量插入"的同步；`AdminOrgScopeMapper.selectOrgScopesByAdminId`
（`admin/mapper/AdminOrgScopeMapper.xml`，INNER JOIN `tab_org` 回填组织名）供管理员详情页展示。
这套数据目前 100% 是 CRUD/展示用途，没有任何查询接口真正按它过滤数据——三次历史 change
（`admin-management`、`rbac-permission-authorization` 的 proposal/design）都明确把"按管辖组织范围
过滤业务数据"列为故意排除、留到后续的工作。本次就是要把这个"地基"用起来。

`tab_org` 是扁平表（仅 `parent_id`，无 `path`/`level`/物化路径列）。运行环境是 MySQL 5.7
（本仓库 Flyway 迁移日志"MySQL 5.7 is outside of Redgate community support"可确认），不支持
`WITH RECURSIVE`，所以现有 `OrgServiceImpl.getTree()` 是把全部未删除组织一次性查出来，在 Java
内存里按 `parentId` 建 `Map` 组装树——"组织 + 全部子孙"这类需求必须延续这个内存遍历套路，
不能指望一条递归 SQL 解决。

当前登录身份解析：`IdentityAuthFilter` 校验通过后把 `tab_user.id` 存入
`CurrentUserContext`（`ThreadLocal<Long>`）。`tab_admin.user_id` 唯一关联到 `tab_user.id`
（`AdminServiceImpl.checkUserIdUnique` 保证同一用户至多一个未删除管理员身份），已有的
`PermissionMapper.selectGrantedPermissionCodesByUserId` 走的正是
`tab_admin a WHERE a.user_id = #{userId} AND a.status = 2000` 这条关联路径，本次新增的
管辖范围解析要复用同一条关联路径。

## Goals / Non-Goals

**Goals:**
- 新增"管辖组织范围解析"能力：给定当前登录用户 id，解析出"是否受限"以及受限时的允许组织
  id 集合（已展开 `include_children` 子孙）。
- 组织树/组织列表、任职列表、应用列表三类查询接口，在管理员配置了管辖范围时按该范围过滤；
  未配置时行为完全不变。
- 不引入缓存，每次请求实时解析（与 `AuthorizationServiceImpl.hasPermission` 现有"不缓存，
  实时查库"的设计保持一致）。

**Non-Goals:**
- 用户列表（`GET /api/users`）的数据权限过滤——用户与组织是通过 `tab_user_position` 的
  间接多对多关系（一个用户可能在多个组织有任职记录），"用户是否在管辖范围内"需要独立设计
  "任一任职落在范围内即可见" vs "全部任职都要落在范围内才可见"等语义，风险和复杂度都明显
  更高，留到后续独立 change。
- 不改变 `tab_admin_org_scope` 的数据结构或维护界面（新增/编辑管理员时勾选组织范围的既有
  流程不变）。
- 不做管辖范围变更后的实时推送/缓存失效——和已有的 `permission-driven-visibility` 能力
  一样，管辖范围本身每次请求都实时查库，天然没有缓存失效问题。
- 不改变任何接口的请求/响应 DTO 字段形状，只改变返回哪些数据行。

## Decisions

### Decision 1：新增 `OrgScopeService`，落在 `auth` 模块，语义上是 `AuthorizationService` 的同类能力
新增 `cn.nihility.rbac.auth.service.OrgScopeService`（+ `impl.OrgScopeServiceImpl`）：

```java
public interface OrgScopeService {
    /**
     * 解析当前用户的管辖组织范围。
     * @return 空 Optional 表示不受限制（未配置管辖范围，或用户没有启用状态的管理员身份）；
     *         非空时表示受限，Set 为已展开 include_children 子孙的允许组织 id 全集。
     */
    Optional<Set<Long>> resolveAllowedOrgIds(Long userId);
}
```

`OrgScopeServiceImpl` 依赖：
- `AdminOrgScopeMapper`（新增方法 `selectOrgScopesByUserId`，见 Decision 2）——admin 模块。
- `OrgDescendantExpander`（新增的独立组件，见 Decision 3）——org 模块。**注意**：最初设计
  是把展开能力放在 `OrgService` 接口上，实现中发现这会构成循环 bean 依赖，已改为独立组件，
  Decision 3 已更新为实际实现，不要再按"`OrgService.expandWithDescendants`"理解。

放在 `auth` 模块而不是 `admin` 或 `org` 模块：这是"给定当前会话身份，判断其数据可见范围"，
语义上和 `AuthorizationService.hasPermission`（"给定当前会话身份，判断其菜单权限"）是同一类
横切关注点，`auth` 模块已经有跨模块直接注入其他模块 Mapper 的先例
（`AuthorizationServiceImpl` 注入 `permission` 模块的 `PermissionMapper`），延续同一模式。
不放在 `admin` 模块：`admin` 模块负责管理员身份本身的 CRUD，不适合承载"这份配置如何影响
其他模块的查询"这种跨模块编排逻辑。

"没有启用状态的管理员身份"与"有管理员身份但未配置范围"被统一处理为同一种"不受限制"
结果——不额外区分，因为二者都归结为 Decision 2 的查询返回空列表，不需要在
`OrgScopeServiceImpl` 里写分支特判。实际场景里能通过 `IdentityAuthFilter` 权限校验、
走到本次要收紧的这几个查询接口的请求，调用者必然已经有一个启用状态的管理员身份（现有
`AuthorizationService` 的权限点体系本身就要求 `tab_admin.status = 2000`），"用户身份但无
管理员身份"这一支路径在当前系统里理论上不会被触发，但解析逻辑仍然按统一规则处理，不额外
加一层"必须有管理员身份，否则报错"的强校验，避免过度设计。

### Decision 2：`AdminOrgScopeMapper` 新增按用户 id（经 `tab_admin` 关联）查询管辖范围
```java
// AdminOrgScopeMapper 接口新增
List<AdminOrgScopeEntity> selectOrgScopesByUserId(@Param("userId") Long userId);
```
```sql
<!-- AdminOrgScopeMapper.xml 新增 -->
<select id="selectOrgScopesByUserId" resultType="cn.nihility.rbac.admin.entity.AdminOrgScopeEntity">
    SELECT s.*
    FROM tab_admin_org_scope s
    INNER JOIN tab_admin a ON a.id = s.admin_id AND a.status = 2000
    WHERE a.user_id = #{userId}
</select>
```
直接复用 `AdminOrgScopeEntity`（`orgId`、`includeChildren` 字段已具备，不需要新建 DTO）
作为返回类型——这条查询不需要回填组织名（不是给界面展示用，是给过滤逻辑内部消费），
和已有的 `selectOrgScopesByAdminId`（INNER JOIN `tab_org` 回填组织名，供管理员详情页展示）
是两个不同用途的查询，不合并、不复用彼此。

### Decision 3：新增独立组件 `OrgDescendantExpander`（而不是 `OrgService` 接口方法）展开子孙组织 id，内存遍历，不用递归 SQL
```java
// cn.nihility.rbac.org.support.OrgDescendantExpander，只依赖 OrgMapper
public Set<Long> expandWithDescendants(Set<Long> rootOrgIds) { ... }
```
实现：查询全部未删除组织，按 `parentId` 建一个 `parentId -> 直属子节点 id 列表` 的邻接表，
对每个 `rootOrgIds` 做一次 BFS 收集自身 + 全部子孙 id，取并集返回。这个方法不感知"管辖范围"
这个业务概念——`OrgScopeServiceImpl` 只对"配置了 `include_children = 1`"的那部分管辖范围行
调用它展开，"`include_children = 0`"的行直接把 `orgId` 本身加入结果集，不展开。

**本决策在实现阶段做过一次调整，记录调整过程供后续读者理解取舍**：最初设计把这个方法放在
`OrgService` 接口上（`Set<Long> expandWithDescendants(Set<Long> rootOrgIds)`），理由是
"`getTree()` 已经证明了这个内存遍历模式可行，不应该有第二份重复实现，且未来别的功能需要
'某组织全部子孙 id'时也能直接复用 `OrgService`"。但落地时发现这会产生一个 Spring 纯构造器
注入无法解析的循环 bean 依赖：`OrgServiceImpl`（Decision 4，依赖 `OrgScopeService` 过滤组织
树/列表）→ `OrgScopeServiceImpl`（依赖 `OrgService.expandWithDescendants`）→ 又绕回
`OrgServiceImpl`。第一版修复是给 `OrgScopeServiceImpl` 构造器的 `OrgService` 参数标
`@Lazy`（用延迟解析的代理打破启动期循环），这能跑通，但属于给设计缺陷打补丁而不是解决它——
`@Lazy` 掩盖了"这两个类本不该互相依赖"这个根本问题。

最终改为：把展开算法从 `OrgService` 接口移出，做成一个只依赖 `OrgMapper` 的独立组件
`OrgDescendantExpander`（放在 `org.support` 包，和 `formfield.support`/
`user.service.support` 下已有的同类"模块内共享工具组件"风格一致）。`OrgScopeServiceImpl`
直接依赖 `OrgDescendantExpander`，不再依赖 `OrgService`——依赖图变成
`OrgServiceImpl → OrgScopeService → OrgDescendantExpander → OrgMapper`，单向无环，两处都
恢复成普通的 `@RequiredArgsConstructor` 构造器注入，不需要 `@Lazy`。"未来别的功能需要复用
展开能力"这个原始理由仍然成立，只是复用方式变成"直接注入 `OrgDescendantExpander`"而不是
"通过 `OrgService` 接口"——对调用方来说同样是一行注入声明，可复用性没有减弱。

### Decision 4：组织树/列表三个接口的过滤方式——受限时以"允许组织 id 集合"为准过滤，
### 顶层查询自然产生"虚拟根节点"
`OrgServiceImpl` 的 `getTree()`、`getChildren(parentId,...)`、`getChildrenTreeNodes(parentId)`
统一改造：

1. 调用 `orgScopeService.resolveAllowedOrgIds(CurrentUserContext.getUserId())`；不受限
   （`Optional.empty()`）时三个方法的现有逻辑完全不变。
2. `getTree()` 受限时：把 `listAllUndeletedOrdered()` 的结果先按"id 在允许集合内"过滤，
   再用**完全不变**的既有"按 `parentId` 建 `Map`、组装树、`parentNode == null` 时收进
   `roots`"算法组装——这一步是设计上的关键点：因为过滤后的实体列表里，某个被过滤实体的
   真实上级组织 id 有可能已经不在过滤后的 `nodeMap` 里，导致该实体在原算法里自然被判定为
   "找不到父节点"从而被收进 `roots`。也就是说，"某管理员只管辖组织 C（C 是根组织 A 下面
   B 的子组织）时，返回的树只有 C 及其子孙、C 表现为根节点"这一行为，不需要新写任何
   "虚拟根节点"的特判分支，是过滤 + 复用原算法的自然结果。
3. `getChildren(parentId,...)`/`getChildrenTreeNodes(parentId)`（这两个方法目前都是直接
   `orgMapper.selectList(childrenQueryWrapper(effectiveParentId))`，改走一个新增的私有
   辅助方法 `queryChildrenRespectingScope(effectiveParentId)`）：
   - `effectiveParentId == 0`（顶层查询，前端懒加载树请求顶级节点、或不带 `parentId` 的
     分页请求）且受限：不能再简单地按 `parentId = 0` 查——`C` 在数据库里的真实 `parentId`
     是 `B`，不是 `0`，直接查 `parentId = 0` 会漏掉它。改为：取全部未删除组织，过滤出
     "id 在允许集合内，且其真实 `parentId` **不在**允许集合内"的那些节点——这正是"虚拟根
     节点"的定义（自身可见，但上级不可见，所以对当前调用者而言它就是顶层）。
   - `effectiveParentId != 0`（下钻某个具体节点）且受限：若该 `parentId` 本身不在允许
     集合内，直接返回空列表（调用者对这个子树完全不可见，不区分"该 id 不存在"和"存在但
     不在管辖范围内"，避免用错误信息反向确认某个 org id 是否存在）；若在允许集合内，
     按原有 `childrenQueryWrapper` 查询后再对结果按允许集合过滤一次（正常情况下这层过滤
     是多余的——`include_children` 展开已经保证任何允许节点的直属子节点必然也在允许集合
     里——但保留这一步作为防御性兜底，不额外增加复杂度只是加一个 `stream().filter(...)`）。

### Decision 5：任职列表——请求的 `orgId` 超出管辖范围时返回空分页，而不是报错
`PositionServiceImpl.getPage(orgId, page, pageSize)` 在现有"`orgId` 必填"校验之后、发起
真正的分页查询之前，插入一次范围校验：受限且 `orgId` 不在允许集合内时，直接构造并返回一个
空的 `PageResult`（`total = 0`），不调用 `userPositionMapper.selectPositionPage`。选择"返回
空分页"而不是"抛业务异常/403"：这个接口的既有语义是"某个组织下没有任职记录时也是返回空
分页，不是报错"，管辖范围之外的组织对当前调用者而言观感上应该和"这个组织下没有任职记录"
一致，不应该额外暴露"这个 `orgId` 存在但你无权查看"这种更具体的越权探测信号。

### Decision 6：应用列表——追加 `org_id IN (:allowedOrgIds)` 过滤条件
`AppServiceImpl.getPage(page, pageSize)` 现有查询完全没有组织维度的过滤（`tab_app.org_id`
是 `NOT NULL` 列，但列表查询从来没有用过它）。受限时给现有 `LambdaQueryWrapper` 追加
`.in(AppEntity::getOrgId, allowedOrgIds)`。防御性细节：`resolveAllowedOrgIds` 返回非空
`Optional` 时，其内部 `Set` 按 Decision 1/2 的实现必然非空（至少有一条配置行贡献至少一个
org id），但为了不让未来的重构意外产生"空 Set 传给 MyBatis-Plus `.in()`"（不同版本对
空集合 `IN ()` 的 SQL 生成行为不一致，可能生成恒真或语法错误的 SQL），转换处加一道防御：
`Set` 为空时改用一个恒不匹配的哨兵条件（如 `.eq(AppEntity::getId, -1L)`），不依赖
"这个集合当前恰好不会为空"这个隐含前提。

## Risks / Trade-offs

- **[风险] 组织树/列表的"虚拟根节点"行为依赖过滤后重跑既有树组装算法**：如果未来有人
  修改 `getTree()`/`childrenQueryWrapper` 的排序或分组逻辑，需要同时确认过滤发生的位置
  仍然在组装之前，否则可能重新引入"祖先节点意外可见"的回归。
  → **缓解**：Decision 4 的实现要求过滤步骤和树组装步骤在同一个方法里紧邻书写，并在
  代码注释里明确标注"虚拟根节点是过滤+现有算法的自然结果，不要拆开"。
- **[风险] `expandWithDescendants` 和 `getTree()` 一样是"全量加载未删除组织到内存"**：
  组织数量增长到一定规模后，每次受限查询都要重新加载全量组织数据用于展开子孙，有性能
  隐患。
  → **接受**：现有 `getTree()` 已经是同样的模式且是既有生产行为，本次改动不引入新的
  性能量级问题；如果未来组织规模显著增长，应该作为独立的性能优化 change（比如给
  `tab_org` 加物化路径列）处理，不在本次范围内解决。
- **[权衡] 用户列表本次不做**：意味着"管辖组织范围"这个功能上线后，行为不一致——组织树、
  任职、应用都会收紧，但用户列表不会。管理员如果误以为"管辖范围"是全局生效的，可能会
  在用户列表上看到超出预期的数据。
  → 已在 proposal.md 的"不在本次范围"里明确写出用户列表被排除的原因；后续独立 change
  跟进时需要在其 proposal 里链接说明这是本次遗留的差距，不是被忽略的疏漏。
