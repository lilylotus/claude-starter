## 1. SSO 登录记录登录日志

- [x] 1.1 重读 `cn.nihility.rbac.auth.service.impl.AuthServiceImpl#login` 的完整分支逻辑（解密失败/账号不存在/账号停用/账号删除/密码不匹配/成功），确认 `LoginFailReason` 常量的取值与顺序
- [x] 1.2 重构 `cn.nihility.rbac.sso.controller.SsoLoginController#login`，按同样的分支粒度分别处理六类场景，各自调用 `LoginLogRecorder#recordFailure`/`#recordSuccess`；对外异常/错误提示文案保持不变
- [x] 1.3 确认 CAS（`CasController`）、OAuth2（`OAuthController`）的票据/授权码签发路径不受影响，不新增登录日志调用（design.md Decision 1）
- [x] 1.4 新增 `SsoLoginControllerTest`（当前无测试覆盖），覆盖六类场景各自写入正确的登录日志字段，且对外提示文案不变

## 2. 组织/用户/任职变更后策略自动重新执行

- [x] 2.1 重读 `cn.nihility.rbac.sync.event.support.DomainChangeEventProcessor#process` 现有的通知候选处理逻辑与 try/catch 结构
- [x] 2.2 在该方法内新增独立 try/catch 分支：`dataType` 属于 `{ORG, USER, POSITION}` 时，查询全部 `status=启用` 的策略 id，逐条调用 `PolicyExecutionService#execute(policyId)`，单条失败仅记录日志、不中断其余策略
- [x] 2.3 确认该分支异步执行、不阻塞原始组织/用户/任职写请求的响应（复用现有 Disruptor 异步转发，不需要额外的线程池）
- [x] 2.4 编写测试：新增用户后命中组织范围策略自动产生授权记录；编辑用户属性后命中属性条件策略自动产生授权记录；停用用户后自动从授权记录中移除；单条策略执行失败不影响其余策略
- [x] 2.5（用户报告"新增用户同时关联任职未自动加入策略"后补充修复）`PolicyExecutionService` 新增 `execute(Long, String)` 重载，执行人显式传参而非依赖 `CurrentOperatorService`（Disruptor 消费者线程无 HTTP 登录态，原实现每次自动重算都会抛 `IllegalStateException` 并被静默吞掉，design.md Decision 6）；`DomainChangeEventProcessor` 改调新重载，传入 `event.getOperator()`；同步修正 `DomainChangeEventProcessorTest` 的 mock 校验

## 3. 登录日志、操作日志定期清理

- [x] 3.1 新增 `@ConfigurationProperties(prefix = "rbac.log-cleanup")` 配置类，字段 `cron`（默认 `"0 0 1 * * ?"`）、`retentionDays`（默认 `180`），仿照 `RbacSsoProperties` 的既有写法
- [x] 3.2 新增 `@Scheduled` 定时任务组件，分别对 `tab_login_log`/`tab_operation_log` 执行按 `create_time` 早于截止时间的批量删除（MyBatis-Plus `LambdaQueryWrapper`，不需要 XML）
- [x] 3.3 两张表的删除操作各自 try/catch，互不影响，失败记录到应用日志
- [x] 3.4 `application.yml` 补充 `rbac.log-cleanup` 默认配置注释说明
- [x] 3.5 编写测试：验证保留期内/外的记录被正确保留/删除（可直接调用清理组件的方法而不依赖真实 cron 触发）

## 4. 策略保存校验放宽 + 执行语义补全

- [x] 4.1 修改 `cn.nihility.rbac.appaccess.policy.service.impl.PolicyServiceImpl#assertScopeAndAttrNotBothEmpty`（或等价重命名），改为接收/校验组织范围、用户属性条件、浏览器白名单、IP 白名单四者，仅四者全部为空时才拒绝；更新校验错误提示文案
- [x] 4.2 修改 `cn.nihility.rbac.appaccess.policy.service.impl.PolicyExecutionServiceImpl#execute`，补充"组织范围与用户属性条件都未配置时，命中系统内全部启用状态用户"分支（design.md Decision 3）
- [x] 4.3 确认 `PolicyController`/`PolicyCreateRequest`/`PolicyUpdateRequest` 的 `@Valid` 校验与手动校验调用点同步更新，不遗漏编辑接口
- [ ] 4.4 前端策略新增/编辑表单的校验提示文案同步确认是否需要调整（若前端也做了"组织范围/用户属性不能同时为空"的前置校验，需要一并放宽）——本次实现范围限定在后端，未触碰 `frontend/`，该项需前端开发单独确认
- [x] 4.5 编写测试：仅配置请求控制条件的策略能成功保存；四者全部为空时拒绝保存；执行仅含请求控制条件的策略命中全部启用用户；执行后配合「最终生效权限计算规则」验证请求控制在运行时正确收窄访问

## 5. 验证

- [x] 5.1 `./gradlew test` 全量测试套件通过
- [x] 5.2 更新根目录 `权限资源.txt`（若本次改动涉及任何菜单/按钮增删——预期不涉及，因为都是既有接口的行为调整，不新增页面/按钮）
- [ ] 5.3 人工核对：SSO 登录成功/失败在 `tab_login_log` 里能查到对应记录；新增用户后无需手动点"执行"即可拿到策略授权；清理任务按配置的 cron 与保留天数正常工作；仅配置请求控制条件的策略能保存且执行后产生全量启用用户的授权记录——以上行为均已有自动化测试覆盖并通过，但真实人工核对需要用户在联调环境中另行确认

## 6. OpenSpec 文档收尾

- [ ] 6.1 实现完成后，若与 design.md 的假设有出入，更新 design.md 对应 Decision 与本 tasks.md 记录实际情况
- [ ] 6.2 归档本 change，并将 4 份 delta spec 同步进 `openspec/specs/app-sso-protocol-runtime`、`openspec/specs/login-log-management`、`openspec/specs/operation-log-management`、`openspec/specs/app-access-authorization`
