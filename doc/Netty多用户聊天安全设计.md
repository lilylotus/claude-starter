# Netty 多用户聊天安全设计（现状实录）

本文档整理的是 `cn.nihility.rbac.chat` 模块**当前已经落地实现**的聊天内容安全方案，
不是通用的理论参考（通用的理论性方案参见 `doc/Netty多用户聊天与端到端加密设计.md`）。
每一节都对应具体的源码文件，方便按文档定位代码、也方便以后改动代码时同步核对文档是否
仍然准确。

聊天系统整体规划分三阶段：**单节点网关 → 集群水平扩展 → 端到端加密**，当前已完整落地
第一、三阶段（集群水平扩展未开始），本文档聚焦"安全"这一个横切面，不是完整的架构文档。

## 一、安全目标与边界

| 场景 | 当前方案是否防护 | 说明 |
| --- | --- | --- |
| 网络中间人窃听/篡改 | ✅ | TLS（`wss://`） |
| 未认证连接滥用 | ✅ | 握手超时断连 + 建连限流 |
| 服务端/运维人员读取**单聊**明文 | ✅ | 端到端加密，服务端只存密文 |
| 服务端/运维人员读取**群聊**明文 | ❌（设计上不防护） | 群聊未做 E2EE，见第五节 |
| 数据库拖库后读取**单聊**历史消息明文 | ✅ | 落库的就是密文信封 |
| 数据库拖库后读取**群聊**历史消息明文 | ❌ | 群聊明文落库 |
| 会话之外的其他用户解密单聊消息 | ✅ | 会话密钥仅由会话双方各自算出 |
| 消息重放/重复提交 | ✅ | `msgId` 幂等去重 |
| 刷屏/恶意灌包 | ✅ | 按用户/按 IP 令牌桶限流 |
| 单聊安全码被中间人替换（密钥目录被篡改） | ⚠️ 仅告警，不阻断 | TOFU + 指纹变更提示，见第五节 |
| 群聊违规内容 | ✅ | 敏感词过滤（AC 自动机） |
| 单聊违规内容 | ❌（设计上做不到） | 服务端拿不到明文，见第四节 |

## 二、连接层安全

### 2.1 传输层加密（TLS）

- 实现：`chat/gateway/config/ChatTlsConfig.java`
- 生产环境必须配置真实证书路径（`chat.gateway.tls.cert-path`/`key-path`）；未配置时自动
  生成自签名证书兜底，启动日志会打印明确的"仅限本地开发环境"警告。
- `chat.gateway.tls.enabled=false` 可整体关闭 TLS 简化本地调试，该开关只用于开发环境。
- 对应 `chat-security` spec 的"传输层加密"需求。

### 2.2 认证握手与超时断连

- 实现：`chat/gateway/handler/ChatAuthHandler.java`
- 连接建立后处于"匿名"状态，`AUTHENTICATED` 属性为 `false`，此状态下除 `LOGIN`/
  `HEARTBEAT` 外的任何业务帧都会被直接拒绝（回 `ERROR` 帧，不路由、不落库）。
- 客户端发送携带既有登录 `accessKey` 的 `LOGIN` 帧，网关复用现有 REST 登录体系的
  `TokenService.verifyAccessKey` 校验（不是聊天模块自己再发一套 token），校验通过才把
  `userId` 绑定到该 Channel，并登记进 `ChatSessionRegistry`（支持同一用户多端同时在线，
  映射为 `userId -> Set<Channel>`）。
- 连接建立后限时等待认证（默认 10 秒，可配置），超时未完成认证 `ctx.close()` 主动断开，
  防止匿名连接长期占用资源（兼具 DoS 防护效果）。
- 连接失活（正常关闭/异常断开/被动关闭）统一在 `channelInactive`/`exceptionCaught` 里
  清理 `ChatSessionRegistry` 映射，不会遗留死连接。

### 2.3 心跳保活与空闲断连

- 实现：`chat/gateway/handler/ChatIdleTimeoutHandler.java` + Netty 内置
  `IdleStateHandler`。
- 客户端按约定周期发送心跳帧，服务端超过配置的空闲阈值未收到任何帧（含心跳）即主动关闭
  连接并清理会话映射。

### 2.4 按用户/按来源限流

- 实现：`chat/gateway/support/ChatRateLimiterManager.java` + `ChatTokenBucket.java`
- 两类独立的令牌桶：已认证用户的消息发送频率（`tryAcquireMessage`）、未认证阶段按来源
  IP 的新建连接频率（`tryAcquireConnection`）。
- 令牌桶按需懒创建，用 Caffeine 缓存并设置访问过期时间（10 分钟），避免长期运行下
  `Map` 无界增长；不活跃的 userId/IP 会被自动淘汰，下次访问重新以满桶状态创建，不影响
  限流正确性。

## 三、消息可靠性与防重放

这部分和"保密性"无关，但同样是"聊天内容安全"里"完整性/可用性"维度的一部分。

### 3.1 消息去重（防重放）

- 实现：`chat/gateway/support/ChatMessageDedupCache.java`（Caffeine 短路缓存）+
  `tab_chat_message.msg_id` 数据库唯一索引（最终兜底）。
- 客户端生成 `msgId` 随消息一起发送；相同 `msgId` 短时间内重复提交（网络重放或客户端
  异常重发）只保留一条记录，不重复落库、不重复投递，仍正常返回 ACK。
- 内存缓存跨进程重启会失效，但正确性由数据库唯一索引兜底，只是重启窗口期内去重缓存
  命中率短暂下降，不影响最终一致性。

### 3.2 ACK 确认与客户端重发

- 客户端发送带 `msgId` 的消息后等待服务端 ACK；超时未收到 ACK 允许用相同 `msgId`
  重发，服务端保持幂等（见 3.1）。

### 3.3 会话内消息顺序

- 实现：`chat/entity/ConversationEntity.java`（`nextSeq` 字段）+
  `chat/mapper/ConversationMapper.java`（`SELECT ... FOR UPDATE` 行锁取号）。
- 每个会话维护一个严格递增的 `nextSeq`，取号必须在事务内配合行锁，不允许绕过直接更新，
  保证同一会话内并发到达的多条消息也能分配到唯一且递增的会话内序号。客户端按该序号
  排序展示，不单纯依赖服务端接收时间戳。

### 3.4 离线消息补偿

- 实现：`chat/gateway/support/OfflineMessagePushService.java`
- 目标用户不在线时消息写入离线消息表；用户重新建立连接并完成认证后（`ChatAuthHandler`
  的 `handleLogin` 末尾），按会话内序号顺序补偿推送全部未送达的离线消息。

### 3.5 断线重连

- 前端 `frontend/src/utils/chatSocket.ts`：连接异常断开后按指数退避策略自动重连，
  重连成功后重新完成认证握手，依赖服务端离线消息补偿机制拉取错过的消息，不需要用户
  手动刷新页面。

## 四、内容安全：敏感词过滤（仅群聊）

- 实现：`chat/service/impl/SensitiveWordFilterServiceImpl.java` +
  `chat/service/support/AhoCorasickAutomaton.java`
- 基于内存中的 AC 自动机做多模式串匹配；应用启动时 `afterPropertiesSet()` 首次加载
  启用状态的词条构建自动机；后台新增/停用词条后调用 `reload()` 整体替换 `automaton`
  引用（`volatile` 字段，写少读多场景下读侧不需要加锁），立即生效、不需要重启服务。
- 后台管理：`chat/controller/SensitiveWordController.java`（分页查询/新增/删除/
  启用停用）。
- **关键边界（自 `chat-e2e-encryption` change 起生效）**：本能力**仅适用于群聊消息**。
  单聊消息经端到端加密后，`ChatSingleFrameBody.content` 在服务端眼里是不透明的密文
  信封，服务端没有明文可供匹配，因此单聊消息**不**经过、也不可能经过敏感词过滤——这是
  端到端加密带来的必然权衡（见第五节、第六节），不是遗漏。

## 五、单聊端到端加密（E2EE）

这是本文档的核心部分，对应 `openspec/specs/chat-e2e-encryption/spec.md`。核心原则：
**服务端只负责存储与转发密文，任何时候都不持有、不能生成能解密单聊消息的私钥材料。**

### 5.1 身份密钥：客户端生成，口令包裹后本地存储

- 实现：`frontend/src/utils/chatCrypto.ts`（密码学原语）+
  `frontend/src/utils/chatKeyStore.ts`（IndexedDB 落库）+
  `frontend/src/stores/chat.ts` 的 `unlockOrGenerateIdentityKeys`。
- 用户首次使用聊天功能时，浏览器本地生成一对 **X25519** 身份密钥对
  （`crypto_box_keypair`）。
- 私钥用登录密码在本地派生出的"包裹密钥"加密后存入 IndexedDB，**明文私钥不出浏览器、
  不发送给服务端、不以明文落盘**：
  - 口令派生：`crypto_pwhash`（**Argon2id**，`OPSLIMIT_INTERACTIVE`/
    `MEMLIMIT_INTERACTIVE` 强度档位）。
  - 包裹/解包：`crypto_secretbox_easy`/`crypto_secretbox_open_easy`
    （**XSalsa20-Poly1305** AEAD）。
- 用户每次登录成功后，用当次输入的密码重新派生包裹密钥解密出私钥、加载进内存（完成
  "解锁"），这个解锁动作只发生一次（首次生成或之后每次解锁），不会每次登录都重新生成
  密钥——除非本地存储确实被清空（见 5.5 的代价说明）。
- 账号在浏览器本地的分区键用的是**账号编码**（`tab_user.code`）而不是数值型
  `userId`：REST 登录响应不带数值 userId，数值 userId 要等 WebSocket 网关认证成功
  （`LOGIN_ACK`）才能拿到，而"登录成功后立即解锁/生成密钥"这一步必须在那之前完成。

> **依赖说明（历史踩坑记录）**：密码学原语全部基于 `libsodium-wrappers-sumo`
> （WebAssembly 封装的 libsodium，全量构建）。项目早期曾误用精简版
> `libsodium-wrappers`，该构建不含 `crypto_pwhash` 整族 API，导致密钥生成/解锁
> 100% 必然失败（`TypeError: length cannot be null or undefined`）；已在
> `openspec/changes/archive/2026-10-09-fix-chat-pwhash-dependency/` 修复，现固定
> 使用 sumo 版本，不能回退。

### 5.2 身份公钥目录（服务端）

- 实现：`backend/.../chat/entity/ChatUserKeyEntity.java`（表 `tab_chat_user_key`）+
  `ChatUserKeyServiceImpl.java` + `ChatKeyController.java`。
- 每个用户至多一条记录（`userId` 唯一索引），只存身份**公钥**（Base64）与一个服务端
  留痕用的指纹（`SHA-256`，仅审计用途，**不是**安全码校验的依据——安全码比对在客户端
  用公钥本身重新计算）。
- `registerMyKey`：已登录用户注册/更新本人公钥，只能操作与当前登录用户一致的记录；
  并发首次注册时若命中唯一索引冲突，回退为更新已存在记录，保持接口整体幂等。
- `getUserKey`：按目标用户查询其已注册公钥；若目标用户从未注册过（典型场景：对方还
  没有在本次修复后登录过一次），明确返回业务错误"该用户尚未注册聊天身份公钥，无法
  建立加密会话"，由前端据此中止发送并提示，不会静默失败。

### 5.3 会话密钥协商与消息级前向保密棘轮

- 实现：`frontend/src/utils/chatCrypto.ts` 的 `computeSharedSecret`/
  `deriveMessageKey`。
- **静态-静态 X25519 ECDH**（`crypto_scalarmult`）：A 用自己的身份私钥 + B 的身份
  公钥，B 用自己的身份私钥 + A 的身份公钥，双方各自独立算出同一个会话共享密钥 `SK`，
  全程在各自客户端本地完成，不经过服务端。注意这是静态-静态 ECDH，不是 Signal X3DH
  那种引入一次性 prekey 的协议——只要双方身份密钥对不变，`SK` 就不变。
- **对称密钥棘轮**（HKDF-Expand 等效实现）：libsodium 标准版没有原生 HKDF-Expand
  API，用 `crypto_generichash`（Blake2b，keyed 模式）以 `SK` 作为 key，把
  `counter（8 字节大端）+ 发送方 userId（8 字节大端）` 拼接成的 16 字节上下文信息作为
  输入，派生每条消息独立的一次性 `messageKey`，用完即弃、不缓存、不在多条消息间复用。
  额外编码发送方 `userId` 是为了让"我发给对方"和"对方发给我"两个方向在同一个
  `counter` 取值下也不会派生出相同的 `messageKey`。
- 发送方向的棘轮计数器持久化在 `frontend/src/utils/chatKeyStore.ts` 的
  `sendCounters` IndexedDB store，而不是只存内存——否则浏览器刷新/重新登录后计数器
  归零，会用同一个 `counter` 重新派生出和历史某条消息完全相同的 `messageKey`，破坏
  前向保密目标。
- **这不是完整的 Signal Double Ratchet**：只做了对称密钥棘轮（KDF 链式派生
  message key），没有做 DH 棘轮部分（即不会每条消息/每轮对话滚动新的 DH 密钥对）。
  这是刻意的范围裁剪，换取实现复杂度的大幅下降，代价是达不到 Double Ratchet 的
  "后向安全/自愈"特性（身份私钥一旦泄露，用该私钥能推导出的历史/后续会话密钥都可被
  还原；对称棘轮本身保证的是"单条消息密钥泄露不影响其它消息"）。

### 5.4 消息加密与密文信封

- 实现：`frontend/src/utils/chatCrypto.ts` 的 `encryptMessage`/`decryptMessage`，
  信封结构 `ChatCiphertextEnvelope`。
- AEAD 算法：`crypto_aead_xchacha20poly1305_ietf_encrypt/decrypt`，24 字节随机
  nonce，每条消息独立生成。
- 密文信封 `{ ciphertext, nonce, ratchetHeader: { counter, version } }` 直接
  `JSON.stringify` 后塞进现有消息发送通道的 `content` 字段（后端
  `ChatSingleFrameBody.content`/`ChatMessageVO` 的同一个字段），**不新增传输通道**。
  服务端把整个信封当作不透明字符串路由/落库，不解析、不校验其内部结构。
- 发送方身份公钥指纹（`senderIdentityKeyFingerprint`）**不放在信封内部**，而是作为
  帧体/DTO 的同级字段传输、原样落库到 `tab_chat_message.sender_identity_key_fingerprint`
  列——这样服务端完全不解析信封 JSON 就能透传这个指纹快照，供接收端在解密前做安全码
  比对（与归档 change design.md 最初设想的"指纹放信封内部"不同，以这个已落地的真实
  实现为准）。
- 服务端对单聊消息的职责边界：只按 `msgId`/会话 id/发送者 id 等元数据做路由、去重、
  排序、落库/离线补偿（见第三节），**不解析、不解密、不以任何方式获知明文**。

### 5.5 安全码指纹校验与信任变更告警（TOFU）

- 实现：`frontend/src/stores/chat.ts` 的 `ensureCounterpartKey`/
  `acknowledgeTrustChange`/`computeMyFingerprint`。
- 指纹计算：对 Base64 公钥解码后的原始字节做 **Blake2b**（20 字节输出）摘要，按每
  4 字节分组、大写展示，供双方通过带外渠道（电话、线下）比对，确认密钥目录未被
  中间人替换。
- 采用 **Trust-On-First-Use（TOFU）**：首次与某用户建立单聊会话时，本地没有缓存过
  对方指纹，直接信任当前查询到的指纹并缓存（`chatKeyStore.setTrustedFingerprint`），
  不触发告警。此后若服务端返回的对方公钥指纹和本地缓存的不一致，判定为"对方安全码
  已变更"，在聊天界面明确提示用户，**但不阻断收发**——用户确认后才更新本地信任缓存
  （`acknowledgeTrustChange`）。这是只告警不强制阻断的设计取舍：优先保证可用性，把
  "是否信任变更后的新公钥"的判断权交给用户。
- **更换浏览器或清空本地数据的代价**：私钥只存在生成它的那台浏览器本地，没有任何
  服务端托管/备份、没有多设备同步。换设备、清浏览器数据，或忘记登录密码，都会导致
  本地私钥永久不可恢复、使用旧密钥加密的历史单聊消息永久无法解密——这是端到端加密
  "服务端不可能代为找回"的必然代价，前端在首次生成密钥时会用明显的文案提示用户
  （`ElMessage` 8 秒不自动消失的警告）。

## 六、设计上明确不做的事（Non-Goals）

以下都是已经明确评估过、刻意不做的范围，不是遗漏：

- **群聊端到端加密**：群聊继续走明文 + 服务端敏感词过滤的路径，没有做 Sender Key
  之类的群组加密方案。
- **完整 Signal Double Ratchet**：只做对称密钥棘轮，不做 DH 棘轮，见 5.3。
- **多设备/多浏览器密钥同步**：私钥只存在生成它的那台浏览器。
- **密钥托管、服务端备份/找回**：忘记密码或清空浏览器数据 = 历史单聊消息永久不可读。
- **图片/文件等非文本消息类型的加密**：当前聊天消息本身也只支持文本类型。
- **生产级 HSM/密钥轮换运维流程**。
- **单聊场景下的服务端内容审核**：端到端加密与服务端内容审核在设计上互斥，二者只能
  二选一，当前选择是"单聊完全不可审核，群聊可审核"。

## 七、关键源码索引

| 能力 | 后端 | 前端 |
| --- | --- | --- |
| 连接认证/会话映射 | `chat/gateway/handler/ChatAuthHandler.java`、`chat/gateway/ChatSessionRegistry.java` | `frontend/src/utils/chatSocket.ts` |
| TLS | `chat/gateway/config/ChatTlsConfig.java` | — |
| 心跳空闲断连 | `chat/gateway/handler/ChatIdleTimeoutHandler.java` | `chatSocket.ts`（心跳发送） |
| 限流 | `chat/gateway/support/ChatRateLimiterManager.java`、`ChatTokenBucket.java` | — |
| 消息去重/幂等 | `chat/gateway/support/ChatMessageDedupCache.java`、`tab_chat_message.msg_id` 唯一索引 | — |
| 会话序号 | `chat/entity/ConversationEntity.java`、`chat/mapper/ConversationMapper.java` | — |
| 离线补偿 | `chat/gateway/support/OfflineMessagePushService.java` | — |
| 敏感词过滤 | `chat/service/impl/SensitiveWordFilterServiceImpl.java`、`AhoCorasickAutomaton.java` | — |
| 身份密钥生成/本地存储 | — | `frontend/src/utils/chatCrypto.ts`、`chatKeyStore.ts` |
| 密钥目录 | `chat/entity/ChatUserKeyEntity.java`、`ChatUserKeyServiceImpl.java`、`ChatKeyController.java` | `frontend/src/api/chatKey.ts` |
| 会话协商/棘轮/加解密 | — | `chatCrypto.ts`（`computeSharedSecret`/`deriveMessageKey`/`encryptMessage`/`decryptMessage`） |
| 密文信封落库 | `chat/entity/ChatMessageEntity.java`、`ChatMessageServiceImpl.java` | `chatCrypto.ts`（`serializeEnvelope`/`deserializeEnvelope`） |
| 安全码/TOFU | — | `frontend/src/stores/chat.ts`（`ensureCounterpartKey` 等） |

## 八、对应 OpenSpec 规范

- `openspec/specs/chat-security/spec.md`：连接安全与内容安全基线（本文档第二、三、四节）。
- `openspec/specs/chat-messaging/spec.md`：消息路由与可靠性（本文档第三节）。
- `openspec/specs/chat-e2e-encryption/spec.md`：单聊端到端加密（本文档第五节）。
- 归档 change：`openspec/changes/archive/2026-09-18-chat-end-to-end-encryption/`
  （原始设计决策）、`openspec/changes/archive/2026-10-09-fix-chat-pwhash-dependency/`
  （依赖包问题修复）。
