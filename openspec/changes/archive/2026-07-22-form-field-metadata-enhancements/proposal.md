## Why

`metadata-field-management` 和 `form-field-definition-management` 上线后，实际使用中暴露出四个问题：
（1）元数据字段目录里没有独立的"字段标识"，表单字段定义每次绑定新元数据字段时都要管理员手敲一遍字段标识，容易和已有定义冲突或拼错；
（2）"绑定元数据字段"这个表单项文案偏长；
（3）新增/编辑弹窗默认居中，字段较多时表单底部（含保存按钮）需要向下滚动才能看到，操作体验差；
（4）编辑字段定义时无法调整已绑定的元数据字段，一旦绑错或者业务需要更换绑定列，只能删除重建（承重字段甚至无法删除），缺少纠错手段。

## What Changes

- `tab_metadata_field` 新增 `field_code`（字段标识）列，已有数据通过迁移脚本按 `columnName` 驼峰化自动回填；**调整**：字段标识后续通过元数据字段的编辑接口/编辑弹窗可编辑（与 `fieldName` 同等对待），不再和 `tableName`/`columnName`/`columnType` 一样"迁移写入后不可改"，但仍要求同一 `bizType` 下唯一。
- 元数据字段详情/列表/编辑接口暴露 `fieldCode`；元数据配置编辑弹窗新增"字段标识"输入框（必填，同 `bizType` 下唯一），详情弹窗同步展示。
- **调整**：表单字段定义的 `fieldCode` 改为完全派生、不可独立编辑——新增/编辑弹窗中该输入框始终禁用，展示的值自动且仅跟随当前选中/绑定的"数据字段"的 `fieldCode`；创建、改绑时把该值写入定义本身，读取（列表/详情/渲染元数据）时进一步以绑定的元数据字段当前的 `fieldCode` 为准（即使元数据字段的字段标识后来被单独编辑过，已绑定的定义读取到的也是最新值），不再支持管理员为表单字段定义手动指定/修改独立的字段标识。
- 表单字段定义弹窗中"绑定元数据字段"文案简写为"数据字段"。
- 表单字段定义新增/编辑弹窗改为靠页面上方展示（而非默认垂直居中），减少字段较多时的滚动操作。
- **BREAKING**：表单字段定义编辑弹窗新增"数据字段"下拉框（此前仅新增弹窗才有），允许在编辑时重新绑定元数据字段；非锁定（`locked=false`）的定义允许改绑，改绑目标限定为同一 `bizType` 下状态为启用且未被其他有效定义绑定的元数据字段（当前绑定的字段本身除外）；锁定（`locked=true`，即承重字段 name/code）的定义维持"绑定关系创建后不可改"的既有约束，编辑弹窗中该下拉框禁用。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `metadata-field-management`：元数据字段目录新增 `fieldCode`（字段标识）属性；迁移预置初始值，此后**可通过更新接口编辑**（与 `fieldName` 同等对待），同一 `bizType` 下唯一。
- `form-field-definition-management`：
  - 修改"表单字段定义数据模型"——`fieldCode` 改为完全派生自所绑定的元数据字段，不再是管理员可独立填写/修改的属性；
  - 移除"fieldCode 在同一业务对象类型下唯一"的独立校验逻辑（唯一性由所绑定元数据字段自身的唯一性约束 + "一个元数据字段至多被一条有效定义绑定"的既有约束间接保证）；
  - 修改"绑定关系创建后不可改"的既有约束——非锁定定义在编辑时允许重新绑定元数据字段，锁定定义仍不可改绑；改绑时 `fieldCode` 随之同步为新绑定元数据字段的当前 `fieldCode`；
  - 新增/编辑弹窗的展示位置调整为靠上（UI 细节，不改变字段/接口契约，视情况决定是否需要落地为可验证的 Requirement）。

## Impact

- 后端：`tab_metadata_field` 新增列的 Flyway 迁移脚本；`MetadataFieldEntity`/`MetadataFieldVO`/`MetadataFieldConvert` 补充 `fieldCode`（可编辑）；`MetadataFieldUpdateRequest` 新增 `fieldCode`，`MetadataFieldServiceImpl.update()` 新增同 `bizType` 唯一性校验；`FormFieldDefinitionCreateRequest`/`FormFieldDefinitionUpdateRequest` 移除 `fieldCode` 字段（不再由客户端提交），改为服务层从绑定的元数据字段派生并写入；`FormFieldDefinitionServiceImpl` 移除独立的 `fieldCode` 唯一性校验，`enrich()` 读取时以绑定元数据字段的当前 `fieldCode` 为准；`FormFieldDefinitionUpdateRequest` 新增可选的 `metadataFieldId`；`FormFieldDefinitionServiceImpl.update()` 新增改绑校验逻辑（锁定拦截、启用状态校验、跨定义占用校验、同 `bizType` 校验，改绑成功时同步刷新 `fieldCode`）；`MetadataFieldController` 的"查询可用元数据字段"接口支持"编辑场景下把当前已绑定字段本身也算作可选项"。
- 前端：`types/metadataField.ts`、`types/formField.ts` 补充 `fieldCode` 相关类型；`MetadataFieldListView.vue` 编辑弹窗新增"字段标识"输入框（必填），详情弹窗展示；`FormFieldListView.vue` 编辑弹窗增加数据字段下拉框（锁定态禁用）、"字段标识"输入框改为禁用态且始终跟随所选数据字段、文案简写、弹窗定位上移，提交时不再携带 `fieldCode`。
- 数据库：新增一条 Flyway 迁移（`V24__...`），对已有 `tab_metadata_field` 数据做 `field_code` 回填；`tab_form_field_definition.field_code` 列结构不变，仅接口/服务层语义调整为"派生写入"。
- 无破坏性数据丢失风险，但编辑弹窗行为变化（新增可改绑字段的下拉框、字段标识改为只读派生）需要在 `openspec/specs/form-field-definition-management/spec.md` 中修改既有 Requirement 而非仅新增。
