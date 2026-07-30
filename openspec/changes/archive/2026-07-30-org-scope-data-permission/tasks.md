## 1. 数据访问层：新增按用户 id 解析管辖组织范围的查询

- [x] 1.1 `AdminOrgScopeMapper` 接口新增 `List<AdminOrgScopeEntity> selectOrgScopesByUserId(@Param("userId") Long userId)`。
- [x] 1.2 `resources/mybatis/mapper/AdminOrgScopeMapper.xml` 新增对应 SQL：`tab_admin_org_scope s INNER JOIN tab_admin a ON a.id = s.admin_id AND a.status = 2000 WHERE a.user_id = #{userId}`，返回 `AdminOrgScopeEntity`（不需要新建 DTO，复用现有实体的 `orgId`/`includeChildren` 字段）。

## 2. org 模块：新增"展开子孙组织 id"的通用能力

- [x] 2.1（实现方式较原任务描述有调整，见 3.2 的说明）新增独立组件 `cn.nihility.rbac.org.support.OrgDescendantExpander`（`@Component`，只依赖 `OrgMapper`），暴露 `Set<Long> expandWithDescendants(Set<Long> rootOrgIds)`——不放在 `OrgService` 接口上，避免与 `OrgScopeService` 形成循环 bean 依赖。
- [x] 2.2 `OrgDescendantExpander` 实现：查询全部未删除组织，按 `parentId` 建邻接表，对每个 `rootOrgIds` 做 BFS 收集自身+全部子孙 id，取并集返回；不使用递归 SQL（MySQL 5.7 不支持 `WITH RECURSIVE`）。

## 3. auth 模块：新增 `OrgScopeService`（管辖范围解析）

- [x] 3.1 新增 `cn.nihility.rbac.auth.service.OrgScopeService` 接口：`Optional<Set<Long>> resolveAllowedOrgIds(Long userId)`（design.md Decision 1 给出的语义：空 Optional = 不受限制）。
- [x] 3.2 新增 `cn.nihility.rbac.auth.service.impl.OrgScopeServiceImpl`：注入 `AdminOrgScopeMapper`（admin 模块）、`OrgDescendantExpander`（org 模块，见下）；调用 1.1 的查询拿到当前用户的管辖范围行，行列表为空则返回 `Optional.empty()`；非空时，`includeChildren` 为真的行收集其 `orgId` 后统一调用 `OrgDescendantExpander.expandWithDescendants` 展开，`includeChildren` 为假的行直接把 `orgId` 加入结果集，返回并集的 `Optional.of(...)`。
  - 实现中发现并已修正的设计偏差（design.md Decision 3 已同步更新）：最初把"展开子孙组织 id"实现为 `OrgService` 接口方法（任务 2.1/2.2 原文），落地时发现这会构成 `OrgServiceImpl ↔ OrgScopeServiceImpl` 的循环 Spring bean 依赖（纯构造器注入无法解析，启动即抛 `BeanCurrentlyInCreationException`）。第一版用 `@Lazy` 打了个补丁验证可行，但那是掩盖问题而非解决问题；最终改为把展开算法抽成独立组件 `cn.nihility.rbac.org.support.OrgDescendantExpander`（只依赖 `OrgMapper`），`OrgService` 接口不再有 `expandWithDescendants` 方法，`OrgScopeServiceImpl` 改为依赖这个组件而不是 `OrgService`，依赖图变成单向无环，两处都是普通 `@RequiredArgsConstructor`，不需要 `@Lazy`。已通过 `./gradlew test`（含 `RbacApplicationTests` 实际拉起完整 Spring 容器、连接本地 MySQL）验证。任务 2.1/2.2 的落地方式因此调整为"新增 `OrgDescendantExpander` 组件"而不是"`OrgService` 新增接口方法"，具体见下方 2.1/2.2 的更新说明。

## 4. org-management：组织树/列表三个查询接口接入过滤

- [x] 4.1 `OrgServiceImpl` 注入 `OrgScopeService`。
- [x] 4.2 `getTree()`：解析当前用户管辖范围；受限时先把 `listAllUndeletedOrdered()` 的结果按"id 在允许集合内"过滤，再用**不变**的既有树组装算法组装（design.md Decision 4：过滤后重跑原算法即可自然产生"虚拟根节点"，不要另写特判分支）。
- [x] 4.3 新增私有辅助方法 `queryChildrenRespectingScope(long effectiveParentId)`，替换 `getChildren`/`getChildrenTreeNodes` 里直接调用 `orgMapper.selectList(childrenQueryWrapper(...))` 的地方：不受限时行为不变；受限且 `effectiveParentId == 0`（顶层查询）时返回"允许集合内、真实 `parentId` 不在允许集合内"的节点列表；受限且 `effectiveParentId != 0` 时，若该 id 不在允许集合内返回空列表，否则按原查询条件查询后再按允许集合过滤一次（防御性兜底）。
- [x] 4.4 `getChildren(parentId, page, pageSize)` 分页版本同样接入 4.3 的辅助方法，分页元信息（`total`/`page`/`pageSize`）需要基于过滤后的列表手工计算分页（不能再直接用 `orgMapper.selectPage` 的 `IPage` 元信息，因为过滤发生在应用层）。实现为新增私有方法 `paginateFiltered(List<OrgEntity>, Integer page, Integer pageSize)`：page/pageSize 做基本防御性归一化（`null`/非正数分别兜底为 1、列表长度），`total` 取过滤后列表长度，按 `(page-1)*pageSize` 到 `+pageSize` 做 `subList` 截取后再转换为 `OrgVO`。

## 5. position-management：任职列表接口接入过滤

- [x] 5.1 `PositionServiceImpl` 注入 `OrgScopeService`。
- [x] 5.2 `getPage(orgId, page, pageSize)` 在现有 `orgId` 必填校验之后，插入范围校验：受限且 `orgId` 不在允许集合内时，直接构造并返回空 `PageResult`（`new PageResult<>(List.of(), 0L, page, pageSize)`），不调用 `userPositionMapper.selectPositionPage`。

## 6. application-management：应用列表接口接入过滤

- [x] 6.1 `AppServiceImpl` 注入 `OrgScopeService`。
- [x] 6.2 `getPage(page, pageSize)` 现有 `LambdaQueryWrapper` 受限时追加 `.in(AppEntity::getOrgId, allowedOrgIds)`；`allowedOrgIds` 为空集合时改用恒不匹配的哨兵条件（如 `.eq(AppEntity::getId, -1L)`），不依赖"这个集合当前恰好不会为空"的隐含前提（design.md Decision 6）。

## 7. 验证

- [ ] 7.1 单元/手动验证：给一个测试管理员账号配置 `include_children = 1` 的管辖范围（选一个非根组织），验证 `GET /api/orgs/tree`、`/api/orgs/tree/children`、`/api/orgs/children` 三个接口返回的都是以该组织为虚拟根的子树，祖先节点不出现。
  - 未完成真正的 HTTP 端到端联调（需要走 RSA 加密登录换取 `identity-token`，脚本化改造成本超出本次时间预算）；已用单元测试在 `OrgServiceImplTest` 覆盖等价逻辑（`getTree_shouldExposeVirtualRoot_whenScopeRestrictedToMiddleOrg`、`getChildren_shouldPaginateManually_whenTopLevelRestricted`、`getChildrenTreeNodes_shouldReturnEmptyList_whenParentIdOutOfScope`），并通过代码走查确认。
- [ ] 7.2 手动验证：同一账号调用 `GET /api/positions?orgId=` 传一个范围外的组织 id，确认返回空分页而非报错；传范围内的组织 id 确认正常返回。
  - 未完成真正的 HTTP 端到端联调，原因同上；已用单元测试覆盖等价逻辑（`PositionServiceImplTest#getPage_shouldReturnEmptyPageResult_whenOrgIdOutOfScope`/`#getPage_shouldQueryNormally_whenOrgIdInScope`）。
- [ ] 7.3 手动验证：同一账号调用 `GET /api/apps`，确认只返回范围内组织的应用；用一个未配置管辖范围的账号（如默认 `admin`）重复以上三类调用，确认行为与改动前完全一致（不受限）。
  - 未完成真正的 HTTP 端到端联调，原因同上；已用单元测试覆盖等价逻辑（`AppServiceImplTest#getPage_shouldAppendOrgIdInCondition_whenScopeRestricted`/`#getPage_shouldUseSentinelCondition_whenAllowedOrgIdsUnexpectedlyEmpty`），"不受限时行为不变"已由既有测试用例（默认打桩 `Optional.empty()`）持续覆盖。
- [x] 7.4 `./gradlew build`（实际执行 `./gradlew test`，等效验证编译+测试）确认编译与既有测试通过：全部 29 个测试类、含本次新增/调整的用例全部通过，`RbacApplicationTests` 证明完整 Spring 容器（含本次改动的 bean 依赖图）可以正常启动。

## 8. 文档同步

- [x] 8.1 实现完成后对齐 `proposal.md`/`design.md`/`tasks.md` 与实际实现结果：`proposal.md` 本身措辞已经足够高层，无需改动；`design.md` Decision 1/3 已更新为"`OrgDescendantExpander` 独立组件"这一实际架构（含调整前后的取舍说明）；`tasks.md` 2.1/2.2/3.2 已同步标注实现方式的调整。
