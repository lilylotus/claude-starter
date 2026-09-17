## Context

现状核对（写 design 前已读代码确认）：

- `backend/src/main/java/cn/nihility/rbac/chat/` 已实现聊天系统前两阶段（`chat-gateway-core`
  归档 change）：Netty 网关（`gateway/ChatGatewayServer`、`ChatAuthHandler` 复用
  `TokenService.verifyAccessKey` 做连接认证绑定 `userId`）、单聊/群聊消息路由、ACK/幂等、
  离线补偿、会话内严格递增序号（`ConversationEntity.nextSeq` + 行锁取号）、敏感词过滤
  （`SensitiveWordEntity` + AC 自动机，启动时加载进内存）。
- `tab_chat_message.content` 当前是 `TEXT` 明文列，表注释明确写着"本阶段服务端可见明文，
  不做信封加密"（`V1__init_schema.sql`）——这正是本 change 要终结的状态（仅针对单聊）。
- 前端 `frontend/src/stores/chat.ts`、`utils/chatSocket.ts`（原生 WebSocket 封装 + 指数退避
  重连）、`views/chat/ChatView.vue` 已实现消息收发 UI；`package.json` 当前没有任何加密库
  依赖。
- 登录鉴权：`cn.nihility.rbac.auth` 下 `AuthController`/`TokenService` 签发不透明
  `accessKey`（非 JWT），`password-login-auth` spec 覆盖登录密码校验；本 change 不改动这个
  协议，只在登录成功之后的前端流程里插入"解锁聊天身份密钥"步骤。
- 项目 MySQL 目标版本 5.7，手写 SQL 禁止窗口函数/CTE 等 8.0+ 特性；新增 Flyway 迁移脚本
  需从 `V7` 开始（当前最新已应用的是 `V6__add_approval_record_view_permission.sql`）。
- 本 change 是聊天系统三阶段规划的第三阶段（见 `chat-security` spec Purpose），只做**单聊**
  端到端加密，不做群聊 E2EE、不做多设备密钥同步。

## Goals / Non-Goals

**Goals:**
- 单聊消息端到端加密：服务端只能看到、存储密文信封，不持有任何可解密单聊消息内容的密钥。
- 身份密钥体系：X25519 身份密钥对生成于浏览器本地，私钥用登录密码本地派生的包裹密钥加密后
  存 IndexedDB；公钥注册到服务端「密钥目录」供对方查询。
- 会话密钥协商 + 前向保密：X25519 ECDH 得到共享密钥后，用 HKDF 对称棘轮为每条消息派生一次性
  message key，加密算法用 XChaCha20-Poly1305（AEAD）。
- 安全码/指纹校验 UI + TOFU 告警：防御密钥目录被篡改导致的中间人攻击。
- 现有可靠性机制（ACK、msgId 去重、离线补偿、会话内严格序号）对密文信封透明适用，不因加密
  而改变服务端路由/排序逻辑。

**Non-Goals（本阶段明确不做）：**
- 群聊端到端加密（Sender Key / MLS 类多方协议）——留给后续阶段，群聊继续走明文 + 服务端
  敏感词过滤的既有路径。
- 完整 Signal Double Ratchet 的 DH 棘轮部分（每条消息/每轮对话滚动新的 DH 密钥对）——本阶段
  只做对称密钥棘轮（HKDF 链式派生 message key），见 Decision 3 的取舍说明。
- 多设备/多浏览器密钥同步与历史消息漫游——私钥只存在生成它的那台浏览器本地，换设备/清浏览器
  数据视为丢失聊天历史解密能力（刻意的安全权衡，见 Decision 2）。
- 密钥托管、服务端密钥备份/找回。
- 图片/文件等非文本消息类型的加密（`chat-messaging` 当前也只支持文本类型，保持一致）。
- 生产级 HSM/密钥轮换运维流程。

## Decisions

### 1. 密文信封承载方式：复用现有帧体/DTO 字段，不新增传输通道
单聊的 `ChatSingleFrameBody`（网关帧体）与 REST 侧 `ChatMessageVO` 的消息内容字段，从
`String content`（明文）改为一个密文信封对象/JSON 字符串，包含：
`ciphertext`（Base64）、`nonce`（Base64）、`ratchetHeader`（棘轮计数器等，见 Decision 3）、
`senderIdentityKeyFingerprint`（发送方身份公钥指纹，供接收端在解密前做安全码比对）。
服务端把整个信封当作不透明字节串路由/落库，不解析、不校验其内部结构（校验完全在客户端做）。
- **备选**：新增独立的加密消息类型/协议帧，明文消息与加密消息走两套帧体。
- **为什么不选**：单聊本身在本 change 之后 100% 加密，不存在"同一会话内明文密文混发"的场景；
  复用既有字段改变其取值语义即可，避免网关协议层再增加一套分支处理。群聊字段保持不变（明文），
  两者用 `msgType`/会话类型区分，服务端处理路径本就已经按会话类型分叉。

### 2. 私钥存储：口令包裹（password-wrapped），无跨设备恢复
每个用户首次使用聊天时，浏览器本地生成 X25519 身份密钥对：
1. 用户登录时输入的密码（**只在浏览器内存中使用，不额外发送给服务端**，与登录请求本身发送给
   服务端做校验的密码是同一次输入、两次独立用途）经 PBKDF2（或 Argon2id，视浏览器端可用的
   `libsodium-wrappers` API 而定）派生出一个包裹密钥（wrap key）。
2. 用包裹密钥以 AES-GCM 加密身份私钥，密文连同盐值、KDF 参数存入浏览器 IndexedDB。
3. 每次登录成功后，前端用当次输入的密码重新派生包裹密钥，解密出私钥加载进内存（不持久化明文
   私钥），完成"解锁"。
4. 身份公钥注册/更新到服务端密钥目录（见 Decision 4）。

**Decision（刻意的安全权衡）**：不做密钥托管或服务端备份。用户忘记密码、清空浏览器数据、
更换设备，都会导致本地私钥不可恢复、历史加密消息永久不可读——这是端到端加密"服务端不可能
代为找回"的必然代价，需要前端在生成密钥/首次解锁时用明显文案提示用户。
- **备选 A**：服务端托管一份用户可找回的加密备份（类似 WhatsApp 的云备份）。
- **为什么不选**：本 change 明确限定客户端形态为"仅 Web 单设备"，引入服务端备份等于重新
  开一个"服务端持有可解密材料"的口子，与"服务端不可能解密"的核心目标冲突，留给未来如果要
  支持多设备时专门设计（届时大概率需要类似 Signal 的"链接设备"协议，而不是简单备份）。
- **备选 B**：私钥不加密，明文存 IndexedDB。
- **为什么不选**：IndexedDB 内容可被同源的恶意脚本（XSS）或本机其他有权限的进程直接读取，
  口令包裹至少要求攻击者同时拿到密码才能解出私钥，是低成本的额外防线。

### 3. 会话密钥协商与消息棘轮：简化对称棘轮，非完整 Double Ratchet
- 双方各自的身份公钥已在服务端密钥目录可查询到；发起方用自己的身份私钥 + 对方身份公钥做
  X25519 ECDH，得到一个会话共享密钥 `SK`（单聊双方各自独立计算出同一个 `SK`，双方身份密钥
  对不变则 `SK` 不变——这是"静态-静态 ECDH"，而非 Signal X3DH 里引入的一次性 prekey）。
- 用 `SK` 作为 HKDF 的输入密钥材料（IKM），结合一个单调递增的消息计数器（`ratchetHeader`
  里的 `counter`）派生每条消息独立的一次性 `messageKey`；`messageKey` 用后即弃（不缓存在
  内存以外的任何地方），提供"单条消息 key 泄露不影响其他消息"的前向保密。
- **备选 A**：完整 Signal Double Ratchet（DH 棘轮 + 对称棘轮结合，每次收发后滚动新的临时
  DH 密钥对）。
- **为什么不选（本阶段）**：Double Ratchet 的 DH 棘轮部分主要收益是"即使某一时刻的棘轮状态
  被窃取，后续消息仍然安全"（post-compromise security），在单设备、无消息转发的场景下收益
  有限，而实现和状态管理复杂度显著更高（需要处理乱序到达、跳过的消息密钥缓存等）。本阶段选择
  更简单、更容易正确实现和审计的对称棘轮，先把"服务端不可解密"这个核心目标落地。
- **演进路径**：`ratchetHeader` 预留字段结构（版本号），后续若要升级到完整 Double Ratchet，
  可以在不破坏已落库历史消息可解密性的前提下按版本号区分新旧棘轮逻辑。
- **备选 B**：不做棘轮，每条消息复用同一个从 `SK` 直接派生的固定 key。
- **为什么不选**：完全没有前向保密——一旦 `SK`（或某条消息的 key，因为都一样）泄露，会话
  内全部历史消息（只要密文还在）都可被解密，安全收益远低于对称棘轮，而实现复杂度差异很小。

### 4. 密钥目录：新表只存公钥，不做委托签发，靠客户端安全码校验兜底
新增 `tab_chat_user_key` 表（`user_id` 唯一、`identity_public_key`、`key_fingerprint`、
审计字段），提供 REST 接口：
- 注册/更新本人公钥（需登录态，只能写自己的 `user_id`）。
- 按 `user_id` 查询对方公钥（发起单聊/首次建立会话时调用）。

服务端**不**对公钥做任何额外签名/背书，这意味着如果服务端被攻破或恶意篡改密钥目录，理论上
可以对某个用户的公钥做替换实施中间人攻击（服务端自身作为攻击者）。这是端到端加密在"不引入
额外的可信第三方 CA"前提下的已知局限，缓解手段是纵深防御而非杜绝：
1. **安全码/指纹校验 UI**：双方在聊天页面可查看对方身份公钥的指纹（如公钥 SHA-256 摘要的
   分组十六进制展示），通过带外渠道（当面/电话/其他已验证信道）比对一致即可确认未被中间人
   替换。
2. **Trust-On-First-Use（TOFU）+ 变更告警**：客户端本地缓存"上次确认过的对方公钥指纹"，
   一旦服务端返回的公钥指纹发生变化，前端在聊天界面明确提示"对方安全码已变更，请重新核实"，
   而不是静默接受新公钥继续加密。
- **备选**：引入服务端对公钥的签名/证书链（类似 CA）。
- **为什么不选**：证书签发/吊销体系是重量级基础设施，且签发者若还是本服务端，并不能消除
  "服务端可以给自己签发的证书本身也造假"这个根本问题（除非引入独立于本系统的第三方 CA，
  超出本 change 范围）；TOFU + 安全码是 Signal/WhatsApp 等成熟 E2EE 产品的实际做法，成本
  与本项目规模匹配。

### 5. 单聊敏感词过滤：关闭服务端过滤，范围收窄为仅群聊
`chat-security` 现有「消息命中敏感词被拦截或替换」能力依赖服务端读取明文，端到端加密后
服务端对单聊内容不可见，无法继续执行。决定：
- 单聊路径完全跳过服务端敏感词过滤/替换逻辑，`ChatMessageEntity.filtered` 对单聊消息恒为
  `false`（或改为语义上的"不适用"，具体建表方案见 tasks.md）。
- 敏感词库与 AC 自动机过滤能力保留，范围收窄为仅群聊消息生效（群聊本阶段仍是明文）。
- 不做"客户端预检测"作为强制替代（见 Alternatives）——避免给用户"消息仍被过滤"的错误安全
  预期。
- **备选**：客户端在加密前先用同一份敏感词库做一次本地检测/拦截。
- **为什么不选**：客户端代码可被用户自行修改/绕过（浏览器开发者工具、篡改后的前端资源），
  本质上只能防"误发"、防不了"故意绕过"，如果把它包装成"敏感词过滤"能力容易让运营/合规
  方误以为单聊也有强制内容管控。本设计选择诚实地在 proposal/spec 里声明"单聊不再有服务端
  内容过滤"，而不是用一个防君子不防小人的客户端检查掩盖这个事实。若未来产品需要对单聊做
  合规审计，需要走"举报后由当事人自愿提供明文"这类事后取证路径，不在本 change 范围。

### 6. 数据模型改动
`tab_chat_message` 表：
- `content` 列语义变更为「密文信封的序列化结果（JSON 字符串，含 `ciphertext`/`nonce`/
  `ratchetHeader`）」，列类型保持 `TEXT` 即可（Base64 密文体积可控，不需要 `LONGTEXT`）。
- 新增 `sender_identity_key_fingerprint VARCHAR(128)` 列，落库发送时使用的发送方身份公钥
  指纹快照——即使发送方后续更换密钥，历史消息仍能追溯"当时用的是哪把公钥加密"，辅助排查
  安全码变更相关的问题。
- `filtered` 列语义收窄为"仅群聊消息有效"，单聊消息该列固定写 `0`。

新增 `tab_chat_user_key` 表：`id`、`user_id`（唯一索引，关联 `tab_user.id`）、
`identity_public_key VARCHAR(64)`（X25519 公钥 Base64，定长）、`key_fingerprint
VARCHAR(128)`、审计字段（`create_by`/`create_time`/`update_by`/`update_time`）。

Flyway 迁移脚本从 `V7` 开始编号，字段命名沿用下划线分隔、避开保留字的既有约定；不使用
MySQL 8.0+ 语法。

### 7. 前端新增加密依赖：`libsodium-wrappers`
选用 `libsodium-wrappers`（NaCl/libsodium 的 WebAssembly 封装，浏览器兼容性和审计历史都
比手写 WebCrypto 拼接 X25519+XChaCha20-Poly1305 更可靠——原生 `SubtleCrypto` 目前对
X25519/XChaCha20 的浏览器支持仍不一致）提供：`crypto_box_keypair`（身份密钥生成）、
`crypto_scalarmult`（X25519 ECDH）、`crypto_pwhash`（口令派生包裹密钥）、
`crypto_aead_xchacha20poly1305_ietf_*`（消息 AEAD 加解密）、`crypto_generichash`（HKDF 替代/
指纹计算）。
**需在正式编码前与用户单独确认此依赖选型**（`package.json` 新增依赖，虽不像后端
`build.gradle` 那样有强制确认条款，但属于安全敏感依赖，比照同等谨慎程度处理）。
- **备选**：浏览器原生 `SubtleCrypto`（WebCrypto API）。
- **为什么不选（本阶段）**：`SubtleCrypto` 没有原生 X25519/XChaCha20-Poly1305（只有
  P-256/P-384 等 NIST 曲线和 AES-GCM），要拼出与 Signal 同族的加密方案需要引入额外的曲线
  运算库，收益上不如直接用已被广泛审计的 `libsodium-wrappers` 一步到位；如果后续想换成纯
  WebCrypto + P-256 方案，属于独立的技术选型变更，不在本 change 范围。

### 8. 后端不新增依赖
服务端只需要生成/存储/透传不透明的密文与公钥字节串（Base64 字符串走现有 JSON 序列化），
不需要做任何加解密运算，因此本 change 不涉及 `backend/build.gradle` 改动。

## Risks / Trade-offs

- **[密钥目录被篡改的中间人风险]** 服务端是公钥分发的唯一来源，若服务端被攻破可实施 MITM
  → 缓解：安全码/指纹校验 UI + TOFU 变更告警（Decision 4）；不能完全杜绝，需在产品文案里
  如实说明该局限，不得宣传为"绝对不可窃听"。
- **[丢密码/换设备=丢历史消息可读性]** → 刻意权衡（Decision 2），需要前端在密钥生成/首次
  解锁流程中用清晰文案提示用户，并在产品文档/帮助中心说明。
- **[单聊不再有服务端内容过滤/审核能力]** → 合规与内容安全团队需要知晓这一变化（属于
  **BREAKING** 变更，proposal.md 已标注）；如后续有合规需求，需走独立方案（举报后自愿提供
  明文等），不在本 change 内解决。
- **[简化对称棘轮而非完整 Double Ratchet，无 post-compromise security]** → 若某次的棘轮
  状态（`SK` 派生链的中间状态）被窃取，攻击者可推算出该状态之后的所有 message key → 已在
  Decision 3 记录为已知局限和后续演进路径，非本阶段解决目标。
- **[libsodium-wrappers 是 WebAssembly 库，增加前端包体积]** → 需要确认按需加载（不阻塞
  首屏），具体加载策略在 tasks.md 落地时评估。
- **[敏感词库/AC 自动机代码路径需要按会话类型分叉，增加一处条件判断]** → 影响范围可控，
  已在 Decision 5/6 明确边界（单聊固定跳过，群聊不变）。

## Migration Plan

1. 前端新增 `libsodium-wrappers` 依赖（`package.json`）——**编码开始前需与用户确认**。
2. 新增 Flyway 迁移脚本（`V7__*.sql`）：`tab_chat_message` 增加
   `sender_identity_key_fingerprint` 列；新增 `tab_chat_user_key` 表。
3. 后端：新增密钥目录 REST 接口（`controller`/`service`/`entity`/`mapper`/`mapstruct`）；
   单聊敏感词过滤路径按会话类型跳过（群聊不变）；`ChatSingleFrameBody`/`ChatMessageVO` 的
   内容字段改为承载密文信封（服务端仍视为不透明字符串，不解析）。
4. 前端：新增身份密钥生成/口令包裹存储（IndexedDB）、登录后"解锁聊天密钥"流程、单聊消息
   发送前加密/接收后解密、安全码展示与 TOFU 变更告警 UI。
5. 同步更新 `openspec/specs/chat-messaging`、`openspec/specs/chat-security` 的 delta，
   以及新增 `openspec/specs/chat-e2e-encryption`（本次 proposal.md 已列出）。
6. 若新增独立的"安全码校验"页面/按钮，同步登记根目录 `权限资源.txt`。
7. 验证方式：启动真实 `backend`（连接开发数据库）与 `frontend` dev server，用两个已登录
   用户的浏览器会话验证：双方生成密钥并互相注册公钥、发起单聊后消息在服务端落库为密文
   （直接查库确认 `content` 列不含明文）、双方能正确解密显示、篡改服务端返回的对方公钥后
   前端触发"安全码已变更"告警、单聊消息不再被敏感词库拦截（群聊仍被拦截）。

**回滚策略**：新增字段/新表是纯增量（`sender_identity_key_fingerprint` 允许为空、
`tab_chat_user_key` 是全新表），不破坏现有明文消息的兼容性；但一旦切换前端加密逻辑上线，
新发送的单聊消息即不可回退为明文（除非同时回滚前端代码）。若上线后发现严重问题，优先回滚
前端加密相关代码到"单聊也走明文"的上一版本（历史密文消息在回滚后会显示为无法解密，需要
产品侧提示），不建议尝试"部分回滚"（明文加密混用会破坏棘轮状态的连续性）。

## Open Questions

1. **口令派生参数（Argon2id vs PBKDF2、迭代次数/内存参数）**：留待编码阶段按
   `libsodium-wrappers` 实际提供的 `crypto_pwhash` 默认强度参数（`OPSLIMIT_INTERACTIVE`/
   `MEMLIMIT_INTERACTIVE` 或更高档位）评估浏览器端性能后确定，不阻塞设计定稿。
2. **`sender_identity_key_fingerprint` 快照列 vs 单独的历史密钥版本表**：本设计选择更简单
   的快照列方案，如果未来需要支持"用户主动更换身份密钥后重新加密历史消息"等高级能力，
   可能需要升级为单独的版本表，本阶段不做。
3. **libsodium-wrappers 包体积对首屏加载的影响**：需要在 tasks.md 实现阶段实测并决定是否
   做路由级懒加载（进入聊天页面才加载 WASM），不阻塞设计定稿。
