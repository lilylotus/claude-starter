## MODIFIED Requirements

### Requirement: Flyway 迁移目录保持单一基线
`backend/src/main/resources/db/migration/` 目录 SHALL 以一份反映当前最终数据库状态（全部
建表语句 + 全部种子数据）的单一基线迁移文件（`V1__init_schema.sql`）作为起点，不 SHALL
无限堆积仅用于记录"某张表历史上是怎么一步步改过来的"的中间过程 `ALTER`/`UPDATE` 文件；
基线文件 SHALL 直接体现字段的最终形态（如已完成的字典编码转换），不保留转换前的中间列
定义或转换步骤。后续新的结构变更 SHALL 继续以递增版本号的增量迁移文件形式添加在基线之后，
不 SHALL 修改已发布的基线文件本身；当增量迁移文件积累到影响可维护性的程度时，SHALL 允许
仿照本次操作重新合并出一份新的基线。

#### Scenario: 新环境执行迁移只需应用基线文件
- **WHEN** 在一个全新、空的数据库上执行 `flyway migrate`
- **THEN** 系统只需应用 `V1__init_schema.sql` 一个文件即可得到包含全部 22 张业务表结构与
  种子数据的完整基线，无需再应用任何历史中间迁移文件

#### Scenario: 已执行过旧版本历史迁移的库需要清库重建
- **WHEN** 某个数据库此前已经执行过被合并、删除的旧版本迁移文件（如原 `V5__init_tab_user.sql`）
- **THEN** 该库需要先清空（或删除 `flyway_schema_history` 表）后重新执行新的
  `V1__init_schema.sql`，否则 Flyway 会因找不到对应版本号的历史文件而报错
