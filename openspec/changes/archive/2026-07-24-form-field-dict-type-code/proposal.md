## Why

"表单字段定义"（`tab_form_field_definition`）里控件类型为"字典下拉"/"多选字典下拉"的
字段，通过 `dict_type_id` 关联 `tab_dict_type.id` 记录要用哪个字典类型驱动下拉选项。
用户指出这个关联方式不合适：字典类型的自增主键 `id` 在数据发生变化（比如某个环境的
字典类型被删除重建、或者跨环境导数据）时可能改变，而字典类型的业务编码 `code`
（如 `gender`、`position_type`）语义稳定、不会变化。用现在的 `id` 关联方式，一旦 `id`
在某次数据迁移/环境切换后对不上，所有依赖它的表单字段定义就会全部失效（下拉选项查不到、
渲染元数据里的字典选项变成空列表），且没有任何报错提示，只是静默失效。

## What Changes

- `tab_form_field_definition.dict_type_id`（`BIGINT`，关联 `tab_dict_type.id`）改为
  `dict_type_code`（`VARCHAR(64)`，关联 `tab_dict_type.code`），历史数据按现有关联
  关系原地转换（用 `dict_type_id` 反查对应字典类型的 `code` 写入新列）。
- 后端字段定义的新增/更新校验、动态渲染元数据接口（`render-schema`）解析下拉选项、
  操作日志字段快照里字典值转标签，均改为按字典类型编码而非主键 id 关联查询；其中按 id
  查询字典类型再取其 code 去查字典项这一步中间查询被省去（本来就要用 code 去查字典项，
  现在直接存 code，不用先查一次字典类型表）。
- 前端"表单管理 - 字段定义"页面的新增/编辑弹窗，字典类型选择器提交的值从字典类型 id
  改为字典类型编码。

## Capabilities

### Modified Capabilities
- `form-field-definition-management`：字段定义与字典类型的关联方式由主键 id 改为
  业务编码。

## Impact

- 后端：新增 Flyway 迁移（列改名/改类型 + 历史数据按现有关联关系转换）；
  `FormFieldDefinitionEntity`/`FormFieldDefinitionCreateRequest`/
  `FormFieldDefinitionUpdateRequest`/`FormFieldDefinitionVO` 的 `dictTypeId` 改为
  `dictTypeCode`；`FormFieldDefinitionServiceImpl`（校验、解析下拉选项、操作日志快照）、
  `FormFieldSnapshotSupport`（组织/人员/任职/应用四模块共用的扩展字段快照字典值解析）
  相应调整。
- 前端：`types/formField.ts`、`FormFieldDefinitionPanel.vue` 的字典类型选择器与提交
  payload 调整。
- 文档：`openspec/specs/form-field-definition-management/spec.md` 更新对应需求条目。
