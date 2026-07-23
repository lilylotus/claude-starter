## MODIFIED Requirements

### Requirement: 用户字段的动态列表与表单渲染
用户管理列表页与新增/编辑表单 SHALL 除 `status`（启停用，通过独立接口维护）外的全部字段——含原有表字段（`name`、`code`、`gender`、`mobile`、`idCard`、`showOrder`、`remark`）与扩展字段（`ext1`~`ext10`）——统一按"表单字段定义"（`bizType=USER`）中启用状态的定义动态渲染：`showInList=true` 的定义渲染为列表列，`showInCreate=true`/`showInEdit=true` 的定义分别渲染为新增/编辑表单项；控件类型按定义的 `controlType` 渲染为下拉字典选择器、文本输入框或数字输入框；`editable=false` 的表单项渲染为只读展示；有 `placeholder` 的表单项展示对应输入提示文字。`gender`（性别）不再保持硬编码渲染，纳入本次动态渲染范围，取值来自字典类型 `gender` 下的字典项编码；仅 `status` 继续保持硬编码渲染。

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

#### Scenario: 性别字段按字段定义动态渲染
- **WHEN** 用户打开用户新增或编辑表单，且 `bizType=USER` 下 `gender` 对应的字段定义为启用状态
- **THEN** 性别渲染为字典下拉选择器，可选项来自字典类型 `gender` 下当前启用的字典项，是否必填、是否可编辑均由该字段定义的配置决定，与手机号、身份证号等字段的渲染方式一致

#### Scenario: 状态字段保持硬编码渲染
- **WHEN** 用户打开用户新增或编辑表单
- **THEN** 启停用状态的渲染方式不受"表单字段定义"配置影响，不出现在动态渲染的字段列表中
