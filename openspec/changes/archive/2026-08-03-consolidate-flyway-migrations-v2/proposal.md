## Why

`backend/src/main/resources/db/migration/` 之前已经在 `2026-07-24-consolidate-flyway-migrations`
change 里把当时的 34 个增量文件合并成过一份 `V1__init_schema.sql` 基线。此后陆续又新增了
`V2`~`V9` 共 8 个增量文件（用户密码表、默认管理员账号、重置密码按钮、角色权限关联表、94 条
权限点种子数据+超级管理员角色、补齐导入相关菜单按钮、登录日志能力、日志菜单重新分组），
目录里累计已有 9 个版本文件。`openspec/specs/backend-common-utilities/spec.md` 里"Flyway
迁移目录保持单一基线"这条需求已经明确允许"当增量迁移文件积累到影响可维护性的程度时，仿照
本次操作重新合并出一份新的基线"——本次就是照此约定，把 `V1~V9` 再次合并为一份反映最终状态
的单一基线文件。

## What Changes

- 新增（覆盖重写）`V1__init_schema.sql`，内容为按顺序应用原 `V1~V9` 全部迁移后的最终数据库
  状态：19 张表（`tab_org`/`tab_dict_type`/`tab_dict_item`/`tab_user`/`tab_user_position`/
  `tab_user_password`/`tab_app`/`tab_role`/`tab_role_permission`/`tab_permission`/`tab_menu`/
  `tab_admin`/`tab_admin_role`/`tab_admin_org_scope`/`tab_operation_log`/`tab_metadata_field`/
  `tab_form_field_definition`/`tab_import_field_config`/`tab_login_log`）的最终建表语句，以及
  全部种子数据（字典数据、默认管理员账号+密码、95 条权限点、超级管理员角色及其权限关联、
  管理员身份及角色关联、完整的 5 组一级菜单树含全部页面/按钮节点），不再保留任何中间过程
  INSERT/UPDATE 步骤——例如 `tab_menu` 里"重置密码"按钮（原 `V4`）、四个页面的导入相关按钮
  （原 `V7`）、登录日志菜单及其挂载到"日志管理"分组（原 `V8`+`V9`）、操作日志菜单挂载到
  "日志管理"分组（原 `V9`）均直接以最终形态出现在种子数据里，不再分步骤先插入再 UPDATE。
- 删除原 `V2__add_user_password_table.sql` 到 `V9__reorganize_log_menu.sql` 共 8 个迁移文件
  （git 历史仍可追溯，非破坏性删除）。
- 迁移目录最终只保留 `V1__init_schema.sql` 一个文件。

## Capabilities

### New Capabilities
（无——本次不引入新的业务能力。）

### Modified Capabilities
（无——`backend-common-utilities` spec 里"Flyway 迁移目录保持单一基线"这条需求本身已经
覆盖并预见了本次这种"再次合并"的操作，不需要修改需求文本本身。）

## Impact

- **后端代码**：无 Java 代码改动，仅 `backend/src/main/resources/db/migration/` 下的 SQL
  迁移文件（9 个文件合并为 1 个）。
- **本地/测试数据库**：任何已经执行过旧版 `V1~V9`（含 `V2`~`V9`）的数据库（本地开发库、CI
  用库）在拉取本次改动后，Flyway 会因为找不到旧版本号对应的历史文件、以及 `V1` 文件内容/
  checksum 变化而报错，需要先清空该库（或删除 `flyway_schema_history` 表）后重新执行新的
  `V1__init_schema.sql`。生产环境如已基于旧版本迁移过，需要额外的迁移策略（本次改动不涉及
  生产环境，不在范围内）。
- **风险**：纯迁移文件层面的重组，不改变最终 schema 与种子数据的语义；需要逐表、逐条种子
  数据与旧 9 个文件按顺序应用后的最终状态比对一致（尤其是 `tab_menu` 完整菜单树、`tab_permission`
  95 条权限点、`tab_role_permission` 超级管理员的全量权限关联），并通过 `./gradlew test`
  在清空后的本地开发库上验证 Flyway 迁移无报错、`contextLoads` 通过。
