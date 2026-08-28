## 1. 数据库迁移

- [x] 1.1 在 `backend/src/main/resources/db/migration/` 新增下一个可用序号的 `V*__add_form_field_show_in_export.sql`：为 `tab_form_field_definition` 新增 `show_in_export TINYINT(1) NOT NULL DEFAULT 1` 列，随后执行 `UPDATE tab_form_field_definition SET show_in_export = show_in_list`；验证：`./gradlew bootRun` 启动时 Flyway 迁移无报错，`DESCRIBE tab_form_field_definition;` 能看到新列，存量记录 `show_in_export` 与 `show_in_list` 取值一致

## 2. 表单字段定义：新增"是否导出"配置

- [x] 2.1 `FormFieldDefinitionEntity` 新增 `showInExport` 字段（`Boolean`），验证：实体编译通过，字段命名与既有 `showInList`/`showInCreate` 风格一致
- [x] 2.2 `FormFieldDefinitionCreateRequest`/`FormFieldDefinitionUpdateRequest`/`FormFieldDefinitionVO`/`FormFieldRenderItemVO` 新增 `showInExport` 字段，`FormFieldDefinitionConvert`（MapStruct）同步映射；验证：`./gradlew build` 编译通过
- [x] 2.3 `FormFieldDefinitionServiceImpl` 新增/更新逻辑中，确认 `showInExport` 的写入 **不** 加入 `LockedFormFields`/`locked` 相关的锁定校验分支（承重字段的 `isRequired`/`showInCreate`/`showInEdit`/状态/删除保护逻辑保持原样，不覆盖到 `showInExport`）；验证：新增一个针对承重字段（如绑定 `code` 的定义）把 `showInExport` 从 `true` 改为 `false` 的更新请求能正常保存的单元测试
- [x] 2.4 `GET /api/form-fields/render-schema` 响应携带 `showInExport`；验证：调用该接口返回结果包含该字段，对应单元/集成测试通过
- [x] 2.5 补充/更新 `formfield` 相关单元测试覆盖：新增字段定义默认 `showInExport` 行为、更新时独立于 `showInList` 调整、承重字段可自由调整 `showInExport`；验证：`./gradlew test --tests "cn.nihility.rbac.formfield.*"` 通过

## 3. 后端：excelexport 模块

- [x] 3.1 创建 `cn.nihility.rbac.excelexport` 包骨架（`controller`/`service`/`service.impl`/`support`/`constant`），复用 `excelimport.constant.ImportBizTypes`（或抽取共用的 `bizType` 中文名映射常量，避免和 excelimport 各自维护一份不一致的映射）；验证：包结构创建完成，`bizType` 中文名映射在导出/导入两处结果一致
- [x] 3.2 新增 `ExportDictLabelSupport`：给定 `controlType`/`dictTypeCode`/原始存储值，返回单选/多选字典的展示标签（多选按逗号切分、顿号"、"拼接，找不到匹配回退原始值），复用 `DictItemService.getEnabledOptions`；验证：针对单选命中、单选未命中回退、多选混合命中/未命中三种场景的单元测试通过
- [x] 3.3 在 `OrgServiceImpl`（或抽取的独立支持类）新增导出用查询：复用现有"查询全部组织实体 + 按 `allowedOrgIds` 过滤"逻辑，返回按 `id` 升序的扁平 `OrgVO` 列表（不做树形组装）；验证：受限/不受限两种场景下返回集合分别符合管辖范围的单元测试通过
- [x] 3.4 在 `PositionServiceImpl`（或抽取的独立支持类）新增"按管辖范围查询全部任职记录"的方法：受限时 `WHERE org_id IN (:allowedOrgIds)`，不受限时不过滤，复用现有联表查询 SQL 但去除单一 `orgId` 参数与分页；验证：受限/不受限两种场景下返回集合分别符合管辖范围的单元测试通过
- [x] 3.5 在 `AppServiceImpl`（或抽取的独立支持类）新增导出用查询：复用现有分页查询已具备的 `allowedOrgIds` 过滤逻辑，去除分页，返回全部匹配 `AppVO`；验证：受限/不受限两种场景下返回集合分别符合管辖范围的单元测试通过
- [x] 3.6 新增用户导出查询：直接查询全部未删除 `UserVO`，不接入 `OrgScopeService`；验证：管辖范围受限的用户调用该查询仍返回全部未删除用户的单元测试通过
- [x] 3.7 新增 `ExcelExportService`/`ExcelExportServiceImpl`：按 `bizType` 分发到 3.3~3.6 的查询方法获取数据行；查询 `tab_form_field_definition`（状态启用且 `showInExport=true`，按 `showOrder` 升序）得到字段定义驱动列；POSITION/APP 额外拼接设计文档 Decision 4 描述的固定关联展示列（POSITION 前置"姓名""组织"，APP 后置"负责人""所属组织"）；对每个字段定义驱动列用 `BeanWrapperImpl` 按 `columnName` 反射取值，字典/多选字典列经 `ExportDictLabelSupport` 换算为标签；数据行数超过 50000 时抛出业务异常且不生成文件；验证：四个 `bizType` 各自的列顺序、固定列位置、字典标签换算、超限拒绝的单元测试通过
- [x] 3.8 `ExcelExportServiceImpl` 用 `SXSSFWorkbook` 生成 `.xlsx` 字节数组：表头加粗，数据行只写文本值，不设置数据校验/下拉；验证：生成的文件能被 Apache POI 正常读回、行列数与预期一致的单元测试通过
- [x] 3.9 新增 `ExcelExportController`：`GET /api/excel-export/download?bizType=` 返回 `.xlsx` 文件流，`Content-Disposition` 文件名规则为 `<bizType中文名>导出-yyyyMMddHHmm.xlsx`（参考 `ExcelImportController.downloadTemplate` 的响应头写法），补充 `@Tag`/`@Operation`/`@Parameter` springdoc 注解；验证：`curl`/集成测试调用该接口返回 200 与正确的 `Content-Type`/`Content-Disposition`

## 4. 权限编码

- [x] 4.1 新增四个权限点 `OrgManagement:org:export`、`UserManagement:user:export`、`PositionManagement:position:export`、`AppManagement:app:export`（按项目现有权限数据初始化方式登记，如迁移脚本/初始化 SQL，与 `xxx:import` 权限点的登记方式保持一致）；验证：登录拥有对应角色的账号后，`GET` 当前用户权限编码接口返回结果包含新增编码
- [x] 4.2 更新仓库根目录 `权限资源.txt`，在对应模块的 `import`/`importTemplate` 条目后追加 `export` 条目；验证：`git diff 权限资源.txt` 显示四条新增记录，格式与既有条目一致

## 5. 前端

- [x] 5.1 新增 `frontend/src/api/excelExport.ts`：封装 `GET /excel-export/download?bizType=`，`responseType: 'blob'`，触发浏览器下载（参考 `api/excelImport.ts` 下载模板函数的实现）；验证：TypeScript 编译通过（`npm run build`）
- [x] 5.2 `OrgManagementView.vue` 工具栏在"批量导入"按钮之后新增"导出Excel"按钮，`v-if="hasPermission('OrgManagement:org:export')"`，点击调用 5.1 的导出函数（`bizType=ORG`）；验证：本地启动前后端，用拥有/不拥有该权限的账号分别验证按钮显隐与点击下载行为
- [x] 5.3 `UserManagementView.vue` 同样新增"导出Excel"按钮（`bizType=USER`，权限码 `UserManagement:user:export`）；验证同 5.2
- [x] 5.4 `PositionManagementView.vue` 同样新增"导出Excel"按钮（`bizType=POSITION`，权限码 `PositionManagement:position:export`）；验证同 5.2
- [x] 5.5 `views/application/app/AppManagementView.vue` 同样新增"导出Excel"按钮（`bizType=APP`，权限码 `AppManagement:app:export`）；验证同 5.2
- [x] 5.6 表单管理页面（字段定义新增/编辑弹窗，`/system/form-fields`）新增"是否导出"勾选项，与"是否列表展示"等勾选项并列展示，回填/提交 `showInExport`；承重字段编辑时该勾选项保持可编辑（不禁用），与"是否必填"等被禁用的勾选项区分开；验证：本地启动后手动编辑一条普通字段与一条承重字段（如绑定 `code`），确认"是否导出"均可勾选/取消并保存成功
- [x] 5.7（补充，事后发现遗漏）`FormFieldDefinitionPanel.vue` 字段定义列表的"属性"列此前只渲染了 `isRequired`/`isUnique`/`showInList`/`showInCreate`/`showInEdit`/`editable`/`locked` 对应标签，遗漏了 5.6 新增的 `showInExport`；补充渲染"导出"标签，位置紧跟"编辑"标签之后；验证：勾选某字段"是否导出"保存后，列表该行"属性"列出现"导出"标签，取消勾选后标签消失

## 6. 端到端验证

- [x] 6.1 `./gradlew build` 后端整体编译 + 单元测试全部通过
- [x] 6.2 `npm run build` 前端类型检查 + 构建通过
- [x] 6.3 本地启动前后端，用一个配置了受限管辖组织范围的管理员账号，分别对组织/人员/任职/应用点击"导出Excel"，人工检查：导出的组织/任职/应用数据只包含管辖范围内的记录，人员导出包含全部用户；任职/应用导出包含固定的姓名/组织、负责人/所属组织列；字典字段展示中文标签；表单管理页面调整某字段"是否导出"为否后重新导出，确认该列从结果中消失
