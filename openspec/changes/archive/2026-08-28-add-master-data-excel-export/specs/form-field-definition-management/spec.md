## MODIFIED Requirements

### Requirement: 表单字段定义数据模型
系统 SHALL 提供 `tab_form_field_definition` 表记录组织（`ORG`）、人员（`USER`）、任职（`POSITION`）、应用（`APP`）四类业务对象的字段定义，每条定义 SHALL 绑定一个元数据字段（`metadataFieldId`，关联 `tab_metadata_field.id`），并包含业务对象类型（`bizType`，创建时取自所绑定元数据字段）、展示名称（`fieldName`）、前端/DTO 字段标识（`fieldCode`，完全派生自所绑定的元数据字段，不可由管理员独立设置，见"字段定义的字段标识完全派生自绑定的元数据字段"）、控件类型（`controlType`）、是否唯一、是否必填、是否列表展示、是否新增表单展示、是否编辑表单展示、是否可编辑、**是否导出（`showInExport`）**、正则校验规则（`validateRegex`）、输入提示文字（`placeholder`）、显示序号、状态。"是否导出"决定该字段是否出现在 `master-data-excel-export` 能力的导出 Excel 列中，与"是否列表展示"是两个独立的开关，互不影响。

#### Scenario: 字段定义绑定元数据字段并可独立设置展示名称
- **WHEN** 系统管理员为业务对象类型 `USER` 创建一条字段定义，绑定一个 `columnName=ext6`、`fieldCode=idCardNo` 的元数据字段，`fieldName` 设为"身份证号"
- **THEN** 系统保存该定义，之后查询该 `bizType` 的字段定义列表时能看到"身份证号（`idCardNo`）绑定 `ext6`"这一条记录

#### Scenario: 是否导出与是否列表展示可配置为不同的值
- **WHEN** 一条字段定义配置为 `showInList=true`、`showInExport=false`
- **THEN** 系统正常保存，该字段在页面列表中展示，但不出现在导出 Excel 的列中

### Requirement: 字段定义的展示与校验配置
系统 SHALL 支持为每条字段定义独立配置：是否唯一、是否必填、是否列表展示、是否新增表单展示、是否编辑表单展示、是否可编辑、**是否导出**、正则校验规则、输入提示文字；"是否新增表单展示"与"是否编辑表单展示"SHALL 可分别配置为不同的值；"是否可编辑"与"是否表单展示"是两个独立的开关，表单展示为真而可编辑为假时表示该字段在表单中只读展示。"是否导出"SHALL 可独立于"是否列表展示"配置为不同的值，且不受承重字段锁定保护规则约束（见"承重字段的锁定保护"），管理员可自由调整任意字段（含承重字段）的"是否导出"。

#### Scenario: 字段仅在编辑表单展示而新增表单不展示
- **WHEN** 一条定义配置为 `showInCreate=false`、`showInEdit=true`
- **THEN** 该字段在新增表单的渲染元数据中不出现，在编辑表单的渲染元数据中出现

#### Scenario: 字段表单可见但不可编辑
- **WHEN** 一条定义配置为 `showInEdit=true`、`editable=false`
- **THEN** 编辑表单渲染元数据中包含该字段且标记为只读

#### Scenario: 承重字段的是否导出配置不受锁定保护
- **WHEN** 客户端更新一条绑定承重字段（`name`/`code`/`position_type`）的定义，请求体将 `showInExport` 由 `true` 改为 `false`
- **THEN** 系统正常保存该次更新，不因该定义被锁定而拒绝

### Requirement: 动态字段渲染元数据接口
系统 SHALL 提供渲染元数据查询接口，返回指定 `bizType` 下全部启用状态的字段定义，按显示序号升序排列（数值越小越靠前）；当某条定义 `controlType` 为"下拉单选字典"或"多选字典下拉"时，返回结果 SHALL 内嵌该字典类型下的可选项列表（标签、值）；`controlType` 为"日期"时不内嵌 `dictOptions`。

#### Scenario: 查询组织业务对象的渲染元数据
- **WHEN** 客户端调用 `GET /api/form-fields/render-schema?bizType=ORG`
- **THEN** 系统返回 `bizType=ORG` 下全部启用的字段定义，包含 `fieldCode`、`fieldName`、`controlType`、`isRequired`、`isUnique`、`showInList`、`showInCreate`、`showInEdit`、`showInExport`、`editable`、`locked`（根据绑定的元数据字段是否为承重字段计算得出），按显示序号升序排列

#### Scenario: 字典下拉字段的渲染元数据内嵌字典选项
- **WHEN** 渲染元数据中某条定义 `controlType` 为"下拉单选字典"且 `dictTypeCode` 指向一个存在的字典类型
- **THEN** 该条定义的返回结果中包含 `dictOptions` 数组，每项含 `label`、`value`，数据来源于该字典类型下的启用字典项

#### Scenario: 多选字典下拉字段的渲染元数据内嵌字典选项
- **WHEN** 渲染元数据中某条定义 `controlType` 为"多选字典下拉"且 `dictTypeCode` 指向一个存在的字典类型
- **THEN** 该条定义的返回结果中同样包含 `dictOptions` 数组，每项含 `label`、`value`，数据来源于该字典类型下的启用字典项，供前端渲染为多选控件

#### Scenario: 日期字段的渲染元数据不内嵌字典选项
- **WHEN** 渲染元数据中某条定义 `controlType` 为"日期"
- **THEN** 该条定义的返回结果中不包含 `dictOptions` 字段

## ADDED Requirements

### Requirement: 存量字段定义的导出可见性迁移初始化
系统 SHALL 通过数据库迁移为 `tab_form_field_definition` 新增 `show_in_export` 列，并对迁移执行时已存在的全部记录（不区分 `bizType`、不区分是否为承重字段），将 `show_in_export` 初始值设置为与该记录当前 `show_in_list` 相同的值；迁移完成后，两个开关此后各自独立，互不联动。

#### Scenario: 迁移后存量字段定义的导出可见性与列表展示一致
- **WHEN** 系统完成新增 `show_in_export` 列的数据库迁移
- **THEN** 迁移执行时已存在的每一条字段定义，其 `showInExport` 值都等于该记录迁移前的 `showInList` 值

#### Scenario: 迁移后管理员可独立调整导出可见性而不影响列表展示
- **WHEN** 管理员在迁移完成后，通过表单管理页面把某条字段定义的"是否导出"改为与"是否列表展示"不同的值
- **THEN** 系统正常保存该次调整，该字段的列表展示行为不受影响

### Requirement: 表单管理页面新增/编辑弹窗支持配置是否导出
表单管理页面（`/system/form-fields`）的字段定义新增/编辑弹窗 SHALL 提供"是否导出"勾选项，与既有的"是否列表展示"/"是否新增表单展示"/"是否编辑表单展示"勾选项并列展示，默认值取自该字段定义当前的 `showInExport`（新增时的默认值遵循后端创建接口的默认值）；勾选状态的调整 SHALL NOT 受承重字段锁定保护规则限制（承重字段的"是否导出"勾选项与其余非受保护属性一样正常可编辑，不禁用）。

#### Scenario: 新增字段定义时可勾选是否导出
- **WHEN** 用户在表单管理页面新增一条字段定义，勾选"是否导出"
- **THEN** 保存后该字段定义的 `showInExport` 为 `true`

#### Scenario: 编辑承重字段定义时是否导出勾选项可正常调整
- **WHEN** 用户打开一条绑定承重字段（`name`/`code`/`position_type`）的定义的编辑弹窗
- **THEN** "是否导出"勾选项渲染为可编辑态（不禁用），与"是否必填"/"是否新增表单展示"/"是否编辑表单展示"这几项被禁用的情况不同

#### Scenario: 字段定义列表的属性列展示是否导出标签
- **WHEN** 某条字段定义 `showInExport=true`，用户在表单管理页面查看字段定义列表
- **THEN** 该条记录所在行的"属性"列展示"导出"标签，与"列表"/"新增"/"编辑"等既有属性标签风格一致；`showInExport=false` 的定义所在行不展示该标签
