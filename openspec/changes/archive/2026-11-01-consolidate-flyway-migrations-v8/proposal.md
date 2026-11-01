## Why

上一次基线合并（consolidate-flyway-migrations-v7）之后，迁移目录又从单一 V1 累积为 V1、V9（ABAC 策略引擎）、V10（视频管理）共 3 个脚本。为保持项目约定的单一初始化脚本，需要把它们按执行顺序形成的最终数据库状态重新规整为一份 `V1__init_schema.sql`。

## What Changes

- **BREAKING**：将 V1、V9、V10 按版本顺序执行后的最终结构和种子数据合并为新的 `V1__init_schema.sql`，删除 `V9__add_abac_policy.sql`、`V10__add_video.sql`；已执行过旧迁移链的数据库（包括本地开发库 `rbac`，其 history 现为 V1/V9/V10）不能直接换用新基线，需保留旧发布物或在确认数据可丢弃后重建空 schema。
- 新 V1 直接定义 81 张业务表（原 76 张 + `tab_abac_policy`、`tab_abac_policy_resource`、`tab_abac_policy_user_attr`、`tab_abac_policy_time_window`、`tab_video`）。V9、V10 均只有建表与种子插入，没有 `ALTER`/`UPDATE`，表定义原样迁入。
- ABAC 管理菜单/6 个权限点、视频管理菜单/3 个权限点并入既有菜单与权限种子块，位于 SUPER_ADMIN 全量授权之前，由其天然覆盖，移除 V9/V10 中的分段补授语句。
- 保持同一脚本兼容 MySQL 5.7 与 MySQL 8.0。
- 更新 `backend-common-utilities` 权威 spec 中单基线的合并来源、表数量与权限数量；同步修正进行中 change `add-video-management` 中"视频迁移为 `V10__add_video.sql`"的描述（改为已并入 V1）。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `backend-common-utilities`：单基线范围从 76 张业务表更新为 81 张，合并来源更新为 V1、V9、V10，权限编码数量与 `权限资源.txt` 当前唯一编码集合对齐。

## Impact

- 数据库脚本：重写 `backend/src/main/resources/db/migration/V1__init_schema.sql`，删除 V9、V10。
- OpenSpec：修改 `openspec/specs/backend-common-utilities/spec.md`；更新 `openspec/changes/add-video-management` 中关于迁移文件的描述。
- 本地开发库：`rbac` 已执行 V1/V9/V10，换用新 V1 后 Flyway 会因 V1 校验和变化与 V9/V10 缺失而启动失败，需要确认数据可丢弃后删库重建（库中已上传视频的记录会丢失，磁盘上的视频文件不受影响）。
- 不修改 Java、前端、接口、依赖、`权限资源.txt` 编码集合。

## Implementation Result

- 迁移目录已收敛为单一 `V1__init_schema.sql`：原 V1 中就地插入 ABAC 4 张表（`tab_admin_org_scope` 之后）、`tab_video`（`tab_image` 之后），ABAC 与视频的菜单/权限点种子插入到 SUPER_ADMIN 全量授权之前，表定义与种子语句按行号原样搬入；移除 V9/V10 的分段补授。`V9__add_abac_policy.sql`（已跟踪）与 `V10__add_video.sql`（未提交）已删除。
- 静态检查：81 条 `CREATE TABLE`、0 条 `ALTER TABLE`、1 条 SUPER_ADMIN 全量授权语句；未发现 CTE、窗口函数、`JSON_TABLE`、厂商专属 upsert；权限编码集合与 `权限资源.txt` 的 160 个唯一编码完全一致。
- MySQL 5.7.40 双路径：legacy（V1→V9→V10 只读副本）与新 V1 的 81 张表的表/列（类型、可空、默认值、注释、顺序）/索引元数据零差异；20 个非空种子表行数一致，按稳定业务键归一化后零差异；tab_menu 166、tab_permission 160、SUPER_ADMIN 授权 160，ABAC 菜单挂在 `permission`、视频菜单挂在 `common-service` 下，与旧链一致。
- 指向隔离空 schema 的后端全量测试 1495 项全部通过，`flyway_schema_history` 仅记录成功的 V1；全程未连接本地 `rbac` 库，其 history 仍为 V1/V9/V10，需用户删库重建后才能以新 V1 启动。
- MySQL 8.0 未验证（本地无 8.0 实例），tasks 4.2 保持未完成。
- 已同步 `add-video-management` change 中关于 `V10__add_video.sql` 的描述（标注已并入 V1）。
