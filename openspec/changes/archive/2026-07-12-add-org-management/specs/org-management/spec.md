## ADDED Requirements

### Requirement: 组织树查询
系统 SHALL 提供组织树查询接口，返回按上下级关系（`parentId`）组装成的嵌套树形结构，且不包含已逻辑删除的组织。

#### Scenario: 查询组织树
- **WHEN** 客户端调用 `GET /api/orgs/tree`
- **THEN** 系统返回嵌套树形结构，每个节点包含 `id`、`name`、`code`、`parentId`、`status`、`showOrder`、`children` 字段

#### Scenario: 树形结构不包含已删除组织
- **WHEN** 某组织的 `status` 为 `-1000`（已逻辑删除）
- **THEN** 该组织不出现在 `GET /api/orgs/tree` 的返回结果中（其未删除的子组织若能通过其他路径挂接则正常返回，本场景仅约束已删除节点本身不可见）

#### Scenario: 同级节点按显示序号排序
- **WHEN** 多个组织拥有相同的 `parentId`
- **THEN** 返回结果中这些同级节点按 `showOrder` 降序排列（值越大越靠前），`showOrder` 相同时按 `id` 升序排列

### Requirement: 直属子组织查询
系统 SHALL 提供按上级组织 id 查询其直属子组织列表的接口，仅返回下一层级，不包含更深层级的子孙组织。

#### Scenario: 查询指定组织的直属子组织
- **WHEN** 客户端调用 `GET /api/orgs/children?parentId={id}`
- **THEN** 系统返回 `parentId` 等于该 id 的未删除组织列表，不包含孙子级组织

#### Scenario: 未指定 parentId 时查询顶级组织
- **WHEN** 客户端调用 `GET /api/orgs/children` 且未携带 `parentId` 参数
- **THEN** 系统按 `parentId = 0` 处理，返回顶级组织列表

### Requirement: 组织详情查询
系统 SHALL 提供按 id 查询组织详情的接口，返回结果包含上级组织名称。

#### Scenario: 查询存在的组织
- **WHEN** 客户端调用 `GET /api/orgs/{id}` 且该组织存在且未被删除
- **THEN** 系统返回该组织的完整信息，包括根据 `parentId` 回填的 `parentName`

#### Scenario: 查询不存在的组织
- **WHEN** 客户端调用 `GET /api/orgs/{id}` 且该 id 不存在或已被逻辑删除
- **THEN** 系统返回业务错误（非零 `code`），不返回 HTTP 500

### Requirement: 新增组织
系统 SHALL 支持创建组织，组织名称和编码为必填项，且编码在未被逻辑删除的组织范围内必须唯一。新建组织默认状态为启用（`2000`）。

#### Scenario: 成功创建组织
- **WHEN** 客户端调用 `POST /api/orgs`，携带合法的 `name`、`code`、`parentId`、`showOrder`，且 `code` 在未删除组织中不重复
- **THEN** 系统创建该组织，状态为 `2000`（启用），并返回创建后的组织信息

#### Scenario: 编码重复时拒绝创建
- **WHEN** 客户端调用 `POST /api/orgs`，其 `code` 与某个未被逻辑删除的组织重复
- **THEN** 系统拒绝创建，返回业务错误（非零 `code`）

#### Scenario: 编码可在原组织删除后被复用
- **WHEN** 某组织 A 的 `code` 为 `X`，A 被逻辑删除后，客户端创建新组织并使用相同的 `code` `X`
- **THEN** 系统允许创建成功（唯一性校验仅针对未删除组织）

### Requirement: 更新组织
系统 SHALL 支持更新组织的名称、编码、上级组织、显示序号；编码唯一性校验范围为未被逻辑删除的组织，且排除被更新组织自身。更新接口不修改组织状态。

#### Scenario: 成功更新组织
- **WHEN** 客户端调用 `PUT /api/orgs/{id}`，携带合法的 `name`、`code`、`parentId`、`showOrder`
- **THEN** 系统更新该组织信息并返回更新后的结果，`status` 保持不变

#### Scenario: 编码与其他组织重复时拒绝更新
- **WHEN** 客户端调用 `PUT /api/orgs/{id}`，其 `code` 与另一个未删除组织重复（非自身）
- **THEN** 系统拒绝更新，返回业务错误

### Requirement: 组织启用与停用
系统 SHALL 提供独立的接口将组织状态切换为启用（`2000`）或停用（`3000`），与新增/更新接口分离。

#### Scenario: 启用组织
- **WHEN** 客户端调用 `PUT /api/orgs/{id}/enable`
- **THEN** 系统将该组织 `status` 置为 `2000` 并返回更新后的组织信息

#### Scenario: 停用组织
- **WHEN** 客户端调用 `PUT /api/orgs/{id}/disable`
- **THEN** 系统将该组织 `status` 置为 `3000` 并返回更新后的组织信息

### Requirement: 组织逻辑删除
系统 SHALL 支持对组织执行逻辑删除（将 `status` 置为 `-1000`），不做物理删除；当组织存在未被逻辑删除的直属子组织时，系统拒绝删除。

#### Scenario: 成功删除无子组织的组织
- **WHEN** 客户端调用 `DELETE /api/orgs/{id}`，且该组织不存在任何未删除的直属子组织
- **THEN** 系统将该组织 `status` 置为 `-1000`，该组织此后不再出现在树查询、子组织查询、详情查询的结果中

#### Scenario: 存在未删除子组织时拒绝删除
- **WHEN** 客户端调用 `DELETE /api/orgs/{id}`，且该组织存在至少一个未被逻辑删除的直属子组织
- **THEN** 系统拒绝删除，返回业务错误（非零 `code`），该组织状态不变

### Requirement: 组织状态语义
系统 SHALL 使用统一的整型状态码表达组织的启停用与删除语义：`2000` 表示启用，`3000` 表示停用，`-1000` 表示已逻辑删除；三者互斥，任意时刻组织只处于其中一种状态。

#### Scenario: 状态码含义一致
- **WHEN** 系统返回任意组织的 `status` 字段
- **THEN** 其值必为 `2000`、`3000`、`-1000` 三者之一，分别代表启用、停用、已删除

### Requirement: 组织管理前端界面
系统 SHALL 提供组织管理页面（路径 `/identity/orgs`），左侧展示组织树，右侧以表格展示当前选中组织节点的直属子组织数据；未选中任何节点时右侧不展示数据。

#### Scenario: 默认未选中节点时右侧为空
- **WHEN** 用户打开组织管理页面且尚未点击左侧树的任何节点
- **THEN** 右侧表格不展示任何组织数据

#### Scenario: 选中树节点后展示其直属子组织
- **WHEN** 用户点击左侧组织树中的某个节点
- **THEN** 右侧表格展示该节点的直属子组织列表（通过直属子组织查询接口获取），列表包含组织名称、编码、状态、显示序号、新增人、新增时间、更新人、更新时间

#### Scenario: 新增组织时上级组织默认值
- **WHEN** 用户在已选中某个树节点的情况下点击"新增"
- **THEN** 新增表单的上级组织默认预填为当前选中的节点；若用户未选中任何节点，则默认新增为顶级组织（`parentId` 为 `0`）

#### Scenario: 防止选择自身或子孙节点作为上级组织
- **WHEN** 用户编辑某个组织并在上级组织选择器中操作
- **THEN** 该组织自身及其所有子孙组织不可被选为其自己的上级组织

#### Scenario: 表格操作触发的状态变更实时可见
- **WHEN** 用户在右侧表格中对某行执行启用、停用或删除操作
- **THEN** 操作成功后左侧组织树与右侧表格的数据均刷新，以反映最新状态
