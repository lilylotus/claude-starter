## Why

应用管理目前只覆盖内部的业务字段（名称、编码、负责人、所属组织等），完全没有支撑"外部系统调用本系统开放接口"所需的身份凭证与接口配置能力。为了让后续的开放接口（对外数据同步、对外查询等）具备可鉴权、可验签的基础，需要先给每个应用补上一套 AppId/AccessKey/SecretKey 凭证，以及配套的签名算法、数据同步范围配置，并在应用管理页面提供一个独立的"配置"入口页面管理这些内容。

本次改动**只做管理后台的配置能力**（凭证生成/展示/重置、签名算法选择、同步范围开关），不实现真正对外开放、执行签名验签或数据同步的接口——那些留给后续独立的 change，本次只是为它们打好凭证与配置的地基。

补充（实现完成后的三处调整）：
1. 配置页面三个分区（基础信息/接口配置/同步配置）改为 `el-tabs` 切换展示，不再纵向堆叠。
2. AppId/AccessKey 生成规则去掉 `app_`/`ak_` 前缀，统一为纯随机十六进制字符串。
3. 该能力（页面标题、Swagger 分组、权限点展示名）由"应用接口配置"更名为"应用配置"；权限点编码本身（`AppManagement:app:config` 等）不变。

补充（同步配置追加基础同步配置项）：
"同步配置"除组织/用户/应用/字典四个数据范围开关外，追加整个应用一份的基础同步配置项：同步方式（`通知`/`拉取` 二选一）；同步方式为"通知"时，需额外配置回调接口地址与自定义参数（key-value）。仍然只做配置存储，不实现真正的 HTTP 通知发送或拉取接口。

## What Changes

- 新增 `tab_app_config` 表，与 `tab_app` 一对一，新建应用时在同一事务内自动生成一条配置记录，包含：
  - 对外应用标识 AppId（`open_app_id`，系统生成，全局唯一，24 位随机十六进制，不带前缀）
  - AccessKey（`access_key`，系统生成，全局唯一，32 位随机十六进制，不带前缀）
  - SecretKey（`secret_key`，系统生成，落库前用 SM4 对称加密，不存明文）
  - 接口签名算法（`sign_algorithm`，`SHA256` 或 `SM3`，默认 `SHA256`）
  - 同步范围开关（`sync_org_enabled`/`sync_user_enabled`/`sync_app_enabled`/`sync_dict_enabled`，是否允许同步组织/用户/应用/字典数据，默认全部关闭）
  - 基础同步配置项：同步方式（`sync_mode`，`NOTIFY` 通知或 `PULL` 拉取，整个应用一份、不分数据域，默认 `PULL`）、通知回调接口地址（`notify_url`，同步方式为 `NOTIFY` 时必填，须为合法 http/https URL）、通知请求自定义参数（`notify_params`，JSON 对象，key-value 均为字符串）
- SecretKey 展示策略：创建应用时生成的初始 SecretKey **不**在任何查询接口返回明文；管理员需要明文时通过"重置 SecretKey"操作换取一个新的 SecretKey，该操作的响应**仅这一次**返回明文，此后所有查询接口只返回"已设置"这类不可逆的展示态，不再返回明文（即便是重置前的旧值也不可查）。
- 应用管理列表新增"配置"按钮（`AppManagement:app:config` 权限点），不使用弹窗，跳转到独立路由页面（`/application/list/:id/config`，与现有"详情"页 `/application/list/:id` 同级，页面标题"应用配置"），页面内以 `el-tabs` 切换三个分区：
  - 基础信息：AppId、AccessKey（明文常显，非敏感）、SecretKey（遮蔽展示 + "重置 SecretKey"按钮）
  - 接口配置：签名算法单选（SHA-256 / SM3）
  - 同步配置：组织/用户/应用/字典四个独立开关；同步方式单选（通知/拉取）；同步方式为"通知"时展示接口地址输入框与自定义参数（key-value，支持增删行）编辑区
- 配置相关的写操作（重置 SecretKey、修改签名算法、修改同步配置）复用 `org-scope-write-guard` change 已确立的管辖组织范围校验模式：按该应用的 `orgId` 校验当前登录管理员是否有权限操作，越权时报与"应用不存在"一致的错误提示。
- 新增 4 个权限资源编码并同步更新仓库根目录 `权限资源.txt`：`AppManagement:app:config`（配置页面访问）、`AppManagement:app:config:resetSecret`（重置 SecretKey）、`AppManagement:app:config:editSignAlgorithm`（修改签名算法）、`AppManagement:app:config:editSync`（修改同步配置）。

## Capabilities

### New Capabilities

- `app-api-credentials`：应用对外接口凭证（AppId/AccessKey/SecretKey）与接口配置（签名算法、同步范围开关）的生成、展示、重置、修改能力，以及配置页面的前端界面要求。

### Modified Capabilities

（无，`application-management` 现有需求不变，本次是新增独立能力，与其协作但不修改其既有需求）

## Impact

- 新增数据库迁移 `V3__app_config.sql`（建表）、`V4__app_config_permission_seed.sql`（权限点/菜单种子数据）、`V5__app_config_rename_and_no_prefix.sql`（更名 + 去前缀的展示文案/列注释修正）、`V6__app_sync_notify_config.sql`（追加 `sync_mode`/`notify_url`/`notify_params` 三列）。
- 涉及后端新文件：`app/entity/AppConfigEntity.java`、`app/mapper/AppConfigMapper.java`、`app/dto/AppConfigVO.java`、`app/dto/SignAlgorithmUpdateRequest.java`、`app/dto/SyncConfigUpdateRequest.java`、`app/dto/SecretKeyVO.java`、`app/service/AppConfigService.java` + `impl/AppConfigServiceImpl.java`、`app/controller/AppConfigController.java`、`app/support/AppCredentialGenerator.java`、`app/constant/SignAlgorithm.java`、`app/constant/SyncMode.java`、`app/config/AppSecretProperties.java`（`@ConfigurationProperties`，SM4 主密钥配置）。
- 涉及后端修改文件：`app/service/impl/AppServiceImpl.java`（`create` 时同事务内生成默认配置）、`application.yml`（新增 `rbac.app-secret.sm4-key` 配置项）。
- 涉及前端新文件：`views/application/app/AppConfigView.vue`、路由新增 `application/list/:id/config`、`api/app.ts` 新增配置相关请求函数、`types/app.ts` 新增配置相关类型。
- 涉及前端修改文件：`views/application/app/AppManagementView.vue`（新增"配置"按钮）。
- 同步更新仓库根目录 `权限资源.txt`。
- 不涉及真正对外开放的签名验签接口、数据同步接口——这些是后续独立 change 的范围，本次只落地凭证与配置数据模型、管理后台 CRUD 与展示。
