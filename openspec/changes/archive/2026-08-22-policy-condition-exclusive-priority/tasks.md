## 1. 数据库

- [x] 1.1 新增 Flyway 迁移脚本 `V4__add_policy_show_order_and_denied_policy_id.sql`：`ALTER TABLE tab_app_access_policy ADD COLUMN show_order INT NOT NULL DEFAULT 0 COMMENT '显示序号，数值越小优先级越高，运行时按升序取第一条命中身份的策略计算结果'`，使用通用可移植 SQL，不引入版本相关特性
- [x] 1.2 同一脚本新增 `ALTER TABLE tab_sso_protocol_log ADD COLUMN denied_policy_id BIGINT UNSIGNED NULL COMMENT '被应用访问授权策略拒绝时，拒绝来源的策略 id，仅该失败原因下非空'`

## 2. 后端：显示序号字段

- [x] 2.1 `PolicyEntity`（`backend/src/main/java/cn/nihility/rbac/appaccess/policy/entity/PolicyEntity.java`）新增 `showOrder` 字段
- [x] 2.2 `PolicyVO`、`PolicyCreateRequest`、`PolicyUpdateRequest` 新增 `showOrder` 字段（`PolicyCreateRequest`/`PolicyUpdateRequest` 可选，未提供时默认 `0`）
- [x] 2.3 `PolicyConvert`（MapStruct）核对 `showOrder` 是否正确映射
- [x] 2.4 `PolicyController` 新增/编辑接口的 OpenAPI 描述（`@Parameter`/`@Schema`）补充 `showOrder` 说明
- [x] 2.5 策略规则分页查询默认排序改为按 `show_order` 升序、`id` 升序（`PolicyMapper`/`PolicyServiceImpl` 分页查询逻辑）

## 3. 后端：三选一互斥校验（已实现后按用户反馈撤回，见第 8 组）

- [x] 3.1 ~~`PolicyServiceImpl.assertScopeAndAttrNotBothEmpty` 替换为互斥校验方法~~（已撤回，见 8.1）
- [x] 3.2 ~~`create`/`update` 方法调用新校验方法~~（已撤回，互斥校验本身已移除）
- [x] 3.3 ~~更新 Javadoc 为"三类互斥"~~（已撤回，见 8.2）
- [x] 3.4 ~~新增互斥相关单元测试~~（已撤回，见 8.3）

## 4. 后端：运行时判定改为排序取第一条

- [x] 4.1 `resources/mybatis/mapper/PolicyGrantMapper.xml` 中 `selectActivePolicyIds` 对应 SQL 新增 JOIN `tab_app_access_policy` 取 `show_order`，并按 `p.show_order ASC, p.id ASC` 排序返回
- [x] 4.2 `AppAccessEffectivePermissionServiceImpl#isAuthorized(userId, appId, clientIp, userAgent)` 的候选策略遍历逻辑改为只取排序后的第一个候选策略，判断其请求控制条件是否满足，直接返回结果，不再循环遍历其余候选
- [x] 4.3 确认 `isAuthorized(userId, appId)`（无请求上下文重载）与 `existsActiveGrant` 逻辑保持不变，不引入排序/优先级语义
- [x] 4.4 更新/新增单元测试：覆盖"排序第一条满足则放行""排序第一条不满足则直接拒绝、不再检查第二条""序号相同按 id 升序 tie-break""无候选策略时不可访问"等分支，重点验证与原有"任一满足即放行"行为不同的新场景

## 5. 后端：记录拒绝来源的策略 id

- [x] 5.1 新增 `AppAccessAuthorizationDecision`（record：`boolean authorized`、`Long deniedByPolicyId`），`AppAccessEffectivePermissionService`/`Impl` 新增 `checkAuthorization(userId, appId, clientIp, userAgent)` 方法返回该结构；原有 `isAuthorized(userId, appId, clientIp, userAgent)` 改为内部委托，签名与对外行为不变
- [x] 5.2 `SsoProtocolException` 新增 `Long deniedByPolicyId` 字段、对应构造函数与 getter，原单参构造函数保留（`deniedByPolicyId` 默认 `null`）
- [x] 5.3 `AppAccessAuthorizationChecker#assertAuthorized` 改为调用 `checkAuthorization(...)`，未授权时把 `deniedByPolicyId` 一并传入抛出的 `SsoProtocolException`
- [x] 5.4 `SsoProtocolLogRecorder#recordFailure`、`SsoProtocolLogRecorderImpl`、`SsoProtocolLogEntity`、`SsoProtocolLogVO`、`SsoProtocolLogConvert` 新增 `deniedPolicyId` 字段
- [x] 5.5 `CasController.login`、`OAuthController.authorize` 中"授权校验被拒绝"的 catch 分支从异常取出 `deniedByPolicyId` 传给 `recordFailure`；核对其余全部 `recordFailure` 调用点（`CasController`/`OAuthController`/`SsoLogoutController`/`SsoLogoutExecutor`，约 10 处）改为显式传 `null`
- [x] 5.6 更新/新增单元测试：覆盖"被策略拒绝时记录对应策略 id""被人工例外拒绝时该字段为空""其余失败原因该字段为空"等分支

## 6. 前端：策略规则表单三选一（已实现后按用户反馈撤回，见第 8 组）+ 显示序号

- [x] 6.1 ~~`PolicyRulePanel.vue` 新增"条件类型"单选~~（已撤回，见 8.4）
- [x] 6.2 ~~编辑存量策略默认选中优先级最高的非空类型~~（已撤回，三个区块始终同时可见，不需要默认选中逻辑）
- [x] 6.3 "用户属性条件"相关 label/提示文案改为"用户属性"（保留）
- [x] 6.4 新增"显示序号"数字输入项（默认 `0`），随表单一起提交（保留）
- [x] 6.5 ~~`validateConditions()` 改为"必须且只能选中一类"~~（已撤回，见 8.4，改回"三类中至少一类非空"）
- [x] 6.6 策略规则列表新增"序号"列，列表默认按序号升序排列（保留）
- [x] 6.7 `frontend/src/types/appAccess.ts` 的 `PolicyVO`/`PolicyFormRequest` 新增 `showOrder` 字段（保留）

## 7. 验证

- [x] 7.1 后端执行 `./gradlew test`，确认新增/受影响测试通过
- [x] 7.2 前端执行 `npm run build`，确认类型无误
- [ ] 7.3 手工验证：新增策略规则时组织范围/用户属性/请求控制可同时配置、至少一类非空；显示序号可填写；策略列表按序号升序展示；构造多条策略验证"排序第一条不满足即拒绝、不再看后续策略"的新判定行为
- [ ] 7.4 手工验证：构造一次被策略拒绝的 CAS/OAuth2 登录票据签发请求，确认 `tab_sso_protocol_log` 对应失败记录的 `denied_policy_id` 正确记录了拒绝来源的策略 id
- [x] 7.5 核对 `权限资源.txt`：本次未新增/删除菜单或按钮，确认无需更新

## 8. 撤回三选一互斥（用户反馈：需保持可同时配置多类、至少一类非空）

- [x] 8.1 `PolicyServiceImpl` 校验方法由 `assertConditionsExclusive`（有且仅有一类非空）改回 `assertConditionsNotAllEmpty`（三类不能同时为空，允许同时配置多类），错误文案改回"组织范围、用户属性、请求控制条件不能同时为空，请至少配置一类"
- [x] 8.2 `PolicyController`、`PolicyCreateRequest`、`PolicyUpdateRequest` 的 OpenAPI 描述/Javadoc 同步改回"至少一类非空，可同时配置多类"
- [x] 8.3 `PolicyServiceImplTest`：移除"同时配置两类被拒绝"的用例，改为验证"同时配置组织范围与用户属性两类应成功保存"（create/update 各一条）；"三类全空被拒绝"用例的错误文案断言改回"不能同时为空"
- [x] 8.4 `PolicyRulePanel.vue`：移除"条件类型"单选与相关 `conditionType` 状态；组织范围/用户属性/请求控制三个区块恢复为始终可见；`toSubmitPayload()` 恢复为原样提交三类当前内容（不清空）；`validateConditions()` 改回"三类中至少一类非空 + 已填写类型内部行数据合法"；`openEditDialog()` 移除"默认选中类型"逻辑
- [x] 8.5 `PolicyExecutionServiceImpl` 不受影响，未做改动（组织范围∩用户属性交集算法本就支持二者同时配置，无需还原）
- [x] 8.6 delta spec `specs/app-access-authorization/spec.md` 同步改回：移除互斥描述与相关 Scenario，恢复"策略规则的手动执行"需求中"执行组织范围与属性条件组合的策略规则"原始 Scenario
- [x] 8.7 `proposal.md`/`design.md` 补充说明本次撤回的背景与最终状态
- [x] 8.8 回归验证：`./gradlew test` 全量通过、`npm run build` 通过
