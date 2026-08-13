## Context

本系统目前只有"数据流出"方向的同步能力（`app-api-credentials` + `app-sync-notify-pull`：本系统的组织/用户/任职/应用/角色变更，按需通知或供外部应用拉取）与一次性的批量导入能力（`excel-import-export`：管理员手工上传 Excel，`ImportRowExecutor` 按 `bizType` 路由到组织/用户/任职/应用各自的"按编码匹配、存在则更新/不存在则新增"逻辑，复用各业务模块既有 `create`/`update` service）。本次要新增的是"数据流入"方向、持续运行的能力：从外部上游系统按接口或数据库表两种方式之一，定期拉取组织/用户/任职三类数据，用与 Excel 导入相同的匹配算法落库。

可直接复用的既有基础设施：
- `Sm4JdkUtils` + `AppSecretProperties`（SM4 加密，目前用于 `AppConfigEntity.secretKey` 落库前加密）。
- `FieldMappingTransformer`（GraalVM JS 沙箱执行转换脚本，`value` 全局变量绑定源值、最后一个表达式为结果，200ms 超时保护）、`TransformScriptValidator`（保存前静态语法校验）。
- 元数据字段目录查询（`metadata-field-management`，`MetadataFieldEntity` 已有 `bizType` 区分 ORG/USER/POSITION/APP/ROLE，`fieldCode` 与对应实体/DTO 的 Java 属性名一一对应，`app.sync` 模块已经在用它做字段映射的"源字段目录"）。
- `RbacApplication` 已 `@EnableScheduling`，`sync.sign.NonceStore` 已有一个 `@Scheduled` 定时清理任务的先例。
- `OrgService`/`UserService`/`PositionService` 现有 `create`/`update`，内含 Bean Validation、唯一性校验、锁定字段保护等既有规则。
- `mysql-connector-j` 已是项目依赖（`backend/build.gradle`）。

## Goals / Non-Goals

**Goals:**
- 支持配置多个"上游数据源"，每个数据源二选一同步方式（接口 / 数据库表），三个数据域（组织/用户/任职）各自独立启用、独立取数来源、独立字段映射。
- 定时调度（按间隔或按每日固定时间点）+ 手动立即触发，一次同步按组织→用户→任职顺序处理。
- 落库复用既有"按编码匹配、存在则更新/不存在则新增"语义，不引入新的一套校验规则。
- 敏感配置（DB 密码、接口鉴权 header 值）加密落库。
- 同步执行留痕，供问题排查。

**Non-Goals:**
- 不支持上游侧分页协议、不做大数据量的流式/分批拉取——一次同步一次性取回上游返回的全部数据，数据量控制由管理员自己在 SQL `WHERE` 条件或上游接口里处理。
- 不处理"上游侧记录消失"的停用/删除语义——本次同步只做新增/更新。
- DB 模式只支持 MySQL，不做多数据库驱动抽象。
- DB 模式不提供"连接测试后选表选列"的可视化配置器，管理员自己填写 SQL。
- 不对外暴露任何 OpenAPI（这是管理端内部功能，"上游"是别的系统，本系统是调用方/数据库客户端，不是被调用方）。
- 不做同步失败的自动重试/告警通知（同步记录仅供管理员自行查看排查，与 `tab_app_notify_record` 定位一致）。

## Decisions

### Decision 1：数据模型——一个数据源 = 一套连接信息 + 三个数据域子配置 + 各自的字段映射

比照 `app.sync` 模块"一个应用 = 一套凭证 + 六个数据域子配置（含各自字段映射）"的既有结构，新增四张表：

- `tab_upstream_source`：数据源主表。字段：`name`（数据源名称）、`sync_type`（`API`/`DB_TABLE`）、`enabled`、调度配置（`schedule_type`：`INTERVAL`/`FIXED_TIME`；`interval_unit`：`MINUTE`/`HOUR`；`interval_value`；`fixed_time`，`HH:mm` 文本）、`last_trigger_time`（上次触发时间，供轮询任务判断是否到点）、接口模式专属列（`api_auth_headers`，JSON 文本存自定义请求头 key-value）、数据库模式专属列（`db_jdbc_url`、`db_username`、`db_password`，SM4 加密落库）。接口模式的自定义请求头值、数据库模式的密码，均视为敏感信息，同一套 SM4 主密钥加密（复用 `AppSecretProperties` 的 `sm4Key` 配置，不新增一把独立密钥——两者都是"落库前加密、用时解密"的同类需求，没必要拆分主密钥管理复杂度）。连接配置更新接口（`UpstreamConnectionConfigRequest`）两侧的"不回显明文如何支持修改"处理方式不同：请求头字段本身就是零散的 key-value 集合，天然按"整体替换"处理（提交的完整集合替换已保存集合，管理员想改一个 key 就要把全部 key 重新带上）；密码是单一取值，整体替换会强制管理员每次都重新输入密码，体验更差，改为"留空表示不修改已保存值"（非空时才落库更新）。DB 模式的 JDBC URL/用户名走同一个连接配置更新接口，非敏感、正常展示明文，不受上述"不回显"约束。
- `tab_upstream_domain_config`：数据域子配置，`(source_id, data_type)` 唯一，`data_type` 取值 `ORG`/`USER`/`POSITION`（复用 `app.sync.constant.SyncDomain` 里已有的常量定义，或抽出一个更小的三值常量，见 Decision 6）。列：`enabled`、接口模式的 `api_url`（该数据域的请求地址）、`api_method`（`GET`/`POST`）、数据库模式的 `db_sql`（该数据域的查询语句）、`last_sync_time`（该数据域上次同步完成时间，仅展示用途，不驱动增量逻辑——本次不做增量，见 Non-Goals）。
- `tab_upstream_field_mapping`：字段映射，`(source_id, data_type)` 分组，每行：`upstream_field_name`/`upstream_field_code`（管理员手工填写，无目录可选）、`metadata_field_id`（关联元数据字段，取其当前 `fieldName`/`fieldCode` 作为"系统字段名称/系统字段编码"展示，实时读取不做快照——与 `app.sync` 字段映射对元数据字段的引用方式一致）、`transform_type`（`NO_TRANSFORM`/`FIXED_VALUE`/`SCRIPT`，复用 `app.sync.constant.TransformType` 同款三值）、`transform_value`。保存语义同样是"整体替换"（先清空该数据源该数据域下的全部映射行，再按提交内容重新写入），与 `app.sync` 字段映射的既有保存语义一致。
- `tab_upstream_sync_record`：同步执行记录。列：`source_id`、`data_type`（该次记录针对哪个数据域，一次同步执行会为组织/用户/任职分别各写一条，而不是一条大记录笼统汇总——粒度对齐"三个数据域独立处理"）、`trigger_type`（`SCHEDULE`/`MANUAL`）、`start_time`/`end_time`、`status`（`SUCCESS`/`PARTIAL`/`FAILED`）、`total_count`/`success_count`/`fail_count`、`fail_summary`（失败摘要文本，截断到合理长度，不是完整堆栈）。

字段映射与拉取来源都按数据域拆分（而不是一个数据源共用一份字段映射/一个请求地址），是因为组织/用户/任职三者的字段集合、上游接口路径或数据库表天然不同，不可能共用同一份映射或同一个 URL/SQL——这一点和 `app.sync` 的"六个数据域各自字段映射"是同构的，直接照搬结构。

- **备选方案**：三个数据域各自建一个独立的"数据源"（不共享连接信息）。未采纳——多数企业只有一个上游身份系统，组织/用户/任职从同一个系统的不同接口/表里取，共享连接信息（同一个 JDBC 连接串、同一套接口鉴权 header）更符合实际场景，也和用户需求描述"支持同步组织、用户、任职"（作为一个数据源的三个能力面）的表述一致。

### Decision 2：定时调度——单个 `@Scheduled` 轮询 tick，不引入 Quartz（用户已确认）

新增一个 `@Scheduled(fixedRate = 60_000)` 的轮询任务（`UpstreamSyncScheduler`，仿照 `NonceStore` 的既有写法），每分钟执行一次：查询所有 `enabled=true` 的数据源，逐个判断是否到达同步时间点：
- `INTERVAL` 类型：`now - last_trigger_time >= interval_value * (MINUTE ? 60_000 : 3_600_000)`（`last_trigger_time` 为空视为立即到期，数据源刚启用/刚创建时首次轮询即触发一次）。
- `FIXED_TIME` 类型：`now` 的 `HH:mm` 等于配置的 `fixed_time` 且 `last_trigger_time` 不在同一天内已经触发过（避免同一分钟内 tick 抖动或轮询延迟导致同一天重复触发多次）。

到点即调用同步执行引擎（Decision 3），执行完成后更新 `last_trigger_time`。轮询 tick 与"立即同步一次"手动触发共用同一个执行引擎入口，只是 `trigger_type` 不同。轮询任务捕获每个数据源级别的异常（不让一个数据源的异常中断其余数据源的轮询判断），异常情况下仍然记录一条 `FAILED` 的同步记录。

- **备选方案**：引入 Quartz，为每个数据源注册独立的 `Trigger`。未采纳（用户已确认）——当前调度需求（分钟/小时级间隔、每日固定时间点）用轮询 tick 完全可以满足，不需要 Quartz 的 cron 表达式灵活性、错过执行补偿、集群协调等能力，避免为了不需要的灵活性引入新依赖和其运行时复杂度（misfire 策略、JobStore 配置等）。

### Decision 3：同步执行引擎——按数据域顺序拉取 + 复用既有 Service 落库，不复用 `ImportRowExecutor` 本身

新增 `UpstreamSyncExecutor`（建议放在 `cn.nihility.rbac.identity.upstream.support`），核心方法 `syncSource(Long sourceId, String triggerType)`：
1. 查询数据源配置与三个数据域子配置，按 `ORG → USER → POSITION` 固定顺序，跳过未启用的数据域。
2. 每个数据域：调用取数组件拉到原始行列表（`List<Map<String, Object>>`，key 为管理员填写的"上游字段编码"）——接口模式用 `UpstreamHttpFetcher`（内部用项目已有的 `HttpClientUtils` 发起请求，按配置的 `Content-Type: application/json` 解析响应体为 JSON 数组），数据库模式用 `UpstreamJdbcFetcher`（`DriverManager.getConnection` + `Statement.executeQuery`，按 `ResultSetMetaData` 的列名逐行转成 Map，用完关闭连接，不做连接池——同步任务是分钟级低频操作，没有必要为此引入连接池管理的复杂度）。
3. 用该数据域的字段映射，把每行"上游字段编码 → 原始值"转换成"系统字段编码（即 metadataField 的 `fieldCode`，等价于对应 CreateRequest/UpdateRequest 的 Java 属性名）→ 转换后的值"，转换逻辑复用与 `FieldMappingTransformer` 相同的契约（`NO_TRANSFORM` 原样、`FIXED_VALUE` 取固定值、`SCRIPT` 用同款 GraalVM 沙箱执行，`value` 绑定原始值）——但新写一个 `UpstreamFieldMappingTransformer`，不直接依赖 `app.sync.transform.FieldMappingTransformer`（后者硬编码依赖 `AppSyncFieldMappingMapper`，是为"应用同步"这个具体场景写的，跨模块直接复用需要改造其签名，成本和收益不对等；两者共享的只是"三种转换类型的求值算法"这一段本质上很短的逻辑，可以接受在两处各自实现，而不是抽一个跨模块共享基类——见 Risks 里关于这一点的取舍）。
4. 用转换后的 `Map<String, Object>`，新写 `UpstreamRowUpserter`，按数据域调用 `orgMapper`/`userMapper`/`userPositionMapper` 做"按编码匹配"查询（算法与 `ImportRowExecutor.processOrg`/`processUser`/`processPosition` 完全一致：组织按 `code`，用户按 `code`，任职把"人员标识"按 `code`/`mobile`/`idCard` 任一匹配、"组织"按 `code` 匹配，再按 `userId+orgId+positionType` 复合键匹配已有任职记录），命中零条调用对应 `Service.create`，命中一条调用 `Service.update`，命中多条计入失败明细（不抛异常中断整批，见 Decision 4）；用反射（`BeanWrapper`，与 `ImportRowExecutor.bindProperties` 同款写法）把 Map 值设置到 `CreateRequest`/`UpdateRequest` 属性上。
5. 汇总本数据域处理结果，写入一条 `tab_upstream_sync_record`。

不直接复用 `ImportRowExecutor`（尽管算法一致）：`ImportRowExecutor.processRow` 的入参 `List<ImportFieldConfigVO> configs` 携带的是 Excel 表头文字、必填标记、字典下拉反查等 Excel 导入场景专属的语义，本次数据来源是已经过字段映射转换的结构化 Map，不需要"必填校验"（交给下游 `CreateRequest` 的 Bean Validation 兜底，和 `ImportRowExecutor.validateRequest` 最终效果一致）、不需要字典 label 反查（上游要么直接给字典 `code`，要么用转换脚本/固定值自己转换成 `code`，不是本模块的职责）。跨模块直接依赖 `excelimport` 包内部类也不是好的模块边界。因此新写一个更小的 `UpstreamRowUpserter`，只保留"按编码匹配 + 调用既有 create/update service + BeanWrapper 反射赋值"这部分算法，不引入 Excel 场景的额外关注点。

- **备选方案**：抽取 `ImportRowExecutor` 中的匹配/落库算法为一个跨模块共享的基类或工具方法，两处都调用它。未采纳——当前 `ImportRowExecutor` 的四个 `processXxx` 私有方法与"字段配置驱动的必填校验+字典反查"耦合得比较紧，抽取需要先重构 `excelimport` 模块（超出本次改动范围、增加不相关模块的回归风险），本次改动量本身不大（三个 processXxx 方法各自二三十行），直接各自实现更可控。

#### Decision 3 实现阶段补充：ORG/POSITION 数据域缺少解析外键/层级关系的机制（后端实现时发现并修复）

上文步骤 3/4 描述"字段映射把上游字段编码转换成系统字段编码"这条路径，隐含假设三个数据域都能把落库所需的全部信息（含外键/层级关系）作为普通的字段映射配置出来。但实际实现时发现这个假设对 POSITION、ORG 两个数据域都不成立，是同一类问题、同一套解法：

- **POSITION**：落库需要"所属人员标识"/"所属组织编码"两个值解析 `userId`/`orgId`。POSITION bizType 的元数据字段目录（`tab_metadata_field` 按 `table_name`/`column_name` 与 `tab_user_position` 的真实物理列一一对应，是本仓库既有的强不变量，元数据字段没有"创建"接口、只能编辑既有种子数据的名称/编码）只包含 `positionType`/`positionAddress`/`positionPhone`/`remark`/`ext1~10` 这些开放配置列，不包含外键 `userId`/`orgId`（它们是选择器，不是可开放配置的展示字段，`tab_user_position` 本身也没有 `code`/`mobile`/`idCard` 这类可匹配的列）。
- **ORG**：落库需要"上级组织编码"解析 `parentId`，表达组织树的层级关系。ORG bizType 的元数据字段目录同样只描述 `tab_org` 自身的开放配置列（`name`/`code`/`showOrder`/`remark`/`ext1~10`），不包含表达层级关系的 `parentId`；`OrgCreateRequest`/`OrgUpdateRequest` 上对应的可写属性是内部数据库主键 `parentId`（`Long`），不是人可读的编码，上游系统不可能知道这个数字。组织树的父子层级是本系统组织管理的核心概念（`OrgManagement`、组织范围权限等大量功能都建立在 `parentId` 层级之上），如果放任上游同步的组织永远落地成 `parentId=0`（全部拍平成顶级组织），是使用者很难接受的功能缺陷，不能当作 Non-Goal 一句话带过。

而 spec.md「上游字段映射配置」要求里又明确"目标限定 bizType 为 ORG/USER/POSITION 且与所属数据域一致"，两个数据域的字段映射表因此结构性地无法选中其他 bizType 的字段作为映射目标——即原设计里没有任何路径能让 ORG/POSITION 数据域的一行上游数据携带"这条组织的上级是谁""这条任职记录属于哪个人、哪个组织"的信息。

采用的修复方案：比照 Excel 批量导入 `cn.nihility.rbac.excelimport.constant.OrgPseudoFieldCode`/`PositionPseudoFieldCode` 的既有解法，新增两个固定伪字段编码常量类：

- `cn.nihility.rbac.identity.upstream.constant.UpstreamOrgPseudoFieldCode`：`PARENT_CODE = "__parentCode"`，按组织 `code` 匹配得到 `parentId`；与 Excel 导入的差异是——Excel 导入场景该列强制必填（模板本身有"固定必填列"的前置保障机制），取值为空即判定整行失败，而上游同步没有这层保障，管理员完全可能只想同步一批平级组织、不关心层级，因此取不到该编码、取值为空或字面为 `"0"` 均视为顶级组织（`parentId=0`），不判定失败；其余取值按 `tab_org.code` 匹配，匹配不到时该行判定失败。
- `cn.nihility.rbac.identity.upstream.constant.UpstreamPositionPseudoFieldCode`：`USER_IDENTIFIER = "__userIdentifier"`（按用户 `code`/`mobile`/`idCard` 任一匹配，取不到/为空即判定该行失败，无"缺省即顶级"这类兜底语义）、`ORG_CODE = "__orgCode"`（按组织 `code` 匹配）。

管理员配置 ORG/POSITION 数据域的取数 SQL（列别名）或接口（JSON 字段名）时，可选/须按这些固定编码命名对应的列/字段，**不经过字段映射表配置**——这些值直接从取数阶段拉取到的原始行（字段映射转换之前）里按固定 key 读取，其余字段（`name`/`code`/`positionType` 等）仍走字段映射转换后的行数据。相应地，`UpstreamRowUpserter.upsertRow`（含 `upsertOrg`/`upsertPosition` 两个私有方法）、`UpstreamSyncExecutor.syncDomain` 的方法签名从"只接收转换后的 Map"扩展为"同时接收转换后的 Map 与原始 Map"，对 ORG/USER/POSITION 三个数据域统一传递（USER 数据域当前不使用 rawRow，但签名统一，避免按数据域再拆分调用约定）。

- **影响面**：前端"数据范围"分区 ORG/POSITION 两个数据域的"是否启用（含取数来源配置）"页面，都需要在 SQL/接口 URL 输入框旁提示各自固定编码的约定文案（ORG 如"上级组织编码可选，取数结果如包含 `__parentCode` 列则按其匹配组织编码得到上级组织，不提供或取值为空/0 视为顶级组织，不在下方字段映射中配置"；POSITION 如"任职数据域的取数结果须包含 `__userIdentifier`（人员标识）与 `__orgCode`（组织编码）两列，用于确定任职记录归属，不在下方字段映射中配置"）。这一约定在本次改动的后端实现阶段确定，前端 agent 实现"数据范围"分区时需要据此补充提示文案。
- **备选方案 1**：放宽字段映射目标的 bizType 限制，允许 ORG/POSITION 数据域额外选择其他 bizType 的字段（如 ORG 的 `code`）作为映射目标。未采纳——这会让"目标 bizType 与所属数据域一致"这条本来清晰的约束出现例外，字段映射服务层的校验逻辑、前端"系统字段"下拉的可选项来源都要为 ORG/POSITION 数据域单独打补丁，改动面明显大于新增固定伪字段编码。
- **备选方案 2**：在 `tab_metadata_field` 里为 ORG/POSITION bizType 补几条指向 `parent_id`/`user_id`/`org_id` 物理列的元数据字段种子数据，纳入常规字段映射体系。未采纳——这几个物理列存的是内部数据库自增主键，管理员/上游系统并不知道这个数字，可选的取值只能是人可读的编码/手机号/身份证号，这与元数据字段目录"物理列 ↔ 展示字段"一一对应的既有语义矛盾（该列的取值天然不是同一个东西），会把"字段映射的目标是物理列"这条不变量搞乱。

### Decision 4：单行失败不中断整批，行为对齐 Excel 导入的"逐行独立事务"模式

每个数据域的每一行处理，参照 `ImportRowExecutor.processRow` 的 `@Transactional(propagation = Propagation.REQUIRES_NEW)` 独立事务 + 捕获异常计入失败明细的模式：单行失败（匹配到多条、必填校验不过、格式不合法等）不影响其余行，最终该数据域的同步记录里 `status` 按"全部成功=SUCCESS/部分失败=PARTIAL/全部失败=FAILED"判定，`fail_summary` 汇总前若干条失败原因（避免无限增长，截断长度参照 `tab_app_notify_record.error_msg` 的 `VARCHAR(500)` 量级）。

### Decision 5：字段映射方向与"应用同步"相反，但表格 UI/转换方式配置完全复用现成样式

`app.sync` 字段映射的语义是"源=本系统元数据字段（目录选）、目标=应用字段名称/编码（手工填写）"；本能力反过来："源=上游字段名称/编码（手工填写，因为上游 schema 未知）、目标=本系统元数据字段（目录选，限 ORG/USER/POSITION 三个 bizType）"。前端表格沿用 `AppConfigView.vue` 字段映射表格同款结构（新增一行=从"系统字段"下拉选一个未使用的元数据字段插入一行、上游字段名称/编码两个输入框可编辑、转换方式下拉+条件展示的转换取值输入框、删除按钮），只是把"只读源字段列 + 可编辑目标字段列"的位置和文案对调：本能力里"系统字段名称/系统字段编码"是选中后自动带出的只读列，"上游字段名称/上游字段编码"是可编辑输入框（新增一行时不做默认预填——不同于 app-config-page-ux-refine change 里"应用字段默认预填源字段名"的场景，这里恰恰相反，上游字段名称通常与系统字段名称不同，预填反而会误导管理员以为不用改）。

转换方向也相应反过来：`app.sync` 的转换是"本系统字段值 → 转换 → 应用字段值（出）"；本能力是"上游字段原始值 → 转换 → 系统字段值（入）"，`value` 绑定的语义从"待发送的本系统字段值"变成"待写入的上游原始值"，脚本编写者需要清楚这一点的方向差异，前端转换取值输入框的 placeholder 文案需要相应调整（如"请输入把上游取值转换为系统取值的 JavaScript 转换脚本"），避免管理员直接照搬应用同步那边的脚本习惯搞反方向。

### Decision 6：数据域常量新建一个三值集合，不复用 `app.sync.constant.SyncDomain`

`SyncDomain` 目前是六值（含 APP/ROLE/DICT），本能力只涉及三值（ORG/USER/POSITION）。为避免"复用六值常量但另外四个取值在本能力里永远非法"的隐晦约束，新建 `cn.nihility.rbac.identity.upstream.constant.UpstreamDataType`，只定义 `ORG`/`USER`/`POSITION` 三个常量（取值字符串与 `SyncDomain` 对应常量保持一致，方便理解，但类型独立）。

### Decision 7：DB 模式 SQL 由管理员直接编写，不做只读/注入防护之外的额外限制

管理员填写的 SQL 是管理端配置项（需要 `UpstreamManagement:source:config:edit` 权限），不是终端用户可影响的输入，不存在常规意义上的 SQL 注入风险（SQL 本身就是受信任管理员的配置内容，原样交给 JDBC 执行，不做参数拼接）。设计上仅做两件事：执行前校验 SQL 文本以 `SELECT`/`WITH` 开头的简单前缀检查（防止管理员误配一条 `DELETE`/`UPDATE` 语句而不是校验安全性），以及在文档/前端提示文案里建议管理员使用只读账号的 JDBC 凭证（最佳实践提示，不是系统强制）。

### Decision 8：权限点粒度简化，不照搬应用管理的多权限点拆分

`AppManagement:app:config:editSync`/`editSignAlgorithm` 是历史演进出来的两个独立权限点（本身也在 `app-config-page-ux-refine` change 里合并成一个使用面）。本能力直接一开始就设计成较粗粒度：`UpstreamManagement:source:view`（列表页）、`:add`/`:edit`/`:delete`/`:enable`/`:disable`（列表页操作按钮）、`:config`（进入配置页）、`:config:edit`（配置页内连接配置/调度配置/数据域启用/字段映射的所有保存动作，共用一个权限点，不再进一步拆分）、`:manualSync`（"立即同步一次"按钮，因为这是一个有副作用、可能对上游系统产生访问压力的操作，值得单独控权，不和其余只读/编辑区分）。

## Risks / Trade-offs

- [风险] DB 模式下管理员配置的 JDBC 账号如果权限过大（非只读账号），加上 SQL 本身没有语法白名单，理论上能执行任意 SQL（含写操作）→ 缓解：Decision 7 的 `SELECT`/`WITH` 前缀校验挡掉最直接的误操作，真正的权限收敛依赖运维给上游 JDBC 账号配置只读权限（不是本系统能控制的范围，只能在前端提示里给出建议）。
- [风险] 接口模式下上游一次性返回超大 JSON 数组，可能导致本系统单次同步内存占用过高或超时 → 缓解：不做特殊防护（Non-Goals 已声明不支持分页），如果后续遇到真实的大数据量场景，需要新开一个 change 引入分页协议或流式解析，本次不预先设计。
- [风险] `UpstreamFieldMappingTransformer`/`UpstreamRowUpserter` 与 `app.sync` 侧的 `FieldMappingTransformer`/`ImportRowExecutor` 存在"算法相似但各自独立实现"的代码重复 → 缓解：这是 Decision 3/5 里权衡过的有意选择（避免跨模块耦合/大范围重构），重复的部分都不大（转换求值几十行、匹配落库几十行），后续如果出现第三个类似场景，再考虑抽公共组件也不迟。
- [风险] 轮询 tick 每分钟扫描全部启用数据源，数据源数量很大时（实际场景不太可能，企业上游系统数量通常个位数到几十）可能有性能问题 → 缓解：不预先优化，当前定位是管理端配置量级功能，几十条数据源级别的轮询判断（内存计算，无重 IO）性能可忽略。

## Migration Plan

新增 Flyway 迁移脚本 `V{N}__identity_upstream_data_sync.sql`（沿用仓库既有的"新建表直接一次性写完整定义"惯例，`N` 取当前最大版本号+1），不涉及既有表结构变更，纯新增，无需数据迁移、无回滚特殊步骤（迁移失败按 Flyway 常规方式处理）。
