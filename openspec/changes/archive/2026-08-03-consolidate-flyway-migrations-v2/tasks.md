## 1. 整理合并后的基线迁移文件

- [x] 1.1 按版本号顺序读取旧 `V1__init_schema.sql` 到 `V9__reorganize_log_menu.sql` 共 9 个文件
- [x] 1.2 逐表整理最终建表语句（19 张表：`tab_org`/`tab_dict_type`/`tab_dict_item`/`tab_user`/
      `tab_user_position`/`tab_user_password`/`tab_app`/`tab_role`/`tab_role_permission`/
      `tab_permission`/`tab_menu`/`tab_admin`/`tab_admin_role`/`tab_admin_org_scope`/
      `tab_operation_log`/`tab_metadata_field`/`tab_form_field_definition`/
      `tab_import_field_config`/`tab_login_log`）
- [x] 1.3 整理 `tab_menu` 最终种子数据：5 个一级分组（身份管理/应用管理/权限管理/系统管理/
      日志管理）+ 各页面节点 + 各按钮节点，登录日志、操作日志直接挂在"日志管理"分组下，
      用户管理页面下直接包含"重置密码"按钮，组织/用户/任职/应用四个页面下直接包含
      `importTemplate`/`import` 按钮
- [x] 1.4 整理 `tab_permission` 最终种子数据：95 条权限点（原 94 条 + 登录日志 1 条）
- [x] 1.5 整理 `tab_role`/`tab_role_permission` 种子数据：超级管理员角色 + 全量权限关联
- [x] 1.6 整理默认账号种子数据：`tab_user`（admin）+ `tab_user_password` + `tab_admin` +
      `tab_admin_role`
- [x] 1.7 整理 `tab_dict_type`/`tab_dict_item` 种子数据（任职类型、性别）
- [x] 1.8 写入 `backend/src/main/resources/db/migration/V1__init_schema.sql`，文件头部注明
      "本文件由原 V1~V9 合并而来，本地库需清空后重新执行"

## 2. 校验一致性

- [x] 2.1 逐表核对新文件与旧文件按顺序应用后的最终 schema 一致（19 张表全部覆盖）
- [x] 2.2 逐条核对种子数据一致：`tab_menu` 节点总数与层级关系、`tab_permission` 95 条、
      `tab_role_permission` 关联条数
- [x] 2.3 核对没有遗漏 `V4`（重置密码按钮）、`V7`（8 条导入按钮）、`V8`（登录日志菜单/权限点/
      SUPER_ADMIN 关联）、`V9`（日志管理分组挂载）任何一条数据

## 3. 清理与验证

- [x] 3.1 删除旧 `V2`~`V9` 共 8 个迁移文件（`git rm`，保留 git 历史可追溯）
- [x] 3.2 确认迁移目录最终只剩 `V1__init_schema.sql`
- [x] 3.3 本地开发库清空（或删除 `flyway_schema_history` 表）后跑 `./gradlew test`，确认新
      `V1` 迁移无报错、`contextLoads` 通过
- [x] 3.4 人工核对迁移后的数据库：`tab_menu` 树、`tab_permission` 总数（95）、
      `tab_role_permission` 中 SUPER_ADMIN 关联条数（95）、admin 账号可正常登录链路完整

## 4. OpenSpec 收尾

- [x] 4.1 实现完成后运行 `openspec-doc-sync`，核对本变更的 proposal/design/tasks 与实际实现
      是否一致（agent 核对后确认三份文档已准确反映实际实现，无需改动）
- [x] 4.2 确认无误后归档（本次不涉及 spec delta，跳过 `openspec-sync-specs`，直接执行
      `openspec-archive-change`）
