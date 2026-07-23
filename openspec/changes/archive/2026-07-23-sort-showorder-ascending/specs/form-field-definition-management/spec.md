## MODIFIED Requirements

### Requirement: 动态字段渲染元数据接口
系统 SHALL 提供渲染元数据查询接口，返回指定 `bizType` 下全部启用状态的字段定义，按显示序号升序排列（数值越小越靠前）；当某条定义 `controlType` 为"下拉单选字典"或"多选字典下拉"时，返回结果 SHALL 内嵌该字典类型下的可选项列表（标签、值）；`controlType` 为"日期"时不内嵌 `dictOptions`。

#### Scenario: 查询组织业务对象的渲染元数据
- **WHEN** 客户端调用 `GET /api/form-fields/render-schema?bizType=ORG`
- **THEN** 系统返回 `bizType=ORG` 下全部启用的字段定义，包含 `fieldCode`、`fieldName`、`controlType`、`isRequired`、`isUnique`、`showInList`、`showInCreate`、`showInEdit`、`editable`、`locked`（根据绑定的元数据字段是否为承重字段计算得出），按显示序号升序排列

#### Scenario: 字典下拉字段的渲染元数据内嵌字典选项
- **WHEN** 渲染元数据中某条定义 `controlType` 为"下拉单选字典"且 `dictTypeId` 指向一个存在的字典类型
- **THEN** 该条定义的返回结果中包含 `dictOptions` 数组，每项含 `label`、`value`，数据来源于该字典类型下的启用字典项

#### Scenario: 多选字典下拉字段的渲染元数据内嵌字典选项
- **WHEN** 渲染元数据中某条定义 `controlType` 为"多选字典下拉"且 `dictTypeId` 指向一个存在的字典类型
- **THEN** 该条定义的返回结果中同样包含 `dictOptions` 数组，每项含 `label`、`value`，数据来源于该字典类型下的启用字典项，供前端渲染为多选控件

#### Scenario: 日期字段的渲染元数据不内嵌字典选项
- **WHEN** 渲染元数据中某条定义 `controlType` 为"日期"
- **THEN** 该条定义的返回结果中不包含 `dictOptions` 字段
