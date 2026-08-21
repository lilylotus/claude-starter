## 1. 后端：SSO 协议调用记录 VO 补充会话ID字段

- [x] 1.1 在 `SsoProtocolLogVO`（`backend/src/main/java/cn/nihility/rbac/ssoprotocollog/dto/SsoProtocolLogVO.java`）新增 `sessionId` 字段，补充字段注释说明其为 SSO 会话令牌的 SHA-256 摘要
- [x] 1.2 核对 `SsoProtocolLogConvert`（`backend/src/main/java/cn/nihility/rbac/ssoprotocollog/mapstruct/SsoProtocolLogConvert.java`）：若为显式逐字段映射，补充 `sessionId` 映射；若依赖同名自动映射，确认生效
- [x] 1.3 补充/更新相关单元测试，验证 `GET /api/sso-protocol-logs` 返回结果包含 `sessionId` 字段且值与查询参数一致
- [x] 1.4 `SsoSessionIdHasher`（`backend/src/main/java/cn/nihility/rbac/sso/session/SsoSessionIdHasher.java`）截取 SHA-256 摘要前 16 字节（32 位十六进制字符），不再返回完整 64 位摘要

## 2. 前端：登录日志列表展示会话ID列

- [x] 2.1 在 `LoginLogManagementView.vue`（`frontend/src/views/system/log/LoginLogManagementView.vue`）表格中新增"会话ID"列，绑定 `row.sessionId`，为空时显示为空
- [x] 2.2 为该列添加列头 tooltip/说明文案，注明"会话标识（摘要值，不可用于登录）"
- [x] 2.3 "操作"列按钮文案由"查看SSO调用记录"改为"协议详情"（原文案在窄列宽下展示不全）

## 3. 前端：SSO 协议调用记录弹窗展示会话ID列

- [x] 3.1 在 `SsoProtocolLogRow` 类型（`frontend/src/types/ssoProtocolLog.ts`）新增 `sessionId` 字段
- [x] 3.2 在 `SsoProtocolLogDialog.vue`（`frontend/src/components/SsoProtocolLogDialog.vue`）表格中新增"会话ID"列，绑定 `row.sessionId`；移除此前"sessionId 不在页面上展示"的相关注释
- [x] 3.3 弹窗（`el-dialog`）宽度加宽，容纳新增的"会话ID"列后各列字段不再被压缩/截断

## 4. 验证

- [x] 4.1 后端执行 `./gradlew test`，确认新增/受影响测试通过
- [x] 4.2 前端执行 `npm run build`（vue-tsc 类型检查 + vite build），确认类型无误
- [ ] 4.3 手工验证：登录日志列表能看到会话ID列；点击"查看SSO调用记录"弹窗表格能看到与登录日志一致的会话ID列
