## Why

`app-api-credentials` 能力目前只做了应用对外接口凭证与同步配置的**存储**（AppId/AccessKey/SecretKey、签名算法、同步方式、数据域开关、字段映射），明确声明"不实现真正的 HTTP 通知发送、数据拉取或转换脚本执行"。这些配置目前是摆设：组织/用户/任职/应用/角色数据变化时不会触发任何通知，外部应用也没有可调用的拉取接口。需要把配置真正落地为可运行的通知/拉取能力，才能让应用同步这件事对接入方有意义。

## What Changes

- 在 `tab_app_config` 新增 `need_sign` 字段（是否需要签名/验签校验），挂在现有"同步配置"分区，随 `SyncConfigUpdateRequest`/`AppConfigController` 一并读写。
- 新增签名/验签工具：HMAC-SHA256（对齐现有 `SignAlgorithm.SHA256`）与 HMAC-SM3（对齐 `SignAlgorithm.SM3`，基于已有 `bcprov-jdk18on` 依赖）两种签名方式，`需要签名` 勾选后，出站"通知"请求携带签名头/参数，入站"拉取"请求做验签过滤。
- 新增 `HttpClientUtils`（`cn.nihility.rbac.common.util` 包，作为 `backend-common-utilities` 能力的新增能力项）：基于已引入但未使用的 `httpclient5` 依赖，封装 GET/POST/PUT/PATCH、JSON/form-data/x-www-form-urlencoded/binary 请求体、跳过 HTTPS 校验、连接池化、可配置的单次/全局超时。
- 新增数据变更事件发布抽象（生产者 API 不暴露 Disruptor 细节），基于已引入但未使用的 `disruptor` 依赖实现内存队列版本，为后续切换 RabbitMQ/RocketMQ 预留扩展点。
- 在组织、用户、任职、应用、角色五类资源现有 service 实现类的新增/编辑/启用/停用/删除写操作成功点，紧邻现有 `OperationLogRecorder` 调用之后，新增一次领域事件发布调用。
- 新增变更记录表（全局单调递增序列号），事件消费者落库变更记录，并在目标应用同步配置勾选"通知"时调用该应用的通知回调地址（按签名配置决定是否签名）。
- 新增对外"拉取"接口：按数据类型 + id 查询单条/按类型批量、按序列号游标批量拉取，支持验签。
- **BREAKING**: 无（新增字段与新增接口，不改变现有请求/响应契约）。

## Capabilities

### New Capabilities
- `app-sync-notify-pull`: 应用数据同步的领域事件发布、内存消息队列（可扩展外部 MQ）、变更记录落库与序列号、通知发送、拉取接口（按 id / 按序列号）、通知与拉取请求的签名/验签校验。

### Modified Capabilities
- `app-api-credentials`: 同步配置新增"是否需要签名/验签校验"（`needSign`）字段的读写与校验；原先"不实现真正的 HTTP 通知发送、数据拉取"的声明失效，改为引用 `app-sync-notify-pull` 能力。
- `backend-common-utilities`: 新增统一的 HTTP 客户端工具类 `HttpClientUtils`。

## Impact

- 后端 · 新增包 `cn.nihility.rbac.sync`（事件/变更记录/通知/签名/对外拉取接口/字段映射转换，共约 30 个文件）：
  - `event/`（`DomainChangeEvent`、`DomainEventPublisher`、`DomainSnapshotSupport`、`event/config/SyncProperties`、`event/support/{DisruptorDomainEventPublisher,DomainChangeEventHandler,DomainChangeEventHolder,DomainChangeEventProcessor}`）
  - `changelog/`（`AppDataChangeLogEntity`/`Mapper`/`Service`/`ServiceImpl`，`resources/mybatis/mapper/AppDataChangeLogMapper.xml`）
  - `notify/`（`AppNotifyRecordEntity`/`Mapper`、`AppNotifyService`/`ServiceImpl`、`NotifyPayload`/`NotifyTargetRow`/`NotifyStatus`，`resources/mybatis/mapper/NotifyTargetMapper.xml`）
  - `sign/`（`SignAlgorithmCodec`/`SignAlgorithmCodecImpl`、`SignCanonicalizer`、`SignConstants`、`NotifySignatureAppender`、`OpenApiSignInterceptor`、`NonceStore`）
  - `openapi/`（`SyncNotifyPullController`、`SyncPullService`/`ServiceImpl`、`SyncPullRecordVO`、`OpenApiCallerContext`、`OpenApiSyncWebConfig`）
  - `transform/FieldMappingTransformer`
  - `constant/SyncOperationType`
- 后端 · 新增：`common/util/HttpClientUtils`、`common/config/HttpClientProperties`。
- 后端 · 修改：`app` 模块（`AppConfigEntity`/`AppConfigVO`/`SyncConfigUpdateRequest`/`AppConfigController`/`AppConfigServiceImpl`/`AppServiceImpl` 新增 `needSign` 读写与事件发布调用点；`app.sync` 模块 `SyncDomain`/`AppSyncDomainConfigEntity`/`AppSyncConfigController`/`AppSyncConfigService`/`AppSyncConfigServiceImpl` 支持第 6 个 `POSITION` 域）；`org/service/impl/OrgServiceImpl`、`user/service/impl/{UserServiceImpl,PositionServiceImpl}`、`role/service/impl/RoleServiceImpl`（各自 create/update/enable/disable/delete 5 个方法接入事件发布，共 5 个类）；`auth/filter/IdentityAuthFilter`（白名单新增 `/open/api/sync/**`）；启动类 `RbacApplication` 新增 `@EnableScheduling`（支撑 `NonceStore` 定时清理过期 nonce）；`application.yml` 新增 `rbac.http-client`/`rbac.sync` 配置节。
- 数据库：新增 Flyway 迁移 `db/migration/V2__app_sync_notify_pull.sql`——`tab_app_config` 加列 `need_sign`；为存量应用补一行 `sync_domain='POSITION'`；新建 `tab_app_data_change_log`、`tab_app_notify_record` 两张表。
- 依赖：复用已在 `backend/build.gradle` 中但尚未落地使用的 `com.lmax:disruptor:4.0.0`、`org.apache.httpcomponents.client5:httpclient5:5.6.4`，未新增依赖。
- 前端：`views/application/app/AppConfigView.vue`、`api/app.ts`、`types/app.ts`——"同步配置"分区新增"需要签名/验签校验"开关，数据范围左侧纵向 tabs 新增"任职"域；拉取/通知接口面向外部系统调用，未新增管理端页面/路由。
- 权限资源：`权限资源.txt` 已核对，本次改动复用既有 `AppManagement:app:config`，未新增页面/按钮权限点。
