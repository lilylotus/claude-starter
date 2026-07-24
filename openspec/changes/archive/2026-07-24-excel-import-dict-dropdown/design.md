## Context

`excelimport` 模块（`backend/src/main/java/cn/nihility/rbac/excelimport/`）已经用 Apache POI
（非 EasyExcel）实现了"按 `bizType` 生成/下载导入模板"与"按 `bizType` 批量导入"两条链路，均
由 `tab_import_field_config` 驱动列顺序、表头文字、是否必填。字典类型字段（`positionType`、
`gender` 等）当前只是普通文本列，模板里既不提示合法取值，导入解析时也只是原样把单元格文本
塞进 CreateRequest/UpdateRequest 对应属性（`ImportRowExecutor.bindProperties`），全靠请求
对象自身的 Bean Validation（如果有）兜底，通常直接把不合法的 code 落库或者被业务层校验拒绝，
用户无从得知合法取值有哪些。

"某列是否字典字段"目前没有直接标记：`ImportFieldConfigEntity`/`ImportFieldConfigVO` 只记录
可空的 `formFieldDefinitionId`，字典语义（`controlType`、`dictTypeCode`）挂在
`FormFieldDefinitionEntity` 上（见 `formfield/constant/FormFieldControlType.java` 的
`DICT`/`MULTI_DICT`/`DICT_TYPES`）。项目里已有一处"字典 code → label"的既有实现可参考：
`formfield/support/FormFieldSnapshotSupport.resolveLabelByCode`，用于操作日志展示，内部调用
`dict/service/DictItemService#getEnabledOptions(dictTypeCode)` 拿到该字典类型全部启用项
（`DictItemOptionVO { code, label, showOrder }`，按 `showOrder desc, id asc` 排序）。本次要做
的是同一份数据反过来用：模板列展示 `label` 列表供选择，导入解析把选中的 `label` 换算回
`code`。

## Goals / Non-Goals

**Goals:**
- 生成模板时，`controlType=DICT` 的列在 Excel 中获得一个下拉列表（数据校验），选项是该
  字典类型当前启用项的 `label`，按 `showOrder` 排序，与前端表单里字典下拉的展示顺序一致。
- 批量导入解析该列时，把单元格里的 `label` 文本换算成对应字典项的 `code` 再绑定到
  CreateRequest/UpdateRequest，换算不到（值非空但不在当前启用项 `label` 集合中）时该行判定
  失败，失败原因体现"取值不是有效的字典选项"。
- 复用既有的 `DictItemService#getEnabledOptions`，不新增字典相关的数据访问路径，不改
  `build.gradle`（POI 已经在依赖里）。

**Non-Goals:**
- 多选字典（`controlType=MULTI_DICT`）列的下拉与反查——本次不处理，行为保持不变（纯文本列）。
- 不改变 `ImportFieldConfigVO` 对外（管理页面）暴露的字段结构；是否字典列、对应哪个字典类型，
  只在后端内部（模板生成、批量导入解析）使用，不作为新增的管理接口响应字段。
- 不为"用户直接填写 code"提供兼容识别——模板只教用户选 label，导入侧只按 label 匹配，
  不做"label 或 code 任一匹配即可"的双重兼容（Open Questions 里记录了这个取舍，需最终确认）。

## Decisions

### Decision 1：新增一个内部支持类判断"是否字典列"，不扩展 `ImportFieldConfigVO`
新增 `excelimport/service/support/DictImportColumnSupport`（与现有
`excelimport/service/support/ImportRowExecutor` 同级），对外提供一个方法，输入一组
`ImportFieldConfigVO`，内部按 `formFieldDefinitionId` 批量查询
`FormFieldDefinitionMapper`（`ImportFieldConfigServiceImpl` 已有同样的直接跨模块注入
先例），过滤出 `controlType == FormFieldControlType.DICT` 的配置，产出
`Map<String fieldCode, String dictTypeCode>`。

**备选方案**：把 `controlType`/`dictTypeCode` 加进 `ImportFieldConfigVO`，在
`ImportFieldConfigServiceImpl.enrich()`里一并回填（和现有 `locked` 字段是同样的"计算得出、
不落库"模式）。
**未采用原因**：`ImportFieldConfigVO` 是导入字段配置管理接口（面向"表单管理"页面的导入模板
配置 tab）的响应体，字典元数据只在服务端内部两条链路（模板生成、批量导入解析）里需要，前端
当前没有展示这个信息的需求；为了这个纯后端内部诉求扩大一个已经对外暴露的 DTO 的字段面，属于
本次不需要的额外改动面（YAGNI）。如果之后前端确实要在导入模板配置列表里标注"字典列"，再补
这个字段。

### Decision 2：下拉选项通过隐藏辅助 sheet 提供，不用内联列表常量
POI 的数据校验列表约束（`DataValidationConstraint.createExplicitListConstraint`）有 255
字符总长度限制，字典项数量和 label 长度不受此限制保证，用固定数量的辅助 sheet 区域引用
（`createFormulaListConstraint("'字典选项'!$B$2:$B$N")`）从一开始就避免这个坑，不需要按
"选项是否超长"分支两套实现。做法：
1. 生成模板时先收集当前 `bizType` 下所有字典列涉及到的 `dictTypeCode`（去重）；
2. 为每个 `dictTypeCode` 调用一次 `DictItemService.getEnabledOptions`，把 `label` 列表
   写入一个新增的隐藏 sheet（sheet 名固定为"字典选项"）的一列（一个 `dictTypeCode` 占一列，
   从第 2 行开始，第 1 行留作该 `dictTypeCode` 的说明/表头，便于排查）；
3. 对主表中该字典列的单元格区域（第 2 行到第 `N+1` 行，`N` 取批量导入单次允许的最大行数，
   与 `BatchImportServiceImpl.MAX_ROW_COUNT` 保持一致——实现时把该常量提到一个双方都能引用
   的位置，避免两处各写一份 `1000` 后续改一处漏改另一处）添加
   `XSSFDataValidation`，约束公式指向对应的辅助 sheet 区域，`errorStyle` 设为
   `STOP`（阻止直接输入非下拉值——但这只是客户端体验优化，服务端解析仍然按 Decision 3
   独立校验，不依赖 Excel 本身的约束一定生效，用户仍可能复制粘贴绕过）；
4. 辅助 sheet 通过 `workbook.setSheetVisibility(index, SheetVisibility.HIDDEN)`
   隐藏，避免误导普通填表用户，同时不阻止有需要的人手动取消隐藏查看。

### Decision 3：反查（label → code）发生在批量导入的"逐行执行"阶段，不在"解析阶段"
`BatchImportServiceImpl.parseDataRows` 是一次性、无 try/catch 的批量循环（先完整解析全部行
再进入执行阶段是既有的既定行为，不能被单行失败提前打断）；而
`BatchImportServiceImpl.processDataRows` 对每一行调用 `importRowExecutor.processRow` 时
已经有 per-row 的 try/catch，异常会被转换为该行的失败明细而不影响其余行。因此字典列的
label→code 反查放在 `ImportRowExecutor.processRow` 内部、`checkRequiredColumns` 之后、
`bindProperties` 之前执行：对每个字典列，若该单元格文本非空且在
`DictItemService.getEnabledOptions(dictTypeCode)` 的 `label` 集合里找不到匹配项，直接
`throw new BusinessException(表头文字 + "取值不是有效的字典选项")`，复用既有"单行异常
即失败明细"的机制（`ImportRowExecutor` 类注释里已经记录的既定模式），不需要改动
`BatchImportServiceImpl` 的整体控制流。反查命中后，把 `rowValues` 里该 `fieldCode` 对应的
值就地替换成 `code`，后续 `bindProperties` 不用感知这是不是字典列，行为不变。

### Decision 4：匹配语义为精确字符串匹配 `label`，不兼容直接填 `code`
`resolveEnabledOptions` 拿到的是当次启用状态的字典项快照；反查时按原样字符串比较单元格文本
与每一项的 `label`（不 trim 之外做大小写/模糊匹配）。用户若在模板里手填了字典 `code`
而不是选下拉里的 `label`，除非这个 code 字符串恰好等于某个启用项的 label，否则会被判定为
"取值不是有效的字典选项"——这是有意为之，避免"code 和 label 都认"引入的匹配歧义
（万一某个字典项的 `label` 恰好和另一项的 `code` 撞了，双重匹配会产生二义性）。

## Risks / Trade-offs

- [Excel 数据校验能被绕过（复制粘贴、拖拽填充非法值）] → 服务端反查仍然独立生效
  （Decision 3），Excel 下拉只是引导，不是唯一防线。
- [隐藏辅助 sheet 被用户手动取消隐藏或误删] → 取消隐藏不影响功能（区域引用仍有效）；如果
  用户删除了该 sheet 或其中的行，对应下拉会失效或指向空白，属于用户主动破坏模板结构，导入
  侧的服务端反查依然按当前启用字典项校验，不会因为 Excel 侧下拉失效而放宽或收紧判定。
- [下载模板之后、导入之前，管理员改了某个字典项的 label 或把它停用] → 反查按"导入时刻"的
  启用项集合判断，旧模板里保留的旧 label 会被判定为"取值不是有效的字典选项"，这是预期行为
  （与"启用状态"这个既有语义一致），不是 bug。
- [某个字典类型当前没有任何启用项] → 生成的下拉是空列表，用户无法选择任何值；导入时任何
  非空取值都会反查失败。这种情况需要管理员先在字典管理里维护该类型的启用项，本次不做额外
  兜底（如"临时允许自由文本"）。
- [`MAX_ROW_COUNT` 常量目前只在 `BatchImportServiceImpl` 里定义，模板生成侧需要同一个数值
  确定下拉应用到多少行] → 提取为共享常量（如 `excelimport/constant/ImportLimits`），两处
  引用同一处定义，避免后续修改行数上限时只改一处导致下拉覆盖范围与实际导入上限不一致。

## Open Questions（已确认，保留记录）

- 是否要同时兼容"填 code 也算数"（即单元格文本先按 label 找、找不到再按 code 找）？
  **已确认：不兼容**，按 Decision 4 仅认 label，不做双重匹配。
- 隐藏辅助 sheet 用普通 `HIDDEN` 还是 `VERY_HIDDEN`？**已确认：使用普通 `HIDDEN`**，与
  Decision 2 的实现一致。

## Implementation Notes（实现完成后补记）

- 无启用项时的下拉区域处理：Decision 2/Risks 提到"生成的下拉是空列表"，但 Excel 数据
  校验的公式列表约束要求引用区域首行不晚于末行，不能表达"零行"。实现时统一取
  `max(labelCount, 1)`，即无启用项时仍引用辅助 sheet 上紧邻表头的一个空白单元格，
  呈现为"只有一个空白可选项"的下拉，而不是完全没有下拉约束；效果上仍然是"用户选不出
  任何有意义的值"，与设计意图一致，只是实现层面用一个空白单元格代替"零选项"。
- `DictImportColumnSupport` 未采用 `formFieldDefinitionMapper.selectBatchIds`（该方法
  在当前 MyBatis-Plus 版本已标记 `@Deprecated`），改用非弃用的 `selectByIds`，行为等价。
- 任务 2.5 的手动 Excel/WPS 验证，在本次自动化实现流程中改为 POI 回读断言（读取生成的
  `.xlsx` 字节内容，断言 `XSSFDataValidation` 的公式/区域、辅助 sheet 单元格内容与
  `SheetVisibility`），验证内容等价，只是验证方式从人工目测调整为自动化测试，测试用例见
  `ImportTemplateServiceImplTest`。如果需要人工在 Excel/WPS 中做一次最终视觉确认，可
  另行下载 `bizType=POSITION`/`USER` 的模板手动打开检查。
- 实现过程中发现 `develop` 分支基线已存在一个与本 change 无关的编译阻塞问题：
  `PositionServiceImplTest`/`UserServiceImplTest` 未同步此前 `85abeb4` 提交给
  `PositionLogSnapshotSupport`/`UserServiceImpl` 新增的 `DictItemService` 构造参数。
  为了能够跑通 `./gradlew test`，已按现有生产代码构造签名机械性修复这两个测试文件
  （仅补齐缺失的 mock 与构造参数，未改动任何断言或业务逻辑），修复后全量测试通过。
