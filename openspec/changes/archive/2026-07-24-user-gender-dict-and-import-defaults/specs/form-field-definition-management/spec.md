## MODIFIED Requirements

### Requirement: 默认初始化四类业务对象的表单字段定义
系统 SHALL 通过数据库迁移为组织、人员、任职、应用四类业务对象各自"可开放配置的原有表字段"预置启用状态的表单字段定义，绑定对应的元数据字段；`ext1`~`ext10` 对应的元数据字段默认不预置字段定义。人员（`bizType=USER`）的 `gender`（性别）SHALL 一并预置为默认启用的字段定义，控件类型为字典下拉，绑定字典类型 `gender`。

#### Scenario: 迁移完成后原有字段的默认渲染元数据即可查询到
- **WHEN** 系统完成数据库迁移后，客户端调用 `GET /api/form-fields/render-schema?bizType=ORG`
- **THEN** 返回结果中包含组织"组织名称""组织编码""显示序号""备注"对应的字段定义，无需管理员额外配置

#### Scenario: 扩展字段默认无字段定义
- **WHEN** 系统完成数据库迁移后，客户端调用 `GET /api/form-fields/render-schema?bizType=ORG`
- **THEN** 返回结果中不包含任何绑定 `ext1`~`ext10` 元数据字段的定义，直到管理员手动新增

#### Scenario: 迁移完成后人员性别字段的默认渲染元数据即可查询到
- **WHEN** 系统完成数据库迁移后，客户端调用 `GET /api/form-fields/render-schema?bizType=USER`
- **THEN** 返回结果中包含人员"性别"对应的字段定义，控件类型为字典下拉，可选项来自字典类型 `gender`，无需管理员额外配置

## ADDED Requirements

### Requirement: 人员性别字段可作为导入字段配置选择
系统 SHALL 允许管理员在为 `bizType=USER` 新增或编辑"导入字段配置"时，从当前启用状态的表单字段定义中选中"性别"字段，与选中手机号、身份证号等其他字段的操作方式一致。

#### Scenario: 导入字段配置的关联字段选择器包含性别
- **WHEN** 管理员为 `bizType=USER` 新增一条导入字段配置，打开"关联字段"选择器
- **THEN** 选择器列表中包含"性别"这一项，选中后可继续配置该列的 Excel 表头名称、是否主键、是否必填、显示序号
