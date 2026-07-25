## 1. 前端：批量导入弹窗标题按业务对象类型显示

- [x] 1.1 `frontend/src/components/BatchImportDialog.vue` 引入
      `FORM_FIELD_BIZ_TYPE_OPTIONS`（`@/types/metadataField`），按 `props.bizType` 计算出
      显示名称，把 `<el-dialog title="批量导入">` 改为 `` `${label}批量导入` ``。
- [x] 1.2 确认组织/用户/任职/应用四个调用方无需改动（只传 `biz-type`，不需要新增 prop）。

## 2. 后端：导入模板字符串列设为 Excel 文本格式

- [x] 2.1 `DictImportColumnSupport` 新增 `resolveTextFormatFieldCodes(configs)` 方法，判定
      规则：`formFieldDefinitionId == null`（固定标识列）或关联的 `controlType !=
      FormFieldControlType.NUMBER` 均返回 true（应设文本格式），复用
      `resolveDictColumns()` 里"按 `formFieldDefinitionId` 批量 `selectByIds`"的查询模式。
- [x] 2.2 `ImportTemplateServiceImpl.generateTemplate()` 新增 `buildTextCellStyle(workbook)`
      （`setDataFormat(workbook.createDataFormat().getFormat("@"))`），对
      `resolveTextFormatFieldCodes()` 命中的列在数据区域（第 2 行到第
      `1 + ImportLimits.MAX_ROW_COUNT` 行，与 `applyDictDropdowns()` 的行区间一致）设置该
      样式。
- [x] 2.3 补充/更新单元测试：`DictImportColumnSupportTest`、`ImportTemplateServiceImplTest`
      新增用例，验证 POSITION 模板中 `__userCode`/`__orgCode` 固定列、`positionType`
      （DICT）列的数据区域为文本格式，`showOrder`（NUMBER）列不是。**已额外用真实运行的服务
      做过活体验证**：重启 `bootRun` 后下载 POSITION 模板，用一个临时 JUnit 读取该文件，确认
      人员编号/组织编码/任职类型/任职地址/任职电话/备注 六列 `dataFormat=@`，显示序号列未创建
      数据单元格（保持默认数字格式），与设计一致；验证完成后已删除临时测试类与临时文件。

## 3. 后端：批量导入失败时生成标注版错误文件（首版）

- [x] 3.1 `ImportResultVO` 新增可空字段 `errorFileBase64`（String）、`errorFileName`
      （String）。
- [x] 3.2（**已被 3.6~3.9 取代**，见下方"第二版"）~~`BatchImportServiceImpl.importExcel()`
      在 `processDataRows()` 得到结果后、`try (Workbook workbook = ...)` 块结束前，若
      `failList` 非空：定位 `headerRow.getLastCellNum()` 作为新增列下标，在原始 `workbook`
      上追加"错误原因"列~~——用户反馈错误文件不应包含成功的行、文件名要用中文，首版"直接在
      原始 workbook 上追加一列"的实现方式被下方第二版取代（不是叠加，是替换）。
- [x] 3.3（同上，已被取代）
- [x] 3.4（同上，已被取代，验证方式沿用到第二版重新做一遍）

### 第二版（用户反馈后调整）：仅含失败行 + 中文文件名

- [x] 3.5 `ImportBizTypes` 新增 `labelOf(String bizType)` 方法，返回业务对象类型的中文名
      （`ORG→组织、USER→人员、POSITION→任职、APP→应用`），与前端 `FORM_FIELD_BIZ_TYPE_OPTIONS`
      的文案保持一致。
- [x] 3.6 `BatchImportServiceImpl.processDataRows()` 遍历 `orderedRows` 处理失败分支时，
      除了现有的 `failList.add(...)`，额外把该 `ParsedRow` 连同失败原因收集进一个方法内部
      使用的列表（`record FailedRow(ParsedRow parsedRow, String reason) {}`，不放进
      `ImportResultVO`/`ImportFailItemVO`，不对外暴露）。
- [x] 3.7 新增私有方法 `buildErrorFile(String bizType, List<ImportFieldConfigVO> configs,
      List<FailedRow> failedRows)`：表头按 `configs`（`showOrder` 升序）逐列取
      `excelHeaderName` 生成、末尾追加"错误原因"列；数据行只写 `failedRows`（成功的行不出现），
      每行按 `configs` 顺序从 `parsedRow.rowValues()` 取值填入对应列，最后一列写 `reason`
      并应用 `buildErrorReasonStyle`（红字，沿用首版实现）；复用 2.1 的
      `dictImportColumnSupport.resolveTextFormatFieldCodes(configs)` 对数据列（错误原因列
      除外）设置文本格式；文件名用 `` `${ImportBizTypes.labelOf(bizType)}-${时间戳}.xlsx` ``，
      时间戳格式 `DateTimeFormatter.ofPattern("yyyyMMddHHmm")` 格式化 `LocalDateTime.now()`
      （如 `任职-202607252318.xlsx`）。
- [x] 3.8 `importExcel()` 里改为调用 `buildErrorFile(...)`（`failedRows` 非空时）替换掉
      首版直接操作原始 `workbook` 的逻辑；全部成功（`failedRows` 为空）时
      `errorFileBase64`/`errorFileName` 仍保持 `null`。
- [x] 3.9 更新 `BatchImportServiceImplTest` 里首版新增的测试用例：断言从"新增列内容/红字"
      调整为"错误文件只有失败行、行数等于失败明细条数、列顺序与 `configs` 一致、成功行的数据
      完全不出现在文件里、文件名匹配 `业务对象类型中文名-12位时间戳.xlsx` 的格式"。
- [x] 3.10（**实现代理未预料到、由后续真实活体验证发现并修复的 bug**）
      `ImportRowExecutor.resolveDictColumns()` 会把字典列的取值从 label **原地改写**成
      code（`rowValues.put(fieldCode, code)`），而 `processDataRows()` 传给
      `importRowExecutor.processRow(...)` 的正是 `parsedRow.rowValues()` 这个可变 Map 的
      引用；如果该行在字典反查成功之后、人员/组织标识匹配阶段才判定失败，`failedRows` 捕获到
      的就是已经被改写成 code 的值，导致导出的错误文件里字典列（如"任职类型"）显示的是
      `primary` 这类内部编码而不是用户原本填写的 `主职` 标签——用户按这份文件修正后重新
      上传，会在字典列上出现新的、莫名其妙的失败。修复：在调用 `processRow(...)` 前先
      `new LinkedHashMap<>(parsedRow.rowValues())` 克隆一份，失败时用这份克隆构造
      `FailedRow`，不再共享会被原地修改的引用。已在真实运行的服务上用一份"人员标识不存在 +
      任职类型为字典 label"的测试数据复现问题、验证修复；并在
      `BatchImportServiceImplTest` 新增
      `importExcel_shouldKeepOriginalDictLabel_whenProcessRowMutatesRowValues` 用例，用
      `doAnswer` 模拟 `processRow` 原地改写 + 抛异常的行为，锁定这个修复（不加这个用例的话，
      现有测试都是 mock 掉 `importRowExecutor`，永远不会触发真实的原地改写逻辑，无法覆盖
      这个 bug）。

## 4. 前端：失败明细改为下载入口

- [x] 4.1 `BatchImportDialog.vue` 删除现有渲染 `result.failList` 的 `el-table`，改为展示
      "成功 {successCount} 条，失败 {failList.length} 条"汇总文案。
- [x] 4.2 `failList.length > 0` 时展示"下载失败明细"按钮，点击后把 `errorFileBase64` 解码
      （`atob` + `Uint8Array`）为 `Blob`（MIME
      `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`），
      `URL.createObjectURL` 后用隐藏的 `<a download>` 触发浏览器下载，文件名取
      `errorFileName`（写法与 `src/api/excelImport.ts` 里下载模板的方式对齐）。

## 5. 第三版（用户反馈后再调整）：错误文件名加"失败明细"固定后缀

- [x] 5.1 `BatchImportServiceImpl.buildErrorFile()` 里文件名拼接从
      `` `${ImportBizTypes.labelOf(bizType)}-${时间戳}.xlsx` `` 改为
      `` `${ImportBizTypes.labelOf(bizType)}失败明细-${时间戳}.xlsx` ``（如
      `任职失败明细-202607252318.xlsx`）。
- [x] 5.2 同步更新 `BatchImportServiceImplTest` 里断言文件名格式的正则
      （`^组织-\d{12}\.xlsx$` → `^组织失败明细-\d{12}\.xlsx$`）。
- [x] 5.3 已用真实运行的服务验证：上传一份 POSITION 保证失败的数据，确认响应
      `errorFileName` 为 `任职失败明细-202607252348.xlsx`，格式符合预期。

## 6. 验证

- [x] 6.1 `./gradlew test` 全量通过。
- [x] 6.2 `npm run build`（vue-tsc 类型检查 + vite build）通过。
- [x] 6.3 端到端验证：已完成后端两处的真实活体验证（见第 2、3 组备注：模板文本格式 + 错误
      标注文件均对真实运行的服务发起过 HTTP 请求验证）。第二版调整（仅含失败行 + 中文文件名）
      同样重启服务后用真实数据验证：上传 1 条成功 + 1 条失败的任职数据，确认响应
      `errorFileName` 为 `任职-202607252331.xlsx` 格式（业务对象类型中文名+12位时间戳），
      解码后的错误文件 `lastRowNum=1`（只有失败那一行，成功行不出现），并在验证过程中发现并
      修复了 3.10 记录的字典 label 被原地改写的 bug、复测确认修复生效；第三版调整（文件名加
      "失败明细"后缀）见上方第 5 组，同样已用真实服务验证。**未做浏览器可视化
      验证**（本会话没有可用的浏览器自动化工具）——弹窗标题文案变化、"下载失败明细"按钮的
      点击/下载交互、以及"填入手机号/身份证号后另存重新打开确认仍是文本"这几步需要真实浏览器
      操作，未能在本会话内验证，建议使用者用浏览器手动确认一遍视觉效果与下载交互。
