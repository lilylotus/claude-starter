## 1. 合并前终态清单

- [x] 1.1 保存当前 V1–V14 的文件名、SHA-256 与只读副本用于 legacy 对比，验证 14 个来源文件均被纳入且不包含工作区其他未提交文件
- [x] 1.2 按版本顺序建立最终 schema 清单，验证表数量为 46，并逐项列出 V2–V14 新增的表、列、默认值、可空性、索引和唯一约束
- [x] 1.3 建立最终种子数据清单，验证权限点为 127 个、超级管理员覆盖全部权限点，并记录插件、Excel 导出、审批菜单、审批开关、`tab_org` 无种子记录和同步元数据的最终值

## 2. 重写单一 V1 基线

- [x] 2.1 更新 V1 的既有建表定义，直接纳入登录会话、策略排序、导出开关、组织路径及版本、同步实体版本、配置纪元、通知任务状态和字典项版本等最终字段/索引，并以终态清单逐项核对
- [x] 2.2 将 `tab_sso_protocol_log`、`tab_approval_request`、`tab_approval_switch`、`tab_app_data_change_log`、`tab_app_sync_metadata`、`tab_app_sync_cursor` 六张新表按所属模块加入 V1，验证 V1 恰好包含 46 条业务表建表语句
- [x] 2.3 重写受影响的种子 INSERT：`show_in_export` 等于对应 `show_in_list`、审批开关默认及四条种子均为 0、待我审批直接使用最终菜单层级、`tab_org` 保持无种子记录、同步保留序号种子为 0，并以稳定业务键核对最终值
- [x] 2.4 将插件管理、四个 Excel 导出、审批管理菜单及 9 个新增权限点并入既有种子块，确保超级管理员全量授权语句位于全部 127 个权限点之后，并验证菜单编码/父子层级/资源类型与旧链终态一致
- [x] 2.5 更新 V1 文件头，明确它由 V1–V14 合并、仅适用于空 schema、兼容 MySQL 5.7/8.0、已有数据库不得通过删除 `flyway_schema_history` 强制重跑，并静态验证不含历史性 `ALTER TABLE` 或存量 `UPDATE` 回填

## 3. 清理增量脚本

- [x] 3.1 删除 V2–V14 共 13 个增量迁移文件，使用 `rg --files backend/src/main/resources/db/migration` 验证目录仅剩 `V1__init_schema.sql`
- [x] 3.2 静态扫描新 V1，验证未包含 CTE、窗口函数或仅 MySQL 8.0 支持的语法，且未混入 Flowable `ACT_*`/`FLW_*` 表

## 4. 双版本等价性验证

- [x] 4.1 在隔离的 MySQL 5.7 临时环境中分别执行 legacy V1–V14 与新 V1，比较表/列/默认值/索引/约束和按稳定业务键归一化后的种子数据，验证两条路径均为 46 张表、127 个权限点且无语义差异；环境不可用时保持任务未完成并报告阻塞
- [x] 4.2 在隔离的 MySQL 8.0 临时环境中重复 4.1 的同脚本、同清单验证，确认没有语法错误或结构差异；环境不可用时保持任务未完成并报告阻塞
- [x] 4.3 使用指向隔离临时 schema 的 datasource 覆盖运行 `./gradlew test --tests "cn.nihility.rbac.RbacApplicationTests"` 和 `./gradlew test`，验证 Flyway 只记录 V1 且后端测试通过，禁止连接或清理 `application.yml` 当前配置的 `rbac` schema

## 5. OpenSpec 收尾

- [x] 5.1 基于实际 SQL diff 和双版本验证结果更新本 change 的 proposal/design/tasks 计数与实施记录；若终态与已确认设计不一致，先暂停实现并重新请求确认
- [x] 5.2 将 delta 同步到 `openspec/specs/backend-common-utilities/spec.md`，验证权威 spec 记录 46 张表、MySQL 5.7/8.0 共用基线和空 schema 限制
- [x] 5.3 核对 `权限资源.txt` 无需变更，因为本次未新增、删除或改名任何菜单/按钮编码，并以新旧权限编码集合比较结果作为验证依据
