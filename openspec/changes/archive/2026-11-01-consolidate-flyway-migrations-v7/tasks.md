## 1. 锁定合并来源

- [x] 1.1 重新枚举迁移目录，确认合并来源仍为 V1–V8 共 8 个文件；若集合或内容已变化，先更新 OpenSpec 文档并重新请求用户确认
- [x] 1.2 保存 8 个来源脚本的文件名、SHA-256 和只读副本（放在临时目录，不入库）

## 2. 重写单一 V1 结构

- [x] 2.1 将 V2 `route_field_codes` 并入 `tab_wf_process_definition`、V7 `sender_identity_key_fingerprint` 并入 `tab_chat_message`，保持原 `AFTER` 列顺序
- [x] 2.2 将 V4 折叠进 `tab_wf_process_binding`：`status` 替代 `enabled`、维度索引改为普通索引；全局绑定种子改写 `status = 2000`
- [x] 2.3 将 V3 `tab_file_upload`、V7 `tab_chat_user_key`、V8 `tab_image` 并入建表区，确认共 76 条 `CREATE TABLE`
- [x] 2.4 更新 V1 文件头：合并来源、表数量、权限数量、空 schema 限制

## 3. 合并最终种子数据

- [x] 3.1 将 `WorkflowDesign:binding:delete`、`ApprovalManagement:record:view` 及"公共服务"/图片相关菜单和权限点并入 SUPER_ADMIN 全量授权之前的种子块
- [x] 3.2 移除 V5/V6/V8 分段补授，更新种子块中的权限数量注释
- [x] 3.3 静态扫描新 V1：0 条 `ALTER TABLE`，无 CTE/窗口函数/`JSON_TABLE`/厂商专属 upsert；权限编码集合与 `权限资源.txt` 一致

## 4. 清理增量迁移文件

- [x] 4.1 删除 V2–V8 共 7 个增量脚本，确认迁移目录只剩 `V1__init_schema.sql`

## 5. 验证

- [x] 5.1 在 MySQL 5.7 隔离空 schema 中分别执行 legacy V1–V8 与新 V1，比较表/列/索引及按稳定业务键归一化的种子数据，确认无语义差异
- [ ] 5.2 若有 MySQL 8.0 环境则重复双路径验证；不可用时保持未完成并报告缺口（本地仅有 MySQL 5.7.40，未执行）
- [x] 5.3 指向隔离 baseline schema 运行 `./gradlew test --tests "cn.nihility.rbac.RbacApplicationTests"`，确认 `flyway_schema_history` 只记录成功的 V1
- [x] 5.4 清理隔离 schema

## 6. OpenSpec 收尾

- [x] 6.1 基于实际 diff 与验证结果更新本 change 的 proposal/design/tasks
- [x] 6.2 确认 `权限资源.txt` 无需修改（本 change 不新增/删除/重命名编码）
