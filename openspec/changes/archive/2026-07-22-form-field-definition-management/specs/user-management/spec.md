## ADDED Requirements

### Requirement: 用户字段的动态列表与表单渲染
用户管理列表页与新增/编辑表单 SHALL 除 `status`（启停用，通过独立接口维护）外的全部字段——含原有表字段（`name`、`code`、`mobile`、`idCard`、`showOrder`、`remark`）与扩展字段（`ext1`~`ext10`）——统一按"表单字段定义"（`bizType=USER`）中启用状态的定义动态渲染：`showInList=true` 的定义渲染为列表列，`showInCreate=true`/`showInEdit=true` 的定义分别渲染为新增/编辑表单项；控件类型按定义的 `controlType` 渲染为下拉字典选择器、文本输入框或数字输入框；`editable=false` 的表单项渲染为只读展示；有 `placeholder` 的表单项展示对应输入提示文字。`gender`（性别）保持既有硬编码渲染，不纳入本次动态渲染范围。

#### Scenario: 列表页按字段定义渲染原有字段列
- **WHEN** `bizType=USER` 下 `mobile`、`idCard` 两条 `CORE` 字段定义均为 `showInList=true`
- **THEN** 用户管理列表页渲染出"手机号"、"身份证号"两列

#### Scenario: 列表页追加动态扩展列
- **WHEN** `bizType=USER` 存在一条 `showInList=true` 的 `EXT` 字段定义
- **THEN** 用户管理列表页渲染出该定义对应的列，展示各行用户的对应 `extN` 值

#### Scenario: 编辑表单按定义渲染扩展字段
- **WHEN** `bizType=USER` 存在一条 `showInEdit=true` 的 `EXT` 字段定义
- **THEN** 用户编辑表单渲染出该定义对应的表单项

#### Scenario: 未配置定义的扩展列不展示
- **WHEN** 某个 `extN` 列在 `bizType=USER` 下没有任何启用状态的字段定义
- **THEN** 用户管理列表与新增/编辑表单均不展示该扩展列对应的字段

#### Scenario: 性别与状态字段保持硬编码渲染
- **WHEN** 用户打开用户新增或编辑表单
- **THEN** 性别选择器与启停用状态的渲染方式不受"表单字段定义"配置影响，不出现在动态渲染的字段列表中

### Requirement: 用户非锁定字段的必填、正则与唯一性校验
新增或更新用户时，系统 SHALL 按 `bizType=USER` 下 `locked=false` 且适用于当前场景的字段定义，对提交的对应字段值执行必填、正则、唯一性（未被逻辑删除的用户范围内，更新时排除自身）校验；任一校验失败 SHALL 拒绝保存并返回业务错误。`locked=true` 的字段定义（`name`、`code`）不走这条校验管线，其必填与唯一性仍由既有硬编码逻辑保证。

#### Scenario: 必填的非锁定字段为空时拒绝创建
- **WHEN** 创建用户时，某条 `bizType=USER`、`locked=false`、`showInCreate=true`、`isRequired=true` 的定义（如 `idCard` 被配置为必填）对应的值为空
- **THEN** 系统拒绝创建，返回业务错误

#### Scenario: 非锁定字段值不匹配正则时拒绝保存
- **WHEN** 创建或更新用户时，某条 `locked=false` 的定义配置了 `validateRegex`，提交的对应值不匹配该正则
- **THEN** 系统拒绝保存，返回业务错误

#### Scenario: 唯一字段值（如身份证号）与其他有效用户重复时拒绝保存
- **WHEN** 某条 `locked=false` 的定义 `isUnique=true`（如 `idCard` 配置为唯一），提交的对应值与另一个未被逻辑删除的用户的同一字段值相同
- **THEN** 系统拒绝保存，返回业务错误

#### Scenario: 唯一字段值与已删除用户重复时允许保存
- **WHEN** 某条 `locked=false` 的定义 `isUnique=true`，提交的对应值仅与某个已被逻辑删除的用户的同一字段值相同
- **THEN** 系统允许保存成功

#### Scenario: 锁定字段不受数据驱动校验管线影响
- **WHEN** `name`、`code` 对应的字段定义（`locked=true`）配置了与既有校验不一致的选项
- **THEN** 用户新增/编辑仍按既有的硬编码校验执行，不因这些配置而放松或改变约束
