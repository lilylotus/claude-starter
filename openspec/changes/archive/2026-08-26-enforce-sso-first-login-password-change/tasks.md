## 1. SSO 首登改密后端接口

- [x] 1.1 新增 SSO 登录成功响应 DTO，在 `SsoLoginController#login` 返回 `firstLogin` 并更新 OpenAPI 注解；通过控制器测试验证普通用户为 `false`、待改密用户为 `true` 且均正确签发 SSO Cookie
- [x] 1.2 新增 `POST /api/authn/sso/password`，仅以有效 `sso_session` Cookie 解析用户，要求当前仍处于待改密状态、校验旧密码后更新新密码；通过测试验证成功、无会话、旧密码错误和状态已清除场景

## 2. CAS/OAuth2.0 协议门禁

- [x] 2.1 扩展协议重定向辅助方法，在保留原完整协议请求的同时标记 `forcePasswordChange=true`；通过辅助方法或控制器断言验证 URL 编码与查询参数
- [x] 2.2 在 CAS 登录入口的有效会话分支增加首登状态检查，待改密时重定向到 SSO 强制改密页且不签发服务票据；运行 `CasControllerTest` 验证
- [x] 2.3 在 OAuth2.0 授权入口的有效会话分支增加首登状态检查，待改密时重定向到 SSO 强制改密页且不签发授权码；运行 `OAuthControllerTest` 验证

## 3. SSO 登录页双状态交互

- [x] 3.1 更新 SSO API 与类型定义，接收登录返回的 `firstLogin` 并封装 SSO Cookie 改密接口；通过 TypeScript 编译验证类型一致
- [x] 3.2 将 `SsoLoginView.vue` 实现为登录/强制改密双状态，保留原 `redirect`，改密成功后整页恢复协议请求；检查桌面与移动布局、键盘提交、loading 和错误提示均与当前状态一致
- [x] 3.3 保持现有品牌蓝与“身份→授权→凭证→应用”链式视觉，调整改密态标题、说明和表单但不新增管理端会话依赖；运行 `npm run build` 验证前端构建

## 4. 回归与文档同步

- [x] 4.1 运行 SSO 登录、CAS、OAuth2.0 聚焦测试，验证正常登录流程不回归且首登门禁覆盖两种协议
- [x] 4.2 运行后端 `./gradlew test` 和前端 `npm run build` 完成全量回归
- [x] 4.3 基于最终代码 diff 与测试结果回填 `proposal.md`、`design.md`、`tasks.md`，核对两份 delta spec 与实际行为一致
