## ADDED Requirements

### Requirement: 任职记录字段的动态列表与表单渲染
任职管理列表页与新增/编辑表单 SHALL 除 `orgId`（关联组织）、`userId`（关联用户）、`positionType`（认证类型，已绑定固定字典类型 `position_type`）、`status`（启停用，通过独立接口维护）外的全部字段——含原有表字段（`positionAddress`、`positionPhone`、`showOrder`、`remark`）与扩展字段（`ext1`~`ext10`）——统一按"表单字段定义"（`bizType=POSITION`）中启用状态的定义动态渲染：`showInList=true` 的定义渲染为列表列，`showInCreate=true`/`showInEdit=true` 的定义分别渲染为新增/编辑表单项；控件类型按定义的 `controlType` 渲染为下拉字典选择器、文本输入框或数字输入框；`editable=false` 的表单项渲染为只读展示；有 `placeholder` 的表单项展示对应输入提示文字。任职管理没有 `locked=true` 的字段定义。

#### Scenario: 列表页按字段定义渲染原有字段列
- **WHEN** `bizType=POSITION` 下 `positionAddress`、`positionPhone` 两条 `CORE` 字段定义均为 `showInList=true`
- **THEN** 任职管理列表页渲染出"任职地址"、"任职电话"两列

#### Scenario: 列表页追加动态扩展列
- **WHEN** `bizType=POSITION` 存在一条 `showInList=true` 的 `EXT` 字段定义
- **THEN** 任职管理列表页渲染出该定义对应的列，展示各行任职记录的对应 `extN` 值

#### Scenario: 新增表单按定义渲染扩展字段
- **WHEN** `bizType=POSITION` 存在一条 `showInCreate=true` 的 `EXT` 字段定义
- **THEN** 任职新增表单渲染出该定义对应的表单项

#### Scenario: 未配置定义的扩展列不展示
- **WHEN** 某个 `extN` 列在 `bizType=POSITION` 下没有任何启用状态的字段定义
- **THEN** 任职管理列表与新增/编辑表单均不展示该扩展列对应的字段

#### Scenario: 关联组织、关联用户、认证类型、状态字段保持硬编码渲染
- **WHEN** 用户打开任职新增或编辑表单
- **THEN** 关联组织选择器、关联用户选择器、认证类型下拉、启停用状态的渲染方式不受"表单字段定义"配置影响，不出现在动态渲染的字段列表中

### Requirement: 任职记录字段的必填、正则与唯一性校验
新增或更新任职记录时，系统 SHALL 按 `bizType=POSITION` 下适用于当前场景的字段定义，对提交的对应字段值执行必填、正则、唯一性（未被逻辑删除的任职记录范围内，更新时排除自身）校验；任一校验失败 SHALL 拒绝保存并返回业务错误。

#### Scenario: 必填扩展字段为空时拒绝创建
- **WHEN** 创建任职记录时，某条 `bizType=POSITION`、`showInCreate=true`、`isRequired=true` 的定义对应的值为空
- **THEN** 系统拒绝创建，返回业务错误

#### Scenario: 字段值不匹配正则时拒绝保存
- **WHEN** 创建或更新任职记录时，某条定义配置了 `validateRegex`，提交的对应值不匹配该正则
- **THEN** 系统拒绝保存，返回业务错误

#### Scenario: 唯一字段值与其他有效任职记录重复时拒绝保存
- **WHEN** 某条定义 `isUnique=true`，提交的对应值与另一个未被逻辑删除的任职记录的同一字段值相同
- **THEN** 系统拒绝保存，返回业务错误

#### Scenario: 唯一字段值与已删除任职记录重复时允许保存
- **WHEN** 某条定义 `isUnique=true`，提交的对应值仅与某个已被逻辑删除的任职记录的同一字段值相同
- **THEN** 系统允许保存成功
