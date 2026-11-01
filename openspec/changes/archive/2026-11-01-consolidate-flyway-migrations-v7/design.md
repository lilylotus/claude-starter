## Context

当前迁移目录包含 v6 合并产出的 73 表 `V1__init_schema.sql`，以及 V2–V8 共 7 个增量脚本：

| 版本 | 内容 |
| --- | --- |
| V2 | `tab_wf_process_definition` 增加 `route_field_codes` 列 |
| V3 | 新建 `tab_file_upload` |
| V4 | `tab_wf_process_binding`：删除 UNIQUE 维度索引、新增 `status`、按 `enabled` 回填、删除 `enabled`；登记 `WorkflowDesign:binding:delete` 菜单/权限 |
| V5 | 为 SUPER_ADMIN 补授 `WorkflowDesign:binding:delete` |
| V6 | 登记 `ApprovalManagement:record:view` 菜单/权限并补授 SUPER_ADMIN |
| V7 | `tab_chat_message` 增加 `sender_identity_key_fingerprint`；新建 `tab_chat_user_key` |
| V8 | 新建 `tab_image`；登记"公共服务"分组 + 图片管理/图片展示菜单及 4 个权限点并补授 SUPER_ADMIN |

项目已有六次基线合并实践，权威 spec 要求迁移目录保持可维护的单一基线，且一份脚本同时兼容 MySQL 5.7 和 8.0。工作区当前另有未提交的 `backend/src/main/resources/application.yml`（本地数据源地址），本 change 不触碰该文件。

## Goals / Non-Goals

**Goals:**

- 用单一 V1 一步创建现有迁移链的最终 76 张业务表、索引、约束和全部最终种子数据。
- 直接表达最终字段、默认值、可空性和种子状态，不保留历史 `ALTER`、补授或回填过程。
- 在隔离 MySQL 空 schema 上证明旧链和新基线的 schema 及语义种子数据等价。
- 使迁移目录最终只保留 `V1__init_schema.sql`，并同步 OpenSpec 中的单基线契约。

**Non-Goals:**

- 不为已有数据的数据库提供原地升级、Flyway history 修复或数据保留方案。
- 不修改业务 Java/前端代码、接口、依赖、权限编码或菜单语义。
- 不把 Flowable 管理的 `ACT_*`/`FLW_*` 表纳入业务 Flyway 基线。

## Decisions

### Decision 1: 在现有 V1 上就地折叠增量

现有 V1 已是规整后的终态脚本，本次只需把 V2–V8 的差异就地并入：

- 列变更（V2、V7 的 `ADD COLUMN`）放进对应 `CREATE TABLE`，保持原 `AFTER` 指定的列顺序（`route_field_codes` 在 `form_version_id` 之后；`sender_identity_key_fingerprint` 在 `filtered` 之后）。
- `tab_wf_process_binding` 直接定义 `status INT NOT NULL DEFAULT 2000`（位于 `revision` 之后），删除 `enabled` 列，维度索引改为普通 `KEY idx_tab_wf_process_binding_dimension`；V1 内全局绑定种子 INSERT 由写 `enabled = 1` 改为写 `status = 2000`（与 V4 回填结果一致）。
- 新表 `tab_file_upload`、`tab_chat_user_key`、`tab_image` 放入所属模块的建表区（文件/图片作为公共服务模块，聊天密钥表紧随聊天表），建表语句总数变为 76。

备选方案是把 V2–V8 原样拼接在 V1 尾部，但会保留中间形态（如 `enabled` 回填），不满足单基线"直接表达终态"的要求。

### Decision 2: 新权限种子并入既有种子块，由 SUPER_ADMIN 全量授权覆盖

- `WorkflowDesign:binding:delete` 菜单/权限紧随 `WorkflowDesign:binding:edit` 插入。
- `ApprovalManagement:record:view` 菜单（`approval` 分组下，show_order=5）与权限并入审批管理种子块。
- "公共服务"分组（`common-service`，show_order=50）、图片管理/图片展示页面及上传/删除按钮菜单和 4 个权限点放在 SUPER_ADMIN 授权之前的独立小节。
- 全部位于 `INSERT INTO tab_role_permission ... SELECT ... FROM tab_permission` 之前，因此不再需要 V5/V6/V8 中的分段补授语句。菜单名、编码、父子关系、资源类型、show_order、备注与旧链完全一致。

### Decision 3: 双路径等价验证

在本地 MySQL 5.7.40 上建立两个显式命名的隔离空 schema（如 `rbac_merge_legacy`、`rbac_merge_baseline`），不使用 `application.yml` 中的业务 schema：

- legacy schema 按顺序执行保存的 V1–V8 只读副本；baseline schema 仅执行新 V1。
- 比较 `information_schema` 的表、列（类型/可空/默认值/注释/顺序）、索引；按稳定业务键（菜单/权限/角色 code 等）归一化后比较种子数据；核对权限编码集合与 `权限资源.txt` 一致且 SUPER_ADMIN 全量授权。
- 指向 baseline schema 运行 `RbacApplicationTests`，确认 Flyway 只记录成功的 V1。
- 本地无 MySQL 8.0 实例时，8.0 验证标记为未执行并在结果中如实报告；脚本仅使用两版本共同语法子集（静态扫描兜底）。

### Decision 4: 已执行旧迁移链的数据库不切换新基线

替换已执行的 V1 会改变校验和，删除 V2–V8 也会改变迁移历史。保留数据的数据库继续使用匹配旧校验和的发布物；只有明确可丢弃数据的环境才能完整删除并重建 schema 后使用新 V1。

## Risks / Trade-offs

- [菜单 id 自增顺序变化] → 新菜单插入位置前移会改变自增 id，但所有引用均通过 code 子查询解析，比较以稳定业务键为准。
- [种子 `create_by` 等审计字段差异] → V5/V6/V8 补授与全量授权均使用 `'1'`，预计无差异；若有差异在验证结果中记录。
- [MySQL 8.0 未实测] → 只使用共同语法，并在结果中明确标注。
- [新制品用于已有库会触发 Flyway 校验失败] → 在 V1 文件头和交付说明中显著声明空 schema 限制。

## Migration Plan

1. 保存 V1–V8 只读副本与 SHA-256。
2. 就地重写 V1，删除 V2–V8。
3. 静态核对 76 张表、0 条 `ALTER TABLE`、权限编码集合与 MySQL 5.7 禁用语法。
4. 在隔离 schema 上执行 legacy/new 双路径并比较结构与种子数据。
5. 运行上下文测试，再依据真实结果同步 OpenSpec 文档。

代码回滚通过 Git 恢复旧的 8 文件迁移集合。

## Open Questions

无。若实施开始前迁移目录新增或修改脚本，则视为范围变化，需先更新本 change 并再次确认。

## Validation Results

- MySQL 5.7.40：legacy V1–V8 与新 V1 均执行成功；76 张表的表/列/索引元数据零差异（含 `route_field_codes`、`sender_identity_key_fingerprint` 列位置，以及 `tab_wf_process_binding` 的 `status` 列与 `idx_tab_wf_process_binding_dimension` 普通索引）。
- 种子数据：20 个非空表行数一致（tab_menu 157、tab_permission 151、tab_role_permission 151、tab_wf_process_binding 20 等）；排除自增 id/时间戳并解析引用 code 后零差异，20 条全局绑定 `status` 均为 2000。审计字段 `create_by/update_by` 均为 `'1'`，无差异。
- 权限编码集合与 `权限资源.txt` 151 个唯一编码（含四段式）完全一致。
- `RbacApplicationTests` 在隔离空 schema 上通过，Flyway history 仅有 V1；验证用 schema 已删除。
- MySQL 8.0：未验证（环境不可用），tasks 5.2 保持未完成。
