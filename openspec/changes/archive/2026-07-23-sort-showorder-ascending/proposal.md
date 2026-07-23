## Why

"表单字段定义"（`/system/form-fields` 字段定义 tab）与"导入模板配置"（`/system/form-fields`
导入模板配置 tab）当前都按显示序号（`showOrder`）**降序**排列（数值越大排越前），这个方向
不符合大多数管理员对"显示序号"的直觉——习惯上序号越小越靠前（类似 Excel 行号、列表
优先级）。且这两处的降序目前和 Excel 导入模板生成的表头顺序、org/user/position/app
四个对象动态表单的字段渲染顺序直接挂钩，排序方向的直觉错位会导致管理员配置出和预期相反的
表头/字段顺序。用户要求把这两处的排序方向改成升序（值小的排在前）。

## What Changes

- 表单字段定义列表（管理页面分页查询、动态渲染元数据接口 `GET /api/form-fields/render-schema`）
  按 `showOrder` **升序**排列，取代现有的降序。
- 依赖渲染元数据接口的四个动态表单（org/user/position/app 的列表列顺序、新增/编辑表单
  字段顺序，`useDynamicFormFields.ts`）随之改为升序展示，前端不再对已经降序排好的数据
  二次倒序展示，而是直接按后端返回的升序结果渲染。
- 导入字段配置列表（管理页面查询、驱动 Excel 模板表头顺序与批量导入必填校验遍历顺序的
  `listActiveByBizType`）按 `showOrder` **升序**排列，取代现有的降序；Excel 导入模板的
  表头列顺序相应变为"显示序号越小的列越靠前"。
- 两处前端"显示序号"输入框下方的提示文案从"数值越大，排序越靠前"改为"数值越小，排序越
  靠前"。
- **BREAKING**：对已经按旧的降序规则配置过 `showOrder` 数值的管理员而言，排序方向反转后
  实际展示顺序会变化（不改变任何已存的 `showOrder` 数值本身，只改变读取时的排序方向），
  需要管理员按新语义重新调整数值以获得期望的顺序。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `form-field-definition-management`：动态字段渲染元数据接口的排序方向从降序改为升序。

## Impact

- 后端：`cn.nihility.rbac.formfield.service.impl.FormFieldDefinitionServiceImpl`
  （`getPage`、`listActiveByBizType`、`buildRenderSchema` 三处 `orderByDesc` 改
  `orderByAsc`）、`cn.nihility.rbac.excelimport.service.impl.ImportFieldConfigServiceImpl`
  （`getPage`、`listActiveByBizType` 两处 `orderByDesc` 改 `orderByAsc`）。
- 前端：`frontend/src/composables/useDynamicFormFields.ts`（`sortBySchemaOrder` 比较器
  反向）、`frontend/src/views/system/formfields/FormFieldDefinitionPanel.vue`、
  `frontend/src/views/system/formfields/ImportFieldConfigPanel.vue`（提示文案）。
- 文档：`openspec/specs/form-field-definition-management/spec.md` 需要一条 MODIFIED
  delta（"动态字段渲染元数据接口"的排序方向表述）；`openspec/changes/add-excel-import-export/`
  这个 change 尚未归档，其内部的 `design.md`/`tasks.md`/`specs/excel-import-export/spec.md`
  里"按 showOrder 降序"相关表述直接原地改为"升序"，不走 delta 机制（该 change 还没有把
  `excel-import-export` 这个新能力同步进 `openspec/specs/`）。
