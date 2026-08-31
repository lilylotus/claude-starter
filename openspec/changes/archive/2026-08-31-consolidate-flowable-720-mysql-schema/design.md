## Context

见 `proposal.md` 的 Why。项目当前固定使用 Flowable 7.2.0，数据库部署需要同时覆盖 MySQL 5.7 与 MySQL 8.0，并在 `application.yml` 中仅启用 BPMN ProcessEngine，关闭 App、CMMN、DMN、Event Registry 与 IDM。Flowable 表目前由 `flowable.database-schema-update: true` 在应用启动时管理；仓库中没有可交付的 Flowable SQL。

Flowable 7.2.0 解析到的依赖包包含三个与本项目运行范围对应的 MySQL 全量建表资源：

- `flowable-engine-common-7.2.0.jar!/org/flowable/common/db/create/flowable.mysql.create.common.sql`
- `flowable-engine-7.2.0.jar!/org/flowable/db/create/flowable.mysql.create.engine.sql`
- `flowable-engine-7.2.0.jar!/org/flowable/db/create/flowable.mysql.create.history.sql`

历史 `upgradestep.*.sql` 是针对特定已存在 schema 版本的增量迁移，不能安全地作为空库初始化链直接拼接。仓库约束要求手写 SQL 至少兼容 MySQL 5.7；本 change 在此基础上增加 MySQL 8.0 兼容性，并保持 Flowable 官方表不与业务 Flyway 迁移混用。

## Goals / Non-Goals

**Goals:**

- 生成一个能够将空 MySQL 5.7 或 MySQL 8.0 schema 初始化为 Flowable 7.2.0 ProcessEngine 终态的同一 SQL 文件。
- 保持官方有效 SQL 原样、顺序可追溯，并提供可重复执行的生成/校验方法。
- 初始化完成后让运行时只做版本校验，不允许应用隐式改变生产 schema。

**Non-Goals:**

- 不为任何 Flowable 旧版本设计原地升级路线。
- 不让脚本幂等，不处理部分执行后的自动恢复。
- 不创建数据库或用户，不授予权限，不包含项目 `tab_*` 业务表。
- 不启用或初始化 IDM、DMN、CMMN、App、Event Registry。

## Decisions

### Decision 1: 以 7.2.0 全量建表脚本为源，不串联历史升级脚本

最终结构直接取自当前运行版本随包发布的全量建表资源。增量升级脚本依赖明确的起始版本、已有数据和已存在对象；在空库执行会遇到对象缺失或重复变更，也难以证明最终结果等同于全量脚本。

备选方案是从最早版本开始拼接所有 create/upgrade 脚本。未采用，因为用户确认的是 7.2.0 目标版本而非某一旧版本升级，而且该方案会人为引入多条历史分支和迁移前置条件。

### Decision 2: 使用 common → engine → history 的固定合并顺序

`common` 先建立 `ACT_GE_PROPERTY`、通用运行时任务/变量/作业等共享对象；`engine` 再建立流程定义与执行对象、补充跨模块外键并写入 `schema.version=7.2.0.2` 和 `schema.history=create(7.2.0.2)`；`history` 最后补齐流程历史对象与索引。三段之间只加入来源和边界注释，不改变有效 SQL。

备选方案是按文件名字典序排列。未采用，因为字典序不表达对象依赖，`engine` 需要引用 `common` 创建的共享表。

### Decision 3: 脚本放在独立的 Flowable 资源目录

输出文件定为 `backend/src/main/resources/db/flowable/flowable.mysql.create.7.2.0.sql`。它不进入 `db/migration`，因此不会被现有 `spring.flyway.locations=classpath:db/migration` 自动扫描。部署人员需要在应用首次启动前显式执行它。

备选方案是将脚本命名为新的 Flyway `V*` 迁移。未采用，因为 Flowable schema 有独立版本元数据和官方升级路径，混入业务 Flyway 会造成两个版本管理者之间的职责冲突，也违背项目现有约定。

### Decision 4: 手工初始化后关闭 Flowable schema 自动更新

把 `flowable.database-schema-update` 从 `true` 改为 `false`。这样启动时仍校验已存在 schema，但不会在未经数据库审核的情况下自动创建或升级结构。部署顺序因此固定为“执行单脚本 → 启动应用”。

备选方案是保留 `true`。未采用，因为这会让单一审核脚本失去权威性，后续依赖版本变化仍可能在启动时隐式修改数据库。

### Decision 5: 生成与验证均以依赖解析出的官方资源为准

实现时使用仓库已解析的 Flowable 7.2.0 JAR 读取三个资源并确定性生成合并文件；验证命令检查依赖版本、源资源存在性、合并顺序、有效 SQL 等价性、危险语句缺失及版本属性值。只提交最终 SQL 交付物，不提交依赖本机 Gradle 缓存路径的临时提取文件或一次性生成工具，部署也不依赖本地 Gradle 缓存。

执行验证必须分别使用独立的临时 MySQL 5.7 与 MySQL 8.0 schema，禁止使用 `application.yml` 当前配置的 `rbac` schema。两个版本都执行同一份合并脚本，执行后按相同清单核对表、索引、外键和 `ACT_GE_PROPERTY` 版本记录，并以 `database-schema-update=false` 启动或初始化 ProcessEngine 验证兼容性。官方脚本中的 `utf8`/`utf8_bin` 等兼容写法保持原样；MySQL 8.0 即使产生弃用提示，只要不影响执行和结构一致性，不单独改写为 8.0 专属语法，以免破坏 MySQL 5.7 兼容性。

备选方案是人工复制粘贴后只做目视检查。未采用，因为六百余行共享 SQL 容易遗漏或重排，且无法稳定证明后续重新生成仍一致。

### Decision 6: 在建表语句前补充表用途注释

每个官方 `CREATE TABLE` 语句前新增一条中文 SQL 行注释，说明该表所属的流程引擎能力与主要存储内容。注释不改动官方的建表、索引、外键、插入版本属性等有效 SQL，因而仍可通过官方语句规范化等价性校验。

## Risks / Trade-offs

- [Flowable 依赖升级后脚本可能过期] → 校验入口同时读取 `FlowableVersion` 和 JAR 资源；版本不为 7.2.0 或内容不一致时失败，要求重新生成并评审。
- [脚本在非空 schema 中部分执行后失败] → 文件头明确一次性空库前置条件；不提供 `DROP` 回滚，失败时仅删除专用临时 schema 后重建，生产环境按数据库备份/恢复流程处理。
- [关闭自动更新后未预执行脚本会导致应用启动失败] → 部署说明固定执行顺序，并通过启动验证覆盖该情形。
- [MySQL 5.7 或 MySQL 8.0 任一环境不可用导致兼容性验证不完整] → 静态等价性校验仍必须完成；两个版本的实际数据库验证均完成前，相关任务不得标记完成，并明确报告缺失环境。
- [为消除 MySQL 8.0 弃用告警而改用新字符集名称，可能破坏与官方脚本及 MySQL 5.7 的一致性] → 保留 Flowable 7.2.0 官方有效 SQL，仅把告警记入验证结果，不做版本专属分叉。
- [官方脚本含 Flowable 共享服务表，看似超出“流程引擎”范围] → 这些表由 ProcessEngine 运行时依赖，按官方 common 资源完整保留，不按表名手工裁剪。

## Migration Plan

1. 在独立的空 MySQL 5.7 和 MySQL 8.0 schema 上分别执行同一份合并脚本，并完成相同的结构与版本校验。
2. 对目标环境做数据库备份，确认目标 schema 不存在任何 Flowable `ACT_*`/`FLW_*` 对象。
3. 在目标 schema 中执行合并脚本；发生失败时停止部署并按数据库备份/恢复流程恢复，不重复执行残缺脚本。
4. 部署 `database-schema-update=false` 的应用并验证 ProcessEngine 启动及审批流程定义加载。

对于已经由 Flowable 自动建表且投入使用的环境，本 change 不提供迁移操作，也不得执行该初始化脚本；这些环境仅在确认现有 schema 已为 7.2.0 后切换自动更新配置。
