## ADDED Requirements

### Requirement: 字段定义的字段标识完全派生自绑定的元数据字段
表单字段定义的字段标识（`fieldCode`）SHALL NOT 由管理员独立填写或修改：创建时，系统 SHALL 将其设置为所绑定元数据字段当时的 `fieldCode`；非锁定定义改绑元数据字段时（见"非锁定字段定义支持编辑时改绑数据字段"），系统 SHALL 将其同步刷新为新绑定元数据字段当时的 `fieldCode`。查询（分页查询、详情查询、渲染元数据查询）返回的 `fieldCode` SHALL 始终反映其当前绑定的元数据字段的最新 `fieldCode`，即使该元数据字段的 `fieldCode` 是在本定义创建或最近一次改绑之后才被单独编辑的。新增/编辑表单字段定义的请求参数 SHALL NOT 包含可独立提交的 `fieldCode` 字段。

#### Scenario: 创建定义时字段标识取自所绑定的元数据字段
- **WHEN** 客户端调用 `POST /api/form-fields`，绑定一个 `fieldCode=idCard` 的元数据字段
- **THEN** 系统创建的定义 `fieldCode=idCard`，无需（也不支持）请求体携带独立的 `fieldCode`

#### Scenario: 改绑后字段标识同步为新绑定字段的标识
- **WHEN** 非锁定定义原绑定元数据字段 A（`fieldCode=extA`），改绑到元数据字段 B（`fieldCode=extB`）
- **THEN** 改绑成功后，该定义的 `fieldCode` 变为 `extB`

#### Scenario: 元数据字段标识后续被单独编辑时查询结果同步更新
- **WHEN** 某条定义绑定的元数据字段 A 的 `fieldCode` 在该定义创建之后被单独编辑为新值
- **THEN** 之后查询该定义详情、分页列表或渲染元数据时，返回的 `fieldCode` 均为元数据字段 A 编辑后的新值，而非该定义创建时保存的旧值

### Requirement: 非锁定字段定义支持编辑时改绑数据字段
编辑非锁定（`locked=false`）的表单字段定义时，系统 SHALL 支持将其重新绑定到同一 `bizType` 下另一个状态为启用、且未被其他有效定义绑定的元数据字段；改绑后该定义的 `bizType` 保持不变（改绑目标必须与当前 `bizType` 一致）。锁定（`locked=true`，即绑定承重字段 `name`/`code`）的定义 SHALL 继续禁止改绑，行为与既有的"绑定关系创建后不可改"约束一致。

#### Scenario: 非锁定定义改绑到另一个可用元数据字段
- **WHEN** 客户端调用 `PUT /api/form-fields/{id}`，该定义 `locked=false`，请求体 `metadataFieldId` 指向同一 `bizType` 下另一个状态启用且未被占用的元数据字段
- **THEN** 系统更新该定义的绑定关系为新的元数据字段，返回的详情中 `metadataFieldId`/`columnName`/`fieldCode` 为新值

#### Scenario: 改绑目标已被其他有效定义占用时拒绝
- **WHEN** 客户端调用 `PUT /api/form-fields/{id}` 尝试改绑到一个已被另一条有效定义绑定的元数据字段
- **THEN** 系统拒绝该次更新，返回业务错误，绑定关系保持不变

#### Scenario: 改绑目标不存在或未启用时拒绝
- **WHEN** 客户端调用 `PUT /api/form-fields/{id}` 尝试改绑到一个不存在或状态非启用的元数据字段
- **THEN** 系统拒绝该次更新，返回业务错误，绑定关系保持不变

#### Scenario: 改绑目标跨越 bizType 时拒绝
- **WHEN** 客户端调用 `PUT /api/form-fields/{id}` 尝试改绑到一个 `bizType` 与当前定义不同的元数据字段
- **THEN** 系统拒绝该次更新，返回业务错误，绑定关系保持不变

#### Scenario: 锁定定义尝试改绑被拒绝
- **WHEN** 客户端调用 `PUT /api/form-fields/{id}`，该定义 `locked=true`，请求体 `metadataFieldId` 与当前值不同
- **THEN** 系统拒绝该次更新，返回业务错误，绑定关系保持不变

#### Scenario: 请求体 metadataFieldId 与当前值相同视为不改绑
- **WHEN** 客户端调用 `PUT /api/form-fields/{id}`，请求体 `metadataFieldId` 与该定义当前绑定的值相同
- **THEN** 系统正常保存其余属性的更新，不触发改绑校验，绑定关系与 `fieldCode` 均不变

## MODIFIED Requirements

### Requirement: 表单字段定义数据模型
系统 SHALL 提供 `tab_form_field_definition` 表记录组织（`ORG`）、人员（`USER`）、任职（`POSITION`）、应用（`APP`）四类业务对象的字段定义，每条定义 SHALL 绑定一个元数据字段（`metadataFieldId`，关联 `tab_metadata_field.id`），并包含业务对象类型（`bizType`，创建时取自所绑定元数据字段）、展示名称（`fieldName`）、前端/DTO 字段标识（`fieldCode`，完全派生自所绑定的元数据字段，不可由管理员独立设置，见"字段定义的字段标识完全派生自绑定的元数据字段"）、控件类型（`controlType`）、是否唯一、是否必填、是否列表展示、是否新增表单展示、是否编辑表单展示、是否可编辑、正则校验规则（`validateRegex`）、输入提示文字（`placeholder`）、显示序号、状态。

#### Scenario: 字段定义绑定元数据字段并可独立设置展示名称
- **WHEN** 系统管理员为业务对象类型 `USER` 创建一条字段定义，绑定一个 `columnName=ext6`、`fieldCode=idCardNo` 的元数据字段，`fieldName` 设为"身份证号"
- **THEN** 系统保存该定义，之后查询该 `bizType` 的字段定义列表时能看到"身份证号（`idCardNo`）绑定 `ext6`"这一条记录

### Requirement: fieldCode 在同一业务对象类型下唯一
系统 SHALL 保证 `fieldCode` 在同一 `bizType` 下唯一；由于 `fieldCode` 完全派生自所绑定的元数据字段（见"字段定义的字段标识完全派生自绑定的元数据字段"），且元数据字段本身的 `fieldCode` 已在同 `bizType` 下唯一、每个元数据字段至多被一条有效表单字段定义绑定，这一唯一性由上述两个约束间接保证，系统 SHALL NOT 在表单字段定义创建/更新时对 `fieldCode` 做额外的独立唯一性校验。

#### Scenario: 不同定义绑定不同元数据字段时字段标识天然不重复
- **WHEN** `bizType=USER` 下已存在一条绑定元数据字段 A（`fieldCode=idCardNo`）的有效定义，此时创建另一条绑定元数据字段 B（`fieldCode` 不等于 `idCardNo`）的定义
- **THEN** 系统正常创建成功，两条定义的 `fieldCode` 不重复

### Requirement: 字段定义的绑定关系一经创建不可修改
系统 SHALL 保证锁定（`locked=true`，绑定承重字段 `name`/`code`）的表单字段定义，其 `bizType`、`metadataFieldId` 创建后不可再被修改，更新接口对这两个字段的改动请求 SHALL 被拒绝。非锁定（`locked=false`）的表单字段定义，其 `bizType` 创建后同样不可修改，但 `metadataFieldId` SHALL 允许在编辑时按"非锁定字段定义支持编辑时改绑数据字段"的规则重新绑定；不满足改绑条件时，只能删除当前定义后重新创建。

#### Scenario: 锁定定义的更新请求尝试修改绑定的元数据字段被拒绝
- **WHEN** 客户端调用 `PUT /api/form-fields/{id}`，该定义 `locked=true`，请求体中 `metadataFieldId` 与该定义当前值不同
- **THEN** 系统拒绝该次更新，返回业务错误，该定义绑定的元数据字段保持原值不变

#### Scenario: 任意定义的更新请求尝试修改 bizType 被忽略
- **WHEN** 客户端调用 `PUT /api/form-fields/{id}` 且请求体中包含与该定义当前 `bizType` 不同的取值
- **THEN** 系统忽略该字段的改动，该定义的 `bizType` 保持原值不变

### Requirement: 字段定义只能绑定元数据字段目录中的可用条目
系统 SHALL 保证创建表单字段定义时，`metadataFieldId` 必须指向一个当前状态为启用、且未被其他有效字段定义绑定的元数据字段；否则拒绝创建。非锁定定义在编辑改绑时，新的 `metadataFieldId` 同样必须满足这一条件（且需与当前定义的 `bizType` 一致），否则拒绝该次更新。

#### Scenario: 绑定不存在或已停用的元数据字段被拒绝
- **WHEN** 客户端调用 `POST /api/form-fields`，`metadataFieldId` 指向一个不存在或状态非启用的元数据字段
- **THEN** 系统拒绝创建，返回业务错误（非零 `code`）

#### Scenario: 绑定已被其他有效定义占用的元数据字段被拒绝
- **WHEN** 某元数据字段已被一条有效的表单字段定义绑定，客户端尝试创建另一条绑定同一元数据字段的定义
- **THEN** 系统拒绝创建，返回业务错误

#### Scenario: 已删除定义释放元数据字段供重新绑定
- **WHEN** 某条绑定元数据字段 A 的定义被逻辑删除（`status=-1000`）后，创建新的绑定元数据字段 A 的定义
- **THEN** 系统允许创建成功（互斥校验仅针对未删除定义）

### Requirement: 表单管理前端界面
系统 SHALL 在"系统管理"菜单下提供"表单管理"页面（路径 `/system/form-fields`），支持按业务对象类型（组织/人员/任职/应用）切换查看对应的字段定义列表；新增/编辑字段定义弹窗中均展示"数据字段"选择器：新增时从"元数据配置"目录中选择当前业务对象类型下尚未绑定的可用元数据字段；编辑非锁定定义时，选择器展示当前绑定的元数据字段与其余同 `bizType` 下未被占用的启用元数据字段，可重新选择改绑；编辑锁定定义时，选择器渲染为禁用态，仅展示当前绑定不可更改。弹窗中的"字段标识"展示项 SHALL 渲染为禁用态（不可编辑），其值始终跟随当前选中/绑定的"数据字段"的 `fieldCode`。控件类型选择"下拉单选字典"时 SHALL 展示字典类型选择器。新增/编辑弹窗 SHALL 整体展示在页面靠上位置，减少字段较多时的滚动操作。

#### Scenario: 切换业务对象类型查看对应字段定义
- **WHEN** 用户在表单管理页面切换到"人员"分类
- **THEN** 页面展示 `bizType=USER` 的字段定义列表

#### Scenario: 新增字段定义时从元数据配置目录选择字段
- **WHEN** 用户在表单管理页面点击"新增"并打开数据字段选择器
- **THEN** 选择器展示当前业务对象类型下状态为启用、且尚未被占用的元数据字段列表；用户选中一项后"字段标识"自动展示为该元数据字段的 `fieldCode`（禁用态），可继续配置展示名称、控件类型等属性并保存

#### Scenario: 已占用的元数据字段不可重复选择
- **WHEN** 用户新增字段定义时打开数据字段选择器
- **THEN** 当前业务对象类型下已被有效定义占用的元数据字段不出现在可选项中

#### Scenario: 编辑非锁定定义时可重新选择数据字段
- **WHEN** 用户打开一条非锁定字段定义的编辑弹窗，选择器展示当前绑定项与其余同业务对象类型下未被占用的启用元数据字段，选择其他项后保存
- **THEN** 该定义完成改绑，弹窗中的"字段标识"展示项同步变为新选中数据字段的 `fieldCode`

#### Scenario: 编辑锁定定义时数据字段选择器与字段标识均禁用
- **WHEN** 用户打开一条绑定承重字段（`name`/`code`）的定义的编辑弹窗
- **THEN** 数据字段选择器渲染为禁用态，仅展示当前绑定的元数据字段，不可更改；"字段标识"展示项同样为禁用态，展示当前值

#### Scenario: 承重字段定义不提供删除或停用入口
- **WHEN** 用户在表单管理页面查看一条绑定承重字段（`name`/`code`）的定义
- **THEN** 该行操作列不展示"删除"与"停用"按钮

#### Scenario: 承重字段定义的受限属性在编辑界面禁用
- **WHEN** 用户打开一条绑定承重字段的定义的编辑弹窗
- **THEN** "是否必填"/"是否新增表单展示"/"是否编辑表单展示"对应的开关渲染为禁用态并附带说明文字，其余属性正常可编辑

#### Scenario: 新增/编辑弹窗展示在页面靠上位置
- **WHEN** 用户打开新增或编辑字段定义弹窗
- **THEN** 弹窗顶部靠近视口上方展示，无需先向下滚动页面即可看到弹窗顶部的表单项
