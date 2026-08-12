## 1. 合并前置核对

- [x] 1.1 逐份重读 `V1__init_schema.sql` ~ `V11__org_parent_code_metadata_field.sql`，列出全部 22 张表的最终建表语句应有的列/索引/注释清单
- [x] 1.2 核对 `tab_app_config` 最终应保留的列（不含 `V7` 删除的四个布尔列，含 `V6` 新增的 `sync_mode`/`notify_url`/`notify_params`）
- [x] 1.3 核对 `tab_metadata_field` 最终种子数据：ORG/USER/POSITION/APP 四类原有记录 + ROLE 四条（`V9`）+ ORG.parent_code 一条（`V11`）
- [x] 1.4 核对 `tab_menu`/`tab_permission` 最终种子数据：应用配置相关按钮资源使用最终文案"应用配置"（不是"应用接口配置"），超级管理员角色通过 `INSERT ... SELECT id FROM tab_permission` 全量捕获全部权限点
- [x] 1.5 核对审计字段种子值：全部种子 `INSERT` 的 `create_by`/`update_by` 直接写最终的用户 id 字符串形式（不体现 `V2` 的 UPDATE 过程）

## 2. 编写新基线文件

- [x] 2.1 按"先建表（含 22 张表的最终结构）、后插入有依赖关系的种子数据"的顺序，把合并结果写入新的 `V1__init_schema.sql`（先写到临时文件或直接覆盖，视实现时机而定）
- [x] 2.2 `tab_app_sync_domain_config`、`tab_app_sync_field_mapping` 只建表，不写种子 `INSERT`（design.md Decision 3）

## 3. 清理旧增量文件

- [x] 3.1 `git rm` 删除 `V2__audit_fields_use_user_id.sql` ~ `V11__org_parent_code_metadata_field.sql` 共 10 个文件
- [x] 3.2 用新内容覆盖 `V1__init_schema.sql`

## 4. 验证

- [x] 4.1 清空本地/测试库（或删除 `flyway_schema_history` 表）
- [x] 4.2 `./gradlew test --tests "cn.nihility.rbac.RbacApplicationTests"` 确认新基线 Flyway 迁移无报错、应用能正常启动
- [x] 4.3 `./gradlew test` 全量测试套件通过
- [x] 4.4 人工/查询核对：22 张表全部建出；`tab_metadata_field` 的 ORG 分组含 `parent_code`、ROLE 分组存在且为 4 条；`tab_app_config` 不含已废弃的四个布尔列；`tab_permission`/`tab_role_permission` 总数与超级管理员权限关联数一致；应用配置相关菜单文案是最终态

## 5. OpenSpec 文档收尾

- [x] 5.1 实现完成后，若合并结果与 design.md 的假设有出入（如发现 `tab_app` 系列表实际存在种子数据），更新 design.md 对应 Decision 与本 tasks.md 记录实际情况

### 实施结果记录

与 design.md 的假设基本一致，实现过程中额外确认/补充的细节：

- 为了让全部种子 `INSERT` 的 `create_by`/`update_by` 都能直接使用管理员账号的用户 id
  字符串值（design.md Decision 6），把"默认管理登录用户 admin/admin"引导数据从文件
  末尾移到了全部 `CREATE TABLE` 语句之后、其余种子数据之前，并新增会话变量
  `@admin_user_id_text`（取值 `'1'`，因为该行是 `tab_user` 表在全新数据库中插入的
  第一行，`AUTO_INCREMENT` 保证其 id 为 1）供后续全部种子 `INSERT` 复用，取代原先
  分散使用的 `'admin'`/`'system'` 字面量。这一调整不影响最终数据状态，只是为了避免
  再引入一次"先插占位符、再 UPDATE"的过程，与 Decision 6/7 的精神保持一致。
- 权限点种子数据合并后共 99 条（原文件注释里的 94+1=95 条基础上，追加应用配置相关
  4 条），已在新文件对应注释中更新说明；实测 `tab_permission` 总数与超级管理员角色
  在 `tab_role_permission` 里的关联数均为 99，一致。
- 人工核对结果：22 张业务表全部建出；`tab_metadata_field` 分组计数为
  APP=14、ORG=15（含 `parent_code`）、POSITION=15、ROLE=4、USER=17；
  `tab_app_config` 表结构确认不含 `sync_org_enabled`/`sync_user_enabled`/
  `sync_app_enabled`/`sync_dict_enabled`，含 `sync_mode`/`notify_url`/
  `notify_params`；`tab_app_sync_domain_config`/`tab_app_sync_field_mapping`
  在全新库上均为 0 行；`AppManagement:app:config` 对应的菜单名称为"应用配置"、
  权限点名称为"应用配置页面访问"，均为最终文案。
