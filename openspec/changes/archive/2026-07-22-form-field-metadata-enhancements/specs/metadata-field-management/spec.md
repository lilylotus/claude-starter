## ADDED Requirements

### Requirement: 元数据字段目录的字段标识可编辑
系统 SHALL 为 `tab_metadata_field` 提供字段标识（`fieldCode`）属性，用于在"表单字段定义"选择/切换绑定的元数据字段时自动带出对应的字段标识。`fieldCode` SHALL 通过数据库迁移预置初始值，存量数据 SHALL 按 `columnName` 的下划线转驼峰规则回填（如 `id_card` → `idCard`、`show_order` → `showOrder`），回填结果 SHALL 与 `tab_form_field_definition` 中已绑定该元数据字段的定义当时的 `fieldCode` 保持一致。与 `tableName`/`columnName`/`columnType` 不同，`fieldCode` SHALL 可通过更新元数据字段的接口修改（与 `fieldName` 同等对待），且 SHALL 保证在同一 `bizType` 下唯一。

#### Scenario: 元数据字段记录包含字段标识
- **WHEN** 客户端查询 `bizType=USER` 的元数据字段列表
- **THEN** 返回结果中 `columnName=id_card` 的记录 `fieldCode=idCard`

#### Scenario: 字段标识可通过更新接口修改
- **WHEN** 客户端调用更新元数据字段的接口，请求体中 `fieldCode` 改为同 `bizType` 下当前未被占用的新值
- **THEN** 系统保存该次更新，该记录的 `fieldCode` 变为新值

#### Scenario: 修改为同业务对象类型下已被占用的字段标识时拒绝
- **WHEN** 客户端调用更新元数据字段的接口，请求体中的 `fieldCode` 与同 `bizType` 下另一条记录当前的 `fieldCode` 相同
- **THEN** 系统拒绝该次更新，返回业务错误，该记录的 `fieldCode` 保持原值不变

#### Scenario: 同一业务对象类型下字段标识唯一
- **WHEN** 数据库迁移、后续数据订正或并发的更新请求尝试为同一 `bizType` 下的两条元数据字段写入相同的 `fieldCode`
- **THEN** 该操作因应用层校验或数据库唯一约束冲突而失败，数据库中不存在同一 `bizType` 下 `fieldCode` 重复的两条记录

## MODIFIED Requirements

### Requirement: 元数据字段配置数据模型
系统 SHALL 提供 `tab_metadata_field` 表记录组织（`ORG`）、人员（`USER`）、任职（`POSITION`）、应用（`APP`）四类业务对象"可开放配置"的表字段目录，每条记录 SHALL 包含业务对象类型（`bizType`）、字段所属表名称（`tableName`）、字段列名（`columnName`，数据库字段定义）、字段类型（`columnType`，数据库字段类型）、字段标识（`fieldCode`）、字段名称（`fieldName`）、状态。

#### Scenario: 元数据字段记录包含表名称、列名、列类型、字段标识与展示名称
- **WHEN** 客户端查询 `bizType=ORG` 的元数据字段列表
- **THEN** 返回结果中存在一条记录，`tableName=tab_org`、`columnName=code`、`columnType` 为数据库字段类型描述（如 `VARCHAR(255)`）、`fieldCode=code`、`fieldName="组织编码"`

### Requirement: 查询可用于新增表单字段定义的元数据字段
系统 SHALL 提供按 `bizType` 查询"可用"元数据字段的接口，返回该 `bizType` 下状态为启用（`2000`）且未被任何有效表单字段定义绑定的元数据字段列表，供表单管理新增字段定义时选择。该接口 SHALL 支持可选的 `excludeDefinitionId`（表单字段定义 id）参数：传入时，系统 SHALL 额外把该表单字段定义当前绑定的元数据字段（若状态为启用）一并纳入返回列表，供表单管理编辑字段定义时选择"改绑"的目标（含保留当前绑定不变）。

#### Scenario: 查询组织业务对象的可用元数据字段
- **WHEN** 客户端调用 `GET /api/metadata-fields/available?bizType=ORG`
- **THEN** 系统返回 `bizType=ORG` 下状态为启用、且当前未被任何有效表单字段定义绑定的元数据字段列表

#### Scenario: 已被绑定的元数据字段不出现在可用列表中
- **WHEN** 某条元数据字段已被一条有效的表单字段定义绑定
- **THEN** 该元数据字段不出现在 `GET /api/metadata-fields/available` 的返回结果中

#### Scenario: 编辑场景下当前绑定的元数据字段仍出现在可选列表中
- **WHEN** 客户端调用 `GET /api/metadata-fields/available?bizType=USER&excludeDefinitionId={id}`，`{id}` 对应的定义当前绑定元数据字段 A
- **THEN** 返回结果中除其余未被占用的启用元数据字段外，还包含元数据字段 A（即便 A 已被 `{id}` 这条定义占用）

#### Scenario: excludeDefinitionId 对应定义不存在或已删除时按普通可用查询处理
- **WHEN** 客户端调用 `GET /api/metadata-fields/available?bizType=USER&excludeDefinitionId={id}`，`{id}` 不存在或对应定义已被逻辑删除
- **THEN** 系统忽略该参数，按不携带 `excludeDefinitionId` 的可用查询逻辑返回结果

### Requirement: 元数据配置查询接口
系统 SHALL 提供元数据字段的分页查询（按 `bizType` 过滤）与详情查询接口，返回结果 SHALL 包含 `fieldCode`。

#### Scenario: 按业务对象类型分页查询元数据字段
- **WHEN** 客户端调用 `GET /api/metadata-fields?bizType=USER&page=1&pageSize=10`
- **THEN** 系统返回 `bizType=USER` 的元数据字段分页列表

#### Scenario: 查询元数据字段详情
- **WHEN** 客户端调用 `GET /api/metadata-fields/{id}` 且该记录存在
- **THEN** 系统返回该记录的完整信息，包含 `tableName`、`columnName`、`columnType`、`fieldCode`、`fieldName`、`status`

### Requirement: 元数据配置前端界面
系统 SHALL 在"系统管理"菜单下提供"元数据配置"页面（路径 `/system/metadata-fields`），支持按业务对象类型（组织/人员/任职/应用）切换查看对应的元数据字段列表，支持编辑字段名称、字段标识与状态，支持查看字段详情（含字段标识）；页面 SHALL NOT 提供新增或删除元数据字段的入口。

#### Scenario: 切换业务对象类型查看元数据字段列表
- **WHEN** 用户在元数据配置页面切换到"任职"分类
- **THEN** 页面展示 `bizType=POSITION` 的元数据字段列表

#### Scenario: 编辑元数据字段的展示名称与字段标识
- **WHEN** 用户在元数据配置页面对某条记录点击"编辑"并修改字段名称、字段标识后保存
- **THEN** 系统保存新的字段名称与字段标识，该记录的表名称/字段列名/字段类型保持不变

#### Scenario: 字段标识为空或与同业务对象类型下其他记录重复时拒绝保存
- **WHEN** 用户在元数据配置页面编辑弹窗中把"字段标识"清空，或改为与同业务对象类型下另一条记录相同的值后点击保存
- **THEN** 页面提示错误，不提交或保存失败，该记录的字段标识保持原值

#### Scenario: 查看元数据字段详情包含字段标识
- **WHEN** 用户在元数据配置页面点击某条记录的"详情"
- **THEN** 页面展示该记录的表名称、字段列名、字段类型、字段标识、字段名称、状态等完整信息
