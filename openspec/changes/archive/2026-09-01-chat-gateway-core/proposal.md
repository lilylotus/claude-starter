## Why

系统目前没有即时通讯能力，用户之间无法在平台内直接沟通协作。仓库根目录《Netty多用户聊天与端到端加密设计.md》给出了一套完整的多用户聊天系统设计（网关+协议+集群+E2EE），工作量很大，需要分阶段落地。本 change 是第一阶段：搭建可独立验收的单节点 Netty 聊天网关（单聊+群聊）与配套前端聊天入口，为后续「集群水平扩展」「端到端加密」两个阶段打好协议与会话管理基础。

## What Changes

- 新增 Netty 长连接网关（WebSocket，单节点，非集群）：TLS 加密、自定义应用层协议（魔数+版本+消息类型+长度域，`LengthFieldBasedFrameDecoder` 解决粘包半包）、`IdleStateHandler` 心跳检测、`userId -> Set<Channel>` 多端登录映射。
- 新增连接建立后的 JWT 认证握手：限定时间内未完成认证强制断开；认证复用系统现有登录态签发的 token。
- 新增会话与消息路由能力：单聊、群聊消息投递；同一会话内消息带会话级序号保证顺序展示。
- 新增消息可靠性机制：客户端生成 `msgId`，服务端基于 `msgId` 短时间去重防重放；ACK 确认机制（服务端回执，客户端超时重发）；离线消息落库，用户上线后按序补偿推送。
- 新增内容安全能力：发送/落库前经敏感词过滤（DFA/AC 自动机），命中后按拦截/替换处理；按用户/IP令牌桶限流防刷屏；配套最小可用的敏感词后台管理界面（增删词条、启用/停用）。
- 新增会话与消息持久化：会话（单聊/群聊）、群成员、消息记录、消息已读回执、离线消息四类表（Flyway 迁移，表名 `tab_chat_*` 前缀）。
- 新增前端「聊天」一级菜单：会话列表（单聊+群聊）、消息收发界面（WebSocket 客户端、断线重连+指数退避、重连后补偿拉取离线消息）、群聊创建/成员管理基础界面。
- 同步更新 `权限资源.txt`，补充聊天模块菜单/按钮资源编码。
- **不包含**（留给后续 change）：跨节点集群转发（Redis 会话路由/消息总线）、端到端加密（X3DH/Double Ratchet/Sender Key）、图片/文件类消息的异步内容审核对接、审计日志检索界面。本阶段消息在服务端可见明文，仅做传输层 TLS 加密 + 落库，不做信封加密。

## Capabilities

### New Capabilities
- `chat-messaging`：Netty 网关连接/会话管理、自定义协议、单聊与群聊消息路由、可靠性（ACK/去重/离线补偿/顺序保证）、前端聊天界面与断线重连体验。
- `chat-security`：连接认证鉴权（JWT握手+超时断连）、心跳保活、敏感词过滤、按用户/IP限流防滥用。

### Modified Capabilities
（无——本次不修改任何已归档 spec 的既有需求；侧边导航的权限驱动可见性机制沿用 `navigation` 现有能力，聊天菜单只是新增一条受权限控制的菜单数据，不改变 `navigation` 的需求定义。）

## Impact

- **新增依赖**：`backend/build.gradle` 实际新增 `io.netty:netty-all`、`com.github.ben-manes.caffeine:caffeine`
  （均已与用户确认，见 design.md Decision 6/Migration Plan）。不需要 Protobuf 序列化库，本阶段消息体用
  JSON（design.md Decision 3）。
  **【实现后核实发现，已闭合】** 实现过程中还新增了 `commons-io`、`commons-collections4` 两个依赖，全仓库
  范围内均无代码引用、未经用户确认，已核实后从 `build.gradle` 移除（详见 design.md Risks、`tasks.md`
  8.4）。`spring-boot-starter-websocket` 核实为实现委托发起前就已存在于工作区的依赖，非本次新增，维持
  不使用、不删除。
- **新增数据库表**：`tab_chat_conversation`（会话）、`tab_chat_conversation_member`（会话成员/群成员）、`tab_chat_message`（消息记录）、`tab_chat_message_offline`（离线消息队列）、`tab_chat_message_read`（已读回执，或并入消息表），均需 Flyway 迁移脚本，字段遵循仓库表结构约定（默认创建人/创建时间/更新人/更新时间、下划线命名、避开关键字）。
- **新增后端进程内组件**：Netty 网关会与 Spring Boot 应用同进程启动（`bootRun` 时一并监听聊天端口），不引入独立部署单元。
- **前端**：新增 `聊天` 一级菜单、路由、`src/api/chat.ts`、`src/stores/chat.ts`、`src/views/chat/` 页面组件，新增 WebSocket 客户端封装。
- **`权限资源.txt`**：新增聊天模块资源编码条目。
- **无破坏性变更**：不修改现有模块的接口或数据结构。
