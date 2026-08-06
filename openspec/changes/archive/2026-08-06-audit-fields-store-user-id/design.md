## Context

见 `proposal.md - Why`。当前实现细节（本次改造的起点）：

- `CurrentOperatorService.resolveCode()`（`auth/service/impl/CurrentOperatorServiceImpl.java`）
  内部已经是"取 `CurrentUserContext.getUserId()` → `userMapper.selectById` → 返回
  `user.getCode()`"，即已经拿到了用户 id，只是最后多一步把 id 换算成账号编码再返回。
- 除 `tab_login_log` 外，以下 18 张表都有 `create_by`/`update_by VARCHAR(64)` 列，值为账号编码：
  `tab_org`、`tab_dict_type`、`tab_dict_item`、`tab_user`、`tab_user_position`、
  `tab_user_password`、`tab_app`、`tab_role`、`tab_role_permission`、`tab_permission`、
  `tab_menu`、`tab_admin`、`tab_admin_role`、`tab_admin_org_scope`、`tab_operation_log`、
  `tab_metadata_field`、`tab_form_field_definition`、`tab_import_field_config`。其中
  `tab_role_permission`/`tab_admin_role`/`tab_admin_org_scope` 是纯关联表，没有对应的详情/
  列表 VO 直接展示这两个字段。**本次明确保留 `VARCHAR(64)` 列类型不变**（用户已确认，见
  本次对话记录），只改存入的内容——从账号编码文本改为用户 id 的字符串形式（如 `"1001"`），
  entity 字段也相应保持 `String` 类型，不做 `Long` 改造。
- 其余 15 张表对应的 VO（`OrgVO`/`UserVO`/`RoleVO`/`AdminVO`/`AppVO`/`MenuVO`/`PermissionVO`/
  `DictTypeVO`/`DictItemVO`/`PositionVO`/`UserPositionVO`/`MetadataFieldVO`/
  `FormFieldDefinitionVO`/`ImportFieldConfigVO`/`OperationLogVO`/`OperationLogDetailVO`，
  `Position`/`UserPosition` 两个 VO 都来自同一张 `tab_user_position`）目前的 `createBy`/
  `updateBy` 是 `String` 字段，由 MapStruct 按同名同类型属性自动从 entity 映射过来，前端
  约 15 个详情/列表页面（如 `OrgDetailView.vue`）直接把它当纯文本展示，没有把它当 id 做
  跳转/查询等其他用途（已用 `grep` 确认）。
- 已有先例：`OrgConvert.toVO` 对 `parentName` 用 `@Mapping(target = "parentName", ignore = true)`
  跳过自动映射，由 service 层（`OrgServiceImpl`）另行查询回填——本次 `createBy`/`updateBy`
  的展示名回填复用同一模式。
- 操作日志的"操作人"查询（`OperationLogQueryRequest.createBy` → `OperationLogMapper` 已有的
  自定义 XML `selectOperationLogPage`）目前是对 `tab_operation_log.create_by` 做精确文本匹配，
  前端是一个自由文本输入框（`OperationLogManagementView.vue`），用户输入账号编码精确查询。
- 迁移目录当前只有一份已合并基线 `V1__init_schema.sql`（`backend-common-utilities` spec 的
  "Flyway 迁移目录保持单一基线"要求：新变更以递增版本号的增量文件添加在基线之后，不修改
  已发布的基线本身，积累到一定程度再手工合并）。

## Goals / Non-Goals

**Goals:**
- 全部（`tab_login_log` 除外）业务表的 `create_by`/`update_by` 落库值改为 `tab_user.id`
  的字符串形式，列类型/entity 字段类型均保持 `VARCHAR(64)`/`String` 不变。
- 保证前端约 15 个详情/列表页面改造前后展示效果等价（仍是人可读文本），不需要改前端代码。
- 操作日志"按操作人搜索"的输入交互方式不变（仍是输入框输入账号编码/姓名文本）。
- 种子数据里的审计字段不再使用 `'system'`/`'admin'` 等脱离真实用户表的占位字符串。

**Non-Goals:**
- 不改变任何写操作的业务逻辑、字段校验规则、状态机。
- 不改变数据库列类型或 entity 字段的 Java 类型（用户已明确要求保留 `VARCHAR`/`String`）。
- 不给前端新增"查看创建人详情"之类的新交互（展示名只是文本，不做超链接/跳转）。
- 不处理历史生产数据迁移（项目当前无正式环境数据，`tab_user.code` 也未发生过真实变更，
  迁移时不需要"把旧账号编码字符串反查成 id"这类兜底逻辑——种子数据里的占位字符串直接
  替换为具体 id 的字符串即可，不需要通用的字符串转 id 兜底）。
- `tab_login_log`、以及经确认后如果发现的其他"非常规审计"表不在本次范围内。

## Decisions

### 1. `CurrentOperatorService` 直接返回 id，去掉查库换算

`resolveCode(): String` 改名为 `resolveUserId(): Long`，方法体简化为直接返回
`CurrentUserContext.getUserId()`（校验非 null，null 时仍抛 `IllegalStateException`），
不再注入 `UserMapper`、不再查库。

**理由**：换算成账号编码这一步本身就是本次要去掉的东西——上一次改造（commit `dbc3680`）
引入这个查库换算，是因为当时的目标是"账号编码"；本次目标直接是 id，`CurrentUserContext`
本来就已经是 id，不需要再多一次数据库往返。方法返回值仍用 `Long`（而不是直接返回
`String`），保持"当前操作人 id"这个概念在类型上的清晰；各写操作调用点在赋值给 entity 的
`createBy`/`updateBy`（`String` 字段）时用 `String.valueOf(currentOperatorService
.resolveUserId())` 转换。

**替代方案**：保留查库、只是校验一下 id 存在再返回——否决，因为徒增一次不必要的数据库
调用，且"当前登录用户 id 在数据库里查不到"这种情况已经不可能发生在正常请求路径上
（`IdentityAuthFilter` 校验时已经确认过用户存在）。`resolveUserId()` 直接返回 `String`
——否决，`Map<Long, String>` 形式的批量展示名解析（见 Decision 3）需要 `Long` 类型的 id
做查询键，让调用方两头各自转换类型不如让"当前操作人"这个概念保持 `Long`，只在落库这一个
点上转字符串。

### 2. VO 的 `createBy`/`updateBy` 字段名和类型不变，语义改为"解析后的展示名"

Entity 的 `createBy`/`updateBy` **保持 `String` 类型不变**（对应列保持 `VARCHAR(64)`），
但存储内容从"账号编码"改为"用户 id 的字符串形式"；对应的 15 个 VO 的 `createBy`/
`updateBy` 同样保持 `String` 类型和字段名不变，内容改为 `姓名（账号编码）` 形式的展示名
（如 `张三（ZS0001）`），由 service 层在查询完成后批量回填。虽然 entity 和 VO 字段类型
都是 `String`，MapStruct 的 `toVO`/`toVOList` 仍需要新增
`@Mapping(target = "createBy", ignore = true)` 与 `updateBy` 同理——不能让"用户 id 文本"
被自动映射（同名同类型会被直接复制）到 VO 上冒充展示名，必须手动跳过再由 service 赋值。

**理由**：前端已确认（见本次对话前置的澄清问答）希望详情/列表页面继续展示人可读文本而不是
裸 id；沿用原字段名/类型可以让前端**零改动**，改造范围完全收敛在后端。entity 字段类型保持
`String`（不改 `Long`）是用户本轮明确的要求，避免数据库列类型变更。

**替代方案**：新增 `createByName`/`updateByName` 字段、`createBy`/`updateBy` 直接暴露原始
id——否决，因为前端目前没有任何地方需要用到原始 id（已用 `grep` 确认所有相关页面都只是把
`createBy` 当文本展示），新增字段只会让改造面同时覆盖前端 15 个页面的联调，且未来如果真的
需要展示原始 id，再加字段也不迟。

**展示名解析失败时的规则**：`createBy`/`updateBy` 为空字符串/`null`（历史脏数据或允许为空
的场景）时展示为空字符串，与当前"字段为空则不显示"的前端行为保持一致；非空但内容无法解析
为合法用户 id、或解析出的 id 在 `tab_user` 里查不到（用户已被删除）时，显示固定文案
`"未知用户"`。

### 3. 新增一个跨模块的批量展示名解析能力

在 `user` 模块新增 `UserDisplayService`（或类似命名，最终类名由实现阶段确定），提供
`Map<String, String> resolveDisplayNames(Collection<String> userIdTexts)`——输入是
entity 里 `createBy`/`updateBy` 原样的字符串值，内部先过滤掉空白/无法解析为数字的值，把
剩余的转成 `Long` 后用 `userMapper.selectBatchIds` 一次查询批量取回 `tab_user.name`/
`code`，拼成 `姓名（账号编码）` 格式，返回时 key 仍用原始输入字符串（避免调用方在
拿到结果后还要做字符串⇄数字的往返转换）；查不到/无法解析的 id 不放入返回的 `Map`，调用方
按第 2 条的规则展示"未知用户"。各模块 service 的详情/列表方法在拿到 VO 之后，收集本页/
本条记录涉及的 `createBy`/`updateBy` 原始字符串（去重合并成一次查询），调用该服务批量
回填。

**理由**：15 个模块都有同样的"按 id 文本批量转展示名"需求，避免每个模块各自实现一遍、
避免逐行单独查询造成 N+1；输入输出都用 `String` 是为了直接对接 entity/VO 的字段类型，
不需要调用方各自处理 `Long`⇄`String` 转换。放在 `user` 模块是因为它直接依赖 `tab_user`
表，与 `UserMapper` 同处一处，符合"谁的数据谁负责查"的现有分层习惯（对照
`CurrentOperatorService` 放在 `auth` 模块但内部依赖 `UserMapper` 的既有先例）。

**替代方案**：每个模块的 Convert/Service 里各自手写批量查询——否决，15 处重复代码，且
展示名拼接规则（"未知用户"文案、`姓名（编码）`格式）一旦要调整需要改 15 处。输入输出用
`Long`——否决，entity/VO 字段本身是 `String`，调用方两头转换类型纯属多余的样板代码。

### 4. 操作日志"操作人"搜索：service 层先把文本解析成 id 列表，XML 按字符串比较

`OperationLogQueryRequest.createBy`（前端输入的文本）在 `OperationLogQueryServiceImpl.getPage`
里先用 `UserMapper` 按 `code = 输入文本 OR name = 输入文本` 查出候选用户 id 列表，若为空
直接返回空分页（不下发查询给 `operationLogMapper`）；若非空，把候选 id（转成字符串形式，
与 `create_by` 列的存储格式一致）列表传给 `OperationLogMapper.selectOperationLogPage` 的
XML，`WHERE` 条件从 `create_by = #{createBy}` 改为 `create_by IN <foreach>`（列类型/参数
类型不变，仍是字符串比较）。分页结果的展示名回填复用第 3 条的批量解析能力，与其余 14 个
模块保持一致，不在 XML 里额外 JOIN `tab_user`。

**理由**：现有 XML 查询结构改动最小（只改一个条件从单值等于变成多值 `IN`，列类型/参数
类型都不用变）；查询语义从"精确匹配账号编码"变成"精确匹配账号编码或姓名"，对使用者更
宽松而不是更严格，不构成体验倒退。

**替代方案**：在 XML 里直接 JOIN `tab_user` 做 `WHERE u.code = #{createBy} OR u.name =
#{createBy}`——否决，两种方式效果等价，但 service 层先查 id 列表更符合项目里"多表关联查询
放 XML，简单场景避免不必要 JOIN"的既有习惯，且能复用第 3 条已有的批量解析能力做展示名
回填，不用在这个模块单独处理。

### 5. 迁移文件：新增 `V2`，只更新种子数据取值，不改列类型

新增 `V2__audit_fields_use_user_id.sql`，**不做任何 `ALTER TABLE`**，只对 18 张表种子数据
里出现过占位字符串（如 `'system'`、`'admin'`，具体以 `V1` 种子数据实际值为准逐一核对，
不是所有表一定都用的是这两个值）的行执行 `UPDATE`，把 `create_by`/`update_by` 改为种子
超级管理员用户 `id` 的字符串形式（如 `UPDATE tab_xxx SET create_by = (SELECT
CAST(id AS CHAR) FROM tab_user WHERE code = 'admin' LIMIT 1), update_by = (...) WHERE
create_by IN ('system', 'admin')`）；`tab_user`/`tab_user_password` 里种子超级管理员账号
自己那一行，`create_by`/`update_by` 改为指向自己 `id` 的字符串（自引用——种子数据里"系统"
这个概念本身就是这个初始账号）。

**理由**：本轮已确认列类型保持 `VARCHAR(64)` 不变，迁移文件不再需要处理"改类型前必须先
迁移数据"的顺序风险，只是把种子数据里的占位字符串替换成另一个字符串（真实用户 id 的文本
形式），是纯内容层面的调整。延续项目"基线 + 递增迁移文件，定期人工合并回基线"的既有约定
（新增 `V2` 而不是直接改 `V1`），避免和已执行过 `V1` 的开发环境产生 Flyway 版本冲突。

## Risks / Trade-offs

- **[风险] 12 个 Convert + ~14 个 service 调用点，改动面广，遗漏某处未同步会导致 VO 上
  直接显示"用户 id 文本"而不是解析后的展示名（不会编译失败，只会运行时展示错误内容，
  不易第一时间发现）。** → 缓解：按表/模块拆分 `tasks.md`，每个模块一个独立可验证的任务项
  （convert ignore + service 回填 + 对应测试），依次实现并人工核对该模块页面的实际展示，
  而不是一次性大改再统一排查。
- **[风险] `createBy`/`updateBy` 存的是"数字的字符串形式"，本质上仍是弱类型——理论上不能
  阻止未来有代码往里写入非数字内容。** → 缓解：写入路径统一收敛到
  `CurrentOperatorService.resolveUserId()` + `String.valueOf(...)` 这一个模式，`tasks.md`
  按模块检查所有写操作调用点都走这条路径，不留旁路。
- **[风险] 批量展示名解析引入的额外查询可能在列表分页场景下增加一次 `SELECT ... WHERE id
  IN (...)`。** → 影响可接受：每页数据量有限（分页大小通常 ≤ 100），批量查询一次即可覆盖
  一整页涉及的所有 `createBy`/`updateBy` id，不是逐行查询。
- **[权衡] `createBy`/`updateBy` 展示名里拼接了账号编码（`姓名（编码）`），比单纯显示账号
  编码更长。** → 可接受：前端目前用固定宽度的表格列/描述项展示这两个字段，长度增加不影响
  可用性；具体格式如实现后发现不合适，可在不改变字段类型/语义的前提下单独调整拼接格式。

## Migration Plan

1. 先落地 `CurrentOperatorService`/`Impl` 改造（`resolveCode(): String` →
   `resolveUserId(): Long`）+ 对应单测，确认编译通过。
2. 新增 `user` 模块的批量展示名解析能力（`UserDisplayService`），先独立于其他模块落地并
   补单测。
3. 按模块逐个推进（写操作调用点改用 `String.valueOf(currentOperatorService
   .resolveUserId())` → convert 的 `toVO` 新增 `createBy`/`updateBy` 的 `ignore` →
   service 查询方法接入批量展示名回填 → 对应测试同步更新），每个模块改完即可编译通过、
   不依赖其他未完成模块（entity/VO 字段类型都不变，模块之间没有类型层面的耦合）。
4. 操作日志模块单独处理查询条件改造（Decision 4）。
5. 最后新增 `V2` 迁移文件（只含 `UPDATE`，无 `ALTER TABLE`），本地重新执行
   `flyway migrate`（或清库重跑 `V1`+`V2`）验证种子数据取值正确。
6. 无生产环境，不需要设计线上回滚方案；本地验证失败可直接改迁移文件重跑（`V2` 尚未随
   代码合并发布前允许直接修改）。
