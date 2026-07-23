## 1. 数据库迁移

- [x] 1.1 新增迁移（下一个可用版本号，先确认当前迁移目录最大版本号）：为
      `tab_form_field_definition` 新增 `dict_type_code VARCHAR(64) NULL` 列，按现有
      `dict_type_id → tab_dict_type.id` 关联关系批量回填对应的 `code`，再删除
      `dict_type_id` 列，写法见 design.md 决策 1（实际版本号 V34，见
      `V34__convert_form_field_dict_type_id_to_code.sql`）

## 2. 后端：字段定义实体与 DTO

- [x] 2.1 `FormFieldDefinitionEntity.dictTypeId`（`Long`）改为 `dictTypeCode`
      （`String`），`@TableField` 注解的 `value` 同步改为 `dict_type_code`，保留
      `updateStrategy = FieldStrategy.ALWAYS`
- [x] 2.2 `FormFieldDefinitionCreateRequest`/`FormFieldDefinitionUpdateRequest` 的
      `dictTypeId`（`Long`）改为 `dictTypeCode`（`String`），加
      `@Size(max = 64, message = "字典类型编码长度不能超过 64 个字符")`
- [x] 2.3 `FormFieldDefinitionVO.dictTypeId`（`Long`）改为 `dictTypeCode`（`String`）

## 3. 后端：服务层校验与查询改为按编码

- [x] 3.1 `FormFieldDefinitionServiceImpl.validateDictType`：参数改为
      `(Integer controlType, String dictTypeCode)`，控件类型属于 `DICT_TYPES` 时按
      `tab_dict_type.code` 查询存在一条未逻辑删除的记录，否则抛
      `DictTypeRequiredException`
- [x] 3.2 `FormFieldDefinitionServiceImpl.resolveDictOptions`：参数改为
      `(Integer controlType, String dictTypeCode)`，直接
      `dictItemService.getEnabledOptions(dictTypeCode)`，删除原先"先查字典类型换出
      code"的中间步骤
- [x] 3.3 `FormFieldDefinitionServiceImpl.fetchDictTypeNameMap`：改为按字典类型
      `code` 集合批量查询，返回 `Map<String, String>`（code → name）
- [x] 3.4 `FormFieldDefinitionServiceImpl.enrich()`/`create()`/`update()` 里所有
      `getDictTypeId()`/`setDictTypeId()` 调用改为
      `getDictTypeCode()`/`setDictTypeCode()`
- [x] 3.5 `FormFieldSnapshotSupport.resolveLabelByCode`：参数改为
      `(String dictTypeCode)`，直接 `dictItemService.getEnabledOptions(dictTypeCode)`；
      如果这是该类里唯一用到 `DictTypeMapper` 的地方，移除该依赖注入
- [x] 3.6 全仓库搜索确认没有遗漏的 `dictTypeId`/`getDictTypeId`/`setDictTypeId`
      引用（注意排除 `tab_dict_item.dict_type_id` 相关的、语义完全不同的引用，见
      design.md Non-Goals）

## 4. 前端：字段定义弹窗改为提交字典类型编码

- [x] 4.1 `frontend/src/types/formField.ts`：`FormFieldDefinition`/
      `FormFieldDefinitionFormFields` 的 `dictTypeId: number | null` 改为
      `dictTypeCode: string | null`
- [x] 4.2 `FormFieldDefinitionPanel.vue`：字典类型 `<el-select>` 的 `<el-option>`
      `:value="opt.id"` 改为 `:value="opt.code"`；`form.dictTypeId` 及其初始值、
      编辑回填（`form.dictTypeId = row.dictTypeId`）、提交 payload、`rules` 里的 key
      全部改名为 `dictTypeCode`；`validateDictType` 校验函数参数类型改为
      `string | null`

## 5. 文档同步

- [x] 5.1 更新本 change 的 spec delta
      `openspec/changes/form-field-dict-type-code/specs/form-field-definition-management/spec.md`：
      "字段定义的控件类型配置"需求条目里 `dictTypeId` 改为 `dictTypeCode`，并新增
      "关联的字典类型编码不存在时拒绝保存"场景（对应 `validateDictType` 按 `code` 查询
      排除逻辑删除记录的校验）；同步到 `openspec/specs/` 是 `openspec-sync-specs` 归档
      时的职责，不在本任务范围内
- [x] 5.2 实现完成后，按 `.claude/agents/openspec-doc-sync.md` 约定，对照真实 diff/
      测试结果核对并更新本 change 的 `tasks.md`/`design.md`/`proposal.md`

## 6. 测试与验证

- [x] 6.1 后端：`./gradlew compileJava compileTestJava`、
      `./gradlew test --tests "cn.nihility.rbac.formfield.*"` 通过；检查是否有既有
      测试直接构造/断言 `dictTypeId`，同步调整为 `dictTypeCode`
- [x] 6.2 前端：`npm run build`（vue-tsc 类型检查 + vite build）通过
- [ ] 6.3 手动验证（需要真实 MySQL 环境，留给用户本地执行）：
      - `./gradlew build` 全量测试通过，重点关注新迁移在真实库上执行后
        `tab_form_field_definition` 现有数据（含"性别"字段定义，如果
        `user-gender-dict-and-import-defaults` 已先落地）的 `dict_type_code` 是否
        正确回填
      - 表单管理 - 字段定义页面：新增/编辑一条字典下拉类型的字段定义，字典类型选择器
        正常展示可选项、保存成功；对应业务模块页面的动态渲染（下拉选项、操作日志字典
        值展示）恢复正常，与改造前行为一致
