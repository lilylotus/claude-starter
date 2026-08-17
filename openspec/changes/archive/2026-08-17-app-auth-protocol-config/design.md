## Context

现状（详见 proposal.md - Why）：`tab_app_config` 已是应用维度一对一的对外接口凭证配置表，
`AppConfigServiceImpl.createDefaultConfig` 在应用创建事务内同时调用
`AppSyncConfigService.createDefaultDomainConfigs` 生成同步配置默认行——本次新增的认证配置
沿用同一接线方式。前端 `AppConfigView.vue` 已用 `el-tabs` 组织"基础信息"/"同步配置"两个
分区，风格（`activeTab` 类型、保存按钮受权限点 `v-if="hasPermission(...)"` 控制、
参数行编辑用"行数组 + 增删按钮"模式如 `notifyParamRows`）作为新增"认证管理" tab 的参照。

本次只做配置存储与展示，不做协议运行时逻辑（proposal.md Non-Goals），因此不引入
`AntPathMatcher` 之外的任何新依赖，也不涉及浏览器 Cookie 会话。

## Goals / Non-Goals

**Goals:**
- 定义 `tab_app_auth_config` 表结构、后端模块划分、DTO 形状、校验规则。
- 定义前端"认证管理" tab 的交互结构，与现有"同步配置" tab 保持一致的编辑/保存/权限模式。
- 给出协议接口地址的只读展示内容来源（后端计算 vs 前端拼接）。

**Non-Goals:**
- 不实现 CAS/OAuth2 协议运行时端点、票据/令牌签发校验、浏览器 SSO 会话（proposal.md
  Non-Goals，留给后续 change）。
- 不引入 ANT 表达式之外的其他匹配语法（如正则），匹配规则統一按 Spring
  `org.springframework.util.AntPathMatcher` 语义存储，实际匹配逻辑本身也留给运行时端点
  那次 change 实现——本次只存储规则列表，不执行匹配。

## Decisions

### 1. 数据表：`tab_app_auth_config`

与 `tab_app_config` 同构（一对一、随应用创建自动生成默认行、审计字段齐全）：

```sql
CREATE TABLE IF NOT EXISTS `tab_app_auth_config` (
    `id`                          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `app_id`                      BIGINT      NOT NULL COMMENT '所属应用 id，关联 tab_app.id，一对一唯一',
    `auth_protocol`               VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT '单点登录协议类型：NONE/CAS/OAUTH2',
    `cas_service_patterns`        TEXT                 DEFAULT NULL COMMENT 'CAS service 参数 ANT 匹配规则列表，JSON 字符串数组，auth_protocol=NONE 时为空',
    `oauth2_redirect_uri_patterns` TEXT                DEFAULT NULL COMMENT 'OAuth2 redirect_uri ANT 匹配规则列表，JSON 字符串数组，auth_protocol=NONE 时为空',
    `create_by`                   VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`                 DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`                   VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`                 DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_app_auth_config_app_id` (`app_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '应用单点登录协议配置表，与 tab_app 一对一，仅存协议类型与回跳地址匹配规则，不含运行时票据/令牌数据';
```

列名核对 MySQL/PostgreSQL/Oracle/SQL Server 保留字：`auth_protocol`/
`cas_service_patterns`/`oauth2_redirect_uri_patterns` 均非保留字。直接并入
`V1__init_schema.sql`（原因见 proposal.md Impact），不新开 `V2`。

匹配规则列表用单个 `TEXT` 列存 JSON 字符串数组（对齐 `tab_app_config.notify_params` 存
JSON 文本、读写时用 `JacksonUtils` 与 Java 集合互转的既有模式），不另建关联表——规则条数
少（预期个位数到十几条），不需要按规则单条查询/索引。

### 2. 后端模块划分

新增包 `cn.nihility.rbac.app.authconfig`，结构对齐 `cn.nihility.rbac.app.sync`：

- `constant/AuthProtocol`：`NONE`/`CAS`/`OAUTH2` 字符串常量（同 `SyncMode`/`SignAlgorithm`
  的写法，落库可读字符串而非整型码值）。
- `entity/AppAuthConfigEntity`：对应 `tab_app_auth_config`，`appRefId` 映射 `app_id` 列
  （沿用 `AppConfigEntity` 的 `appRefId` 命名，`app_id`/`appId` 已被 `AppConfigEntity` 用于
  对外 AppId 语义，这里延续同一惯例避免误用）。
- `mapper/AppAuthConfigMapper extends BaseMapper<AppAuthConfigEntity>`。
- `dto/AppAuthConfigVO`：`authProtocol`、`casServicePatterns: List<String>`、
  `oauth2RedirectUriPatterns: List<String>`，以及 6 个只读计算字段——
  `casLoginUrl`/`casServiceValidateUrl`/`casLogoutUrl`/`oauthAuthorizeUrl`/
  `oauthTokenUrl`/`oauthUserInfoUrl`，无论 `authProtocol` 取值为何都一并返回（Requirement
  "查询应用认证配置"），方便管理员提前查看/复制协议接口地址。
- `dto/AppAuthConfigUpdateRequest`：`authProtocol`（`@NotBlank`）、
  `casServicePatterns: List<String>`、`oauth2RedirectUriPatterns: List<String>`（均可为
  空列表，具体是否允许空由 service 层按 `authProtocol` 条件校验，见 Decision 3）。
- `mapstruct/AppAuthConfigConvert`：entity → VO 的 `authProtocol` 字段直接映射，两个
  `List<String>` 字段与只读计算字段手动在 service 层填充（同 `AppConfigServiceImpl.toVO`
  对 `notifyParams` 的处理方式，MapStruct 不做 JSON 解析）。
- `service/AppAuthConfigService` + `impl/AppAuthConfigServiceImpl`：
  `createDefaultConfig(appRefId, operator)`、`getByAppId(appRefId)`、
  `updateConfig(appRefId, request)`。
- `controller/AppAuthConfigController`：
  - `GET /api/apps/{id}/config/auth` — 查询，不做管辖组织范围校验（对齐
    `AppConfigController.getConfig` 现状，读操作不做范围过滤）。路径实现时改为
    `config/auth`（而非本节最初拟定的 `auth-config`），对齐同级 `AppSyncConfigController`
    已用的 `/api/apps/{id}/config/sync/**` 路径前缀风格，属于实现阶段的路径命名调整，
    不影响接口语义。
  - `PUT /api/apps/{id}/config/auth` — 修改，复用 `AppScopeGuard.getExistingAppInScope`
    做管辖组织范围校验（对齐 `AppConfigServiceImpl` 三个写操作的现状）。

`AppConfigServiceImpl.createDefaultConfig` 内新增一行
`appAuthConfigService.createDefaultConfig(appRefId, operator)`（注入
`AppAuthConfigService`），与现有 `appSyncConfigService.createDefaultDomainConfigs(...)`
调用并列，保持"应用创建时一次性把所有配置类子表默认行建好"的既有结构。

### 3. 协议类型与匹配列表的校验规则

在 `AppAuthConfigServiceImpl.updateConfig` 内做条件校验（不是简单 Bean Validation 能表达
的跨字段规则）：

```text
if authProtocol == CAS:
    casServicePatterns 去重去空白后 SHALL 非空，否则拒绝
    oauth2RedirectUriPatterns 存空列表（不保留旧值）
elif authProtocol == OAUTH2:
    oauth2RedirectUriPatterns 去重去空白后 SHALL 非空，否则拒绝
    casServicePatterns 存空列表（不保留旧值）
elif authProtocol == NONE:
    两个列表均存空列表
```

匹配规则本身只做"非空白字符串 + trim + 去重"层面的校验，不做 ANT 语法合法性校验——
`AntPathMatcher` 对任意字符串都能构造匹配器，没有"编译失败"的概念，真正的匹配行为验证
留给运行时端点那次 change（写测试用例覆盖具体匹配场景）。

### 4. 协议接口地址的计算方式

6 个只读地址在后端 `AppAuthConfigServiceImpl` 里用应用的 `appId`（`tab_app_config.open_app_id`
值，通过 `AppConfigMapper` 按 `appRefId` 查一次）拼接固定路径模板得到，不落库、每次查询
实时计算：

```text
casLoginUrl           = {baseUrl}/api/authn/cas/{appId}/login
casServiceValidateUrl = {baseUrl}/api/authn/cas/{appId}/p3/serviceValidate
casLogoutUrl           = {baseUrl}/api/authn/cas/{appId}/logout
oauthAuthorizeUrl      = {baseUrl}/api/authn/oauth/authorize
oauthTokenUrl          = {baseUrl}/api/authn/oauth/token
oauthUserInfoUrl        = {baseUrl}/api/authn/oauth/userinfo
```

`baseUrl` 暂不做服务端拼接（不同环境域名不同，且当前项目 `application.yml` 未配置
`server.servlet.context-path`/公网域名），只返回路径部分，前端展示时按当前页面 origin
拼接为完整 URL（同 `frontend/src/api/request.ts` 里 `/api` 反向代理到后端的既有约定）。

> **需要用户确认的一处偏差**：用户原始需求里 OAuth2 的"获取用户信息"接口路径写的是
> `/api/authn/oauth/token`，与"Access Token Request接口"路径完全相同，两个不同用途的接口
> 不应共用同一路径，判断是笔误。本设计按惯例把用户信息接口改为独立路径
> `/api/authn/oauth/userinfo`。这两个地址在本次改动里只是静态展示文本，不影响任何运行时
> 行为（运行时端点是下一个 change 的范围），如果实际期望的路径不同，届时实现运行时端点的
> change 里可以直接改，不影响本次的表结构或接口契约。

### 5. 权限点

新增 `AppManagement:app:config:editAuth`（修改认证管理配置：协议类型、CAS service 匹配
列表、OAuth2 redirect_uri 匹配列表），同步写入 `权限资源.txt`。查看沿用已有的
`AppManagement:app:config`（应用配置页面访问），不新增单独的查看权限点（与"同步配置"
tab 的权限点粒度一致——`editSync` 只控制编辑，查看靠页面访问权限点）。

### 6. 前端交互结构

`AppConfigView.vue`：
- `activeTab` 类型从 `'basic' | 'sync'` 扩为 `'basic' | 'sync' | 'auth'`，新增
  `<el-tab-pane label="认证管理" name="auth">`。
- 协议下拉：`el-select`（三选一：无/CAS/OAuth2.0），对齐现有 `el-radio-group` 风格改用
  `el-select` 是因为选项含"无"这个默认态，三选项用下拉比单选组更贴合用户需求原文"下拉
  选择"的表述。
- 匹配规则列表：复用 `syncForm` 区块里 `notifyParamRows` 的"行数组 + 添加/删除按钮"交互
  模式（`el-input` 单值输入，非 key-value 两列），协议为 CAS 时渲染 CAS 规则行，协议为
  OAuth2.0 时渲染 OAuth2 规则行，协议为"无"时两个列表区域都不展示。
- 协议接口地址：只读展示区块（`el-descriptions` 或简单 `div` 列表 + 复制按钮，参照"基础
  信息" tab 里 AppId/AccessKey 行的"文本 + 复制按钮"样式），CAS 3 条 / OAuth2 3 条按当前
  选中协议展示对应分组；OAuth2 授权接口下方附带参数说明表格（`response_type`/`client_id`/
  `redirect_uri`/`scope`/`state` 五个参数的名称/是否必选/说明，纯静态文案，来自用户需求
  原文，不依赖接口返回）。
- 保存按钮受 `hasPermission('AppManagement:app:config:editAuth')` 控制，未持有该权限点
  时不渲染（对齐 Requirement "认证管理配置的访问控制"）。
- `getAppAuthConfig`/`updateAppAuthConfig` 两个请求封装实现时并入既有
  `frontend/src/api/app.ts`（而非本节最初拟定的独立 `appAuthConfig.ts`），对齐该文件
  已把同步配置/字段映射/同步范围等全部应用相关接口集中一处、按注释分区而非按文件拆分
  的既有组织方式；类型定义并入 `frontend/src/types/app.ts`，不新开文件——该文件已集中
  放置应用相关的各类 VO/请求类型，`AppAuthConfigVO`/`AppAuthConfigUpdateRequest`/
  `AuthProtocol` 加进去
  即可，避免类型定义分散）。

## Risks / Trade-offs

- **[Risk] 本次只做配置存储，管理员可能误以为保存后 SSO 立即生效** → **Mitigation**：
  前端"认证管理" tab 顶部加一条 `el-alert` 提示"当前仅支持协议配置维护，协议运行时接口
  尚未开放"，避免误导。
- **[Risk] 匹配规则不做 ANT 语法校验，管理员可能存入一个实际无法匹配任何地址的规则
  （如打错字符）** → **Mitigation**：运行时端点那次 change 上线前，匹配规则本来就无法
  验证是否"实际有效"（要等真实回跳请求才知道），这是 SSO 配置类功能的通用限制，不在本次
  解决范围；后续可以考虑加一个"测试匹配"辅助工具，留作后续增强，不阻塞本次。

## Migration Plan

- `tab_app_auth_config` 是新表，`V1__init_schema.sql` 本身不做任何应用类种子数据（见
  `tab_app_sync_domain_config` 表注释"应用类数据是运行时业务数据，不通过 Flyway 种子"），
  一个全新初始化的数据库里 `tab_app` 也没有任何行，所以不存在"存量应用需要补默认认证配置
  行"的问题。经检索确认 `tab_app_config`/`tab_app_sync_domain_config` 在 `V1` 里同样没有
  对应的批量补数据 `INSERT ... SELECT`（此前设想的"沿用既有批量补数据写法"并不存在，已
  核实修正）。
- 但如果是已经跑过旧版 `V1` 的开发库（`tab_app` 里已有历史应用数据），加完
  `tab_app_auth_config` 表后再查询这些应用的认证配置会查不到行。为此
  `AppAuthConfigServiceImpl.getByAppId` 在查不到记录时 SHALL 懒创建一条默认配置行（协议
  "无"、两个列表为空）后再返回，而不是直接抛"应用不存在"（这里与 `AppConfigServiceImpl`
  的行为特意不同——后者理论上不可能查不到，因为 `AppConfigEntity` 和 `AppEntity` 从项目
  最初就是同一个事务里一起插入的老表；`tab_app_auth_config` 是本次新加的表，需要兼容"表
  比应用数据新"这一现实情况）。
- 无需回滚脚本（新表 + 无种子数据，回滚即整体回退本次 change 的代码与迁移）。
