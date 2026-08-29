## Why

`app-sync-notify-pull` 现有规范明确要求"停用/已删除记录仍会被拉取到"（`openspec/specs/app-sync-notify-pull/spec.md` Scenario："停用/已删除记录仍会被拉取到"），`ORG`/`USER`/`POSITION`/`APP`/`ROLE` 五类同步实体的 `/open/api/sync/pull` 查询 SQL 本身也确实都没有过滤 `status` 字段。但实际验证发现，`ORG`/`USER`/`POSITION` 三个"按组织范围过滤"的数据域存在一个隐藏 bug：组织范围解析共用的 `OrgDescendantExpander.expandWithDescendants` 在展开 `includeChildren=true` 的范围配置时，对根组织和子孙组织都加了 `status != DELETED` 的过滤条件——这是为 `auth.OrgScopeService`（管理员管辖组织范围，删除的组织本就不该出现在管理员可操作范围内）设计的，但被 `sync.AppSyncOrgScopeResolver.resolveAllowedOrgIds`（应用同步组织范围）直接复用。

后果：一旦某个组织被逻辑删除（`status=-1000`），它会从 `allowedOrgIds` 里连带消失——如果这个组织正是某应用配置的 `includeChildren=true` 范围根组织，则整棵子树都会瞬间从 `allowedOrgIds` 里消失。`/open/api/sync/pull` 对 `ORG`/`USER`/`POSITION` 三个数据域都是用 `id`/`org_id` IN `allowedOrgIds` 下推过滤的，一旦某条记录（或其归属组织）不在 `allowedOrgIds` 里，即使 `tab_org`/`tab_user`/`tab_user_position` 里这一行物理上依然存在且 `status=-1000`，也会从拉取结果里彻底消失。外部应用因此无法通过 `pull` 结果里的 `status=-1000` 判断某条记录已被删除，只能看到"这条记录突然不返回了"，无法据此清理/管理自己缓存的数据（尤其在按 `code` 做增量比对时，缺失的记录既可能是真的越出范围，也可能是被删除，两种语义被现有实现混为一谈）。

## What Changes

- 新增一个只供应用同步组织范围解析使用的组织子孙展开方法，与 `OrgDescendantExpander.expandWithDescendants`（供 `auth.OrgScopeService` 管理员范围使用，保留现状——继续排除已删除组织）区分开：新方法在展开根组织与子孙组织时**不**排除 `status=DELETED` 的组织，保证一个组织被逻辑删除后仍然留在其所属应用的 `allowedOrgIds` 里。
- `sync.AppSyncOrgScopeResolver.resolveAllowedOrgIds`（供 `/open/api/sync/pull` 的 `ORG`/`USER`/`POSITION` 三个数据域，以及 `isOrgIdWithinScope`/`isUserWithinScope`/`filterUsersWithinScope` 使用）改为调用这个新方法，不再间接排除已删除组织。
- 不改变 `resolveScopePrefixes`（供 `/open/api/sync/changes` 增量游标接口使用）——它走的是 `orgPath` 前缀匹配，不经过 `OrgDescendantExpander`，本来就不受这个 bug 影响，属于本次 Non-Goals。
- 不改变 `auth.OrgScopeService`/管理员管辖组织范围的现有行为：管理员可操作范围继续排除已删除组织，这是正确语义，不属于本次修复范围。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `app-sync-notify-pull`：明确"组织范围解析（供 `/pull` 的 `ORG`/`USER`/`POSITION` 三个数据域使用）在展开 `includeChildren` 子孙时不得因为组织被逻辑删除而将其排除出范围"这一约束，并补充对应场景，防止后续实现回归为共用管理员范围的排除已删除组织语义。

## Impact

- **后端**：新增 `OrgDescendantExpander`（或 `AppSyncOrgScopeResolver` 内部）一个不排除已删除组织的展开方法；`AppSyncOrgScopeResolver.resolveAllowedOrgIds` 改用该方法；`auth.OrgScopeService` 用法不变。
- **数据库**：无迁移。
- **前端**：无影响，本次不改管理端页面。
- **兼容性**：修复后，之前因为组织被删除而"消失"的 ORG/USER/POSITION 记录会重新出现在 `/pull` 结果里（`status=-1000`）；已经把"记录消失"当作删除信号硬编码的外部应用不受影响（`status=-1000` 同样能让它们判定为删除，只是现在能明确看到这条记录，而不是无声消失），因为组织确实离开某应用范围（未删除但被移出范围）的场景不受影响，继续保持"查不到"的语义。
