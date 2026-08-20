## 1. 数据库迁移

- [x] 1.1 新增 `backend/src/main/resources/db/migration/V10__add_app_access_policy_request_control.sql`：
  - `tab_app_access_policy_browser_rule`：`id/policy_id/browser_code(VARCHAR，CHROME/FIREFOX/SAFARI/EDGE/OPERA/IE)/审计字段`，`UNIQUE KEY(policy_id, browser_code)`
  - `tab_app_access_policy_ip_rule`：`id/policy_id/ip_cidr(VARCHAR(64))/审计字段`，`UNIQUE KEY(policy_id, ip_cidr)`
  - 均无物理外键，字段命名核对不与 MySQL/PostgreSQL/Oracle/SQL Server 保留字冲突

## 2. 后端：共享工具

- [x] 2.1 新增 `IpCidrMatcher`（放在 `backend/src/main/java/cn/nihility/rbac/appaccess/support/`）：`matches(String clientIp, String ipCidrRule)` 判断客户端 IP 是否命中单 IP/CIDR 规则（`java.net.InetAddress` 手写字节比较，只处理 IPv4，非法格式/非 4 字节地址返回不匹配）；另提供 `isValidRule(String ipCidrRule)` 供 Service 层保存校验复用（CIDR 前缀 0-31，`/32` 视为单 IP 允许省略）
- [x] 2.2 新增 `common/util/ClientRequestUtils.java`：把 `LoginLogRecorderImpl` 现有的私有客户端 IP 解析方法（`X-Forwarded-For` 第一个值优先，否则 `request.getRemoteAddr()`）提炼为共享静态方法 `resolveClientIp(HttpServletRequest)`，逻辑原样迁移，不改变行为
- [x] 2.3 `LoginLogRecorderImpl` 改为调用 `ClientRequestUtils.resolveClientIp(...)`，删除自己的私有实现；运行其现有测试确认行为未变

## 3. 后端：策略规则请求控制条件读写

- [x] 3.1 新建 `PolicyBrowserRuleEntity`/`PolicyIpRuleEntity`（`appaccess/policy/entity/`）+ 对应 Mapper（`appaccess/policy/mapper/`）。实现调整：两张子表均为单表查询（无需 JOIN 回填其它表字段，`browserLabel` 由 `PolicyRequestControlBrowser` 常量类在 Java 侧映射），故未新增 XML，直接复用 `BaseMapper#selectList` + `LambdaQueryWrapper#in` 做批量按 `policy_id` 查询（一次 `IN` 查询取多个候选策略的规则，避免 N+1），与项目"多表查询才用 XML"的既有约定一致
- [x] 3.2 `PolicyService`/`PolicyServiceImpl`：新增/编辑策略时读写浏览器白名单、IP 白名单（整体替换语义，先删后插）；IP 条目保存前用 `IpCidrMatcher.isValidRule` 校验格式，不合法拒绝保存；浏览器白名单/IP 白名单校验放宽为"完全可选，不参与组织范围/用户属性条件的非空校验"
- [x] 3.3 `PolicyController` 现有接口透传新字段，`PolicyCreateRequest`/`PolicyUpdateRequest` 新增 `browserRules: string[]`（浏览器编码数组）、`ipRules: string[]`（IP/CIDR 字符串数组）
- [x] 3.4 `PolicyVO` 新增 `browserRules: [{browserCode, browserLabel}]`、`ipRules: [{ipCidr}]`，与 `orgScopes`/`userAttrs`/`targetApps` 并列回显；"配置是否已变更待重新执行"（`pendingReExecute`）判定 SHALL NOT 把请求控制条件的 `update_time` 纳入比较（请求控制不参与执行计算，见 tasks.md 4.1 的策略执行不变说明）

## 4. 后端：策略执行不变，确认不受影响

- [x] 4.1 确认 `PolicyExecutionService.execute(...)` 不读取、不校验请求控制条件（无需改动代码，补一条注释/Javadoc 说明"请求控制是运行时校验，不参与本方法的批量身份计算"，避免后来者误以为遗漏）

## 5. 后端：最终生效权限计算扩展

- [x] 5.1 `AppAccessEffectivePermissionService` 新增方法 `isAuthorized(Long userId, Long appId, String clientIp, String userAgent)`：① 存在 `DENY` 人工例外 → `false`；② 存在 `GRANT` 人工例外 → `true`（不看 `clientIp`/`userAgent`）；③ 查该 `user_id+app_id` 在启用中策略下的 `tab_app_access_policy_grant` 记录，取 `policy_id` 去重集合，为空则 `false`；④ 批量查这些候选 `policy_id` 的浏览器/IP 规则（一次 `IN` 查询，避免逐个策略查询）；⑤ 逐个候选策略判断是否同时满足浏览器白名单（`UserAgentParser.parseBrowser(userAgent)` 识别结果命中白名单之一，白名单为空则视为满足）与 IP 白名单（`IpCidrMatcher.matches` 命中任一条目，白名单为空则视为满足）；⑥ 存在至少一个满足的候选策略 → `true`，否则 `false`
- [x] 5.2 原有 `isAuthorized(Long userId, Long appId)` 方法保留独立实现（沿用 `existsActiveGrant` 单条 EXISTS 查询），与新方法共享私有辅助方法 `resolveOverrideDecision(userId, appId)` 处理人工例外优先级判定，确保其对外行为完全不变（现有测试未改动断言即全部通过）
- [x] 5.3 `listEffectiveByUser`/`listEffectiveByApp` 查询结果新增 `hasRequestControl` 字段：命中依据为 `POLICY` 时，只要 `policyNames` 对应的任一策略配置了浏览器或 IP 白名单即为 `true`

## 6. 后端：SSO 登录拦截读取请求上下文

- [x] 6.1 `AppAccessAuthorizationChecker.assertAuthorized` 签名调整为 `assertAuthorized(Long userId, Long appId, String clientIp, String userAgent)`，内部改调用 5.1 的新方法
- [x] 6.2 `CasController.login`：调用 `assertAuthorized` 前，用 `ClientRequestUtils.resolveClientIp(request)` 与 `request.getHeader("User-Agent")` 取值并传入
- [x] 6.3 `OAuthController.authorize`：同 6.2

## 7. 前端

- [x] 7.1 `frontend/src/types/appAccess.ts`：`PolicyVO`/`PolicyCreateRequest`/`PolicyUpdateRequest` 新增 `browserRules`/`ipRules` 字段类型；`AppAccessEffectiveItemVO` 新增 `hasRequestControl` 字段
- [x] 7.2 `frontend/src/api/appAccess.ts`：透传新字段，无需新增接口方法
- [x] 7.3 `PolicyRulePanel.vue` 新建/编辑表单新增"请求控制"配置区块：浏览器多选（Chrome/Firefox/Safari/Edge/Opera/IE 复选框或多选下拉）+ IP/网段可增删的文本输入列表，紧邻组织范围/用户属性条件之后展示，明确标注"均可选，不配置则不限制"
- [x] 7.4 `EffectiveQueryPanel.vue` 结果表格：`sourceType=POLICY` 的记录若 `hasRequestControl=true`，展示一个提示图标/标签（如"含请求控制"），hover 或点击展示"实际访问还需满足对应策略的浏览器/IP 限制"文案

## 8. 测试

- [x] 8.1 `IpCidrMatcherTest`：覆盖单 IP 精确匹配、CIDR 网段匹配（含边界地址）、非法格式、非 IPv4 地址
- [x] 8.2 `ClientRequestUtilsTest`：覆盖 `X-Forwarded-For` 存在/不存在两种取值路径（`LoginLogRecorderImpl` 当前没有独立单元测试，故未合并，`ClientRequestUtilsTest` 是该逻辑唯一的直接单元测试）
- [x] 8.3 `PolicyServiceImplTest` 新增用例：浏览器/IP 白名单整体替换语义、非法 IP 格式/非法浏览器编码拒绝保存、请求控制条件留空不触发"组织范围/属性条件非空"校验之外的额外报错
- [x] 8.4 `AppAccessEffectivePermissionServiceImplTest` 新增用例：覆盖 5.1 的六步判定逻辑（浏览器满足/不满足、IP 满足/不满足、两者都配置的 AND 语义、多策略命中取"任一满足"、GRANT 不受约束、DENY 优先级不变）；确认原两参数方法行为不变（补充回归用例）；补充 `hasRequestControl` 标记的两条用例
- [x] 8.5 `CasControllerTest`/`OAuthControllerTest` 新增用例：身份命中但浏览器/IP 不满足对应策略请求控制时返回 403 JSON；身份命中且满足请求控制时正常签发（CAS 侧覆盖浏览器维度，OAuth2 侧覆盖 IP 维度）
- [x] 8.6 `PolicyExecutionServiceImplTest`：补充一条用例确认配置了请求控制的策略执行结果与未配置时一致（请求控制不影响执行计算）
- [x] 8.7 `./gradlew test`（在 `backend/` 目录下）通过：734 个测试全部通过，0 失败

## 9. 文档

- [x] 9.1 检查 `权限资源.txt` 是否需要更新——确认不需要，浏览器/IP 白名单是策略新建/编辑表单内的新增字段，复用既有 `AppAccessManagement:policy:add`/`AppAccessManagement:policy:edit` 权限点，未引入新的页面级/按钮级权限点
- [x] 9.2 `npm run build`（在 `frontend/` 目录下）通过

## 10. OpenSpec 收尾

- [x] 10.1 实现完成后运行 `openspec-doc-sync` 对齐 `proposal.md`/`design.md`/`tasks.md` 与实际改动
- [ ] 10.2 视用户指示决定是否执行 `openspec-sync-specs`——注意本次 delta 依赖的基线是 `app-access-authorization` change 已实现但尚未同步进 `openspec/specs/` 的内容，执行前需要先确认 `app-access-authorization` 的 delta 是否已同步，避免顺序颠倒导致合并结果与主 spec 脱节（归档仍为用户手动触发，不自动执行）
