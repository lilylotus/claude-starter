## Context

见 proposal.md - Why。这是对已归档的 `app-api-credentials-config` change（能力 `app-api-credentials`）的一次后续增强，把"同步配置"从简单的四个布尔开关升级为按数据域配置字段级映射。

用户在澄清问题里确认的关键决策（详见 proposal.md）：
1. 数据域：组织/用户/应用/角色四个支持字段级配置，字典保留为布尔开关（共 5 个数据域）。
2. 字段来源：复用 `metadata-field-management` 元数据字段目录，新增 `bizType=ROLE`。
3. 分页大小：每个数据域各一份。
4. 转换脚本：JavaScript，用 GraalJS 做静态语法校验（不执行），已确认新增 `org.graalvm.polyglot:polyglot` + `org.graalvm.polyglot:js-community` 依赖。

## Goals / Non-Goals

**Goals:**
- 5 个数据域（组织/用户/应用/角色/字典）各自独立的启用开关 + 拉取分页大小配置。
- 组织/用户/应用/角色 4 个数据域支持字段级同步映射：选择源字段（来自元数据字段目录）、填写应用侧目标字段名称/编码、选择转换方式（不转换/固定值/转换脚本）。
- 转换脚本保存前做 JavaScript 语法校验，语法错误时拒绝保存。
- 角色纳入元数据字段目录（`bizType=ROLE`），预置 `tab_role` 的 `name`/`code`/`show_order`/`remark`。
- 停用元数据字段时，若被应用同步字段映射引用，同样拒绝停用（与被表单字段定义绑定时拒绝停用的既有规则并列）。

**Non-Goals:**
- 不实现真正的数据拉取、字段转换执行、对外通知发送——转换脚本只做静态语法解析，不执行；分页大小只是存储的配置值，不驱动任何真实分页拉取逻辑。
- 不把角色纳入 `FormFieldBizType`／动态表单渲染管线——角色管理页面本身不使用动态字段渲染，`bizType=ROLE` 的元数据字段目录只服务于本次的同步字段映射选择。
- 不做字段映射的按行独立 CRUD 接口——采用整体替换（全量提交当前数据域的映射列表）。
- 不做转换脚本的沙箱执行环境、超时控制等运行时能力——语法校验之外的一切执行相关能力都留给后续实现"真正同步"的 change。

## Decisions

### 1. 用独立表 `tab_app_sync_domain_config` 替换 `tab_app_config` 上的四个布尔列，而不是加列

`tab_app_config` 已经有 `sync_org_enabled`/`sync_user_enabled`/`sync_app_enabled`/`sync_dict_enabled` 四个布尔列。这次要给 5 个数据域（新增角色）各自加一个"分页大小"，如果继续加列会变成 `sync_org_enabled`/`sync_org_page_size`/`sync_user_enabled`/`sync_user_page_size`/... 共 10 列，且未来如果再加数据域还要继续加列对（不可扩展）。改用一张"应用 id + 数据域"为唯一键的子表 `tab_app_sync_domain_config`（每个数据域一行，`sync_domain` + `sync_enabled` + `page_size`），横向扩展只需要多插入一行，不需要改表结构。

备选方案：继续在 `tab_app_config` 上加列（`sync_role_enabled`、5 个 `*_page_size` 列）。放弃原因：列数膨胀、新增数据域需要 `ALTER TABLE`，且 `page_size` 与 `enabled` 概念上是"同一个数据域的两个属性"，用行而不是列表达更自然，也更符合"多表按数据域关联查询"的既有模式（参照 `[[feedback_multi_table_query_xml]]` 的既有约定，本次子表查询同样走 MyBatis XML）。

### 2. `sync_domain`/`sync_enabled` 列名，不用 `domain`/`enabled`

`DOMAIN` 是 SQL 标准（SQL-92/SQL:1999）里的保留字，虽然 MySQL 本身不禁止且仓库建表语句统一加反引号可以规避冲突，但按仓库既有约定"检查是否和各类型数据库关键字冲突"，直接用更具体的 `sync_domain`/`sync_enabled` 列名从根源上避免歧义，Java 字段对应 `syncDomain`/`syncEnabled`。

### 3. 迁移策略：新增 V7 建表 + 回填 + 删列，不修改已应用的 V3/V6

`V3__app_config.sql`（建 `sync_*_enabled` 四列）与 `V6__app_sync_notify_config.sql` 已经在开发库执行过，按 Flyway 约定不可再编辑（参照已归档 `app-api-credentials-config` design.md Decision 9 的同款处理方式）。新增 `V7__app_sync_domain_config.sql`：
1. `CREATE TABLE tab_app_sync_domain_config`；
2. 用 `INSERT ... SELECT` 把 `tab_app_config` 每一行的 `sync_org_enabled`/`sync_user_enabled`/`sync_app_enabled`/`sync_dict_enabled` 展开成 4 行（`sync_domain` 分别为 `ORG`/`USER`/`APP`/`DICT`），外加 1 行 `sync_domain='ROLE'`、`sync_enabled=0`（角色数据域是本次新增，存量应用没有对应的历史值，默认不启用），5 行的 `page_size` 均取默认值 20；
3. `ALTER TABLE tab_app_config DROP COLUMN sync_org_enabled, DROP COLUMN sync_user_enabled, DROP COLUMN sync_app_enabled, DROP COLUMN sync_dict_enabled`（四个旧布尔列的数据已经搬到新表，删除避免同一份"是否启用"数据在两张表里各存一份）。

`AppConfigEntity` 相应删除这四个 Java 字段；`AppConfigServiceImpl.createDefaultConfig`/`updateSyncConfig`/`toLogSnapshot` 相应移除对它们的读写。

### 4. `tab_app_sync_field_mapping` 只存 `metadata_field_id`，源字段名称/编码实时 JOIN 读取，不做快照

参照已归档 `form-field-definition-management` 能力的既定模式（"字段定义的字段标识完全派生自绑定的元数据字段"）：如果把源字段的 `fieldName`/`fieldCode` 快照进 `tab_app_sync_field_mapping`，元数据字段目录后续编辑展示名称时，已保存的映射行会显示过期的名称。改为只存 `metadata_field_id` 外键，查询时 JOIN `tab_metadata_field` 取最新的 `fieldName`/`fieldCode`/`tableName`/`columnName`。这个 JOIN 查询按仓库既有约定（`[[feedback_multi_table_query_xml]]`）写在 MyBatis XML（`AppSyncFieldMappingMapper.xml`），不在 Java 侧批量查询后手工合并。

### 5. 字段映射保存采用"整体替换"语义，不做按行 CRUD 接口

参照仓库里 `tab_role_permission` 的既定模式（"角色新增/更新时整体同步：先按 role_id 物理删除既有关联，再按提交内容重建，不做按行 diff"）：保存某个应用某个数据域的字段映射时，`PUT /api/apps/{id}/config/sync/field-mappings?domain=ORG` 接收当前完整的映射行列表，服务层先按 `(app_ref_id, sync_domain)` 物理删除旧行，再按提交内容批量插入新行，整个操作在一个事务内完成。

备选方案：为每一行提供独立的新增/编辑/删除接口。放弃原因：前端交互本身就是"整张表格编辑完再点保存"（与同步配置 tab 内其他区块一致的保存节奏），按行接口没有对应的独立触发点，徒增接口数量和前后端状态同步的复杂度；`tab_role_permission` 已经验证过这个模式在本仓库是可行且被接受的。

### 6. 转换脚本语法校验：GraalJS `Context.parse`，纯 Java 依赖，不做执行

新增依赖 `org.graalvm.polyglot:polyglot` + `org.graalvm.polyglot:js-community`（GraalJS 的"社区版"，纯字节码解释执行，不依赖 GraalVM 原生编译能力，可在标准 OpenJDK 21 上运行，不需要切换到 GraalVM 发行版）。校验逻辑：

```java
try (Context context = Context.newBuilder("js").build()) {
    context.parse(Source.newBuilder("js", scriptText, "sync-transform.js").build());
}
```

`Context.parse` 只做静态解析（语法树构建），不调用 `eval`/`Value.execute`，因此不会执行脚本里的任何代码——满足"只校验语法，不执行"的范围边界。解析失败抛 `PolyglotException`，转换为 `BusinessException` 返回给前端。校验逻辑封装为 `app/sync/support/TransformScriptValidator`，无状态工具类（参照 `AppCredentialGenerator` 的静态工具类风格，不注册为 Spring bean）。

### 7. 角色元数据字段：新增 `bizType=ROLE`，但不加入 `FormFieldBizType`

`metadata-field-management` 目前覆盖 `FormFieldBizType` 定义的 ORG/USER/POSITION/APP 四类，是"表单字段定义"动态渲染管线的数据来源。角色管理页面本身是固定字段（`name`/`code`/`show_order`/`remark`/`status`），没有 `ext1`~`ext10` 扩展列，也没有接入 `form-field-definition-management` 的动态渲染——本次给角色加元数据字段目录，唯一目的是让"应用同步字段映射"可以选择角色的哪些字段参与同步，与表单字段定义体系无关。因此 `bizType=ROLE` 的元数据字段记录只服务于 `tab_app_sync_field_mapping.metadata_field_id` 的可选来源，**不**把 `ROLE` 加进 `FormFieldBizType` 常量类（那个类专属于表单字段定义体系的 4 个业务对象类型枚举），避免造成"角色支持动态表单字段"的错误暗示。

`GET /api/metadata-fields?bizType=ROLE` 复用已有的分页查询接口即可获取角色元数据字段列表，不新增专属查询接口。

### 8. 字段映射选择源字段时复用现有"分页查询"接口，不复用"可用字段"（`available`）接口

`metadata-field-management` 现有的 `GET /api/metadata-fields/available?bizType=X` 语义是"未被任何有效表单字段定义绑定"，专为表单字段定义的新增/改绑下拉框设计——组织/用户/应用下常用的核心字段（如 `code`/`name`）通常已经被表单字段定义绑定（承重字段），如果同步字段映射的选择器复用 `available` 接口，这些核心字段反而不会出现在可选列表里，这是不对的：字段映射的"字段是否被占用"逻辑和表单字段定义完全独立（一个元数据字段可以同时被表单字段定义绑定、又被多个应用的同步映射引用）。因此前端选择源字段时改用现有的分页查询接口 `GET /api/metadata-fields?bizType=ORG&pageSize=200`（一次性拉一页，数量级在几十条以内，不需要真正的分页交互），前端过滤 `status=2000`（启用）后展示为下拉选项，不新增后端接口。

### 9. 停用元数据字段的守卫规则扩展到同步字段映射

现有 `MetadataFieldServiceImpl.disable` 只检查 `formFieldDefinitionMapper.existsActiveByMetadataFieldId(id)`。新增字段映射体系后，如果某个元数据字段正被某个应用的同步映射引用，停用它会让映射行的"源字段"信息（`fieldName`/`fieldCode`，Decision 4 里是 JOIN 实时读取）变得不可靠——前端展示时要么要额外处理"源字段已停用"的降级展示，要么直接在源头堵住。选择直接在源头堵住：`disable` 方法追加一次 `appSyncFieldMappingMapper.existsByMetadataFieldId(id)` 校验，被引用时拒绝停用（错误文案区分于表单字段定义占用的提示，如"已被应用同步字段映射引用，无法停用"）。`app/sync` 模块因此对 `metadata` 模块产生一个新的 Mapper 级依赖（单向：`MetadataFieldServiceImpl` 注入 `AppSyncFieldMappingMapper`），与 `MetadataFieldServiceImpl` 已经注入 `FormFieldDefinitionMapper` 的既有模式一致。

### 10. 前端"数据范围"区块改版为左侧纵向 tabs

`AppConfigView.vue` 的"同步配置" tab 内，原来纵向排列的四个 `el-switch` 改为一个内嵌的 `el-tabs`（`tab-position="left"`），5 个子 tab 对应组织/用户/应用/角色/字典。每个子 tab 右侧展示：启用开关、拉取分页大小（`el-input-number`，最小 1）；组织/用户/应用/角色额外展示字段映射表格（`el-table`，行内编辑应用字段名称/编码 + 转换方式下拉，转换方式为固定值/脚本时该行展开或在下方展示对应的取值输入）与"新增字段"按钮（打开一个从元数据字段目录选择源字段的下拉/弹窗）。每个子 tab 有独立的"保存"按钮，切换子 tab 不提交未保存的改动（与外层三个大 tab 各自独立保存的既有交互习惯一致），复用同一个 `AppManagement:app:config:editSync` 权限点控制展示。

备选方案：字段映射表格做成弹窗而不是内嵌。放弃原因：一个数据域下字段映射条目可能有十几条，弹窗内还要嵌套编辑体验不如内嵌表格直接，且与仓库"应用配置"页面偏好独立路由/内嵌区域而非弹窗的既有风格（design.md 已归档 change 里"配置"用独立页面而非弹窗）一致。

## Risks / Trade-offs

- [风险] `V7` 迁移的 `ALTER TABLE ... DROP COLUMN` 是不可逆操作，一旦执行历史的四个布尔值就不再以原始列形式存在（已通过同一迁移文件内先回填新表再删列的顺序保证不丢数据，但如果新表回填逻辑写错，没有"回滚重跑"的余地，只能再写一个修复迁移）→ 可接受：回填逻辑是简单的 `INSERT ... SELECT`，风险可控；且这是 `V3`/`V6` 已应用迁移在 Flyway 约束下的唯一可行路径（Decision 3）。
- [权衡] 转换脚本只做语法校验、不做任何"执行结果预览"，管理员填错转换逻辑（语法正确但语义错误，如引用了不存在的输入变量）在保存阶段发现不了 → 与本次"只做配置存储"的范围边界一致（proposal.md Non-Goals），等后续真正实现同步执行的 change 上线后才会暴露；如需要提前发现，后续可以加一个"用示例数据试跑一次"的功能，属于增量能力。
- [权衡] 字段映射保存是整体替换（先删后插），高并发下两个管理员同时编辑同一个应用同一个数据域的映射会互相覆盖 → 可接受：与仓库里 `tab_role_permission` 已验证的既有模式一致，应用配置本身是低频、单人操作的管理后台场景，不需要乐观锁/冲突检测这类复杂度。
- [风险] GraalJS 依赖（`polyglot` + `js-community`）会增加构建产物体积和 JVM 启动时的类加载开销 → 可接受：两个依赖都是纯 Java 实现（用户已确认，不需要 GraalVM 原生发行版），体积和启动开销在可控范围内，是"做语法校验"这个需求下最直接的实现路径。
