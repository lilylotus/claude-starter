## MODIFIED Requirements

### Requirement: 承重字段的锁定保护
系统 SHALL 判定某条表单字段定义是否绑定到承重字段（组织/用户/应用各自的 `name`、`code`，以及任职
（POSITION）的 `position_type` 对应的元数据字段）；对绑定承重字段的定义，更新接口 SHALL 拒绝将其
状态改为非 `2000`（即不可停用），`DELETE` 接口 SHALL 拒绝删除，也 SHALL 拒绝将 `isRequired`、
`showInCreate`、`showInEdit` 改为 `false`；该类定义的展示名称、显示序号、`placeholder`、
`validateRegex`、`editable`、`isUnique`、`showInList` 仍可自由调整。

#### Scenario: 尝试停用绑定承重字段的定义被拒绝
- **WHEN** 客户端对一条绑定 `name` 或 `code` 元数据字段的定义调用停用接口
- **THEN** 系统拒绝停用，返回业务错误，该定义状态保持 `2000`

#### Scenario: 尝试删除绑定承重字段的定义被拒绝
- **WHEN** 客户端对一条绑定 `name` 或 `code` 元数据字段的定义调用 `DELETE /api/form-fields/{id}`
- **THEN** 系统拒绝删除，返回业务错误

#### Scenario: 尝试将承重字段的定义配置为非必填被拒绝
- **WHEN** 客户端调用 `PUT /api/form-fields/{id}` 更新一条绑定承重字段的定义，请求体 `isRequired=false`
- **THEN** 系统拒绝该次更新中 `isRequired` 的变更，返回业务错误

#### Scenario: 承重字段定义的展示名称仍可调整
- **WHEN** 客户端调用 `PUT /api/form-fields/{id}` 更新一条绑定承重字段的定义，仅改动 `fieldName` 与 `placeholder`
- **THEN** 系统正常保存该次更新

#### Scenario: 任职类型定义受承重字段保护
- **WHEN** 客户端对 `bizType=POSITION` 下绑定 `position_type` 元数据字段的定义调用停用、删除接口，
  或调用更新接口尝试把 `isRequired`/`showInCreate`/`showInEdit` 改为 `false`
- **THEN** 系统拒绝该次操作，返回业务错误，行为与 ORG/USER/APP 的 `name`/`code` 承重字段一致
