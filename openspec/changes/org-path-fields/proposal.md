## Why

组织的"某组织及其全部子孙组织"这一查询目前由 `OrgDescendantExpander` 实现：每次调用都把全部未逻辑删除的组织整表加载进内存，用 `parentId` 建邻接表后做 BFS 遍历（因为运行环境是 MySQL 5.7，没有 `WITH RECURSIVE`）。这个组件被两处管辖范围解析复用（`OrgScopeServiceImpl` 管理员管辖组织范围、`AppSyncOrgScopeResolver` 应用同步组织范围），且不带缓存、每次请求实时计算，组织树越大这个开销越明显。给组织补一个"路径"冗余字段（`org_path`，用 `/` 分隔的 id 路径），把"展开子孙组织"从"整表加载 + Java 端 BFS"降级为一条 `LIKE 前缀` SQL 查询，是消除这个性能瓶颈最直接的办法；顺带补充组织名称路径（`org_name_path`，用于前端/日志展示完整路径而不必逐级查父级名称）与上级路径（`org_parent_path`，`org_path` 去掉自身这一段），三者共用同一套维护时机。此外，即将设计的"4A 主数据推拉同步"change 里，变更流水表需要冗余存储组织路径用于按机构范围前缀过滤，也依赖这里先落地 `org_path`。

## What Changes

- `tab_org` 新增三个字段：`org_path`（id 路径，如 `1/2/3`）、`org_name_path`（名称路径，如 `一级组织/二级组织/三级组织`）、`org_parent_path`（上级路径，即 `org_path` 去掉最后一段，顶级组织为空）。
- 创建组织时按上级组织当前的 `org_path`/`org_name_path` 拼接写入新组织自身的三个路径字段。
- 变更组织的上级组织（`parentId` 改变）时，重新计算该组织自身的 `org_path`/`org_parent_path`，并级联更新其**全部**子孙组织（不止直属子组织）的 `org_path`/`org_parent_path`——用一条 `SUBSTRING` 前缀替换的 `UPDATE` 语句一次性完成，不在 Java 侧递归。
- 组织改名（`name` 变化）时，级联更新该组织**全部**子孙组织的 `org_name_path`（因为改名的可能是路径中间某一段），用同样的一条 `UPDATE` 语句完成。
- **BREAKING（内部实现，不改变对外契约）**：`OrgDescendantExpander.expandWithDescendants` 的实现从"整表加载 + BFS"改为按 `org_path` 前缀查询，方法签名与语义不变，`OrgScopeServiceImpl`/`AppSyncOrgScopeResolver` 两个调用方不需要跟着改。
- 新增一条 Flyway 迁移脚本，给 `tab_org` 存量数据回填三个路径字段的初始值（按层级从根往下滚动多轮 `UPDATE`，不使用递归 CTE）。
- `OrgVO`/`OrgTreeNodeVO` 可选追加这三个字段供前端展示（非强制，见 design.md 建议）。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `org-management`：新增一条独立的"组织路径字段自动维护"Requirement，说明三个字段的语义、维护时机（创建时写入、`parentId` 变化时级联重算、改名时级联重算）与级联范围（全部子孙，不止直属子级）。

`org-scope-data-permission` 的"解析当前登录用户管辖组织范围"能力不在此列：`OrgDescendantExpander` 从整表 BFS 改为路径前缀查询是纯内部实现优化，输入输出契约（给定根组织集合，返回自身+全部未删除子孙 id 的并集）与已有全部 Scenario 完全不变，不产生新的可观察行为，因此不需要该能力的 delta spec。

## Impact

- **数据库**：新增 Flyway 迁移脚本，`tab_org` 加三列 + 索引（`org_path` 需要支持前缀 `LIKE` 查询，建普通 `KEY` 索引即可，MySQL 对 `LIKE 'prefix%'` 能利用最左前缀索引）；存量数据回填脚本。
- **后端**：`OrgEntity`/`OrgVO`/`OrgTreeNodeVO`/`OrgConvert`（新增字段映射）、`OrgServiceImpl`（`create`/`update` 内维护路径字段）、`OrgMapper`（新增按前缀级联更新的方法，写在 `OrgMapper.xml`）、`OrgDescendantExpander`（改用路径前缀查询取代整表 BFS）。
- **不涉及**：审批流程本身的分流逻辑不变（路径维护发生在 `OrgServiceImpl.create`/`update` 内部，与调用方是直接调用还是审批通过后调用无关，天然对齐"审批通过、真正执行创建/更新那一刻"这个时机）；不改变任何对外 API 的请求/响应契约中的必填字段，新增字段是纯追加。
