## Why

`app-access-authorization` 能力目前只按"身份"（组织范围/用户属性）判定策略授权，一旦策略命中，任何设备、任何网络位置的请求都能用该授权登录目标应用。管理员需要能在策略层面进一步限制"从哪里、用什么访问"——例如某类敏感应用只允许公司内网 IP 段访问，或只允许通过 Chrome 浏览器访问，而不是仅凭身份放行。

## What Changes

- 策略规则新增可选的"请求控制"条件，与组织范围/用户属性条件平级：浏览器白名单（多选，预置 Chrome/Firefox/Safari/Edge/Opera/IE 等基于 User-Agent 识别的浏览器类型）与 IP/IP 段白名单（单 IP 或 CIDR 网段，多条）。两个维度均可选，都不配置时该策略不做请求控制限制；都配置时需同时满足（AND）；只配置其中一项时只按该项校验。
- 请求控制是**请求时校验**，不参与策略"执行"时的批量计算——`tab_app_access_policy_grant` 继续只表达"身份是否命中"，不感知浏览器/IP（这两者本来就无法离线批量计算，只能在请求发生的那一刻判断）。
- 最终生效权限判定规则调整：策略来源的授权在"身份命中"基础上，新增"至少存在一条身份命中且请求满足其请求控制条件的策略"这一约束（多条策略命中同一用户+应用时，只要其中一条策略本身未配置限制或请求满足该策略的限制即算通过，语义与策略间"并集"关系一致）；人工追加授权（`MANUAL_GRANT`）**不受**请求控制约束，无条件放行（沿用其"管理员明确决定、优先级最高"的既有语义）；人工收回（`MANUAL_DENY`）不受影响，仍无条件拒绝。
- SSO 登录拦截（CAS 服务票据签发、OAuth2 授权码签发）在校验授权时一并读取当前请求的客户端 IP（复用 `X-Forwarded-For`/`getRemoteAddr()` 既有解析方式）与 `User-Agent`，传入最终生效权限判定；管理端"最终生效权限查询"审计接口继续只做身份维度判定（无请求上下文可用），额外展示命中策略是否配置了请求控制，提示这是"身份层面"结果，不代表所有请求场景都能通过。
- 管理端策略规则新建/编辑表单新增"请求控制"配置区块（浏览器多选 + IP/网段可增删列表）。

## Capabilities

### Modified Capabilities
- `app-access-authorization`：策略规则新增请求控制条件（浏览器白名单/IP 白名单），最终生效权限计算规则相应调整。
- `app-sso-protocol-runtime`：CAS/OAuth2 登录拦截新增读取请求客户端 IP/User-Agent 并纳入授权判定。

## Impact

- 新增数据库表：`tab_app_access_policy_browser_rule`（`policy_id + browser_code`）、`tab_app_access_policy_ip_rule`（`policy_id + ip_cidr`），均为可选多行、整体替换语义，与既有 `tab_app_access_policy_org_scope`/`tab_app_access_policy_target_app` 同构。
- 复用现有 `backend/src/main/java/cn/nihility/rbac/operationlog/util/UserAgentParser.java`（浏览器识别正则，不重写）；新增 CIDR/IP 匹配工具（仓库目前没有现成实现）；把 `LoginLogRecorderImpl` 里"解析客户端 IP（`X-Forwarded-For` 优先，否则 `getRemoteAddr()`）"的私有逻辑提炼为共享工具方法，供本次改动与该模块共同复用，避免第二处重复实现。
- 修改 `AppAccessEffectivePermissionService`：新增一个携带请求上下文（客户端 IP、User-Agent）的判定入口，供 SSO 拦截使用；原有不带请求上下文的入口保持不变，供管理端查询接口使用（语义调整为"仅身份维度"）。
- 修改 `AppAccessAuthorizationChecker`、`CasController.login`、`OAuthController.authorize`：读取请求的客户端 IP/User-Agent 并传入授权校验。
- 修改策略规则的 Controller/Service/DTO：新增请求控制条件的读写；`PolicyVO` 新增回显字段。
- 前端：策略规则新建/编辑表单新增请求控制配置区块；最终生效权限查询结果标注命中策略是否配置了请求控制。
- 更新根目录 `权限资源.txt`（如涉及新增按钮权限点）与 OpenSpec 权威 spec（视用户指示决定是否同步）。
