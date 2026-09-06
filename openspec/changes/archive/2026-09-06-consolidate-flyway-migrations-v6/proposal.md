## Why

自上一次基线合并后，`backend/src/main/resources/db/migration/` 已从单一 V1 再次累积为 V1–V14、V18–V20 共 17 个脚本，包含聊天、登录方式、用户角色规则、工作流引擎及生产级审批生命周期的多轮建表、字段增改和权限种子补授。为保持项目约定的可审核单基线，需要把这些脚本按执行顺序形成的最终数据库状态重新规整为一份初始化脚本。

## What Changes

- **BREAKING**：将现有 V1–V14、V18–V20 按版本顺序执行后的最终结构和种子数据合并回新的 `V1__init_schema.sql`，删除其余 16 个增量脚本；已经执行过旧迁移链的数据库不能直接换用新基线，必须继续保留旧发布物或在确认数据可丢弃后重建空 schema。
- 新 V1 直接定义 73 张业务表的最终结构，将后续新增列、索引、默认值和可空性放入对应 `CREATE TABLE`，不保留只服务于历史演进的 `ALTER` 或回填步骤。
- 将聊天敏感词、默认审批流程、流程业务绑定、菜单、权限点和超级管理员补授等最终种子状态合并到 V1；基线应覆盖 `权限资源.txt` 当前列出的 145 个权限编码。
- 保持同一脚本兼容 MySQL 5.7 与 MySQL 8.0，不引入 CTE、窗口函数、`JSON_TABLE` 或厂商专属 upsert 等不兼容写法。
- 更新 `backend-common-utilities` 权威 spec 中单基线的迁移范围、表数量和旧库处理要求。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `backend-common-utilities`：单基线迁移的最终范围从旧 V1 的 46 张业务表更新为当前迁移链终态的 73 张业务表，并明确被合并版本集合与空 schema 使用限制。

## Impact

- 数据库脚本：重写 `backend/src/main/resources/db/migration/V1__init_schema.sql`，删除 V2–V14、V18–V20 共 16 个增量文件。
- OpenSpec：修改 `openspec/specs/backend-common-utilities/spec.md` 中“Flyway 迁移目录保持单一基线”的要求。
- 部署：新建或可重建环境使用新 V1；已有数据环境不得通过删除 `flyway_schema_history`、`repair` 或直接替换脚本来绕过校验。
- 验证：在隔离的 MySQL 5.7 与 8.0 空 schema 中对比旧迁移链与新 V1 的结构和稳定业务键种子数据，并运行后端测试。
- 不修改 Java、前端、接口、依赖、Flowable 自带表或 `权限资源.txt` 的编码集合；权限数据只改变其 Flyway 脚本组织位置。

## Implementation Result

- 迁移目录已收敛为单一 `V1__init_schema.sql`，V2–V14、V18–V20 共 16 个历史增量文件已删除；新 V1 直接包含 73 张业务表、908 列、272 条索引元数据和 120 条约束元数据，不含 `ALTER TABLE`。
- 新 V1 在隔离的 MySQL 5.7.40 和 MySQL 8.0.46 上均执行成功；与原 17 文件迁移链相比，表、列、索引、约束和按稳定业务键归一化的 20 个非空种子表均无差异。
- 两个数据库版本均包含 145 个权限点、145 条 SUPER_ADMIN 授权和 20 条全局流程绑定；权限编码与 `权限资源.txt` 的 145 个唯一编码完全一致。
- 指向隔离 MySQL 8.0 schema 的 `RbacApplicationTests` 通过，`flyway_schema_history` 仅记录成功的 V1。
- 后端全量测试实际执行 1310 项，其中 23 项现有工作流集成测试失败；失败 SQL 使用 `processInstanceId`/`nodeId` 等驼峰列名查询下划线数据库字段，属于既有 MyBatis 实体字段映射问题。旧链与新基线的相关表结构完全一致，本 change 未扩大范围修改 Java 业务代码；用户在知悉该警告后要求同步并归档。
