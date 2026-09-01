## ADDED Requirements

### Requirement: 发起单聊等待结果的客户端状态复位
前端聊天客户端发起单聊（服务端首次发送时自动创建会话）时，SHALL 通过一个与该次发送
`msgId` 关联的 Promise/等待态向调用方（发起单聊弹窗等 UI）报告最终结果；该等待态在
以下三种服务端终态路径下 SHALL 都被正确 settle，不 SHALL 存在导致等待态永久悬挂
（既不 resolve 也不 reject）的路径：
1. 收到匹配 `msgId` 的 ACK 帧 → 以创建/复用的会话 id resolve。
2. ACK 超时且自动重发次数达到上限 → reject。
3. 收到匹配 `msgId` 的业务 ERROR 帧（服务端校验失败）→ reject。

依赖该等待态驱动的 UI 加载状态（如"发送"按钮的 loading）SHALL 在上述任一终态后
恢复可交互，不 SHALL 因为等待态悬挂而永久停留在加载中且无法重试或取消。

#### Scenario: 服务端返回业务 ERROR 帧后等待态被 reject
- **WHEN** 客户端发起单聊消息（携带 `msgId`）后，服务端因业务校验失败（如目标用户
  不存在、内容非法等）回复携带同一 `msgId` 的 ERROR 帧
- **THEN** 客户端与该 `msgId` 关联的等待态被 reject，驱动的 UI 加载状态恢复正常，
  用户可以重新编辑并再次提交，或取消当前操作

#### Scenario: ACK 超时判定失败后等待态被 reject
- **WHEN** 客户端发起单聊消息后，在自动重发达到最大次数后仍未收到匹配 `msgId` 的
  ACK 帧
- **THEN** 客户端判定该次发送失败，与该 `msgId` 关联的等待态被 reject，驱动的 UI
  加载状态恢复正常

#### Scenario: 正常收到 ACK 后等待态被 resolve
- **WHEN** 客户端发起单聊消息后，收到服务端返回的匹配 `msgId` 的 ACK 帧
- **THEN** 与该 `msgId` 关联的等待态以 ACK 帧携带的会话 id resolve，UI 据此切换到
  新创建/复用的会话
