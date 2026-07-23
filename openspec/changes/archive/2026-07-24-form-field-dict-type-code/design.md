## Context

`tab_form_field_definition.dict_type_id`（`BIGINT NULL`，仅 `control_type` 为 3
字典下拉 或 5 多选字典下拉时必填）关联 `tab_dict_type.id`。当前后端在四个用到它的地方
都要先拿 `dict_type_id` 查一次 `tab_dict_type` 表得到该字典类型的 `code`，再拿 `code`
去查 `tab_dict_item` 得到实际的下拉选项（`FormFieldDefinitionServiceImpl
.resolveDictOptions`、`FormFieldSnapshotSupport.resolveLabelByCode` 均是这个"先按 id
查类型、再按类型的 code 查字典项"两跳模式）——`tab_dict_item` 本身就是按字典类型的
`code`（`typeCode`）查询的（`DictItemService.getEnabledOptions(code)`），`dict_type_id`
从头到尾只是中间用来换出这个 `code` 的一个间接层。

## Goals / Non-Goals

**Goals:**
- 表单字段定义与字典类型的关联换成业务编码（`code`），不再依赖自增主键 `id`，避免 `id`
  在数据迁移/环境切换场景下失配导致字典下拉选项静默失效。
- 顺带去掉"先按 id 查字典类型、再按 code 查字典项"这个不必要的中间查询。

**Non-Goals:**
- 不改变 `tab_dict_item.dict_type_id` 关联 `tab_dict_type.id` 的既有设计——那是字典项
  归属字典类型的父子关系，字典管理模块内部维护，创建后同一批数据不会跨环境迁移错位，
  用户本次提出的问题特指"表单字段定义"这一处跨模块引用，不涉及字典管理模块自身。
- 不改变字典类型 `code` 一经创建是否可修改的既有规则（不在本次改动范围内调整
  `dict-management` 的编辑限制）。

## Decisions

### 1. `dict_type_id` 列改为 `dict_type_code`，历史数据按现有关联关系原地转换

新增迁移：

```sql
ALTER TABLE `tab_form_field_definition` ADD COLUMN `dict_type_code` VARCHAR(64) NULL
    COMMENT '关联的字典类型编码，关联 tab_dict_type.code，仅 control_type=3/5 时必填' AFTER `dict_type_id`;

UPDATE `tab_form_field_definition` d
    JOIN `tab_dict_type` t ON d.dict_type_id = t.id
    SET d.dict_type_code = t.code
    WHERE d.dict_type_id IS NOT NULL;

ALTER TABLE `tab_form_field_definition` DROP COLUMN `dict_type_id`;
```

先加新列、按现有 `dict_type_id → tab_dict_type.id` 的关联关系批量回填 `code`，再删旧列，
保证历史数据（包括本次会话里另一个尚未归档的 change `user-gender-dict-and-import-defaults`
新增的"性别"字段定义，它的 `dict_type_id` 指向新建的 `gender` 字典类型）在这次迁移执行
时无论顺序如何都能正确转换——不需要单独处理这一条，因为迁移是按版本号顺序执行的通用
`UPDATE ... JOIN`，覆盖当时表里所有已存在的行。

`FormFieldDefinitionEntity.dictTypeId`（`Long`）改为 `dictTypeCode`（`String`），
`@TableField(value = "dict_type_id", updateStrategy = FieldStrategy.ALWAYS)` 改为
`@TableField(value = "dict_type_code", updateStrategy = FieldStrategy.ALWAYS)`（这个
`FieldStrategy.ALWAYS` 覆盖仍然需要保留——控件类型切换为非字典类时要能显式把该列写成
`NULL`，MyBatis-Plus 默认的 `NOT_NULL` 更新策略会跳过 null 值字段，这一点不因列改名
而改变）。

### 2. 服务层校验、动态渲染、操作日志快照三处均改为按编码直接查询，去掉中间的按 id 查类型步骤

`FormFieldDefinitionServiceImpl`：
- `validateDictType(Integer controlType, Long dictTypeId)` 改为
  `validateDictType(Integer controlType, String dictTypeCode)`：控件类型属于
  `DICT_TYPES` 时，`dictTypeCode` 非空且按 `tab_dict_type.code` 查询存在一条未被逻辑
  删除的记录（写法比照 `DictTypeServiceImpl.checkCodeUnique` 里 `LambdaQueryWrapper`
  按 `code` 查询、`ne(status, DELETED)` 排除已删除的既有模式），否则抛出既有的
  `DictTypeRequiredException`。相比改造前"`dictTypeMapper.selectById(dictTypeId) ==
  null`"（不过滤状态，逻辑删除的字典类型只要行还在物理表里就能通过校验），改造后按
  `code` 查询时显式排除已逻辑删除的记录，是一个更严格、更正确的校验，不算是功能倒退。
- `resolveDictOptions(Integer controlType, String dictTypeCode)`：直接
  `dictItemService.getEnabledOptions(dictTypeCode)`，不需要先查 `tab_dict_type` 换出
  `code` 这一步。
- `fetchDictTypeNameMap`：批量查询字典类型名称时，改为按 `code` 集合查询
  `tab_dict_type`（`in(DictTypeEntity::getCode, codes)`），返回 `code → name` 的
  `Map<String, String>`（原来是 `Map<Long, String>`，key 从 id 换成 code）。
- `enrich()` 里 `if (entity.getDictTypeId() != null)` 相应改为
  `if (entity.getDictTypeCode() != null)`，用 `dictTypeCode` 去查上面这个 map。

`FormFieldSnapshotSupport.resolveLabelByCode(Long dictTypeId)` 改为
`resolveLabelByCode(String dictTypeCode)`：直接
`dictItemService.getEnabledOptions(dictTypeCode)`，删除原来"先
`dictTypeMapper.selectById(dictTypeId)` 换出 `code`"这一步，连带这个方法不再需要注入
`DictTypeMapper`（确认删除该字段前，检查这个组件里是否还有其他地方用到
`dictTypeMapper`，目前看只有这一处用到，可以整个移除这个依赖）。

`FormFieldDefinitionCreateRequest`/`FormFieldDefinitionUpdateRequest`/
`FormFieldDefinitionVO` 的 `dictTypeId`（`Long`）改为 `dictTypeCode`（`String`），
`@Size(max = 64)` 约束（字典类型编码长度上限，比照 `tab_dict_type.code` 列定义）。

### 3. 前端字典类型选择器提交值从 id 改为 code

`FormFieldDefinitionPanel.vue` 的字典类型 `<el-select>`：`<el-option>` 的
`:value="opt.id"` 改为 `:value="opt.code"`（`DictTypeRow` 本来就有 `code` 字段，来自
`GET /api/dict-types` 分页接口，不需要改动接口或类型定义）；`form.dictTypeId`（及其
初始值、编辑回填、提交 payload、校验规则 key）整体改名为 `form.dictTypeCode`，类型从
`number | null` 改为 `string | null`；`validateDictType` 校验函数的参数类型同步改为
`string | null`。`types/formField.ts` 里 `FormFieldDefinition`/
`FormFieldDefinitionFormFields` 的 `dictTypeId: number | null` 改为
`dictTypeCode: string | null`。

被拒绝的替代方案：保留 `dict_type_id` 不变，只在应用层加一层"id 失配时按名称模糊匹配
兜底"的容错逻辑。这治标不治本，且"按名称模糊匹配"本身也不可靠（名称允许重复/修改），
用户明确指出的根因是"id 会变、code 不会变"，直接换成 code 关联是对症的做法。

## Risks / Trade-offs

- **[风险] 列替换（加列回填、删旧列）是一次性的破坏性 schema 变更**——与
  `user-gender-dict-and-import-defaults` 里 `tab_user.gender` 的类型转换同理，项目
  处于开发阶段、历史数据量小，风险可控。
- **[取舍] `tab_form_field_definition` 与 `tab_dict_type` 之间不再有数据库外键约束
  可言（原来也没有真正的 `FOREIGN KEY` 约束，只是应用层校验）**——`dict_type_code`
  同样不加数据库外键，校验仍然只在应用层（`validateDictType`）进行，与原有
  `dict_type_id` 的约束强度一致，不是本次改动引入的新风险。
