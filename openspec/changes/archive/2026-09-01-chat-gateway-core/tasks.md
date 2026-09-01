## 1. 前置确认与依赖引入

- [x] 1.1 已与用户确认 `build.gradle` 新增 `io.netty:netty-all`（design.md Migration Plan 1）
- [x] 1.2 已与用户确认 design.md 中 Open Questions（消息体用 JSON、敏感词管理需要最小后台界面、保留 `spring-boot-starter-websocket` 不动，TLS 证书来源非阻塞项按开发默认处理）
- [x] 1.3 在 `build.gradle` 新增 `io.netty:netty-all` 依赖

## 2. 数据库迁移

- [x] 2.1 编写 Flyway 迁移脚本创建 `tab_chat_conversation`（含 `next_seq` 字段）
- [x] 2.2 编写 Flyway 迁移脚本创建 `tab_chat_conversation_member`
- [x] 2.3 编写 Flyway 迁移脚本创建 `tab_chat_message`（`msg_id` 唯一索引、`conversation_seq`）
- [x] 2.4 编写 Flyway 迁移脚本创建 `tab_chat_message_offline`
- [x] 2.5 编写 Flyway 迁移脚本创建 `tab_chat_sensitive_word` 并写入种子敏感词数据
- [x] 2.6 核对所有手写 SQL 不使用 MySQL 8.0+ 专属语法（窗口函数/CTE 等），与 5.7 兼容

## 3. 后端：持久层与基础服务

- [x] 3.1 创建 `cn.nihility.rbac.chat` 包结构（entity/mapper/dto/service/impl/mapstruct/exception/gateway）
- [x] 3.2 实现会话（`Conversation`）、群成员（`ConversationMember`）、消息（`ChatMessage`）、离线消息（`ChatMessageOffline`）、敏感词（`SensitiveWord`）对应 entity + MyBatis-Plus `BaseMapper`
- [x] 3.3 实现会话内消息序号获取逻辑（事务内 `SELECT ... FOR UPDATE` 取 `next_seq` 并自增，design.md Decision 7）
- [x] 3.4 实现敏感词过滤服务：启动时加载词库构建 AC 自动机，提供文本过滤/命中判定方法；词库增删改后触发内存自动机重建
- [x] 3.5 实现 msgId 幂等落库逻辑（唯一索引冲突按已存在处理，返回既有记录）

## 4. 后端：Netty 网关

- [x] 4.1 定义自定义协议帧结构与编解码器 `ChatFrameCodec`（魔数+版本+消息类型+长度域+JSON body，design.md Decision 2/3）
- [x] 4.2 实现 `ChatGatewayServer`（`SmartLifecycle`），管理 `bossGroup`/`workerGroup`，监听独立端口，随 Spring 生命周期启停
- [x] 4.3 配置 Netty pipeline：`HttpServerCodec` + `WebSocketServerProtocolHandler` 完成 WebSocket 升级，`SslContext` 按配置开启/关闭 TLS
- [x] 4.4 配置 `IdleStateHandler` 心跳空闲检测，超时关闭连接
- [x] 4.5 实现 `ChatSessionRegistry`（`userId -> Set<Channel>` 进程内映射），在连接生命周期回调中维护
- [x] 4.6 实现认证握手 Handler：限时等待认证帧、调用 `TokenService.verifyAccessKey` 校验、绑定 `userId`、超时/失败断连
- [x] 4.7 实现业务处理线程池（`DefaultEventExecutorGroup`）隔离 IO 线程与业务逻辑（DB/过滤/限流）
- [x] 4.8 实现单聊消息处理 Handler：落库（含敏感词过滤）→ 判断在线状态 → 直投/写离线队列 → 回 ACK
- [x] 4.9 实现群聊消息处理 Handler：成员校验 → 落库一次 → 按成员在线状态分别投递/写离线队列 → 回 ACK
- [x] 4.10 实现 msgId 去重短路缓存（Caffeine，TTL 5 分钟）叠加数据库唯一约束兜底
- [x] 4.11 实现按用户/按 IP 的进程内令牌桶限流（design.md Decision 6），超限返回错误帧
- [x] 4.12 实现离线消息补偿推送：认证成功后按会话序号顺序推送未送达消息并标记已送达
- [x] 4.13 实现连接异常/关闭时的会话映射清理（`channelInactive`/`exceptionCaught`）

## 5. 后端：REST 接口

- [x] 5.1 会话列表查询接口（当前用户参与的单聊+群聊会话列表）
- [x] 5.2 历史消息分页查询接口（按会话 + `conversation_seq` 游标分页）
- [x] 5.3 创建群聊接口（含初始成员）
- [x] 5.4 群成员管理接口（添加成员、退出/移出群聊）
- [x] 5.5 敏感词管理接口：分页查询、新增、删除、启用/停用词条
- [x] 5.6 补充 springdoc-openapi 注解（`@Tag`/`@Operation`），核对 Swagger UI 展示正常
- [x] 5.7 `application.yml` 新增聊天网关配置项（端口、TLS、认证超时、心跳阈值、限流阈值、`chat.gateway.enabled` 开关）

## 6. 前端

- [x] 6.1 新增「聊天」一级菜单项与路由（`router/index.ts`、`router/menu.ts`）
- [x] 6.2 实现 `src/utils/chatSocket.ts`：原生 WebSocket 封装、自定义协议帧编解码（`ArrayBuffer`/`DataView`）、指数退避重连、重连后重新认证
- [x] 6.3 实现 `src/stores/chat.ts`（会话列表、当前会话消息、在线状态、未读数等状态）
- [x] 6.4 实现 `src/api/chat.ts`（会话列表、历史消息、创建群聊、成员管理等 REST 封装）
- [x] 6.5 实现会话列表界面（单聊+群聊混排，含最近消息摘要）
- [x] 6.6 实现消息收发界面（历史消息滚动加载、发送、ACK 状态展示、离线补偿消息顺序展示）
- [x] 6.7 实现群聊创建/成员管理基础界面
- [x] 6.8 断线重连时的 UI 状态提示（连接中/已断开/重连成功）
- [x] 6.9 实现敏感词后台管理页面（列表、新增、删除、启用/停用）

## 7. 安全与合规收尾

- [x] 7.1 核对认证超时、心跳超时、限流阈值等安全相关配置默认值合理且可配置
- [x] 7.2 验证敏感词命中拦截/替换行为符合预期（脚本化真实验证，见下方说明：命中词 `赌博` 被替换为 `**`，替换后内容正常投递）
- [x] 7.3 验证限流触发行为（脚本化真实验证：同一用户群聊连续发送 60 条消息，第 8 条起持续收到 `ERROR(1003)` 限流错误帧）

## 8. 文档与收尾

- [x] 8.1 更新根目录 `权限资源.txt`，新增聊天模块菜单/按钮资源编码
- [x] 8.2 端到端联调验证：单聊收发、群聊收发、离线消息补偿（方式说明见下）
- [x] 8.3 实现完成后用 `openspec-doc-sync` 依据实际改动核对/更新本 change 的 proposal/design/tasks
- [x] 8.4 已核实并处理 `build.gradle` 依赖问题：
      - `commons-io`/`commons-collections4`：确认全仓库零引用、无实际用途，已从 `build.gradle` 移除，
        移除后 `compileJava`/`compileTestJava` 均通过，不影响任何功能。
      - `spring-boot-starter-websocket`：核实 `git show HEAD:backend/build.gradle` 确实不含该行，但通过
        `git log`/时间线核对，它是在本次会话发起实现委托**之前**就已存在于工作区未提交状态中的（不是
        本次任何实现步骤新增的），保持"不使用、不删除"的既有结论不变（design.md Open Question 3 的
        用户决定），只是把它"仓库已有依赖"这个表述的依据从"已提交" 更正为"实现开始前工作区就已存在"。

### 7.2/7.3/8.2 验证方式说明

浏览器自动化工具（claude-in-chrome）连接的是用户本机的 Chrome 会话，无法访问本次实现所在沙箱环境的
`localhost`，因此无法做真正的"双浏览器手工点击"验证。改为启动真实的 `backend`（`bootRun`，连接项目配置的
开发数据库 `10.10.88.31`）与 `frontend`（`vite dev`）后，用脚本对**真实运行的 Netty 网关 + REST 接口**
做端到端验证（不是单元测试/mock）：

1. 完成 `admin` 账号首登强制改密（经用户确认后执行，新密码见下）。
2. 通过 `POST /api/users` 创建了一个真实的第二测试账号（`chattest_*`），同样走首登改密流程。
3. 用两个独立的 WebSocket 连接（分别以 admin、测试账号的 accessKey 完成协议层认证握手）验证：
   单聊在线投递+ACK、相同 `msgId` 幂等重发（`conversationSeq` 不重复）、离线消息补偿（对方断线期间发送，
   重连后自动收到 `offline:true` 的补偿推送）、群聊创建（REST）+ 群聊消息投递、敏感词替换、限流拦截、
   历史消息分页查询，全部 11 项检查通过。
4. 遗留在开发数据库中的测试数据：`admin` 密码已改为 `AdminChat#2026`（原 `admin/admin` 首登状态已清除），
   新增了 1-2 个 `chattest_*` 测试用户及对应的测试会话/消息记录，未做清理，需要用户确认是否要清理。

前端 UI 层面（页面渲染、交互点击）未经真实浏览器验证，仅通过 `npm run build`（vue-tsc 类型检查 + vite
build）确认无类型错误，前端实现是否在浏览器里可用、样式是否符合预期仍需要用户后续手动确认。
