## Why

聊天系统的整体规划（见 `openspec/specs/chat-security` 的 Purpose）分三阶段："单节点网关 →
集群水平扩展 → 端到端加密"，前两阶段已落地，聊天消息目前以明文形式经服务端中转并落库
（`tab_chat_message.content` 为 TEXT 明文列）。现在需要落地第三阶段：对单聊消息做端到端
加密（E2EE），使消息内容在网络中间人、服务端/运维人员、数据库拖库、会话之外的其他用户四类
场景下均不可被解密，服务端此后只承担密文中转与存储职责。

## What Changes

- 新增聊天端到端加密能力：每个用户在浏览器本地生成 X25519 身份密钥对，公钥注册到服务端
  「密钥目录」，私钥仅保存在浏览器本地（IndexedDB），用登录密码在浏览器本地派生的包裹密钥
  加密后存储，不落盘明文、不上传服务端。
- 单聊双方基于身份公钥做 X25519 ECDH 协商共享密钥，使用 HKDF 对称密钥棘轮为每条消息派生
  一次性 message key（提供前向保密），消息正文用 XChaCha20-Poly1305 等 AEAD 算法加密后
  再经现有 Netty 网关帧体 / REST 接口传输。
- 新增「安全码/指纹校验」UI：双方可带外比对身份公钥指纹；采用 Trust-On-First-Use（TOFU），
  对方身份公钥变更时前端明确告警，作为对密钥目录被篡改风险的纵深防御。
- **BREAKING**：`tab_chat_message.content` 语义从「服务端可读的明文（经敏感词过滤/替换）」
  变为「客户端生成的密文信封（含 nonce、棘轮头部等字段）」，仅适用于单聊；服务端不再具备
  读取单聊消息明文的能力。
- **BREAKING**：单聊场景关闭现有服务端敏感词过滤（`chat-security` 的「消息命中敏感词被
  拦截或替换」能力）——服务端拿不到明文，无法继续对单聊内容做该过滤；敏感词库与过滤能力
  保留，范围收窄为仅群聊生效。
- 新增前端加密依赖（如 `libsodium-wrappers`），后端不新增依赖（服务端只透传/存储密文，
  不参与加解密）。
- 登录成功后新增「解锁/生成聊天身份密钥」步骤：首次使用生成密钥对并注册公钥，后续每次登录
  用密码派生包裹密钥解锁本地私钥；不改动现有登录鉴权协议本身。

## Capabilities

### New Capabilities
- `chat-e2e-encryption`：单聊端到端加密能力——身份密钥生成与本地存储、公钥目录注册与查询、
  会话密钥协商与消息级棘轮、密文信封的加解密、安全码/指纹校验与 TOFU 告警。

### Modified Capabilities
- `chat-messaging`：单聊消息的「消息发送与路由」「会话内消息顺序保证」等能力涉及的消息体，
  从明文文本变为不透明密文信封（服务端只按 `msgId`/`conversationSeq` 做路由与排序，不再
  解析消息内容语义）；群聊消息体不受影响，仍为明文。
- `chat-security`：「消息命中敏感词被拦截或替换」等敏感词过滤 Requirement 的适用范围
  收窄为「仅群聊」，单聊不再适用（密钥目录完整性/安全码校验/TOFU 告警等新增安全能力归入
  新能力 `chat-e2e-encryption`，不重复放在本能力里）。

## Impact

- 后端：`cn.nihility.rbac.chat` 模块新增密钥目录相关 entity/mapper/controller/service；
  `ChatMessageEntity`/`tab_chat_message` 表结构调整（新增 Flyway 迁移脚本，当前最新为
  V6，需遵循 MySQL 5.7 兼容约束）；单聊路径的敏感词过滤逻辑增加「仅群聊生效」的分支判断；
  `ChatSingleFrameBody` 等网关帧体与 REST DTO（`ChatMessageVO` 等）的消息内容字段改为
  密文信封结构。
- 前端：`frontend/src/stores/chat.ts`、`utils/chatSocket.ts`、`views/chat/` 下新增密钥
  生成/本地存储/解锁、加解密、安全码校验相关逻辑；新增加密库依赖（`package.json` 改动，
  需在编码前与用户确认具体库选型）；登录流程新增「解锁聊天密钥」步骤。
- 数据库：新增用户公钥目录表（如 `tab_chat_user_key`），`tab_chat_message.content` 列
  语义变更（可能需要新增字段承载 nonce/棘轮头部等密文信封的结构化部分）。
- 文档：`权限资源.txt` 若安全码校验等页面新增交互入口/权限点需同步登记（具体以实现阶段
  是否新增独立菜单/按钮为准）。
