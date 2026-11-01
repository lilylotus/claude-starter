## Context

迁移目录当前为：

| 版本 | 内容 |
| --- | --- |
| V1 | v7 合并产出的基线，76 张业务表 + 种子数据 |
| V9 | 新建 `tab_abac_policy`、`tab_abac_policy_resource`、`tab_abac_policy_user_attr`、`tab_abac_policy_time_window`；"权限管理"下登记"ABAC 策略管理"菜单 + 5 个按钮、6 个权限点，并补授 SUPER_ADMIN |
| V10 | 新建 `tab_video`；"公共服务"下登记"视频管理"菜单 + 2 个按钮、3 个权限点，并补授 SUPER_ADMIN |

V9 已提交（`e4acb6f`），V10 属于尚未提交/归档的 `add-video-management` change。两者均无 `ALTER`/`UPDATE`/回填。

## Goals / Non-Goals

**Goals:**

- 单一 V1 一步创建 V1→V9→V10 执行后的最终 81 张业务表与全部种子数据。
- 在隔离 MySQL 空 schema 上证明旧链与新基线结构、种子数据等价。
- 迁移目录只保留 `V1__init_schema.sql`，同步 OpenSpec 单基线契约。

**Non-Goals:**

- 不为已有数据的数据库提供原地升级或 history 修复方案。
- 不修改业务代码、接口、权限编码或菜单语义。

## Decisions

### Decision 1: 在现有 V1 上就地追加

- 建表：ABAC 4 张表放在"权限管理"相关建表区之后；`tab_video` 放在公共服务模块（`tab_file_upload`、`tab_image`）之后。表定义（列、默认值、注释、索引、引擎/字符集）原样搬入，不做任何改写。
- 种子：ABAC 菜单/权限点、视频菜单/权限点放在 SUPER_ADMIN 全量授权语句之前，菜单名、编码、父子关系、资源类型、show_order、备注与旧链一致；依赖的父节点（`permission`、`common-service`）在 V1 中均已先于此插入。
- 移除 V9/V10 末尾的 `tab_role_permission` 分段补授：全量授权 `INSERT ... SELECT ... FROM tab_permission` 已覆盖。
- 更新文件头：合并来源、表数量。

### Decision 2: 双路径等价验证

沿用 v7 做法：在本地 MySQL 5.7.40 上建两个隔离空 schema，legacy 依次执行保存的 V1、V9、V10 只读副本，baseline 只执行新 V1；比较 `information_schema` 表/列/索引，以及按稳定业务键归一化后的种子数据（菜单父子 code、角色—权限 code 等）；核对权限编码集合与 `权限资源.txt` 一致且 SUPER_ADMIN 全量授权；再指向隔离空 schema 运行后端全量测试。验证全程使用 `SPRING_DATASOURCE_URL` 指向隔离 schema，**不得**连接本地业务库 `rbac`。本地无 MySQL 8.0，8.0 验证保持未完成并如实报告。

### Decision 3: 已执行旧链的数据库不切换新基线

V1 校验和变化且 V9/V10 记录缺失，Flyway validate 会失败。保留数据的环境继续使用旧迁移集合；可丢弃数据的开发环境（含本地 `rbac`）需完整删库重建后执行新 V1。本 change 不自动操作本地 `rbac` 库，重建由用户确认后执行。

## Risks / Trade-offs

- [菜单自增 id 变化] → 引用均按 code 子查询，比较以业务键为准。
- [`add-video-management` 尚未提交，文档写的是 V10] → 同步修正其 proposal/design/tasks 描述，避免归档后文档与代码不一致。
- [本地 `rbac` 重建会丢失已上传视频的数据库记录] → 在确认环节明确告知，由用户决定。

## Migration Plan

1. 保存 V1、V9、V10 只读副本与 SHA-256。
2. 就地重写 V1，删除 V9、V10。
3. 静态核对：81 条 `CREATE TABLE`、0 条 `ALTER TABLE`、无 MySQL 8.0 专属语法、权限编码集合一致。
4. 隔离 schema 双路径比较；后端全量测试指向隔离 schema。
5. 依据真实结果同步 OpenSpec 文档。

代码回滚通过 Git 恢复旧的 3 文件迁移集合。

## Open Questions

无。
