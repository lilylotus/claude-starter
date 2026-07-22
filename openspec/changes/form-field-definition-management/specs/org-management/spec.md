## ADDED Requirements

### Requirement: 组织字段的动态列表与表单渲染
组织管理列表页与新增/编辑表单 SHALL 除 `parentId`（上级组织树选择器）与 `status`（启停用，通过独立接口维护）外的全部字段——含原有表字段（`name`、`code`、`showOrder`、`remark`）与扩展字段（`ext1`~`ext10`）——统一按"表单字段定义"（`bizType=ORG`）中启用状态的定义动态渲染：`showInList=true` 的定义渲染为列表列，`showInCreate=true`/`showInEdit=true` 的定义分别渲染为新增/编辑表单项；控件类型按定义的 `controlType` 渲染为下拉字典选择器、文本输入框或数字输入框；`editable=false` 的表单项渲染为只读展示；有 `placeholder` 的表单项展示对应输入提示文字。

#### Scenario: 列表页按字段定义渲染原有字段列
- **WHEN** `bizType=ORG` 下 `name`、`code` 两条 `CORE` 字段定义均为 `showInList=true`
- **THEN** 组织管理列表页渲染出"组织名称"、"编码"两列，列的展示顺序按各自的显示序号排列

#### Scenario: 列表页追加动态扩展列
- **WHEN** `bizType=ORG` 存在一条 `showInList=true` 的 `EXT` 字段定义"身份证号（`idCardNo`）绑定 `ext6`"
- **THEN** 组织管理列表页渲染出"身份证号"列，展示各行组织的 `ext6` 值

#### Scenario: 新增表单按定义渲染扩展字段
- **WHEN** `bizType=ORG` 存在一条 `showInCreate=true` 的 `EXT` 字段定义
- **THEN** 组织新增表单渲染出该定义对应的表单项，控件类型与 `placeholder` 均按定义渲染

#### Scenario: 未配置定义的扩展列不展示
- **WHEN** 某个 `extN` 列在 `bizType=ORG` 下没有任何启用状态的字段定义
- **THEN** 组织管理列表与新增/编辑表单均不展示该扩展列对应的字段

#### Scenario: 上级组织与状态字段保持硬编码渲染
- **WHEN** 用户打开组织新增或编辑表单
- **THEN** 上级组织选择器与启停用状态的渲染方式不受"表单字段定义"配置影响，不出现在动态渲染的字段列表中

### Requirement: 组织非锁定字段的必填、正则与唯一性校验
新增或更新组织时，系统 SHALL 按 `bizType=ORG` 下 `locked=false` 且适用于当前场景（新增看 `showInCreate`，编辑看 `showInEdit`）的字段定义，对提交的对应字段值执行：`isRequired=true` 时非空校验、`validateRegex` 非空时的正则格式校验、`isUnique=true` 时在未被逻辑删除（`status != -1000`）的组织范围内的唯一性校验（更新时排除组织自身）；任一校验失败 SHALL 拒绝保存并返回业务错误。`locked=true` 的字段定义（`name`、`code`）不走这条校验管线，其必填与唯一性仍由既有的 Bean Validation 与 service 层硬编码逻辑保证。

#### Scenario: 必填的非锁定字段为空时拒绝创建
- **WHEN** 创建组织时，某条 `bizType=ORG`、`locked=false`、`showInCreate=true`、`isRequired=true` 的定义（如 `remark` 被配置为必填）对应的值为空
- **THEN** 系统拒绝创建，返回业务错误

#### Scenario: 非锁定字段值不匹配正则时拒绝保存
- **WHEN** 创建或更新组织时，某条 `locked=false` 的定义配置了 `validateRegex`，提交的对应值不匹配该正则
- **THEN** 系统拒绝保存，返回业务错误

#### Scenario: 唯一的非锁定字段值与其他有效组织重复时拒绝保存
- **WHEN** 某条 `locked=false` 的定义 `isUnique=true`，提交的对应值与另一个未被逻辑删除的组织的同一字段值相同
- **THEN** 系统拒绝保存，返回业务错误

#### Scenario: 唯一字段值与已删除组织重复时允许保存
- **WHEN** 某条 `locked=false` 的定义 `isUnique=true`，提交的对应值仅与某个已被逻辑删除（`status=-1000`）的组织的同一字段值相同
- **THEN** 系统允许保存成功

#### Scenario: 锁定字段不受数据驱动校验管线影响
- **WHEN** `name`、`code` 对应的字段定义（`locked=true`）配置了 `isRequired=false` 或某个不匹配当前值的 `validateRegex`
- **THEN** 组织新增/编辑仍按既有的 `@NotBlank` 与编码唯一性硬编码校验执行，不因这些配置而放松或改变约束
