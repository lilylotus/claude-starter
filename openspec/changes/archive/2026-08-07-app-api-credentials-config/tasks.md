## 1. 数据库迁移

- [x] 1.1 新增 `backend/src/main/resources/db/migration/V3__app_config.sql`：创建 `tab_app_config` 表（`id`、`app_id` 外键唯一、`open_app_id` 唯一、`access_key` 唯一、`secret_key`、`sign_algorithm` 默认 `SHA256`、`sync_org_enabled`/`sync_user_enabled`/`sync_app_enabled`/`sync_dict_enabled` 默认 `0`、`create_by`/`create_time`/`update_by`/`update_time`），字段命名遵循下划线分隔、避免数据库关键字，风格参照 `tab_admin_org_scope` 建表语句。

## 2. 后端：凭证生成与加密

- [x] 2.1 新增 `app/support/AppCredentialGenerator.java`：`generateOpenAppId()`（`app_` + 24 位随机十六进制）、`generateAccessKey()`（`ak_` + 32 位随机十六进制）、`generateSecretKey()`（48 位随机十六进制，无前缀），复用 `SecureRandom`，参考 `auth/util/PasswordDigestUtils.randomSalt()` 的实现风格。
- [x] 2.2 新增 `AppSecretProperties`（`@ConfigurationProperties(prefix = "rbac.app-secret")`，字段 `sm4Key`），风格参照 `RbacLoginProperties`。
- [x] 2.3 `application.yml` 新增 `rbac.app-secret.sm4-key` 配置项，使用一个本地开发默认值（16 字节 Base64），并加注释提示生产环境必须替换（参照现有 RSA 密钥对注释风格）。

## 3. 后端：数据层

- [x] 3.1 新增 `app/entity/AppConfigEntity.java`（`@TableName("tab_app_config")`），字段：`id`、`appId`（Java 字段名，`@TableField("open_app_id")` 映射到 `open_app_id` 列，代表对外 AppId，注意与下面的内部 FK 区分）、内部 FK 用单独字段名如 `appRefId`（`@TableField("app_id")`）、`accessKey`、`secretKey`（存储 SM4 加密后的 Base64 密文）、`signAlgorithm`、`syncOrgEnabled`、`syncUserEnabled`、`syncAppEnabled`、`syncDictEnabled`、审计字段。优先使用精确 Lombok 注解（`@Getter`/`@Setter`/`@Builder`），不用笼统 `@Data`。
- [x] 3.2 新增 `app/mapper/AppConfigMapper.java`（`BaseMapper<AppConfigEntity>`）。
- [x] 3.3 新增 `app/constant/SignAlgorithm.java`：`SHA256`/`SM3` 两个允许取值的常量（字符串常量或枚举，与 `AppStatus` 整型常量风格不同，用字符串是因为要直接落库为可读值）。

## 4. 后端：DTO

- [x] 4.1 新增 `app/dto/AppConfigVO.java`：`appId`、`accessKey`、`signAlgorithm`、`syncOrgEnabled`、`syncUserEnabled`、`syncAppEnabled`、`syncDictEnabled`、`createBy`、`createTime`、`updateBy`、`updateTime`（**不包含** SecretKey 字段）。
- [x] 4.2 新增 `app/dto/SignAlgorithmUpdateRequest.java`：`signAlgorithm`（`@NotBlank` + 自定义/正则校验只允许 `SHA256`/`SM3`）。
- [x] 4.3 新增 `app/dto/SyncConfigUpdateRequest.java`：`syncOrgEnabled`/`syncUserEnabled`/`syncAppEnabled`/`syncDictEnabled`（均 `@NotNull` boolean）。
- [x] 4.4 新增 `app/dto/SecretKeyVO.java`：`secretKey`（仅供"重置"接口响应使用，明文，仅这一个字段）。
- [x] 4.5 新增 `app/mapstruct/AppConfigConvert.java`（MapStruct，`INSTANCE` 静态单例，不用 `componentModel = "spring"`），entity → `AppConfigVO`（不映射 `secretKey`）。

## 5. 后端：Service

- [x] 5.1 新增 `app/service/AppConfigService.java` 接口：`createDefaultConfig(Long appRefId, String operator)`（供 `AppServiceImpl.create` 调用）、`getByAppId(Long appRefId)`、`updateSignAlgorithm(Long appRefId, SignAlgorithmUpdateRequest request)`、`updateSyncConfig(Long appRefId, SyncConfigUpdateRequest request)`、`resetSecretKey(Long appRefId)`。
- [x] 5.2 新增 `app/service/impl/AppConfigServiceImpl.java`：
  - 注入 `AppConfigMapper`、`AppMapper`（只读查应用实体，不注入 `AppService`，见 design.md Decision 6）、`OrgScopeService`、`CurrentOperatorService`、`OperationLogRecorder`、`AppSecretProperties`。`AppCredentialGenerator` 未作为依赖注入，改为纯静态工具类（与 `PasswordDigestUtils` 同款风格）直接静态调用，详见本文件末尾"实现偏差说明"。
  - `createDefaultConfig`：生成 AppId/AccessKey/SecretKey，SecretKey 经 `Sm4JdkUtils.encrypt(secretKey, appSecretProperties.getSm4Key())` 加密后落库，签名算法默认 `SHA256`，四个同步开关默认 `false`，写入审计字段。
  - 三个写操作（`updateSignAlgorithm`/`updateSyncConfig`/`resetSecretKey`）复用一个私有 `getExistingConfigInScope(Long appRefId)`：按 `appRefId` 查 `AppEntity`（`AppMapper.selectById`），不存在或已删除时抛"应用不存在"；再用 `orgScopeService.isOrgIdAllowed(CurrentUserContext.getUserId(), appEntity.getOrgId())` 校验，不通过时同样抛"应用不存在"（不额外暴露越权信号，与 org-scope-write-guard change 风格一致）；校验通过后按 `appRefId` 查 `AppConfigEntity`。
  - `resetSecretKey`：生成新明文 SecretKey，SM4 加密后 `updateById`，记录操作日志（只记录"重置了 SecretKey"这一事实，不记录明文/密文，见 design.md Decision 8），返回 `SecretKeyVO`（明文）。
  - `updateSignAlgorithm`/`updateSyncConfig`：更新对应字段，记录操作日志（字段级前后值快照，`resourceType` 用 `OperationLogResourceType.APP`，`targetId`/`targetName` 取自应用本身）。
  - `getByAppId`：不做管辖范围校验（对齐现有 `AppServiceImpl.getById` 的既有行为，详情类查询不受管辖范围限制），查不到时抛"应用不存在"。
- [x] 5.3 `app/service/impl/AppServiceImpl.java`：注入 `AppConfigService`；`create` 方法插入 `AppEntity` 成功后调用 `appConfigService.createDefaultConfig(entity.getId(), operator)`；整个 `create` 方法加 `@Transactional`（参照 `UserServiceImpl.create` 的既有用法），保证两张表原子写入。

## 6. 后端：Controller

- [x] 6.1 新增 `app/controller/AppConfigController.java`（`@Tag`/`@Operation` 注解齐全），路由前缀 `/api/apps/{id}/config`：
  - `GET /api/apps/{id}/config` → `AppConfigVO`
  - `PUT /api/apps/{id}/config/sign-algorithm`（`@Valid @RequestBody SignAlgorithmUpdateRequest`）→ `AppConfigVO`
  - `PUT /api/apps/{id}/config/sync`（`@Valid @RequestBody SyncConfigUpdateRequest`）→ `AppConfigVO`
  - `POST /api/apps/{id}/config/secret-key/reset` → `SecretKeyVO`
  - controller 保持薄层，只做参数接收 + 调用 service。

## 7. 权限资源编码

- [x] 7.1 仓库根目录 `权限资源.txt` 的 `AppManagement` 分组下新增 4 条：`AppManagement:app:config`（应用接口配置页面访问）、`AppManagement:app:config:resetSecret`（重置 SecretKey）、`AppManagement:app:config:editSignAlgorithm`（修改签名算法）、`AppManagement:app:config:editSync`（修改同步配置）。
- [x] 7.2 若菜单/权限点数据由数据库迁移脚本预置（检查 `V1__init_schema.sql` 里 `tab_permission`/`tab_menu` 相关 INSERT 是如何为 `AppManagement:app:*` 现有权限点建的记录），新增迁移语句为这 4 个权限点补充对应的 `tab_permission`（以及如果有对应按钮级菜单资源记录也一并补充），风格与现有 `AppManagement:app:*` 记录保持一致。

## 8. 前端：路由与列表入口

- [x] 8.1 `frontend/src/router/index.ts`：新增子路由 `application/list/:id/config`（命名如 `application-list-config`），懒加载 `AppConfigView.vue`，`meta.permissionKey: 'AppManagement:app:config'`，风格参照现有 `application-list-detail` 路由。
- [x] 8.2 `frontend/src/views/application/app/AppManagementView.vue`：操作列新增"配置"按钮（`v-if="hasPermission('AppManagement:app:config')"`），点击 `router.push({ name: 'application-list-config', params: { id: row.id } })`。

## 9. 前端：API 与类型

- [x] 9.1 `frontend/src/types/app.ts`（或对应位置）新增：`AppConfigVO`（appId/accessKey/signAlgorithm/syncOrgEnabled/syncUserEnabled/syncAppEnabled/syncDictEnabled/createBy/createTime/updateBy/updateTime）、`SignAlgorithmUpdateRequest`、`SyncConfigUpdateRequest`、`SecretKeyResult`。
- [x] 9.2 `frontend/src/api/app.ts`：新增 `getAppConfig(id)`、`updateAppSignAlgorithm(id, payload)`、`updateAppSyncConfig(id, payload)`、`resetAppSecretKey(id)` 四个请求函数。

## 10. 前端：配置页面

- [x] 10.1 新增 `frontend/src/views/application/app/AppConfigView.vue`，全页展示（非弹窗），风格参照 `AppDetailView.vue`（独立路由、独立拉取数据、页面左上角"返回"按钮回到应用列表）：
  - 基础信息卡片：AppId（可复制）、AccessKey（可复制）、SecretKey 行（固定遮蔽占位文案，如"已设置，点击重置查看新密钥"）+ "重置 SecretKey" 按钮（`v-if="hasPermission('AppManagement:app:config:resetSecret')"`，点击先 `ElMessageBox.confirm` 二次确认，因为是破坏性操作会让旧密钥失效）。
  - 重置成功后，用弹窗/`ElMessageBox.alert` 展示新 SecretKey 明文一次，附"请妥善保管，关闭后不再显示"提示 + 复制按钮，关闭后不再从任何状态里保留这个明文值（不写入 store，仅局部变量，弹窗关闭即丢弃）。
  - 接口配置卡片：签名算法单选（`el-radio-group`，SHA-256/SM3），保存按钮（`v-if="hasPermission('AppManagement:app:config:editSignAlgorithm')"`）。
  - 同步配置卡片：组织/用户/应用/字典四个 `el-switch`，保存按钮（`v-if="hasPermission('AppManagement:app:config:editSync')"`）。
- [x] 10.2 无权限对应按钮时不展示；页面本身的访问由路由 `permissionKey` 拦截。

## 11. 验证

- [x] 11.1 `cd backend && ./gradlew test` 全量跑通；新增单元测试覆盖：`AppConfigServiceImpl` 的创建默认配置、重置 SecretKey（新旧值不同、返回明文）、修改签名算法（合法/非法取值）、修改同步配置、管辖组织范围校验（受限时越权拒绝、范围内允许）。
- [x] 11.2 `AppServiceImpl.create` 补充/调整单元测试，验证创建应用时会调用 `AppConfigService.createDefaultConfig`。
- [x] 11.3 `cd frontend && npm run build`（`vue-tsc` 类型检查 + `vite build`）通过。
- [x] 11.4 手工核对 `权限资源.txt` 与前端实际按钮/路由权限点一致；核对时发现后端、前端两个 agent 并行实现时各自都在文件末尾追加过一份 `AppManagement:app:config*` 条目，导致重复，已合并去重为一份。
- [x] 11.5 已在实现完成后的总结中向用户明确提醒：同步配置本次只做四个布尔开关（未做更细粒度范围限定）。用户后续反馈了三项界面/命名调整（见下方第 12 节），未再要求调整同步配置粒度，视为该假设已被接受。

## 12. 实现完成后的调整（用户反馈）

- [x] 12.1 `AppConfigView.vue`：三个分区（基础信息/接口配置/同步配置）由纵向堆叠卡片改为 `el-tabs` 切换展示，参照 `FormFieldListView.vue` 的外层 tabs 用法。
- [x] 12.2 `AppCredentialGenerator`：`generateOpenAppId()`/`generateAccessKey()` 去掉 `app_`/`ak_` 前缀，改为纯随机十六进制字符串；同步更新 `AppConfigServiceImplTest` 里对应的断言与测试夹具数据。
- [x] 12.3 能力更名"应用接口配置" → "应用配置"：`AppConfigController` 的 `@Tag`/`@Operation`/Javadoc、`AppServiceImpl`/测试类的 Javadoc 注释、前端路由 `application-list-config` 的 `meta.title`、`权限资源.txt` 展示文案。权限点编码（`AppManagement:app:config` 等）本身不变。
- [x] 12.4 `V4__app_config_permission_seed.sql` 已在开发库执行过，不能直接编辑；新增 `V5__app_config_rename_and_no_prefix.sql`：`UPDATE tab_menu`/`tab_permission` 修正展示文案，`ALTER TABLE tab_app_config MODIFY COLUMN ... COMMENT` 修正 `V3` 里两个列注释中已过时的前缀格式描述（见 design.md Decision 8）。
- [x] 12.5 `cd backend && ./gradlew test` 全量跑通（含 V5 迁移在真实开发数据库上执行成功）；`cd frontend && npm run build` 通过。

## 13. 基础同步配置项：同步方式/通知地址/自定义参数（用户反馈）

- [x] 13.1 新增 `V6__app_sync_notify_config.sql`：`ALTER TABLE tab_app_config` 追加 `sync_mode`（`VARCHAR(16) NOT NULL DEFAULT 'PULL'`）、`notify_url`（`VARCHAR(255) NULL`）、`notify_params`（`TEXT NULL`，JSON 对象文本）三列。
- [x] 13.2 新增 `app/constant/SyncMode.java`：`NOTIFY`/`PULL` 两个允许取值常量。
- [x] 13.3 `AppConfigEntity` 新增 `syncMode`/`notifyUrl`/`notifyParams`（原始 JSON 文本）三个字段。
- [x] 13.4 `AppConfigVO` 新增 `syncMode`/`notifyUrl`/`notifyParams`（`Map<String, String>`）三个字段。
- [x] 13.5 `SyncConfigUpdateRequest` 新增 `syncMode`（`@NotBlank` + `@Pattern` 只允许 `NOTIFY`/`PULL`）、`notifyUrl`（`@Size` 长度约束，是否必填由服务层按 `syncMode` 校验）、`notifyParams`（`Map<String, String>`，可选）。
- [x] 13.6 `AppConfigConvert`：`notifyParams` 类型不匹配（entity 是 JSON 文本，VO 是 Map），用 `@Mapping(target = "notifyParams", ignore = true)` 显式忽略。
- [x] 13.7 `AppConfigServiceImpl`：
  - `createDefaultConfig`：`syncMode` 默认 `PULL`，`notifyUrl`/`notifyParams` 默认 `null`。
  - 新增私有 `toVO(entity)`，在 `AppConfigConvert.INSTANCE.toVO` 之后用 `JacksonUtils`（复用其内置 `MAP_STRING_TYPE_REFERENCE`）把 `notifyParams` 的 JSON 文本解析回 `Map`（为空时回填空 `Map` 而不是 `null`），`getByAppId`/`updateSignAlgorithm`/`updateSyncConfig` 统一改用这个方法而不是直接调用 `AppConfigConvert.INSTANCE.toVO`。
  - 新增私有 `assertValidNotifyUrl(notifyUrl)`：非空 + 用 `java.net.URI` 校验 `scheme` 为 `http`/`https` 且 `host` 非空，格式不合法抛 `BusinessException`。
  - `updateSyncConfig`：`syncMode=NOTIFY` 时调用 `assertValidNotifyUrl`；`syncMode=PULL` 时不校验、也不强制清空已保存的 `notifyUrl`/`notifyParams`。持久化前 `notifyParams` 经 `JacksonUtils.toJson` 序列化。
  - `toLogSnapshot` 新增"同步方式"/"通知回调接口地址"/"通知自定义参数"三个快照字段（非敏感信息，可直接记录，不同于 SecretKey）。
- [x] 13.8 补充/调整单元测试（`AppConfigServiceImplTest`）：`createDefaultConfig` 默认 `syncMode=PULL`；`PULL` 模式不要求 `notifyUrl`；`NOTIFY` 模式缺少 `notifyUrl` 被拒绝；`NOTIFY` 模式 `notifyUrl` 格式非法（非 http/https）被拒绝；`NOTIFY` 模式合法时正确落库（JSON 文本）并在响应里还原为 `Map`；`getByAppId` 在未配置过通知时 `notifyParams` 还原为空 `Map` 而不是 `null`；`SyncConfigUpdateRequest` 的 `syncMode` Bean Validation 校验（非法值/空值被拒绝）。
- [x] 13.9 前端 `types/app.ts`：新增 `SyncMode` 类型，`AppConfigVO`/`SyncConfigUpdateRequest` 补充 `syncMode`/`notifyUrl`/`notifyParams` 字段。
- [x] 13.10 前端 `AppConfigView.vue`："同步配置" tab 内新增"基础同步配置"分组：同步方式单选（通知/拉取）；同步方式为通知时展示接口地址输入框（浏览器端用 `new URL(...)` 做一次等价预校验，避免明显非法输入才发起网络请求）与自定义参数 `{key, value}[]` 行编辑器（支持增删行，提交前收敛为 `Record<string, string>`，忽略空 key 的行）；同步方式为拉取时隐藏这两项但不清空已填数据。
- [x] 13.11 `cd backend && ./gradlew test` 全量跑通（含 V6 迁移在真实开发数据库上执行成功）；`cd frontend && npm run build` 通过。
- [x] 13.12（用户反馈）"同步配置" tab 内两个分组顺序调整为"基础同步配置"（同步方式/接口地址/自定义参数）在前、"数据范围"（组织/用户/应用/字典开关）在后；分组标题的分隔线样式（`:not(:first-child)` 选择器）按 DOM 顺序自动跟随，无需改动样式代码。

## 实现偏差说明（后端）

- **`AppCredentialGenerator` 未注册为 Spring bean / 未被注入**：5.2 原计划里 `AppConfigServiceImpl`
  注入的依赖列表包含 `AppCredentialGenerator`，实现时改为纯静态工具类（`private` 构造器 + 全部
  `public static` 方法），与 `PasswordDigestUtils`（本身也是同款风格，2.1 明确要求参照它）保持
  一致，`AppConfigServiceImpl` 直接静态调用 `AppCredentialGenerator.generateXxx()`，不作为构造器
  依赖注入。行为等价，仅是"是否注册为 Spring bean"的实现选择，不影响外部可观察行为。
- **`getExistingConfigInScope` 拆分为两个私有方法**：5.2 描述的单一私有帮助方法
  `AppConfigEntity getExistingConfigInScope(Long appRefId)` 在实现中拆成了
  `AppEntity getExistingAppInScope(Long appRefId)`（校验应用存在性 + 管辖组织范围，返回
  `AppEntity`）与 `AppConfigEntity findByAppRefId(Long appRefId)`（按内部外键查配置行）两步，
  三个写操作各自依次调用这两步。原因：`resetSecretKey`/`updateSignAlgorithm`/`updateSyncConfig`
  的操作日志 `targetName` 都需要"应用本身的名称"（来自 `AppEntity`），若帮助方法只返回
  `AppConfigEntity` 则拿不到这个名称，要么在方法内部重复查一次 `AppEntity`（多一次不必要的
  数据库调用），要么拆成两步复用。拆分后两个方法各自职责单一，且避免了重复查询，净效果与
  design.md Decision 6 的校验逻辑（存在性 + 管辖范围 + "应用不存在"统一错误文案）完全一致。
- **迁移文件拆分为 V3（建表）+ V4（权限点/菜单种子数据）**：design.md/proposal.md 提到的
  `V3__app_config.sql` 只包含 `CREATE TABLE tab_app_config`；4 个新增权限点对应的
  `tab_permission`/`tab_menu` 种子数据以及"超级管理员角色关联新增权限点"放在新增的
  `V4__app_config_permission_seed.sql` 中，保持"建表"与"权限点数据初始化"两类关注点的
  迁移文件相互独立（任务 7.2 允许的两种拆分方式之一）。
