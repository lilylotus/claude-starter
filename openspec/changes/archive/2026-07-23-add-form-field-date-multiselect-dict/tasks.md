## 1. 数据库迁移

- [x] 1.1 新增 `backend/src/main/resources/db/migration/V25__update_tab_form_field_definition_control_type_comment.sql`，更新 `tab_form_field_definition.control_type` 列注释，说明新增取值 `4=日期`、`5=多选字典下拉`

## 2. 后端：控件类型常量与校验

- [x] 2.1 `FormFieldControlType` 新增 `DATE=4`、`MULTI_DICT=5` 常量，新增 `DICT_TYPES`（`Set<Integer>`，包含 `DICT`、`MULTI_DICT`）静态集合
- [x] 2.2 `FormFieldDefinitionServiceImpl.validateDictType()` 改为按 `FormFieldControlType.DICT_TYPES.contains(controlType)` 判断是否需要校验 `dictTypeId`
- [x] 2.3 `FormFieldDefinitionServiceImpl.resolveDictOptions()` 改为按 `DICT_TYPES` 判断是否内嵌 `dictOptions`
- [x] 2.4 `FormFieldDefinitionServiceImpl.controlTypeLabel()` 新增 `DATE`→"日期"、`MULTI_DICT`→"多选字典下拉" 分支
- [x] 2.5 全仓搜索 `FormFieldControlType.DICT` 与 `Objects.equals(...DICT)` 的其余引用点，确认是否需要同步改为 `DICT_TYPES` 判断（如有遗漏分支）——发现 `create()`/`update()` 中清空 `dictTypeId` 的判断（原 `!Objects.equals(entity.getControlType(), FormFieldControlType.DICT)`）也一并改为 `!DICT_TYPES.contains(...)`，否则 `MULTI_DICT` 创建/更新时会被错误清空 `dictTypeId`

## 3. 后端：接口文档与校验

- [x] 3.1 检查 `FormFieldDefinitionCreateRequest`/`FormFieldDefinitionUpdateRequest` 上与 `controlType` 取值范围相关的 Bean Validation 注解或 Swagger 描述，同步更新为五种取值——未发现 `@Min`/`@Max` 等范围校验注解，仅更新了 Javadoc 与 `@Schema` 描述文案；顺带同步了 `FormFieldDefinitionVO`/`FormFieldRenderItemVO`/`FormFieldDefinitionEntity`/`FormFieldDefinitionController`/`FormFieldDefinitionService`/`DictTypeRequiredException` 中同类型的硬编码"1=文本框，2=数字框，3=字典下拉"描述，保持 Swagger 文档与代码一致
- [x] 3.2 补充/更新涉及 `controlType` 分支的单元测试（`validateDictType`、`resolveDictOptions`、`controlTypeLabel` 针对 `DATE`、`MULTI_DICT` 的用例）

## 4. 前端：类型与常量

- [x] 4.1 `frontend/src/types/formField.ts` 新增 `FORM_FIELD_CONTROL_TYPE_DATE=4`、`FORM_FIELD_CONTROL_TYPE_MULTI_DICT=5`，更新 `FORM_FIELD_CONTROL_TYPE_OPTIONS`

## 5. 前端：表单管理页面

- [x] 5.1 `FormFieldListView.vue` 的字典类型选择器 `v-if` 条件扩展为 `controlType === DICT || controlType === MULTI_DICT`
- [x] 5.2 `FormFieldListView.vue` 的 `dictTypeId` 必填校验规则触发条件同步扩展
- [x] 5.3 确认 `controlTypeLabel()`（前端）取值表与后端 `controlTypeLabel()` 文案一致

## 6. 前端：动态表单渲染

- [x] 6.1 `useDynamicFormFields.ts` 的 `buildFormModel()` 新增 `DATE`→`null`、`MULTI_DICT`→`[]` 默认值分支
- [x] 6.2 `useDynamicFormFields.ts` 的 `buildRules()` 新增 `DATE` 必填校验（`required + trigger: 'change'`）与 `MULTI_DICT` 必填校验（`type: 'array', required: true, min: 1`）分支
- [x] 6.3 `useDynamicFormFields.ts` 新增/扩展 `dictOptionLabel(s)`，支持对数组值做多值 label 拼接展示，找不到对应字典项时 fallback 展示原始 value
- [x] 6.4 涉及动态渲染字段的列表页/表单页组件（消费 `useDynamicFormFields` 的业务模块，如组织/用户/任职/应用管理页面）验证日期控件与多选字典控件渲染正常

## 7. 验证

- [x] 7.1 后端 `./gradlew test` 全量通过——154 个测试中仅 `RbacApplicationTests.contextLoads()` 因预置测试库的 Flyway 迁移校验和（V4/V5）不匹配而失败，与本次改动无关（V4/V5 文件本次未被改动，`--tests "*FormField*"` 单独运行 25/25 全部通过）
- [x] 7.2 前端 `npm run build`（vue-tsc 类型检查 + vite build）通过
- [ ] 7.3 手动验证：新增一条"日期"类型字段定义、一条"多选字典下拉"字段定义，分别在动态渲染接口与表单管理前端页面确认展示与校验行为符合预期——需要连接真实数据库/浏览器交互，两个实现 agent 均无法执行，留待人工验证

## 8. 手动验证问题修复（多选字典提交格式、日期控件中文本地化）

- [x] 8.1 `frontend/src/main.ts` 引入 `element-plus/dist/locale/zh-cn.mjs`，`app.use(ElementPlus, { locale: zhCn })`，修复 `el-date-picker` 月份/星期表头显示英文的问题
- [x] 8.2 `useDynamicFormFields.ts` 的 `buildFormModel()`：`MULTI_DICT` 回填分支改为对字符串类型的 `sourceValue` 按逗号 `split(',')` 还原为数组（空字符串/`null`/`undefined` 还原为 `[]`）
- [x] 8.3 `useDynamicFormFields.ts` 的 `dictOptionLabels()`：入参兼容逗号分隔的字符串（先 `split(',')` 再按原逻辑映射 label），而不仅仅接受数组
- [x] 8.4 新增 `useDynamicFormFields.ts` 导出函数（如 `buildSubmitModel(fields, model)`），把 `MULTI_DICT` 字段的数组值 `join(',')` 成字符串，供提交前调用
- [x] 8.5 `OrgManagementView.vue`/`UserManagementView.vue`/`PositionManagementView.vue`/`AppManagementView.vue` 的 `submitForm()` 在构建请求 payload 时调用 8.4 的序列化函数，避免把数组直接提交给后端导致 `HttpMessageNotReadableException`
- [x] 8.6 `UserManagementView.vue` 内嵌的"任职信息"子表单：`openEditDialog()` 里手写的 `ext1`~`ext10` 映射改为复用 `positionFields.buildFormModel()`（与 `blankPosition()` 的既有做法一致），`submitForm()` 里 `form.positions.map(...)` 提交前同样对每行的 `MULTI_DICT` 字段做 8.4 的序列化
- [x] 8.7 前端 `npm run build` 通过；手动/尽力验证多选字典下拉新增、编辑回填、列表/详情展示的完整链路——构建通过（额外补了一个 `element-plus/dist/locale/zh-cn.mjs` 的环境声明文件修复 8.1 遗留的 TS7016/TS2769 类型报错，否则 `vue-tsc -b` 无法通过）；四个只读详情页（`OrgDetailView.vue`/`UserDetailView.vue`/`PositionDetailView.vue`/`AppDetailView.vue`）核对后确认 `dictOptionLabels()` 调用点均直接透传原始行/详情数据（未做任何预先的数组转换或守卫），本次 8.3 的兼容性改造后无需改动；无浏览器/数据库环境，无法实际点击验证新增/编辑/回填/展示的端到端行为，以上仅为类型检查 + 构建通过 + 代码走查层面的确认

## 9. 操作历史字典值展示修复（用户报告：多选字典展示的是编码不是标签）

- [x] 9.1 `FormFieldSnapshotSupport` 由静态工具类改为 Spring `@Component`（`@RequiredArgsConstructor` 注入 `DictItemService`、`DictTypeMapper`），`appendExtFieldSnapshot(...)` 改为实例方法
- [x] 9.2 实例方法内按 `definition.getControlType()`/`DICT_TYPES` 判断，对 `DICT` 做单值编码→标签解析，对 `MULTI_DICT` 按逗号切分后逐个解析、用"、"拼接，均带"查不到回退展示原编码"的兜底
- [x] 9.3 `OrgServiceImpl`/`UserServiceImpl`/`AppServiceImpl` 从静态调用改为注入 `FormFieldSnapshotSupport` bean 后走实例方法调用——追查过程中发现 `PositionLogSnapshotSupport`（`org`/`user`/`app` 之外的第 4 个静态调用点，供 `PositionServiceImpl`/`UserServiceImpl.syncPositions` 共用）也直接静态调用了同一方法，静态改实例后若不同步改造会导致编译失败，一并注入改造，副作用是任职记录（`POSITION`）的 ext 字段操作日志快照也随之修复了同样的字典编码展示问题
- [x] 9.4 新增 `FormFieldSnapshotSupportTest`（mock `DictItemService`/`DictTypeMapper`，未复用 `OrgServiceImplTest` 等集成式测试，与本仓库既有的"被测组件单独出单元测试类"风格一致），覆盖：非字典类控件原样展示、`DICT`/`MULTI_DICT` 正常解析为标签、`DICT`/`MULTI_DICT` 部分编码找不到时逐项回退展示原编码、`dictTypeId` 缺失时原样展示、字段定义列表为空时不修改快照，共 7 个用例；同时同步更新了 `OrgServiceImplTest`/`UserServiceImplTest`/`AppServiceImplTest`/`PositionServiceImplTest` 因新增构造参数而需要调整的被测服务构造调用
- [x] 9.5 `./gradlew test` 通过（161/161，含 `RbacApplicationTests.contextLoads()`，本次实测未复现此前记录的 V4/V5 Flyway 校验和历史问题）；核实 `PositionServiceImpl` 通过共享的 `PositionLogSnapshotSupport` 实际已接入 ext 字段操作日志快照（并非任务分派说明中假设的"完全没有接入"），9.3 的改造已一并覆盖到位，不存在需要额外补的缺口
