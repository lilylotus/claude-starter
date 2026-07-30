## MODIFIED Requirements

### Requirement: 组织树查询
系统 SHALL 提供组织树查询接口，返回按上下级关系（`parentId`）组装成的嵌套树形结构，且不包含已逻辑删除的组织。当前登录用户的管辖组织范围解析结果为受限时，返回的树形结构 SHALL 只包含解析结果允许的组织节点；若某个允许节点的真实上级组织不在允许范围内，该节点 SHALL 作为返回结果中的根节点出现，不 SHALL 展示其真实祖先节点。

#### Scenario: 查询组织树
- **WHEN** 客户端调用 `GET /api/orgs/tree`
- **THEN** 系统返回嵌套树形结构，每个节点包含 `id`、`name`、`code`、`parentId`、`status`、`showOrder`、`children` 字段

#### Scenario: 树形结构不包含已删除组织
- **WHEN** 某组织的 `status` 为 `-1000`（已逻辑删除）
- **THEN** 该组织不出现在 `GET /api/orgs/tree` 的返回结果中（其未删除的子组织若能通过其他路径挂接则正常返回，本场景仅约束已删除节点本身不可见）

#### Scenario: 同级节点按显示序号排序
- **WHEN** 多个组织拥有相同的 `parentId`
- **THEN** 返回结果中这些同级节点按 `showOrder` 降序排列（值越大越靠前），`showOrder` 相同时按 `id` 升序排列

#### Scenario: 管辖范围受限时组织树被收窄
- **WHEN** 当前登录用户的管辖组织范围解析结果为受限，只包含组织 C 及其子孙（C 是根组织 A 下 B 的子组织）
- **THEN** `GET /api/orgs/tree` 返回的树只包含 C 及其未删除的子孙节点，C 在返回结果中作为根节点出现，A、B 均不出现在返回结果中

#### Scenario: 管辖范围不受限时行为不变
- **WHEN** 当前登录用户没有配置管辖组织范围（解析结果不受限）
- **THEN** `GET /api/orgs/tree` 返回全部未删除组织组装成的完整树，行为与本次改动之前一致

### Requirement: 组织树懒加载子节点查询
系统 SHALL 提供一个不分页的直属子组织查询接口，专门供前端组织树逐层懒加载展开使用；仅返回下一层级，不包含更深层级的子孙组织，且不包含已逻辑删除的组织，排序规则与其他组织列表查询一致（`showOrder` 降序，相同时按 `id` 升序）。当前登录用户的管辖组织范围解析结果为受限时：查询顶级节点（未指定 `parentId` 或 `parentId = 0`）SHALL 返回允许范围内、其真实上级组织不在允许范围内的节点（即受限视角下的根节点）；查询某个具体节点的直属子节点时，若该节点本身不在允许范围内，SHALL 返回空列表；若在允许范围内，仅返回同样在允许范围内的直属子节点。

#### Scenario: 查询指定组织的直属子组织用于树展开
- **WHEN** 客户端调用 `GET /api/orgs/tree/children?parentId={id}`
- **THEN** 系统返回 `parentId` 等于该 id 的全部未删除直属子组织列表（不分页），每个节点包含 `id`、`name`、`code`、`parentId`、`status`、`showOrder`、`children` 字段，`children` 固定为空数组

#### Scenario: 未指定 parentId 时查询顶级组织
- **WHEN** 客户端调用 `GET /api/orgs/tree/children` 且未携带 `parentId` 参数
- **THEN** 系统按 `parentId = 0` 处理，返回全部未删除的顶级组织列表

#### Scenario: 管辖范围受限时顶层查询返回受限视角下的根节点
- **WHEN** 当前登录用户的管辖组织范围解析结果为受限，只包含组织 C 及其子孙；客户端调用 `GET /api/orgs/tree/children`（未指定 `parentId`）
- **THEN** 系统返回包含 C 在内的、允许范围内且其真实上级组织不在允许范围内的节点列表，不返回 C 的真实祖先节点

#### Scenario: 管辖范围受限时查询范围外节点的子节点返回空列表
- **WHEN** 当前登录用户的管辖组织范围解析结果为受限，客户端调用 `GET /api/orgs/tree/children?parentId={id}`，该 `id` 不在允许范围内
- **THEN** 系统返回空列表，不返回业务错误

### Requirement: 直属子组织查询
系统 SHALL 提供按上级组织 id 分页查询其直属子组织列表的接口，仅返回下一层级，不包含更深层级的子孙组织；分页参数 `page`（页码，默认 `1`）、`pageSize`（每页条数，默认 `10`）均为可选，响应中 SHALL 包含当前页数据、总条数、页码、每页条数。当前登录用户的管辖组织范围解析结果为受限时，过滤规则与"组织树懒加载子节点查询"一致：查询顶级节点返回受限视角下的根节点，查询范围外节点的子节点返回空分页。

#### Scenario: 查询指定组织的直属子组织
- **WHEN** 客户端调用 `GET /api/orgs/children?parentId={id}&page={page}&pageSize={pageSize}`
- **THEN** 系统返回 `parentId` 等于该 id 的未删除组织中第 `page` 页、每页 `pageSize` 条的分页结果，包含 `records`（当前页数据列表，不含孙子级组织）、`total`（未删除的直属子组织总数）、`page`、`pageSize`

#### Scenario: 未指定 parentId 时查询顶级组织
- **WHEN** 客户端调用 `GET /api/orgs/children` 且未携带 `parentId` 参数
- **THEN** 系统按 `parentId = 0` 处理，返回顶级组织的分页结果

#### Scenario: 未指定分页参数时使用默认值
- **WHEN** 客户端调用 `GET /api/orgs/children` 且未携带 `page`、`pageSize` 参数
- **THEN** 系统按 `page = 1`、`pageSize = 10` 处理

#### Scenario: 管辖范围受限时查询范围外节点的子节点返回空分页
- **WHEN** 当前登录用户的管辖组织范围解析结果为受限，客户端调用 `GET /api/orgs/children?parentId={id}`，该 `id` 不在允许范围内
- **THEN** 系统返回 `total = 0` 的空分页结果，不返回业务错误
