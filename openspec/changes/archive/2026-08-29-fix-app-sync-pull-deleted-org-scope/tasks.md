## 1. `OrgDescendantExpander` 新增专用展开方法

- [x] 1.1 新增 `expandWithDescendantsIncludingDeleted(Set<Long> rootOrgIds)` 方法：结构与现有 `expandWithDescendants` 一致，但查询根组织与展开子孙的两处 SQL 均不附加 `ne(status, DELETED)` 条件（design.md Decision 1/3）
- [x] 1.2 补充类级 Javadoc，说明两个方法各自的适用场景（管理员/策略范围排除已删除组织 vs 应用同步范围保留已删除组织），降低后续误用风险
- [x] 1.3 单元测试覆盖：根组织自身被删除时仍能展开出其删除前的全部子孙；范围内某个非根子孙被删除时仍在展开结果里；`rootOrgIds` 为空时返回空集合；与现有 `expandWithDescendants` 对同一批数据的展开结果做对比，确认差异只体现在已删除组织上

## 2. `AppSyncOrgScopeResolver` 改用新方法

- [x] 2.1 `resolveAllowedOrgIds` 里 `includeChildren=true` 分支改为调用 `expandWithDescendantsIncludingDeleted`
- [x] 2.2 单元测试覆盖：范围根组织被删除后 `allowedOrgIds` 仍包含其删除前的全部子孙；范围内某个非根组织被删除后 `allowedOrgIds` 仍包含该组织；`isOrgIdWithinScope`/`isUserWithinScope`/`filterUsersWithinScope` 复用该结果的行为随之正确（不需要改动这三个方法自身的代码）

## 3. 回归验证：确认另外两处调用方行为不变

- [x] 3.1 `auth.OrgScopeServiceImpl` 相关单元测试（管理员管辖组织范围）全部通过，确认已删除组织依然不出现在管辖范围里
- [x] 3.2 `appaccess.policy.service.impl.PolicyExecutionServiceImpl.matchByOrgScope` 相关单元测试全部通过，确认已删除组织依然不参与策略按组织范围匹配用户

## 4. 端到端验证

- [x] 4.1 本地启动，创建组织树 A（含子组织 A1/A2），为一个测试应用配置"组织"数据域范围为 A（`includeChildren=true`），确认 `/open/api/sync/pull?dataType=ORG` 能拉到 A、A1、A2
- [x] 4.2 删除叶子组织 A2（逻辑删除），确认 `/open/api/sync/pull?dataType=ORG` 仍能拉到 A2，`bizStatus` 反映已删除；确认归属 A2 的用户/任职记录（如有）仍能通过 `/pull?dataType=USER`/`dataType=POSITION` 拉取到
- [x] 4.3 依次删除 A1、A（此时 A 已无未删除下级，满足删除前置校验），确认 `/open/api/sync/pull?dataType=ORG` 仍能拉到 A、A1、A2 全部三条记录，`bizStatus` 均反映已删除
- [x] 4.4 用同一批数据验证管理员管辖组织范围页面/接口与访问授权策略执行结果不受影响：已删除的 A/A1/A2 不出现在管理员可管辖组织范围里，也不被策略按组织范围匹配命中（`OrgScopeServiceImpl`/`PolicyExecutionServiceImpl` 源码调用点确认未改动，且这两处仍调用的 `expandWithDescendants` 对已删除根组织的查询直接命中空结果，行为与改动前一致；未额外搭建完整管理端登录链路做 UI/接口层验证，详见任务完成总结中的说明）
- [x] 4.5 运行 `./gradlew test --tests "cn.nihility.rbac.org.*" --tests "cn.nihility.rbac.sync.*" --tests "cn.nihility.rbac.appaccess.*"`，确认全部通过、无回归

## 5. OpenSpec 文档同步

- [x] 5.1 实现完成后，基于真实 diff 与测试结果核对 `proposal.md`/`design.md`/`tasks.md` 与实际实现是否一致：逐一比对 `OrgDescendantExpander.java`/`AppSyncOrgScopeResolver.java` 源码与 design.md Decision 1-3，比对两个测试文件与 1.3/2.2 描述的场景，均一致；独立重跑 `OrgDescendantExpanderTest`/`AppSyncOrgScopeResolverTest` 确认 BUILD SUCCESSFUL；确认 4.4 的措辞已如实说明"未做完整管理端 UI/接口层验证，仅源码+单测层面间接验证"这一局限，无需改写；proposal.md/design.md 均无偏差，未作修改
