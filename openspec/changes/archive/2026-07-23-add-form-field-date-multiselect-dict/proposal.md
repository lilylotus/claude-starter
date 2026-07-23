## Why

表单字段定义（`form-field-definition-management`）目前只支持三种控件类型：文本框、数字框、字典单选下拉。业务侧需要录入日期类字段（如入职日期、有效期）以及"可多选"的字典类字段（如多个标签、多个能力项），现有三种类型都无法表达，需要扩展控件类型枚举并打通字段定义、动态渲染、动态表单值默认展示三层。

## What Changes

- 新增控件类型「日期」（`FormFieldControlType.DATE`），字段定义无需关联字典，动态渲染时提供日期默认值/校验。
- 新增控件类型「多选字典下拉」（`FormFieldControlType.MULTI_DICT`），与现有单选字典下拉（`DICT`）并列，同样依赖 `dict_type_id` 关联字典类型，区别在于动态渲染/前端交互允许多选。
- 后端：`FormFieldControlType` 增加两个常量；`FormFieldDefinitionServiceImpl` 的 `validateDictType()`、`resolveDictOptions()`、`controlTypeLabel()` 及所有按 `DICT` 分支判断的位置同步支持 `MULTI_DICT`；新增 `DATE` 的对应分支（无需字典校验）。
- 数据库：新增 Flyway 迁移（`V25__...sql`）更新 `tab_form_field_definition.control_type` 列注释，说明新增的两个取值；不改变列类型（仍为 `INT`），`dict_type_id` 的"仅字典类控件类型必填"约束扩展为 `DICT` 与 `MULTI_DICT` 均适用。
- 前端：`types/formField.ts` 增加 `FORM_FIELD_CONTROL_TYPE_DATE`、`FORM_FIELD_CONTROL_TYPE_MULTI_DICT` 常量及对应 `FORM_FIELD_CONTROL_TYPE_OPTIONS` 选项；`FormFieldListView.vue` 的字典类型选择器 `v-if` 条件、`dictTypeId` 校验规则扩展为 `DICT`/`MULTI_DICT` 均触发；新增日期选择控件的表单项。
- 前端动态渲染：`useDynamicFormFields.ts` 的 `buildRules()`、`buildFormModel()`、`dictOptionLabel()` 扩展 `DATE`（默认值 `null`，日期格式校验）与 `MULTI_DICT`（默认值 `[]`，多值 label 拼接）分支。
- 权限资源：控件类型是字段值而非独立功能点，不新增 `权限资源.txt` 资源码。

## Capabilities

### New Capabilities
(无)

### Modified Capabilities
- `form-field-definition-management`: 「字段定义的控件类型配置」需求扩展为支持五种控件类型（新增 DATE、MULTI_DICT），字典关联校验规则从"仅 DICT 需要 dictTypeId"扩展为"DICT 与 MULTI_DICT 均需要 dictTypeId"；「动态字段渲染元数据接口」需求扩展 `dictOptions` 在 `MULTI_DICT` 下同样返回选项列表（供前端渲染多选控件），并说明 `DATE` 类型不返回 `dictOptions`。

## Impact

- 受影响代码：
  - `backend/src/main/java/cn/nihility/rbac/formfield/constant/FormFieldControlType.java`
  - `backend/src/main/java/cn/nihility/rbac/formfield/service/impl/FormFieldDefinitionServiceImpl.java`
  - `backend/src/main/resources/db/migration/V25__update_tab_form_field_definition_control_type_comment.sql`（新增）
  - `frontend/src/types/formField.ts`
  - `frontend/src/views/system/formfields/FormFieldListView.vue`
  - `frontend/src/composables/useDynamicFormFields.ts`
- 不涉及新的数据库表、不涉及权限资源变更、不涉及 API 路径变化（仍是现有的表单字段定义/动态渲染接口，仅取值范围扩展）。
- 不属于 BREAKING：新增枚举值向后兼容，已有 TEXT/NUMBER/DICT 字段定义行为不变。
