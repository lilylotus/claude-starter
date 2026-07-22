## ADDED Requirements

### Requirement: 元数据字段配置数据模型
系统 SHALL 提供 `tab_metadata_field` 表记录组织（`ORG`）、人员（`USER`）、任职（`POSITION`）、应用（`APP`）四类业务对象"可开放配置"的表字段目录，每条记录 SHALL 包含业务对象类型（`bizType`）、字段所属表名称（`tableName`）、字段列名（`columnName`，数据库字段定义）、字段类型（`columnType`，数据库字段类型）、字段名称（`fieldName`）、状态。

#### Scenario: 元数据字段记录包含表名称、列名、列类型与展示名称
- **WHEN** 客户端查询 `bizType=ORG` 的元数据字段列表
- **THEN** 返回结果中存在一条记录，`tableName=tab_org`、`columnName=code`、`columnType` 为数据库字段类型描述（如 `VARCHAR(255)`）、`fieldName="组织编码"`

### Requirement: 默认初始化四类业务对象的元数据字段目录
系统 SHALL 通过数据库迁移默认初始化组织、人员、任职、应用四类业务对象的元数据字段目录，覆盖各自"可开放配置的原有表字段"与全部 `ext1`~`ext10` 扩展字段；已有专用交互控件的字段（组织的 `parentId`、任职的 `orgId`/`userId`/`positionType`、应用的 `ownerId`/`orgId`，以及四类对象共有的 `status`，用户的 `gender`）SHALL 不出现在该目录中。

#### Scenario: 迁移完成后可查询到四类业务对象的元数据字段
- **WHEN** 系统完成数据库迁移后，客户端分别查询 `bizType=ORG`/`USER`/`POSITION`/`APP` 的元数据字段列表
- **THEN** 每个 `bizType` 下都能查询到对应的原有可配置字段记录与 10 条 `ext1`~`ext10` 记录

#### Scenario: 专用控件字段不出现在元数据目录中
- **WHEN** 客户端查询 `bizType=ORG` 的元数据字段列表
- **THEN** 结果中不包含 `columnName` 为 `parent_id` 或 `status` 的记录

### Requirement: 元数据字段的物理属性创建后不可修改
系统 SHALL 保证元数据字段记录的 `bizType`、`tableName`、`columnName`、`columnType` 一经写入（迁移种子数据）不可通过接口修改；只有 `fieldName`、`status` 可编辑。

#### Scenario: 更新请求尝试修改列名被忽略
- **WHEN** 客户端调用更新元数据字段的接口，请求体中 `columnName` 与当前值不同
- **THEN** 系统保存 `fieldName`/`status` 等可编辑属性的更新，但该记录的 `columnName`/`tableName`/`columnType`/`bizType` 保持原值不变

#### Scenario: 展示名称可自由调整
- **WHEN** 客户端调用更新元数据字段的接口，仅改动 `fieldName`
- **THEN** 系统正常保存该次更新

### Requirement: 元数据字段目录不支持前端新增或删除
系统 SHALL 不提供创建新元数据字段记录的接口，目录只能通过数据库迁移预置；系统 SHALL 不提供物理或逻辑删除元数据字段记录的接口，只能通过启用/停用接口调整其状态。

#### Scenario: 不存在新增元数据字段的接口
- **WHEN** 客户端尝试调用任意"新增元数据字段"相关接口
- **THEN** 系统不提供该接口（路由不存在）

### Requirement: 元数据字段的启用与停用
系统 SHALL 提供独立的接口将元数据字段状态切换为启用（`2000`）或停用（`3000`）；当某元数据字段当前被至少一条有效（`status != -1000`）的表单字段定义绑定时，系统 SHALL 拒绝将其停用。

#### Scenario: 停用未被绑定的元数据字段
- **WHEN** 客户端对一条当前未被任何有效表单字段定义绑定的元数据字段调用停用接口
- **THEN** 系统将其状态置为 `3000`

#### Scenario: 停用已被绑定的元数据字段被拒绝
- **WHEN** 客户端对一条当前被某条有效表单字段定义绑定的元数据字段调用停用接口
- **THEN** 系统拒绝停用，返回业务错误，该元数据字段状态保持不变

### Requirement: 查询可用于新增表单字段定义的元数据字段
系统 SHALL 提供按 `bizType` 查询"可用"元数据字段的接口，返回该 `bizType` 下状态为启用（`2000`）且未被任何有效表单字段定义绑定的元数据字段列表，供表单管理新增字段定义时选择。

#### Scenario: 查询组织业务对象的可用元数据字段
- **WHEN** 客户端调用 `GET /api/metadata-fields/available?bizType=ORG`
- **THEN** 系统返回 `bizType=ORG` 下状态为启用、且当前未被任何有效表单字段定义绑定的元数据字段列表

#### Scenario: 已被绑定的元数据字段不出现在可用列表中
- **WHEN** 某条元数据字段已被一条有效的表单字段定义绑定
- **THEN** 该元数据字段不出现在 `GET /api/metadata-fields/available` 的返回结果中

### Requirement: 元数据配置查询接口
系统 SHALL 提供元数据字段的分页查询（按 `bizType` 过滤）与详情查询接口。

#### Scenario: 按业务对象类型分页查询元数据字段
- **WHEN** 客户端调用 `GET /api/metadata-fields?bizType=USER&page=1&pageSize=10`
- **THEN** 系统返回 `bizType=USER` 的元数据字段分页列表

#### Scenario: 查询元数据字段详情
- **WHEN** 客户端调用 `GET /api/metadata-fields/{id}` 且该记录存在
- **THEN** 系统返回该记录的完整信息，包含 `tableName`、`columnName`、`columnType`、`fieldName`、`status`

### Requirement: 元数据配置前端界面
系统 SHALL 在"系统管理"菜单下提供"元数据配置"页面（路径 `/system/metadata-fields`），支持按业务对象类型（组织/人员/任职/应用）切换查看对应的元数据字段列表，支持编辑字段名称与状态，支持查看字段详情；页面 SHALL NOT 提供新增或删除元数据字段的入口。

#### Scenario: 切换业务对象类型查看元数据字段列表
- **WHEN** 用户在元数据配置页面切换到"任职"分类
- **THEN** 页面展示 `bizType=POSITION` 的元数据字段列表

#### Scenario: 编辑元数据字段的展示名称
- **WHEN** 用户在元数据配置页面对某条记录点击"编辑"并修改字段名称后保存
- **THEN** 系统保存新的字段名称，该记录的表名称/字段列名/字段类型保持不变

#### Scenario: 查看元数据字段详情
- **WHEN** 用户在元数据配置页面点击某条记录的"详情"
- **THEN** 页面展示该记录的表名称、字段列名、字段类型、字段名称、状态等完整信息
