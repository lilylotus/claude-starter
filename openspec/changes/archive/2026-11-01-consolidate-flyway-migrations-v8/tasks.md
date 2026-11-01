## 1. 锁定合并来源

- [x] 1.1 重新枚举迁移目录，确认合并来源仍为 V1、V9、V10；若集合或内容已变化，先更新文档并重新确认
- [x] 1.2 保存 3 个来源脚本的 SHA-256 与只读副本（临时目录，不入库）

## 2. 重写单一 V1

- [x] 2.1 并入 ABAC 4 张表与 `tab_video` 建表语句，确认共 81 条 `CREATE TABLE`
- [x] 2.2 并入 ABAC 菜单/6 个权限点、视频菜单/3 个权限点种子，位于 SUPER_ADMIN 全量授权之前；移除分段补授
- [x] 2.3 更新 V1 文件头（合并来源、表数量）与权限数量注释
- [x] 2.4 静态扫描：0 条 `ALTER TABLE`，无 CTE/窗口函数/`JSON_TABLE`/厂商专属 upsert；权限编码集合与 `权限资源.txt` 一致

## 3. 清理增量迁移文件

- [x] 3.1 删除 `V9__add_abac_policy.sql`、`V10__add_video.sql`，确认目录只剩 `V1__init_schema.sql`

## 4. 验证

- [x] 4.1 MySQL 5.7 隔离空 schema 双路径比较（结构 + 按业务键归一化的种子数据），确认无差异
- [ ] 4.2 若有 MySQL 8.0 环境则重复；不可用时保持未完成并报告（本地仅有 MySQL 5.7.40，未执行）
- [x] 4.3 指向隔离空 schema 运行后端全量测试，确认 `flyway_schema_history` 只有成功的 V1；全程不连接本地 `rbac` 库
- [x] 4.4 清理隔离 schema

## 5. 文档收尾

- [x] 5.1 同步 `add-video-management` change 中关于 `V10__add_video.sql` 的描述
- [x] 5.2 依据实际结果更新本 change 的 proposal/design/tasks
