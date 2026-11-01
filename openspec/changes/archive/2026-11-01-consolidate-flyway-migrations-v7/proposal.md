## Why

上一次基线合并（consolidate-flyway-migrations-v6）之后，`backend/src/main/resources/db/migration/` 又从单一 V1 累积为 V1–V8 共 8 个脚本，包含流程定义路由字段、文件上传表、业务绑定软删除、审批历史权限、单聊端到端加密、图片管理等多轮建表、字段增改和权限种子补授。为保持项目约定的可审核单基线，需要把这些脚本按执行顺序形成的最终数据库状态重新规整为一份初始化脚本。

## What Changes

- **BREAKING**：将现有 V1–V8 按版本顺序执行后的最终结构和种子数据合并回新的 `V1__init_schema.sql`，删除 V2–V8 共 7 个增量脚本；已经执行过旧迁移链的数据库不能直接换用新基线，必须继续保留旧发布物或在确认数据可丢弃后重建空 schema。
- 新 V1 直接定义 76 张业务表（原 73 张 + `tab_file_upload`、`tab_chat_user_key`、`tab_image`）的最终结构：
  - `tab_wf_process_definition` 直接包含 `route_field_codes`（V2）；
  - `tab_wf_process_binding` 直接使用 `status` 三态字段替代 `enabled`，维度索引由 UNIQUE 改为普通索引 `idx_tab_wf_process_binding_dimension`（V4）；
  - `tab_chat_message` 直接包含 `sender_identity_key_fingerprint`（V7）。
- 种子数据直接表达最终状态：全局流程绑定直接写 `status = 2000`；`WorkflowDesign:binding:delete`、`ApprovalManagement:record:view`、"公共服务"分组及 4 个图片权限点并入既有菜单/权限种子块，位于 SUPER_ADMIN 全量授权之前，由该授权天然覆盖，移除 V5/V6/V8 的分段补授。
- 保持同一脚本兼容 MySQL 5.7 与 MySQL 8.0，不引入 CTE、窗口函数、`JSON_TABLE` 或厂商专属 upsert 等写法。
- 更新 `backend-common-utilities` 权威 spec 中单基线的迁移来源、表数量和权限数量。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `backend-common-utilities`：单基线迁移的范围从 73 张业务表更新为 76 张，合并来源更新为 V1–V8，权限编码数量与 `权限资源.txt` 当前唯一编码集合对齐。

## Impact

- 数据库脚本：重写 `backend/src/main/resources/db/migration/V1__init_schema.sql`，删除 V2–V8 共 7 个增量文件。
- OpenSpec：修改 `openspec/specs/backend-common-utilities/spec.md` 中"Flyway 迁移目录保持单一基线"的要求。
- 部署：新建或可重建环境使用新 V1；已执行过 V1–V8 且需保留数据的环境不得通过删除 `flyway_schema_history`、`repair` 或直接替换脚本来绕过校验。
- 验证：在隔离的 MySQL 空 schema 中对比旧迁移链与新 V1 的结构和稳定业务键种子数据，并运行后端上下文测试。
- 不修改 Java、前端、接口、依赖、Flowable 自带表或 `权限资源.txt` 的编码集合；权限数据只改变其 Flyway 脚本组织位置。

## Implementation Result

- 迁移目录已收敛为单一 `V1__init_schema.sql`，V2–V8 共 7 个增量文件已删除；新 V1 包含 76 条 `CREATE TABLE`、0 条 `ALTER TABLE`。
- 在隔离的 MySQL 5.7.40 上，legacy V1–V8 链与新 V1 的 76 张表的列（类型/可空/默认值/注释/顺序）、索引元数据比较零差异；20 个非空种子表行数一致，按稳定业务键归一化（菜单父子 code、角色—权限 code、绑定维度—流程定义、模型当前定义、字典类型—字典项）后零差异。
- 权限点实际为 151 个（原基线 145 个 + 本次 6 个），与 `权限资源.txt` 的 151 个唯一编码（含 5 个四段式编码）完全一致，SUPER_ADMIN 拥有全部 151 条授权；起草时写的"146 个"是漏算四段式编码导致的统计错误，已在 spec delta 中更正为 151。
- 指向隔离空 schema 的 `RbacApplicationTests`（3 项）通过，`flyway_schema_history` 仅记录成功的 V1。
- MySQL 8.0 双路径验证未执行（本地无 8.0 实例）；新 V1 仅使用两版本共同语法，静态扫描未发现 CTE、窗口函数、`JSON_TABLE` 或厂商专属 upsert。
- 未运行后端全量测试（上一轮合并已记录 23 项与 SQL 合并无关的既有工作流集成测试失败）。
