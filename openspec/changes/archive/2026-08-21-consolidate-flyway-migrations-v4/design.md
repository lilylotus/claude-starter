## Context

现有 `V1__init_schema.sql` 已是 31 张表的基线（此前经历过若干次未以独立 OpenSpec change 记录的整合提交，如 `feat(数据库脚本): 初始化脚本整合`/`feat(初始化脚本): 初始化脚本整合`，本次不追溯核实那些提交是否完整覆盖了当时的中间态，只以当前仓库里实际的 `V1~V10` 文件内容为准）。本次要合并的范围是其后新增的 `V2`~`V10` 共 9 个增量文件：

- `V2__add_app_userinfo_field_mapping.sql`：新增 `tab_app_userinfo_field_mapping` 表（CAS/OAuth2.0 共用的用户信息响应字段映射）。
- `V3__add_app_auth_logout_notify_url.sql`：`tab_app_auth_config` 新增 `logout_notify_url` 列（单点登出回调地址）。
- `V4__unify_app_auth_service_patterns.sql`：新增 `service_patterns` 列并从 `cas_service_patterns`/`oauth2_redirect_uri_patterns` 迁移数据后删除这两个旧列（协议无关的回跳地址匹配规则存储统一）。
- `V5__add_app_sync_notify_pull_logs.sql`：`tab_app_notify_record` 新增 `data_type`/`biz_id`/`notify_url` 三列 + 复合索引 `idx_tab_app_notify_record_app_time`；新增 `tab_app_pull_record` 表。
- `V6__add_app_sync_master_switch.sql`：`tab_app_config` 新增 `sync_master_enabled` 列（应用级同步总开关）。
- `V7__drop_app_data_change_log.sql`：删除 `tab_app_data_change_log` 表；`tab_app_notify_record` 删除 `change_log_id` 列及其索引；`tab_app_pull_record` 删除 `pull_mode` 列。
- `V8__add_app_access_authorization.sql`：新增 6 张应用访问授权相关表：`tab_app_access_policy`、`tab_app_access_policy_org_scope`、`tab_app_access_policy_user_attr`、`tab_app_access_policy_target_app`、`tab_app_access_policy_grant`、`tab_app_access_manual_override`。
- `V9__add_app_access_authorization_menu.sql`：为应用访问授权能力追加 1 个页面菜单 + 8 个按钮资源、对应 9 条 `tab_permission` 种子，并授予超级管理员角色。
- `V10__add_app_access_policy_request_control.sql`：新增 2 张表：`tab_app_access_policy_browser_rule`、`tab_app_access_policy_ip_rule`（策略的浏览器/IP 白名单请求控制条件）。

这批增量里 `V2`/`V5`/`V8`/`V10` 是新建表，`V7` 是删表 + 两处列级调整，`V3`/`V5`/`V6` 是纯列级 `ALTER ADD COLUMN`，`V4` 是"加列回填再删列"的完整生命周期，`V9` 是纯种子数据（菜单 + 权限点 + 角色权限关联）——合并时要特别注意 `V4` 与 `V7` 这两条"先加、回填、再删"的链路必须体现最终形态，不保留中间列。

`openspec/specs/backend-common-utilities/spec.md` 的"Flyway 迁移目录保持单一基线"需求已经预见并允许本次操作，需求文本本身不需要改，但里面"22 张表"的具体数字描述是上一次记录在案的合并（v3，2026-08-13）时的状态，此后经历过未记录在案的整合与本次要合并的 9 个增量，实际数字早已漂移，本次一并更正为合并后的最终值。

## Goals / Non-Goals

**Goals:**
- 迁移目录重新收敛为一份反映当前最终 schema + 种子数据状态的基线文件 `V1__init_schema.sql`。
- 合并过程不引入任何语义差异：40 张表（现有 31 张 + 本次净增 9 张）的最终结构、全部种子数据（含 `tab_menu`/`tab_permission`/`tab_role_permission` 完整种子）都要和旧 `V1~V10` 按顺序执行后的最终状态完全一致。

**Non-Goals:**
- 不处理生产环境或任何已经执行过旧版本迁移的库的升级路径——本次改动假定使用方清空库后重新执行新基线（与前三次合并的既有约定一致）。
- 不改变任何业务代码、接口行为，纯粹是迁移文件的组织方式调整。
- 不追溯核实此前未记录在案的整合提交是否完整、准确，只以当前仓库里 `V1~V10` 的实际内容为合并依据。

## Decisions

### 1. 继续合并为单个 `V1__init_schema.sql`，版本号沿用 `V1`

延续前三次的决策：项目表之间存在种子数据的相互引用（`tab_menu` 自引用 `parent_id`、`tab_role_permission` 引用 `tab_role`/`tab_permission` 等），单文件按"先建表、后插入有依赖关系的种子数据"的顺序线性组织最清晰。版本号不接着旧编号延续为 `V11`，理由同前三次：`V1` 代表"从零开始建库的基线"，语义清晰；代价是旧版本号 `V1~V10` 不能再复用，任何还在用旧版本号跑过 migrate 的库必须清库重来。

### 2. `tab_app_auth_config` 直接建成"含 `service_patterns`/`logout_notify_url`，不含 `cas_service_patterns`/`oauth2_redirect_uri_patterns`"的最终形态

`V3` 新增 `logout_notify_url`，`V4` 新增 `service_patterns` 并回填数据后删除 `cas_service_patterns`/`oauth2_redirect_uri_patterns` 两个旧列。合并后的列顺序为 `id`/`app_id`/`auth_protocol`/`service_patterns`/`logout_notify_url`/`create_by`/`create_time`/`update_by`/`update_time`，不体现两个旧列以及"先加列再删列"的中间过程。

### 3. `tab_app_config` 直接建成含 `sync_master_enabled` 的最终形态

`V6` 在 `need_sign` 之后新增 `sync_master_enabled`（应用级同步总开关，默认 1）。合并后 `tab_app_config` 建表语句里 `sync_master_enabled` 紧跟 `need_sign` 之后、`sync_mode` 之前。

### 4. `tab_app_notify_record`/`tab_app_pull_record` 直接建成"下线变更记录表后"的最终形态，不再创建 `tab_app_data_change_log`

`V7` 删除了 `tab_app_data_change_log` 表本身，以及 `tab_app_notify_record.change_log_id`（含其索引）、`tab_app_pull_record.pull_mode`。合并后：
- 不再有 `tab_app_data_change_log` 建表语句。
- `tab_app_notify_record` 最终列为 `id`/`app_ref_id`/`data_type`/`biz_id`/`notify_status`/`http_status`/`error_msg`/`notify_url`/`create_by`/`create_time`/`update_by`/`update_time`，索引为 `PRIMARY KEY(id)`、`idx_tab_app_notify_record_app_ref_id(app_ref_id)`、`idx_tab_app_notify_record_app_time(app_ref_id, create_time)`（`change_log_id` 列与其索引不出现）。
- `tab_app_pull_record` 最终列为 `id`/`app_ref_id`/`data_type`/`request_summary`/`result_count`/`create_by`/`create_time`/`update_by`/`update_time`（不含 `pull_mode`）。

### 5. `tab_app_userinfo_field_mapping`、应用访问授权 8 张表直接按 `V2`/`V8`/`V10` 的建表语句原样收纳，均无种子数据

已核实这 9 张新表（`tab_app_userinfo_field_mapping`、`tab_app_access_policy` 及其 5 张关联表、`tab_app_access_policy_browser_rule`、`tab_app_access_policy_ip_rule`）在 `V2`/`V8`/`V10` 里均只有建表语句，没有任何字面量种子 `INSERT`（应用类运行时数据不通过 Flyway 种子），合并后同样只建表、不写种子。

### 6. 应用访问授权菜单/权限点种子数据（`V9`）直接并入现有种子数据块

`V9` 新增的 1 个页面菜单（`AppAccessManagement:appAccess:view`，挂在 `permission` 一级分组下）+ 8 个按钮资源，合并后并入 `tab_menu` 种子数据里"权限管理"分组下现有的管理员管理菜单之后；对应的 9 条 `tab_permission` 记录并入现有权限点种子数据块末尾（权限点总数从 109 条增至 118 条——现有基线文件里"108 条"的注释实际是历史遗留的记录误差，实测原本就是 109 条，本次一并在新基线注释里更正）。超级管理员角色的权限关联沿用现有"`INSERT ... SELECT id FROM tab_permission`"全量捕获写法（该语句在全部 `tab_permission` 种子插入完毕之后执行），新增的 9 条权限点会被自动吸收，不需要像 `V9` 原文件那样再单独写一条限定 `code` 列表的 `INSERT ... SELECT`。

### 7. 不保留任何中间过程 UPDATE/ALTER 步骤

与前三次一致：合并只保留"如何一步到位建出最终状态"，不保留"这张表/这行数据历史上是怎么一步步改过来的"的过程记录（`V4` 的回填 `UPDATE`、`V7` 的 `DROP COLUMN` 均不出现在新基线里）。这部分历史仍可通过 git 提交记录（本次合并对应的 commit，以及各自来源 change 的历史记录）追溯。

## Risks / Trade-offs

- [风险] 40 张表 + 全部种子数据一次性重写，人工合并容易遗漏个别列/记录，尤其是 `tab_app_auth_config`/`tab_app_notify_record`/`tab_app_pull_record` 这几张经历过多轮列增删的表 → **缓解**：合并完成后用 `./gradlew test` 跑通 `RbacApplicationTests`（含 Flyway 迁移执行），并按 Migration Plan 的核对清单逐项人工核对关键计数（表数量、`tab_permission` 总数、超级管理员权限关联数、上述三张表的最终列清单）。
- [风险] `tab_role_permission` 的全量捕获写法依赖"新增权限点必须在该 `INSERT ... SELECT` 之前插入"这一顺序约束 → **缓解**：合并时把应用访问授权的 `tab_permission` 种子数据放在现有权限点种子数据块内（该块整体位于超级管理员角色权限关联语句之前），保持顺序正确。

## Migration Plan

1. 按版本号顺序读取旧 `V1__init_schema.sql` 到 `V10__add_app_access_policy_request_control.sql`，逐表、逐条整理成最终状态，写入新的 `V1__init_schema.sql`。
2. 删除 `V2`~`V10` 共 9 个文件（`git rm`，保留 git 历史可追溯）。
3. 本地/测试库清空（或删除 `flyway_schema_history` 表）后重新执行新 `V1`，通过 `./gradlew test` 验证 Flyway 迁移无报错、`RbacApplicationTests` 的 `contextLoads` 通过。
4. 人工核对合并后的关键数据：40 张表全部建出，且不存在 `tab_app_data_change_log`；`tab_app_auth_config` 含 `service_patterns`/`logout_notify_url`、不含 `cas_service_patterns`/`oauth2_redirect_uri_patterns`；`tab_app_config` 含 `sync_master_enabled`；`tab_app_notify_record` 不含 `change_log_id`、含 `data_type`/`biz_id`/`notify_url` 及对应索引；`tab_app_pull_record` 不含 `pull_mode`；`tab_permission`/`tab_role_permission` 总数一致（118）且超级管理员角色权限关联数同为 118；应用访问授权相关菜单/按钮文案与挂载位置与 `V9` 一致。
5. 无回滚计划——如需回退，`git revert` 本次改动对应的 commit 即可恢复旧的 `V1~V10` 十个文件。

## Open Questions

（无）
