## Why

自上一次基线合并后，`backend/src/main/resources/db/migration/` 又从单一 `V1` 累积到 `V14`，其中包含多轮建表、字段增改、存量回填、种子数据补授和后续修正，已影响新环境建库脚本的可读性与审核效率。需要延续项目既有的单基线约定，把 V1–V14 的最终状态重新规整为一个脚本。

## What Changes

- **BREAKING**：把现有 `V1__init_schema.sql` 至 `V14__add_dict_item_version.sql` 按版本顺序执行后的最终数据库状态，合并回新的单一 `V1__init_schema.sql`，并删除 V2–V14；已经执行过旧迁移链的数据库不能直接使用新基线，必须继续保留旧发布物或在确认可丢弃数据后重建空 schema。
- 新基线直接定义 46 张业务表的最终结构，不保留只服务于历史演进的 `ALTER`、`UPDATE`、菜单层级修正和字段回填过程。
- 将 V2–V14 新增或修正的全部种子数据并入 V1，包括 SSO 日志、插件管理、Excel 导出、审批管理以及应用同步变更流水相关内容；超级管理员基线授权一次性覆盖全部 127 个权限点。
- 对有历史生命周期的结构直接表达最终形态，包括审批开关默认关闭、组织路径字段、同步实体版本、通知任务状态与可空通知结果、字典项版本等。
- 新基线继续使用 MySQL 5.7 可用的保守 SQL，同时要求同一脚本兼容 MySQL 8.0；不引入 CTE、窗口函数或仅适用于 MySQL 8.0 的语法。
- 同步更新 `backend-common-utilities` 权威 spec 中单基线的表数量和旧库处理要求，纠正“仅删除 `flyway_schema_history` 即可重跑基线”的不安全表述。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `backend-common-utilities`：单基线迁移的最终表数量从 40 更新为 46，明确新基线必须在空 schema 上执行，并要求同一 V1 同时兼容 MySQL 5.7 与 MySQL 8.0。

## Impact

- 数据库脚本：重写 `backend/src/main/resources/db/migration/V1__init_schema.sql`，删除 V2–V14 共 13 个增量文件。
- OpenSpec：修改 `openspec/specs/backend-common-utilities/spec.md` 中“Flyway 迁移目录保持单一基线”的要求。
- 部署：新建或可重建环境使用新 V1；已有数据环境不得直接替换迁移历史，需继续使用与其历史校验和匹配的旧迁移集合。
- 不修改 Java、前端、接口、依赖版本、Flowable 初始化方案或权限资源编码清单；现有权限编码只改变其 Flyway 种子的组织位置。

## Implementation Result

- 迁移目录已收敛为单一 `V1__init_schema.sql`，V2–V14 共 13 个历史增量文件已删除。
- 新 V1 在隔离的 MySQL 5.7.44 和 MySQL 8.0.46 上均执行成功；与旧 V1–V14 链相比，表、列、索引、约束和按稳定业务键归一化的种子数据均无语义差异。
- 两个数据库版本均为 46 张业务表、127 个权限点和 127 条超级管理员授权；授权关联表只存在无语义的自增主键顺序差异。
- 指向隔离 MySQL 8.0 schema 的 `RbacApplicationTests` 和后端全量测试均通过，`flyway_schema_history` 仅记录成功的 V1。
