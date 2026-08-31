## Context

见 `proposal.md` 的 Why。当前 V1 是 `consolidate-flyway-migrations-v4` 形成的 40 表基线，V2–V14 又新增 6 张表并对既有结构、种子数据进行多轮修改：

- V2–V4：新增 SSO 协议日志，并为登录/SSO 日志补充会话标识，为策略与拒绝日志补充优先级和拒绝来源。
- V5、V7：新增插件管理与四类主数据 Excel 导出菜单/权限种子，并补授超级管理员。
- V6：表单字段新增 `show_in_export`，存量值从 `show_in_list` 回填。
- V8–V10：新增审批申请/审批开关表及审批菜单权限，随后修正“待我审批”菜单层级并把审批默认值改为关闭。
- V11：组织表新增三类路径字段与索引，并以 20 轮更新回填存量组织路径。
- V12–V14：新增全局变更流水、同步元数据、同步游标三张表；为五类同步实体增加版本，为应用配置增加纪元，为通知记录增加事件、重试和租约字段并将 `notify_status` 改为可空；最后为字典项增加版本。

最终表数量为 40 + 1（V2）+ 2（V8）+ 3（V12）= 46。V1 原有 118 个权限点，V5、V7、V8 分别增加 1、4、4 个，最终为 127 个。项目仍要求 SQL 兼容 MySQL 5.7；用户追加要求兼容 MySQL 8，因此必须用一份共同语法子集脚本在两个版本上验证。

工作区中的 `application.yml`、`gradle.properties` 和 Flowable OpenSpec change 存在其他未提交改动，本 change 不覆盖或夹带这些内容。`app-sync-changelog-pull` change 当前仍未归档，但其已完成的 V12–V14 是本次基线终态来源，合并只改变文件组织，不改变其需求语义。

## Goals / Non-Goals

**Goals:**

- 用一个新的 V1 一步创建当前 V1–V14 的最终 46 表结构和全部种子数据。
- 在 MySQL 5.7 与 MySQL 8.0 上证明新 V1 与旧 V1–V14 顺序执行的 schema 和基线数据等价。
- 删除只用于历史演进的 V2–V14，同时通过 Git/OpenSpec 保留来源可追溯性。

**Non-Goals:**

- 不提供保留现有数据的旧库升级或 Flyway history 修复方案。
- 不修改业务代码、数据模型语义、权限编码、菜单文案或运行配置。
- 不把 Flowable 的 `ACT_*`/`FLW_*` 表纳入业务 Flyway 基线。
- 不借合并机会重命名表、列、索引或统一历史 SQL 风格。

## Decisions

### Decision 1: 继续用 V1 表示空库最终基线，删除 V2–V14

延续既有四次 consolidation：V1 表示“从零建立当前版本数据库”的基线，V2–V14 的历史演进由 Git 与 OpenSpec 追溯。新 V1 内按“先建表、后按依赖顺序插入种子数据”组织，不保留 `ALTER`、存量 `UPDATE` 或先插后修正过程。

备选方案是新建 V15 汇总脚本但保留旧迁移。未采用，因为 V15 无法代替 V1–V14，新环境仍需执行全部历史文件，不能实现单脚本目标。

### Decision 2: 结构按最终形态直接写入

受 V2–V14 影响的既有表按以下终态修改 V1 建表定义：

- `tab_login_log` 含可空 `session_id` 及其索引。
- `tab_app_access_policy` 含非空、默认 0 的 `show_order`。
- `tab_form_field_definition` 含非空、默认 1 的 `show_in_export`。
- `tab_org` 含 `org_path`、`org_name_path`、`org_parent_path`、`version` 及两个路径索引。
- `tab_user`、`tab_user_position`、`tab_app`、`tab_role` 各含默认 1 的 `version`。
- `tab_app_config` 含默认 0 的 `config_epoch`。
- `tab_app_notify_record` 含 V12 的事件/流水/实体版本/请求体/任务状态/重试/租约字段和两个索引，且 `notify_status` 最终为可空。
- `tab_dict_item` 含默认 1 的 `version`。

新表 `tab_sso_protocol_log` 直接同时包含 V3 的 `session_id`、V4 的 `denied_policy_id` 及全部最终索引；`tab_approval_request`、`tab_approval_switch`、`tab_app_data_change_log`、`tab_app_sync_metadata`、`tab_app_sync_cursor` 按最终定义纳入，使总数达到 46。

备选方案是在 V1 尾部原样附加 V2–V14。未采用，因为这仍保留中间状态与冗余回填，不是规整后的最终基线。

### Decision 3: 种子数据直接表达 V14 终态

- 表单字段种子 INSERT 增加 `show_in_export`，每行初始值取该行 `show_in_list`，等价于 V6 的存量回填。
- 审批开关列默认值和 ORG/USER/POSITION/APP 四条种子均直接使用 0，不保留 V8 的“先开启”与 V10 的回填。
- `ApprovalManagement:request:approve` 直接作为“审批管理”下“待我审批”的二级页面资源，不先作为“我的申请”按钮再由 V9 更新。
- 插件管理、四个 Excel 导出按钮、审批管理菜单和对应权限点并入既有菜单/权限种子块；超级管理员的 `INSERT ... SELECT id FROM tab_permission` 保持在全部 127 个权限点插入之后，因此不保留 V5/V7/V8 的补授语句。
- `tab_app_sync_metadata` 保留 `CHANGE_LOG_RETENTION_FLOOR_SEQ=0` 基线种子。
- V11/V14 面向存量数据的路径和版本 UPDATE 不进入新 V1。经实施前清单核对，V1–V14 没有任何 `tab_org` 种子记录，因此空库基线只需定义组织路径列和索引，不需要路径回填或组织种子；所有新建字典项依靠列默认值获得版本 1。

备选方案是保留所有 UPDATE 以降低人工改写量。未采用，因为空库基线中的中间回填会掩盖错误的初始 INSERT，也违背单基线直接表达终态的要求。

### Decision 4: 使用“双路径、双数据库版本”验证等价性

在 MySQL 5.7 和 MySQL 8.0 各准备两个独立空 schema：legacy schema 执行修改前的 V1–V14，baseline schema 只执行新 V1。对每一数据库版本比较：

- 表、列、数据类型、可空性、默认值、主键、索引和唯一约束。
- 46 张表数量及关键新表/字段清单。
- 基线种子数据按稳定业务键比较，忽略自增 id、`NOW()` 生成时间和 `flyway_schema_history` 校验和等必然差异。
- `tab_permission` 为 127 条，超级管理员权限关联完整；关键菜单编码、层级、资源类型一致。
- `tab_approval_switch` 四条记录均关闭，`tab_org` 在空库基线中无种子记录，同步元数据种子存在。

所有验证 schema 必须使用显式、唯一的临时名称，且不得指向 `application.yml` 当前配置的 `rbac` schema。删除临时 schema 前必须再次核对解析后的绝对连接目标和 schema 名称；测试无法提供两个 MySQL 版本时，兼容性任务保持未完成并报告环境缺口。

备选方案是只跑 Spring 上下文测试。未采用，因为上下文启动只能证明一条路径可执行，无法证明旧迁移链与新基线的结构和种子数据等价。

### Decision 5: 已有数据环境不执行新基线

Flyway 会记录版本、描述和校验和。替换已执行的 V1 并删除 V2–V14 会使已有数据库的历史校验失败；仅删除 `flyway_schema_history` 还会让新 V1 在已有表和种子数据上重复执行，可能产生冲突或部分成功。因此本次交付只支持全新 schema 或明确可完整重建的环境。

备选方案是自动执行 `flyway repair` 或删除 history 后重跑。未采用，因为它无法证明业务表已经处于新 V1 的精确终态，也不能安全处理重复种子数据。

## Risks / Trade-offs

- [46 张表和大量种子数据人工整合容易遗漏] → 以旧链和新基线的双路径 schema/data 对比作为主要验收，不仅依赖目视检查。
- [自增 id 与 NOW() 时间使逐行二进制比较产生假差异] → 用 code/biz_type/metadata_key 等稳定业务键比较，忽略非语义生成值，同时检查引用关系解析结果。
- [MySQL 5.7 与 8.0 对默认值、字符集或保留字处理存在差异] → 保留反引号与现有 utf8mb4 定义，只采用两版共同语法，并分别真实执行。
- [活动中的 `app-sync-changelog-pull` 仍以 V12–V14 记录其原始实现] → 将这些引用视为历史实施记录，不回写该 change；本 consolidation 的 proposal/design 单独记录文件被后续合并，避免篡改先前 change 的时间线。
- [已有数据库随新制品启动时 Flyway validation 失败] → 将 breaking 限制写入基线文件头和发布说明；已有数据环境继续使用旧迁移集合，不通过 repair 绕过。

## Migration Plan

1. 保存修改前 V1–V14 的内容与哈希，建立终态清单。
2. 重写 V1 为 46 表最终基线，整合最终种子数据，并删除 V2–V14。
3. 在 MySQL 5.7 与 8.0 的隔离空 schema 上完成 legacy/new 双路径对比和 Flyway 执行验证。
4. 运行后端测试，并基于真实结果同步 OpenSpec 文档和表/权限点计数。
5. 新环境直接使用新 V1；可丢弃数据的开发/测试环境完整重建 schema 后使用新 V1；已有数据环境继续使用旧发布物。

回滚代码时通过 Git 恢复旧 V1–V14。数据库层面不对已经用新 V1 建成的环境原地回滚；需要回退时删除该可重建 schema，再用旧迁移链重建。

## Validation Results

- MySQL 5.7.44：legacy V1–V14 与新 V1 均成功，46 张表、564 列元数据、173 条索引元数据、94 条约束元数据的对比均为零差异。
- MySQL 8.0.46：使用同一新 V1 重复上述双路径验证，表、列、索引和约束对比均为零差异。
- 两版本的 legacy/new 均包含 127 个权限点和 127 条超级管理员授权；除 `tab_role_permission.id` 因合并后插入顺序不同而变化外，其他种子表逐行一致，授权按“角色编码 + 权限编码”归一化后零差异。
- 隔离 MySQL 8.0 schema 上的 `RbacApplicationTests` 与后端全量 Gradle 测试均通过；Flyway 历史只有 V1，未连接或清理 `application.yml` 当前配置的 `rbac` schema。
