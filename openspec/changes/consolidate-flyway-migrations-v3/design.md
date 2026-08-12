## Context

自 2026-08-03 那次 `consolidate-flyway-migrations-v2`（`V1`~`V9` 合并为新 `V1`）以来，`V1__init_schema.sql` 之后又新增了 11 个版本（本次要合并的范围是 `V2`~`V11` 共 10 个增量文件）：

- `V2__audit_fields_use_user_id.sql`：审计字段种子数据里的占位字符串（`'system'`/`'admin'`）改写为对应用户 id 的字符串形式；不含 `ALTER TABLE`，只改种子数据取值。
- `V3__app_config.sql`：新增 `tab_app_config` 表（应用对外接口凭证配置，与 `tab_app` 一对一）。
- `V4__app_config_permission_seed.sql`：为"应用接口配置"能力追加 4 个权限点种子数据（`tab_menu` 按钮资源 + `tab_permission` + 授予超级管理员角色）。
- `V5__app_config_rename_and_no_prefix.sql`：`UPDATE tab_menu` 把"应用接口配置"文案改名为"应用配置"（AppId/AccessKey 生成规则去前缀是纯代码改动，不落库）。
- `V6__app_sync_notify_config.sql`：`tab_app_config` 新增 `sync_mode`/`notify_url`/`notify_params` 三列（基础同步配置）。
- `V7__app_sync_domain_config.sql`：把 `tab_app_config` 上原有的 `sync_org_enabled`/`sync_user_enabled`/`sync_app_enabled`/`sync_dict_enabled` 四个布尔列升级为独立子表 `tab_app_sync_domain_config`（组织/用户/应用/角色/字典五个数据域各一行），回填存量数据后删除原四列。
- `V8__app_sync_field_mapping.sql`：新增 `tab_app_sync_field_mapping` 表（组织/用户/应用/角色四个数据域的字段级同步映射配置）。
- `V9__metadata_field_role_seed.sql`：`tab_metadata_field` 追加 `bizType=ROLE` 的 4 条种子记录（`name`/`code`/`show_order`/`remark`）。
- `V10__org_parent_code.sql`：`tab_org` 新增 `parent_code` 列并回填存量数据。
- `V11__org_parent_code_metadata_field.sql`：`tab_metadata_field` 追加 1 条 `bizType=ORG`、`columnName=parent_code` 的种子记录。

这批增量里 `V3`/`V7`/`V8` 是新建表，`V6`/`V7`/`V10` 有列级 `ALTER TABLE`（`V7` 还有先加列回填再删列的完整生命周期），`V2`/`V5` 是纯 `UPDATE`，`V4`/`V9`/`V11` 是纯种子 `INSERT`——类型上比 v2 那次（几乎全是新建表+种子）更杂，合并时要特别注意 `V7` 里"先建子表、回填、再删除父表旧列"这条链路必须体现最终形态（子表直接建成最终结构，不保留过程中的四布尔列）。

`openspec/specs/backend-common-utilities/spec.md` 的"Flyway 迁移目录保持单一基线"需求已经预见并允许本次操作，不需要修改需求文本（与 proposal.md 一致）。

## Goals / Non-Goals

**Goals:**
- 迁移目录重新收敛为一份反映当前最终 schema + 种子数据状态的基线文件 `V1__init_schema.sql`。
- 合并过程不引入任何语义差异：22 张表（原 19 张 + `tab_app_config`/`tab_app_sync_domain_config`/`tab_app_sync_field_mapping`）的最终结构、全部种子数据（含 `tab_menu` 完整菜单树、权限点、角色权限关联、`tab_metadata_field` 目录含 ROLE/ORG.parent_code）都要和旧 `V1~V11` 按顺序执行后的最终状态完全一致。

**Non-Goals:**
- 不处理生产环境或任何已经执行过旧版本迁移的库的升级路径——本次改动假定使用方清空库后重新执行新基线（与前两次合并的既有约定一致）。
- 不改变任何业务代码、接口行为，纯粹是迁移文件的组织方式调整。
- 不修改 `openspec/specs/backend-common-utilities/spec.md` 的需求文本。

## Decisions

### 1. 继续合并为单个 `V1__init_schema.sql`，版本号沿用 `V1`

延续前两次的决策：项目表之间存在种子数据的相互引用（`tab_menu` 自引用 `parent_id`、`tab_role_permission` 引用 `tab_role`/`tab_permission`、`tab_app_config`/`tab_app_sync_domain_config`/`tab_app_sync_field_mapping` 引用 `tab_app`、`tab_app_sync_field_mapping` 引用 `tab_metadata_field`），单文件按"先建表、后插入有依赖关系的种子数据"的顺序线性组织最清晰。版本号不接着旧编号延续为 `V12`，理由同前两次：`V1` 代表"从零开始建库的基线"，语义清晰；代价是旧版本号 `V1~V11` 不能再复用，任何还在用旧版本号跑过 migrate 的库必须清库重来。

### 2. `tab_app_config` 直接建成"去掉四布尔列、含 sync_mode/notify_url/notify_params"的最终形态

`V3` 建表时带 `sync_org_enabled`/`sync_user_enabled`/`sync_app_enabled`/`sync_dict_enabled` 四个布尔列，`V6` 追加 `sync_mode`/`notify_url`/`notify_params`，`V7` 再把四个布尔列删除、数据迁到新子表。合并后的 `V1` 里 `tab_app_config` 建表语句 SHALL 只包含最终保留的列（不含四个布尔列），不体现"先加列再删列"的中间过程。

### 3. `tab_app_sync_domain_config` 只建表，不需要任何种子 `INSERT`

已核实 `V7` 里这张子表的数据来源是 `INSERT ... SELECT ... FROM tab_app_config`（动态按存量行回填，不是字面量种子 `INSERT`），而全部 11 个迁移文件里没有任何一处往 `tab_app`/`tab_app_config` 写入字面量种子数据——应用类数据是运行时业务数据，不通过 Flyway 种子。因此在一个全新数据库上，这条 `SELECT` 天然查不到任何行，`tab_app_sync_domain_config` 合并后只需要建表语句，不需要保留这条"回填"逻辑对应的种子 `INSERT`（Open Question 已解决，不带入 tasks 阶段）。

### 4. `tab_metadata_field` 种子数据合并为一次性 INSERT，覆盖 ORG（含 parent_code）/USER/POSITION/APP/ROLE 五类

`V1`（旧）里 ORG/USER/POSITION/APP 四类的种子、`V9` 的 ROLE 四条、`V11` 的 ORG.parent_code 一条，合并后按 `bizType` 分组一次性 `INSERT`，`ORG` 分组里除原有字段外追加 `parent_code` 一条记录。

### 5. 权限点/菜单种子数据直接体现最终挂载关系与文案

`V4` 新增的"应用配置"按钮资源、`V5` 对该按钮资源文案的改名，合并后 `V1` 里 `tab_menu`/`tab_permission` 种子数据直接使用最终文案（"应用配置"，而不是"应用接口配置"），不保留"先插入旧文案、再 UPDATE 改名"的中间过程；新增的权限点直接体现在超级管理员角色的全量权限关联里（沿用 v2 那次"用 `INSERT ... SELECT id FROM tab_permission` 全量捕获"的写法，新增权限点会被自动吸收，不需要额外追加关联语句）。

### 6. 审计字段种子值直接写最终值（用户 id 字符串），不保留 `V2` 的 UPDATE 步骤

`V2` 把种子数据里 `create_by`/`update_by` 的占位字符串从账号编码改写为用户 id 字符串形式；合并后 `V1` 里所有种子 `INSERT` 语句直接写最终的用户 id 字符串值（管理员账号自引用其自身 id），不再体现"先插入编码占位符、再 UPDATE 改写"的过程。

**实现补充**：为了让全部种子 `INSERT` 都能引用到管理员账号的用户 id，实现时把
"默认管理登录用户 admin/admin"引导数据从原文件末尾调整到了全部 `CREATE TABLE`
语句之后、其余种子数据最前面，并声明会话变量 `@admin_user_id_text := '1'`（该行
是 `tab_user` 表在全新数据库中插入的第一行，`AUTO_INCREMENT` 保证其 id 为 1）供
后续全部种子 `INSERT` 复用，取代原先分散的 `'admin'`/`'system'` 字面量。这只是
文件内种子数据的先后顺序调整，不引入任何 UPDATE 步骤，最终数据状态不变。

### 7. 不保留任何中间过程 UPDATE/ALTER 步骤

与前两次一致：合并只保留"如何一步到位建出最终状态"，不保留"这张表/这行数据历史上是怎么一步步改过来的"的过程记录。这部分历史仍可通过 git 提交记录（本次合并对应的 commit，以及各自来源 change 的历史记录）追溯。

## Risks / Trade-offs

- [风险] `tab_app_config`/`tab_app_sync_domain_config`/`tab_app_sync_field_mapping` 三张新表遗漏建表列或约束 → **缓解**：三张表均无种子数据（Decision 3 已核实），风险只在于建表语句本身的列/约束/注释是否完整照抄自 `V3`/`V6`/`V7`/`V8`，逐列比对即可。
- [风险] 22 张表 + 全部种子数据一次性重写，人工合并容易遗漏个别列/记录 → **缓解**：合并完成后用 `./gradlew test` 跑通 `RbacApplicationTests`（含 Flyway 迁移执行），并按 Migration Plan 的核对清单逐项人工核对关键计数（表数量、`tab_metadata_field` 各 `bizType` 记录数、`tab_permission` 总数、超级管理员权限关联数等）。

## Migration Plan

1. 按版本号顺序读取旧 `V1__init_schema.sql` 到 `V11__org_parent_code_metadata_field.sql`，逐表、逐条整理成最终状态，写入新的 `V1__init_schema.sql`。
2. 删除 `V2`~`V11` 共 10 个文件（`git rm`，保留 git 历史可追溯）。
3. 本地/测试库清空（或删除 `flyway_schema_history` 表）后重新执行新 `V1`，通过 `./gradlew test` 验证 Flyway 迁移无报错、`RbacApplicationTests` 的 `contextLoads` 通过。
4. 人工核对合并后的关键数据：22 张表全部建出；`tab_metadata_field` 里 `ORG` 分组包含 `parent_code`、`ROLE` 分组存在且为 4 条记录；`tab_app_config` 不含四个已废弃的布尔列、含 `sync_mode`/`notify_url`/`notify_params`；`tab_app_sync_domain_config` 结构正确（5 个数据域枚举值校验通过）；`tab_permission`/`tab_role_permission` 总数与超级管理员权限关联数一致；"应用配置"相关菜单文案已是最终态、不是"应用接口配置"。
5. 无回滚计划——如需回退，`git revert` 本次改动对应的 commit 即可恢复旧的 `V1~V11` 十一个文件。

## Open Questions

（无——原先关于 `tab_app` 系列表是否存在种子数据的疑问已在 Decision 3 中核实并解决）
