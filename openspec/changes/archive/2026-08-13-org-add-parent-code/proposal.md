## Why

组织数据后续需要同步给外部应用（`app-api-credentials` 能力已经具备"组织数据域"的同步配置与字段映射能力）。当前组织之间的上下级关系只通过内部自增主键 `parentId` 表达，而这个 id 是本系统私有的，一旦同步到外部应用，外部系统里对应记录的 id 可能与本系统不一致（应用方可能会重新分配）；相反，组织编码（`code`）是业务侧维护的稳定标识，天然适合作为跨系统关联"上级组织"的锚点。当前组织既不持久化也不对外返回上级组织的编码，外部系统拿到组织数据后无法可靠地还原上下级关系，需要补上这个字段。

## What Changes

- `tab_org` 新增持久化列 `parent_code`（对应实体属性 `parentCode`），保存创建/更新时上级组织当前的 `code`；顶级组织（`parentId=0`）该列为空。
- 新增组织、更新组织（含变更上级组织）时，系统在写入的同一事务内根据 `parentId` 回填 `parentCode`。
- 当某组织自身的 `code` 被修改时，系统在同一事务内级联更新其全部直属子组织的 `parentCode`，保持数据一致（只影响直属子组织，不需要递归到孙级——孙级的 `parentCode` 指向的是子级的 `code`，未变化）。
- 组织树查询（`GET /api/orgs/tree`）、组织树懒加载子节点查询（`GET /api/orgs/tree/children`）、直属子组织分页查询（`GET /api/orgs/children`）、组织详情查询（`GET /api/orgs/{id}`）的响应结构中新增 `parentCode` 字段。
- Flyway 迁移脚本为存量数据回填 `parent_code`（按当前 `parentId` 关联父记录的 `code` 一次性回填；顶级组织留空）。
- 前端组织详情页面新增"上级组织编码"只读展示项（跟随既有的 `parentName` 展示位置附近），不在新增/编辑表单中提供手动输入——该值完全由后端根据所选上级组织自动派生。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `org-management`：组织树查询、树懒加载子节点查询、直属子组织查询、详情查询的响应新增 `parentCode` 字段；新增/更新组织时自动维护该字段，并在上级组织 `code` 变更时级联更新直属子组织的 `parentCode`；组织详情页面展示该字段；组织详情操作历史的字段级变更快照新增该字段。
- `metadata-field-management`：`tab_metadata_field` 的 `bizType=ORG` 目录新增一条 `tab_org.parent_code` 的元数据字段记录，使"上级组织编码"可作为"表单字段定义"绑定来源与"应用同步字段映射配置"（`app-api-credentials` 能力）的源字段被选择——否则前两处新增的持久化列对这两个下游能力仍然不可见。

## Impact

- 后端：`tab_org` 表结构（新增列 + Flyway 迁移）、`OrgEntity`、`OrgMapper`/`OrgServiceImpl`（创建/更新/改编码时的级联维护逻辑）、`OrgTreeNodeVO`、`OrgVO`（详情响应）等 DTO。
- 前端：组织详情页面（`views` 下组织详情组件）新增一个只读字段展示；组织相关的 TypeScript 类型定义同步补充 `parentCode`。
- 不涉及："对外接口配置写操作的管辖组织范围校验"等既有权限校验逻辑（本变更不改变管辖范围过滤规则），也不实现真正对外推送/拉取组织数据的接口（仍是既有 Non-Goal）。
