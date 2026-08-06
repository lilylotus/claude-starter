## Why

所有业务表的 `create_by`/`update_by` 审计字段目前存的是操作人当时的 `tab_user.code`（账号编码）快照。
账号编码允许被修改（用户改名/改编码后 `tab_user.code` 会变），一旦变更，历史记录里旧的
`create_by`/`update_by` 字符串就再也关联不回真实用户——既查不到该用户当前姓名，也无法用它做
"某用户创建了哪些记录"这类反查。审计字段的职责是可靠地指向"当时是谁"，理应使用不随改名/改编码
变化的主键 `tab_user.id`，而不是易变的业务编码。

## What Changes

- **BREAKING**：`CurrentOperatorService.resolveCode(): String` 改为 `resolveUserId(): Long`，
  直接返回 `CurrentUserContext.getUserId()`（不再查库把 id 换算成账号编码），所有调用方
  （Admin/App/Dict/DictItem/ImportFieldConfig/FormFieldDefinition/Menu/MetadataField/
  OperationLogRecorder/Org/Permission/Role/Position/User 等约 14 处写操作服务）同步改为
  用解析出的用户 id 填充 `createBy`/`updateBy`。
- **BREAKING**：除 `tab_login_log` 外的全部 18 张业务表（`tab_org`/`tab_dict_type`/
  `tab_dict_item`/`tab_user`/`tab_user_position`/`tab_user_password`/`tab_app`/`tab_role`/
  `tab_role_permission`/`tab_permission`/`tab_menu`/`tab_admin`/`tab_admin_role`/
  `tab_admin_org_scope`/`tab_operation_log`/`tab_metadata_field`/`tab_form_field_definition`/
  `tab_import_field_config`）的 `create_by`/`update_by` **列类型保持 `VARCHAR(64)` 不变**，
  存储内容从"账号编码"改为"用户 id 的字符串形式"（如 `"1001"`），对应的 entity 字段也保持
  `String` 类型不变；种子数据里原来的 `'system'`/`'admin'` 占位字符串改为种子超级管理员
  用户自身 id 的字符串形式。`tab_login_log` 的 `create_by`/`update_by` 语义特殊（记录的是
  登录尝试提交的账号文本，不是常规审计含义，且已有独立 `userId` 字段关联真实用户），本次
  明确排除、保持现状不变。
- 新增一个跨模块复用的用户展示名批量解析能力（放在 `user` 模块），供各业务模块的详情/列表
  查询按 `create_by`/`update_by` 的用户 id 批量回填人可读展示名。
- 涉及的约 15 个模块的详情/列表 VO（`OrgVO`/`UserVO`/`RoleVO`/`AdminVO`/`AppVO`/`MenuVO`/
  `PermissionVO`/`DictTypeVO`/`DictItemVO`/`PositionVO`/`UserPositionVO`/`MetadataFieldVO`/
  `FormFieldDefinitionVO`/`ImportFieldConfigVO`/`OperationLogVO`/`OperationLogDetailVO`）
  的 `createBy`/`updateBy` 字段保持 `String` 类型和字段名不变，语义从"账号编码"改为"解析后的
  人可读展示名"（如 `姓名（账号编码）`），前端约 15 个详情/列表页面无需改动展示逻辑。
- 操作日志模块的"操作人"查询条件（`OperationLogQueryRequest.createBy`，目前是对
  `tab_operation_log.create_by` 的精确文本匹配）改为：先按输入文本在 `tab_user` 里匹配
  账号编码或姓名解析出候选用户 id，再按 id 过滤 `tab_operation_log`；前端搜索框输入/交互
  方式不变。

## Capabilities

### New Capabilities
（无——不引入新的业务能力，是既有审计字段存储形式的调整。）

### Modified Capabilities
- `backend-common-utilities`：`CurrentOperatorService` 的职责从"解析当前登录操作人账号编码"
  改为"解析当前登录操作人用户 id"，写操作审计字段的取值语义随之从账号编码变为用户 id。

## Impact

- **数据库**：新增一份增量 Flyway 迁移文件（当前迁移目录只有一份已合并基线
  `V1__init_schema.sql`，遵循项目既有的"基线 + 增量，定期再合并"约定），只更新 18 张表
  种子数据里 `create_by`/`update_by` 的取值（不改列类型，仍是 `VARCHAR(64)`）；
  `flyway_schema_history` 已执行过基线的库会自动按新版本号应用这份增量。
- **后端代码**：`CurrentOperatorService`/`Impl`、约 14 个模块的 service 写操作调用点
  （写入时把解析出的用户 id 转成字符串）、12 个 MapStruct Convert（`toVO` 系列需要新增
  `ignore` + 服务层手动回填展示名）、15 个模块的 service 查询方法（批量回填展示名）、
  操作日志的查询 Mapper/XML 与 Service（操作人过滤条件改为先按账号编码/姓名查 id 再过滤）。
  entity 字段类型不变（仍是 `String`），无需改动。
- **前端代码**：预期无需改动（VO 字段名/类型不变，只是展示内容从账号编码变为"姓名（账号编码）"
  这样的展示名）；如实现过程中发现个别页面对 `createBy`/`updateBy` 做了额外处理（如直接当账号
  编码使用而非纯展示），需要单独确认调整。
- **测试**：`CurrentOperatorServiceImplTest` 及约 14 个受影响 service 的现有测试用例中对
  `createBy`/`updateBy` 断言的地方需要同步改为按用户 id 断言。
