## MODIFIED Requirements

### Requirement: 默认初始化四类业务对象的元数据字段目录
系统 SHALL 通过数据库迁移默认初始化组织、人员、任职、应用四类业务对象的元数据字段目录，覆盖各自"可开放配置的原有表字段"与全部 `ext1`~`ext10` 扩展字段；已有专用交互控件的字段（组织的 `parentId`、任职的 `orgId`/`userId`/`positionType`、应用的 `ownerId`/`orgId`，以及四类对象共有的 `status`，用户的 `gender`）SHALL 不出现在该目录中。系统 SHALL 另外通过数据库迁移初始化角色（`ROLE`）业务对象类型的元数据字段目录，覆盖角色"可开放配置的原有表字段"（`name`/`code`/`show_order`/`remark`）；角色数据表 SHALL NOT 包含 `ext1`~`ext10` 扩展列，角色元数据字段目录也 SHALL NOT 包含任何 `ext` 字段记录。组织业务对象的元数据字段目录 SHALL 额外包含 `tab_org.parent_code`（"上级组织编码"，`fieldCode=parentCode`）一条记录；该字段虽然是系统自动派生、不接受用户直接编辑的只读字段，但没有专用交互控件覆盖同样的语义（不同于被排除的 `parentId`），因此不适用"专用控件字段不出现在目录中"的排除规则，SHALL 正常出现在目录中，可作为"表单字段定义"绑定来源与"应用同步字段映射配置"的源字段被选择。

#### Scenario: 迁移完成后可查询到四类业务对象的元数据字段
- **WHEN** 系统完成数据库迁移后，客户端分别查询 `bizType=ORG`/`USER`/`POSITION`/`APP` 的元数据字段列表
- **THEN** 每个 `bizType` 下都能查询到对应的原有可配置字段记录与 10 条 `ext1`~`ext10` 记录

#### Scenario: 专用控件字段不出现在元数据目录中
- **WHEN** 客户端查询 `bizType=ORG` 的元数据字段列表
- **THEN** 结果中不包含 `columnName` 为 `parent_id` 或 `status` 的记录

#### Scenario: 迁移完成后可查询到角色业务对象类型的元数据字段
- **WHEN** 系统完成数据库迁移后，客户端查询 `bizType=ROLE` 的元数据字段列表
- **THEN** 返回结果中存在四条记录，`columnName` 分别为 `name`/`code`/`show_order`/`remark`，不存在任何 `ext` 字段记录

#### Scenario: 组织元数据字段目录包含上级组织编码
- **WHEN** 系统完成数据库迁移后，客户端查询 `bizType=ORG` 的元数据字段列表
- **THEN** 返回结果中存在一条记录，`tableName=tab_org`、`columnName=parent_code`、`fieldCode=parentCode`、`fieldName="上级组织编码"`、状态为启用（`2000`）

#### Scenario: 上级组织编码可作为应用同步字段映射的源字段被选择
- **WHEN** 客户端在配置某个应用组织数据域的字段映射时，调用 `GET /api/metadata-fields/available?bizType=ORG` 查询可选源字段
- **THEN** 返回结果中包含"上级组织编码"这条元数据字段，可被选为一条字段映射记录的源字段
