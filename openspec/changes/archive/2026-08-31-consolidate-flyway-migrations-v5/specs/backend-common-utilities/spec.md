## MODIFIED Requirements

### Requirement: Flyway 迁移目录保持单一基线
`backend/src/main/resources/db/migration/` 目录 SHALL 以一份反映当前最终数据库状态（全部建表语句 + 全部种子数据）的单一基线迁移文件（`V1__init_schema.sql`）作为起点，不 SHALL 无限堆积仅用于记录“某张表历史上是怎么一步步改过来的”的中间过程 `ALTER`/`UPDATE` 文件；基线文件 SHALL 直接体现字段和种子数据的最终形态，不保留转换前的中间列定义、存量回填步骤或后续修正语句。后续新的结构变更 SHALL 继续以递增版本号的增量迁移文件形式添加在基线之后，不 SHALL 修改已发布的基线文件本身；当增量迁移文件积累到影响可维护性的程度时，SHALL 允许重新合并出一份新的基线。

同一基线文件 SHALL 能在空的 MySQL 5.7 与 MySQL 8.0 schema 上执行，且执行后产生相同范围的 46 张业务表及种子数据；基线不得使用仅 MySQL 8.0 支持、MySQL 5.7 不支持的 CTE、窗口函数等语法。

#### Scenario: 新环境执行迁移只需应用基线文件
- **WHEN** 在一个全新、空的 MySQL 5.7 schema 上执行 `flyway migrate`
- **THEN** 系统只应用 `V1__init_schema.sql` 一个文件，即得到全部 46 张业务表结构与完整种子数据，无需再应用任何历史中间迁移文件

#### Scenario: MySQL 8.0 新环境执行同一基线文件
- **WHEN** 在一个全新、空的 MySQL 8.0 schema 上执行 `flyway migrate`
- **THEN** 系统应用与 MySQL 5.7 完全相同的 `V1__init_schema.sql`，并得到相同范围的 46 张业务表结构与完整种子数据

#### Scenario: 已执行过旧版本历史迁移的库需要清库重建
- **WHEN** 某个数据库已经执行过本次被合并、删除的旧 V1–V14 迁移链且其中的数据需要保留
- **THEN** 该数据库不得仅删除 `flyway_schema_history` 或直接换用新 V1，而必须继续使用与既有历史校验和匹配的旧迁移集合，或者经过单独设计和验证的数据迁移方案

#### Scenario: 可丢弃数据的环境重建后使用新基线
- **WHEN** 某个开发或测试环境确认全部数据可以丢弃，并需要从旧迁移链切换到新 V1
- **THEN** 部署人员先完整删除并重建目标 schema，再执行新 V1；仅清除 `flyway_schema_history` 而保留业务表不满足空库前置条件
