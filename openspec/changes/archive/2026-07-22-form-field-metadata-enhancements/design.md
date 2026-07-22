## Context

`metadata-field-management`（元数据字段目录）与 `form-field-definition-management`（表单字段定义）已上线并归档（`openspec/specs/metadata-field-management`、`openspec/specs/form-field-definition-management`）。当前行为：

- `tab_metadata_field` 只有 `fieldName` 可编辑，`tableName`/`columnName`/`columnType`/`bizType` 迁移写入后不可变，没有独立的"字段标识"概念——表单字段定义自己的 `fieldCode`（如 `idCard`、`showOrder`）需要管理员在绑定元数据字段时手动敲。
- `FormFieldDefinitionServiceImpl.create()` 校验元数据字段"启用 + 未被其他有效定义绑定"，写入后 `metadataFieldId` 不可再变；`update()` 的请求体 `FormFieldDefinitionUpdateRequest` 里根本没有 `metadataFieldId` 字段。
- 前端 `FormFieldListView.vue` 里"绑定元数据字段"下拉框只在 `dialogMode === 'create'` 时渲染；弹窗 `<el-dialog>` 没有指定 `top`，Element Plus 默认垂直居中。
- 是否"锁定"（承重字段）由 `LockedFormFields.isLocked(bizType, columnName)` 在读取/更新时反查计算，不落库；当前只约束"必填/新增展示/编辑展示不可关闭 + 不可停用/删除"，没有涉及"是否可改绑"，因为改绑此前完全不存在。

## Goals / Non-Goals

**Goals:**
- 元数据字段增加"字段标识"（`fieldCode`），迁移预置初始值并为已有数据自动回填，此后可通过元数据字段的编辑接口/编辑弹窗调整（与 `fieldName` 同等对待，同一 `bizType` 下唯一）。
- 表单字段定义的"字段标识"改为完全派生：不再是管理员可独立填写的属性，创建/改绑时取自所绑定元数据字段的 `fieldCode`，读取时始终反映该元数据字段的当前 `fieldCode`；新增/编辑弹窗中对应输入框禁用，仅展示当前值。
- 非锁定的表单字段定义支持在编辑时重新绑定元数据字段；锁定定义（name/code）继续禁止改绑。
- 弹窗文案简化、弹窗定位上移，纯前端体验调整。

**Non-Goals:**
- 不允许通过接口新增/删除元数据字段目录项（沿用现状，只能迁移预置）。
- 不允许锁定定义改绑，也不允许改绑跨越 `bizType`。
- 不引入元数据字段本身的启停用/删除接口变化。
- 不改变操作日志记录的既有格式约定，只补充新增字段的日志字段。

## Decisions

### 1. `field_code` 归属元数据字段本身，而不是"表单字段定义"独有；且可编辑（与 `columnName` 等真正结构性字段区别对待）

**决策**：在 `tab_metadata_field` 新增 `field_code VARCHAR(64) NOT NULL`，迁移预置初始值；**与 `tableName`/`columnName`/`columnType` 不同，`fieldCode` 通过元数据字段的更新接口可编辑**（和 `fieldName` 同等对待），同一 `bizType` 下唯一（更新时做应用层唯一性校验，DB 唯一约束兜底）。

**理由**：字段标识本质上是"这个数据库列在 API/DTO 层面的规范名字"，是元数据本身的属性，不依赖是否存在表单字段定义，放在元数据字段上可以让"自动回填"有唯一权威来源。但和 `columnName`/`columnType` 这类描述"数据库物理结构"的字段不同，`fieldCode` 是一个命名约定，管理员可能需要按业务习惯调整（如同一列在不同项目里想叫不同的 DTO 字段名），因此改为可编辑，比照 `fieldName` 的编辑权限处理，而不是锁死为只读。相应地，表单字段定义自己不再维护一份独立的 `fieldCode` 输入——既然元数据字段本身的标识可以改，"表单字段定义再复制一份可独立编辑的字段标识"只会产生两处标识不一致的风险，因此表单字段定义的 `fieldCode` 改为完全派生（见决策 4），任何时候都以其绑定的元数据字段当前的 `fieldCode` 为准。

**备选方案**：只在表单字段定义里维护一份"元数据字段 id → 常用 fieldCode"的映射表/常量，元数据字段本身不暴露 `fieldCode` 编辑入口。被否决：不满足"元数据管理需要支持配置字段标识"的明确需求。

### 2. 迁移回填策略：`columnName` 按下划线转驼峰

**决策**：`V24__add_field_code_to_tab_metadata_field.sql` 里为存量数据回填 `field_code = camelCase(column_name)`，与 `V21` 种子数据里手写的 `field_code`（如 `id_card → idCard`、`show_order → showOrder`）保持完全一致的转换规则；新增列上加 `UNIQUE KEY (biz_type, field_code)` 唯一约束防止后续脏数据。

**理由**：现有 `V21` 种子数据本来就是按这个规则手写的，回填结果和线上表单字段定义的 `fieldCode` 天然吻合，不会产生"回填值 ≠ 已绑定表单字段的 fieldCode"的不一致。

**备选方案**：`field_code` 直接等于 `columnName`（不转驼峰）。被否决：会导致回填后自动带出的值和已有表单字段定义的 `fieldCode`（驼峰）不一致，自动回填功能形同虚设。

### 3. 编辑弹窗改绑校验放在 service 层，复用现有"是否可用"判定逻辑

**决策**：
- `FormFieldDefinitionUpdateRequest` 新增可选字段 `metadataFieldId`（允许为 `null` 表示"不改绑"，服务层按"请求值等于当前值"处理为不变更）。
- `MetadataFieldService.listAvailable(bizType)` 保持现有语义（启用 + 未被任何有效定义占用）不变；新增重载 `listAvailable(bizType, currentMetadataFieldId)`，在原有过滤结果基础上，把 `currentMetadataFieldId` 对应的元数据字段（若启用）一并纳入返回列表，供编辑弹窗展示"当前绑定 + 其余可选"的下拉选项。`GET /api/metadata-fields/available` 增加可选查询参数 `excludeDefinitionId`（表单字段定义 id），内部据此反查其 `metadataFieldId` 传给新重载；不传则退化为原有行为，创建弹窗调用方式不变。
- `FormFieldDefinitionServiceImpl.update()`：
  1. 先按既有 `bizType`/`metadataFieldId` 计算 `locked`；
  2. 若 `locked == true` 且请求的 `metadataFieldId` 与当前值不同，抛 `LockedFormFieldException`；
  3. 若 `locked == false` 且请求的 `metadataFieldId` 与当前值不同：校验新元数据字段存在、启用、`bizType` 与当前定义一致，且未被其他有效定义占用（复用 `existsActiveByMetadataFieldId`，排除自身）；通过后连同其余字段一起更新写入，并把该定义的 `fieldCode` 同步刷新为新绑定元数据字段的当前 `fieldCode`（见决策 4）。

**理由**：改绑本质是"绑定关系"这条既有约束的一次放松，放在原有 `update()` 方法里增量加校验，比新增单独的"改绑"接口更贴合现有 REST 资源设计（`PUT /api/form-fields/{id}` 本来就是"整体更新"语义），改动面也更小。

**备选方案**：新增专门的 `PUT /api/form-fields/{id}/rebind` 接口。被否决：`update()` 已经是全量更新语义，没有必要为一个字段单独拆一个接口，增加前端调用复杂度。

### 4. 表单字段定义的 `fieldCode` 改为完全派生，不可编辑，写时同步 + 读时兜底双重保证一致

**决策**（替代最初的"影子记忆上一次自动值"方案）：`FormFieldDefinitionCreateRequest`/`FormFieldDefinitionUpdateRequest` 都不再包含 `fieldCode` 字段，客户端无法提交该值。服务层在两处写入它：
1. 创建时：`entity.setFieldCode(metadata.getFieldCode())`，取自所绑定元数据字段当时的 `fieldCode`。
2. 非锁定定义改绑时：连同 `metadataFieldId` 一起把 `fieldCode` 更新为新绑定元数据字段当时的 `fieldCode`（决策 3）。

此外，由于元数据字段的 `fieldCode` 本身现在可编辑（决策 1），已绑定的表单字段定义里"写入时复制"的那份 `fieldCode` 存在变旧的可能（管理员后来单独编辑了元数据字段的字段标识，没有再触发一次表单字段定义的改绑）。为避免这种陈旧，`FormFieldDefinitionServiceImpl.enrich()` 在读取路径（分页查询、详情查询、渲染元数据查询）里，和现有 `columnName`/`locked` 的计算方式一样，用查到的 `MetadataFieldEntity.getFieldCode()` 覆盖 VO 的 `fieldCode`，即所有对外可见的读结果永远反映"当前绑定的元数据字段的最新字段标识"，不依赖定义表里那份可能滞后的副本。

前端弹窗中"字段标识"输入框保留（作为只读展示），不再可编辑：`v-model="form.fieldCode"` 搭配 `disabled`，其值由"选中/绑定的数据字段" 的 `fieldCode` 单向决定（`watch(() => form.metadataFieldId, ...)` 直接赋值，无需再判断"是否手动改过"）。

**理由**：一旦字段标识的权威来源迁移到元数据字段本身（决策 1），表单字段定义层面允许"手动覆盖 + 有条件保留手动输入"的复杂 UX（原决策 4）就失去了意义——两处都能改会导致同一个"标识"出现两份可能不一致的值，增加维护心智负担。改为单向派生 + 读时兜底刷新，语义更简单，也彻底消除了原方案里"用户手动改过之后再切换元数据字段该不该覆盖"的边界判断。

**备选方案 A**：保留原"影子记忆"UX，同时也允许元数据字段编辑 `fieldCode`。被否决：两个独立可编辑的"字段标识"会不同步，且需要额外设计"哪个优先""要不要提示冲突"，复杂度不成比例。
**备选方案 B**：改绑/元数据字段标识变更时才刷新，读取路径不做兜底覆盖（只在写时同步）。被否决：管理员编辑元数据字段的 `fieldCode` 后，所有已绑定该字段的表单字段定义都需要遍历更新，属于"写扩散"，且容易遗漏；读时兜底覆盖是和现有 `columnName` 完全一致的既有模式，改动成本更低也更可靠。

### 5. 元数据字段 `fieldCode` 的唯一性校验放在 `MetadataFieldServiceImpl.update()`

**决策**：仿照 `FormFieldDefinitionServiceImpl` 原有的 `checkFieldCodeUnique` 模式，在 `MetadataFieldServiceImpl.update()` 里新增同名校验：若请求的 `fieldCode` 与当前值不同，查询同 `bizType` 下是否已存在其他记录使用该 `fieldCode`，存在则拒绝更新并返回业务错误；不存在才允许保存。DB 层的 `UNIQUE KEY (biz_type, field_code)`（决策 2 中已添加）作为最后一道防线，防止并发场景下应用层校验之间出现竞态导致的重复写入。

**理由**：和项目里其它唯一性字段（`fieldCode`、组织/用户编码等）的校验方式保持一致，避免把原始的数据库唯一约束异常直接暴露给前端。

### 6. 弹窗上移的实现方式

**决策**：给 `FormFieldListView.vue` 的 `<el-dialog>` 增加 `top="5vh"` 属性（Element Plus 原生支持，控制弹窗顶部相对视口的距离），不改动弹窗内部布局或引入自定义滚动容器。

**理由**：最小改动解决"底部按钮要滚动才能看到"的问题；`top` 是 Element Plus `el-dialog` 的标准 prop，不需要额外 CSS hack。

**备选方案**：给 `<el-form>` 包一层固定高度 + `overflow-y: auto` 的滚动容器。被否决：需求描述的是"弹窗整体上移"，不是"表单内部滚动"，维持弹窗自身随内容变高、只是起始位置上移的观感更符合诉求。

## Risks / Trade-offs

- **[风险]** 迁移回填的驼峰转换规则如果和某条历史数据的真实预期字段标识不完全吻合（例如未来人工建的、不遵循标准命名的列名）→ **缓解**：回填后管理员可通过元数据字段编辑接口/弹窗直接订正 `fieldCode`（决策 1 已改为可编辑），不必依赖后续迁移脚本。
- **[风险]** 编辑时改绑元数据字段后，`bizType`/`columnName` 变化会导致 `computeLocked` 结果、`columnType` 关联的控件类型校验前提发生变化，如果改绑到一个 `columnType` 不兼容当前 `controlType`（比如原来是字典下拉，新绑定列是数字类型也允许继续用字典下拉，因为字典的值本身就是字符串编码）→ **缓解**：本次改动不新增"根据 columnType 反向约束 controlType"的校验（现状本来就没有这层校验，创建时也没有），维持现有"controlType 由管理员自行选择"的既有行为不变，只新增改绑本身的校验，不扩大校验范围。
- **[风险]** 元数据字段的 `fieldCode` 允许编辑后，如果管理员编辑了一个"当前已被某条表单字段定义绑定"的元数据字段的 `fieldCode`，而该定义此后一直没有被改绑触发同步写入 → **缓解**：按决策 4，读取路径（`enrich()`）始终以元数据字段当前的 `fieldCode` 覆盖展示值，不依赖定义表里可能滞后的副本，因此管理员和最终用户任何时候查询到的 `fieldCode` 都是最新值，即使底层存储的副本尚未被写路径刷新。
- **[Trade-off]** `GET /api/metadata-fields/available` 新增可选参数后，创建/编辑两种场景复用同一个接口而不是拆两个接口，接口职责略微复杂化 → 可接受：复用避免了前端維护两套几乎相同的下拉数据源逻辑。
- **[Trade-off]** 表单字段定义的 `fieldCode` 列虽然逻辑上完全派生，但仍物理保留在 `tab_form_field_definition` 表中（而不是彻底去掉该列、改成纯运行时 join 计算）→ 可接受：保留列意味着操作日志、历史数据、`buildRenderSchema` 等既有代码路径不需要额外改动来"临时拼装"这个值；代价是存在"写路径不同步就会有一份过期副本"的可能，已通过决策 4 的读时覆盖兜底。

## Migration Plan

1. 新增 Flyway 迁移 `V24__add_field_code_to_tab_metadata_field.sql`：`ALTER TABLE tab_metadata_field ADD COLUMN field_code VARCHAR(64) NOT NULL DEFAULT '' COMMENT '字段标识（前端/DTO 使用），初始值由迁移预置，此后可通过接口编辑' AFTER column_type;`，同一脚本内按 `column_name` 驼峰转换规则 `UPDATE` 回填全部存量行，最后加 `UNIQUE KEY uk_tab_metadata_field_biz_field_code (biz_type, field_code)`。
2. 后端：
   - 元数据字段模块：实体/VO/Convert 补充 `fieldCode`；`MetadataFieldUpdateRequest` 新增 `fieldCode`（必填、`Size(max=64)`）；`MetadataFieldServiceImpl.update()` 新增同 `bizType` 下唯一性校验（决策 5）。
   - 表单字段定义模块：`FormFieldDefinitionCreateRequest`/`FormFieldDefinitionUpdateRequest` 移除 `fieldCode` 字段；`create()`/改绑分支分别从绑定的元数据字段派生并写入 `fieldCode`；移除原先独立的 `checkFieldCodeUnique` 校验（及未再使用到的 `FieldCodeDuplicateException`，若确认无其它引用则一并清理）；`enrich()` 读取路径用元数据字段当前 `fieldCode` 覆盖 VO 值；`FormFieldDefinitionUpdateRequest` 新增可选 `metadataFieldId` 字段与改绑校验（决策 3）。
3. 前端：
   - 类型、API 封装同步调整。
   - `MetadataFieldListView.vue`：编辑弹窗新增"字段标识"必填输入框，详情弹窗展示。
   - `FormFieldListView.vue`：编辑弹窗数据字段下拉框（锁定态禁用）+ 弹窗定位上移 + 文案简写；"字段标识"输入框改为禁用态，单向跟随所选数据字段（决策 4），提交时不再携带 `fieldCode`。
4. 回滚策略：新增列且默认值兜底为空字符串（迁移内立即回填不会长期停留在空值状态），如需回滚只需新增一条反向迁移丢弃该列；不涉及破坏性删除已有数据，风险低。

## Open Questions

（无——编辑改绑对锁定字段的处理已通过用户确认：锁定定义禁止改绑）
