## Why

Excel 导入模板中，字典类型字段（如任职类型 `positionType`、性别 `gender`）目前只是普通文本
列，用户必须手工填写字典 `code`（如 `1`/`M`）才能被批量导入正确识别，容易因为记不住 code
含义而填错，且填错时批量导入没有前置校验、只能等提交后看失败明细。需要让这些列在模板里变成
下拉选择，用户选可读的字典值（`label`），系统在导入解析时再把选中的 `label` 换算回对应的
`code`。

## What Changes

- 生成 Excel 导入模板时，对属于字典类型（`controlType` 为 `FormFieldControlType.DICT`）的
  列，使用 POI 数据校验（`DataValidationHelper` + `XSSFDataValidation`）为该列整列单元格
  添加下拉列表约束，下拉选项来源于 `DictItemService.getEnabledOptions(dictTypeCode)` 返回的
  启用项 `label` 列表（按 `showOrder` 排序）；选项较多（超出 Excel 下拉直接内联的长度限制）
  时，选项写入一个隐藏辅助 sheet，下拉引用该 sheet 区域。
- 批量导入解析数据行时，对字典类型列，把单元格文本（`label`）按同一份 `code`↔`label` 对照
  表反向解析为 `code` 后再绑定到创建/更新请求对象；单元格取值无法在该字典类型的启用项中找到
  匹配的 `label` 时，该行判定为失败，失败原因说明该列取值不是有效的字典选项。
- `ImportFieldConfigVO`（以及模板生成、批量导入内部使用的配置读取路径）需要能拿到该列关联
  的表单字段定义的 `controlType` 与 `dictTypeCode`，用于判断"是否字典列"及取哪个字典类型的
  选项——具体是否扩展 `ImportFieldConfigVO` 字段、还是在服务内部另行查询，由 design.md 决定。
- 多选字典（`controlType=MULTI_DICT`）不在本次范围内，本次只处理单选字典列（`DICT`）。

## Capabilities

### Modified Capabilities
- `excel-import-export`: "按业务对象类型下载 Excel 导入模板" 需求新增——字典类型列渲染为
  下拉列表（选项为启用字典项的 label）；"按业务对象类型批量导入" 需求新增——字典类型列按
  label 反查 code 写入业务记录，反查失败时该行判定为失败。

## Impact

- `backend/src/main/java/cn/nihility/rbac/excelimport/service/impl/ImportTemplateServiceImpl.java`
  ——新增按字典列附加数据校验下拉的逻辑。
- `backend/src/main/java/cn/nihility/rbac/excelimport/service/impl/BatchImportServiceImpl.java`
  与 `excelimport/service/support/ImportRowExecutor.java` ——数据行解析阶段新增字典列
  label→code 反查与失败原因。
- `excelimport/dto/ImportFieldConfigVO.java` / `entity/ImportFieldConfigEntity.java` 或相关
  service 内部查询路径——需要能获取列对应的 `controlType`/`dictTypeCode`。
- 依赖既有 `dict/service/DictItemService#getEnabledOptions(String dictTypeCode)`，不新增
  依赖、不改动 `build.gradle`。
- `openspec/specs/excel-import-export/spec.md` 增补两条需求下的场景。
