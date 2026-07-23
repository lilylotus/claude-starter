## Context

`tab_user.gender` 目前是 `INT NOT NULL DEFAULT 0`（0=未知/1=男/2=女），由后端常量类
`cn.nihility.rbac.user.constant.UserGender` 与前端常量 `USER_GENDER_OPTIONS`
（`frontend/src/types/user.ts`）双重硬编码维护，创建/编辑表单里的性别选择器是
`UserManagementView.vue` 里的静态 `<el-select>`，不经过"表单字段定义"驱动的动态表单
体系（`useDynamicFormFields('USER')`）。这是历史遗留的设计——`tab_metadata_field` 的
种子迁移（V19）注释里明确写"已有专用交互控件的字段（parentId、orgId、userId、
positionType、ownerId、status、gender 等）不出现在此目录中，继续保持硬编码渲染"，
把 gender 和"选择器类"字段（如上级组织）归为一类，但实际上 gender 和"任职类型"
（`positionType`，已经是字典驱动，只是同样没接入动态表单体系）更相似：都是"取值范围
有限、需要能通过管理页面调整选项"的字段，不是"关联另一张表记录"的选择器。

批量导入引擎（`cn.nihility.rbac.excelimport.service.support.ImportRowExecutor`）在
`add-excel-import-export` 归档时确立的做法（design.md 决策 4）是：不经过 controller 的
`@Valid`，改为在 `bindProperties` 把 Excel 单元格值反射设置到新建的
CreateRequest/UpdateRequest 实例上之后，手动调用 `jakarta.validation.Validator` 触发
DTO 上的 Bean Validation 注解。`showOrder`（组织/人员/任职/应用四类 DTO 都有）与
（改造前的）`gender` 都带有硬编码的 `@NotNull`，这类校验独立于"表单字段定义"/"导入字段
配置"的 `isRequired` 开关之外，管理员在这两处配置界面把某列改成非必填，并不会影响 DTO
自身的 `@NotNull`。

## Goals / Non-Goals

**Goals:**
- 性别的可选项能通过"字典管理"页面调整，不需要改代码。
- 性别能像手机号、身份证号一样出现在"表单管理 - 导入模板配置"的字段选择器里，管理员可
  为人员批量导入配置这一列。
- 管理员把某个数值类型的字段（如显示序号）在导入字段配置或表单字段定义里标记为非必填后，
  批量导入时该列留空不再被拒绝。

**Non-Goals:**
- 不把"性别"字段迁移成完全自由的文本字段——它仍然是字典下拉（`control_type=DICT`），
  只是数据来源从 Java 常量改为字典表，选项本身默认还是"未知/男/女"三个，只是新增了
  "以后可以加选项"的能力，不代表本次要新增第四个选项。
- 不改造 `positionType`（任职类型）的渲染方式——它目前虽然也是硬编码渲染（不经过动态
  表单体系），但用户没有对它提出诉求，本次不顺带重构，避免范围蔓延。
- 不给字符串类型字段（备注、扩展字段）的批量导入引入"留空保留默认值"的语义——它们目前
  留空即显式清空的行为符合"导入即整行覆盖"的既有约定（`add-excel-import-export`
  design.md 已经确立的"导入 = 全量记录维护"原则），本次修复只解决数值类型属性因 Spring
  的隐式空字符串转 `null` 而意外触发 `@NotNull` 的问题，不改变字符串字段的既有语义。

## Decisions

### 1. 性别改造为普通的字典驱动可配置字段，比照手机号/身份证号纳入"元数据字段"体系

新增字典类型 `gender`（性别），种子数据比照 `position_type`（V4 迁移）的写法：

| 字典项编码 | 标签 | 显示序号 |
|---|---|---|
| `unknown` | 未知 | 1 |
| `male` | 男 | 2 |
| `female` | 女 | 3 |

`tab_user.gender` 列由 `INT NOT NULL DEFAULT 0` 改为
`VARCHAR(64) NOT NULL DEFAULT 'unknown'`，历史数据按原码值语义原地转换（`0→unknown`、
`1→male`、`2→female`），迁移分两步：先 `ALTER ... MODIFY COLUMN`（MySQL 会把已有的
`0`/`1`/`2` 原样转成字符串 `"0"`/`"1"`/`"2"`），再用三条 `UPDATE` 语句把这三个字符串
数字重新映射成字典编码。

新增一条"元数据字段"记录（`biz_type=USER`、`table_name=tab_user`、`column_name=gender`、
`field_code=gender`、`column_type=VARCHAR(64)`）与一条默认启用的"表单字段定义"记录
（`control_type=3` 字典下拉、`dict_type_id` 指向新的 `gender` 字典类型、
`is_required=1`，与迁移前的行为一致——只是现在这个"必填"是可以被管理员在"表单管理"
页面调整的配置项，不再是写死的 Java 注解），`show_order` 追加在人员现有字段序列的末尾
（`name=1,code=2,mobile=3,idCard=4,showOrder=5,remark=6` 之后，取 `7`），不重排已有
字段的 `show_order`——一是这些值是 `V21` 迁移已经写入的历史数据，本次不属于必须修改的
范围；二是管理员本来就可以在"表单管理"页面自行调整显示序号，不需要通过迁移脚本重排。

后端 `UserEntity.gender`/`UserCreateRequest.gender`/`UserUpdateRequest.gender`/
`UserVO.gender` 的类型从 `Integer` 改为 `String`；`UserCreateRequest`/
`UserUpdateRequest` 上原有的 `@NotNull(message = "性别不能为空")` 移除，改为
`@Size(max = 64, message = "性别长度不能超过 64 个字符")`——这与 `positionType`
（`@NotBlank` + `@Size(max=64)`，见 `PositionCreateRequest`）不完全一致，因为
`positionType` 是"离开这个字段任职记录就没有业务身份"的强制字段，语义上仍然应该保持
必填；而 `gender` 现在的"是否必填"完全交给"表单字段定义"的 `isRequired` 配置项决定
（与手机号、身份证号、扩展字段这些真正的动态字段一致，DTO 层不再有自己的独立必填规则），
两者不应该用同一套约束方式。`private String gender = "unknown";`
在两个 DTO 上都保留一个 Java 层默认值（比照 `showOrder = 0` 的既有做法），避免正常 API
调用方漏传该字段时落库出现空字符串这种不对应任何字典项的"脏值"。

`cn.nihility.rbac.user.constant.UserGender` 常量类整体删除；`UserServiceImpl` 里原来
调用 `genderLabel(Integer)` 把码值翻译成"男/女/未知"中文再放进操作日志快照的做法一并
移除，直接存入原始的字典编码字符串（`entity.getGender()`）——这与 `positionType`
现有的操作日志快照写法完全一致（`PositionLogSnapshotSupport` 里
`snapshot.put("任职类型", entity.getPositionType())`，同样是直接存编码，不做中文翻译），
不新增字典编码到标签的查询逻辑。

前端：`UserManagementView.vue`/`UserDetailView.vue` 里性别相关的硬编码（`USER_GENDER_*`
系列常量的使用、`genderLabel` 函数、性别专属的静态 `<el-select>`、`form.gender` 静态
初始值/静态回填赋值）全部移除，`resetDynamicKeys(form, ['gender', 'positions'])` 改为
`resetDynamicKeys(form, ['positions'])`（`positions` 仍然是唯一的真正静态字段，因为它
是任职子表单数组，不是简单标量，不适合并入动态表单体系；这一点本次不改动）。

两个视图对"动态渲染"的接入程度不同，分别说明：

- `UserManagementView.vue`（列表 + 新增/编辑表单）：性别真正接入
  `useDynamicFormFields('USER')` 驱动的 `v-for` 渲染循环——列表列来自
  `userFields.listColumns`，表单项来自 `userFields.createFields`/`editFields`，与
  手机号、身份证号等字段完全走同一套 `v-for` 逻辑（校验规则、字典选项标签均由该
  组合式函数生成，见其 `buildFormModel`/`dictOptionLabel` 实现）。
- `UserDetailView.vue`（详情页）：手机号、身份证号等原有表字段本来就不是通过
  `v-for` 遍历 schema 渲染的——详情页只有 `ext1`~`ext10` 走 `v-for`（`positionExtFieldValue`
  等），其余固有字段（姓名、编号、性别、手机号、身份证号、状态等）都是手写的
  `<el-descriptions-item>`。本次改动没有把性别并入 `v-for` 循环，而是保持手写结构
  不变，只是把取值方式从静态查表的 `genderLabel()` 换成
  `userFields.dictOptionLabel(genderField, detailData.gender)`（`genderField` 为
  `userFields.schema` 中 `columnName === 'gender'` 的那条定义）——与手机号、身份证号
  "一致的展示方式"指的是"同样手写结构、但性别的取值改走字典标签查找"，不是指详情页
  被改造成了 `v-for` 循环。

本次不需要改动 `useDynamicFormFields` 组合式函数本身，它已经是通用的、不针对某个
具体字段特化。`types/user.ts` 里的 `USER_GENDER_UNKNOWN`/`USER_GENDER_MALE`/
`USER_GENDER_FEMALE`/`USER_GENDER_OPTIONS` 全部删除；`UserRow`/`UserFormRequest`
（详情页复用 `UserRow` 类型，未单独定义 `UserDetail`）上的 `gender` 字段类型由
`number` 改为 `string`。

被拒绝的替代方案：只解决"导入配置选不到性别"这一个症状（比如给导入字段配置单开一个
"伪字段"特例，类似 ORG 的 `__parentCode`），不改变性别本身的渲染与存储方式。这治标不
治本——用户明确要求的是"性别选项本身要能通过字典管理，方便后面修改"，伪字段方案完全
无法满足这一点，而且性别不像 `__parentCode` 那样需要"查另一张表反查 id"的特殊解析
逻辑，本质上就是一个普通的字典下拉字段，纳入现有的元数据字段/表单字段定义体系是更
匹配问题本质的做法。

### 2. 批量导入绑定字段值时，数值类型属性在单元格留空时跳过设置而非强制置空

现状：`ImportRowExecutor.bindProperties` 对 `rowValues` 里的每一项都无条件调用
`wrapper.setPropertyValue(fieldCode, entry.getValue())`，单元格留空时 `entry.getValue()`
是空字符串 `""`。Spring 的 `BeanWrapperImpl` 在把 `""` 转换成非 `String` 目标类型
（如 `Integer`）时，遵循"空字符串按 `null` 处理"的默认类型转换规则，于是新建的
CreateRequest/UpdateRequest 实例上，字段本身声明的 Java 默认值（如
`private Integer showOrder = 0;`）被这次显式赋值覆盖成 `null`，随后
`ImportRowExecutor.validateRequest` 手动触发的 Bean Validation 因为 `@NotNull` 判定
该行失败——即使管理员已经通过"导入字段配置"或"表单字段定义"把这一列标记为非必填。
`checkRequiredColumns`（在 `bindProperties` 之前执行）已经正确地依据配置判断"必填但
留空"的情况并提前拒绝；这个 bug 出现在配置为**非必填**、单元格留空、但字段本身在 DTO
上还带着独立于配置之外的硬编码 `@NotNull` 这三个条件同时成立的场景。

修复：`bindProperties` 改为——单元格文本为空白（`!StringUtils.hasText(value)`）且目标
属性类型不是 `String`（`wrapper.getPropertyType(fieldCode) != String.class`）时，跳过
`setPropertyValue` 调用，保留请求对象自身的 Java 默认值；其余情况（非空值，或目标类型
就是 `String`）行为不变。这个修复统一放在 `bindProperties` 里，对组织/人员/任职/应用
四类业务对象、以及 `showOrder` 之外任何其他数值类型的原生列都自动生效，不需要在四个
`process*` 方法里分别处理。

为什么按"目标属性类型是否为 `String`"区分，而不是无条件跳过所有空白单元格：字符串类型
的字段（备注、扩展字段 1~10、手机号、身份证号等）留空时，管理员的意图很可能是"清空这个
字段的原值"（对应更新场景）——`add-excel-import-export` 已经确立"导入 = 整行覆盖"的
语义（design.md 决策 2 提到 `__parentCode` 留空即按整行覆盖语义处理），如果对字符串
字段也跳过设置，会导致"通过导入清空一个原本有值的备注"这个操作永远做不到（因为
`bindProperties` 每次都是在一个全新构造的 CreateRequest/UpdateRequest 实例上操作，
Java 默认值就是 `null`，"跳过设置"和"设置为 `null`"对字符串字段而言效果相同，但目前
的实现是显式设置成 `""` 而非 `null`——这一点保持不变，不属于本次修复范围）。数值类型
属性则不存在"清空"的合理语义（`showOrder`、`Integer`/`Long` 类型的原生列在这四个
业务对象里目前都有一个有意义的默认值，如 `showOrder` 默认 `0`），留空更合理的解读是
"不提供、用默认值"，而不是"清空成一个数据库里存不了的 null"。

被拒绝的替代方案一：移除 `showOrder` 等字段上的 `@NotNull` 注解。这会同时影响正常
的（非导入路径的）Controller `@Valid` 校验，改变普通新增/编辑接口在漏传该字段时的行为
（目前普通接口若干场景下依赖这个 `@NotNull` 兜底防止 `null` 落到数据库的 `NOT NULL`
列上），波及范围超出"修复导入场景下配置为非必填不生效"这个具体问题，予以拒绝。

被拒绝的替代方案二：在四个 `process*` 方法里分别对 `showOrder` 做特判（留空时手动
`request.setShowOrder(0)`）。能达到同样效果，但需要在四处重复几乎一样的代码，且未来
新增的数值类型原生列（如果有）还要记得同样特判；`bindProperties` 里按属性类型统一处理
是更通用、不需要为每个新字段重复踩坑的做法。

## Risks / Trade-offs

- **[风险] `tab_user.gender` 列类型变更（`INT` → `VARCHAR(64)`）是一次不可逆的破坏性
  schema 变更**——项目目前处于开发阶段（`backend/.editorconfig`、迁移历史均未显示生产
  发布迹象），历史数据量小、可通过同一迁移里的 `UPDATE` 语句原地重映射，风险可控；若
  未来该库已有生产数据，需要额外评估迁移窗口，不在本次范围内讨论。
- **[取舍] 新增的"性别"表单字段定义 `show_order` 追加在末尾而非插回原有的"第三列"
  视觉位置**——见决策 1，管理员可自行在"表单管理"页面调整，不通过迁移脚本重排历史数据。
- **[风险] `bindProperties` 的"数值类型留空跳过设置"修复是四类业务对象共用的通用改动**
  ——理论上会同时影响 ORG/APP/POSITION 的 `showOrder` 导入行为（此前这三类对象没有人
  报告过这个问题，可能是因为它们的 `showOrder` 列目前都还保持默认必填配置，没有人尝试
  把它设置成非必填）。这是期望内的副作用（修复本身就是通用 bug 修复，不是人员专属
  补丁），不额外为其他三类对象补充回归测试之外的专门验证。
