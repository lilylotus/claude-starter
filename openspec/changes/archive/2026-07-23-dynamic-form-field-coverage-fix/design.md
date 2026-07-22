## Context

组织（ORG）、用户（USER）、任职（POSITION）、应用（APP）四个模块的列表页与"自身"的新增/编辑表单已经通过 `useDynamicFormFields(bizType)`（前端）+ `FormFieldDefinitionService.listActiveByBizType`/`buildRenderSchema`（后端）接入了"表单字段定义"（`ext1`~`ext10`）动态渲染。四个服务的 `ServiceImpl` 均已注入 `FormFieldDefinitionService` 并在创建/更新时调用 `validateDynamicFields` 做必填/正则/唯一性校验。本次要修的三处缺口都发生在这套机制**之外**的相邻界面：

1. 用户管理弹窗内嵌的"任职信息"子表单——它提交的是 `UserPositionRequest`（`UserCreateRequest.positions[]`/`UserUpdateRequest.positions[]` 的元素类型），而不是独立任职管理用的 `PositionCreateRequest`；`UserPositionRequest`/`UserPositionVO` 没有 `ext1`~`ext10`，`UserConvert.java` 里对应的 `@Mapping(target = "extN", ignore = true)` 显式屏蔽了这些字段。
2. 四个"详情"页面（`OrgDetailView.vue` 等）是独立路由页面，用静态 `<el-descriptions>` 渲染固定字段列表，从未调用 `useDynamicFormFields`。
3. 四个 `toLogSnapshot(Entity)` 方法（`UserServiceImpl`/`OrgServiceImpl`/`PositionServiceImpl`/`AppServiceImpl`）手工构建 `Map<String, Object>`，只 `put` 硬编码的原有字段，不含 `ext1`~`ext10`。

## Goals / Non-Goals

**Goals:**
- 内嵌任职子表单的读写路径（`UserPositionRequest`/`UserPositionVO`/`UserConvert`/前端子表单）与独立任职管理入口（`PositionCreateRequest`/`PositionVO`）在"支持自定义字段"这一点上行为一致。
- 四个详情页面展示当前启用的自定义字段（`ext1`~`ext10`），展示名取自表单字段定义的 `fieldName`，未配置定义的 `extN` 列不展示。
- 四个模块的操作历史在自定义字段被修改时，能在字段级变更详情中看到该字段的旧值/新值，字段标签使用表单字段定义的 `fieldName` 而不是 `ext1` 这样的技术列名。

**Non-Goals:**
- 不改变"表单字段定义"（`form-field-definition-management`）与"元数据字段配置"（`metadata-field-management`）本身的模型或管理界面。
- 不为详情页新增编辑能力——详情页的自定义字段展示仍是只读的。
- 不改变 `gender`（用户性别）、`orgId`/`userId`/`positionType`（任职）、`ownerId`/`orgId`（应用）等既有"不纳入动态渲染范围"字段的处理方式，这些字段在详情页/历史中继续按原有硬编码方式展示。
- 不引入新的数据库迁移（`ext1`~`ext10` 列已存在），不新增第三方依赖。

## Decisions

### 1. 内嵌任职子表单补齐 ext1~ext10，直接对齐独立任职管理入口
`UserPositionRequest`/`UserPositionVO` 按 `PositionCreateRequest`/`PositionVO` 的既有写法追加 `ext1`~`ext10`（`String`，`@Schema` 注解风格保持一致）。`UserConvert.java` 中 `toPositionEntity`/`updatePositionEntity` 上的 10 个 `@Mapping(target = "extN", ignore = true)` 全部删除，交给 MapStruct 按同名字段自动映射；`toPositionVO` 同理无需新增 ignore（字段名一致会自动映射）。

校验方面复用 `UserServiceImpl.validateDynamicFields` 已有的机制：该方法当前是否已经对 `positions[]` 里的动态字段做校验需要在实现时确认；若只校验了用户自身请求对象，需要为 `positions[]` 中每个 `UserPositionRequest` 追加一次对 `FormFieldBizType.POSITION` 的动态字段校验（必填/正则/唯一性），复用 `PositionServiceImpl` 里已有的校验逻辑或抽取共享方法，避免重复实现两套动态字段校验逻辑。

前端 `UserManagementView.vue` 的任职子表单额外调用 `useDynamicFormFields('POSITION')`，渲染逻辑与独立任职管理页面的新增/编辑表单一致（`showInCreate`/`showInEdit`/`controlType`/`editable`/`placeholder`），逐行渲染在子表单的每一行内；`blankPosition()` 初始化时把 `ext1`~`ext10` 置为空字符串，`openEditDialog` 回填时把既有任职记录的 `ext1`~`ext10` 一并拷贝进子表单的行数据模型。

### 2. 详情页面展示"当前全部启用的自定义字段"，不受 showInList/showInCreate/showInEdit 过滤
四个详情页各自调用 `useDynamicFormFields(bizType)` 拿到该 `bizType` 下启用状态的字段定义列表，遍历后以 `fieldName: value` 的形式追加渲染到 `<el-descriptions>`（字典下拉类型的字段展示 `dictOptions` 中对应 `label` 而不是原始 `value`）。

考虑过按 `showInList`/`showInEdit` 过滤只展示"编辑表单里能看到的字段"，但这三个开关语义上是"新增/编辑/列表表单的展示与录入控制"，跟"详情页是否应该展示这个字段的当前值"是两个独立关注点——一个字段即使配置为编辑时只读或列表不展示，只要它启用且有值，详情页作为该资源的完整只读视图也应该能看到。因此详情页统一展示 `listActiveByBizType(bizType)` 返回的全部启用定义对应的值，不做二次过滤。

`UserDetailView.vue` 内嵌的任职记录表格同理接入 `useDynamicFormFields('POSITION')`，为每条任职记录追加自定义字段列（或展开行展示，视现有表格列宽而定，具体交给实现阶段按现有 UI 风格决定）。

### 3. 操作历史快照复用 `FormFieldDefinitionService.listActiveByBizType`，按 `columnName` 匹配 extN 值
在各 `ServiceImpl` 现有的 `toLogSnapshot(Entity)` 方法末尾追加一段：调用 `formFieldDefinitionService.listActiveByBizType(bizType)`，过滤出 `columnName` 属于 `ext1`~`ext10` 的定义，按 `columnName` 从 entity 上取对应的 `getExtN()` 值，以该定义的 `fieldName` 作为 key `put` 进快照 map。四处逻辑重复度高，抽取一个小的共享工具方法（例如 `formfield` 包下新增 `FormFieldSnapshotSupport.appendExtFieldSnapshot(Map<String, Object> snapshot, List<FormFieldDefinitionVO> definitions, Map<String, String> extValuesByColumnName)`），四个 `ServiceImpl` 在各自 `toLogSnapshot` 里构造一个 `Map.of("ext1", entity.getExt1(), ..., "ext10", entity.getExt10())` 传入即可，避免在四个类里各写一遍相同的过滤+put 逻辑。

未配置字段定义的 `extN` 列不出现在快照里，因此该列即使底层有值也不会被拿来对比——这与"扩展字段默认无字段定义"这一既有约束一致，管理员未通过表单管理开放的自定义字段不会出现在操作历史里，符合"看不见的字段也不该出现在变更记录里"的直觉。

`OperationLogRecorderImpl` 的通用 diff 逻辑不需要改动，它已经是"逐 key 对比两份快照"的通用实现。

## Risks / Trade-offs

- [四处 `toLogSnapshot` 都新增一次 `listActiveByBizType` 查询] → 该方法内部已有走 MyBatis-Plus 索引的简单条件查询，四个写操作路径（创建/更新/启用/停用/删除）本身就不是高频批量操作，可接受；如后续有性能顾虑可在 `FormFieldDefinitionServiceImpl` 内部加短 TTL 缓存，本次不做。
- [详情页展示"全部启用定义"而不是"showInEdit 的字段"] → 如果某个自定义字段被管理员配置为 `showInList=false && showInCreate=false && showInEdit=false`（既不在列表也不在表单出现，形同临时下线但未删除定义），详情页仍会展示它。这是本设计的有意选择（见 Decision 2），如果实际验收时产品期望不同，需要回来调整为按某个开关过滤。
- [内嵌任职子表单的动态字段校验是否已有埋点] → 需要在实现阶段读代码确认 `validateDynamicFields`/`PositionServiceImpl` 现有校验方法能否直接复用；如果两处校验逻辑此前就是分别独立实现的，本次只需让内嵌路径"也调用一次"，不需要重新设计校验规则本身。

## Migration Plan

无数据库迁移。纯代码改动，后端与前端可分别独立发布；后端先上不影响现有功能（新增字段默认为 `null`，`toLogSnapshot` 未配置定义时行为与改动前一致），前端上线后内嵌任职子表单、四个详情页才会开始展示/录入自定义字段。建议后端先合并部署，前端随后跟上，避免中间态下前端调用了尚不支持 ext 字段的旧后端。

## Open Questions

- 内嵌任职子表单里，多行任职记录且每行都渲染一整套自定义字段时，弹窗横向/纵向空间是否足够，需要实现阶段结合现有 UI（`所属组织`/`任职类型` 已经要求"标签不换行、两列对齐"）验证观感，必要时收窄自定义字段的展示宽度或改为可折叠区域。
