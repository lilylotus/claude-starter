## 1. 公共基础

- [x] 1.1 提取批量导入单次最大行数为共享常量（如
      `excelimport/constant/ImportLimits.MAX_ROW_COUNT`），`BatchImportServiceImpl` 与
      模板生成侧共用同一个数值，避免两处各写一份数字。
- [x] 1.2 新增 `excelimport/service/support/DictImportColumnSupport`：输入一组
      `ImportFieldConfigVO`，按 `formFieldDefinitionId` 批量查询
      `FormFieldDefinitionMapper`，过滤出 `controlType == FormFieldControlType.DICT`
      的配置，产出 `fieldCode -> dictTypeCode` 的映射；同时提供按 `dictTypeCode` 取
      `DictItemService.getEnabledOptions` 结果（label 列表、以及 label→code 的查找）
      的辅助方法，供模板生成与批量导入两侧复用。

## 2. 模板生成：字典列下拉

- [x] 2.1 在 `ImportTemplateServiceImpl.generateTemplate` 中，对 `configs` 调用
      `DictImportColumnSupport` 得到本次涉及的字典列集合（`fieldCode -> dictTypeCode`），
      按 `dictTypeCode` 去重后逐个调用 `DictItemService.getEnabledOptions`。
- [x] 2.2 新增一个隐藏辅助 sheet（"字典选项"），每个用到的 `dictTypeCode` 占一列，从第
      2 行开始写入其启用字典项的 `label`（按 `showOrder` 排序）；无启用项时该列留空。
- [x] 2.3 对主表中每个字典列的单元格区域（第 2 行到第 `1 + MAX_ROW_COUNT` 行）用
      `XSSFDataValidationHelper.createFormulaListConstraint` 引用对应的辅助 sheet
      列区域，添加 `XSSFDataValidation`（`errorStyle=STOP`，附中文报错提示）。
- [x] 2.4 用 `workbook.setSheetVisibility(...)` 把辅助 sheet 设为 `HIDDEN`。
- [x] 2.5 验证 `bizType=POSITION`（`positionType` 为字典列）与 `bizType=USER`
      （`gender` 为字典列）的模板生成逻辑：下拉选项内容/顺序、必填表头样式、辅助 sheet
      隐藏状态均符合预期。**执行方式与原计划有出入**：本环境无法交互式打开 Excel/WPS
      手动验证，改为在 `ImportTemplateServiceImplTest` 中用 POI 回读生成的字节内容，
      程序化断言 `XSSFDataValidation` 的公式/区域、辅助 sheet 的单元格内容与可见性
      （见任务 4.1），验证强度等价，只是验证载体从人工目测改为自动化断言。

## 3. 批量导入：字典列反查

- [x] 3.1 在 `ImportRowExecutor.processRow` 中，`checkRequiredColumns` 之后、
      `bindProperties`/业务分支处理之前，新增字典列反查步骤：对每个字典列，若
      `rowValues` 中对应取值非空，按 `DictImportColumnSupport` 提供的 label→code 查找
      精确匹配换算；命中则把 `rowValues` 中该 `fieldCode` 的值就地替换为 `code`，未命中
      则抛出 `BusinessException`（消息包含表头文字与"取值不是有效的字典选项"）。
- [x] 3.2 确认该异常经由 `BatchImportServiceImpl.processDataRows` 既有的 per-row
      try/catch 被正确捕获为该行的失败明细，不影响其余行处理（无需改动
      `BatchImportServiceImpl` 的控制流）。
- [x] 3.3 确认反查发生在必填校验、主键匹配之前不会引入新的顺序问题：必填字典列取值为空
      时仍先由 `checkRequiredColumns` 判定失败（既有行为不变），非必填字典列取值为空时
      跳过反查（保持既有的"非必填数值列留空用默认值"逻辑不受影响）。

## 4. 测试

- [x] 4.1 为 `ImportTemplateServiceImpl` 新增/补充单元测试：字典列生成了带下拉约束的
      `XSSFDataValidation`，选项内容与 `DictItemService.getEnabledOptions` 一致；非字典
      列不受影响；字典类型无启用项时下拉选项为空列表。
- [x] 4.2 为 `ImportRowExecutor`/`BatchImportServiceImpl` 新增/补充单元测试：字典列
      按 label 精确匹配换算为 code 后成功导入；取值不是任何启用 label 时该行进入失败
      明细且不影响其他行；多选字典（`MULTI_DICT`）列不受本次改动影响。另外补充
      `DictImportColumnSupport` 自身的单元测试（覆盖列识别过滤、label 列表透传、
      label→code 精确匹配命中/未命中）。
- [x] 4.3 跑 `./gradlew test`（在 `backend/` 目录下）确保全量测试通过。**过程中发现并
      顺带修复了一个与本 change 无关的预置问题**：`develop` 分支基线上
      `PositionServiceImplTest`/`UserServiceImplTest` 因更早的 `85abeb4`（历史记录字典
      展示问题修复）提交给 `PositionLogSnapshotSupport`/`UserServiceImpl` 增加了
      `DictItemService` 构造参数，但忘记同步更新这两个测试文件的构造调用，导致整个
      `compileTestJava` 无法编译（阻塞全量测试运行，与字典下拉本身无关）。已按生产
      代码现有构造签名机械性补全测试里缺失的 `DictItemService` mock 与构造参数，未涉及
      任何业务逻辑改动。修复后全量 222 个测试用例通过（`./gradlew clean test`）。

## 5. OpenSpec 文档收尾

- [x] 5.1 实现完成后，按 `openspec-doc-sync` 的约定，依据实际 diff 与测试结果核对/更新
      本 change 的 `proposal.md`/`design.md`/`tasks.md`（如有调整）。
- [x] 5.2 已按用户确认执行 `openspec-sync-specs`，delta spec 合入
      `openspec/specs/excel-import-export/spec.md`（"按业务对象类型下载 Excel 导入模板"
      与"按业务对象类型批量导入"两条需求各新增 2 条场景），`openspec validate` 通过。
