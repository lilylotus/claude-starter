## Context

组织（`tab_org`）、人员（`tab_user`）、任职（`tab_user_position`）、应用（`tab_app`）四张表既有一批原有表字段，目前只有 `tab_org` 预留了 `ext1`~`ext10`（`VARCHAR`）扩展列，其余三张表没有。原有字段的展示名称、列表/表单可见性、控件类型、校验规则完全硬编码在前端模板与后端 Bean Validation 注解里。本次引入两层新概念：

1. **元数据字段配置**（`tab_metadata_field`）：一份目录，记录"这四张业务表里，哪些列可以被拿来做展示配置"，每条记录对应一个真实存在的数据库列（表名称、列名、列类型、展示名称、状态）。
2. **表单字段定义**（`tab_form_field_definition`）：在元数据目录的基础上，选一条元数据字段记录，配上业务侧关心的展示/校验属性（控件类型、唯一性、必填、列表/表单可见性、可编辑、正则、提示文字），驱动四个管理页面的动态渲染。

两层分离的原因：元数据配置描述的是"物理事实"（这一列确实存在、类型是什么），理论上可以被将来的其他能力复用（不仅限于表单管理）；表单字段定义描述的是"业务侧怎么用这一列"，两者关注点不同、变化频率也不同（元数据目录随 schema 变化，字段定义随业务需求变化）。

约束：
- 四个业务表的列结构本身不变（本次迁移只新增 `ext1`~`ext10` 给另外三张表，不改名/不删除任何既有列）。
- 组织的 `parentId`（上级组织树选择器）、任职的 `orgId`/`userId`（组织/用户关联）与 `positionType`（认证类型，已绑定固定字典类型 `position_type`）、应用的 `ownerId`/`orgId`，以及四类对象共有的 `status`（启停用），这些字段已经有专用的、超出"文本框/数字框/字典下拉"三选一范畴的交互控件，本次**不**纳入元数据字段目录，也不纳入字段定义体系，继续保持硬编码渲染。
- 组织/用户/应用各自的 `name`、`code` 是当前后端已有 `@NotBlank` + 唯一性硬编码校验的"承重字段"，其对应的字段定义可以调整展示名称/顺序/提示文字等元数据，但不能被停用、删除，也不能把必填/新增表单可见/编辑表单可见改为否。
- 复用已有的 `cn.nihility.rbac.dict`（`tab_dict_type`/`tab_dict_item`）作为"下拉单选字典"控件的选项来源，不引入新的字典体系。
- 复用项目既有的分层约定、`Result` 统一响应、Bean Validation、MapStruct 静态单例转换。

## Goals / Non-Goals

**Goals:**
- 四张业务表在扩展字段能力上保持一致（都有 `ext1`~`ext10`）。
- 一份元数据字段目录描述"哪些列可配置"，表单字段定义只能绑定目录里的条目，不能凭空指定任意列名，从源头避免拼错列名。
- 表单字段定义统一覆盖原有表字段与扩展字段两类来源，列表列与新增/编辑表单的渲染统一由字段定义驱动。
- 同一元数据字段同一时刻只能被一条有效字段定义绑定。
- 承重字段（`name`/`code`）的必填/可见性受保护，不因误配置而在表单中消失。
- 后端在 org/user/position/app 的新增、编辑逻辑中，对非承重字段按字段定义做：必填、正则、唯一性（同业务对象类型内、`status != -1000` 范围）校验。
- 前端四个页面的列表列、新增表单、编辑表单，除少量硬编码专用控件字段外，全部按字段定义动态渲染。

**Non-Goals:**
- 元数据字段目录不支持前端新增/删除，只能通过迁移预置、通过接口编辑展示名称与状态（列结构本身的变化仍然只能通过写新的 Flyway 迁移完成）。
- 不做字段定义/元数据配置的版本历史/审批流程（沿用现有"操作日志"模块记录增删改即可，不单独设计）。
- 不做跨业务对象类型的字段复用/继承。
- 前端不做"字段定义驱动的可视化表单设计器"（拖拽式），只是配置表单 + 动态渲染。
- 不把 `parentId`/`orgId`/`userId`/`positionType`/`ownerId`/`status` 这类已有专用交互控件的字段纳入元数据目录或字段定义体系。

## Decisions

### 1. 业务对象类型标识：字符串枚举而非纯数字
新增共享常量类 `cn.nihility.rbac.formfield.constant.FormFieldBizType`，定义 `ORG`/`USER`/`POSITION`/`APP` 四个字符串常量，供元数据模块与表单字段定义模块共用；`tab_metadata_field.biz_type`、`tab_form_field_definition.biz_type` 均存 `VARCHAR(20)`。
- **原因**：`bizType` 需要在前端路由/API 参数里直接传递，字符串枚举比数字码可读、可调试；两个模块共用同一常量类避免取值不一致。

### 2. 元数据字段配置表：只描述物理事实，字段与用户描述一一对应
新表 `tab_metadata_field`：

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | 主键 |
| `biz_type` | VARCHAR(20) | `ORG`/`USER`/`POSITION`/`APP`，便于按业务对象类型查询 |
| `table_name` | VARCHAR(64) | 字段所属表名称，如 `tab_org` |
| `column_name` | VARCHAR(64) | 字段列名（数据库字段定义），如 `code`、`show_order`、`ext6` |
| `column_type` | VARCHAR(32) | 字段类型（数据库字段类型），如 `VARCHAR(255)`、`INT` |
| `field_name` | VARCHAR(64) | 字段名称，如"组织编码" |
| `status` | INT | `2000`=启用，`3000`=停用，`-1000`=已逻辑删除 |
| `create_by`/`create_time`/`update_by`/`update_time` | — | 沿用项目统一审计字段 |

`table_name`/`column_name`/`column_type`/`biz_type` 四者一经写入（迁移种子数据）不可通过接口修改，只有 `field_name`、`status` 可编辑——因为前四者描述的是真实数据库结构，允许编辑会导致目录与实际 schema 不一致。目录不提供新增/删除接口，只能通过 Flyway 迁移预置（Decision 3），接口只提供分页查询、详情查询、编辑（`field_name`/`status`）、启用/停用、按 `bizType` 查询"可用字段"（Decision 6）。

### 3. 默认初始化的元数据字段目录
新增迁移为 `tab_user`、`tab_user_position`、`tab_app` 各补齐 `ext1`~`ext10`（`VARCHAR(255)`，与 `tab_org` 一致）。随后通过种子数据为四类业务对象的"可开放配置字段"写入 `tab_metadata_field` 记录：

| bizType | tableName | 可开放配置的原有列 | 扩展列 |
|---|---|---|---|
| ORG | `tab_org` | `name`、`code`、`show_order`、`remark` | `ext1`~`ext10` |
| USER | `tab_user` | `name`、`code`、`mobile`、`id_card`、`show_order`、`remark` | `ext1`~`ext10` |
| POSITION | `tab_user_position` | `position_address`、`position_phone`、`show_order`、`remark` | `ext1`~`ext10` |
| APP | `tab_app` | `name`、`code`、`show_order`、`remark` | `ext1`~`ext10` |

`parentId`/`orgId`/`userId`/`positionType`/`ownerId`/`status`/`id`/`gender`/审计字段不出现在目录中（不可配置，继续硬编码渲染，理由见 Context 约束）。`gender`（用户性别）当前是固定三值枚举，不是自由文本/数字，也未接入 `tab_dict`，视为已有专用交互控件，不纳入目录。

### 4. 承重字段的保护规则不落库，由代码维护的白名单判定
`tab_metadata_field`/`tab_form_field_definition` 都不新增额外的"是否锁定"列——按元数据表 4 个业务属性的既定范围，不引入这份规格之外的列。承重字段保护改为在 `cn.nihility.rbac.formfield.constant.LockedFormFields`（或等价的 Java 常量集合）里维护一份 `(bizType, columnName)` 白名单：`(ORG, name)`、`(ORG, code)`、`(USER, name)`、`(USER, code)`、`(APP, name)`、`(APP, code)`。表单字段定义在读取/更新时，通过其绑定的 `metadataFieldId` 反查 `tab_metadata_field` 的 `(bizType, columnName)`，比对这份白名单得到一个只读的、计算得出的 `locked` 布尔值（在 VO 中体现，不持久化）。
- **原因**：这份保护名单是四个业务模块既有校验逻辑（`@NotBlank`、编码唯一性判断）决定的，属于代码层面的既定事实，不属于管理员可以在界面上调整的"元数据配置"，落库反而给人"这是可配置项"的错觉；用代码常量维护，改动需要走代码评审，更符合它"受保护"的定位。
- **备选**：在 `tab_metadata_field` 或 `tab_form_field_definition` 上加 `locked` 列；放弃，因为用户对元数据表的字段范围有明确描述（表名称/字段类型/字段列名/字段名称/状态），额外加列超出了这个范围，且锁定逻辑的本质是"代码里已经有别的校验在保护它"，用代码常量表达更贴切。

对 `locked=true`（计算得出）的表单字段定义，更新接口拒绝：将 `status` 改为非 `2000`（不可停用）、`DELETE` 请求（不可删除）、将 `isRequired`/`showInCreate`/`showInEdit` 改为 `false`；其余属性（展示名称、顺序、`placeholder`、`validateRegex`、`editable`、`isUnique`、`showInList`）仍可自由调整。后端对这些锁定字段的必填与唯一性仍完全依赖现有硬编码校验（`@NotBlank` + `OrgServiceImpl`/`UserServiceImpl`/`AppServiceImpl` 里既有的编码唯一性判断），字段定义里的 `isRequired`/`isUnique`/`validateRegex` 配置对锁定字段只影响前端展示提示，不重复触发本次新增的后端数据驱动校验管线。

### 5. 表单字段定义表结构：绑定元数据字段而非直接写列名
新表 `tab_form_field_definition`：

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | 主键 |
| `biz_type` | VARCHAR(20) | 冗余存储，创建时取自所绑定元数据字段的 `bizType`，之后不可变 |
| `metadata_field_id` | BIGINT | 绑定的元数据字段，关联 `tab_metadata_field.id`；一经创建不可再改绑 |
| `field_name` | VARCHAR(64) | 展示名称，创建时默认取自元数据字段的 `field_name`，此后可独立编辑（与目录解耦，允许同一物理列在不同使用场景下叫不同名字——虽然本次每个元数据字段同时只能被一条有效定义绑定，但仍保留独立可编辑的语义） |
| `field_code` | VARCHAR(64) | 管理员在"表单管理"里为这条字段定义起的展示/标识别名，如把绑定 `ext6` 的定义命名为 `idCardNo`；同一 `bizType` 下唯一 |
| `control_type` | INT | `1`=文本框，`2`=数字框，`3`=字典下拉 |
| `dict_type_id` | BIGINT NULL | 仅 `control_type=3` 时必填，关联 `tab_dict_type.id` |
| `is_unique` | TINYINT(1) | 是否要求同 `bizType` 下有效数据唯一 |
| `is_required` | TINYINT(1) | 是否必填 |
| `show_in_list` | TINYINT(1) | 是否在列表中展示 |
| `show_in_create` | TINYINT(1) | 是否在新增表单中展示 |
| `show_in_edit` | TINYINT(1) | 是否在编辑表单中展示 |
| `editable` | TINYINT(1) | 表单中展示时是否可编辑（`false` 则只读展示） |
| `validate_regex` | VARCHAR(255) NULL | 正则校验规则，前后端共用同一个字符串 |
| `placeholder` | VARCHAR(128) NULL | 输入提示文字 |
| `show_order` | INT | 显示序号，值越大越靠前 |
| `status` | INT | `2000`=启用，`3000`=停用，`-1000`=已逻辑删除 |
| `create_by`/`create_time`/`update_by`/`update_time` | — | 沿用项目统一审计字段 |

`metadata_field_id`/`biz_type` 一经创建不可修改（更新接口忽略/拒绝对这两个字段的改动）；要"换绑"只能删除当前定义（非锁定时）再新建一条绑定新元数据字段的定义。

**`field_code` 不是数据绑定 key，只是展示别名**：`field_code` 纯粹是管理员在"表单管理"界面上为这条定义起的展示/标识名字（例如给绑定 `ext6` 的定义取名 `idCardNo`），它与请求 DTO/实体上真正存在的 Java 属性名（或前端行数据的 key）常常不同，不能拿来做反射取值或表单模型的 key。后端校验（Decision 9）与前端动态渲染（Decision 10）实际使用的数据绑定 key，统一是该定义绑定的元数据字段 `column_name`（数据库列名，下划线形式，如 `id_card`、`show_order`、`ext6`）转换成驼峰形式后的结果（`idCard`、`showOrder`、`ext6`）——这正好对应 Create/Update/VO 这些 DTO 上由字段名直接映射得到的 Java 属性名。这一点在实现验证阶段被发现并修正过一次（详见 tasks.md 9.3 的验证记录）：早期实现错误地按 `field_code` 反射取值，导致所有"`field_code` 与物理列名不同"的场景（即本次改动最核心的用例——`ext6` 绑定成 `idCardNo`）必填/正则/唯一性校验全部失效。

### 6. 同一元数据字段的绑定互斥
系统 SHALL 保证同一个 `metadata_field_id` 同一时刻只能被一条有效（`status != -1000`）的表单字段定义绑定。`GET /api/metadata-fields/available?bizType=ORG` 返回该 `bizType` 下状态为启用、且未被任何有效表单字段定义占用的元数据字段列表，供"表单管理"新增字段定义时选择；编辑现有定义时，其自身当前绑定的元数据字段也需要出现在可选范围内（不能因为"已被自己占用"而在编辑态选不到）。
- **原因**：这条规则把此前"`ext1`~`ext10` 互斥占位"的特例规则泛化成了通用规则，同时也自然防止同一个原有列被两条不同的定义重复绑定（比如两条定义都想展示 `remark`），比只针对 `ext` 列做特例校验更一致。

### 7. 表单字段定义的创建/删除/停用不再区分"原有字段"和"扩展字段"
不同于此前"原有字段只能改元数据、扩展字段才能新增删除"的设计，现在创建、删除、停用统一只看 Decision 4 的锁定判定：
- **创建**：`POST /api/form-fields` 可以绑定目录里任意"可用"的元数据字段（不区分是原有列还是 `extN`）。
- **删除**：`DELETE /api/form-fields/{id}` 对非锁定字段定义生效（释放其绑定的元数据字段供重新创建），对锁定字段定义拒绝。
- **停用**：同删除，锁定字段定义拒绝停用。
- **原因**：既然锁定保护已经泛化为"字段是否承重"而不是"字段是不是扩展列"，创建/删除/停用的权限判断也应该统一按锁定与否来定，不再需要区分两种来源，逻辑更简单、一致。

### 8. 唯一性校验的实现方式（防注入）
非锁定字段绑定的 `column_name` 目标列名只能来自 `tab_metadata_field` 目录（迁移种子数据写入，非任意字符串），所以业务模块（org/user/position/app）各自的 Mapper 可以安全地用 MyBatis `${columnName}` 做列名占位实现一个通用方法：

```java
int countByColumnValue(@Param("column") String column, @Param("value") String value, @Param("excludeId") Long excludeId);
```

放在 `resources/mybatis/mapper/{Org,User,UserPosition,App}Mapper.xml` 里，`WHERE status != -1000 AND ${column} = #{value} AND id != #{excludeId}`。调用前，`formfield` 模块会先按 `bizType` 从 `FormFieldDefinitionService` 拿到该定义绑定的元数据字段的 `columnName`，这个值来自数据库里由迁移写入的目录记录（不接受任意字符串拼接进 SQL），不构成 SQL 注入风险；这是本项目里第一批需要手写 XML 的 Mapper，遵循 `mybatis/mapper/*.xml` 既有约定。
- **备选**：在 `formfield`/`metadata` 模块里用反射/通用 `SELECT COUNT(*) FROM tab_xxx WHERE ...` 拼表名；放弃，因为表名拼接同样需要额外的防护，且让这两个模块直接访问四张业务表破坏了模块边界，不如让各业务模块自己实现一个方法。

### 9. 校验编排位置
四个业务模块的 `XxxServiceImpl.create/update` 方法里，在保存前调用 `FormFieldDefinitionService.listActiveByBizType(bizType)`，过滤出非锁定（Decision 4）且适用于当前场景（新增看 `showInCreate`，编辑看 `showInEdit`）的定义，交给共用工具类 `cn.nihility.rbac.formfield.support.DynamicFieldValidator.validate(...)` 依次做：必填非空校验 → 正则校验（`Pattern.matches`）→（`isUnique=true` 时）调用本模块 Mapper 的 `countByColumnValue` 唯一性校验。任一失败抛 `BusinessException`，走现有全局异常处理，返回非零 `code`。锁定字段定义（`name`/`code`）跳过这条数据驱动校验管线，完全依赖既有的 Bean Validation 与 service 层硬编码校验。

**取值 key**：`DynamicFieldValidator` 通过 Spring `BeanWrapperImpl` 按属性名反射从请求 DTO 上取值，这个属性名**不是** `field_code`，而是该定义绑定的元数据字段 `column_name` 转换成驼峰形式后的结果（详见 Decision 5 补充说明）；`UserServiceImpl` 原先硬编码的身份证号唯一性校验（`checkIdCardUnique`）已随之移除，改由默认表单字段定义（`isUnique=true`）驱动这条数据驱动管线产生等价效果。

### 10. 渲染元数据接口
新增 `GET /api/form-fields/render-schema?bizType=ORG` 返回该 `bizType` 下全部启用（`status=2000`）的表单字段定义列表（按 `showOrder` 降序），每项包含 `fieldCode`（管理员自定义的展示别名，不用于数据绑定，见 Decision 5 补充说明）、`fieldName`、`controlType`、`isRequired`、`isUnique`、`showInList`、`showInCreate`、`showInEdit`、`editable`、`locked`（计算得出）、`validateRegex`、`placeholder`，以及 `columnName`（来自绑定的元数据字段的数据库列名，下划线形式，如 `id_card`）；当 `controlType=3`（字典下拉）时额外内嵌 `dictOptions`（`[{label, value}]`，服务端调用现有 `DictItemService` 按 `dictTypeId` 查询后拼装）。
- 前端四个页面各自在挂载时通过 `useDynamicFormFields(bizType)`（Decision 12）调用一次该接口；拉取结果后统一先把每项的 `columnName` 转换成驼峰形式（如 `id_card` → `idCard`，`ext1`..`ext10` 本身不含下划线、转换后不变），下游一律用这个转换后的驼峰 `columnName` 作为 `el-table`/`el-form` 动态列与表单模型的绑定 key（而不是 `fieldCode`），这样才能与四个模块 `XxxCreateRequest`/`XxxUpdateRequest`/`XxxVO` 上实际存在的 Java 属性名（JSON 字段名）对上。渲染结果驱动：`el-table` 动态渲染列（`showInList=true` 的项）、新增/编辑弹窗动态渲染 `el-form-item`（分别按 `showInCreate`/`showInEdit` 过滤）、控件类型映射为 `el-input`/`el-input-number`/`el-select`，`editable=false` 时该表单项渲染为禁用态而非直接隐藏。`parentId`/`orgId`/`userId`/`positionType`/`ownerId`/`status`/`gender` 等专用控件字段不出现在 `render-schema` 结果中，仍由页面模板里固定的一小段硬编码代码渲染。
- 提交时，`ext1`..`ext10` 部分放进请求体的平铺字段，原有字段部分直接对应实体已有的同名属性；四个模块的 `XxxCreateRequest`/`XxxUpdateRequest`/`XxxVO` 只需新增 `ext1`..`ext10` 十个可选 `String` 字段；MapStruct 转换按字段名自动映射，无需手写转换代码。
- **行为变化说明**：用户管理原先前端硬编码的手机号/身份证号格式校验（正则）与后端 `checkIdCardUnique` 唯一性校验，随本次改造一并移除——默认表单字段定义（V21 种子数据）没有为 `mobile`/`id_card` 预置 `validate_regex`，格式校验现在完全交由管理员在"表单管理"里按需配置；身份证号唯一性则改由 `isUnique=true` 的默认定义驱动数据驱动管线保证，等价但不完全等同于原硬编码逻辑（不再有编译期保证，依赖运行时配置未被误改）。这是本次改造范围内的既知行为变化，不是遗留缺陷。

### 11. 默认字段定义的初始化
系统通过迁移种子数据为 Decision 3 表格里列出的"可开放配置的原有列"（不含 `ext1`~`ext10`）各预置一条启用状态的表单字段定义，绑定对应的元数据字段，展示名称取自然语言描述（如"组织名称""组织编码""显示序号""备注"），`showInList`/`showInCreate`/`showInEdit`/`editable` 均为 `true`，控件类型按列的自然类型给 `1`（文本框）或 `2`（数字框，如 `showOrder`）。`ext1`~`ext10` 对应的元数据字段目录里存在、但默认**不**预置字段定义——保持"零配置不出现，管理员按需绑定"的状态，与此前设计一致。
- **原因**：如果不预置这些定义，迁移完成后四个管理页面会因为"没有任何启用状态的字段定义"而看不到 `name`/`code`/`remark` 等字段，等于功能倒退；预置扩展列的定义则没有必要，因为扩展列本来就该是"空白待分配"的状态。

### 12. 前端复用：`useDynamicFormFields` 组合式函数
新增 `src/composables/useDynamicFormFields.ts(bizType)`，封装拉取 `render-schema`、生成 `el-table` 列配置、生成新增/编辑表单项配置、生成对应的 `el-form` `rules`（必填/正则映射为 `rules`，唯一性不做前端异步校验，交给提交时后端返回的业务错误统一提示）。四个页面各自 `useDynamicFormFields('ORG' | 'USER' | 'POSITION' | 'APP')` 接入，页面模板保留的硬编码部分只剩专用控件字段（如上级组织选择器）与操作列。

## Risks / Trade-offs

- **[风险] `${columnName}` MyBatis 动态列名拼接存在潜在注入面** → `column_name` 只能来自迁移写入 `tab_metadata_field` 的目录记录，接口层面不提供新增/修改该列的能力，业务模块的唯一性查询方法也只接受从这份目录解析出来的值，双重防护。
- **[风险] 承重字段跳过数据驱动校验，字段定义里配置的必填/正则/唯一对 `name`/`code` 看起来生效但实际不生效** → 表单管理页面对锁定字段在这些属性旁展示"该字段的必填/唯一性由系统强制保证，此处配置仅影响展示提示"的说明文案。
- **[风险] "锁定"判定是代码里维护的白名单，新增一个承重字段需要改代码而不是改配置** → 这是有意为之的取舍（见 Decision 4），承重字段本身就是极少变化的、由现有硬编码校验决定的字段，不属于高频配置场景。
- **[风险] 元数据目录不支持前端新增列，新增一个可开放配置的原有字段仍需要一次数据库迁移** → 相比"新增一个可展示的原有字段"的低频程度，以及避免管理员误配置指向不存在物理列的风险，这个取舍是合理的；`ext1`~`ext10` 依然保持"零迁移、纯配置"的能力，覆盖了绝大多数"业务方要加个字段"的场景。
- **[风险] 后端正则（Java `Pattern`）与前端正则（JS `RegExp`）语法存在细微差异** → 表单管理页面提示"请使用 Java 与 JavaScript 都兼容的基础正则语法"，提供"测试"按钮用当前浏览器 JS 正则即时验证一个示例值。
- **[风险] 唯一性校验通过应用层而非数据库唯一索引实现，存在并发写入下的竞态窗口** → 与现有 `org.code` 唯一性校验的既有风险等级一致，本次不额外加强。

## Migration Plan

- `V17__add_ext_fields_to_user_position_app.sql`：为 `tab_user`、`tab_user_position`、`tab_app` 各补齐 `ext1`~`ext10`（`VARCHAR(255)`）。
- `V18__init_tab_metadata_field.sql`：建 `tab_metadata_field` 表。
- `V19__seed_metadata_field_catalog.sql`：写入 Decision 3 表格里列出的目录记录（四张表各自的可配置原有列 + 全部 `ext1`~`ext10`）。
- `V20__init_tab_form_field_definition.sql`：建 `tab_form_field_definition` 表。
- `V21__seed_form_field_core_definitions.sql`：写入 Decision 11 描述的默认字段定义（绑定到对应元数据字段）。
- `V22__seed_metadata_field_menu_resource_data.sql` + `V23__seed_form_field_menu_resource_data.sql`：在 `tab_menu` 里插入"元数据配置""表单管理"两个菜单及其按钮资源，参考 `V16__seed_dict_detail_menu_resource_data.sql` 的写法。
- 不改动 `tab_org`/`tab_user`/`tab_user_position`/`tab_app` 既有列，只新增列，无需数据迁移。
- 回滚：本变更新增的是独立新表 + 新页面 + 三张表新增的 `ext` 列，四个现有模块的改动均为"新增可选扩展字段透传 + 对既有原有字段追加动态渲染元数据"；即使动态渲染部分回滚，元数据目录与默认字段定义的种子数据也不影响业务表原有数据，可安全回滚。

## Open Questions

- 数字输入框（`control_type=2`）的取值范围/精度是否需要额外配置（如最大值、小数位数）？本次先按"数字框 = 前端用 `el-input-number` + 后端额外校验是否为合法数字字符串"处理，不单独加精度/范围配置项，超出范围可后续迭代。
