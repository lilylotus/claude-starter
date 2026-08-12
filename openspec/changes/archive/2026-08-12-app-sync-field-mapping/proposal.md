## Why

应用配置里现有的"数据范围"（`app-api-credentials` 能力）只是组织/用户/应用/字典四个布尔开关，只能整体决定"允不允许同步某类数据"，无法表达"同步哪些具体字段、这些字段怎么映射/转换成外部应用需要的字段"，也没有"每次拉取分页大小"这类对接细节。随着后续要真正对接外部系统的数据同步接口临近，需要先把"同步配置"从简单开关升级为按数据域配置字段级映射的能力。

用户在澄清问题里确认的四个关键决策：
1. 数据域从组织/用户/应用/字典四个，变为组织/用户/应用/角色四个支持字段级配置，字典域保留（仍是布尔开关，不做字段级配置）——即最终共 5 个数据域：组织/用户/应用/角色/字典。
2. 每个数据域的"当前同步数据字段"（可选字段名称、字段编码）复用已有的 `metadata-field-management`（`tab_metadata_field`）元数据字段目录；角色（ROLE）之前不在该目录覆盖范围内，本次新增 `bizType=ROLE` 并做数据库迁移预置（`tab_role` 的 `name`/`code`/`show_order`/`remark`，无 `ext` 扩展列）。
3. "每次应用拉取数据分页大小"按数据域各自配置一份（组织/用户/应用/角色/字典各一个 `pageSize`），而不是整个应用一份。
4. 转换方式的"转换脚本"需要做语法校验（不执行）：脚本语言用 JavaScript，引入 GraalJS 依赖（`org.graalvm.polyglot:polyglot` + `org.graalvm.polyglot:js-community`，纯 Java 实现，不需要本机 GraalVM 发行版）做静态语法解析，不做脚本执行——与既有"同步能力目前只做配置存储，不实现真正的通知发送/拉取"的范围边界保持一致。

本次改动**仍然只做配置能力**（数据域启用/分页大小配置、字段映射的增删改查、脚本语法校验），不实现真正的数据拉取/转换执行/对外通知接口——那些留给后续独立 change。

## What Changes

- 用新表 `tab_app_sync_domain_config`（每个应用 5 行，分别对应组织/用户/应用/角色/字典）替换 `tab_app_config` 上原有的 `sync_org_enabled`/`sync_user_enabled`/`sync_app_enabled`/`sync_dict_enabled` 四个布尔列：每行包含数据域标识（`sync_domain`）、是否启用（`sync_enabled`）、每次拉取分页大小（`page_size`，默认 20）。新建应用时自动生成 5 行（默认全部不启用，分页大小默认 20）；已有应用数据在迁移时按原有四个布尔列的值回填组织/用户/应用/字典四行，角色行默认不启用。
- `tab_app_config` 上原有的 `sync_mode`/`notify_url`/`notify_params`（整个应用一份的基础同步配置项）不变。
- 新增 `tab_app_sync_field_mapping` 表，为组织/用户/应用/角色四个数据域（不含字典）提供字段级同步映射配置：每行关联一个 `tab_metadata_field` 记录（同步的源字段，字段名称/字段编码来自该元数据字段，实时读取不做快照），加上应用字段名称、应用字段编码（外部应用需要的目标字段，管理员手工填写）、转换方式（`NO_TRANSFORM` 不转换 / `FIXED_VALUE` 固定值 / `SCRIPT` 转换脚本）与转换取值（固定值的具体值，或脚本源码）。保存整个数据域的字段映射列表时采用整体替换语义（先删后插，参照仓库里 `tab_role_permission` 的既有模式），不做按行的增量 CRUD 接口。
- 转换方式为"转换脚本"时，保存前用 GraalJS 对脚本文本做静态语法解析校验（`Context.parse`，不执行），语法错误时拒绝保存；新增 `build.gradle` 依赖 `org.graalvm.polyglot:polyglot`、`org.graalvm.polyglot:js-community`（已与用户确认）。
- `metadata-field-management` 能力新增 `bizType=ROLE`：通过数据库迁移预置角色（`tab_role`）的 `name`/`code`/`show_order`/`remark` 四个字段（无 `ext` 扩展列），复用元数据字段配置的既有查询/编辑/启停用接口，不新增专属接口。角色元数据字段**不**加入 `FormFieldBizType`（那是"表单字段定义"绑定的业务对象类型枚举，角色管理页面本身不接入动态表单渲染管线，避免语义混淆）。
- `metadata-field-management` 的"停用元数据字段"守卫规则扩展：除已有的"被有效表单字段定义绑定时拒绝停用"外，新增"被至少一条应用同步字段映射引用时拒绝停用"，避免停用后同步映射悬空引用一个已停用的源字段。
- 应用配置页面"同步配置" tab 内的"数据范围"区块，由四个 `el-switch` 改为左侧纵向 tabs（组织/用户/应用/角色/字典）+ 右侧展示当前数据域的配置：是否允许同步、拉取分页大小；组织/用户/应用/角色四个数据域额外展示字段映射表格（字段名称、字段编码只读展示源字段信息；应用字段名称、应用字段编码可编辑；转换方式下拉选择，选中"固定值"或"转换脚本"时展示对应的取值输入框/多行文本框），支持从元数据字段目录选择新增一行、删除已有行；字典数据域只展示启用开关与分页大小，不展示字段映射表格。写操作复用既有的 `AppManagement:app:config:editSync` 权限点，不新增权限资源编码。

## Capabilities

### Modified Capabilities

- `app-api-credentials`：同步配置能力扩展——数据域从"组织/用户/应用/字典四个布尔开关"变为"组织/用户/应用/角色/字典五个数据域各自的启用开关 + 拉取分页大小"，并为组织/用户/应用/角色四个数据域新增字段级同步映射配置（含转换方式、脚本语法校验）；前端配置页面"同步配置" tab 的"数据范围"区块相应改版。
- `metadata-field-management`：新增 `bizType=ROLE` 元数据字段目录（供本次同步字段映射选择源字段使用）；停用元数据字段的守卫规则扩展为同时校验应用同步字段映射的引用。

## Impact

- 新增数据库迁移：
  - `V7__app_sync_domain_config.sql`：建表 `tab_app_sync_domain_config`，回填存量数据，删除 `tab_app_config` 上的四个旧布尔列。
  - `V8__app_sync_field_mapping.sql`：建表 `tab_app_sync_field_mapping`。
  - `V9__metadata_field_role_seed.sql`：预置 `bizType=ROLE` 的元数据字段目录数据。
- 后端新文件（预期）：`app/sync/constant/SyncDomain.java`、`app/sync/constant/TransformType.java`、`app/sync/entity/AppSyncDomainConfigEntity.java`、`app/sync/entity/AppSyncFieldMappingEntity.java`、`app/sync/mapper/AppSyncDomainConfigMapper.java`、`app/sync/mapper/AppSyncFieldMappingMapper.java`（+ XML，源字段名称/编码需要 JOIN `tab_metadata_field`）、`app/sync/dto/*`、`app/sync/service/AppSyncConfigService.java` + `impl/AppSyncConfigServiceImpl.java`、`app/sync/controller/AppSyncConfigController.java`、`app/sync/support/TransformScriptValidator.java`（GraalJS 语法校验）。
- 后端修改文件：`app/entity/AppConfigEntity.java`（删除四个旧布尔字段）、`app/service/impl/AppConfigServiceImpl.java`（`createDefaultConfig` 同步创建 5 行数据域配置，不再写四个布尔列；`updateSyncConfig`/`toLogSnapshot` 移除对应字段）、`app/dto/AppConfigVO.java`/`SyncConfigUpdateRequest.java`（移除四个布尔字段）、`app/controller/AppConfigController.java`（保持不变，新增接口在新 controller）、`metadata/service/impl/MetadataFieldServiceImpl.java`（`disable` 增加同步字段映射占用校验）、`build.gradle`（新增 GraalJS 两个依赖）。
- 前端修改文件：`views/application/app/AppConfigView.vue`（"数据范围"区块改版为左侧 tabs + 字段映射表格）、`api/app.ts`（新增数据域配置、字段映射相关请求函数；`SyncConfigUpdateRequest` 类型移除四个布尔字段）、`types/app.ts`（新增 `SyncDomain`/`TransformType`/字段映射相关类型）。
- 权限资源编码：不新增，复用 `AppManagement:app:config:editSync`；同步更新 `权限资源.txt` 中该条目的描述文字（"组织/用户/应用/字典四个开关" → 反映新范围）。
- 不涉及真正的数据拉取执行、转换脚本执行、对外通知接口——留给后续独立 change。
