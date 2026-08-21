## MODIFIED Requirements

### Requirement: SSO 协议调用记录分页查询
系统 SHALL 提供 SSO 协议调用记录的只读分页查询接口，支持按应用、协议类型、事件类型、调用结果、
SSO 会话标识、调用时间范围筛选，均为可选参数；分页参数 `page`（默认 `1`）、`pageSize`（默认 `10`）
均为可选；结果按调用发生时间降序排列。该接口不提供新增、编辑、删除能力。按 SSO 会话标识筛选主要
供"登录日志"页面查看某次成功 SSO 登录之后关联的协议调用记录使用（见 `login-log-management` 能力
"登录日志页面查看关联的 SSO 协议调用记录"需求）。查询结果的每条记录 SHALL 包含该条记录关联的
`sessionId`（SSO 会话令牌的 SHA-256 摘要），供前端在协议调用记录详情表格中展示；`sessionId` 为空的
记录该字段返回空。

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
