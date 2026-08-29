## Context

`OrgDescendantExpander.expandWithDescendants(Set<Long> rootOrgIds)`（`backend/src/main/java/cn/nihility/rbac/org/support/OrgDescendantExpander.java`）目前有三处调用方，全部走同一段"排除已逻辑删除组织"的展开逻辑：

1. `auth.OrgScopeServiceImpl`（管理员管辖组织范围解析）——排除已删除组织是正确语义：已删除的组织不应出现在任何管理员可管辖/可操作的范围里。
2. `appaccess.policy.service.impl.PolicyExecutionServiceImpl.matchByOrgScope`（访问授权策略按组织范围匹配用户）——同样应排除已删除组织：策略匹配的是"当前有效的组织归属关系"，已删除组织下不应再匹配出用户来触发授权。
3. `sync.scope.AppSyncOrgScopeResolver.resolveAllowedOrgIds`（应用同步组织范围解析，供 `/open/api/sync/pull` 的 `ORG`/`USER`/`POSITION` 三个数据域，以及 `isOrgIdWithinScope`/`isUserWithinScope`/`filterUsersWithinScope` 使用）——这里排除已删除组织是**错误**的：`app-sync-notify-pull` 规范明确要求"停用/已删除记录仍会被拉取到"，一旦组织被删除就从范围里消失，会导致该组织自身、以及归属该组织的用户/任职记录，一并从 `/pull` 结果里消失，外部应用无法再通过 `bizStatus=-1000` 感知删除，只能看到数据"凭空消失"。

三处里只有第 3 处需要改变语义，第 1、2 处必须保持现状不变（且没有收到任何 bug 反馈，行为符合预期）。

## Goals / Non-Goals

**Goals:**
- `sync.AppSyncOrgScopeResolver` 解析出的 `allowedOrgIds`（及依赖它的 `isOrgIdWithinScope`/`isUserWithinScope`/`filterUsersWithinScope`）不再因为组织被逻辑删除而把它排除出范围。
- `auth.OrgScopeServiceImpl`、`PolicyExecutionServiceImpl` 两处现有调用方行为保持完全不变（继续排除已删除组织）。
- 不引入新的循环依赖、不新增数据库迁移。

**Non-Goals:**
- 不改变 `resolveScopePrefixes`（供 `/open/api/sync/changes` 使用，走 `orgPath` 前缀匹配，不经过 `OrgDescendantExpander`，本来就不受影响）。
- 不处理"组织被物理删除"场景（现状本就极为罕见，`resolveScopePrefixes` 已有防御性跳过逻辑，本次不涉及）。
- 不改变 `POSITION` 数据域"任职记录被物理删除"（`UserServiceImpl.syncPositions` 批量同步分支）的可见性语义——那是 `app-sync-changelog-pull` change 里已经设计好的"指针存在但 `/pull` 复核查不到=已离开范围"隐式信号，与本次"逻辑删除应可见"的场景是两套不同的东西，不属于本次修复范围。

## Decisions

### 1. 新增专用方法，不修改 `expandWithDescendants` 现有签名/行为

在 `OrgDescendantExpander` 里新增一个方法 `expandWithDescendantsIncludingDeleted(Set<Long> rootOrgIds)`，实现与现有 `expandWithDescendants` 结构一致，唯一区别是查询根组织与展开子孙时都**不**附加 `ne(status, DELETED)` 条件。现有 `expandWithDescendants` 方法签名、行为、调用方（`OrgScopeServiceImpl`、`PolicyExecutionServiceImpl`）完全不变。

**备选方案**：给 `expandWithDescendants` 加一个 `boolean includeDeleted` 参数。未采用：会同时改动 `OrgScopeServiceImpl`、`PolicyExecutionServiceImpl`、`AppSyncOrgScopeResolver` 三处调用点的调用代码（都要显式传入 `false`/`true`），改动面比新增一个方法更大，且未来读代码的人看到 `expandWithDescendants(ids, false)` 这种调用不如看到 `expandWithDescendantsIncludingDeleted(ids)` 直观；两个方法各自的方法名已经完整表达了语义差异，不需要靠参数值才能看懂调用意图。

### 2. `AppSyncOrgScopeResolver.resolveAllowedOrgIds` 改用新方法

```java
if (!recursiveRootOrgIds.isEmpty()) {
    allowedOrgIds.addAll(orgDescendantExpander.expandWithDescendantsIncludingDeleted(recursiveRootOrgIds));
}
```

`isOrgIdWithinScope`/`isUserWithinScope`/`filterUsersWithinScope` 均直接复用 `resolveAllowedOrgIds` 的结果，不需要单独改动。`resolveScopePrefixes` 不调用 `OrgDescendantExpander`，不受影响。

### 3. 组织本身被删除、且是范围根组织的场景：直接查询根组织时同样不排除已删除

新方法内部先查询根组织自身信息（用于取 `orgPath` 做前缀展开），这一步如果继续排除已删除组织，遇到"范围根组织自身被删除"的场景仍然会导致查询根组织返回空、直接查不到 `orgPath`、无法展开子孙——因此这一步也必须去掉 `ne(status, DELETED)` 条件，与展开子孙那一步保持一致。这是本次 bug 里最隐蔽的一层：只去掉子孙查询的状态过滤、保留根组织查询的状态过滤，仍然无法修复"范围根组织自身被删除"这一具体场景（design 阶段验证过，proposal.md 的"范围根组织自身被删除后，其原有子孙范围保持不变"场景正是为了防止未来实现只改一半）。

### 4. 组织删除的前置校验保证不会产生"组织删除后子孙仍是未删除状态"的不一致

沿用 `app-sync-changelog-pull` change design.md Decision 3 已经确认的现状：`OrgServiceImpl.delete` 有"存在未删除的下级组织即拒绝删除"的前置校验，因此一个组织被删除时，它的下级要么全部已经是已删除状态，要么根本没有下级。新方法展开子孙时不排除已删除组织，不会出现"父组织已删除但查出一批状态为启用/停用的子孙"这种反直觉结果——展开出来的要么是历史上就在范围内、后来陆续被删除的组织，语义上仍然成立。

## Risks / Trade-offs

- [新方法与 `expandWithDescendants` 存在结构重复代码] → 两个方法体量很小（各自不到 20 行 SQL 拼装逻辑），且语义差异是本次修复的核心所在，提取公共私有方法收益有限、还要传参数区分两种状态过滤，权衡后接受这点重复，保持两个公开方法各自职责单一、一望即知。
- [调用方以后新增第三方需求时可能搞错该用哪个方法] → 两个方法名都完整拼出了语义（`expandWithDescendants` vs `expandWithDescendantsIncludingDeleted`），并在类级 Javadoc 里说明各自适用场景（管理员/策略范围 vs 应用同步范围），降低误用概率。
- [已经上线的外部应用可能已经把"记录消失"当作删除信号硬编码] → proposal.md Impact 已声明：这类应用不受影响，因为它们后续看到的仍然是 `bizStatus=-1000`，只是现在记录会明确出现而不是无声消失，属于让实际行为更贴近既有规范文档的修复，不是破坏性变更。

## Migration Plan

1. `OrgDescendantExpander` 新增 `expandWithDescendantsIncludingDeleted` 方法，覆盖单元测试：根组织被删除、子孙组织被删除、混合场景。
2. `AppSyncOrgScopeResolver.resolveAllowedOrgIds` 改用新方法，覆盖单元测试验证 `allowedOrgIds` 包含已删除组织。
3. 端到端验证：创建组织树并配置应用范围，删除范围根组织与范围内某个子孙组织，确认 `/open/api/sync/pull` 对 `ORG`/`USER`/`POSITION` 三个数据域都能拉到这些已删除组织及其归属的用户/任职记录，且 `bizStatus` 正确反映已删除状态；同时验证管理员管辖范围（`OrgScopeService`）与访问授权策略（`PolicyExecutionServiceImpl`）两处现有行为不受影响（已删除组织依然不出现在管理员范围/策略匹配结果里）。
4. 运行现有回归测试（`org.*`、`sync.*`、`appaccess.policy.*`），确认无回归。
