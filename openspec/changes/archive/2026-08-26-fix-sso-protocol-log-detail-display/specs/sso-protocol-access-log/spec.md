## MODIFIED Requirements

### Requirement: SSO 协议调用记录分页查询
系统 SHALL 提供 SSO 协议调用记录的只读分页查询接口，支持按应用、协议类型、事件类型、调用结果、
SSO 会话标识、调用时间范围筛选，均为可选参数；分页参数 `page`（默认 `1`）、`pageSize`（默认 `10`）
均为可选；结果按调用发生时间降序排列。该接口不提供新增、编辑、删除能力。按 SSO 会话标识筛选主要
供"登录日志"页面查看某次成功 SSO 登录之后关联的协议调用记录使用（见 `login-log-management` 能力
"登录日志页面查看关联的 SSO 协议调用记录"需求）。查询结果的每条记录 SHALL 包含该条记录关联的
`sessionId`（SSO 会话令牌的 SHA-256 摘要），供前端在协议调用记录详情表格中展示；`sessionId` 为空的
记录该字段返回空。

查询结果的每条记录 SHALL 额外包含以下两个只读展示字段，供前端在协议调用记录详情表格中直接展示，
不需要前端另行查询用户管理/策略管理接口核对：
- `userName`：按记录的 `userId` 关联查得的用户姓名；`userId` 为空、或 `userId` 关联的用户已不存在
  （如账号被物理删除）时，`userName` 均返回空。
- `deniedPolicyName`：按记录的 `deniedPolicyId` 关联查得的应用访问授权策略名称；`deniedPolicyId`
  为空、或 `deniedPolicyId` 关联的策略已不存在（如策略被删除）时，`deniedPolicyName` 均返回空。

#### Scenario: 查询调用记录分页列表
- **WHEN** 客户端调用 `GET /api/sso-protocol-logs?page={page}&pageSize={pageSize}`
- **THEN** 系统返回 SSO 协议调用记录的分页结果，按调用发生时间降序排列

#### Scenario: 按应用与事件类型筛选
- **WHEN** 客户端调用 `GET /api/sso-protocol-logs?appRefId={appRefId}&eventType={eventType}`
- **THEN** 系统返回指定应用、指定事件类型的调用记录分页结果

#### Scenario: 按 SSO 会话标识查询某次登录之后的全部协议调用
- **WHEN** 客户端调用 `GET /api/sso-protocol-logs?sessionId={sessionId}`，`sessionId` 取自某条登录日志记录
- **THEN** 系统返回该 SSO 会话产生的全部 CAS/OAuth2 协议调用记录（成功+失败），按调用发生时间排列

#### Scenario: 查询结果返回会话标识字段供展示
- **WHEN** 客户端调用 `GET /api/sso-protocol-logs?sessionId={sessionId}` 并获得非空的分页结果
- **THEN** 返回的每条记录都包含其关联的 `sessionId` 字段，值与查询参数一致

#### Scenario: 查询结果返回用户姓名供展示
- **WHEN** 客户端查询到一条 `userId` 非空且该用户仍存在的记录
- **THEN** 返回的该条记录 `userName` 字段为该用户当前的姓名

#### Scenario: 用户已不存在时用户姓名返回空
- **WHEN** 客户端查询到一条 `userId` 非空、但该用户 id 在 `tab_user` 中已查不到对应记录的记录
- **THEN** 返回的该条记录 `userName` 字段为空，`userId` 字段仍保留原始值

#### Scenario: 被应用访问授权策略拒绝的记录返回拒绝策略名称
- **WHEN** 客户端查询到一条失败原因为"当前用户无权访问该应用"、`deniedPolicyId` 非空且该策略仍存在的记录
- **THEN** 返回的该条记录 `deniedPolicyName` 字段为该策略当前的名称

#### Scenario: 未命中具体拒绝策略时拒绝策略名称返回空
- **WHEN** 客户端查询到一条 `deniedPolicyId` 为空的记录（含调用成功、或失败但拒绝原因与具体策略无关的情况）
- **THEN** 返回的该条记录 `deniedPolicyName` 字段为空
