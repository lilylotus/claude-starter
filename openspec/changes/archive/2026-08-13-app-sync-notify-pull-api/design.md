## Context

`app-api-credentials` 已经落地了配置存储：`tab_app_config`（AppId/AccessKey/SecretKey/signAlgorithm/syncMode/notifyUrl/notifyParams）与 `tab_app_sync_domain_config`（每个应用固定 5 行：ORG/USER/APP/ROLE/DICT，各自 `syncEnabled`+`pageSize`）、`tab_app_sync_field_mapping`（ORG/USER/APP/ROLE 四个数据域的字段级映射，`metadataFieldId` 关联 `tab_metadata_field`，转换方式 NO_TRANSFORM/FIXED_VALUE/SCRIPT，脚本目前只做语法校验不执行）。`metadata-field-management` 的 `bizType` 已经包含 ORG/USER/**POSITION**/APP 四类基础对象（角色 ROLE 后补），其中 POSITION 目前没有对应的 `SyncDomain`，也不在字段映射范围内。

`OperationLogRecorder` 建立了"service 实现类写库成功后手动调用记录入口"的既有模式（不用 AOP），org/user/position(任职)/role/app 的 `*ServiceImpl` 已在每个新增/编辑/启用/停用/删除方法末尾调用它。本次改动复用同一模式。

`backend/build.gradle` 已经（未提交）引入 `com.lmax:disruptor:4.0.0` 与 `org.apache.httpcomponents.client5:httpclient5:5.6.4`，均未被任何代码使用，本次首次落地。项目已有 `bcprov-jdk18on`（国密 SM3/SM4）与 `graalvm polyglot js-community`（JS 语法校验，见 `TransformScriptValidator`，当前只解析不执行）。

## Goals / Non-Goals

**Goals:**
- 把"通知"与"拉取"两种同步方式变成真正可运行的对外接口，接入现有配置模型。
- 提供不泄漏 Disruptor 细节的事件发布抽象，未来切 RabbitMQ/RocketMQ 只需替换一个实现类。
- 通知/拉取请求支持基于 AccessKey/SecretKey 的签名与验签（HMAC-SHA256 / HMAC-SM3），签名开关可控（`needSign`）。
- 字段级同步映射（`app-sync-field-mapping`）在通知/拉取的 payload 组装阶段真正生效（含脚本转换的执行，此前只做语法校验）。

**Non-Goals:**
- 通知失败重试、死信队列、多实例间的 nonce 防重放存储（本次仅做单进程内 nonce 短时去重，见 Risks）。
- 拉取接口的管辖组织范围校验（`org-scope-data-permission`）——拉取面向外部应用而非后台管理员，鉴权只走 AccessKey + 签名。
- 外部 MQ（RabbitMQ/RocketMQ）的真正接入代码，只搭抽象接口。
- 字典（DICT）数据域的变更通知/拉取（用户需求列出的触发资源只有组织/用户/任职/应用/角色，不含字典；DICT 域配置维持现状，不参与本次改动）。

## Decisions

### 1. "任职"（POSITION）升级为第 6 个 `SyncDomain`，而不是并入 USER 域

**决定**：`SyncDomain` 新增 `POSITION` 常量；`ALL_DOMAINS` 与 `FIELD_MAPPING_DOMAINS` 均纳入 POSITION；`AppSyncDomainConfigEntity` 从"每个应用固定 5 行"变为"固定 6 行"。实现时额外新增了第三个集合常量 `CHANGE_LOG_DOMAINS`（取值与 `FIELD_MAPPING_DOMAINS` 相同：ORG/USER/POSITION/APP/ROLE，不含 DICT），语义上专供 `app-sync-notify-pull` 能力校验拉取接口 `dataType` 参数合法性，与 `FIELD_MAPPING_DOMAINS`（供字段映射相关请求校验）语义区分开，避免不同能力共用同一个常量在未来其中一方需要单独调整取值时产生耦合。

**理由**：`tab_metadata_field` 的 `bizType` 本来就单列 `POSITION`（区别于 `USER`），字段映射的既有校验规则是"源字段必须属于对应数据域的业务对象类型"（`metadataFieldId.bizType == syncDomain`）。如果把任职变更强行挂在 USER 域下，任职的元数据字段（`bizType=POSITION`）就永远无法通过这条校验，字段映射对任职数据形同虚设。让 POSITION 成为独立域，模型保持对称、不用改动既有校验语义。

**代价**：需要一条新的 Flyway 迁移，为 `tab_app` 现存的每一行在 `tab_app_sync_domain_config` 补一行 `syncDomain=POSITION`（`syncEnabled=false`、`pageSize=20`，与 `createDefaultDomainConfigs` 现有默认值一致）；`AppSyncConfigServiceImpl.createDefaultDomainConfigs` 同步改为生成 6 行。

**Alternatives considered**：把任职并入 USER 域（拒绝，字段映射会失效）；任职完全不参与本次同步能力，只做组织/用户/应用/角色 4 类（拒绝，用户需求明确列出"任职"）。

### 2. "是否通知"复用现有 `syncMode`，不新增域级通知开关

**决定**：不新增任何"是否发通知"字段。触发条件 = 应用级 `tab_app_config.sync_mode == NOTIFY` **且** 该数据域 `tab_app_sync_domain_config.sync_enabled == true`。`syncMode == PULL` 时，变更只落"变更记录表"，不发出站请求，外部应用自行按序列号/id 拉取。

**理由**：现有 `SyncConfigUpdateRequest`/`app-api-credentials` spec 已经把 `syncMode` 定义为"通知/拉取二选一"的应用级开关，语义上已经等价于"是否需要通知"；`syncEnabled` 语义是"是否允许同步该数据域"，NOTIFY 模式下"允许同步"自然意味着"该域变更需要通知"，两者没有冲突，不需要叠加第三个开关制造歧义。

### 3. 签名开关 `needSign` 落在 `tab_app_config`，同时管辖出站通知与入站拉取

**决定**：`tab_app_config` 新增列 `need_sign`（`TINYINT(1)`/`BOOLEAN`，默认 `0`）。`needSign=true` 时：
- 出站通知请求必须携带签名参数（见 Decision 8/10），使用该应用当前的 `signAlgorithm`（`SHA256`→HMAC-SHA256，`SM3`→HMAC-SM3）与解密后的 SecretKey。
- 入站拉取请求必须携带同样的签名参数并通过校验，否则返回 401 类业务错误；`needSign=false` 时拉取请求仍需在 header 携带 `X-App-Key`（用于识别调用方 + 后续的域权限/分页配置查找），但不校验 ts/nonce/signature。

复用 `SyncConfigUpdateRequest`/`AppConfigController` 的既有读写路径，`AppConfigVO` 增加 `needSign` 字段回显。

### 4. 事件发布抽象 + Disruptor 实现

**包结构**：新建顶级模块 `cn.nihility.rbac.sync`（跨 org/user/role/app 多个业务模块，不归属任何一个），与 `app.sync`（现有字段映射/域配置）保持独立但互相依赖 `app.sync` 的 `SyncDomain`。

```
cn.nihility.rbac.sync
  event/DomainChangeEvent          事件负载 POJO（不可变，Builder）
  event/DomainEventPublisher        生产者对外接口（不暴露 Disruptor）
  event/DomainSnapshotSupport       事件快照构造工具（camelCase key，见 Decision 5）
  event/config/SyncProperties       `rbac.sync` 前缀配置（ring-buffer-size 等）
  event/support/DisruptorDomainEventPublisher   Disruptor 实现（SmartLifecycle）
  event/support/DomainChangeEventHandler         Disruptor `EventHandler`（薄适配层）
  event/support/DomainChangeEventProcessor       真正的"落库 + 通知"处理逻辑，不依赖 Disruptor API
  changelog/entity/AppDataChangeLogEntity        变更记录实体
  changelog/mapper/AppDataChangeLogMapper
  changelog/service/AppDataChangeLogService（含查询给拉取接口用的方法）
  notify/service/AppNotifyService                通知发送 + 通知结果落库
  notify/entity/AppNotifyRecordEntity
  sign/SignAlgorithmCodec                         HMAC-SHA256 / HMAC-SM3 计算与校验
  sign/NotifySignatureAppender                    出站通知签名参数构造（供 Decision 8 使用）
  sign/OpenApiSignInterceptor                     拉取接口验签（HandlerInterceptor）
  sign/NonceStore                                 nonce 单进程内去重（`@Scheduled` 定时清理）
  openapi/controller/SyncNotifyPullController      对外拉取接口 `/open/api/sync/**`
  openapi/dto/...
  transform/FieldMappingTransformer                拉取结果字段映射转换（含 SCRIPT 执行，见 Decision 9）
```

`DomainEventPublisher`：
```java
public interface DomainEventPublisher {
    void publish(DomainChangeEvent event);
}
```
`DomainChangeEvent` 字段：`dataType`（`SyncDomain` 五个可变更取值之一，不含 DICT）、`bizId`、`operationType`（复用 `cn.nihility.rbac.operationlog.constant.OperationType` 的 int 码值 CREATE/UPDATE/ENABLE/DISABLE/DELETE）、`snapshot`（`Map<String,Object>`，实体当前字段快照，key 为实体属性名/camelCase，与 `tab_metadata_field.field_code` 对齐）、`operator`（当前登录用户，用于变更记录审计字段）、`occurredAt`。

`DisruptorDomainEventPublisher`：单生产者（Spring 单例 publisher 内部串行调用即可，`ProducerType.MULTI` 兜底并发场景）、`RingBuffer` 大小可配置（默认 1024，2 的幂），`WaitStrategy` 用 `BlockingWaitStrategy`（默认低 CPU 占用，吞吐量在本场景足够，不追求微秒级延迟），单一 `DomainChangeEventHandler`（`EventHandler<DomainChangeEvent>`）处理："先落变更记录表拿到 `id`（即序列号）→ 若命中 Decision 2 的通知条件，逐个匹配应用调用 `AppNotifyService` 发送通知（同步阻塞在这个消费者线程内，失败只记录状态不重试，见 Non-Goals）"。事件发布方（各 `*ServiceImpl`）调用 `publish` 后立即返回，不等待落库/通知完成。

**切换外部 MQ 的路径**：新增一个实现 `DomainEventPublisher` 的类（如 `RabbitDomainEventPublisher`），在 `@Configuration` 里换掉注入的 Bean 即可；`DomainChangeEventHandler` 的落库+通知逻辑本身与 Disruptor 无关，可以直接被新 MQ 的消费者复用（消费者签名从 `EventHandler<DomainChangeEvent>` 换成 MQ 客户端的消息监听器，内部转调同一段处理逻辑，因此这段逻辑单独抽成 `DomainChangeEventProcessor`，不写死在 `EventHandler` 实现里）。

### 5. 数据变更事件产生点：紧邻 `OperationLogRecorder` 之后，快照走独立的 camelCase 构造（不复用 `OperationLogRecorder` 的中文快照）

**决定**：在 `OrgServiceImpl`（create/update/enable/disable/delete）、`UserServiceImpl`（同 5 个方法）、`PositionServiceImpl`（同 5 个方法，`dataType=POSITION`）、`RoleServiceImpl`（同 5 个方法）、`AppServiceImpl`（同 5 个方法）里，每个方法对 `operationLogRecorder.recordXxx(...)` 调用之后，紧接着调用 `domainEventPublisher.publish(DomainChangeEvent.builder()...build())`。

`snapshot` **不**复用该方法里给 `operationLogRecorder` 用的那份快照（`toLogSnapshot(entity)`/`recordCreate` 等内部构造的 Map，key 是中文字段名如"组织名称"，面向管理端操作日志页面展示）——那份快照的 key 无法被 Decision 9 的字段映射按 `fieldCode`（camelCase 实体属性名）匹配上。事件快照改为调用新增的 `cn.nihility.rbac.sync.event.DomainSnapshotSupport#snapshot(Object entity)`，内部用 `JacksonUtils.convert(entity, Map.class)` 直接从实体对象生成快照，key 天然是实体属性名（camelCase，如 `orgName`），与 `tab_metadata_field.field_code` 对齐；两份快照分别独立构造、互不影响，各自服务不同的下游（操作日志展示 vs. 拉取接口字段映射）。

`recordCreate`/`recordUpdate`/`recordStatusChange` 对应的事件用变更后（mutate 后）的实体快照；`recordDelete` 对应的事件用删除前的快照——需要在 `entity.setStatus(DELETED)` 之前单独调用一次 `DomainSnapshotSupport.snapshot(entity)` 捕获一份局部变量（如 `beforeEventSnapshot`），因为该方法内给 `operationLogRecorder` 用的 before 快照同样不能直接复用（中文 key 问题同上）。

不引入 AOP 切面，理由与 `OperationLogRecorder` 一致：变更点分散在业务方法内部不同位置（部分校验失败提前返回），AOP 环绕通知难以精确复用已经拼好的快照 Map，手动调用更直观、可控。

### 6. 变更记录表：`id` 自增列本身就是拉取用的序列号

**决定**：新建表 `tab_app_data_change_log`：

| 列 | 说明 |
| --- | --- |
| `id` BIGINT AUTO_INCREMENT PK | 全局单调递增，直接作为对外的"序列号"（`sequence`），不额外维护一份计数器——避免双写不一致 |
| `data_type` VARCHAR(20) | `SyncDomain` 取值：ORG/USER/POSITION/APP/ROLE（不含 DICT） |
| `biz_id` BIGINT | 变更对象主键 id |
| `operation_type` TINYINT | 复用 `OperationType` 码值 1/2/3/4/5 |
| `data_snapshot` TEXT | 变更后（DELETE 为变更前）实体字段快照，`JacksonUtils.toJson(Map<String,Object>)` |
| `create_by`/`create_time`/`update_by`/`update_time` | 审计字段 |

`sequence` 一词是 SQL 保留字/对象类型，本表不使用该列名，直接对外把 `id` 称为 `sequence`（DTO 字段名 `sequence`，取值即 `id`）。表名/列名过 MySQL/PostgreSQL/Oracle/SQL Server 保留字检查（`data_type`/`biz_id`/`operation_type`/`data_snapshot` 均非保留字）。

序列号是**全局**的（跨应用、跨数据域递增），不是按应用各自维护；"应用各自的拉取进度"由外部应用自己保存"上次拉到的 sequence"，下次传 `fromSequence` 请求增量数据，本系统不维护每个应用的游标状态（保持无状态、简单）。

### 7. 通知发送记录表：`tab_app_notify_record`（审计用，不驱动重试）

| 列 | 说明 |
| --- | --- |
| `id` BIGINT PK | |
| `change_log_id` BIGINT | 关联 `tab_app_data_change_log.id` |
| `app_ref_id` BIGINT | 关联 `tab_app.id` |
| `notify_status` TINYINT | 1=成功 2=失败 |
| `http_status` INT NULL | 外部接口返回的 HTTP 状态码，失败且未收到响应时为空 |
| `error_msg` VARCHAR(500) NULL | 失败原因摘要 |
| `create_by`/`create_time`/`update_by`/`update_time` | 审计字段 |

仅用于问题排查/展示，不驱动任何自动重试逻辑（Non-Goals）。

### 8. 通知请求契约：只传"指针"，不传完整数据

**决定**：`POST {notifyUrl}`，`Content-Type: application/json`：
```json
{
  "sequence": 1024,
  "dataType": "ORG",
  "operationType": "UPDATE",
  "bizId": 88,
  "occurredAt": "2026-08-13 10:00:00",
  "extra": { "自定义key": "自定义value" }
}
```
`extra` 来自 `tab_app_config.notify_params`（原样透传，不做处理）。外部应用收到通知后，预期用 `dataType`+`bizId`（单条纠错）或 `sequence` 之后的批量拉取（增量同步）主动调用本系统的拉取接口获取真正数据——通知只负责"叫醒"，不负责"运数据"，避免通知失败时数据只能靠人工补偿，而是天然可以靠拉取接口补齐。

请求头 `X-App-Key: {accessKey}` 无论 `needSign` 开关是否打开都携带（与 Decision 10 一致，用于识别调用方）；`notifyUrl` 后追加签名 query 参数（`signMethod`/`ts`/`appKey`/`nonce`/`signature`，见 Decision 10）仅在签名开关开启时进行，未开启时通知请求直接发往原始 `notifyUrl`，不追加任何 query 参数。

### 9. 拉取接口契约：两种入参形态，都只读"变更记录表"，不重查业务表

**决定**：两个接口都定义在 `SyncNotifyPullController`，路径前缀 `/open/api/sync`（区别于 `/api/**` 管理端前缀，语义上是外部系统开放接口）：

- `GET /open/api/sync/pull/by-id?dataType=ORG&bizIds=1,2,3` —— 按数据类型 + 一个或多个 `bizId`（逗号分隔）查询：对每个 `bizId` 取 `tab_app_data_change_log` 中该 `dataType`+`bizId` **最新一条**记录返回（不重新查询组织/用户等业务表的当前状态，避免"通知时是这个状态、拉取时业务表又变了"的语义混乱——拉取到的永远是"变更记录里那次变更"的快照）。
- `GET /open/api/sync/pull/by-sequence?dataType=ORG&fromSequence=1000&limit=50` —— 按 `dataType`（可选，缺省返回该应用当前 NOTIFY/PULL 模式下所有 `syncEnabled=true` 域的变更）查询 `id > fromSequence` 的记录，按 `id` 升序，最多 `limit` 条（缺省取该应用对应数据域的 `pageSize` 配置；`dataType` 缺省时按各域各自的 `pageSize` 取其中最小值兜底）。

两个接口都要求：
1. Header 携带 `X-App-Key`，据此定位 `tab_app_config` → 找到 `tab_app`；`AppKey` 不存在或对应应用已停用/删除时统一返回业务错误（不区分"key 不存在"和"应用不可用"，避免信息泄露）。
2. 校验请求的 `dataType` 落在该应用 `tab_app_sync_domain_config.sync_enabled=true` 的域范围内，不在范围内时返回空结果（不是报错，因为可能是外部系统探测式轮询多个域）。
3. `needSign=true` 时执行 Decision 10 的验签流程，失败返回 401 类业务错误。
4. 返回的每条记录，按该应用该 `dataType` 的字段映射配置（`tab_app_sync_field_mapping`）转换 `data_snapshot`：`NO_TRANSFORM` 直接取 `snapshot.get(fieldCode)`；`FIXED_VALUE` 直接取 `transformValue`；`SCRIPT` 用 GraalVM polyglot 执行 `transformValue`（约定脚本以 `value` 全局变量读入源字段值、脚本最后一个表达式的值作为结果，具体绑定方式与 `TransformScriptValidator` 现有语法校验的执行方言保持一致），输出 key 为 `appFieldCode`。没有为该 `dataType` 配置任何字段映射时，原样返回 `data_snapshot` 全部字段（兜底，避免"配置了同步但没配字段映射就什么都拿不到"）。

### 10. 签名算法（伪代码级）

Header：`X-App-Key: {accessKey}`（明文，签名开关无论开关都要传，用于识别调用方）。

URL query 参数（签名开关打开时必须携带，`signature` 除外均参与签名）：
- `signMethod`：`HMAC_SHA256` 或 `HMAC_SM3`（必须与该应用当前 `signAlgorithm` 一致，不一致直接判定验签失败）
- `ts`：13 位毫秒时间戳；校验时若 `|服务器当前时间 - ts| > 300000`（5 分钟）判定过期，验签失败
- `nonce`：调用方生成的随机字符串（建议 UUID 或 16 位以上随机十六进制），服务端维护一个 `ConcurrentHashMap<String appKey+":"+nonce, Long expireAt>` 做**单进程**内 5 分钟窗口去重，重复 `nonce` 判定为重放，验签失败（多实例部署下防重放不完整，见 Risks）
- `signature`：签名结果，见下

计算步骤（生成方与校验方一致）：
```
1. queryParams = 除 signature 外的全部 URL query 参数（含 appKey/ts/nonce/signMethod，以及接口自身业务参数如 dataType/bizIds/fromSequence/limit）
2. sortedPairs = queryParams 按 key 的 ASCII 码升序排列
3. canonicalQuery = sortedPairs 以 "k1=v1&k2=v2&..." 拼接（值不做二次编码，用原始字符串值）
4. urlSign = HEX_LOWER( HMAC(secretKey, canonicalQuery) )   // HMAC-SHA256 或 HMAC-SM3，均输出 32 字节摘要 → 64 位十六进制
5. 若请求方法是 POST/PUT/PATCH 且存在请求体：
       bodySign = HEX_LOWER( HMAC(secretKey, 原始请求体字节的 UTF-8 字符串) )
       signature = urlSign + bodySign     // 直接拼接，定长 64+64=128 位十六进制，无分隔符
   否则（GET 或无 body）：
       signature = urlSign                 // 定长 64 位
```
校验方按同样规则重新计算，`signature` 长度为 128 时拆前 64/后 64 分别比对 `urlSign`/`bodySign`，长度为 64 时只比对 `urlSign`；`secretKey` 用 `AppSecretProperties.sm4Key` 解密 `tab_app_config.secret_key` 得到明文后参与计算，不落日志。

HMAC-SHA256：`javax.crypto.Mac` + `HmacSHA256`。HMAC-SM3：`org.bouncycastle.crypto.macs.HMac` + `org.bouncycastle.crypto.digests.SM3Digest`。两者统一封装进 `sign/SignAlgorithmCodec`：
```java
public interface SignAlgorithmCodec {
    String hmac(String signAlgorithm, String secretKey, String content); // 返回小写十六进制
}
```

### 11. `HttpClientUtils` 设计要点

包路径：`cn.nihility.rbac.common.util.HttpClientUtils`（对齐 `JacksonUtils` 的既有组织方式），静态方法为主，内部持有一个进程级单例 `CloseableHttpClient`（`PoolingHttpClientConnectionManager`，`maxTotal`/`maxPerRoute` 可配置，默认 200/50）。

```java
HttpClientUtils.get(url, headers, queryParams, responseTimeoutMillis)
HttpClientUtils.postJson(url, headers, Object body, responseTimeoutMillis)      // 用 JacksonUtils.toJson 序列化
HttpClientUtils.postForm(url, headers, Map<String,String> formFields, responseTimeoutMillis)          // x-www-form-urlencoded
HttpClientUtils.postMultipart(url, headers, Map<String,String> fields, List<HttpFilePart> files, responseTimeoutMillis)  // multipart/form-data
HttpClientUtils.postBinary(url, headers, byte[] content, String contentType, responseTimeoutMillis)
HttpClientUtils.put/patch 对应重载
```
统一返回 `HttpClientUtils.HttpResult { int statusCode; Map<String,String> headers; byte[] body; }`，`toJson`/`toObj` 转换留给调用方用 `JacksonUtils`（不在 `HttpClientUtils` 里做自动反序列化，职责单一）。

配置项绑定 `rbac.http-client` 前缀（新增 `HttpClientProperties`）：
- `connect-timeout-millis`（默认 5000）
- `response-timeout-millis`（默认 5000，每次调用可传参覆盖）
- `max-total`（默认 200）、`max-per-route`（默认 50）
- `skip-ssl-verify`（默认 `false`；`true` 时用信任所有证书的 `SSLContext` + `NoopHostnameVerifier`，仅建议内网自签场景开启）

### 12. Flyway 迁移

新增一个增量迁移文件（沿用现有"单一基线 + 增量"约定，见 `backend-common-utilities` spec）：
1. `ALTER TABLE tab_app_config ADD COLUMN need_sign TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否需要签名/验签校验' AFTER sign_algorithm;`
2. `INSERT INTO tab_app_sync_domain_config (app_id, sync_domain, sync_enabled, page_size, create_by, create_time, update_by, update_time) SELECT id, 'POSITION', 0, 20, 'system', NOW(), 'system', NOW() FROM tab_app;`（为存量应用补 POSITION 行）
3. `CREATE TABLE tab_app_data_change_log (...)`
4. `CREATE TABLE tab_app_notify_record (...)`

## Risks / Trade-offs

- **[Risk] nonce 防重放只在单进程内存里维护** → 多实例部署时同一 nonce 可能在不同实例上各自"首次出现"而放行两次。Mitigation：文档标注该已知限制；后续要做多实例强一致防重放可以换成 Redis（`spring-boot-starter-data-redis` 已在依赖里）维护 nonce 集合，本次不做（Non-Goal，避免过度设计）。
- **[Risk] 通知同步阻塞 Disruptor 消费者线程** → 单个 notifyUrl 响应慢会拖慢整条环形缓冲区的消费速度，进而影响变更记录落库的及时性（拉取接口读到的数据会有延迟，不会读错）。Mitigation：`HttpClientUtils` 调用时按 Decision 11 传入较短的 `responseTimeoutMillis`（通知场景可用比全局默认更短的超时，如 3s），超时即判定失败记录、不阻塞太久；如果后续要求更高吞吐，可以在 `DomainChangeEventHandler` 内把"落库"和"通知"拆成两个 Disruptor `EventHandler`（并行消费同一事件，通知阶段独立线程池执行）。
- **[Risk] SCRIPT 转换从"只校验语法"变成"真正执行"，存在恶意脚本读取宿主敏感信息或死循环的风险** → Mitigation：脚本执行只传入单个源字段值（不注入宿主对象、不给网络/文件系统访问能力，GraalVM `Context` 用最小权限沙箱构建，参考 `TransformScriptValidator` 现有的隔离方式）；执行加超时保护（如 200ms），超时判定转换失败，该字段跳过。
- **[Trade-off] 拉取接口只读变更记录表、不重查业务表** → 换来语义简单和实现一致，但如果外部应用错过了某条 UPDATE 记录直接问 `by-id`，拿到的是"最新一条变更记录"而不是数据库当前最新值（两者理论上应该一致，因为每次写操作都会产生一条记录；只有在"事件发布失败/被跳过"这种异常情况下才会不一致，属于可接受的已知边界，不做双重校验)。

## Migration Plan

按 Flyway 增量迁移自动执行，无需手工数据迁移；`need_sign` 默认 `false`、`POSITION` 域默认不启用，均为向后兼容默认值，不影响存量应用的现有行为。回滚方式：新增迁移文件本身不删除旧列/旧表，如需回退功能只需下线新接口/关闭 `needSign`，无需回滚 Flyway。
