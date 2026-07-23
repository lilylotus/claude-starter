## Context

`form-field-definition-management` 目前用 `FormFieldControlType`（`backend/src/main/java/cn/nihility/rbac/formfield/constant/FormFieldControlType.java`，普通 `int` 常量类）定义三种控件类型：`TEXT=1`、`NUMBER=2`、`DICT=3`。`DICT` 类型要求关联字典类型（`dictTypeId`），动态渲染接口（`GET /api/form-fields/render-schema`）会为其内嵌 `dictOptions`（`label`/`value` 单值列表，来自 `DictItemService.getEnabledOptions()`）。前端 `types/formField.ts`、`FormFieldListView.vue`、`useDynamicFormFields.ts` 三处与后端常量一一对应。

本次要新增两种控件类型：
- **日期**：不依赖字典，纯粹是控件渲染形态的扩展。
- **多选字典下拉**：与现有 `DICT`（单选字典下拉）同样依赖 `dictTypeId`，区别仅在于前端渲染为可多选控件、动态表单默认值/校验为数组。

实际字段值存储在 `ORG`/`USER`/`POSITION`/`APP` 各自表的扩展列（`extN`，见 `V17__add_ext_fields_to_user_position_app.sql`），本次改动范围限定在字段定义（控件类型元数据）与动态渲染元数据层面，不涉及扩展列的存储格式变化——多选字典的选中值由业务对象自身的扩展列以字符串形式承载（如 JSON 数组的字符串序列化),该列本身是否为 JSON 类型不属于本次改动范围。

## Goals / Non-Goals

**Goals:**
- 扩展 `FormFieldControlType` 支持 `DATE`、`MULTI_DICT` 两种新取值，五种控件类型可在同一套字段定义 CRUD 接口下配置。
- `dict_type_id` 关联校验规则从"仅 `DICT` 需要"扩展为"`DICT`、`MULTI_DICT` 均需要"。
- 动态渲染元数据接口对 `MULTI_DICT` 同样内嵌 `dictOptions`（复用现有单值 `label`/`value` 选项列表结构，因为"可选项"本身仍是单值列表，"可多选"是前端交互属性而非选项数据结构属性）。
- 前端表单管理页面、动态表单渲染 composable 同步支持这两种新类型的交互与默认值/校验行为。

**Non-Goals:**
- 不新建字段值存储表或修改 `ORG`/`USER`/`POSITION`/`APP` 扩展列的数据类型。
- 不改变字典模块（`dict/` 包）本身的数据结构，不新增"多选字典项"这种字典侧概念——多选与否是表单字段定义侧的控件类型属性。
- 不新增权限资源码（控件类型是字段定义的属性值，不是独立功能点）。
- 不处理已有历史字段定义数据的回填/迁移（新增枚举值不影响存量数据）。

## Decisions

### 1. 新控件类型的常量值：`DATE=4`，`MULTI_DICT=5`
选择按"是否依赖字典"分组、而不是"字典类型相邻排列"（如 `DICT=3` 后紧跟 `MULTI_DICT=4`）：
- `DATE` 独立于字典体系，语义上更接近 `TEXT`/`NUMBER` 这类"无字典依赖"控件，赋值为紧邻 `NUMBER=2` 之后的 `4`，保持"无字典依赖类型"在数值上连续（`1,2,4`），便于未来再加同类简单控件类型时延续在低位。
- `MULTI_DICT` 与 `DICT=3` 语义强相关（都需要 `dictTypeId`），放在 `5`，通过代码里集中的 `DICT_TYPES = {DICT, MULTI_DICT}` 常量集合（而非数值相邻）表达这种关联，数值本身不承载分组语义，避免未来插入新类型时产生数值不连续的尴尬。
- 替代方案（`MULTI_DICT=4, DATE=5`，即按字典类型相邻排列）被放弃：控件类型的分组关系应该由代码里的显式集合表达，而不是依赖常量数值大小的隐式约定，后者容易在下次扩展时被无意打破。

### 2. 字典类关联校验扩展为集合判断
`FormFieldDefinitionServiceImpl.validateDictType()` 目前判断 `Objects.equals(controlType, FormFieldControlType.DICT)`。改为判断是否属于 `FormFieldControlType.DICT_TYPES`（新增的 `Set<Integer>` 静态常量，包含 `DICT`、`MULTI_DICT`），属于该集合则要求 `dictTypeId` 非空且指向存在的启用字典类型；不属于则要求 `dictTypeId` 为空（沿用现有"非字典类型不应带 dictTypeId"的隐含语义，若现状未强制清空则保持现状不额外加约束）。同理 `resolveDictOptions()` 改为按 `DICT_TYPES` 集合判断是否内嵌 `dictOptions`，而不是单独判断 `DICT`。

这样只需要维护一处集合定义，`DATE` 及未来任何新增的"非字典类"控件类型都不需要改动这两处方法的判断逻辑。

### 3. `dictOptions` 数据结构不变，多选语义由前端渲染决定
`FormFieldDictOptionVO`（`label`/`value` 单值字符串）和后端 `resolveDictOptions()` 的返回结构对 `DICT`、`MULTI_DICT` 完全一样——两者的"可选项列表"都是同一字典类型下的启用字典项集合，区别只在于前端渲染成 `el-select`（单选）还是 `el-select multiple`（多选），以及用户"已选值"是一个字符串还是字符串数组。因此不新增 VO 字段，`FormFieldRenderItemVO` 无需改动结构，前端根据 `controlType === MULTI_DICT` 决定 `el-select` 的 `multiple` 属性。

### 4. 前端默认值与校验
- `useDynamicFormFields.ts` 的 `buildFormModel()`：`DATE` 默认值为 `null`（Element Plus `el-date-picker` 的空值语义），`MULTI_DICT` 默认值为 `[]`（数组，对应多选控件的 v-model 类型）。
- `buildRules()`：`DATE` 必填时校验 `required + trigger: 'change'`，不额外做格式校验（`el-date-picker` 本身保证输出格式合法，沿用项目里"必填交给 rules，格式交给控件本身"的既有模式，参考 `NUMBER` 类型目前也未做额外格式规则）。`MULTI_DICT` 必填时校验数组非空（`type: 'array', required: true, min: 1`),提示文案沿用 DICT 的"请选择"。
- `dictOptionLabel()`：`DICT` 保持单值 `.find()` 查找；新增 `dictOptionLabels()`（或扩展为对数组入参做 `.map().join('、')`）供列表页展示 `MULTI_DICT` 字段的已选值文本。

### 5.1 日期选择控件的中文本地化（手动验证阶段发现）
项目此前未配置 Element Plus 的 `locale` 选项，`el-date-picker` 默认使用英文（月份/星期表头显示 `Jan`/`Su` 等）。修复方式：在 `frontend/src/main.ts` 引入 `element-plus/dist/locale/zh-cn.mjs` 并在 `app.use(ElementPlus, { locale: zhCn })` 时传入，全局生效，不需要逐个组件用 `ElConfigProvider` 包裹。这是全局配置缺失，不是"日期"控件类型本身的专属问题，但由本次新增日期控件类型触发发现。

### 5. 需要同步维护的硬编码分支清单
以下位置目前按 `TEXT/NUMBER/DICT` 三分支处理，本次改动都要新增 `DATE`/`MULTI_DICT` 分支（后端 `MULTI_DICT` 与 `DICT` 常合并处理，`DATE` 单独处理）：
- 后端 `FormFieldDefinitionServiceImpl.validateDictType()`、`resolveDictOptions()`、`controlTypeLabel()`
- 前端 `types/formField.ts` 的 `FORM_FIELD_CONTROL_TYPE_OPTIONS`
- 前端 `FormFieldListView.vue`：`controlTypeLabel()` 查表、字典类型选择器 `v-if`（条件从 `=== DICT` 改为 `DICT 或 MULTI_DICT`）、`dictTypeId` 校验规则触发条件、新增日期选择控件对应的表单项（若新增/编辑弹窗需要针对 `DATE` 展示额外配置项，本次不需要——`DATE` 无额外配置字段）
- 前端 `useDynamicFormFields.ts`：`buildRules()`、`buildFormModel()`、`dictOptionLabel()`

## Risks / Trade-offs

- [风险] 数据库层 `control_type` 列没有 CHECK 约束，新增取值属于纯应用层约定，未来若有人绕过 service 层直接写库可能插入非法值 → 缓解：沿用现状（现有 1/2/3 也无 DB 层约束),不在本次改动引入 CHECK 约束这类超出范围的改动;应用层 `validateDictType()`/枚举校验是唯一防线,与现状一致。
- [风险] 前端列表页展示多选字典值的已选文本，若某个字典项被停用/删除后，历史存量数据里的已选值可能找不到对应 `label` → 缓解：`dictOptionLabels()` 对找不到 `label` 的值 fallback 展示原始 `value`，与现有单选 `dictOptionLabel()` 的既有兜底逻辑保持一致（若现状本身没有兜底，则本次也不额外新增，保持行为一致性优先于修复历史问题）。
- [权衡] `MULTI_DICT` 复用 `DICT` 的 `dictOptions` 结构而不新增专属 VO 字段，代价是后端 VO 上无法直接区分"这条渲染元数据的 dictOptions 该按单选还是多选消费"——但这个信息已经由同一条记录的 `controlType` 字段携带，前端消费时天然可判断，不需要额外冗余字段。

### 6. 操作日志快照的字典值展示（手动验证阶段发现的既有缺陷，随本次一并修复）
手动验证操作历史时发现："多选字典下拉"字段在操作日志的变更快照里展示的是原始字典编码（逗号分隔），而不是人类可读的标签。追查后发现 `FormFieldSnapshotSupport.appendExtFieldSnapshot()`（`org`/`user`/`app` 三个模块的 `toLogSnapshot()` 共用的静态工具方法）本身从未做过字典编码→标签的解析——无论 `DICT` 还是 `MULTI_DICT`，都是把 `extValuesByColumnName` 里的原始存储值直接放进快照；`DICT` 类型"看起来正常"很可能只是测试数据恰好编码等于标签的巧合，而不是真的有解析逻辑。这是一个先于本次改动就存在的缺陷，但由于新增 `MULTI_DICT` 后编码/标签不一致更容易被发现，随本次一并修复，覆盖 `DICT` 与 `MULTI_DICT` 两种类型（复用同一段 `DICT_TYPES` 判断逻辑，不单独为 `MULTI_DICT` 开洞）。

修复方式：
- `FormFieldSnapshotSupport` 从纯静态工具类改为 Spring `@Component`（`@RequiredArgsConstructor` 注入 `DictItemService` 与解析字典类型所需的 `DictTypeMapper`），提供实例方法 `appendExtFieldSnapshot(...)`（签名不变，仅从 `static` 改为实例方法），内部对每个 ext 字段定义按 `definition.getControlType()` 判断：
  - 不在 `FormFieldControlType.DICT_TYPES` 或 `dictTypeId` 为空：原样使用存储值（沿用现状，`TEXT`/`NUMBER`/`DATE` 均不受影响）。
  - `DICT`：按当前存储的单个编码查一次字典项标签，查不到时回退展示原始编码（与前端 `dictOptionLabel()` 的兜底逻辑保持一致）。
  - `MULTI_DICT`：按逗号切分存储值为多个编码，逐个解析标签，找不到的编码单独回退展示原编码，用"、"重新拼接（与前端 `dictOptionLabels()` 的展示风格保持一致，是同一套"编码→标签"语义在后端日志侧的对应实现）。
- `org`/`user`/`app` 三个 `*ServiceImpl` 从"直接调用 `FormFieldSnapshotSupport` 的静态方法"改为注入这个 Bean 后调用实例方法。
- `PositionServiceImpl` 目前完全没有接入 `toLogSnapshot`/ext 字段操作日志（既有缺陷，不在本次改动范围内，不在这次修复），仅记录在案供后续单独的 change 处理。

## Migration Plan

1. 新增 Flyway 迁移 `V25__update_tab_form_field_definition_control_type_comment.sql`，仅更新 `control_type` 列注释说明新增的两个取值（`4=日期`、`5=多选字典下拉`），不改变列类型、不做数据回填（不是 ALTER 结构性变更,是文档性 COMMENT 更新)。
2. 后端常量、service 层判断逻辑随代码发布一起上线，向后兼容——存量的 `TEXT/NUMBER/DICT` 定义行为不变。
3. 前端随后端接口发布同步部署，新增控件类型选项在"新增字段定义"弹窗中可选;存量页面不受影响。
4. 无需数据回滚计划——本次改动不修改任何存量数据，仅新增可选枚举值，回滚只需回退代码与迁移即可,不影响已保存的字段定义记录。

## Open Questions（已解决）

- `MULTI_DICT` 类型在 `ORG`/`USER`/`POSITION`/`APP` 扩展列中的实际存储格式：手动验证阶段发现，扩展列（`extN`）在各业务对象的 Create/Update DTO 上是 `String` 类型，前端若提交 JSON 数组会导致 `HttpMessageNotReadableException`（`Cannot deserialize value of type java.lang.String from Array value`）。确定采用**逗号 `,` 分隔的字符串**承载多选值（不采用 JSON 数组字符串），与扩展列的 `String` 类型直接兼容，无需后端改动。前端职责：
  - 提交时：`el-select multiple` 的数组值 SHALL 在拼入请求体前 `join(',')` 为字符串。
  - 回填/展示时：从后端读到的逗号分隔字符串 SHALL 在填入 `el-select multiple` 的 `v-model`（`buildFormModel`）或计算展示文本（`dictOptionLabels`）前 `split(',')` 还原为数组；空字符串/`null`/`undefined` 还原为空数组。
  - 涉及位置：`useDynamicFormFields.ts` 的 `buildFormModel()`（回填分支需按逗号切分）与 `dictOptionLabels()`（入参需兼容字符串，内部先切分）；四个业务页面的 `submitForm()`（提交前需把 `MULTI_DICT` 字段数组 join 成字符串）；`UserManagementView.vue` 内嵌的"任职信息"子表单在编辑回填（`openEditDialog` 里手写的 `ext1`~`ext10` 映射）与提交（`form.positions.map(...)`）两处也需要同样处理，且应改用 `positionFields.buildFormModel()` 复用逻辑而非手写映射，避免两处实现出现不一致。
