## 0. 编码前置确认（阻塞项，未获确认前不得开始后续任务）

- [x] 0.1 与用户确认前端新增依赖 `libsodium-wrappers`（design.md Decision 7）；如用户
      要求更换选型，先更新 design.md 再继续。
- [x] 0.2 与用户确认本 change 的 proposal.md/design.md/tasks.md 内容后，再开始编码
      （CLAUDE.md 约定：过程文档创建完成后需手动确认是否继续执行实现工作）。

## 1. 数据库迁移

- [x] 1.1 新增 Flyway 迁移脚本 `V7__add_chat_e2e_encryption.sql`：
      `tab_chat_message` 增加 `sender_identity_key_fingerprint VARCHAR(128)` 列
      （允许为空，兼容历史明文消息记录）。
- [x] 1.2 同一脚本内新增 `tab_chat_user_key` 表（`id`、`user_id` 唯一索引、
      `identity_public_key VARCHAR(64)`、`key_fingerprint VARCHAR(128)`、
      `create_by`/`create_time`/`update_by`/`update_time`），字段命名下划线分隔、
      避开保留字，不使用 MySQL 8.0+ 语法。
- [ ] 1.3 本地/开发环境执行 `./gradlew flywayMigrate`（或随 `bootRun` 自动迁移）验证
      脚本可正常应用，且不影响历史数据。**尚未针对真实数据库验证**——`./gradlew test`
      跑通不等于 Flyway 脚本在真实 MySQL 5.7 环境应用成功，需要启动 backend 连接开发
      数据库后确认。

## 2. 后端：密钥目录能力

- [x] 2.1 新增 `ChatUserKeyEntity`（对应 `tab_chat_user_key`）与对应 MyBatis-Plus
      `Mapper`。
- [x] 2.2 新增 `ChatUserKeyService`/`impl`：注册/更新本人公钥（校验只能写当前登录用户
      的记录）、按 `userId` 查询目标用户公钥。
- [x] 2.3 新增 `ChatKeyController`（或并入现有 `ConversationController` 所在包下新建
      子模块），提供注册公钥、查询目标用户公钥两个 REST 接口，加 `@Tag`/`@Operation`
      openapi 注解，DTO 走 `dto/` 不直接暴露 entity，使用 `jakarta.validation` 校验
      公钥字段格式。**实现说明**：路径最终用 `/api/v1/chat/keys/...`，与本模块既有
      `ConversationController`/`SensitiveWordController` 的 `/api/chat/...`（无 `v1`）
      风格不一致，参照了 `WorkflowTaskController`/`PluginController` 等模块的 `/api/v1/`
      先例。
- [x] 2.4 新增 MapStruct `ChatUserKeyConvert`（静态单例写法，不用 `componentModel =
      "spring"`）负责 entity/DTO 转换。
- [x] 2.5 单元测试：注册公钥的权限校验（不能写别人的记录）、查询不存在公钥时的错误
      响应。（`ChatUserKeyServiceImplTest`，4 个用例）

## 3. 后端：单聊消息通道改造为密文透传

- [x] 3.1 确认 `ChatSingleFrameBody`、`ChatMessageVO` 等涉及单聊消息内容的字段改为
      承载密文信封（JSON 字符串或结构化字段，含 `ciphertext`/`nonce`/`ratchetHeader`/
      `senderIdentityKeyFingerprint`），服务端只做透传，不解析内部结构。
      **实现说明**：`senderIdentityKeyFingerprint` 实际落地为 `content` 之外的同级
      字段（`ChatSingleFrameBody`/`MessagePushFrameBody`/`ChatMessageVO` 均如此），而
      非 design.md 行文描述的"信封内字段"；前端已按此真实契约对齐实现。
- [x] 3.2 落库路径：`ChatMessageEntity.content` 写入密文信封原文；
      `senderIdentityKeyFingerprint` 写入 `sender_identity_key_fingerprint` 列。
- [x] 3.3 单聊消息处理路径跳过服务端敏感词过滤逻辑（`filtered` 固定写 `0`）；确认
      群聊路径不受影响，仍执行既有敏感词过滤。
- [x] 3.4 更新/新增单元测试与集成测试：验证单聊消息落库内容为不透明密文（测试里用
      任意字节串即可，不要求测试环境具备真实加解密能力）、单聊不再触发敏感词拦截、
      群聊敏感词过滤行为不变。

## 4. 前端：加密核心模块

- [x] 4.1 新增 `libsodium-wrappers` 依赖（已获 0.1 确认后执行 `npm install`）。
- [x] 4.2 新增 `src/utils/chatCrypto.ts`：封装身份密钥对生成（`crypto_box_keypair`）、
      口令派生包裹密钥（`crypto_pwhash`）、AES-GCM/`crypto_secretbox` 包裹与解包私钥、
      X25519 ECDH（`crypto_scalarmult`）、HKDF 风格消息密钥派生、
      `crypto_aead_xchacha20poly1305_ietf_encrypt/decrypt`、公钥指纹计算
      （`crypto_generichash`）。
- [x] 4.3 新增 `src/utils/chatKeyStore.ts`：IndexedDB 读写包裹后的私钥材料、盐值、
      KDF 参数、本地缓存的「已信任」对方公钥指纹表（TOFU 用）。
- [ ] 4.4 补充针对 4.2/4.3 纯函数逻辑的单元测试（如 vitest），覆盖加密-解密往返一致、
      错误密码解包失败、指纹计算稳定性。**未做**——项目当前没有 vitest/任何前端测试
      基础设施，新增会引入未经确认的依赖，留待用户决定是否需要单独引入。

## 5. 前端：密钥生命周期与登录集成

- [x] 5.1 登录成功流程中新增「解锁/生成聊天身份密钥」步骤：本地存在密钥材料则用当次
      密码解锁，不存在则生成新密钥对并调用后端注册接口。
- [x] 5.2 新增 `src/api/chatKey.ts` 封装第 2 节新增的两个 REST 接口。
- [x] 5.3 `src/stores/chat.ts` 增加本地解锁状态、当前用户身份密钥（内存中）、会话对方
      公钥/指纹的状态管理。
- [x] 5.4 密码错误或本地密钥材料损坏时的明确用户提示（不静默失败），以及首次生成密钥
      时提示用户「丢失密码将导致历史消息不可读」的文案。

## 6. 前端：单聊消息收发改造

- [x] 6.1 发起/继续单聊前，查询并缓存对方身份公钥；若对方未注册公钥，明确提示无法建立
      加密会话。
- [x] 6.2 发送单聊消息前：本地完成 ECDH + 棘轮派生消息密钥 + AEAD 加密，组装密文信封
      后再调用现有 `chatSocket` 发送逻辑（复用现有 msgId/ACK 机制，不改动该部分协议）。
- [x] 6.3 接收单聊消息后：解析密文信封，派生对应消息密钥解密，解密失败时展示「消息
      无法解密」提示而非乱码或空白。
- [x] 6.4 历史消息（REST 分页拉取）同样在客户端本地解密后展示，服务端返回的仍是密文
      信封。

## 7. 前端：安全码校验与 TOFU 告警 UI

- [x] 7.1 单聊会话内新增「查看安全码」入口，展示对方当前公钥指纹（分组展示，便于带外
      核对）。
- [x] 7.2 对方公钥指纹与本地缓存的信任指纹不一致时，聊天界面明确展示「对方安全码已
      变更」告警，用户确认后才更新本地信任缓存。
- [x] 7.3 首次与某用户建立单聊会话时，按 TOFU 策略直接缓存当前查询到的指纹，不触发
      告警。

## 8. 文档与权限资源同步

- [x] 8.1 如新增独立的「安全码校验」页面/按钮等权限点，同步登记根目录 `权限资源.txt`。
      **实现说明**：安全码入口是聊天窗口内的按钮，复用既有 `Chat:conversation:view`
      权限点，未新增独立权限编码，`权限资源.txt` 无需改动。
- [x] 8.2 编码完成后，按 `openspec-doc-sync` 约定，基于实际实现结果核对并更新本 change
      的 proposal.md/design.md/tasks.md（记录实现中发现的偏差、新增/移除的依赖等）。
      已核对：密文信封字段结构（Decision 1）、REST 路径前缀（Decision 4）、消息密钥派生
      补充 userId（Decision 3）、安全码指纹算法服务端/客户端不一致（Decision 4）、新增
      错误码 `CHAT_KEY_NOT_FOUND`、权限资源未改动（8.1）、测试覆盖现状（4.4/9.2）、
      Flyway 与端到端验证尚未执行（1.3/9.3/9.4）等偏差均已写入 design.md 对应章节。
- [x] 8.3 用 `openspec-sync-specs` 流程把本 change 的 spec delta 应用到
      `openspec/specs/chat-messaging`、`openspec/specs/chat-security`，并新增
      `openspec/specs/chat-e2e-encryption`。（`openspec validate --specs` 全量通过）

## 9. 验证

- [x] 9.1 后端：`./gradlew test` 全量通过，新增测试覆盖第 2/3 节。
- [x] 9.2 前端：`npm run build`（vue-tsc 类型检查 + vite build）通过。**无新增单元测试**
      （项目当前无 vitest/任何前端测试基础设施，见 4.4 说明），本项只覆盖类型检查 +
      构建通过，不代表加密核心模块有自动化测试回归保护。
- [ ] 9.3 端到端手工/脚本验证（真实 backend + frontend dev server，非 mock）：
      两个已登录用户互相注册公钥 → 发起单聊 → 直接查库确认 `tab_chat_message.content`
      为密文（不含可读明文）→ 双方客户端能正确解密展示 → 人为篡改服务端返回的对方
      公钥后前端触发「安全码已变更」告警 → 单聊消息不再被敏感词库拦截（同时验证群聊
      敏感词过滤行为不变）。
- [ ] 9.4 验证结果与任何实现阶段的偏差记录进 design.md 的 Risks/Open Questions 或
      单独的「实现后核实」小节（参照 `chat-gateway-core` 归档 change 的写法）。
