## Context

三处修复都落在 `excel-import-export` 能力内，彼此独立、互不依赖，可分别实现和验证。关键既有
事实（调研阶段已确认）：

- 前端 `frontend/src/components/BatchImportDialog.vue` 弹窗标题写死为 `"批量导入"`，组件已经
  接收 `bizType: FormFieldBizType` prop，项目里已有现成的显示名称字典
  `FORM_FIELD_BIZ_TYPE_OPTIONS`（`frontend/src/types/metadataField.ts`：
  `ORG→组织、USER→人员、POSITION→任职、APP→应用`），组件内直接复用即可，四个调用方
  （组织/用户/任职/应用管理页面）都不需要改动。
- 后端 `ImportTemplateServiceImpl.generateTemplate()`（`backend/src/main/java/cn/nihility/rbac/
  excelimport/service/impl/ImportTemplateServiceImpl.java`）目前只给必填表头设置了"加粗红字"
  样式（`buildRequiredHeaderStyle`），对数据行区域完全没有设置任何 `CellStyle`/`DataFormat`。
  `ImportFieldConfigVO` 本身不带 `controlType`，该字段落在 `FormFieldDefinitionEntity`
  （通过 `formFieldDefinitionId` 关联），`DictImportColumnSupport.resolveDictColumns()`
  已经有"按 `formFieldDefinitionId` 批量 `selectByIds` 查询 `FormFieldDefinitionEntity` 再
  join 回 `configs`"的现成模式可以照搬。POSITION/APP/ORG 的固定标识列（`__userCode`/
  `__orgCode`/`__ownerCode`/`__parentCode`）`formFieldDefinitionId` 恒为 `null`，天然查不到
  `controlType`，需要单独兜底当作"应设为文本格式"处理（这些列存的正是编号/手机号/身份证号
  这类最容易被 Excel 误判成数值的取值）。
- 后端 `BatchImportServiceImpl.importExcel()` 用 `try (Workbook workbook = WorkbookFactory
  .create(file.getInputStream()))` 打开上传文件，在同一个 try 块内完成表头匹配、数据行解析、
  逐行处理（`processDataRows()` 返回 `ImportResultVO`），`workbook` 在 try 块结束时才关闭。
  `ImportFailItemVO.rowNo` 已经是从 1 开始、与 Excel 客户端看到的行号完全一致
  （`row.getRowNum() + 1`）。
- **【2026-07-25 追加】** 首版实现（决策 3 初版）是直接在已解析的原始 `workbook` 上追加
  "错误原因"列后整份序列化返回，导致导出的文件包含全部行（成功的行该列留空），文件名也是
  英文的 `${bizType}-import-errors.xlsx`。用户反馈这份文件应该只包含失败的行（成功的行不需要
  出现在里面），文件名要用中文业务对象类型名 + 时间戳。这两条反馈使决策 3 的实现方式发生了
  调整（见下方决策 3 的最新描述），不再是"整份原文件追加一列"，而是"仅用失败行的数据重新
  拼装一份新文件"。`processDataRows()` 内部解析阶段已经产出的 `ParsedRow`（`rowNo` +
  `Map<fieldCode, String> rowValues`）天然就是构建这份新文件所需的全部数据来源，不需要再回读
  原始 `Sheet` 的单元格。项目里目前没有 backend 侧的"业务对象类型 → 中文名"映射（这个映射目前
  只存在于前端 `FORM_FIELD_BIZ_TYPE_OPTIONS`），需要在后端补一份等价映射用于拼文件名。

## Goals / Non-Goals

**Goals:**
- 批量导入弹窗标题清晰指出当前是哪个业务对象类型的导入。
- 字符串性质的导入列（文本框、字典单选/多选、日期、固定标识列）在下载的模板里默认是 Excel
  文本格式，数字框列保留数字格式。
- 批量导入存在失败行时，前端提供一个"下载失败明细"入口，下载的 `.xlsx` 只包含失败的行（追加
  一列"错误原因"、标红文字），成功的行不出现在这份文件里；文件名为中文业务对象类型名 +
  时间戳（如 `任职失败明细-202607252318.xlsx`）；不再在弹窗里渲染逐行失败表格。

**Non-Goals:**
- 不改变批量导入本身的业务校验规则、行处理顺序、成功/失败判定逻辑——只改"失败明细怎么呈现给
  用户"这一层。
- 不引入服务端临时文件存储或下载 token 机制——错误标注文件通过本次请求的 JSON 响应以 Base64
  形式一次性返回，不做二次下载接口。
- 不处理 `.xls`（旧版 Excel 二进制格式）与 `.xlsx` 在 `CellStyle`/`Font` API 上的差异——
  `WorkbookFactory.create()` 返回的 `Workbook` 接口对两者 API 一致，本次改动直接基于接口
  编程，不关心具体实现类。
- 日期类型列本次统一按"非数字即文本"规则处理，不单独讨论是否需要 Excel 原生日期格式——
  该列的取值本来就以字符串形式被读取和处理（`DataFormatter.formatCellValue`），设为文本格式
  不影响现有解析逻辑。

## Decisions

### 决策 1：导入弹窗标题——纯前端，复用既有字典
`BatchImportDialog.vue` 内 `import { FORM_FIELD_BIZ_TYPE_OPTIONS } from '@/types/metadataField'`，
用 `computed` 按 `props.bizType` 查出 `label`，标题改为 `` `${label}批量导入` ``（组织批量导入/
人员批量导入/任职批量导入/应用批量导入）。四个调用方（`OrgManagementView.vue`/
`UserManagementView.vue`/`PositionManagementView.vue`/`AppManagementView.vue`）不需要改动。

### 决策 2：导入模板数据列文本格式——新增列级判定方法 + 沿用现有样式构造模式
在 `DictImportColumnSupport` 新增一个方法（与 `resolveDictColumns` 平行的职责，命名如
`resolveTextFormatFieldCodes(configs)`），返回 `Set<String>`（应设为文本格式的 `fieldCode`
集合），判定规则：
- `formFieldDefinitionId == null`（固定标识列）→ 文本格式；
- 关联的 `FormFieldDefinitionEntity.controlType != FormFieldControlType.NUMBER` → 文本格式；
- 仅 `controlType == FormFieldControlType.NUMBER` → 不设文本格式，保留默认数字格式。

`ImportTemplateServiceImpl.generateTemplate()` 拿到这个集合后，仿照 `buildRequiredHeaderStyle()`
的写法新增 `buildTextCellStyle(workbook)`（`style.setDataFormat(workbook.createDataFormat()
.getFormat("@"))`），对集合命中的列在数据区域（第 2 行到第 `1 + ImportLimits.MAX_ROW_COUNT`
行，与 `applyDictDropdowns()` 里 `CellRangeAddressList` 的行区间保持一致）逐个 `createCell(...)
.setCellStyle(textStyle)`，与表头样式设置在同一层循环里处理即可，不需要新的循环结构。
备选方案：只对固定标识列和 `dictColumns`/`multiDictColumns` 涉及的列做文本格式，普通 `TEXT`
文本框列不处理——放弃，因为普通文本框列同样可能被管理员配置成填写身份证号/编号之类的自由
文本（如 `remark` 字段虽不太可能，但没有理由排除，统一按"非数字即文本"处理更简单、更不容易
漏判）。

### 决策 3：失败明细改为标注版 Excel 下载——仅含失败行，从解析结果重新拼装新文件
`BatchImportServiceImpl.processDataRows()` 在遍历 `orderedRows` 逐行调用
`importRowExecutor.processRow(...)` 时，已经能区分成功/失败；失败分支目前只把
`rowNo`+`reason` 存进 `failList`，本次改动额外把该 `ParsedRow`（含 `rowValues`）本身也收集
进一个仅在方法内部使用的列表（如 `List<FailedRow> failedRows`，`record FailedRow(ParsedRow
parsedRow, String reason) {}`，不对外暴露，不放进 `ImportResultVO`/`ImportFailItemVO`，避免
让公开的 API 响应结构承载"整行原始数据"这种量级的信息）。

`failedRows` 非空时，调用新增方法 `buildErrorFile(String bizType, List<ImportFieldConfigVO>
configs, List<FailedRow> failedRows)` 生成一份**全新**的 `.xlsx`（不再复用/修改原始上传的
`workbook`）：
1. 表头：按 `configs`（已经是 `showOrder` 升序）逐列取 `excelHeaderName`，之后追加一列
   "错误原因"——这与模板生成的表头顺序完全一致，不依赖用户上传文件实际的列顺序/是否有多余列，
   比"回读原始 Sheet"更简单可靠。
2. 数据行：只写 `failedRows` 里的行，每行按 `configs` 顺序从 `parsedRow.rowValues()`
   （`fieldCode -> 单元格文本`）取值填入对应列，最后一列写 `reason` 并应用红字样式
   （`buildErrorReasonStyle`，同首版实现里的写法：`createFont().setColor(IndexedColors.RED
   .getIndex())`，不加粗）。成功的行完全不出现在这份文件里。
3. 复用决策 2 的 `DictImportColumnSupport.resolveTextFormatFieldCodes(configs)` 对数据列
   （错误原因列除外）设置文本格式——这份文件本质上是"待管理员修正后重新上传"的草稿，同样需要
   避免手机号/身份证号被 Excel 误判成数值，不应该因为是"错误文件"就跳过这层保护。
4. 文件名格式改为中文业务对象类型名 + 固定后缀"失败明细" + 时间戳：
   `` `${bizTypeLabel}失败明细-${yyyyMMddHHmm}.xlsx` ``（如 `任职失败明细-202607252318.xlsx`，
   2026-07-25 追加二次调整：原方案是 `${bizTypeLabel}-${yyyyMMddHHmm}.xlsx`，用户反馈要加上
   "失败明细"这个固定后缀让文件名自解释）。后端目前没有"业务对象类型 → 中文名"的映射，在
   `ImportBizTypes` 新增一个 `labelOf(String bizType)` 方法返回该映射（`ORG→组织、USER→人员、
   POSITION→任职、APP→应用`，与前端 `FORM_FIELD_BIZ_TYPE_OPTIONS` 的文案保持一致，纯静态
   常量映射，不查库）；时间戳用 `DateTimeFormatter.ofPattern("yyyyMMddHHmm")` 格式化
   `LocalDateTime.now()`。
5. 用 `ByteArrayOutputStream` 序列化为字节数组，`Base64.getEncoder().encodeToString(...)`
   填入 `ImportResultVO.errorFileBase64`，`errorFileName` 填入上一步拼出的文件名。
`ImportResultVO` 仍保留既有的 `successCount`/`failList` 字段不变（`failList` 继续是
`rowNo`+`reason` 的轻量结构，供前端展示"失败 N 条"汇总文案）；仅当存在失败行时才生成、填充
`errorFileBase64`/`errorFileName`，全部成功时保持 `null`。
前端 `BatchImportDialog.vue` 的展示与下载交互逻辑（汇总文案 + "下载失败明细"按钮 + Base64
解码触发浏览器下载）不受本次调整影响，无需改动。
备选方案 1（继续在原始 `workbook` 上操作，但导出前先删除成功的行）——放弃，POI 删除行需要
`sheet.removeRow()` 配合 `sheet.shiftRows()` 手动搬移剩余行、重新计算行号映射，比"用已有的
`rowValues` 直接重新拼一份新文件"更繁琐、更容易出边界 bug（如原文件有合并单元格/公式时行
搬移可能破坏引用），而本项目的导入模板本身不含合并单元格或公式，"从解析结果重建"没有信息
损失。
备选方案 2（后端临时存储 + 下载 token 二次请求）——放弃，见 Non-Goals，状态管理成本与本项目
体量不匹配。
备选方案 3（前端用 JS Excel 库基于原始 `File` 对象自己拼装标注文件）——放弃，需要新增前端
npm 依赖（项目目前没有任何 xlsx/exceljs 类库），而后端已经具备 Apache POI，复用决策 2 已有的
文本格式判定逻辑成本更低、无新增依赖。

## Risks / Trade-offs

- [风险，接受] 决策 3 是一次 **BREAKING** 的前端交互变更——用户不再能在弹窗里直接看到每一行
  的失败原因，必须下载文件才能查看 → 这是本次改动明确要解决的问题本身（大量失败时页面展示
  不下），小批量失败时下载一个文件多一步操作，但换来大批量失败时的可用性，是有意为之的取舍。
- [风险] Base64 编码会让响应体积膨胀约 33% → 单次导入上限 `ImportLimits.MAX_ROW_COUNT`
  行（当前较小，具体见该常量），标注版 Excel 本身体积很小，膨胀后仍在正常 JSON 响应体量级，
  不做额外优化。
- [风险，已在决策 3 最新版本中规避] 首版实现依赖 `headerRow.getLastCellNum()` 定位新增列，
  存在"表头行尾部有样式无内容的历史单元格导致新增列位置偏移"的边界问题；改为"从 `configs`
  重新拼装表头"后不再依赖原始文件的列布局，该风险不复存在。
- [风险] 错误文件的列顺序固定为 `configs` 的 `showOrder` 顺序，如果用户原始上传文件的列顺序
  与当前 `configs` 顺序不同（如管理员事后调整过某些列的 `showOrder`），重新下载的错误文件
  列顺序会和用户最初上传的文件不一致 → 可接受：错误文件的定位是"按当前有效配置重新生成的
  待修正稿"，不是"原文件的逐字节镜像"，列顺序与当前模板一致反而更符合"下载模板→修正→重新
  上传"这个闭环里模板应有的样子。
- [风险，实现落地时发现并已修复] "仅含失败行"这个第二版方案改成从 `parsedRow.rowValues()`
  重新拼装错误文件，而 `ImportRowExecutor.resolveDictColumns()` 会把字典列的取值从 label
  **原地改写**成 code——如果直接复用同一个 `ParsedRow`（共享同一个可变 `Map` 引用）构造
  `FailedRow`，字典反查成功、但后续人员/组织标识匹配才失败的行，错误文件里字典列会显示
  改写后的 code（如 `primary`）而不是用户原本填写的 label（如 `主职`），导致修正后重新上传
  会在字典列上产生新的失败。已修复为在调用 `importRowExecutor.processRow(...)` 前先克隆一份
  `rowValues` 用于错误文件展示，不共享会被原地修改的引用；已用真实运行的服务复现问题并验证
  修复，同时补了一个用 `doAnswer` 模拟原地改写行为的单元测试锁定这个修复（纯 mock
  `ImportRowExecutor` 的既有测试无法覆盖这类"依赖被测对象真实内部副作用"的 bug）。

## Migration Plan

三处均为纯代码改动（前端组件 + 后端两个 service 类 + 一个 DTO 新增字段），不涉及数据库变更，
不涉及既有导入字段配置数据，`ImportResultVO` 新增字段是可空的向后兼容扩展（旧前端忽略未知
字段不受影响）。随正常发布流程上线即可。
