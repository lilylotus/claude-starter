## Why

批量导入功能目前有三处影响可用性的问题：导入弹窗标题不区分业务对象类型，用户容易点错模块；
导入模板里手机号、身份证号等字符串列没有设置 Excel 文本格式，Excel 会按数值处理导致精度丢失
（如身份证号被截断成科学计数法）；导入出错时的失败明细只在弹窗内一个小表格里展示，数据量大、
失败行多时无法有效查看和处理。三处一起修复。

## What Changes

- 批量导入弹窗（`BatchImportDialog.vue`）标题从写死的"批量导入"改为按 `bizType` 显示具体
  业务对象类型（如"组织批量导入"/"人员批量导入"/"任职批量导入"/"应用批量导入"），复用项目里
  已有的业务对象类型显示名称字典（`FORM_FIELD_BIZ_TYPE_OPTIONS`），不新增字典、不改后端。
- Excel 导入模板生成时，非数字类型的列（文本框、字典下拉/多选、日期，以及 `__userCode` 等
  没有关联表单字段定义的固定标识列）的数据单元格区域 SHALL 设置为 Excel 文本格式（`@`），
  防止用户填写手机号、身份证号等纯数字外观的字符串时被 Excel 自动转换为数值导致精度丢失/
  科学计数法/丢失前导 0；仅数字框（`NUMBER`）类型的列保留默认数字格式。
- 批量导入出现失败行时，**BREAKING**：不再在弹窗内以表格逐行展示失败明细，改为后端生成一份
  在原始上传文件基础上追加"错误原因"列的标注版 `.xlsx`（失败行对应单元格红色字体写明该行
  失败原因），随导入结果一并返回给前端；前端展示"成功 X 条、失败 Y 条"的汇总文案，并提供
  "下载失败明细"按钮触发该标注文件下载，用户可以在 Excel 里直接核对、修正后重新上传。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `excel-import-export`：
  - "管理页面的导入模板下载与批量导入入口"——弹窗标题按业务对象类型区分，失败明细展示方式从
    页面内表格改为可下载的标注版 Excel 文件。
  - "按业务对象类型下载 Excel 导入模板"——新增数据单元格文本格式规则。
  - "按业务对象类型批量导入"——批量导入接口在存在失败行时返回一份标注版 Excel 文件（Base64
    编码），供前端下载。

## Impact

- 前端：`frontend/src/components/BatchImportDialog.vue`（标题文案、失败明细展示与下载交互）。
- 后端：
  - `backend/src/main/java/cn/nihility/rbac/excelimport/service/impl/ImportTemplateServiceImpl.java`
    （模板生成时按列的 `controlType`/是否有关联表单字段定义决定是否设置文本格式）。
  - `backend/src/main/java/cn/nihility/rbac/excelimport/service/support/DictImportColumnSupport.java`
    （新增一个类似 `resolveDictColumns` 的方法，识别哪些列应设为文本格式，供模板生成复用）。
  - `backend/src/main/java/cn/nihility/rbac/excelimport/service/impl/BatchImportServiceImpl.java`
    （存在失败行时，在已解析的 `Workbook` 上追加"错误原因"列生成标注版文件）。
  - `backend/src/main/java/cn/nihility/rbac/excelimport/dto/ImportResultVO.java`（新增
    `errorFileBase64`/`errorFileName` 两个可空字段，仅失败行非空时填充）。
- 不涉及数据库结构改动，不涉及既有导入字段配置数据。
