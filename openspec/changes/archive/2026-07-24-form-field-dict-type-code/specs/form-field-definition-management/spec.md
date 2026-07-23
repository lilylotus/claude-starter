## MODIFIED Requirements

### Requirement: 字段定义的控件类型配置
系统 SHALL 支持将字段定义的 `controlType` 配置为下拉单选字典、普通文本输入框、数字输入框、日期、多选字典下拉五种之一；配置为下拉单选字典或多选字典下拉时 SHALL 要求关联一个 `tab_dict_type` 字典类型，关联方式为字典类型编码（`dictTypeCode`，对应 `tab_dict_type.code`）而非主键 id——编码是稳定的业务标识，不会因数据迁移或环境切换导致关联失配；配置为日期时 SHALL NOT 要求关联字典类型。

#### Scenario: 配置为下拉单选字典但未关联字典类型时拒绝保存
- **WHEN** 创建或更新一条 `controlType` 为"下拉单选字典"的定义，但未提供 `dictTypeCode`
- **THEN** 系统拒绝保存，返回业务错误

#### Scenario: 配置为多选字典下拉但未关联字典类型时拒绝保存
- **WHEN** 创建或更新一条 `controlType` 为"多选字典下拉"的定义，但未提供 `dictTypeCode`
- **THEN** 系统拒绝保存，返回业务错误

#### Scenario: 配置为文本框、数字框或日期时无需关联字典类型
- **WHEN** 创建一条 `controlType` 为"文本输入框"、"数字输入框"或"日期"的定义，且未提供 `dictTypeCode`
- **THEN** 系统正常创建该定义

#### Scenario: 关联的字典类型编码不存在时拒绝保存
- **WHEN** 创建或更新一条 `controlType` 为"下拉单选字典"的定义，提供的 `dictTypeCode` 在当前未被逻辑删除的字典类型中不存在
- **THEN** 系统拒绝保存，返回业务错误
