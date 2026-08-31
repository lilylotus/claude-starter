## Context

现状核对（写 design 前已读代码确认，避免凭设计文档假设与仓库实际状态脱节）：

- 后端**已有**登录鉴权体系，不是 CLAUDE.md 里过时描述的"还没有鉴权接口"：`cn.nihility.rbac.auth` 下已有 `AuthController`/`TokenService`/`IdentityAuthFilter`，登录成功签发**不透明 accessKey**（UUID 字符串，非 JWT），业务请求走 `identity-token` 请求头 + `TokenService.verifyAccessKey(accessKey) -> Optional<Long> userId` 校验。因此聊天网关的连接认证握手 **复用 accessKey 校验**，不引入 JWT，与设计文档"JWT token"的表述做本地化调整。
- `backend/build.gradle` **已有** `spring-boot-starter-data-redis`（供 `TokenService` 等使用），本阶段不用于聊天状态存储（见 Decision 6）。
  **【实现后核实更正，已闭合】** 写 design 时称 `spring-boot-starter-websocket` 是"仓库已有依赖"，依据是
  实现开始前对工作区（非 HEAD）的实际读取——核实 `git show HEAD:backend/build.gradle`（最近一次提交）确实
  不含该行，但时间线核对确认它是在本次实现委托发起**之前**就已存在于工作区未提交状态中，不是本次任何
  实现步骤新增的；保持"不使用、不删除"结论不变（Open Question 3）。`commons-io`、`commons-collections4`
  两个依赖则确认是实现过程中新增、全仓库零引用、未经确认（违反 CLAUDE.md 后端约定），已从 `build.gradle`
  移除并验证 `compileJava`/`compileTestJava` 通过，不影响功能，详见 `tasks.md` 8.4。
- 项目 MySQL 目标版本 5.7，手写 SQL 禁止窗口函数/CTE 等 8.0+ 特性（`CLAUDE.md` 后端约定），本设计中会话级消息序号生成方案需遵守。
- 本 change 是聊天系统四阶段规划的第一阶段（见 `proposal.md`），只做单节点网关 + 单聊/群聊 + 基础安全，不做集群转发、不做端到端加密。

## Goals / Non-Goals

**Goals:**
- 单节点 Netty 网关：TLS、自定义协议帧、心跳、认证握手、断线清理。
- 单聊 + 群聊消息路由，会话内消息严格有序展示。
- 可靠性：ACK、msgId 去重防重放、离线消息补偿推送。
- 基础内容安全：敏感词过滤、按用户限流。
- 前端聊天入口：会话列表、消息收发、群聊创建/成员管理、断线重连体验。
- 协议与数据模型为后续 chat-cluster（多节点路由）、chat-e2ee（端到端加密）留出扩展点，不引入需要推倒重来的设计。

**Non-Goals（本阶段明确不做）：**
- 跨节点集群转发（Redis 会话路由 / 消息总线）——留给 `chat-cluster`。
- 端到端加密（X3DH/Double Ratchet/Sender Key）——留给 `chat-e2ee`；本阶段消息在服务端可见明文，只做传输层 TLS + 落库明文（不做信封加密）。
- 图片/文件消息的异步内容审核对接。
- 生产级证书签发流程（只做配置化接入点，不涉及证书申请/轮换的运维流程）。

## Decisions

### 1. Netty 网关与 Spring Boot 同进程部署，独立端口
Netty `ServerBootstrap` 包装为一个实现 `SmartLifecycle` 的 Spring bean（`ChatGatewayServer`），随 `bootRun`/生产 JAR 一起启动/优雅停止，监听独立端口（`chat.gateway.port`，默认 `48091`，与业务 HTTP 端口 48080 分离）。
- **备选**：独立部署为单独的聊天网关进程/服务。
- **为什么不选**：单节点阶段没有独立扩缩容诉求，拆进程只会增加本阶段部署复杂度；且网关内部业务处理需要复用 `TokenService`/`MessageService`/敏感词过滤等既有 Spring bean，同进程直接注入最简单。拆分留给 `chat-cluster` 阶段视水平扩展需要再评估。

### 2. 浏览器可达：Netty 内跑 WebSocket 协议，业务帧作为 WebSocket 载荷
前端是浏览器 SPA，无法直接开原始 TCP socket，网关必须在 Netty pipeline 里完成 HTTP Upgrade 到 WebSocket（`HttpServerCodec` + `WebSocketServerProtocolHandler`），业务消息封装进 `BinaryWebSocketFrame`。**帧内payload 仍然使用设计文档定义的头部结构**（魔数4B+版本1B+消息类型1B+长度4B+消息体）而不是直接裸发 JSON：
- 一是保持与设计文档"自定义应用层协议"的路由/版本演进能力一致（消息类型字节路由到不同 Handler，版本字段为未来协议升级留口子）；
- 二是给未来非浏览器客户端（如做原生 TCP 长连接的桌面/IoT 客户端）复用同一套内层协议解析代码留空间——那种场景才真正需要 `LengthFieldBasedFrameDecoder` 解粘包半包；WebSocket 传输层本身已经天然分帧，浏览器场景下不存在半包问题，但内层协议头保留不去掉，两种传输方式共享同一套 `ChatFrameCodec`（编解码业务帧）与业务 Handler。
- **备选**：直接用文本 WebSocket 帧传 JSON，不设计二进制协议头。
- **为什么不选**：更贴合当前"浏览器优先"的需求，但放弃了设计文档的协议可扩展性，且后续切 Protobuf/新增消息类型时要再动传输层；保留协议头成本很低（几个字段），一次做好。

### 3. 消息体编码：本阶段用 JSON，不引入 Protobuf
- **备选**：Protobuf（设计文档推荐，体积小、强 schema）。
- **为什么先不选**：引入 Protobuf 需要新增 gradle protobuf 插件 + `.proto` schema 维护流程，项目目前所有接口都是 JSON（`Jackson`/`JacksonUtils` 已是通用工具），聊天消息体这个阶段量级下 JSON 的体积/解析开销不是瓶颈。协议头里的"消息体"字段边界已经和编码方式解耦（长度域只关心字节数），未来要切 Protobuf 只需替换 body 编解码器，不影响帧结构，本阶段先用 JSON 降低复杂度和依赖面。作为 Open Question 记录，若后续压测发现序列化开销是瓶颈再切换。

### 4. 认证握手复用 accessKey，不引入 JWT
连接建立后为"匿名"状态，客户端必须在 `chat.gateway.auth-timeout-seconds`（默认 10s）内发送 `LOGIN` 类型帧，body 携带已登录会话的 `accessKey`；网关调用既有 `TokenService.verifyAccessKey(accessKey)` 校验并解析 `userId`，成功后把 `userId` 写入 `Channel.attr(USER_ID_KEY)` 并登记进 `ChatSessionRegistry`；超时未认证或校验失败直接关闭连接（复用现有登录态，避免维护第二套令牌体系）。

### 5. 会话/多端登录映射：进程内 `ChatSessionRegistry`
`ConcurrentHashMap<Long userId, Set<Channel>>`（`Set` 用 `ConcurrentHashMap.newKeySet()`）。`channelInactive`/`exceptionCaught` 统一在网关的连接生命周期 Handler 里清理映射，避免内存泄漏。单聊/群聊投递时按 `userId` 查出全部在线 Channel 广播（多端同步收发）。本阶段单节点，注册表只需进程内内存结构，不落 Redis（跨节点路由是 `chat-cluster` 阶段的职责，届时把这张表的"发现"部分搬到 Redis，本地 Channel 映射仍保留在各节点内存）。

### 6. 去重、限流本阶段选择"进程内实现"而非 Redis
- **msgId 去重**：`tab_chat_message.msg_id` 建唯一索引作为**权威去重依据**（跨重启也生效）；同时用 Caffeine 本地缓存（TTL 5 分钟）做短路优化，命中缓存直接返回已有 ACK，不打库；缓存未命中再落库，遇到唯一约束冲突按"已存在"处理并照常回 ACK（保证幂等，不因为进程重启丢失缓存而对客户端表现不一致）。
- **限流**：按 `userId`（认证后）和按来源 IP（连接建立时，防止未认证阶段的连接风暴）各维护一个进程内令牌桶（手写实现，不新增 Guava 依赖）。
- **为什么不用 Redis**：`spring-boot-starter-data-redis` 虽已是依赖，但单节点场景下引入网络往返只会增加延迟和运维面，收益是"多节点共享限流阈值/去重窗口"——这正是 `chat-cluster` 阶段要解决的问题，届时把这两个组件换成 Redis 实现（`RateLimiter` Lua 脚本 / Redis `SETNX`）。本阶段保持简单。

### 7. 会话内消息顺序：`tab_chat_conversation` 维护自增 `next_seq`，事务内 `SELECT ... FOR UPDATE` 取号
不使用 `ROW_NUMBER() OVER (...)` 等 MySQL 8.0+ 语法（仓库约定禁止，目标库是 5.7）。落库时：开启事务 → `SELECT next_seq FROM tab_chat_conversation WHERE id = ? FOR UPDATE` 拿到当前值 → `UPDATE tab_chat_conversation SET next_seq = next_seq + 1 WHERE id = ?` → 用取到的值作为该条消息的 `conversation_seq` 写入 `tab_chat_message` → 提交。该会话粒度的行锁保证同一会话内严格递增且不重复，跨会话不互相阻塞。

### 8. 表结构与包结构
新增 Flyway 表（均带 `create_by/create_time/update_by/update_time`，字段下划线命名，避开保留字——用 `conversation` 而不是 `group` 表达群聊，避免 `group` 关键字）：
- `tab_chat_conversation`：`id`、`conversation_type`（1单聊/2群聊）、`name`（群聊名称，单聊为空）、`next_seq`、`status`、审计字段。
- `tab_chat_conversation_member`：`id`、`conversation_id`、`user_id`、`role`（群主/普通成员，单聊两条固定记录）、`joined_time`、`status`。
- `tab_chat_message`：`id`、`msg_id`（客户端生成，唯一索引）、`conversation_id`、`conversation_seq`、`sender_id`、`msg_type`（文本/图片占位等）、`content`（敏感词过滤后落库的内容，本阶段明文）、`filtered`（是否命中过敏感词）、`send_time`、审计字段。
- `tab_chat_message_offline`：`id`、`message_id`（关联 `tab_chat_message.id`）、`receiver_id`、`delivered`（是否已补偿推送）、审计字段；补偿推送成功后标记/清理。
- `tab_chat_sensitive_word`：`id`、`word`、`status`、审计字段，启动时加载进内存构建 AC 自动机；提供后端增删改查接口 + 前端最小管理表格（新增/删除/启用/停用词条），变更后刷新内存中的 AC 自动机。

后端包：`cn.nihility.rbac.chat`，其下 `gateway/`（Netty 启动类、`ChannelInitializer`、编解码器、各消息类型 Handler、`ChatSessionRegistry`）与常规分层 `controller/`（历史消息分页查询、会话列表等 REST 接口）、`service`+`impl`、`dto`、`entity`、`mapper`、`mapstruct`、`exception`，遵循后端既有分层约定。

### 9. 前端：浏览器 `WebSocket` 原生 API 封装 + 断线重连
`src/api/chat.ts` 只放 REST 部分（历史消息、会话列表）；新增 `src/utils/chatSocket.ts` 封装原生 `WebSocket`（自定义二进制协议编解码，`ArrayBuffer`/`DataView` 拼装帧头），指数退避重连（如 1s/2s/4s/8s，封顶后固定间隔），重连成功后重新走认证帧，认证成功后由后端做离线消息补偿推送，前端顺序渲染并逐条/批量确认。会话状态放 `src/stores/chat.ts`（Pinia）。

### 10. TLS `SslContext` Bean 按需注册（`@ConditionalOnProperty`），而非返回可空 Bean
实现阶段发现：若 `chatSslContext()` 工厂方法在 TLS 关闭时直接 `return null`，会导致所有以构造器方式按类型
注入 `SslContext` 的 Bean（`ChatChannelInitializer`）抛出 `NoSuchBeanDefinitionException`——Spring 的
`NullBean` 不参与按类型解析，进而使整个应用上下文启动失败（本地手工验证阶段实测发现，非理论假设）。
改为 `@Bean` 方法整体加 `@ConditionalOnProperty(prefix = "chat.gateway.tls", name = "enabled", havingValue =
"true", matchIfMissing = true)`，TLS 关闭时该 Bean 根本不注册；消费方（`ChatChannelInitializer`）改用
`ObjectProvider<SslContext>` 注入，`getIfAvailable()` 安全返回 `null` 后跳过 `SslHandler`。见
`cn.nihility.rbac.chat.gateway.config.ChatTlsConfig`。

### 11. 测试环境通过 `chat.gateway.enabled=false` 关闭端口监听，规避多 Spring 上下文端口冲突
`ChatGatewayServer` 是 `SmartLifecycle` Bean，随 Spring 容器启动即绑定真实 Netty 端口，与内嵌 Servlet 容器
的 `webEnvironment` 类型无关。仓库既有测试套件里大量 `@SpringBootTest` 用例按不同 Mock/属性组合持有各自
独立的 Spring 上下文缓存条目，每个新建上下文都会尝试绑定同一个 `chat.gateway.port`，导致除第一个之外的
全部上下文启动失败，波及全仓库测试（不只是 chat 模块自身）。修复方式：`build.gradle` 的 `test` task 上
加 `systemProperty 'chat.gateway.enabled', 'false'`，测试运行时整体关闭网关端口监听（复用该开关"问题回滚
不需要回退代码"的既有语义），不影响 chat 模块自身的纯 Mockito 单元测试（`ChatFrameCodecTest`、
`ChatTokenBucketTest`、`ChatMessageServiceImplTest`、`AhoCorasickAutomatonTest`，均不依赖 Spring 容器）。

## Risks / Trade-offs

- **[单点故障]** 单节点网关重启期间聊天不可用 → 本阶段可接受（无 SLA 承诺），`chat-cluster` 阶段解决水平扩展与故障转移。
- **[无 E2EE，服务端可见明文]** → 需要在产品侧提前告知用户本阶段聊天非端到端加密（消息经 TLS 传输、落库明文），`chat-e2ee` 阶段补齐；本阶段不得包装成"已加密"对外宣传。
- **[JSON 而非 Protobuf]** → 大群聊/高并发下的序列化开销和带宽高于 Protobuf 方案 → 若后续压测证明是瓶颈，替换 body 编解码器（协议头结构已预留）。
- **[进程内去重缓存在重启后失效]** → 由 `tab_chat_message.msg_id` 唯一索引兜底，保证正确性不受影响，只是重启窗口期内去重命中率短暂下降为"直接走数据库唯一约束"。
- **[进程内限流在多节点下会被绕过（同一用户连不同节点各算各的桶）]** → 本阶段单节点不存在该问题；`chat-cluster` 阶段必须切换为 Redis 实现，否则限流形同虚设。
- **[敏感词管理界面本阶段只做最小可用版本]** → 只支持增删词条与启用/停用，不含分类、导入导出等高级能力 → 若后续证明需要更完整的运营能力，在独立小改动里扩展（不阻塞本阶段验收）。
- **【新增，实现后核实发现，已闭合】[未经确认的 build.gradle 依赖]** → 实现过程中 `commons-io`、
  `commons-collections4` 两个依赖被新增进 `backend/build.gradle`，全仓库范围内均无任何代码引用，且未按
  CLAUDE.md 后端约定"修改 build.gradle 新增依赖前先跟用户确认"走确认流程 → 已核实零引用后从
  `build.gradle` 移除，`compileJava`/`compileTestJava` 验证通过（详见 `tasks.md` 8.4）。
  `spring-boot-starter-websocket` 核实确认是实现委托发起前就已存在于工作区的依赖，非本次新增，维持
  "不使用、不删除"的既有结论（Open Question 3）。

## Migration Plan

1. `build.gradle` 新增 `io.netty:netty-all`、`com.github.ben-manes.caffeine:caffeine`（均已与用户确认，
   Decision 6）。实现过程中还新增了 `commons-io`、`commons-collections4` 两个未使用、未经确认的依赖，
   已在文档同步后核实移除（详见 Risks 与 `tasks.md` 8.4）。`spring-boot-starter-websocket` 确认非本次
   新增，不在此次变更范围内。
2. Flyway 迁移脚本新增 5 张聊天表（`V*__create_chat_tables.sql`）。
3. 后端实现 `cn.nihility.rbac.chat` 模块（网关 + REST + 持久层），网关端口/TLS/心跳超时/认证超时/限流阈值等参数放 `application.yml`，支持 `chat.gateway.enabled` 开关（默认 true，可配置 false 整体禁用网关监听，用于问题回滚而不需要回退代码）。
4. 前端新增聊天菜单、路由、页面、`chatSocket` 封装。
5. 同步更新根目录 `权限资源.txt`。
6. **验证方式（实现后核实更正）**：浏览器自动化工具（claude-in-chrome）连接的是用户本机 Chrome，无法访问
   实现所在沙箱环境的 `localhost`，未能做"两个浏览器会话手工点击"验证。改为启动真实的 `backend`
   （连接项目配置的开发数据库）与 `frontend` dev server，用脚本对真实运行的 Netty 网关 + REST 接口做端到
   端验证（非 mock/单元测试）：单聊在线投递+ACK、相同 `msgId` 幂等重发、离线消息补偿、群聊创建+投递、
   敏感词命中替换、限流触发、历史消息分页查询，共 11 项检查通过，详见 `tasks.md` 第 7/8 节。前端 UI
   层面的真实浏览器渲染/交互未经验证，仅通过 `npm run build` 确认无类型错误，需用户后续手动确认。

**回滚策略**：改动是纯新增（新模块、新表、新菜单），不修改任何既有接口/表结构；出问题时优先用 `chat.gateway.enabled=false` 关停网关监听 + 隐藏聊天菜单权限点（不下发对应权限编码），无需回退已应用的 Flyway 迁移（生产环境 Flyway 迁移不做逆向回滚，这是仓库既有约定的延伸）。

## Open Questions（已确认，记录结论）

1. **消息体编码**：确认本阶段用 JSON，协议头结构预留切换空间，暂不做 Protobuf。
2. **敏感词管理入口**：确认本阶段需要最小可用的后台管理界面（简单增删/启用停用表格），已并入 tasks.md 第 3/5/6 组。
3. **`spring-boot-starter-websocket` 依赖**：写 design 时的前提（"仓库已有该依赖"）核实后更正为——
   `git show HEAD:backend/build.gradle`（最近一次提交）确实不含该依赖，但它在本次实现委托发起**之前**
   就已存在于工作区未提交状态中，不是本次任何实现步骤新增的。本 change 的 WebSocket 升级完全由原生
   Netty（`HttpServerCodec` + `WebSocketServerProtocolHandler`，Decision 2）实现，不依赖该 starter，维持
   "保留不动、不使用"的结论。**另发现**实现过程中新增了 `commons-io`、`commons-collections4` 两个未使用、
   未经确认的依赖，已核实零引用后从 `build.gradle` 移除（`tasks.md` 8.4）。
4. **Netty 依赖引入方式**：确认新增 `io.netty:netty-all`（而非按需拆分子模块），一次性引入完整依赖简化实现阶段的模块管理。
5. TLS 证书来源：本地开发用自签名证书（配置化路径），生产证书如何签发/轮换由谁负责——本设计只做配置接入点，不涉及具体证书运维流程（仍未确认，非阻塞项，实现时按开发环境默认自签名处理）。
