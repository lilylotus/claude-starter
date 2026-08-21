## Why

历史 change `2026-08-21-add-sso-protocol-access-log` 有意不在登录日志表格和 SSO 协议调用记录表格里展示会话ID（`session_id`），理由是"管理员不需要看到这个哈希值本身，前端只用它作为查询参数"。实际使用中，管理员需要在登录日志和协议调用记录之间手动核对/复制会话标识（例如跨系统排查、截图留档），仅靠隐藏的查询参数无法满足这类场景。会话ID落库/展示的都是 SSO 会话令牌的 SHA-256 摘要（不可逆），不是可直接冒充登录的原始凭据，展示它不引入新的安全风险，因此本次反转该设计决策，把会话ID字段显式展示出来。

## What Changes

- 登录日志管理页面（`LoginLogManagementView.vue`）表格新增"会话ID"列，展示 `LoginLogRow.sessionId`（该字段后端已返回，前端类型已定义，仅需新增表格列）。
- SSO 协议调用记录弹窗（`SsoProtocolLogDialog.vue`）表格新增"会话ID"列：
  - 后端 `SsoProtocolLogVO` 新增 `sessionId` 字段并在查询结果中返回。
  - 前端 `SsoProtocolLogRow` 类型新增 `sessionId` 字段。
  - 弹窗表格新增对应列。
- 不改变现有交互结构：继续保留登录日志行内"查看SSO调用记录"按钮 + `el-dialog` 弹窗的下钻方式，不改为行内展开（expand row）。
- 不新增/修改查询接口参数：`GET /api/login-logs` 和 `GET /api/sso-protocol-logs` 的查询参数保持不变，仅扩展两者的响应字段（协议调用记录侧）和前端展示。
- 会话标识展示长度由 64 位十六进制字符缩短为 32 位：`SsoSessionIdHasher` 只截取 SHA-256 摘要的前 128 位，缩短后的值在表格列中更易阅读/核对，不影响关联查询的唯一性。变更上线前已落库的 64 位摘要记录保持原样，不做回填。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `login-log-management`: 登录日志列表的展示字段新增"会话ID"列（此前设计明确不展示，本次反转）。
- `sso-protocol-access-log`: SSO 协议调用记录查询结果新增返回 `sessionId` 字段，并在前端详情表格中展示。

## Impact

- 后端：`SsoProtocolLogVO`（`backend/src/main/java/cn/nihility/rbac/ssoprotocollog/dto/SsoProtocolLogVO.java`）新增字段；对应 MapStruct 转换 `SsoProtocolLogConvert` 若为显式字段映射需同步核对（字段同名同类型，预期无需改动映射逻辑）。`SsoSessionIdHasher`（`backend/src/main/java/cn/nihility/rbac/sso/session/SsoSessionIdHasher.java`）截取摘要长度由 64 位改为 32 位。不涉及数据库结构变更（`session_id` 列已存在，`VARCHAR(64)` 兼容更短的值），不涉及 Controller 接口签名变更。
- 前端：
  - `frontend/src/views/system/log/LoginLogManagementView.vue` 表格列定义新增一列。
  - `frontend/src/types/ssoProtocolLog.ts` 的 `SsoProtocolLogRow` 类型新增 `sessionId` 字段。
  - `frontend/src/components/SsoProtocolLogDialog.vue` 表格列定义新增一列，并移除"sessionId 不在页面上展示"的相关注释。
- 权限资源编码文件（`权限资源.txt`）：本次不新增/删除菜单或按钮，仅新增表格列，无需更新。
- OpenSpec：需要为 `login-log-management` 与 `sso-protocol-access-log` 两个既有 capability 编写 delta spec，更新对应的展示字段要求。
