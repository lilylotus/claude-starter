## 1. 官方脚本提取与合并

- [x] 1.1 从 Gradle 实际解析的 Flowable 7.2.0 依赖中提取 MySQL `common`、`engine`、`history` 三个官方全量建表资源，核对资源路径、版本及 SHA-256，并以命令输出验证三份来源唯一且完整
- [x] 1.2 按 `common` → `engine` → `history` 顺序生成 `backend/src/main/resources/db/flowable/flowable.mysql.create.7.2.0.sql`，增加目标版本、兼容 MySQL 5.7/8.0、空库一次性执行限制及来源边界注释，并以文件检查验证仓库只新增一个 Flowable 初始化 SQL 交付物、不按数据库版本产生分叉
- [x] 1.3 将合并脚本与三个官方源脚本的有效 SQL 规范化后逐语句比较，验证语句数量、顺序与内容一致，同时核对脚本创建 32 张 `ACT_*`/`FLW_*` 表且写入 `schema.version=7.2.0.2`、`schema.history=create(7.2.0.2)`
- [x] 1.4 在每个 `ACT_*`/`FLW_*` 建表语句前增加中文用途注释，规茆化后重新校验所有官方有效 SQL 语句数量、顺序和内容与来源一致

## 2. 安全边界与运行配置

- [x] 2.1 静态扫描合并脚本，验证不存在 `CREATE DATABASE`、`DROP DATABASE`、`DROP TABLE`、`TRUNCATE TABLE` 或删除既有数据的语句，并核对未混入 IDM、DMN、CMMN、App、Event Registry 专属脚本
- [x] 2.2 将 `application.yml` 的 `flowable.database-schema-update` 改为 `false`，同步注释说明“先执行单一初始化脚本、再启动应用”，并以配置检查验证 Flyway locations 仍仅为 `classpath:db/migration`

## 3. 数据库与应用验证

- [x] 3.1 在独立、可销毁且不指向应用现有 `rbac` schema 的 MySQL 5.7 空 schema 中执行完整脚本，验证无 SQL 错误，表、索引、外键和 `ACT_GE_PROPERTY` 版本记录均存在；若没有可用 MySQL 5.7 环境则保持本任务未完成并报告阻塞
- [ ] 3.2 在独立、可销毁且不指向应用现有 `rbac` schema 的 MySQL 8.0 空 schema 中执行与 3.1 完全相同的脚本和结构核对，验证无 SQL 错误且结果范围一致；若没有可用 MySQL 8.0 环境则保持本任务未完成并报告阻塞
- [ ] 3.3 分别使用已初始化的 MySQL 5.7 与 MySQL 8.0 临时 schema 和 `database-schema-update=false` 启动 Flowable 7.2.0 ProcessEngine，验证两个版本均通过 schema 校验、审批 BPMN 部署及一次最小流程实例启动
- [ ] 3.4 在 `backend/` 执行 `./gradlew test`，验证现有后端测试全部通过且本次配置变更未造成回归
