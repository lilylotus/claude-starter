## MODIFIED Requirements

### Requirement: 组织树查询
系统 SHALL 提供组织树查询接口，返回按上下级关系（`parentId`）组装成的嵌套树形结构，且不包含已逻辑删除的组织。当前登录用户的管辖组织范围解析结果为受限时，返回的树形结构 SHALL 只包含解析结果允许的组织节点；若某个允许节点的真实上级组织不在允许范围内，该节点 SHALL 作为返回结果中的根节点出现，不 SHALL 展示其真实祖先节点。

#### Scenario: 查询组织树
- **WHEN** 客户端调用 `GET /api/orgs/tree`
- **THEN** 系统返回嵌套树形结构，每个节点包含 `id`、`name`、`code`、`parentId`、`parentCode`、`status`、`showOrder`、`children` 字段

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

#### Scenario: 顶级组织节点的上级组织编码为空
- **WHEN** 客户端调用 `GET /api/orgs/tree`，返回结果中某节点的 `parentId` 为 `0`（顶级组织）
- **THEN** 该节点的 `parentCode` 为空

### Requirement: 组织树懒加载子节点查询
系统 SHALL 提供一个不分页的直属子组织查询接口，专门供前端组织树逐层懒加载展开使用；仅返回下一层级，不包含更深层级的子孙组织，且不包含已逻辑删除的组织，排序规则与其他组织列表查询一致（`showOrder` 降序，相同时按 `id` 升序）。当前登录用户的管辖组织范围解析结果为受限时：查询顶级节点（未指定 `parentId` 或 `parentId = 0`）SHALL 返回允许范围内、其真实上级组织不在允许范围内的节点（即受限视角下的根节点）；查询某个具体节点的直属子节点时，若该节点本身不在允许范围内，SHALL 返回空列表；若在允许范围内，仅返回同样在允许范围内的直属子节点。

#### Scenario: 查询指定组织的直属子组织用于树展开
- **WHEN** 客户端调用 `GET /api/orgs/tree/children?parentId={id}`
- **THEN** 系统返回 `parentId` 等于该 id 的全部未删除直属子组织列表（不分页），每个节点包含 `id`、`name`、`code`、`parentId`、`parentCode`、`status`、`showOrder`、`children` 字段，`children` 固定为空数组

#### Scenario: 未指定 parentId 时查询顶级组织
- **WHEN** 客户端调用 `GET /api/orgs/tree/children` 且未携带 `parentId` 参数
- **THEN** 系统按 `parentId = 0` 处理，返回全部未删除的顶级组织列表

#### Scenario: 管辖范围受限时顶层查询返回受限视角下的根节点
- **WHEN** 当前登录用户的管辖组织范围解析结果为受限，只包含组织 C 及其子孙；客户端调用 `GET /api/orgs/tree/children`（未指定 `parentId`）
- **THEN** 系统返回包含 C 在内的、允许范围内且其真实上级组织不在允许范围内的节点列表，不返回 C 的真实祖先节点

#### Scenario: 管辖范围受限时查询范围外节点的子节点返回空列表
- **WHEN** 当前登录用户的管辖组织范围解析结果为受限，客户端调用 `GET /api/orgs/tree/children?parentId={id}`，该 `id` 不在允许范围内
- **THEN** 系统返回空列表，不返回业务错误

### Requirement: 组织详情查询
系统 SHALL 提供按 id 查询组织详情的接口，返回结果包含上级组织名称、上级组织编码（`parentCode`）、备注（`remark`）、`ext1`~`ext10`，以及新增人、新增时间、更新人、更新时间等审计字段。

#### Scenario: 查询存在的组织
- **WHEN** 客户端调用 `GET /api/orgs/{id}` 且该组织存在且未被删除
- **THEN** 系统返回该组织的完整信息，包括根据 `parentId` 回填的 `parentName`、`parentCode`、`remark`，以及 `createBy`、`createTime`、`updateBy`、`updateTime`

#### Scenario: 查询不存在的组织
- **WHEN** 客户端调用 `GET /api/orgs/{id}` 且该 id 不存在或已被逻辑删除
- **THEN** 系统返回业务错误（非零 `code`），不返回 HTTP 500

#### Scenario: 详情查询结果包含扩展字段
- **WHEN** 客户端调用 `GET /api/orgs/{id}`，该组织 `ext1`~`ext10` 中部分列有值
- **THEN** 返回结果中包含该组织 `ext1`~`ext10` 的当前值

#### Scenario: 顶级组织的上级组织编码为空
- **WHEN** 客户端调用 `GET /api/orgs/{id}`，该组织的 `parentId` 为 `0`（顶级组织）
- **THEN** 返回结果中 `parentCode` 为空

### Requirement: 组织详情操作历史展示
系统 SHALL 在组织详情页面中展示该组织的操作历史列表：按操作发起时间降序排列，每页 5 条，支持分页；覆盖新增、编辑、启用、停用四类操作（不包含删除记录——组织被逻辑删除后其详情页面本身不可访问，历史列表天然不会出现删除记录）；每条历史记录展示操作时间、操作类型、操作人，其字段级变更详情（旧值→新值）SHALL 默认直接展示在该条记录下方，不需要额外点击即可看到，变更详情 SHALL 包含组织名称、编码、上级组织、上级组织编码（`parentCode`）、显示序号、备注、状态等核心字段，以及 `bizType=ORG` 下当前启用的扩展字段定义（`ext1`~`ext10` 中已配置字段定义的部分）对应的变更，字段标签使用该字段定义的展示名称。进入详情页面时 SHALL 展示截至当前的最新操作历史，不依赖任何缓存的旧数据。

#### Scenario: 打开组织详情页面时展示操作历史
- **WHEN** 用户打开某个组织的详情页面
- **THEN** 系统调用 `GET /api/operation-logs?resourceType=org&targetId={该组织id}&page=1&pageSize=5`，按操作发起时间降序展示该组织的操作历史，每条记录均已带有字段级变更详情

#### Scenario: 操作历史默认展示字段变更明细
- **WHEN** 用户查看组织详情页面的操作历史列表
- **THEN** 每条记录下方直接展示该次操作的字段级变更列表（字段名、旧值、新值），无需点击任何"查看变更"之类的操作即可看到

#### Scenario: 该组织没有可查询到的操作历史时展示空状态
- **WHEN** 该组织是通过数据库迁移预置（Flyway 种子数据）创建、从未经由本系统的新增/编辑/启用/停用接口产生过操作记录
- **THEN** 操作历史列表展示为空并提示"暂无操作记录"，不视为异常

#### Scenario: 离开详情页面后编辑再重新进入时展示最新操作历史
- **WHEN** 用户打开某个组织的详情页面查看历史后返回列表，随后编辑保存该组织，再次进入同一组织的详情页面
- **THEN** 操作历史列表 SHALL 重新拉取并展示包含刚才那次编辑在内的最新记录，而不是停留在上一次进入时的旧列表

#### Scenario: 修改已配置字段定义的扩展字段出现在操作历史中
- **WHEN** 某组织的 `ext2` 被编辑修改，且 `bizType=ORG` 下绑定 `ext2` 的字段定义处于启用状态
- **THEN** 本次编辑对应的操作历史记录的字段级变更详情中包含一条以该字段定义展示名称为标签、旧值→新值的变更

#### Scenario: 未配置字段定义的扩展字段不出现在操作历史中
- **WHEN** 某组织的 `ext6` 被编辑修改，但 `bizType=ORG` 下当前没有绑定 `ext6` 的启用状态字段定义
- **THEN** 本次编辑对应的操作历史记录的字段级变更详情中不出现 `ext6` 相关的条目

#### Scenario: 变更上级组织时操作历史包含上级组织编码的变更
- **WHEN** 某组织的上级组织从 A（`code=A001`）改为 B（`code=B001`），触发一次编辑操作
- **THEN** 本次编辑对应的操作历史记录的字段级变更详情中包含一条"上级组织编码"标签、旧值 `A001`→新值 `B001` 的变更

#### Scenario: 因上级组织自身编码变更而级联更新的 parentCode 不产生该子组织自己的操作历史
- **WHEN** 组织 P 的 `code` 被修改，触发其直属子组织 C 的 `parentCode` 被级联更新
- **THEN** C 自身没有因这次级联更新而新增一条操作历史记录（级联更新不经过 C 的新增/编辑/启用/停用接口，只有直接对 C 发起的操作才会在 C 的操作历史中出现）

## ADDED Requirements

### Requirement: 组织上级编码的自动维护
系统 SHALL 为每个组织持久化上级组织编码（`parentCode`），其值 SHALL 恒等于该组织当前 `parentId` 所指向的父组织的 `code`；顶级组织（`parentId=0`）的 `parentCode` SHALL 为空。该字段完全由系统根据上下级关系派生维护，不接受客户端在新增/更新组织接口中直接指定其取值（请求体中即使携带该字段也 SHALL 被忽略）。

创建组织时，系统 SHALL 在写入组织记录的同一事务内，按请求的 `parentId` 解析对应父组织当前的 `code` 并一并写入 `parentCode`。更新组织时，仅当本次更新导致 `parentId` 发生变化（即变更了上级组织）时，系统 SHALL 重新解析新上级组织的 `code` 并更新 `parentCode`；未变更上级组织时 `parentCode` 保持不变。当某组织自身的 `code` 因更新而发生变化时，系统 SHALL 在同一事务内将其全部未被逻辑删除的直属子组织的 `parentCode` 级联更新为新值，不需要递归到孙级及更深层级。

#### Scenario: 新建非顶级组织时自动派生上级组织编码
- **WHEN** 客户端调用 `POST /api/orgs` 创建一个组织，`parentId` 指向一个 `code` 为 `ORG001` 的既有组织
- **THEN** 系统创建成功，新组织的 `parentCode` 为 `ORG001`

#### Scenario: 新建顶级组织时上级组织编码为空
- **WHEN** 客户端调用 `POST /api/orgs` 创建一个 `parentId=0` 的顶级组织
- **THEN** 系统创建成功，新组织的 `parentCode` 为空

#### Scenario: 变更上级组织时重新派生上级组织编码
- **WHEN** 客户端调用 `PUT /api/orgs/{id}`，将某组织的 `parentId` 从组织 A（`code=A001`）改为组织 B（`code=B001`）
- **THEN** 系统更新成功，该组织的 `parentCode` 变为 `B001`

#### Scenario: 未变更上级组织时上级组织编码保持不变
- **WHEN** 客户端调用 `PUT /api/orgs/{id}`，请求携带的 `parentId` 与该组织当前 `parentId` 相同，仅修改其他字段
- **THEN** 系统更新成功，该组织的 `parentCode` 保持原值不变

#### Scenario: 父组织编码变更后级联更新直属子组织
- **WHEN** 组织 P（`code=P001`）存在两个未被逻辑删除的直属子组织 C1、C2，客户端调用 `PUT /api/orgs/{P的id}` 将 P 的 `code` 改为 `P002`
- **THEN** 系统更新成功后，C1、C2 的 `parentCode` 均变为 `P002`，P 的孙级组织的 `parentCode`（指向 C1/C2 的 `code`，未变化）保持不变

#### Scenario: 父组织编码变更不影响已删除的子组织
- **WHEN** 组织 P 存在一个已被逻辑删除的直属子组织 C3，客户端调用更新接口修改 P 的 `code`
- **THEN** 系统不更新 C3 的 `parentCode`

### Requirement: 组织详情页面展示上级组织编码
组织详情页面（`/identity/orgs/:id`）SHALL 在"上级组织"名称展示项附近以只读方式展示该组织的上级组织编码（`parentCode`）；顶级组织该展示项为空。新增/编辑组织的表单 SHALL NOT 提供该字段的输入项，其取值完全由后端根据所选上级组织自动派生。

#### Scenario: 详情页面展示上级组织编码
- **WHEN** 用户打开某个非顶级组织的详情页面
- **THEN** 页面在上级组织名称附近展示该组织的上级组织编码

#### Scenario: 顶级组织详情页面上级组织编码为空
- **WHEN** 用户打开某个 `parentId=0` 的顶级组织的详情页面
- **THEN** 页面对应展示项为空，不显示占位错误文案
