## Why

`backend/src/main/resources/db/migration/` 下累积了 34 个从 `V1__init_tab_org.sql` 到
`V34__convert_form_field_dict_type_id_to_code.sql` 的增量迁移文件，其中相当一部分是同一张表
在开发过程中反复 `ALTER`/`UPDATE`（如 `V2`/`V6`/`V17`/`V24`/`V25` 陆续给若干张表加列，
`V32`/`V34` 又把早期的原始值列转换成了字典编码列）。这些中间过程文件只在追溯"某个字段是
怎么演变出来的"时有价值，对新克隆仓库、跑一次 `flyway migrate` 就要应用 34 个文件才能拿到
当前基线的场景没有意义，维护成本（文件数量、跨文件排查某张表最终结构）已经超过其价值，
需要合并为一个反映最终状态的单一基线迁移。

## What Changes

- 新增 `V1__init_schema.sql`，内容为按顺序应用原 `V1~V34` 全部迁移后的最终数据库状态：
  16 张表（`tab_org`/`tab_dict_type`/`tab_dict_item`/`tab_user`/`tab_user_position`/`tab_app`/
  `tab_role`/`tab_permission`/`tab_menu`/`tab_admin`/`tab_admin_role`/`tab_admin_org_scope`/
  `tab_operation_log`/`tab_metadata_field`/`tab_form_field_definition`/`tab_import_field_config`）
  的最终建表语句，以及全部种子数据（字典数据、菜单资源数据、表单字段定义、导入字段配置等），
  不再保留任何中间 `ALTER`/`UPDATE` 步骤——例如 `tab_user.gender`、
  `tab_form_field_definition.dict_type_code` 直接以转换后的字典编码列形态出现，不再先建原始列
  再转换。
- 删除原 `V1__init_tab_org.sql` 到 `V34__convert_form_field_dict_type_id_to_code.sql` 共 34 个
  迁移文件（git 历史仍可追溯，非破坏性删除）。
- 迁移目录最终只保留 `V1__init_schema.sql` 一个文件。

## Capabilities

### New Capabilities
（无——本次不引入新的业务能力。）

### Modified Capabilities
- `backend-common-utilities`：补充一条关于 Flyway 迁移基线合并的需求，约束迁移目录应保持
  "合入既有基线、不无限堆积中间过程文件"的组织方式。

## Impact

- **后端代码**：无 Java 代码改动，仅 `backend/src/main/resources/db/migration/` 下的 SQL
  迁移文件。
- **本地/测试数据库**：任何已经执行过旧版 `V1~V34` 的数据库（本地开发库、CI 用库）在拉取本次
  改动后，Flyway 会因为找不到旧版本号对应的历史文件而报错，需要先清空该库或删除
  `flyway_schema_history` 表后重新执行新的 `V1__init_schema.sql`。生产环境如已基于旧版本迁移
  过，需要额外的迁移策略（本次改动不涉及生产环境，不在范围内）。
- **风险**：纯迁移文件层面的重组，不改变最终 schema 与种子数据的语义；已通过逐表、逐条种子
  数据与旧 34 个文件比对（含 `tab_menu` 86 行菜单资源数据的逐条 diff）确认一致，`gradlew
  compileJava` 通过。
