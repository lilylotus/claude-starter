## 1. 后端：表单字段定义排序方向

- [x] 1.1 `FormFieldDefinitionServiceImpl.getPage`：`orderByDesc(...ShowOrder)` 改为
      `orderByAsc(...ShowOrder)`
- [x] 1.2 `FormFieldDefinitionServiceImpl.listActiveByBizType`：同上
- [x] 1.3 `FormFieldDefinitionServiceImpl.buildRenderSchema`：同上（`GET
      /api/form-fields/render-schema` 的排序方向，驱动 org/user/position/app 四个动态
      表单字段顺序）

## 2. 后端：导入字段配置排序方向

- [x] 2.1 `ImportFieldConfigServiceImpl.getPage`：`orderByDesc(...ShowOrder)` 改为
      `orderByAsc(...ShowOrder)`
- [x] 2.2 `ImportFieldConfigServiceImpl.listActiveByBizType`：同上（驱动 Excel 导入模板
      表头顺序与批量导入必填校验遍历顺序）

## 3. 前端

- [x] 3.1 `frontend/src/composables/useDynamicFormFields.ts` 的 `sortBySchemaOrder`
      比较器从 `b.showOrder - a.showOrder` 改为 `a.showOrder - b.showOrder`
- [x] 3.2 `frontend/src/views/system/formfields/FormFieldDefinitionPanel.vue` 提示文案
      "数值越大，排序越靠前"改为"数值越小，排序越靠前"
- [x] 3.3 `frontend/src/views/system/formfields/ImportFieldConfigPanel.vue` 提示文案同上
      改动，及页面顶部注释里"按 showOrder 降序"改为"按 showOrder 升序"

## 4. 已有测试的排序断言核对

- [x] 4.1 检查 `formfield`/`excelimport` 模块下现有单元/集成测试：均为 Mockito 单元测试，
      直接打桩 mapper 的 `selectPage`/`selectList` 返回值，不实际执行真实的 SQL
      `ORDER BY`，因此没有依赖排序方向的断言需要改动；`ImportTemplateServiceImplTest`
      的表头顺序断言验证的是"服务写入顺序 = 查询结果返回顺序"，与排序方向无关，同样无需
      改动。只更新了两处 Javadoc 注释里"按显示序号降序"的过时表述为"升序"
      （`ImportTemplateServiceImplTest` 类注释与方法注释）

## 5. openspec 文档同步

- [x] 5.1 `openspec/changes/add-excel-import-export/design.md`、`tasks.md`、
      `specs/excel-import-export/spec.md` 里所有"按 showOrder 降序"相关表述原地改为
      "升序"（该 change 尚未归档，不走 delta 机制），并追加一句指向本 change 的说明
- [x] 5.2 本 change 的 `specs/form-field-definition-management/spec.md` MODIFIED
      delta 已就绪；额外发现并同步更新了 5 处后端 Javadoc/`@Operation` 注释（原 tasks
      未列出，实现时顺带核对到）：`FormFieldDefinitionController`、
      `FormFieldDefinitionService`、`ExcelImportController`、
      `ImportFieldConfigController`、`ImportFieldConfigService`、
      `ImportTemplateService` 里"按显示序号降序排列"改为"升序排列"

## 6. 验证

- [x] 6.1 `./gradlew compileJava compileTestJava` 通过；
      `./gradlew test --tests "cn.nihility.rbac.formfield.*" --tests
      "cn.nihility.rbac.excelimport.*"` 全部通过
- [x] 6.2 `npm run build`（vue-tsc + vite build）通过
- [ ] 6.3 手动验证：表单管理页面两个 tab 的列表按显示序号升序展示；任一 org/user/
      position/app 动态表单的字段顺序与列表列顺序按升序生效；下载导入模板确认表头列顺序
      按升序排列（未在本次会话中做浏览器端手动验证，建议用户本地起 `npm run dev` +
      `./gradlew bootRun` 走一遍）
