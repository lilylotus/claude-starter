## 1. 数据库迁移

- [x] 1.1 新增 Flyway 增量迁移：`tab_app_config` 加列 `need_sign TINYINT(1) NOT NULL DEFAULT 0`
- [x] 1.2 同一迁移：为 `tab_app` 存量每一行在 `tab_app_sync_domain_config` 补一行 `sync_domain='POSITION'`（`sync_enabled=0`，`page_size=20`，审计字段填 `system`/当前时间）
- [x] 1.3 同一迁移：建表 `tab_app_data_change_log`（`id` 自增即对外序列号、`data_type`、`biz_id`、`operation_type`、`data_snapshot`、四个审计字段）
- [x] 1.4 同一迁移：建表 `tab_app_notify_record`（`id`、`change_log_id`、`app_ref_id`、`notify_status`、`http_status`、`error_msg`、四个审计字段）
- [x] 1.5 迁移完成后本地跑一次 `./gradlew bootRun` 或 flyway 相关测试，确认迁移无语法错误、存量数据补行正确（实际通过 `./gradlew test` 触发 `RbacApplicationTests` 的 `@SpringBootTest` 全量上下文加载，Flyway 针对本地真实 MySQL 库自动执行 `V2__app_sync_notify_pull.sql` 成功，未单独跑 `bootRun`）

## 2. `SyncDomain` 扩展为六域

- [x] 2.1 `SyncDomain` 常量类新增 `POSITION`，`ALL_DOMAINS`/`FIELD_MAPPING_DOMAINS` 纳入 `POSITION`
- [x] 2.2 `AppSyncConfigServiceImpl.createDefaultDomainConfigs` 改为生成六行（新增 `POSITION` 行）
- [x] 2.3 确认字段映射相关校验（"源字段必须属于对应数据域的业务对象类型"）在 `syncDomain=POSITION` 时正确匹配 `bizType=POSITION` 的元数据字段（`AppSyncConfigServiceImpl#assertMetadataFieldValid` 逻辑未改动，天然对 POSITION 域生效，沿用既有单测覆盖）
- [x] 2.4 `AppConfigVO`/`AppSyncDomainConfigVO` 等既有 DTO/文档注释同步提到六个数据域，不再是"五个"

## 3. `needSign` 配置读写

- [x] 3.1 `AppConfigEntity` 新增 `needSign` 字段，映射 `need_sign` 列
- [x] 3.2 `SyncConfigUpdateRequest` 新增 `needSign` 字段（`Boolean`，`@NotNull` 必填，与 `syncMode` 同属整个应用一份的基础同步配置项，随请求一并提交）
- [x] 3.3 `AppConfigVO` 新增 `needSign` 字段回显
- [x] 3.4 `AppConfigServiceImpl`（`createDefaultConfig`/`updateSyncConfig`）保存/查询逻辑处理 `needSign`，默认值 `false`
- [x] 3.5 `AppConfigController` 对应接口的 OpenAPI 注解（`@Operation`/`@Schema`）更新，补充 `needSign` 说明

## 4. `HttpClientUtils` 工具类

- [x] 4.1 新增 `HttpClientProperties`（`rbac.http-client` 前缀）：`connectTimeoutMillis`（默认 5000）、`responseTimeoutMillis`（默认 5000）、`maxTotal`（默认 200）、`maxPerRoute`（默认 50）、`skipSslVerify`（默认 `false`）
- [x] 4.2 新增 `cn.nihility.rbac.common.util.HttpClientUtils`：内部持有单例 `PoolingHttpClientConnectionManager` + `CloseableHttpClient`，`skipSslVerify=true` 时构建信任所有证书的 `SSLContext`
- [x] 4.3 实现 `get`/`postJson`/`postForm`/`postMultipart`/`postBinary` 及 `put`/`patch` 对应方法（`putJson`/`putForm`/`putBinary`/`patchJson`/`patchForm`/`patchBinary`），均支持传入自定义响应超时（未传时用全局默认）
- [x] 4.4 定义统一返回类型 `HttpClientUtils.HttpResult`（状态码、响应头、响应体字节数组），JSON 反序列化交由调用方用 `JacksonUtils` 处理
- [x] 4.5 编写单元测试：用 JDK 自带 `com.sun.net.httpserver.HttpServer` 起本地 mock HTTP 服务验证 GET/POST JSON/form/multipart/binary、响应超时覆盖全局默认值；**已知缺口**：`skipSslVerify`（跳过 HTTPS 证书校验）需要自签名证书测试服务器，搭建成本较高，本次未覆盖，仅通过代码走查确认实现路径

## 5. 签名/验签算法

- [x] 5.1 新增 `cn.nihility.rbac.sync.sign.SignAlgorithmCodec`（接口）+ `SignAlgorithmCodecImpl`：`hmac(signAlgorithm, secretKey, content)` 方法，`SHA256`→`HmacSHA256`（JDK `javax.crypto.Mac`），`SM3`→`bcprov` 的 `HMac`+`SM3Digest`，统一返回小写十六进制字符串
- [x] 5.2 新增签名参数构造工具 `NotifySignatureAppender`（供出站通知使用）：给定 URL、密钥、请求体，按 design.md Decision 10 生成 `signMethod`/`ts`/`appKey`/`nonce`/`signature`，拼接到 URL query
- [x] 5.3 新增验签工具 `OpenApiSignInterceptor` + `NonceStore`（供入站拉取接口使用）：重新计算并比对签名，校验时间戳时效窗口（5 分钟）与 nonce 单进程内去重（`ConcurrentHashMap` + `@Scheduled` 定时清理过期条目）
- [x] 5.4 单元测试：HMAC-SHA256/HMAC-SM3 签名生成与校验的正确性、篡改参数后验签失败、时间戳过期失败、nonce 重放失败；**实现简化说明**：对外拉取接口均为 GET（无请求体），`OpenApiSignInterceptor` 只实现并测试了 64 位十六进制的 urlSign 校验分支，128 位（含 body 签名）分支只在出站通知方向（`NotifySignatureAppenderTest`）覆盖，入站 POST/PUT/PATCH 含 body 验签当前无实际调用方，未单独测试

## 6. 领域事件发布抽象 + Disruptor 实现

- [x] 6.1 新增 `cn.nihility.rbac.sync.event.DomainChangeEvent`（不可变 POJO，Builder）
- [x] 6.2 新增 `cn.nihility.rbac.sync.event.DomainEventPublisher` 接口
- [x] 6.3 新增 `cn.nihility.rbac.sync.event.support.DomainChangeEventProcessor`（真正的"落库 + 通知"处理逻辑，不依赖 Disruptor API，供 Disruptor `EventHandler` 与未来 MQ 消费者共同调用）
- [x] 6.4 新增 `cn.nihility.rbac.sync.event.support.DisruptorDomainEventPublisher implements DomainEventPublisher`：初始化 `RingBuffer`（大小可配置，默认 1024）、`BlockingWaitStrategy`，注册消费者 `DomainChangeEventHandler` 处理 `DomainChangeEventProcessor`（`DomainChangeEventHolder` 作为可复用槽位对象承载不可变事件负载）
- [x] 6.5 新增配置项 `rbac.sync.ring-buffer-size`（默认 1024，`SyncProperties`）
- [x] 6.6 Spring 生命周期管理：`DisruptorDomainEventPublisher implements SmartLifecycle`，应用启动时 `start()` 启动 Disruptor，容器关闭时 `stop()` 调 `Disruptor#shutdown()` 优雅关闭

## 7. 变更记录落库与拉取查询

- [x] 7.1 新增 `AppDataChangeLogEntity`/`AppDataChangeLogMapper`（`tab_app_data_change_log`）
- [x] 7.2 `AppDataChangeLogServiceImpl#record` 落库变更记录，`data_snapshot` 用 `JacksonUtils.toJson` 序列化事件快照 Map
- [x] 7.3 新增按 `dataType`+`bizIds` 查询"每个 id 最新一条记录"的查询方法（`AppDataChangeLogMapper.xml`，窗口函数 `ROW_NUMBER() OVER (PARTITION BY biz_id ORDER BY id DESC)`）
- [x] 7.4 新增按 `dataType` 集合（可选，服务层已按应用允许同步的数据域过滤好）+`fromSequence`+`limit` 升序批量查询的方法

## 8. 通知发送

- [x] 8.1 新增 `AppNotifyRecordEntity`/`AppNotifyRecordMapper`（`tab_app_notify_record`）
- [x] 8.2 新增 `AppNotifyService`/`AppNotifyServiceImpl`：用 `NotifyTargetMapper`（联表 `tab_app_config`/`tab_app`/`tab_app_sync_domain_config`）查出该数据类型下 `syncMode=NOTIFY` 且该域 `syncEnabled=true` 的启用中应用列表，逐个应用构造通知请求体（design.md Decision 8）、按 `needSign` 决定是否附加签名参数、用 `HttpClientUtils.postBinary` 发起 POST 请求（3 秒响应超时）、把结果写入 `tab_app_notify_record`
- [x] 8.3 `DomainChangeEventProcessor` 落库变更记录成功后调用 `AppNotifyService`，单个应用通知异常需 catch 且不影响其余应用（对应 spec"一个应用通知失败不影响其他应用"场景，`AppNotifyServiceImplTest#notifyMatchedApps_shouldContinueOtherAppsWhenOneFails` 覆盖）

## 9. 各业务模块接入事件发布

- [x] 9.1 `OrgServiceImpl`：create/update/enable/disable/delete 五个方法在 `operationLogRecorder.recordXxx` 之后调用 `domainEventPublisher.publish(...)`，`dataType=ORG`
- [x] 9.2 `UserServiceImpl`：同样五个方法接入，`dataType=USER`
- [x] 9.3 `PositionServiceImpl`：同样五个方法接入，`dataType=POSITION`
- [x] 9.4 `RoleServiceImpl`：同样五个方法接入，`dataType=ROLE`
- [x] 9.5 `AppServiceImpl`：同样五个方法接入，`dataType=APP`
- [x] 9.6 快照口径说明（与原计划的实现偏差）：设计文档 Decision 5 原文"snapshot 复用该方法里已经构造好的『变更后快照』"字面上指向 `operationLogRecorder` 使用的中文字段名快照（如"组织名称"），但 Decision 4 又要求事件快照 key 与 `tab_metadata_field.field_code`（camelCase 实体属性名）对齐，两者矛盾——中文快照无法被拉取接口的字段映射按 `fieldCode` 匹配。实现时按 Decision 4（更具体、可测试）为准：新增 `DomainSnapshotSupport.snapshot(entity)`，用 `JacksonUtils.convert(entity, Map.class)` 直接从实体对象生成 camelCase 快照，与 `operationLogRecorder` 的中文快照分开构造、互不影响；`recordCreate`/`recordUpdate`/`recordStatusChange` 用变更后（mutate 后）的实体快照，`recordDelete` 在 `entity.setStatus(DELETED)` 之前单独捕获一份快照变量（`beforeEventSnapshot`）传给事件，语义符合"delete 前的 before snapshot"

## 10. 对外拉取接口

- [x] 10.1 新增 `cn.nihility.rbac.sync.sign.OpenApiSignInterceptor`（`HandlerInterceptor`）：解析 `X-App-Key`，定位应用与其 `AppConfigEntity`，`needSign=true` 时执行验签；数据域范围校验（"不在范围内返回空结果而非报错"）与拦截器"拒绝请求"语义冲突，改为放在 `SyncPullServiceImpl` 内处理，不在拦截器里做
- [x] 10.2 新增 `SyncNotifyPullController`，路径前缀 `/open/api/sync`；`IdentityAuthFilter` 白名单新增 `/open/api/sync/**`（不受管理端登录鉴权/`menu` 头校验影响），改由独立的 `OpenApiSignInterceptor`（`OpenApiSyncWebConfig` 注册，仅拦截该路径前缀）鉴权
- [x] 10.3 实现 `GET /open/api/sync/pull/by-id`：`dataType`、`bizIds`（逗号分隔）入参，查最新变更记录，按字段映射转换后返回
- [x] 10.4 实现 `GET /open/api/sync/pull/by-sequence`：`dataType`（可选）、`fromSequence`、`limit`（可选，缺省取该应用该域 `pageSize`，`dataType` 缺省时取已开通各域 `pageSize` 最小值）入参，返回列表
- [x] 10.5 新增字段映射转换执行器 `cn.nihility.rbac.sync.transform.FieldMappingTransformer`：`NO_TRANSFORM`/`FIXED_VALUE` 直接取值，`SCRIPT` 用 GraalVM `Context` 沙箱执行（`allowAllAccess(false)`，独立线程池 + 200ms 超时保护，超时用 `Context#close(true)` 强制中断），`value` 全局变量绑定方式对齐 `TransformScriptValidator`
- [x] 10.6 OpenAPI 文档注解（`@Tag`/`@Operation`）补充这两个接口

## 11. 前端

- [x] 11.1 应用配置页"同步配置"分区新增"需要签名/验签校验"开关，随 `SyncConfigUpdateRequest` 一并提交
- [x] 11.2 数据域左侧纵向 tabs 新增"任职"选项，复用现有域配置组件（启用开关、分页大小、字段映射表格）
- [x] 11.3 确认 `权限资源.txt` 无需新增权限点（本次改动复用既有 `AppManagement:app:config`，未新增页面/按钮；顺手核对 `权限资源.txt` 已由后端改动同步描述为"六个数据域"/"五个数据域"字段映射，无需再改）

## 12. 测试与收尾

- [x] 12.1 后端测试：创建应用后校验六个数据域配置行、`needSign` 默认值（`AppSyncConfigServiceImplTest#createDefaultDomainConfigs_shouldInsertSixRows`、`AppConfigServiceImplTest#createDefaultConfig_*`，Mockito 单元测试，非端到端集成测试，与仓库现有测试风格一致）
- [x] 12.2 后端测试：`syncMode=NOTIFY` 场景下组织新增触发通知（`AppNotifyServiceImplTest`，用 JDK 内嵌 `HttpServer` 充当 mock 通知地址）、变更记录正确落库（`AppDataChangeLogServiceImplTest`/`DomainChangeEventProcessorTest`）、`OrgServiceImpl`/`RoleServiceImpl` 新增了 `domainEventPublisher.publish` 调用参数的断言测试；序列号递增依赖 `tab_app_data_change_log.id` 的 `AUTO_INCREMENT` 列定义，未单独写测试验证（与仓库其余模块不为自增主键单独写测试的既有做法一致）
- [x] 12.3 后端测试：按 id 拉取、按 sequence 拉取的过滤（未开通域返回空）、字段映射转换生效（`SyncPullServiceImplTest`，Mockito 单元测试）
- [x] 12.4 后端测试：`needSign=true` 场景下正确签名可通过、错误签名/过期时间戳/重放 nonce 被拒绝（`OpenApiSignInterceptorTest`）
- [x] 12.5 `./gradlew build` 全量跑通（编译 + 419 个单元测试全部通过，含 `RbacApplicationTests` 对真实本地 MySQL 库的 Flyway `V2__app_sync_notify_pull.sql` 迁移验证）
- [x] 12.6 前端 `npm run build` 跑通类型检查（vue-tsc + vite build 均通过，无类型错误）
- [x] 12.7 实现完成后按 `openspec-doc-sync` 约定，基于真实 diff/测试结果核对更新 `proposal.md`/`design.md`/`tasks.md`：逐一比对 `sync/` 包全部源文件、`V2__app_sync_notify_pull.sql`、`OrgServiceImpl`/`UserServiceImpl`/`PositionServiceImpl`/`RoleServiceImpl`/`AppServiceImpl` 的暂存版本 diff、`AppConfigView.vue` diff 与 proposal.md/design.md 描述，均一致，无需改动；9.6 记录的 Decision 4/5 矛盾核实为已解决——design.md Decision 5 原文已准确描述实际实现（不复用 `OperationLogRecorder` 中文快照，改用 `DomainSnapshotSupport.snapshot(entity)`），与代码一致，未发现新的偏差
