## 1. 数据库迁移

- [x] 1.1 新增 `backend/src/main/resources/db/migration/V7__app_sync_domain_config.sql`：
  - `CREATE TABLE tab_app_sync_domain_config`（`id`、`app_ref_id` 外键、`sync_domain` VARCHAR(16)、`sync_enabled` 默认 `0`、`page_size` INT 默认 `20`、`create_by`/`create_time`/`update_by`/`update_time`），唯一键 `(app_ref_id, sync_domain)`。
  - `INSERT INTO tab_app_sync_domain_config (...) SELECT app_id, 'ORG', sync_org_enabled, 20, ... FROM tab_app_config`，对 `USER`/`APP`/`DICT` 各写一条同样的 `INSERT ... SELECT`（4 条语句，分别取对应的旧布尔列），再额外为每个 `tab_app_config` 行插入一条 `sync_domain='ROLE'`、`sync_enabled=0`、`page_size=20` 的记录。
  - `ALTER TABLE tab_app_config DROP COLUMN sync_org_enabled, DROP COLUMN sync_user_enabled, DROP COLUMN sync_app_enabled, DROP COLUMN sync_dict_enabled`。
- [x] 1.2 新增 `V8__app_sync_field_mapping.sql`：`CREATE TABLE tab_app_sync_field_mapping`（`id`、`app_ref_id`、`sync_domain` VARCHAR(16)、`metadata_field_id` 外键指向 `tab_metadata_field.id`、`app_field_name` VARCHAR(128)、`app_field_code` VARCHAR(128)、`transform_type` VARCHAR(16)、`transform_value` TEXT NULL、`create_by`/`create_time`/`update_by`/`update_time`），唯一键 `(app_ref_id, sync_domain, metadata_field_id)`。
- [x] 1.3 新增 `V9__metadata_field_role_seed.sql`：向 `tab_metadata_field` 插入 `biz_type='ROLE'` 的 4 条记录，`table_name='tab_role'`，覆盖 `name`（角色名称）、`code`（角色编码）、`show_order`（显示序号）、`remark`（备注），`field_code` 按下划线转驼峰（`show_order` → `showOrder`，其余同列名），`column_type` 与 `tab_role` 建表语句一致（`VARCHAR(64)`/`VARCHAR(64)`/`INT`/`VARCHAR(255)`）。

## 2. 后端：GraalJS 依赖与脚本语法校验

- [x] 2.1 `build.gradle` 新增依赖 `org.graalvm.polyglot:polyglot` 与 `org.graalvm.polyglot:js-community`（已与用户确认，纯 Java 实现，需在实现时确认 Maven Central 上与本项目 JDK 21 兼容的最新稳定版本号）。
- [x] 2.2 新增 `app/sync/support/TransformScriptValidator.java`：无状态工具类（私有构造器 + `public static void validateSyntax(String script)`），用 `Context.newBuilder("js").build()` + `context.parse(Source.newBuilder("js", script, "sync-transform.js").build())` 做静态语法解析，捕获 `PolyglotException` 转换为 `BusinessException`（提示"转换脚本语法错误：..."），不调用任何执行/求值方法。

## 3. 后端：数据层

- [x] 3.1 新增 `app/sync/constant/SyncDomain.java`：`ORG`/`USER`/`APP`/`ROLE`/`DICT` 五个常量，加一个 `FIELD_MAPPING_DOMAINS`（`Set<String>`，包含 `ORG`/`USER`/`APP`/`ROLE`，供接口层校验字段映射相关请求的 `domain` 参数不能是 `DICT`）。
- [x] 3.2 新增 `app/sync/constant/TransformType.java`：`NO_TRANSFORM`/`FIXED_VALUE`/`SCRIPT` 三个常量。
- [x] 3.3 新增 `app/sync/entity/AppSyncDomainConfigEntity.java`（`@TableName("tab_app_sync_domain_config")`），字段：`id`、`appRefId`、`syncDomain`、`syncEnabled`、`pageSize`、审计字段。精确 Lombok 注解（`@Getter`/`@Setter`/`@Builder`）。
- [x] 3.4 新增 `app/sync/entity/AppSyncFieldMappingEntity.java`（`@TableName("tab_app_sync_field_mapping")`），字段：`id`、`appRefId`、`syncDomain`、`metadataFieldId`、`appFieldName`、`appFieldCode`、`transformType`、`transformValue`、审计字段。
- [x] 3.5 新增 `app/sync/mapper/AppSyncDomainConfigMapper.java`（`BaseMapper<AppSyncDomainConfigEntity>`）。
- [x] 3.6 新增 `app/sync/mapper/AppSyncFieldMappingMapper.java`（`BaseMapper<AppSyncFieldMappingEntity>`），新增 `default boolean existsByMetadataFieldId(Long metadataFieldId)`（`LambdaQueryWrapper` 判断是否存在任意引用该元数据字段的映射行，参照 `FormFieldDefinitionMapper.existsActiveByMetadataFieldId` 的写法），以及一个联表查询方法 `List<AppSyncFieldMappingRow> selectByAppRefIdAndDomain(Long appRefId, String syncDomain)`（返回携带 `fieldName`/`fieldCode`/`tableName`/`columnName` 的行，JOIN `tab_metadata_field`）。
- [x] 3.7 新增 `resources/mybatis/mapper/AppSyncFieldMappingMapper.xml`：实现 3.6 的联表查询（`tab_app_sync_field_mapping` LEFT JOIN `tab_metadata_field ON metadata_field_id = tab_metadata_field.id`），按 `app_ref_id`/`sync_domain` 过滤，按 `id` 升序排列（整体替换语义下 id 升序即保存时的提交顺序，见 design.md Decision 5）。
- [x] 3.8 新增 `app/sync/dto/AppSyncFieldMappingRow.java`（Mapper XML 联表查询结果的载体 DTO，非对外 VO）：`id`/`metadataFieldId`/`fieldName`/`fieldCode`/`appFieldName`/`appFieldCode`/`transformType`/`transformValue`。

## 4. 后端：DTO

- [x] 4.1 新增 `app/sync/dto/AppSyncDomainConfigVO.java`：`syncDomain`、`syncEnabled`、`pageSize`。
- [x] 4.2 新增 `app/sync/dto/AppSyncDomainConfigUpdateRequest.java`：`syncEnabled`（`@NotNull`）、`pageSize`（`@NotNull` + `@Min(1)`）。
- [x] 4.3 新增 `app/sync/dto/AppSyncFieldMappingVO.java`：`id`、`metadataFieldId`、`fieldName`、`fieldCode`、`appFieldName`、`appFieldCode`、`transformType`、`transformValue`。
- [x] 4.4 新增 `app/sync/dto/AppSyncFieldMappingSaveRequest.java`（列表元素）：`metadataFieldId`（`@NotNull`）、`appFieldName`（`@NotBlank`）、`appFieldCode`（`@NotBlank`）、`transformType`（`@NotBlank` + `@Pattern` 限定 `NO_TRANSFORM`/`FIXED_VALUE`/`SCRIPT`）、`transformValue`（`@Size` 长度约束，是否必填由服务层按 `transformType` 校验）。
- [x] 4.5 `app/dto/AppConfigVO.java`：删除 `syncOrgEnabled`/`syncUserEnabled`/`syncAppEnabled`/`syncDictEnabled` 四个字段。
- [x] 4.6 `app/dto/SyncConfigUpdateRequest.java`：删除同上四个字段（`syncMode`/`notifyUrl`/`notifyParams` 保留）。
- [x] 4.7 新增 `app/sync/mapstruct/AppSyncDomainConfigConvert.java`、`AppSyncFieldMappingConvert.java`（MapStruct，`INSTANCE` 静态单例，不用 `componentModel = "spring"`）。

## 5. 后端：Service

- [x] 5.1 新增 `app/sync/service/AppSyncConfigService.java` 接口：
  - `createDefaultDomainConfigs(Long appRefId, String operator)`（供 `AppConfigServiceImpl.createDefaultConfig` 调用，插入 5 行默认配置）
  - `List<AppSyncDomainConfigVO> listDomainConfigs(Long appRefId)`
  - `AppSyncDomainConfigVO updateDomainConfig(Long appRefId, String syncDomain, AppSyncDomainConfigUpdateRequest request)`
  - `List<AppSyncFieldMappingVO> listFieldMappings(Long appRefId, String syncDomain)`
  - `List<AppSyncFieldMappingVO> replaceFieldMappings(Long appRefId, String syncDomain, List<AppSyncFieldMappingSaveRequest> requests)`
- [x] 5.2 新增 `app/sync/service/impl/AppSyncConfigServiceImpl.java`：
  - 注入 `AppSyncDomainConfigMapper`、`AppSyncFieldMappingMapper`、`AppMapper`、`MetadataFieldMapper`（校验 `metadataFieldId` 存在、状态启用、`bizType` 与 `syncDomain` 一致）、`OrgScopeService`、`CurrentOperatorService`、`OperationLogRecorder`。
  - 复用 `AppConfigServiceImpl` 里 `getExistingAppInScope` 的同款校验逻辑（应用存在 + 管辖组织范围，越权/不存在统一报"应用不存在"）——评估是否把该方法上提为共享工具（如 `app/support/AppScopeGuard`）供 `AppConfigServiceImpl`/`AppSyncConfigServiceImpl` 共用，避免复制代码。
  - `updateDomainConfig`：`syncDomain` 必须是 `SyncDomain` 五个常量之一，否则报参数错误；按 `(appRefId, syncDomain)` 查行更新（理论上必存在，5 行由 `createDefaultDomainConfigs` 保证），记录操作日志。
  - `replaceFieldMappings`：`syncDomain` 必须在 `SyncDomain.FIELD_MAPPING_DOMAINS` 内，否则报"该数据域不支持字段级同步配置"；对请求列表逐项校验 `metadataFieldId` 存在、状态启用、`bizType` 等于 `syncDomain`；`transformType=FIXED_VALUE` 时校验 `transformValue` 非空；`transformType=SCRIPT` 时校验 `transformValue` 非空且调用 `TransformScriptValidator.validateSyntax`；请求列表内 `metadataFieldId` 不允许重复。全部校验通过后，在事务内先 `delete from tab_app_sync_field_mapping where app_ref_id=? and sync_domain=?`，再按提交顺序批量插入，记录操作日志（仅记录变更摘要，如"字段映射由 N 行变为 M 行"，不需要逐字段 diff）。
  - `listFieldMappings`：调用 `AppSyncFieldMappingMapper.selectByAppRefIdAndDomain`，`syncDomain=DICT` 时直接返回空列表（不报错，前端字典 tab 不会调用这个查询）。
- [x] 5.3 `app/service/impl/AppConfigServiceImpl.java`：
  - `createDefaultConfig`：移除四个 `sync*Enabled` 字段的写入，新增调用 `appSyncConfigService.createDefaultDomainConfigs(appRefId, operator)`（同一事务内，`AppServiceImpl.create` 已有 `@Transactional`）。
  - `updateSyncConfig`/`toLogSnapshot`：移除对四个布尔字段的读写与快照记录（`syncMode`/`notifyUrl`/`notifyParams` 部分不变）。
- [x] 5.4 `metadata/service/impl/MetadataFieldServiceImpl.java`：注入 `AppSyncFieldMappingMapper`；`disable` 方法在现有 `existsActiveByMetadataFieldId` 校验之后，追加 `appSyncFieldMappingMapper.existsByMetadataFieldId(id)` 校验，命中时抛 `MetadataFieldInUseException`（提示文案区分"已被应用同步字段映射引用，无法停用"）。

## 6. 后端：Controller

- [x] 6.1 新增 `app/sync/controller/AppSyncConfigController.java`（`@Tag`/`@Operation` 齐全），路由前缀 `/api/apps/{id}/config/sync`：
  - `GET /api/apps/{id}/config/sync/domains` → `List<AppSyncDomainConfigVO>`
  - `PUT /api/apps/{id}/config/sync/domains/{syncDomain}`（`@Valid @RequestBody AppSyncDomainConfigUpdateRequest`）→ `AppSyncDomainConfigVO`
  - `GET /api/apps/{id}/config/sync/field-mappings?domain=ORG` → `List<AppSyncFieldMappingVO>`
  - `PUT /api/apps/{id}/config/sync/field-mappings?domain=ORG`（`@Valid @RequestBody List<AppSyncFieldMappingSaveRequest>`）→ `List<AppSyncFieldMappingVO>`
  - 写操作复用既有权限点 `AppManagement:app:config:editSync`（前端路由/按钮层面控制，接口层不新增权限校验逻辑，与 `AppConfigController` 现状一致——本仓库权限校验目前落在前端展示层 + 管辖组织范围校验，不在 controller 加 `@PreAuthorize` 之类注解，需确认与现有 `AppConfigController` 的权限校验方式完全对齐）。

## 7. 权限资源编码

- [x] 7.1 `权限资源.txt` 更新 `AppManagement:app:config:editSync` 一行的描述文字，反映新范围（数据域从"组织/用户/应用/字典四个开关"改为"组织/用户/应用/角色/字典五个数据域启用开关+分页大小，以及组织/用户/应用/角色的字段级同步映射"），权限点编码本身不变，不新增权限资源编码。

## 8. 前端：API 与类型

- [x] 8.1 `frontend/src/types/app.ts`：`AppConfigVO`/`SyncConfigUpdateRequest` 移除 `syncOrgEnabled`/`syncUserEnabled`/`syncAppEnabled`/`syncDictEnabled` 四个字段；新增 `SyncDomain`（`'ORG' | 'USER' | 'APP' | 'ROLE' | 'DICT'`）、`TransformType`（`'NO_TRANSFORM' | 'FIXED_VALUE' | 'SCRIPT'`）、`AppSyncDomainConfigVO`（`syncDomain`/`syncEnabled`/`pageSize`）、`AppSyncFieldMappingVO`（`id`/`metadataFieldId`/`fieldName`/`fieldCode`/`appFieldName`/`appFieldCode`/`transformType`/`transformValue`）、`AppSyncFieldMappingSaveRequest`。
- [x] 8.2 `frontend/src/api/app.ts`：新增 `listAppSyncDomainConfigs(id)`、`updateAppSyncDomainConfig(id, domain, payload)`、`listAppSyncFieldMappings(id, domain)`、`replaceAppSyncFieldMappings(id, domain, rows)` 四个请求函数；`updateAppSyncConfig` 请求体类型同步移除四个布尔字段。
- [x] 8.3 确认 `frontend/src/api/metadataField.ts` 已有可复用的"分页查询元数据字段"请求函数 `getMetadataFieldPage`；其 `bizType` 参数类型为 `FormFieldBizType`（不含 `ROLE`），按 design.md Decision 8 新增轻量封装 `getMetadataFieldPageForSyncDomain(bizType: MetadataFieldBizType, pageSize = 200)`（`types/metadataField.ts` 新增 `MetadataFieldBizType = FormFieldBizType | 'ROLE'` 别名，不改动 `FormFieldBizType` 本身，也不新增后端接口）。

## 9. 前端：配置页面"数据范围"改版

- [x] 9.1 `frontend/src/views/application/app/AppConfigView.vue`："同步配置" tab 内的"数据范围"标题区块，替换为内嵌 `el-tabs`（`tab-position="left"`），5 个子 tab：组织/用户/应用/角色/字典，对应 `SyncDomain` 五个取值；切换子 tab 时懒加载该数据域的启用状态/分页大小（`listAppSyncDomainConfigs` 一次性拉 5 行缓存在本地，不用每次切换都请求）与字段映射列表（`listAppSyncFieldMappings`，仅组织/用户/应用/角色四个子 tab 需要，切换到对应子 tab 时按需请求，做简单的按 domain 缓存避免重复请求）。
- [x] 9.2 每个子 tab 展示：启用开关（`el-switch`）、拉取分页大小（`el-input-number :min="1"`）、独立"保存"按钮（调用 `updateAppSyncDomainConfig`，权限点复用 `AppManagement:app:config:editSync`）。
- [x] 9.3 组织/用户/应用/角色四个子 tab 额外展示字段映射表格（`el-table`）：只读列"字段名称"/"字段编码"（来自选中的元数据字段）、可编辑列"应用字段名称"/"应用字段编码"（`el-input`）、"转换方式"（`el-select`：不转换/固定值/转换脚本）、转换取值列（转换方式为固定值时 `el-input`，转换脚本时 `el-input type="textarea"`，不转换时该列不展示/禁用）、操作列"删除"；表格上方"新增字段"按钮打开选择器（下拉列表来自当前 `bizType`（等同 `syncDomain`，`ROLE` 对应新增的 `bizType=ROLE`）的启用元数据字段，展示 `fieldName（fieldCode）`，已在当前表格中的字段不可重复选择），选中后插入新行（`transformType` 默认 `NO_TRANSFORM`，应用字段名称/编码留空待填）；表格下方独立"保存"按钮调用 `replaceAppSyncFieldMappings`（保存前前端校验：应用字段名称/编码必填，固定值/脚本转换方式的取值必填；脚本语法校验交给后端，前端不做）。
- [x] 9.4 字典子 tab 仅展示启用开关与分页大小，不展示字段映射表格与"新增字段"入口。

## 10. 验证

- [x] 10.1 `cd backend && ./gradlew test` 全量跑通；新增单元测试覆盖：
  - `AppSyncConfigServiceImpl`：创建默认 5 行数据域配置、更新单个数据域启用/分页大小、字段映射整体替换（新增/更新/删除混合场景）、`metadataFieldId` 不存在或未启用或 `bizType` 不匹配时拒绝、请求列表内重复 `metadataFieldId` 时拒绝、`transformType=FIXED_VALUE` 缺少 `transformValue` 时拒绝、`transformType=SCRIPT` 语法错误时拒绝、`syncDomain=DICT` 调用字段映射接口时拒绝、管辖组织范围校验（受限时越权拒绝）。
  - `TransformScriptValidator`：合法 JS 语法通过、明显语法错误（如缺少括号）被拒绝。
  - `MetadataFieldServiceImplTest`：新增用例覆盖"被应用同步字段映射引用时拒绝停用"。
  - `AppConfigServiceImplTest`：调整涉及四个旧布尔字段的既有断言（改为验证 `AppSyncConfigService.createDefaultDomainConfigs` 被调用）。
- [x] 10.2 手工核对开发数据库上 `V7`/`V8`/`V9` 迁移执行成功（含存量 `tab_app_config` 数据正确回填到 `tab_app_sync_domain_config`）。
- [x] 10.3 `cd frontend && npm run build`（`vue-tsc` 类型检查 + `vite build`）通过。
- [ ] 10.4 手工验证前端交互：切换数据域子 tab、新增/删除字段映射行、三种转换方式的取值输入展示逻辑、保存后重新加载数据一致。
- [x] 10.5 核对 `权限资源.txt` 描述文字与前端实际界面一致。

## 实现偏差说明（后端）

- **GraalJS 依赖版本选定为 `23.1.12`，而非 Maven Central 上数字最大的 `25.2.4`**：2.1 只要求
  "确认 Maven Central 上与本项目 JDK 21 兼容的最新稳定版本号"，未指定具体选择策略。查阅
  `graalvm.org` 官方文档确认 GraalVM Polyglot SDK 自 23.1 起版本号与目标 JDK 发行版一一对应
  （`23.1.x` ↔ GraalVM for JDK 21 LTS、`24.0.x` ↔ JDK 22、`24.1.x` ↔ JDK 23、`24.2.x` ↔
  JDK 24、`25.x` ↔ JDK 25），本项目 JDK 工具链固定 21，故选用 `23.1.x` 版本线里的最新补丁版本
  `23.1.12`（而不是数字更大但面向 JDK 25 的 `25.2.4`），与"最新稳定版本号"里"与本项目 JDK 21
  兼容"这一限定条件对齐；两个坐标（`polyglot`/`js-community`）在 Maven Central 均已核实存在。
- **`assertRequestsValid` 增加与 4.4 描述的 Bean Validation 声明重复的服务层兜底校验**：4.4 已
  在 `AppSyncFieldMappingSaveRequest` 上声明 `@NotBlank`/`@Pattern` 等注解，6.1 的
  controller 方法签名对 `List<AppSyncFieldMappingSaveRequest>` 请求体标注了 `@Valid`。实测/
  查阅 Spring Framework 6.2 的 `WebDataBinder`/`SpringValidatorAdapter` 源码确认：裸
  `List<T>` 请求体上的 `@Valid` 会触发 `jakarta.validation.Validator#validate(Object)` 校验
  "List 本身"，但并不会级联校验列表内每个元素（这与"实体的字段是 `@Valid List<Bar> items`"
  这种经典级联场景不同，后者才会被 Hibernate Validator 级联校验）。为避免业务正确性依赖这一
  不确定的框架行为，`AppSyncConfigServiceImpl#assertRequestsValid` 显式补充了应用字段名称/
  编码非空、转换方式取值合法两项校验，与 DTO 上的 Bean Validation 注解形成双重保险（DTO 注解
  仍保留，供未来若改为包一层容器 DTO 时复用，也用于 Swagger 文档展示约束）。
- **`assertRequestsValid` 内重复 `metadataFieldId` 校验拆成独立的第一趟遍历**：5.2 原描述里
  "请求列表内 `metadataFieldId` 不允许重复"与"逐项校验 `metadataFieldId` 存在/启用/`bizType`
  匹配"是同一趟遍历里的校验项之一。实现时拆成两趟独立遍历：第一趟只做重复检测（纯内存操作，
  不查数据库），第二趟再做逐项的数据库校验。这样"存在重复 id"这一错误在到达任何数据库查询之前
  就能被发现并快速失败，也让重复校验的正确性不依赖某一具体元素是否已通过其余校验，逻辑更清晰、
  测试更容易正确覆盖。
- **`AppConfigEntity`/`AppSyncConfigServiceImpl` 之间抽出共享工具 `app/support/AppScopeGuard`**：
  5.2 把这一点列为"评估是否…"的开放项，实现时确认可行并落地：把 `AppConfigServiceImpl` 原有的
  私有方法 `getExistingAppInScope` 的校验逻辑上提为无状态静态工具类
  `AppScopeGuard.getExistingAppInScope(AppMapper, OrgScopeService, Long)`，`AppConfigServiceImpl`
  的同名私有方法改为对它的薄转发，`AppSyncConfigServiceImpl` 直接调用同一个静态方法，避免两处
  重复实现同一段"应用存在 + 管辖组织范围"校验逻辑。
- **`replaceFieldMappings` 中 `transformType=NO_TRANSFORM` 时把 `transformValue` 落库为
  `null`，不保留请求中携带的原始文本**：design.md/proposal.md 未明确这一点。实现时选择在
  "不转换"语义下清空取值列，避免前端来回切换转换方式时遗留一份不再生效、但仍会展示的历史脚本/
  固定值文本，属于纯粹的数据整洁性选择，不影响接口契约（`transformValue` 字段本身仍然可空）。
- **`MetadataFieldController`/`MetadataFieldVO`/`MetadataFieldEntity` 的 Javadoc/`@Schema`
  描述文字同步把 "ORG/USER/POSITION/APP" 更新为 "ORG/USER/POSITION/APP/ROLE"**：tasks.md
  未列出这一处文档性质的改动，实现时顺手同步，避免接口文档描述与 `bizType=ROLE` 实际已支持的
  事实脱节；不涉及任何行为变化。
