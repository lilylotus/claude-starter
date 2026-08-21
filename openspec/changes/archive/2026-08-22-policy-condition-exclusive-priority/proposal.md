## Why

应用访问授权的策略规则目前允许组织范围、用户属性条件、请求控制（浏览器/IP白名单）四类条件任意组合配置，运行时多条命中身份的策略之间又是"任一满足请求控制即放行"的并集语义——策略之间没有优先级，无法表达"先按更精确的规则判断、不满足就拒绝"这类常见授权诉求。现在给策略引入显式的生效优先级，使多条策略之间从"并集找一条满足就放行"改为"按优先级取第一条命中身份的策略说了算"；同时把"用户属性条件"展示文案简化为"用户属性"。

> 本 change 最初还计划把单条策略的组织范围/用户属性/请求控制收紧为三选一互斥，已实现并落地过一版；用户反馈希望保持"可以同时配置多类，至少配置一类"的原有交互（列表展示、不做单选），因此互斥校验已撤回，恢复为三者中至少一类非空即可、允许同时配置多类。显示序号+生效优先级、"用户属性"改名、拒绝来源策略 id 记录这三项保留。

## What Changes

- 新增/编辑策略规则时，"组织范围""用户属性""请求控制"（浏览器白名单+IP白名单合并算一类）三者中至少配置一类，允许同时配置多类（服务层校验维持"三类不能同时为空"，不要求互斥）。
- 前端"用户属性条件"展示文案改为"用户属性"（仅前端 label/提示文案与 OpenSpec 需求措辞调整，后端字段名、表名、接口字段等内部标识符不改名，避免无谓的大范围改名）。
- **BREAKING**：策略新增字段"显示序号"（`showOrder`），新增/编辑时可填写；最终生效权限判断（`AppAccessEffectivePermissionServiceImpl#isAuthorized`）从"遍历该用户命中身份的候选策略，任一策略请求控制满足即放行"改为"候选策略按序号升序排序，取第一条，判断其请求控制条件是否满足当前请求——满足则放行，不满足则直接拒绝，不再看后面序号更大的策略"。此为策略级别匹配语义的根本调整，会改变部分现有多策略叠加场景下的最终判定结果。
- 策略规则列表页展示"序号"列，默认按序号升序排列。
- 人工例外（GRANT/DENY override）优先级不变，仍然高于策略规则计算结果，不受本次调整影响。
- 新增：访问在 CAS/OAuth2 登录票据签发环节被应用访问授权策略拒绝时（即"按序号取第一条候选策略、其请求控制条件不满足"这一分支），`tab_sso_protocol_log` 对应的失败记录 SHALL 额外记录是被**哪一条策略**（策略 id）拒绝的，供问题排查时直接定位到具体策略，不用再去反查候选策略集合猜测。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `app-access-authorization`：策略规则的条件配置维持"组织范围/用户属性/请求控制三者中至少一类非空，允许同时配置多类"；新增策略"显示序号"字段；最终生效权限计算规则从"命中任一策略即放行"改为"按序号升序取第一条命中身份的策略，由它单独决定放行或拒绝"；判定接口在"被策略拒绝"这一分支 SHALL 能向调用方提供具体的策略 id。
- `sso-protocol-access-log`：CAS/OAuth2 登录票据签发被应用访问授权策略拒绝时产生的失败记录，新增记录拒绝来源的策略 id。

## Impact

- 数据库：`tab_app_access_policy` 新增 `show_order` 列（Flyway 新迁移脚本），无需回填存量数据（默认值即可）。
- 后端：
  - `PolicyEntity`/`PolicyVO`/`PolicyCreateRequest`/`PolicyUpdateRequest`/`PolicyConvert` 新增 `showOrder` 字段。
  - `PolicyServiceImpl`：条件校验维持"三类（组织范围/用户属性/请求控制）不能同时为空"，不要求互斥，允许同时配置多类；`create`/`update` 落库逻辑不变（各子表分别保存）。
  - `PolicyMapper`/`PolicyGrantMapper`：新增按 `show_order` 升序取候选策略（含关联 policy 表 JOIN 或子查询）的查询能力。
  - `AppAccessEffectivePermissionServiceImpl#isAuthorized`：候选策略遍历逻辑改为"排序取第一条，只判断这一条"，替换原先的循环 OR 逻辑。
  - `PolicyController` 的 OpenAPI 描述同步更新（显示序号说明）；`PolicyExecutionServiceImpl` 的组织范围∩用户属性交集算法不变，组织范围与用户属性条件仍可同时配置。
- 前端：
  - `frontend/src/views/permission/app-access/PolicyRulePanel.vue`：三个条件区块维持一直可见、可同时填写（不做单选切换），新增"显示序号"输入项，"用户属性条件"label 改为"用户属性"。
  - `frontend/src/types/appAccess.ts`：`PolicyVO`/`PolicyFormRequest` 新增 `showOrder` 字段。
  - 策略规则列表页新增"序号"列，默认按序号升序排列。
- 数据库：`tab_sso_protocol_log` 新增 `denied_policy_id` 列（同一 Flyway 迁移脚本或紧随其后的另一个脚本），仅在"被应用访问授权策略拒绝"这一失败原因下非空。
- 后端（拒绝策略 id 的传递链路）：
  - `AppAccessEffectivePermissionService`/`Impl`：新增一个返回"是否放行 + 拒绝来源策略 id"结构化结果的方法，供 `AppAccessAuthorizationChecker` 使用；原有布尔值 `isAuthorized` 方法保留，供其余不需要该细节的调用方继续使用。
  - `SsoProtocolException` 新增可选的 `deniedByPolicyId` 字段/构造函数，`AppAccessAuthorizationChecker#assertAuthorized` 判定为"被策略拒绝"时附带该 id 一并抛出。
  - `SsoProtocolLogRecorder#recordFailure`、`SsoProtocolLogRecorderImpl`、`SsoProtocolLogEntity`/`SsoProtocolLogVO`/`SsoProtocolLogConvert` 新增 `deniedPolicyId` 字段；`CasController.login`、`OAuthController.authorize` 的 catch 分支从异常中取出该 id 传给 `recordFailure`，其余全部 `recordFailure` 调用点（约 10 处，无关本次拒绝场景）传 `null`。
- OpenSpec：`app-access-authorization` 主 spec 需要修改"策略规则的定义与维护""策略规则的用户属性条件"（改名）"策略规则的手动执行""最终生效权限计算规则"等既有需求条款；`sso-protocol-access-log` 主 spec 需要修改"SSO 协议调用记录"需求。
