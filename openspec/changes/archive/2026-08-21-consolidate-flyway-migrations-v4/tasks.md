## 1. 合并前置核对

- [x] 1.1 逐份重读 `V1__init_schema.sql` ~ `V10__add_app_access_policy_request_control.sql`，列出全部 40 张表的最终建表语句应有的列/索引/注释清单
- [x] 1.2 核对 `tab_app_auth_config` 最终应保留的列（`service_patterns`/`logout_notify_url`，不含 `cas_service_patterns`/`oauth2_redirect_uri_patterns`）
- [x] 1.3 核对 `tab_app_config` 最终应保留的列（含 `V6` 新增的 `sync_master_enabled`）
- [x] 1.4 核对 `tab_app_notify_record`/`tab_app_pull_record` 最终列与索引（不含 `change_log_id`/`pull_mode` 及前者对应索引，含 `V5` 新增列与索引）
- [x] 1.5 核对不再创建 `tab_app_data_change_log` 表
- [x] 1.6 核对 `tab_menu`/`tab_permission` 最终种子数据：应用访问授权 1 个页面菜单 + 8 个按钮资源 + 9 条权限点并入现有种子数据块，超级管理员角色通过 `INSERT ... SELECT id FROM tab_permission` 全量捕获全部权限点（含新增的 9 条）

## 2. 编写新基线文件

- [x] 2.1 按"先建表（含 40 张表的最终结构）、后插入有依赖关系的种子数据"的顺序，把合并结果写入新的 `V1__init_schema.sql`
- [x] 2.2 `tab_app_userinfo_field_mapping` 及应用访问授权 8 张表只建表，不写种子 `INSERT`（design.md Decision 5）
- [x] 2.3 应用访问授权菜单/权限点种子数据并入现有 `tab_menu`/`tab_permission` 种子数据块（design.md Decision 6）

## 3. 清理旧增量文件

- [x] 3.1 `git rm` 删除 `V2__add_app_userinfo_field_mapping.sql` ~ `V10__add_app_access_policy_request_control.sql` 共 9 个文件
- [x] 3.2 用新内容覆盖 `V1__init_schema.sql`

## 4. 验证

- [x] 4.1 清空本地/测试库（或删除 `flyway_schema_history` 表）——实际对远程开发库 `10.4.100.122:13306/rbac`（`application.yml` 当前指向的库）执行了 `DROP DATABASE`/`CREATE DATABASE`
- [x] 4.2 `./gradlew test --tests "cn.nihility.rbac.RbacApplicationTests"` 确认新基线 Flyway 迁移无报错、应用能正常启动
- [x] 4.3 `./gradlew test` 全量测试套件通过
- [x] 4.4 人工/查询核对：40 张表全部建出且不存在 `tab_app_data_change_log`；`tab_app_auth_config`/`tab_app_config`/`tab_app_notify_record`/`tab_app_pull_record` 列清单符合 design.md 描述；`tab_permission`/`tab_role_permission` 总数一致（118）；应用访问授权相关菜单/按钮文案与挂载位置正确

## 5. OpenSpec 文档收尾

- [x] 5.1 实现完成后，若合并结果与 design.md 的假设有出入，更新 design.md 对应 Decision 与本 tasks.md 记录实际情况——已记录（见下方"实施结果记录"）
- [x] 5.2 同步更新 `openspec/specs/backend-common-utilities/spec.md`"Flyway 迁移目录保持单一基线"需求里的表数量描述（22 → 40）

### 实施结果记录

- 实际建表数量为 40 张（而非 proposal.md 起草时按"现有 30 张"估算得出的 39 张）：核实后现有基线原本就是 31 张表（`openspec/specs/backend-common-utilities/spec.md` 里"22 张"的描述早已因此前未记录在案的整合提交而漂移），31 + 本次净增 9 张 = 40 张，已同步更正 proposal.md/design.md 的表述。
- `tab_permission` 种子数据合并后实测为 118 条（而非按旧注释"108 + 9 = 117"估算得出的 117 条）：核实后现有基线的 `tab_permission` 种子数据本就是 109 条（文件里"108 条"的注释是历史遗留的记录误差），109 + 9 = 118，已在新基线对应注释与 design.md 中更正。
