## Context

四处修复共享同一个背景：`POSITION`（任职）业务对象的字段渲染/保护体系目前对 `positionType`
（任职类型）做了特殊化处理，与其余三类业务对象（ORG/USER/APP）已经统一到的"表单字段定义"驱动
模型不一致；用户管理任职子表单与导入模板配置面板则各自存在局部实现缺陷。四处改动彼此独立、互不
依赖，可分别验证，但共享同一份 proposal 是因为都是"让现有动态渲染/保护体系覆盖到之前遗漏的角落"
这一类问题。

关键既有事实（调研阶段已确认）：
- `LockedFormFields.java` 用 `(bizType, columnName)` 常量白名单标记"承重字段"，`positionType`
  对应的 `tab_metadata_field` 记录 `column_name='position_type'`，与 `key(FormFieldBizType.POSITION,
  "position_type")` 的写法完全对应；`tab_user_position.position_type` 列本身在 DB 层就是
  `NOT NULL`，且创建接口已把 `positionType` 列为硬编码必填项（`spec: 任职记录的创建`），符合
  `LockedFormFields.java` 类注释里"已有后端硬编码 `@NotBlank` 与业务规则"的承重字段判定标准。
- `GET /api/form-fields/render-schema?bizType=POSITION` 已经对 `controlType` 为字典单选/多选的
  定义内嵌 `dictOptions`（来自绑定的 `dict_type_code`），`positionType` 的表单字段定义已配置为
  字典单选控件、绑定 `position_type` 字典类型 —— 即动态渲染链路所需的数据后端已经具备，不需要为
  此新增接口或字段。
- `useDynamicFormFields` 组合式函数的 `buildFormModel`/`buildRules`/`dictOptionLabel` 等逻辑对
  任意 `columnName` 一视同仁，不需要为 `positionType` 写特殊分支。
- `backend/src/main/resources/db/migration/V1__init_schema.sql` 第 620~623 行有一条注释，声称
  `positionType` 等字段"不出现在元数据字段目录中，继续保持硬编码渲染"，但紧随其后的实际
  `INSERT` 语句（第 670 行）已经把 `positionType` 写入了 `tab_metadata_field`，与注释矛盾——这条
  注释已经过时（不修改既有迁移脚本的历史 SQL 语句本身，但需要在新增迁移脚本或本次改动说明中不再
  依赖这条过时描述；不建议改写已应用过的历史 Flyway 脚本文件）。

## Goals / Non-Goals

**Goals:**
- `positionType` 的锁定保护、动态渲染与其余非硬编码字段完全对齐，删除所有为它单独编写的前端
  特殊分支代码。
- 用户管理任职子表单里，标签不换行、不错位的约束覆盖子表单内全部字段（不仅是当前两个必填字段）。
- 导入模板配置的"关联字段"下拉排除当前 `bizType` 下已被占用的表单字段定义，编辑态保留自身当前值。

**Non-Goals:**
- 不改动 `tab_user_position` 表结构或后端创建/更新接口对 `positionType` 必填性的校验逻辑（该约束
  继续由后端 DTO 校验承担，不因"是否锁定"而改变）。
- 不引入新的锁定字段管理界面或通用配置化机制——继续沿用现有的硬编码白名单 + `locked` 只读标记
  这一既定模式，不为此单独重构。
- 不修改历史 Flyway 迁移脚本文件本身（V1 等已应用脚本不可回改），仅在需要时新增脚本或更新脚本内
  过时注释文本（注释改动不影响已应用的 SQL 语义，允许直接编辑；但会改变 Flyway 用于校验的
  checksum，需要对已应用过该脚本的环境执行一次 repair——具体表现见 Risks / Trade-offs 与
  Migration Plan）。
- 不改造导入字段配置的分页接口（不新增"查询某 bizType 下全部占用字段 id"专用接口），复用既有
  分页接口配合较大 `pageSize` 的既定写法。

## Decisions

### 决策 1：`positionType` 加入承重字段白名单——纯后端常量新增
在 `LockedFormFields.LOCKED_KEYS` 追加 `key(FormFieldBizType.POSITION, "position_type")`。这是
纯常量新增，`FormFieldDefinitionServiceImpl.computeLocked()`、`LockedFormFieldException` 校验、
前端 `FormFieldDefinitionPanel.vue` 的 `locked` 渲染逻辑均无需改动——它们已经是通用实现，只是此前
没有一条 POSITION 记录命中白名单。
备选方案：改为数据库字段（如 `tab_form_field_definition.is_locked`）——放弃，因为会破坏"锁定关系
是代码既定事实而非可配置项"这一既有设计前提，且与仅有的三个先例（ORG/USER/APP 的 name/code）不
一致，改动范围不必要地扩大。

### 决策 2：任职类型改为完全并入动态渲染——删除独立硬编码分支，不新增后端接口
`PositionManagementView.vue`、`PositionDetailView.vue`、`UserManagementView.vue` 中，删除：
- 独立的 `positionTypeOptions` ref 与 `fetchPositionTypeOptions()`/翻译函数（`dict.ts` 的
  `getDictItemOptions('position_type')` 调用点随之移除，`dict.ts` 本身保留，供其他用途）；
- 独立的 `<el-form-item label="任职类型" ...>` 模板块与其单独的 `positionTypeRule`/校验规则；
- `PositionDetailView.vue` 中单独维护的 `positionTypeLabel()` 编码翻译函数，改为详情页扩展字段
  展示逻辑统一走 `dictOptionLabel`（该文件已有的动态字段展示复用同一套逻辑）。

`positionType` 由于已是 `bizType=POSITION` 下 `showInCreate=true`/`showInEdit=true`/
`showInList=true` 的启用状态定义，会自动出现在 `useDynamicFormFields('POSITION').createFields`/
`editFields`/`listColumns` 中，随其余字段一起渲染，不需要新代码专门"加入"它——只需要删除旧的排除
与硬编码渲染代码。

**实现落地记录（对下面这段原设计判断的更正）**：`PositionManagementView.vue` 新增/编辑表单里的
`positionAddress`/`positionPhone`/`showOrder`/`remark` 四个字段，实现时确认在本次改动之前就已经
由 `positionFields.createFields`/`editFields` 循环驱动渲染（并非本节原先估计的"手写的独立
`el-form-item`，未真正走 `positionFields` 循环"）——这是调研阶段对代码现状的误判，本次实际上
**不需要**额外把这四个字段纳入循环。实现范围收窄为：只需要把 `positionType` 一起并入这个已存在
的循环（继续排除 `orgId`、`userId`、`status` 三个继续硬编码渲染的字段，因为它们是选择器/状态
开关，本来就不属于表单字段定义体系），复用该组合式函数已有的 `buildFormModel`/`buildRules`/
`buildSubmitModel` 逻辑即可，不需要改造这四个字段本身。
`UserManagementView.vue` 中任职子表单是另一处需要改动的地方：改动前已经把 `ext*` 字段之外的动态
字段与 `positionType` 分开处理（`positionFields.createFields/editFields` 只用于过滤出 `ext*`
前缀字段，`positionType` 反而没有走这条渲染路径），需要把过滤条件从"只取 `ext*` 前缀"放宽为"取
全部非核心字段"，让 `positionType` 与地址/电话/显示序号/备注/`ext*` 一样统一走同一个 `v-for`
循环渲染。
后端 `render-schema` 已经返回 `positionType` 的 `dictOptions`，且是"读取 `bizType=POSITION` 下
启用状态定义"这一既有查询逻辑的自然结果，不需要后端改动即可避免重复渲染——删除前端硬编码块本身
就消除了"同一字段渲染两次"的风险，无需额外去重逻辑。

**实现落地记录（顺带修复的既有 bug）**：实现过程中发现 `PositionManagementView.vue` 列表页此前
同时存在两处渲染"任职类型"列——一处是硬编码的独立 `<el-table-column label="任职类型">`，另一处是
`positionFields.listColumns` 动态渲染循环（因为 `positionType` 字段定义的 `showInList=true` 本来
就会被这个循环自然包含），导致该列在列表页被重复渲染两次。这是一个在本次改动之前就存在、与
`positionType` 是否"完全并入动态渲染"这一目标本身直接相关的既有 bug，本次删除独立硬编码分支时
一并移除了那个多余的硬编码列。

### 决策 3：任职子表单标签布局——统一改为 `label-width="auto"` + 禁止换行，不做逐字段精确测宽
`UserManagementView.vue` 的 `.user-position-row__fields` 内，把所有 `el-form-item` 的
`label-width` 硬编码像素值（`90px`/`76px`）统一改为 `label-width="auto"`（Element Plus 原生
支持，按标签实际文本宽度自适应），并新增一条 scoped 样式：
```scss
.user-position-row :deep(.el-form-item__label) {
  white-space: nowrap;
}
```
防止任意长度的展示名称在自适应宽度下仍然换行（`auto` 只解决"宽度不够"，不解决"内容超长时是否
换行"，两者需要一起处理）。不做"按最长展示名称动态计算列宽对齐"这类更复杂方案——2 列 CSS Grid
布局下，每个 `el-form-item` 是独立的 flex 容器（标签+控件左右排列），标签宽度只影响同一个
表单项内部控件的可用宽度，不会跨列互相挤压；用超长展示名称（如 8~10 个汉字）压缩同格内控件宽度
是可接受的降级效果，优于当前会导致换行错位的固定像素方案。
备选方案：给 `label-width` 设一个更大的固定值（如 120px）——放弃，因为展示名称来自管理员在表单
管理页面自由填写的 `fieldName`，任何固定值都存在被更长文本打破的可能，`auto` 才是根治方案。

### 决策 4：导入模板配置"关联字段"下拉过滤——前端过滤，复用现有分页接口的大 `pageSize` 写法
不新增后端接口。`openCreateDialog()`/`openEditDialog()` 触发 `fetchAvailableFormFields()` 时，
额外调用一次 `importFieldConfigApi.getImportFieldConfigPage({ bizType, page: 1, pageSize: 200 })`
取回当前 `bizType` 下全部有效（未逻辑删除）导入字段配置，收集其 `formFieldDefinitionId`（过滤掉
`null`，因为 POSITION/APP/ORG 的固定标识列 `formFieldDefinitionId` 为 `null`，不占用任何表单
字段定义名额）为一个 `occupiedIds` 集合；`availableFormFields` 在原有"状态为启用"过滤基础上，
再排除 `occupiedIds` 中的项；编辑态（`openEditDialog`）额外把 `row.formFieldDefinitionId`（若
非空）从 `occupiedIds` 中剔除，保证选择器仍能展示并选中该配置当前关联的字段。
`pageSize: 200` 与既有 `fetchAvailableFormFields()` 的写法保持一致（该文件第 71 行注释已说明
"表单字段定义列表接口不支持按状态过滤，用较大的 pageSize 取回该 bizType 下全部未删除定义"这一
既定假设：单个业务对象类型下的表单字段定义/导入配置数量有上限（原有列 + 10 个 ext 字段 +
少量固定标识列），不会超过 200 条，直接复用同一假设，不引入新的分页遍历逻辑。

## Risks / Trade-offs

- [风险，已在实现阶段验证并处理] 编辑 `V1__init_schema.sql` 里的注释文字（不改动任何 SQL 语句
  本身）仍然会改变 Flyway 用于校验迁移脚本完整性的 CRC32 checksum——Flyway 的 checksum 计算基于
  整个脚本文件内容，不止对 SQL 语句敏感。实现落地时，本机已经应用过 V1 的本地开发数据库
  （`127.0.0.1:3306/rbac`）因此触发了 `FlywayValidateException`；通过临时编写一个调用
  `Flyway.configure()...load().repair()` 的 JUnit 测试类跑完 repair 后随即删除该临时测试类，才让
  `./gradlew test` 恢复通过。**提醒**：任何其他已经应用过 V1 迁移的环境（团队成员本地库、CI、
  测试/生产 MySQL 实例）在合并本次改动后，同样需要执行一次等效的 `flyway repair`（或手动
  `UPDATE flyway_schema_history SET checksum=... WHERE version='1'`），否则应用会在启动时因
  checksum 校验不一致而报错拒绝启动。
- [风险] 决策 4 依赖"单个 `bizType` 下导入字段配置不超过 200 条"的假设，与代码里已有的同类假设
  一致，如果未来该假设被打破会同时影响两处而非仅此一处 → 缓解：不在本次改动中引入新假设，维持
  现状一致性；不在本提案范围内解决，如需要应作为单独的技术债处理（如后端提供"全量不分页"专用
  查询）。
- [风险] 决策 3 的 `label-width="auto"` 在展示名称极端长（如超过 10 个汉字）时会导致同一表单项
  内控件可用宽度被压缩到很窄，视觉不够美观 → 缓解：属于可接受的降级效果，优先保证不发生"文字
  与相邻元素重叠错位"这一更严重的问题；后续如需要可另行提出改为单列布局或增加行内换行的独立
  change。
- [风险] 决策 2 删除 `PositionDetailView.vue` 独立的 `positionTypeLabel()` 后，若该文件的扩展
  字段展示逻辑与 `positionType` 所需的展示逻辑存在细微差异（如 `positionType` 需要展示为必填
  字段但布局位置固定在前几列）→ 缓解：实现阶段需要通读该文件确认统一后的渲染循环在字段顺序/
  必填标记展示上与目前效果一致，如有位置差异以"随 `showOrder` 排列"的既有规则为准。

## Migration Plan

- 后端：新增一行常量（决策 1），无需数据库迁移脚本，无需重启外的额外操作；随正常发布流程上线
  即可，无数据回填需求。
- V1 迁移脚本注释文本更新（Context 最后一条、Non-Goals）：不新增迁移脚本，但会改变该脚本的
  Flyway checksum（见 Risks / Trade-offs）。本机开发库已在实现阶段 repair 过一次；其余任何已经
  应用过 V1 的环境（其他开发者本地库、CI、测试/生产实例）在拉取本次改动后都需要各自执行一次
  `flyway repair`（或等效的 checksum 手工更新），否则会在下次应用启动时报
  `FlywayValidateException`。建议随发布说明一并通知团队。
- 前端三处改动（决策 2/3/4）均为纯前端渲染逻辑调整，不涉及接口契约变更，可与后端改动一起发布，
  也可独立于后端先行发布（决策 2 依赖决策 1 已生效以确保"任职类型"在表单管理页面正确展示为
  系统保护，但即使决策 1 暂未上线，决策 2 的动态渲染改造本身也能独立工作，只是此时任职类型仍可
  被管理员误操作停用/删除，建议两者同批发布）。
- 回滚：任一决策均可独立回滚（各自改动的文件互不重叠），无数据兼容性问题。

## Open Questions

（无）
