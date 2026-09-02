## 1. 数据库迁移

- [x] 1.1 新增 `backend/src/main/resources/db/migration/V3__add_sso_login_methods.sql`：
      `tab_app_auth_config` 新增列 `login_methods VARCHAR(500) NOT NULL DEFAULT '["PASSWORD"]'`。
- [x] 1.2 同一脚本内 `tab_login_log` 新增列 `login_method VARCHAR(20) NOT NULL DEFAULT 'PASSWORD'`。
- [x] 1.3 本地起库验证迁移脚本可正常执行（`./gradlew bootRun` 或专门跑一次 Flyway），确认存量
      数据自动满足默认值语义。

## 2. 后端公共能力

- [x] 2.1 `RedisUtils` 新增基于 `StringRedisTemplate.opsForValue().increment` 的原子自增
      helper（`increment(key, timeout, unit)`：首次自增后设置过期时间），供短信发送每日计数
      复用。
- [x] 2.2 新增 `RbacSmsProperties`（前缀 `rbac.sms`）：验证码有效期、发送冷却秒数、每日发送
      上限、连续校验失败上限，均给出 design.md 里的默认值。
- [x] 2.3 新增 `RbacQrcodeProperties`（前缀 `rbac.qrcode`）：二维码会话有效期。

## 3. 应用认证配置扩展（`app/authconfig`）

- [x] 3.1 `AppAuthConfigEntity` 新增 `loginMethods` 字段（`login_methods` 列，JSON 字符串
      数组文本，读写模式对齐 `servicePatterns`）。
- [x] 3.2 `AppAuthConfigVO`/`AppAuthConfigUpdateRequest` 新增 `loginMethods`
      （`List<String>`）字段，`@Schema` 注解补充说明。
- [x] 3.3 `AppAuthConfigServiceImpl`：
      - 查询时未存过 `loginMethods` 的历史数据按 `["PASSWORD"]` 处理。
      - 修改时校验取值只能是 `PASSWORD`/`SMS`/`QRCODE` 的子集，缺少 `PASSWORD` 时自动补齐，
        非法取值直接拒绝。
      - 新建应用默认认证配置时 `loginMethods` 初始化为 `["PASSWORD"]`。
- [x] 3.4 `AppAuthConfigConvert`（MapStruct）补充新增字段映射。
- [x] 3.5 更新 `AppAuthConfigController` 相关 `@Operation` 描述，覆盖新字段语义。

## 4. SSO 登录上下文解析

- [x] 4.1 `sso/support` 包新增 `SsoLoginContextResolver`：从 `redirect` 原始 URL 反解出
      CAS `appId`（路径 `/api/authn/cas/{appId}/login`）或 OAuth2 `client_id`（查询串），
      解析失败/查不到应用配置时返回"仅 PASSWORD"的保守结果。
- [x] 4.2 单元测试覆盖：CAS 场景、OAuth2 场景、`redirect` 缺失、`redirect` 无法解析、应用
      不存在几种输入。

## 5. 登录方式查询与短信登录

- [x] 5.1 新增 `GET /api/authn/sso/login-methods`（`sso/controller` 或新增
      `sso/loginmethod` 包），复用 `SsoLoginContextResolver` + `AppAuthConfigService`。
- [x] 5.2 新增 `sso/sms` 包：`SmsSender` 接口 + `LogSmsSender` 占位实现（写应用日志）。
- [x] 5.3 新增短信验证码 service：生成/校验 6 位数字验证码、冷却/每日上限/连续失败计数，
      Redis key 前缀延续既有风格（如 `sso:sms:code:`/`sso:sms:cooldown:`/
      `sso:sms:daily:`/`sso:sms:attempts:`）。
- [x] 5.4 手机号唯一匹配查询：按 `mobile` 精确匹配、`status=ENABLED`、未删除过滤，命中数
      恰为 1 时才视为可发送/可登录。
- [x] 5.5 新增 `POST /api/authn/sso/sms/code`（发送验证码，统一成功响应、内部按 4.4 条件
      判断是否真正发送）与 `POST /api/authn/sso/sms/login`（校验并登录，复用
      `SsoSessionService.issue` + Cookie 下发 + 首登标识 + 登录日志记录，`loginMethod=SMS`）。
- [x] 5.6 上述两个接口均先校验目标应用当前是否允许 `SMS`，不允许时拒绝。

## 6. 扫码登录

- [x] 6.1 新增 `sso/qrcode` 包：会话状态枚举（待扫码/已扫码未确认/已确认/已过期不存在），
      Redis 存储结构（`sso:qrcode:<token>`，含 `status`/`appId`/`redirect`/`userId`）。
- [x] 6.2 `POST /api/authn/sso/qrcode/session`：校验目标应用允许 `QRCODE`，生成一次性令牌，
      建立"待扫码"会话，返回令牌与确认页地址。
- [x] 6.3 `GET /api/authn/sso/qrcode/{token}/status`：返回当前状态；命中"已确认"且首次读到时
      签发 SSO 会话（`SsoSessionService.issue` + Cookie 下发 + 首登标识 + 登录日志记录，
      `loginMethod=QRCODE`），随后立即置为不可再消费。
- [x] 6.4 手机端标记已扫码接口：`PENDING → SCANNED`，幂等、令牌无效时静默忽略。
- [x] 6.5 `POST /api/authn/sso/qrcode/{token}/confirm`：要求手机端携带有效 SSO 会话 Cookie，
      校验令牌状态为 `PENDING`/`SCANNED`，更新为 `CONFIRMED` 并记录 `userId`。
- [x] 6.6 单元测试覆盖状态机全部合法/非法流转（重复确认、过期令牌、未登录确认、重复消费
      已确认状态）。

## 7. 登录日志扩展

- [x] 7.1 `LoginLogEntity` 新增 `loginMethod` 字段；`LoginLogVO`/`LoginLogQueryRequest`
      同步新增；`LoginLogConvert` 补充映射。
- [x] 7.2 `LoginLogRecorder`/`LoginLogRecorderImpl` 的 `recordSuccess`/`recordFailure`
      新增登录方式参数（或重载），默认口令登录场景保持原调用方式不变（内部固定传
      `PASSWORD`）。
- [x] 7.3 `SsoLoginController`（口令登录分支）、新增的短信/扫码登录入口分别传入正确的
      `loginMethod`。
- [x] 7.4 `LoginLogQueryServiceImpl` 新增按 `loginMethod` 筛选。
- [x] 7.5 前端登录日志管理页新增"登录方式"列与筛选下拉（口令/短信/扫码文案映射）。

## 8. 前端——SSO 登录页多方式改造

- [x] 8.1 `frontend/package.json` 新增 `qrcode` 依赖。
- [x] 8.2 `api/sso.ts` 新增：查询登录方式、短信发送/登录、二维码会话创建/状态轮询/手机端
      标记扫码/确认 对应的请求封装。
- [x] 8.3 `SsoLoginView.vue` 改造：加载时调用登录方式查询接口，据结果决定展示口令/短信/
      扫码标签页（仅 `PASSWORD` 时退化为当前无标签页样式）；短信标签页含手机号输入、获取
      验证码倒计时、验证码输入、提交登录；扫码标签页展示后端返回内容生成的二维码图片
      （用 `qrcode` 包渲染）、按状态轮询更新提示文案（待扫码/已扫码待确认/已过期可刷新）、
      拿到"已确认"结果后按现有 `redirectToTarget` 逻辑整页跳转。
- [x] 8.4 新增扫码确认页路由与组件（响应式，适配手机浏览器）：解析 `token`，未登录时展示
      与口令登录一致的表单（登录后留在当前页而不整页跳转），已登录时展示"确认登录"按钮，
      调用标记已扫码/确认接口，处理令牌过期态的提示。

## 9. 前端——应用配置页认证管理扩展

- [x] 9.1 应用配置页"认证管理"标签页新增登录认证方式勾选项（口令固定选中且禁用取消勾选，
      短信/扫码可勾选），保存时提交 `loginMethods`。
- [x] 9.2 相关 TypeScript 类型（`types/` 下应用认证配置相关类型）同步新增字段。

## 10. 权限资源编码同步

- [x] 10.1 更新仓库根目录 `权限资源.txt`，调整 `AppManagement:app:config:editAuth` 描述
      文案，覆盖登录认证方式勾选项的保存动作。

## 11. 联调与验证

- [x] 11.1 后端：`./gradlew test` 全量跑通，新增单元测试覆盖 `SsoLoginContextResolver`、
      短信验证码防枚举/限流分支、扫码状态机分支、`AppAuthConfigServiceImpl` 的
      `loginMethods` 校验逻辑。
- [x] 11.2 前端：`npm run build` 类型检查通过。
- [ ] 11.3 手动联调：在浏览器中分别用口令、短信（Mock 发送走应用日志确认验证码）、扫码
      （两个浏览器模拟 PC + 手机）完成一次完整的 SSO 登录，确认最终都能正常跳转回目标应用
      并在登录日志里看到对应的 `loginMethod` 与关联 `sessionId`。
- [ ] 11.4 手动验证：应用未启用短信/扫码时，登录页不展示对应标签页，且直接调用对应接口
      也被服务端拒绝。

## 12. OpenSpec 文档同步

- [x] 12.1 实现完成后，基于真实 diff/测试结果，用 `openspec-doc-sync` 校对更新本 change 的
      `proposal.md`/`design.md`/`tasks.md`，再进行 `openspec-sync-specs`（应用 spec delta）
      与后续归档。
