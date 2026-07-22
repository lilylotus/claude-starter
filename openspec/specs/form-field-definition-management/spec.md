## Purpose

维护组织（`ORG`）、人员（`USER`）、任职（`POSITION`）、应用（`APP`）四类业务对象的"表单字段定义"（`tab_form_field_definition`）：每条定义绑定一个来自"元数据字段配置"（`metadata-field-management`）目录的元数据字段，并配置展示名称、字段标识、控件类型、必填/唯一/列表展示/新增展示/编辑展示/可编辑等开关、正则校验、输入提示文字、显示序号与状态。承重字段（组织/用户/应用各自的 `name`、`code`）对应的定义受到锁定保护，防止被误停用、删除或放宽必填/展示约束。该能力是组织、用户、任职、应用四个管理模块动态渲染列表/表单与执行数据驱动校验的元数据来源。

## Requirements

### Requirement: 表单字段定义数据模型
系统 SHALL 提供 `tab_form_field_definition` 表记录组织（`ORG`）、人员（`USER`）、任职（`POSITION`）、应用（`APP`）四类业务对象的字段定义，每条定义 SHALL 绑定一个元数据字段（`metadataFieldId`，关联 `tab_metadata_field.id`），并包含业务对象类型（`bizType`，创建时取自所绑定元数据字段）、展示名称（`fieldName`）、前端/DTO 字段标识（`fieldCode`）、控件类型（`controlType`）、是否唯一、是否必填、是否列表展示、是否新增表单展示、是否编辑表单展示、是否可编辑、正则校验规则（`validateRegex`）、输入提示文字（`placeholder`）、显示序号、状态。

#### Scenario: 字段定义绑定元数据字段并可独立设置展示名称
- **WHEN** 系统管理员为业务对象类型 `USER` 创建一条字段定义，绑定一个 `columnName=ext6` 的元数据字段，`fieldName` 设为"身份证号"、`fieldCode` 设为 `idCardNo`
- **THEN** 系统保存该定义，之后查询该 `bizType` 的字段定义列表时能看到"身份证号（`idCardNo`）绑定 `ext6`"这一条记录

### Requirement: 字段定义只能绑定元数据字段目录中的可用条目
系统 SHALL 保证创建表单字段定义时，`metadataFieldId` 必须指向一个当前状态为启用、且未被其他有效字段定义绑定的元数据字段；否则拒绝创建。

#### Scenario: 绑定不存在或已停用的元数据字段被拒绝
- **WHEN** 客户端调用 `POST /api/form-fields`，`metadataFieldId` 指向一个不存在或状态非启用的元数据字段
- **THEN** 系统拒绝创建，返回业务错误（非零 `code`）

#### Scenario: 绑定已被其他有效定义占用的元数据字段被拒绝
- **WHEN** 某元数据字段已被一条有效的表单字段定义绑定，客户端尝试创建另一条绑定同一元数据字段的定义
- **THEN** 系统拒绝创建，返回业务错误

#### Scenario: 已删除定义释放元数据字段供重新绑定
- **WHEN** 某条绑定元数据字段 A 的定义被逻辑删除（`status=-1000`）后，创建新的绑定元数据字段 A 的定义
- **THEN** 系统允许创建成功（互斥校验仅针对未删除定义）

### Requirement: 字段定义的绑定关系一经创建不可修改
系统 SHALL 保证表单字段定义创建之后，其 `bizType`、`metadataFieldId` 不可再被修改；更新接口对这两个字段的改动请求 SHALL 被忽略或拒绝。要绑定另一个元数据字段，只能删除当前定义（非承重字段）后重新创建。

#### Scenario: 更新请求尝试修改绑定的元数据字段被忽略
- **WHEN** 客户端调用 `PUT /api/form-fields/{id}` 且请求体中 `metadataFieldId` 与该定义当前值不同
- **THEN** 系统保存其余可配置属性的更新，但该定义绑定的元数据字段保持原值不变

### Requirement: fieldCode 在同一业务对象类型下唯一
系统 SHALL 保证 `fieldCode` 在同一 `bizType` 下唯一。

#### Scenario: fieldCode 重复时拒绝创建
- **WHEN** `bizType=USER` 下已存在 `fieldCode=idCardNo` 的有效定义，此时创建另一条 `bizType=USER`、`fieldCode=idCardNo` 的定义
- **THEN** 系统拒绝创建，返回业务错误

### Requirement: 承重字段的锁定保护
系统 SHALL 判定某条表单字段定义是否绑定到承重字段（组织/用户/应用各自的 `name`、`code` 对应的元数据字段）；对绑定承重字段的定义，更新接口 SHALL 拒绝将其状态改为非 `2000`（即不可停用），`DELETE` 接口 SHALL 拒绝删除，也 SHALL 拒绝将 `isRequired`、`showInCreate`、`showInEdit` 改为 `false`；该类定义的展示名称、显示序号、`placeholder`、`validateRegex`、`editable`、`isUnique`、`showInList` 仍可自由调整。

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

### Requirement: 非承重字段定义支持删除
系统 SHALL 支持对未绑定承重字段的表单字段定义执行逻辑删除，删除后其绑定的元数据字段被释放，可供其他定义重新绑定。

#### Scenario: 删除非承重字段定义
- **WHEN** 客户端对一条未绑定承重字段的定义调用 `DELETE /api/form-fields/{id}`
- **THEN** 系统将该定义 `status` 置为 `-1000`，其绑定的元数据字段此后出现在该 `bizType` 的"可用元数据字段"查询结果中

### Requirement: 字段定义的控件类型配置
系统 SHALL 支持将字段定义的 `controlType` 配置为下拉单选字典、普通文本输入框、数字输入框三种之一；配置为下拉单选字典时 SHALL 要求关联一个 `tab_dict_type` 字典类型（`dictTypeId`）。

#### Scenario: 配置为下拉单选字典但未关联字典类型时拒绝保存
- **WHEN** 创建或更新一条 `controlType` 为"下拉单选字典"的定义，但未提供 `dictTypeId`
- **THEN** 系统拒绝保存，返回业务错误

#### Scenario: 配置为文本框或数字框时无需关联字典类型
- **WHEN** 创建一条 `controlType` 为"文本输入框"或"数字输入框"的定义，且未提供 `dictTypeId`
- **THEN** 系统正常创建该定义

### Requirement: 字段定义的展示与校验配置
系统 SHALL 支持为每条字段定义独立配置：是否唯一、是否必填、是否列表展示、是否新增表单展示、是否编辑表单展示、是否可编辑、正则校验规则、输入提示文字；"是否新增表单展示"与"是否编辑表单展示"SHALL 可分别配置为不同的值；"是否可编辑"与"是否表单展示"是两个独立的开关，表单展示为真而可编辑为假时表示该字段在表单中只读展示。

#### Scenario: 字段仅在编辑表单展示而新增表单不展示
- **WHEN** 一条定义配置为 `showInCreate=false`、`showInEdit=true`
- **THEN** 该字段在新增表单的渲染元数据中不出现，在编辑表单的渲染元数据中出现

#### Scenario: 字段表单可见但不可编辑
- **WHEN** 一条定义配置为 `showInEdit=true`、`editable=false`
- **THEN** 编辑表单渲染元数据中包含该字段且标记为只读

### Requirement: 默认初始化四类业务对象的表单字段定义
系统 SHALL 通过数据库迁移为组织、人员、任职、应用四类业务对象各自"可开放配置的原有表字段"预置启用状态的表单字段定义，绑定对应的元数据字段；`ext1`~`ext10` 对应的元数据字段默认不预置字段定义。

#### Scenario: 迁移完成后原有字段的默认渲染元数据即可查询到
- **WHEN** 系统完成数据库迁移后，客户端调用 `GET /api/form-fields/render-schema?bizType=ORG`
- **THEN** 返回结果中包含组织"组织名称""组织编码""显示序号""备注"对应的字段定义，无需管理员额外配置

#### Scenario: 扩展字段默认无字段定义
- **WHEN** 系统完成数据库迁移后，客户端调用 `GET /api/form-fields/render-schema?bizType=ORG`
- **THEN** 返回结果中不包含任何绑定 `ext1`~`ext10` 元数据字段的定义，直到管理员手动新增

### Requirement: 表单字段定义管理接口
系统 SHALL 提供字段定义的分页查询（按 `bizType` 过滤）、详情查询、新增、更新、启用/停用、逻辑删除接口，行为与项目内其他主数据（如组织、字典）的对应接口保持一致的状态语义（`2000`=启用、`3000`=停用、`-1000`=已逻辑删除）。

#### Scenario: 按业务对象类型分页查询字段定义
- **WHEN** 客户端调用 `GET /api/form-fields?bizType=ORG&page=1&pageSize=10`
- **THEN** 系统返回 `bizType=ORG` 且未被逻辑删除的字段定义分页列表

### Requirement: 动态字段渲染元数据接口
系统 SHALL 提供渲染元数据查询接口，返回指定 `bizType` 下全部启用状态的字段定义，按显示序号降序排列；当某条定义 `controlType` 为"下拉单选字典"时，返回结果 SHALL 内嵌该字典类型下的可选项列表（标签、值）。

#### Scenario: 查询组织业务对象的渲染元数据
- **WHEN** 客户端调用 `GET /api/form-fields/render-schema?bizType=ORG`
- **THEN** 系统返回 `bizType=ORG` 下全部启用的字段定义，包含 `fieldCode`、`fieldName`、`controlType`、`isRequired`、`isUnique`、`showInList`、`showInCreate`、`showInEdit`、`editable`、`locked`（根据绑定的元数据字段是否为承重字段计算得出），按显示序号降序排列

#### Scenario: 字典下拉字段的渲染元数据内嵌字典选项
- **WHEN** 渲染元数据中某条定义 `controlType` 为"下拉单选字典"且 `dictTypeId` 指向一个存在的字典类型
- **THEN** 该条定义的返回结果中包含 `dictOptions` 数组，每项含 `label`、`value`，数据来源于该字典类型下的启用字典项

### Requirement: 表单管理前端界面
系统 SHALL 在"系统管理"菜单下提供"表单管理"页面（路径 `/system/form-fields`），支持按业务对象类型（组织/人员/任职/应用）切换查看对应的字段定义列表；新增字段定义时从"元数据配置"目录中选择当前业务对象类型下尚未绑定的可用元数据字段；支持编辑已有字段定义；控件类型选择"下拉单选字典"时 SHALL 展示字典类型选择器。

#### Scenario: 切换业务对象类型查看对应字段定义
- **WHEN** 用户在表单管理页面切换到"人员"分类
- **THEN** 页面展示 `bizType=USER` 的字段定义列表

#### Scenario: 新增字段定义时从元数据配置目录选择字段
- **WHEN** 用户在表单管理页面点击"新增"并打开元数据字段选择器
- **THEN** 选择器展示当前业务对象类型下状态为启用、且尚未被占用的元数据字段列表；用户选中一项后可继续配置展示名称、控件类型等属性并保存

#### Scenario: 已占用的元数据字段不可重复选择
- **WHEN** 用户新增字段定义时打开元数据字段选择器
- **THEN** 当前业务对象类型下已被有效定义占用的元数据字段不出现在可选项中（编辑场景下，正在编辑的这条定义自身绑定的元数据字段除外，仍需可选中）

#### Scenario: 承重字段定义不提供删除或停用入口
- **WHEN** 用户在表单管理页面查看一条绑定承重字段（`name`/`code`）的定义
- **THEN** 该行操作列不展示"删除"与"停用"按钮

#### Scenario: 承重字段定义的受限属性在编辑界面禁用
- **WHEN** 用户打开一条绑定承重字段的定义的编辑弹窗
- **THEN** "是否必填"/"是否新增表单展示"/"是否编辑表单展示"对应的开关渲染为禁用态并附带说明文字，其余属性正常可编辑
