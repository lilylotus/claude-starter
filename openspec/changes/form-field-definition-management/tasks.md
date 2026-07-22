## 1. 数据库迁移

- [x] 1.1 `V17__add_ext_fields_to_user_position_app.sql`：为 `tab_user`、`tab_user_position`、`tab_app` 各补齐 `ext1`~`ext10`（`VARCHAR(255)`），与 `tab_org` 保持一致
- [x] 1.2 `V18__init_tab_metadata_field.sql`：建 `tab_metadata_field` 表（字段见 design.md 第 2 节），含 `create_by`/`create_time`/`update_by`/`update_time`
- [x] 1.3 `V19__seed_metadata_field_catalog.sql`：按 design.md 第 3 节的清单，为组织/用户/任职/应用写入可开放配置的原有列 + 全部 `ext1`~`ext10` 的元数据字段记录
- [x] 1.4 `V20__init_tab_form_field_definition.sql`：建 `tab_form_field_definition` 表（字段见 design.md 第 5 节，含 `metadata_field_id` 外键列），含审计字段
- [x] 1.5 `V21__seed_form_field_core_definitions.sql`：按 design.md 第 11 节，为组织（`name`/`code`/`showOrder`/`remark`）、用户（`name`/`code`/`mobile`/`idCard`/`showOrder`/`remark`）、任职（`positionAddress`/`positionPhone`/`showOrder`/`remark`）、应用（`name`/`code`/`showOrder`/`remark`）写入默认启用的字段定义，绑定对应元数据字段
- [x] 1.6 `V22__seed_metadata_field_menu_resource_data.sql`：在 `tab_menu` 里插入"系统管理 → 元数据配置"菜单及按钮资源
- [x] 1.7 `V23__seed_form_field_menu_resource_data.sql`：在 `tab_menu` 里插入"系统管理 → 表单管理"菜单及按钮资源

## 2. 后端：元数据字段配置模块（`cn.nihility.rbac.metadata`）

- [x] 2.1 `constant/FormFieldBizType`（`ORG`/`USER`/`POSITION`/`APP`，供元数据与表单字段定义模块共用）、`MetadataFieldStatus`（`2000`/`3000`/`-1000`）——`FormFieldBizType` 按 design.md Decision 1 落在 `cn.nihility.rbac.formfield.constant` 包下，`MetadataFieldStatus` 落在 `cn.nihility.rbac.metadata.constant` 包下
- [x] 2.2 `entity/MetadataFieldEntity`：对应 `tab_metadata_field`，字段命名过数据库关键字检查
- [x] 2.3 `mapper/MetadataFieldMapper`：`BaseMapper`
- [x] 2.4 `dto`：`MetadataFieldUpdateRequest`（只接受 `fieldName` 改动，`status` 由独立的启用/停用接口维护，与项目内其他主数据模块的既有约定一致）、`MetadataFieldVO`
- [x] 2.5 `mapstruct/MetadataFieldConvert`：静态单例转换，不用 `componentModel = "spring"`
- [x] 2.6 `exception`：`MetadataFieldInUseException`（停用被占用字段）；尝试修改物理属性无需异常——`MetadataFieldUpdateRequest` 结构上只暴露 `fieldName`，物理属性天然不可通过接口改动
- [x] 2.7 `service/MetadataFieldService` + `impl`：分页查询（按 `bizType`）、详情、更新（拒绝改动 `tableName`/`columnName`/`columnType`/`bizType`）、启用/停用（停用前检查是否被有效表单字段定义占用，直接注入 `formfield` 模块的 `FormFieldDefinitionMapper` 判断，沿用本项目"跨模块只读查询直接复用对方 Mapper"的既有约定）、`listAvailable(bizType)`（启用且未被占用，供表单管理选择）
- [x] 2.8 `controller/MetadataFieldController`：`GET /api/metadata-fields`（分页）、`GET /api/metadata-fields/{id}`、`PUT /api/metadata-fields/{id}`、`PUT /api/metadata-fields/{id}/enable`、`PUT /api/metadata-fields/{id}/disable`、`GET /api/metadata-fields/available`；加 springdoc `@Tag`/`@Operation` 注解（无新增/删除接口）

## 3. 后端：表单字段定义模块（`cn.nihility.rbac.formfield`）

- [x] 3.1 `constant/FormFieldControlType`（`1`=文本框/`2`=数字框/`3`=字典下拉）、`FormFieldStatus`（`2000`/`3000`/`-1000`）、`LockedFormFields`（`(bizType, columnName)` 承重字段白名单常量：`(ORG,name)`、`(ORG,code)`、`(USER,name)`、`(USER,code)`、`(APP,name)`、`(APP,code)`）
- [x] 3.2 `entity/FormFieldDefinitionEntity`：对应 `tab_form_field_definition`，含 `metadataFieldId`
- [x] 3.3 `mapper/FormFieldDefinitionMapper`：`BaseMapper`，加一个按 `metadataFieldId` 查是否存在有效绑定定义的方法（`existsActiveByMetadataFieldId`，接口内 `default` 方法，基于 `BaseMapper.selectCount` 实现，无需 XML）
- [x] 3.4 `dto`：`FormFieldDefinitionCreateRequest`（含 `metadataFieldId`，服务层校验必须是可用元数据字段）、`FormFieldDefinitionUpdateRequest`（不含 `bizType`/`metadataFieldId` 字段，从结构上保证绑定关系不可修改；服务层对绑定承重字段的记录拒绝 `isRequired`/`showInCreate`/`showInEdit`=false 与停用/删除请求）、`FormFieldDefinitionVO`、`FormFieldRenderItemVO`（渲染元数据用，含计算得出的 `locked`、内嵌 `dictOptions`）、`FormFieldDictOptionVO`（字典下拉可选项 `{label, value}`）
- [x] 3.5 `mapstruct/FormFieldDefinitionConvert`：静态单例转换
- [x] 3.6 `exception`：`MetadataFieldUnavailableException`、`MetadataFieldAlreadyBoundException`、`FieldCodeDuplicateException`、`DictTypeRequiredException`、`LockedFormFieldException`；尝试修改绑定关系无需异常——`FormFieldDefinitionUpdateRequest` 结构上不含 `metadataFieldId`/`bizType`
- [x] 3.7 `service/FormFieldDefinitionService` + `impl`：分页查询、详情、创建（校验 `metadataFieldId` 可用性、`fieldCode` 唯一校验、字典下拉必须关联 `dictTypeId` 校验）、更新（绑定关系不可变，承重字段定义的受保护属性拒绝修改）、启用/停用（承重字段定义拒绝停用）、逻辑删除（承重字段定义拒绝删除，其余释放元数据字段占用）、`listActiveByBizType(bizType)`（供其他模块读取校验元数据，过滤出非承重定义）、`buildRenderSchema(bizType)`（含字典选项拼装，调用 `DictItemService`；含 `locked` 计算）
- [x] 3.8 `controller/FormFieldDefinitionController`：`GET /api/form-fields`（分页）、`GET /api/form-fields/{id}`、`POST /api/form-fields`、`PUT /api/form-fields/{id}`、`PUT /api/form-fields/{id}/enable`、`PUT /api/form-fields/{id}/disable`、`DELETE /api/form-fields/{id}`、`GET /api/form-fields/render-schema`；加 springdoc `@Tag`/`@Operation` 注解

## 4. 后端：组织/用户/任职/应用模块接入字段定义驱动的校验

- [x] 4.1 `OrgEntity`/`OrgCreateRequest`/`OrgUpdateRequest`/`OrgVO` 新增 `ext1`..`ext10` 可选 `String` 字段；`OrgConvert` 自动映射
- [x] 4.2 `UserEntity`/`UserCreateRequest`/`UserUpdateRequest`/`UserVO` 新增 `ext1`..`ext10`；`UserConvert` 自动映射（用户管理内嵌的任职子表单 `UserPositionRequest`/`UserPositionVO` 不纳入本次 `ext` 扩展范围，保持任务描述的最小改动边界）
- [x] 4.3 `UserPositionEntity`/`PositionCreateRequest`/`PositionUpdateRequest`/`PositionVO` 新增 `ext1`..`ext10`；`PositionConvert` 自动映射；`UserPositionMapper.xml` 的 `selectPositionPage`/`selectPositionDetail` 列表同步补充 `ext1`..`ext10`，否则任职管理入口查询不到这些扩展字段的值
- [x] 4.4 `AppEntity`/`AppCreateRequest`/`AppUpdateRequest`/`AppVO` 新增 `ext1`..`ext10`；`AppConvert` 自动映射
- [x] 4.5 在 `resources/mybatis/mapper/` 下新增 `OrgMapper.xml`/`UserMapper.xml`/`AppMapper.xml`，并在既有的 `UserPositionMapper.xml` 里追加，各加一个 `countByColumnValue(column, value, excludeId)` 方法；`column` 除了服务层从 `tab_metadata_field` 解析得到的值以外，还额外经过各 ServiceImpl 内维护的静态列名白名单（`ALLOWED_DYNAMIC_COLUMNS`）二次校验，双重防护 SQL 注入
- [x] 4.6 `OrgServiceImpl`/`UserServiceImpl`/`PositionServiceImpl`/`AppServiceImpl` 的 create/update 方法接入：调用 `FormFieldDefinitionService.listActiveByBizType(bizType)`（已过滤承重字段定义），通过新增的 `cn.nihility.rbac.formfield.support.DynamicFieldValidator` 共用校验逻辑，按场景执行必填/正则/唯一性校验（design.md 第 9 节），保持既有对 `name`/`code` 的硬编码校验不变、不额外经过这条管线；`UserServiceImpl` 原有的身份证号（`idCard`）硬编码唯一性校验（`checkIdCardUnique`）已移除，改由默认表单字段定义（`isUnique=true`）驱动这条数据驱动管线，与 design.md Decision 4 "非锁定字段的校验完全交给数据驱动管线"的定位一致

## 5. 前端：API 与类型

- [x] 5.1 `src/types/metadataField.ts`：`FormFieldBizType`、`MetadataField`——额外补充了 `FORM_FIELD_BIZ_TYPE_OPTIONS`（四个业务对象类型的下拉/tab 选项数据源）与 `METADATA_FIELD_STATUS_ENABLED`/`_DISABLED` 状态常量，风格对齐其他模块
- [x] 5.2 `src/api/metadataField.ts`：分页查询、详情、更新、启用/停用、`fetchAvailableMetadataFields(bizType)`（任务描述里的 `fetchAvailable` 按项目现有 `api/*.ts` 命名习惯加上了模块前缀）
- [x] 5.3 `src/types/formField.ts`：`FormFieldControlType`（`FORM_FIELD_CONTROL_TYPE_TEXT`/`_NUMBER`/`_DICT` 三个常量 + `FORM_FIELD_CONTROL_TYPE_OPTIONS`）、`FormFieldDefinition`、`FormFieldRenderItem`（含 `locked`/`dictOptions`）、`FormFieldDefinitionCreateRequest`/`UpdateRequest`
- [x] 5.4 `src/api/formField.ts`：分页查询、详情、新增、更新、启用/停用、删除、`fetchFormFieldRenderSchema(bizType)`
- [x] 5.5 组织/用户/任职/应用四个模块现有的 `src/types/*.ts` 补充 `ext1`..`ext10` 可选字符串字段（`OrgRow`/`OrgFormRequest`、`UserRow`/`UserFormRequest`、`PositionRow`/`PositionFormRequest`，`PositionCreateRequest` 通过 extends 自动带上、`AppRow`/`AppFormRequest`）；`src/api/*.ts` 无需改动，原有函数签名已经是"透传整个 FormRequest/Row"，新增字段自动随之透传

## 6. 前端：元数据配置页面

- [x] 6.1 `src/views/system/metadatafields/MetadataFieldListView.vue`：用 `el-tabs` 切换组织/人员/任职/应用四个业务对象类型的元数据字段列表 + 编辑弹窗（仅 `fieldName` 一个输入框，`status` 只能通过行内启用/停用按钮调整，不在编辑弹窗里）+ 详情弹窗（`el-descriptions` 展示 `tableName`/`columnName`/`columnType`/`fieldName`/`status`/审计字段）；无新增/删除入口
- [x] 6.2 `frontend/src/router/menu.ts` 新增"元数据配置"子菜单项（`path: '/system/metadata-fields'`，`permissionKey: 'system:metadataField:view'`）；`router/index.ts` 的 `implementedComponents` 注册对应组件

## 7. 前端：表单管理页面

- [x] 7.1 `src/views/system/formfields/FormFieldListView.vue`：用 `el-tabs` 切换四个业务对象类型的字段定义列表 + 新增/编辑弹窗
- [x] 7.2 新增弹窗：元数据字段选择器（调用 `fetchAvailableMetadataFields(bizType)`；编辑弹窗按规格不展示该选择器，绑定关系不可改，故编辑时无需额外把当前定义自身绑定的元数据字段并入可选列表）、控件类型选择、字典下拉时展示字典类型选择器（复用 `dictApi.getDictTypePage({ pageSize: 200 })` 简单实现"查询全部字典类型"，未新增后端接口）、唯一/必填/列表展示/新增展示/编辑展示/可编辑开关、正则输入框、`placeholder` 输入框、显示序号
- [x] 7.3 列表操作列：`locked=true` 的行不展示"停用"与"删除"按钮（仅保留"编辑"）；编辑弹窗中"是否必填"/"是否新增表单展示"/"是否编辑表单展示"对 `locked=true` 的定义渲染为禁用态（`el-switch :disabled`）并附一行说明文字

## 8. 前端：组织/用户/任职/应用页面统一动态渲染接入

- [x] 8.1 `src/composables/useDynamicFormFields.ts`：封装拉取 `render-schema`、生成 `el-table` 动态列配置（`listColumns`）、生成新增/编辑表单项配置（`createFields`/`editFields`）与对应 `el-form` `rules`（`createRules`/`editRules`），另外提供 `buildFormModel`（按 `columnName` 构建/回填动态表单模型）与 `dictOptionLabel`（字典下拉值→标签）两个辅助函数；返回值整体包了一层 `reactive()`，使调用方在 `<script setup>` 与模板里都能像访问 Pinia store 一样直接点号访问、无需 `.value`，和项目里已有 store 的使用习惯保持一致
- [x] 8.2 组织管理列表/新增/编辑页面改造：`parentId`/`status` 保留硬编码，其余字段（`name`/`code`/`showOrder`/`remark`/`ext1`~`ext10`）改为通过 `useDynamicFormFields('ORG')` 渲染；原来硬编码的 `name`/`code`/`showOrder` 表单项与列已删除，改由动态渲染接管
- [x] 8.3 用户管理列表/新增/编辑页面改造：`gender`/`status` 保留硬编码，其余字段改为通过 `useDynamicFormFields('USER')` 渲染；原先硬编码的手机号/身份证号格式校验（`validateMobile`/`validateIdCard`）与身份证号唯一性提示一并移除——默认字段定义未预置 `validateRegex`，格式校验现在完全交由"表单管理"按需配置（与后端同步移除硬编码 `checkIdCardUnique`、改由数据驱动唯一性管线保证一致）；任职子表单（`UserPositionFormItem`）未改动，符合任务范围
- [x] 8.4 任职管理列表/新增/编辑页面改造：`orgId`/`userId`/`positionType`/`status` 保留硬编码，其余字段改为通过 `useDynamicFormFields('POSITION')` 渲染；`userName`/`orgName` 是关联对象的展示派生列（非元数据目录字段），继续硬编码渲染
- [x] 8.5 应用管理列表/新增/编辑页面改造：`ownerId`/`orgId`/`status` 保留硬编码，其余字段改为通过 `useDynamicFormFields('APP')` 渲染；`ownerName`/`orgName` 同样是关联展示派生列，继续硬编码渲染

## 9. 收尾

- [x] 9.1 更新仓库根目录 `权限资源.txt`，补充"元数据配置""表单管理"两个模块的菜单/按钮资源编码（`MetadataFieldManagement`/`FormFieldManagement`，编码与 V22/V23 迁移种子数据一致）
- [x] 9.2 后端 `./gradlew test` 通过；前端 `npm run build`（`vue-tsc` 类型检查 + vite build）通过
- [x] 9.3 手动验证（通过真实后端 API 调用，非浏览器点击）：(a) 组织/用户/应用的 `name`/`code` 对应的元数据字段无法被停用（`MetadataFieldInUseException`），对应字段定义无法被删除/停用/关闭必填与展示——已验证；(b) 人员/任职/应用元数据目录里各自确认有 10 条 `ext1`~`ext10` 记录——已验证；(c) 为 `bizType=USER` 新增一条绑定 `ext6`、`fieldCode=idCardNo2` 的定义（必填+正则+唯一），通过 `POST /api/users` 验证必填/正则/唯一性校验全部生效，成功创建后 `render-schema`/`ext6` 均正确回显——已验证（验证过程中发现并修复了下面这个 bug）；(d) 元数据字段的展示名称可编辑且 `render-schema` 同步生效——已通过 render-schema 返回内容验证。
  **验证中发现的 bug（已修复）**：`cn.nihility.rbac.formfield.support.DynamicFieldValidator.resolveValue` 原先按 `fieldCode`（管理员自定义的展示别名，如 `idCardNo`）反射读取请求 DTO 属性值，但请求 DTO 上实际存在的属性名是绑定的元数据字段列名转驼峰后的结果（`ext6`、`idCard`、`showOrder` 等），与 `fieldCode` 无关；这导致所有自定义 `fieldCode` 与物理列名不同的 `EXT` 字段（即本次改动最核心的使用场景——"身份证号 idCardNo 对应 ext6"）必填校验必定判定为空、正则/唯一性校验永远不触发。已改为对 `columnName` 做下划线转驼峰后再反射取值；前端 `useDynamicFormFields.ts` 此前也被错误指示按原始 `columnName`（下划线形式，如 `id_card`）直接当 key 使用，同样已改为統一在拉取 `render-schema` 后转驼峰。两处修复后已重新跑通 `./gradlew test`（139 测试全部通过）与 `npm run build`，并重启本地 `bootRun` 实例用 `curl` 逐一重放上述 (a)~(c) 场景确认修复生效。
- [x] 9.4 实现完成后，按 `openspec-doc-sync` 约定核对 `proposal.md`/`design.md`/`tasks.md` 与实际实现是否一致，如有偏差据实更新
