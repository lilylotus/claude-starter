## 1. 数据库迁移

- [x] 1.1 新增 `V24__add_field_code_to_tab_metadata_field.sql`：`ALTER TABLE tab_metadata_field ADD COLUMN field_code` 列
- [x] 1.2 同一脚本内按 `column_name` 驼峰转换规则回填全部存量行的 `field_code`
- [x] 1.3 同一脚本内添加 `UNIQUE KEY (biz_type, field_code)` 唯一约束
- [x] 1.4 手工核对回填结果与 `tab_form_field_definition` 现有 `field_code` 是否一致（抽查 ORG/USER/POSITION/APP 各一条已绑定记录）

## 2. 后端 - 元数据字段模块

- [x] 2.1 `MetadataFieldEntity` 新增 `fieldCode` 属性
- [x] 2.2 `MetadataFieldVO` 新增 `fieldCode` 属性并补充 `@Schema` 注解
- [x] 2.3 `MetadataFieldConvert`（entity ↔ VO）同步映射新增字段（`MetadataFieldUpdateRequest` 不新增该字段，保持字段标识不可编辑）
- [x] 2.4 `MetadataFieldService.listAvailable` 新增重载 `listAvailable(String bizType, Long excludeDefinitionId)`：在原有"启用且未被占用"过滤基础上，若 `excludeDefinitionId` 对应一条存在且未删除的表单字段定义，把其当前绑定的元数据字段（若启用）一并纳入结果
- [x] 2.5 `MetadataFieldController#available` 新增可选查询参数 `excludeDefinitionId`，转发给 service 新重载；不传时行为不变
- [x] 2.6 `MetadataFieldServiceImpl.toLogSnapshot` 按需补充 `fieldCode` 到操作日志快照

## 3. 后端 - 表单字段定义模块

- [x] 3.1 `FormFieldDefinitionUpdateRequest` 新增可选字段 `metadataFieldId`（无 `@NotNull`，允许省略/等于当前值表示不改绑）
- [x] 3.2 `FormFieldDefinitionServiceImpl.update()` 新增改绑逻辑：计算 `locked`；`locked=true` 且请求 `metadataFieldId` 变化 → 抛 `LockedFormFieldException`；`locked=false` 且变化 → 校验新元数据字段存在、启用、`bizType` 与当前定义一致、未被其他有效定义占用（复用/参考 `existsActiveByMetadataFieldId`，排除自身），校验通过后一并更新 `metadataFieldId`（进而联动 `bizType` 保持不变、`columnName`/`locked` 等派生值在下次查询时按新绑定重新计算）
- [x] 3.3 `FormFieldDefinitionServiceImpl.toLogSnapshot`/更新前后快照对比中体现"绑定字段"变化，便于操作日志追溯改绑记录
- [x] 3.4 补充/更新 Javadoc 注释，去掉"绑定关系一经创建不可修改"等已过时的类注释表述（`FormFieldDefinitionEntity`、`FormFieldDefinitionUpdateRequest` 等）

## 4. 前端 - 类型与 API 封装

- [x] 4.1 `types/metadataField.ts` 的 `MetadataField` 接口新增 `fieldCode: string`
- [x] 4.2 `api/metadataField.ts` 的 `fetchAvailableMetadataFields` 支持传入可选的 `excludeDefinitionId` 参数并拼接到查询串
- [x] 4.3 `types/formField.ts`（如有独立的更新请求类型）同步新增可选 `metadataFieldId`

## 5. 前端 - 元数据配置页面

- [x] 5.1 `MetadataFieldListView.vue` 列表新增"字段标识"列（`fieldCode`，只读展示）
- [x] 5.2 详情弹窗 `el-descriptions` 新增"字段标识"展示项
- [x] 5.3 编辑弹窗保持不可编辑字段标识（不新增输入框，维持现状）

## 6. 前端 - 表单管理页面

- [x] 6.1 `openEditDialog` 调用 `metadataFieldApi.fetchAvailableMetadataFields(activeBizType.value, row.id)` 获取"当前绑定 + 其余可选"的元数据字段列表，赋值给 `availableMetadataFields`，供编辑弹窗下拉框使用
- [x] 6.2 弹窗模板：把"绑定元数据字段"表单项的 `v-if="dialogMode === 'create'"` 改为始终展示（`create`/`edit` 都渲染），编辑态且 `editingLocked` 为真时下拉框设置 `disabled`
- [x] 6.3 表单项 label 文案从"绑定元数据字段"改为"数据字段"
- [x] 6.4 新增"自动回填字段标识"联动逻辑：监听 `form.metadataFieldId` 变化，维护"上一次自动带出值"内部变量，按设计文档第 4 条策略决定是否覆盖 `form.fieldCode`
- [x] 6.5 `submitForm` 的 `payload` 中补充 `metadataFieldId`（`update` 分支此前未携带该字段，现需要一并提交）
- [x] 6.6 `<el-dialog>` 增加 `top="5vh"` 属性，验证新增/编辑弹窗均整体上移

## 7. 权限资源编码同步

- [x] 7.1 检查本次改动是否新增/删除了页面菜单或按钮（本次为已有页面内的表单项/交互调整，预期不涉及），如无变化则跳过 `权限资源.txt` 更新；如引入新按钮再同步更新

## 8. 验证

- [x] 8.1 `./gradlew test`（backend/ 目录下）跑通既有 + 新增的单元/集成测试
- [x] 8.2 `npm run build`（frontend/ 目录下）跑通 vue-tsc 类型检查
- [x] 8.3 手工验证：新增字段定义选择数据字段后字段标识（禁用态展示）自动带出；切换数据字段后字段标识随之更新（用户始终无法手动编辑该输入框）
- [x] 8.4 手工验证：编辑非锁定定义可改绑到其他未占用的启用元数据字段；编辑锁定定义（name/code）时数据字段选择器与字段标识展示均禁用
- [x] 8.5 手工验证：新增/编辑弹窗展示在页面靠上位置，字段较多时保存按钮无需大幅滚动即可看到

## 9. 调整：字段标识归属反转（元数据字段可编辑字段标识 / 表单字段定义字段标识改为完全派生）

> 本组任务由用户在完成 1-8 组之后追加的两点调整驱动：(1) 表单管理中字段标识改为不可编辑，由所绑定的元数据字段自动带入；(2) 元数据管理需要支持配置（编辑）字段标识。相应地废弃/取代了 2.3 中"`MetadataFieldUpdateRequest` 不新增该字段"的决定，以及 6.4 的"影子记忆"自动回填方案。proposal.md/design.md/两个 delta spec 已同步更新，此处补充落地任务。

### 9.1 后端 - 元数据字段模块（反转为可编辑）

- [x] 9.1.1 `MetadataFieldUpdateRequest` 新增 `fieldCode` 字段（`@NotBlank`、`@Size(max = 64)`、`@Schema`）
- [x] 9.1.2 `MetadataFieldConvert.updateEntity` 恢复映射 `fieldCode`（撤销此前"显式忽略"的处理，改为随 `fieldName` 一起正常写入）
- [x] 9.1.3 `MetadataFieldServiceImpl.update()` 新增 `fieldCode` 同 `bizType` 下唯一性校验（仿照 `FormFieldDefinitionServiceImpl` 原 `checkFieldCodeUnique` 的写法：请求值与当前值不同才校验，排除自身），冲突时抛业务异常（可复用/新增合适的异常类型）
- [x] 9.1.4 `MetadataFieldController` 更新接口的 `@Operation`/字段级 `@Parameter` 描述同步说明 `fieldCode` 现在可编辑

### 9.2 后端 - 表单字段定义模块（反转为完全派生）

- [x] 9.2.1 `FormFieldDefinitionCreateRequest` 移除 `fieldCode` 字段（不再由客户端提交）
- [x] 9.2.2 `FormFieldDefinitionUpdateRequest` 移除 `fieldCode` 字段
- [x] 9.2.3 `FormFieldDefinitionServiceImpl.create()`：不再从请求读取 `fieldCode`，改为 `entity.setFieldCode(metadata.getFieldCode())`；移除 `checkFieldCodeUnique(...)` 调用
- [x] 9.2.4 `FormFieldDefinitionServiceImpl.update()`：移除原先"请求 `fieldCode` 与当前值不同则校验唯一性"的分支；改绑（非锁定且 `metadataFieldId` 变化）校验通过后，除写入新 `metadataFieldId` 外，一并 `entity.setFieldCode(newMetadata.getFieldCode())`
- [x] 9.2.5 检查 `checkFieldCodeUnique` 方法与 `FieldCodeDuplicateException` 是否还有其他调用方/测试引用；若已无引用则一并删除，避免死代码
- [x] 9.2.6 `FormFieldDefinitionServiceImpl.enrich()`：在现有回填 `columnName`/`locked`/`dictTypeName` 的循环里，追加 `vo.setFieldCode(metadata.getFieldCode())`，用绑定的元数据字段当前的 `fieldCode` 覆盖 VO 的展示值（`metadata` 为 null 的极端情况维持实体上存储的旧值，不做特殊报错）
- [x] 9.2.7 更新相关 Javadoc（`FormFieldDefinitionCreateRequest`/`UpdateRequest`/`FormFieldDefinitionEntity` 中关于 `fieldCode` 的注释，说明其为派生字段）
- [x] 9.2.8 更新/补充单元测试：创建时字段标识取自元数据字段；改绑后字段标识同步刷新；元数据字段标识被单独编辑后，再次查询表单字段定义时看到的是最新值

### 9.3 前端 - 元数据配置页面（新增可编辑字段标识）

- [x] 9.3.1 `MetadataFieldListView.vue` 编辑弹窗新增"字段标识"输入框，绑定 `form.fieldCode`，校验规则 `required`
- [x] 9.3.2 `openEditDialog` 回填 `form.fieldCode = row.fieldCode`
- [x] 9.3.3 `submitForm` 提交的请求体中包含 `fieldCode`
- [x] 9.3.4 `types/metadataField.ts` 的 `MetadataFieldUpdateRequest` 类型新增 `fieldCode: string`

### 9.4 前端 - 表单管理页面（字段标识改为禁用展示）

- [x] 9.4.1 移除 6.4 引入的"影子记忆"自动回填逻辑（`lastAutoFieldCode` 及相关 `watch` 判断分支）
- [x] 9.4.2 "字段标识"对应的 `<el-input v-model="form.fieldCode">` 改为始终 `disabled`（新增、编辑均禁用），移除其 `required` 校验规则（不再是用户可编辑的必填项）
- [x] 9.4.3 新增/简化 `watch(() => form.metadataFieldId, ...)`：变化时直接从 `availableMetadataFields` 中查到对应项的 `fieldCode` 赋值给 `form.fieldCode`（无需再判断"是否手动改过"）
- [x] 9.4.4 `submitForm` 的 `payload` 不再携带 `fieldCode`（创建、更新分支均移除）

### 9.5 验证

- [x] 9.5.1 `./gradlew test`（backend/ 目录下）跑通
- [x] 9.5.2 `npm run build`（frontend/ 目录下）跑通
- [x] 9.5.3 接口验证：元数据字段更新接口可修改字段标识，重复值被拒绝返回业务错误（`curl` 验证通过；页面级点击验证见下）
- [x] 9.5.4 手工验证：表单管理新增/编辑弹窗中字段标识输入框始终禁用，且随所选数据字段实时更新
- [x] 9.5.5 接口验证：编辑一个已绑定的元数据字段的字段标识后，`GET /api/form-fields/{id}` 返回的字段标识立即变为最新值，无需改绑（`curl` 验证通过；页面级点击验证见下）
