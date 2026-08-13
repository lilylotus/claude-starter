## 1. 数据库

- [x] 1.1 新增 `backend/src/main/resources/db/migration/V4__identity_upstream_data_sync.sql`：建 `tab_upstream_source`（名称、`sync_type`、`enabled`、调度配置列、`last_trigger_time`、接口模式 `api_auth_headers` JSON 文本、数据库模式 `db_jdbc_url`/`db_username`/`db_password` 加密文本）
- [x] 1.2 同一脚本建 `tab_upstream_domain_config`（`source_id`+`data_type` 唯一、`enabled`、`api_url`/`api_method`、`db_sql`、`last_sync_time`）
- [x] 1.3 同一脚本建 `tab_upstream_field_mapping`（`source_id`+`data_type`、`upstream_field_name`/`upstream_field_code`、`metadata_field_id`、`transform_type`、`transform_value`）
- [x] 1.4 同一脚本建 `tab_upstream_sync_record`（`source_id`、`data_type`、`trigger_type`、`start_time`/`end_time`、`status`、`total_count`/`success_count`/`fail_count`、`fail_summary`）
- [x] 1.5 四张表均加上统一的创建人/创建时间/更新人/更新时间四个默认字段；核对所有列名不与 MySQL/PostgreSQL/Oracle/SQL Server 保留字冲突；不使用窗口函数/CTE 等版本相关写法

## 2. 后端：常量与实体层

- [x] 2.1 新增 `cn.nihility.rbac.identity.upstream.constant` 包：`UpstreamSyncType`（API/DB_TABLE）、`UpstreamDataType`（ORG/USER/POSITION 三值）、`UpstreamScheduleType`（INTERVAL/FIXED_TIME）、`UpstreamIntervalUnit`（MINUTE/HOUR）、`UpstreamTriggerType`（SCHEDULE/MANUAL）、`UpstreamSyncStatus`（SUCCESS/PARTIAL/FAILED）、`UpstreamApiMethod`（GET/POST）——复用 `app.sync.constant.TransformType`，不重复定义转换方式常量；另增 `UpstreamOrgPseudoFieldCode`（`__parentCode`）、`UpstreamPositionPseudoFieldCode`（`__userIdentifier`/`__orgCode`）两个固定伪字段编码常量类（实现阶段发现的必要补充，同一类问题、同一套解法，见 design.md 新增的 Decision 3 补充说明）
- [x] 2.2 新增 `cn.nihility.rbac.identity.upstream.entity`：`UpstreamSourceEntity`、`UpstreamDomainConfigEntity`、`UpstreamFieldMappingEntity`、`UpstreamSyncRecordEntity`，字段命名驼峰、精确 Lombok 注解（`@Getter`/`@Setter`/`@Builder` 等，不用笼统 `@Data`）
- [x] 2.3 新增 `cn.nihility.rbac.identity.upstream.mapper`：四个 `BaseMapper` 接口；`UpstreamFieldMappingMapper` 另外新增一个自定义方法 `selectBySourceIdAndDataType`（+ `mybatis/mapper/UpstreamFieldMappingMapper.xml`，LEFT JOIN `tab_metadata_field` 实时回填目标字段的 `fieldName`/`fieldCode`，结果映射到新增的内部载体 DTO `UpstreamFieldMappingRow`），其余三个是纯 `BaseMapper` 单表 CRUD

## 3. 后端：加密与连接配置

- [x] 3.1 确认 `AppSecretProperties`/`Sm4JdkUtils` 可以被 `identity.upstream` 包直接复用（同一把 SM4 主密钥），`api_auth_headers` 中的 header 值与 `db_password` 落库前用 `Sm4JdkUtils.encrypt`，查询时不回传明文（VO 上不暴露该字段或返回掩码）
- [x] 3.2 数据域配置的 `db_sql` 保存前做 `SELECT`/`WITH` 前缀校验（忽略大小写与首尾空白），不满足时抛 `BusinessException`
- [x] 3.3 数据源保存 `db_jdbc_url` 时校验 `jdbc:mysql://` 前缀

## 4. 后端：配置管理 DTO / MapStruct / Service / Controller

- [x] 4.1 `dto`：`UpstreamSourceCreateRequest`/`UpdateRequest`/`VO`、`UpstreamDomainConfigUpdateRequest`/`VO`、`UpstreamFieldMappingSaveRequest`/`VO`（含 `metadataFieldId` 引用元数据字段，展示时联表取当前 `fieldName`/`fieldCode`）、`UpstreamSyncRecordVO`，`jakarta.validation` 注解配合 controller `@Valid`；另增 `UpstreamConnectionConfigRequest`/`UpstreamScheduleConfigRequest`（连接配置、调度配置各自独立的更新请求，design.md 原文未点名具体类名，实现时按既有分区拆分）
- [x] 4.2 `mapstruct`：`UpstreamSourceConvert`/`UpstreamDomainConfigConvert`/`UpstreamFieldMappingConvert`，静态单例写法（`INSTANCE = Mappers.getMapper(...)`），不用 `componentModel = "spring"`
- [x] 4.3 `service`/`service.impl`：`UpstreamSourceService`（CRUD、启用停用、级联删除、连接配置/调度配置维护、手动同步入口）、`UpstreamDomainConfigService`（按数据源查询三个数据域配置、按数据域更新）、`UpstreamFieldMappingService`（按数据源+数据域查询、整体替换保存，校验目标元数据字段不重复、`bizType` 与数据域一致、转换脚本语法合法性复用 `TransformScriptValidator`）、`UpstreamSyncRecordService`（按数据源查询执行记录列表，倒序）
- [x] 4.4 `controller`：`UpstreamSourceController`（`/api/identity/upstream-sources` 系列 CRUD + 启停 + 子资源的数据域/字段映射/同步记录/手动同步接口），补 springdoc-openapi `@Tag`/`@Operation` 注解，薄层不写业务逻辑

## 5. 后端：同步执行引擎

- [x] 5.1 `support`：`UpstreamHttpFetcher`（复用 `HttpClientUtils` 发起 GET/POST，携带解密后的自定义请求头，响应体解析为 `List<Map<String, Object>>`，非 2xx 或非 JSON 数组时判定该数据域取数失败）
- [x] 5.2 `support`：`UpstreamJdbcFetcher`（`DriverManager.getConnection` 用解密后的用户名/密码连接，执行数据域配置的 SQL，按 `ResultSetMetaData` 列名转 `List<Map<String, Object>>`，`finally` 关闭连接，不做连接池）
- [x] 5.3 `support`：`UpstreamFieldMappingTransformer`（按字段映射把"上游字段编码 → 原始值"转换为"系统字段编码 → 转换后的值"，`NO_TRANSFORM`/`FIXED_VALUE` 直接取值，`SCRIPT` 用 GraalVM 沙箱执行，`value` 绑定原始值，契约与超时保护参照 `sync.transform.FieldMappingTransformer` 但独立实现，不跨模块依赖）
- [x] 5.4 `support`：`UpstreamRowUpserter`（按 `UpstreamDataType` 路由：ORG 按 `code` 匹配、USER 按 `code` 匹配、POSITION 按用户 `code`/`mobile`/`idCard` 任一匹配+组织 `code` 匹配+`userId+orgId+positionType` 复合键匹配，命中零条 create、一条 update、多条计入失败；`BeanWrapper` 反射把 Map 值设置到 `CreateRequest`/`UpdateRequest`；每行 `@Transactional(propagation = REQUIRES_NEW)` 独立事务，异常计入失败明细不中断整批）——实现时发现 POSITION 数据域缺少解析 `userId`/`orgId` 的机制、以及 ORG 数据域缺少解析 `parentId`（上级组织）的机制（见下方"实现偏差"说明，两者是同一类问题、同一套解法），已扩展方法签名同时接收转换后行与原始行，`upsertOrg` 额外解析 `__parentCode` 落地 `parentId`
- [x] 5.5 `support`：`UpstreamSyncExecutor`（`syncSource(sourceId, triggerType)`：按 ORG→USER→POSITION 顺序处理已启用数据域，每个数据域取数→字段映射转换→ upsert →汇总写入一条 `tab_upstream_sync_record`；取数阶段异常直接记一条 `FAILED` 记录，不影响其余数据域继续处理）

### 实现偏差说明（5.4）

design.md Decision 3 原文只描述"用转换后的 `Map<String, Object>` 驱动 `UpstreamRowUpserter`"，未覆盖 POSITION 数据域如何解析所属人员/组织、ORG 数据域如何解析上级组织——两者是同一类问题：POSITION bizType 的元数据字段目录只包含 `tab_user_position` 自身的开放配置列（`positionType`/`positionAddress`/`positionPhone`/`remark`/`ext1~10`），不包含外键 `userId`/`orgId`；ORG bizType 的元数据字段目录同样只包含 `tab_org` 自身的开放配置列，不包含表达层级关系的 `parentId`。而 spec.md 又明确要求字段映射目标"限定 bizType 为 ORG/USER/POSITION 且与所属数据域一致"，导致两个数据域的字段映射机制本身都无法传递这些外键/层级信息（组织树父子层级是本系统组织管理的核心概念，不能放任上游同步的组织全部拍平成顶级组织，不是可以忽略的 Non-Goal）。比照 Excel 导入 `OrgPseudoFieldCode`/`PositionPseudoFieldCode` 的既有解法，新增 `UpstreamOrgPseudoFieldCode`（`__parentCode`，取不到/为空/字面 `"0"` 均视为顶级组织，不同于 Excel 导入强制必填的语义——上游同步没有"固定必填列"的前置保障，允许管理员只同步平级组织）与 `UpstreamPositionPseudoFieldCode`（`__userIdentifier`/`__orgCode`，取不到/为空判定该行失败），要求管理员配置 ORG/POSITION 数据域的取数 SQL/接口时把这些列按固定编码命名，不经过字段映射表配置；`UpstreamRowUpserter.upsertRow`/`UpstreamSyncExecutor` 相应从取数阶段的原始行（转换前）而非字段映射转换后的行读取这些值。前端"数据范围"分区的 ORG/POSITION 数据域页面都需要提示管理员这一约定（后续前端 agent 实现时需要知悉，已同步补充到 design.md）。

## 6. 后端：定时调度

- [x] 6.1 `support`：`UpstreamSyncScheduler`，`@Scheduled(fixedRate = 60_000)`，查询全部 `enabled=true` 数据源，按 `UpstreamScheduleType` 判断是否到期（`INTERVAL` 按 `last_trigger_time` 计算，`FIXED_TIME` 按当天是否已触发过判断），到期调用 `UpstreamSyncExecutor.syncSource(id, SCHEDULE)` 并更新 `last_trigger_time`，捕获单数据源异常不中断本轮轮询其余数据源
- [x] 6.2 controller 新增"立即同步一次"接口，调用 `UpstreamSyncExecutor.syncSource(id, MANUAL)`，不更新 `last_trigger_time`（不影响下次定时判定基准）

## 7. 前端：类型与接口封装

- [x] 7.1 `src/types/upstreamSource.ts`：`UpstreamSyncType`/`UpstreamDataType`/`UpstreamScheduleType`/`UpstreamIntervalUnit`/`UpstreamApiMethod`/`UpstreamTriggerType`/`UpstreamSyncStatus` 等类型定义与选项常量，`UpstreamSourceVO`/`UpstreamDomainConfigVO`/`UpstreamFieldMappingVO`/`UpstreamSyncRecordVO` 等接口，字段命名和后端 DTO 对齐
- [x] 7.2 `src/api/upstreamSource.ts`：按模块封装 axios 请求（CRUD、启停、数据域查询/更新、字段映射查询/整体替换、同步记录查询、手动同步触发），组件不直接调用 axios

## 8. 前端：页面

- [x] 8.1 `src/views/identity/upstream/UpstreamSourceListView.vue`：列表（名称/同步方式/启用状态/操作列：编辑、启停、删除、配置），新增/编辑用弹窗（参照 `AppManagementView.vue` 列表页样式）
- [x] 8.2 `src/views/identity/upstream/UpstreamSourceConfigView.vue`：独立配置路由页，`el-tabs` 五个分区（基础信息/连接配置/调度配置/数据范围/同步记录），连接配置按 `syncType` 联动展示接口或数据库表单，数据范围区左侧数据域纵向 tab + 右侧"是否启用"/"字段映射"二级 tab（复用 `app-config-page-ux-refine` 刚建立的二级 tab 交互与组件写法），调度配置区展示按间隔/按固定时间点二选一表单+"立即同步一次"按钮，同步记录区只读表格
- [x] 8.3 `src/router/index.ts` 新增两个路由（列表页 `/identity/upstream`、配置页 `/identity/upstream/:id/config`），`src/router/menu.ts` 身份管理分组新增"上游数据管理"子菜单项

## 9. 权限资源文档

- [x] 9.1 `权限资源.txt` 新增 `UpstreamManagement` 模块清单：`:source:view`/`:add`/`:edit`/`:delete`/`:enable`/`:disable`/`:config`/`:config:edit`/`:manualSync`

## 10. 测试

- [x] 10.1 后端单测：`UpstreamRowUpserter`（组织/用户/任职各自的新增/更新/多条匹配失败场景，另补充组织"上级组织编码"伪字段缺省/为 "0"/匹配到已有组织/匹配不到已有组织四种场景）、`UpstreamFieldMappingTransformer`（三种转换方式）、`UpstreamSyncScheduler`（按间隔/按固定时间点到期判定的边界条件）
- [x] 10.2 `backend/` 目录执行 `./gradlew test --tests "cn.nihility.rbac.identity.upstream.*"` 确认通过（29 个测试全部通过；`./gradlew build` 全量回归也已确认通过，不影响既有测试）
- [x] 10.3 `frontend/` 目录执行 `npm run build` 确认无类型错误

## 11. 文档同步

- [x] 11.1 实现完成后核对 `proposal.md`/`design.md`/`tasks.md` 与实际改动一致，如实现时有调整需回写

## 12. 归档后补漏（用户验证时发现）

- [x] 12.1 归档后用户实际运行发现前端侧边栏不显示"上游数据管理"菜单、菜单管理/权限点管理页面也看不到对应条目——排查发现原 tasks.md 第 1 组只规划了业务本身的 4 张表，遗漏了 `tab_menu`/`tab_permission`/`tab_role_permission` 三处种子数据（对照 `AppManagement`/`OrgManagement` 等既有模块，这三处种子数据是页面在侧边栏可见、角色可被授权的必要前提，仅更新 `权限资源.txt` 这份文档不会让数据库里真的多出这些行）。新增 `backend/src/main/resources/db/migration/V5__seed_upstream_menu_resource.sql`（未回改 V4，因为 V4 已经执行过，回改会导致 Flyway 校验和不一致）：补齐 `tab_menu` 菜单+按钮树（1 个菜单 + 8 个按钮，挂在 `identity` 一级分组下）、`tab_permission` 同名扁平权限点清单、把这 9 个权限点补授权给 `SUPER_ADMIN` 角色（比照 V1 末尾"超级管理员关联全部种子权限点"的既有模式）。已跑 `RbacApplicationTests`（唯一会启动完整 Spring 上下文、实际执行 Flyway 的测试）确认迁移成功执行，并直接查库确认 `tab_menu`/`tab_role_permission` 数据写入正确。
