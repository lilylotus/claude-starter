## Why

目前组织/用户/任职三类身份数据只能通过管理端页面手工维护，或者一次性 Excel 批量导入（`excel-import-export`）。当企业已有一套上游人力资源/身份系统（如 HR 系统、上级组织的统一身份平台）作为权威数据源时，需要持续、自动地把上游变化同步过来，而不是靠管理员反复手工导入 Excel。需要新增一个"上游数据管理"能力：配置一个或多个上游数据源，按接口或数据库表两种方式之一，定期把上游的组织/用户/任职数据拉取过来，落地为本系统的组织/用户/任职记录。

## What Changes

- 新增"身份管理"分组下的"上游数据管理"菜单与页面：列表页管理多个"上游数据源"配置，独立配置路由页管理单个数据源的详细设置。
- 每个上游数据源二选一同步方式：
  - **接口**：HTTP 拉取，可配置请求方式（GET/POST）、每个数据域各自的请求 URL、自定义请求头（key-value，供上游鉴权使用），响应体约定为 JSON 数组，数组元素是扁平 JSON 对象。
  - **数据库表**：管理员填写 JDBC 连接信息（URL/用户名/密码）与每个数据域各自的一条只读 `SELECT` 语句（列别名对应上游字段编码），仅支持 MySQL。
- 三个数据域（组织/用户/任职）各自独立启用开关、各自的请求来源（URL 或 SQL）、各自的字段映射配置：字段映射表格样式复用应用同步（`app-api-credentials`）现成的 UI 与转换方式（不转换/固定值/转换脚本），但方向相反——管理员手工填写"上游字段名称/上游字段编码"作为源（上游 schema 未知，无法像应用同步那样从目录选），从本系统"元数据字段"目录（限组织/用户/任职三个 bizType）里选择目标"系统字段名称/系统字段编码"。
- 组织的"上级组织"、任职的"所属人员/所属组织"这类外键/层级关系不属于任何 bizType 下可开放配置的元数据字段，不能走字段映射配置：改为约定三个固定的伪字段编码，管理员在对应数据域的取数 SQL 列别名或接口 JSON 字段名里按约定命名即可——组织数据域可选的 `__parentCode`（上级组织编码，缺省/为空/为 `0` 视为顶级组织）、任职数据域必须提供的 `__userIdentifier`（所属人员标识，按用户编码/手机号/身份证号任一匹配）与 `__orgCode`（所属组织编码）。
- 定时调度：数据源级别配置调度方式二选一——按间隔（每 N 分钟/每 N 小时）或按固定时间点（每天 HH:mm）；到点触发一次同步，按组织→用户→任职固定顺序处理该数据源下已启用的数据域。另提供"立即同步一次"手动触发入口。
- 数据落库沿用本系统既有的"存在则更新、不存在则新增"匹配逻辑（组织按 `code`、用户按 `code`、任职按用户与组织匹配结果+`positionType` 复合键），只做新增/更新，不处理上游侧记录消失的场景（不自动停用/删除本地记录）。
- 新增同步执行记录：记录每次同步（含手动触发）的开始/结束时间、状态、处理/成功/失败行数、失败摘要，仅用于问题排查展示。

## Capabilities

### New Capabilities
- `identity-upstream-data-sync`：上游数据源的配置管理（接口/数据库两种同步方式、组织/用户/任职三个数据域各自的启用与字段映射）、定时/手动触发的同步执行、数据落库匹配语义、执行记录。

### Modified Capabilities
（无——本次不改动 `org-management`/`user-management`/`position-management`/`app-api-credentials`/`metadata-field-management` 等既有能力的对外行为，只是复用它们已有的 create/update service 与元数据字段目录查询能力）

## Impact

- 新增后端模块 `cn.nihility.rbac.identity.upstream`（比照 `cn.nihility.rbac.app.sync` 的分层）：数据源配置、数据域配置、字段映射、同步执行记录的 entity/mapper/service/controller/dto/mapstruct，定时轮询触发器（`UpstreamSyncScheduler`），同步执行引擎（`UpstreamHttpFetcher`/`UpstreamJdbcFetcher` 两种取数实现、`UpstreamFieldMappingTransformer`、`UpstreamRowUpserter`、`UpstreamSyncExecutor`）；连接配置、调度配置各自拆成独立的更新请求 DTO（`UpstreamConnectionConfigRequest`/`UpstreamScheduleConfigRequest`），字段映射联表查询新增了一个自定义 Mapper 方法 + `mybatis/mapper/UpstreamFieldMappingMapper.xml`（其余三张表的 mapper 是纯 `BaseMapper`）；新增 `UpstreamOrgPseudoFieldCode`（`__parentCode`）、`UpstreamPositionPseudoFieldCode`（`__userIdentifier`/`__orgCode`）两个固定伪字段编码常量类，承载上文"外键/层级关系不走字段映射"的约定。
- 新增数据库表（Flyway 迁移脚本 `V4__identity_upstream_data_sync.sql`）：`tab_upstream_source`/`tab_upstream_domain_config`/`tab_upstream_field_mapping`/`tab_upstream_sync_record`，均加 `tab_` 前缀。
- 新增前端页面：`frontend/src/views/identity/upstream/UpstreamSourceListView.vue`、`UpstreamSourceConfigView.vue`，新增 `src/api/upstreamSource.ts`、`src/types/upstreamSource.ts`，`src/router/menu.ts`/`src/router/index.ts` 身份管理分组新增子菜单项与两个路由；配置页"数据范围"分区的 ORG/POSITION 数据域面板用 `el-alert` 提示各自固定伪字段编码的约定文案。
- `权限资源.txt` 新增 `UpstreamManagement` 模块的权限点清单：`:source:view`/`:add`/`:edit`/`:delete`/`:enable`/`:disable`/`:config`/`:config:edit`/`:manualSync`（较粗粒度，配置页内连接配置/调度配置/数据域启用/字段映射的所有保存动作共用 `:config:edit` 一个权限点，不进一步拆分）。
- 复用既有能力：`Sm4JdkUtils`（DB 密码/请求头鉴权值加密落库）、`FieldMappingTransformer` 同款 GraalVM 脚本转换契约（新写一份面向本方向的执行器，不跨模块直接依赖 `app.sync` 内部类）、`OrgService`/`UserService`/`PositionService` 既有 create/update 校验与写入逻辑、元数据字段目录查询、`mysql-connector-j`（已有依赖，DB 模式复用，不新增依赖）。连接配置查询接口均不回显敏感信息明文；更新语义两侧略有差异——接口模式的自定义请求头是整体替换（提交的完整 key-value 集合替换已保存集合），数据库模式的密码是"留空表示不修改"（未回显明文导致无法整体替换）。
- 不涉及对外接口契约变化（本能力面向管理端，不对外暴露 OpenAPI）。
