## Context

`app-auth-protocol-config` 能力当前把"应用允许的登录认证方式"（`loginMethods`）设计成
"`PASSWORD` 恒定包含"：前端 `SSO_LOGIN_METHOD_OPTIONS` 里 `PASSWORD` 选项 `disabled: true`
禁止取消勾选，后端 `AppAuthConfigServiceImpl#normalizeLoginMethods` 无论提交内容如何都会
强制补齐 `PASSWORD`。本次变更要把这条约束放宽为"默认勾选口令登录，但允许显式取消"，同时把
表单项文案从"允许的登录认证方式"简化为"认证方式配置"。

已确认的既有实现细节（决定了改动范围）：
- `SsoLoginContextResolver#resolve` 在成功解析出应用配置时，是**直接透传**该应用
  `loginMethods` 原文（`parseLoginMethods` 仅在 JSON 为空/无法解析时才退化为
  `PASSWORD_ONLY`），并没有额外强制补齐 `PASSWORD`。也就是说登录方式查询接口
  `GET /api/authn/sso/login-methods` 已经具备"如实返回应用配置"的能力，本次不需要改动
  这一层，只需要放开上游（`AppAuthConfigServiceImpl`）不再强制写入 `PASSWORD`。
- `redirect` 缺失、无法解析出应用、或应用查不到认证配置这三种"解析失败"场景，固定返回
  `["PASSWORD"]` 的保守退化逻辑不受影响、维持不变（这些场景下根本没有解析出具体应用，无法
  参考其配置，继续保守展示口令登录表单是合理的兜底）。
- 前端 `SsoLoginView.vue` 目前的"仅一种认证方式时不展示 Tab，直接渲染口令表单"分支
  （`loginMethods.length <= 1`）隐含假设了唯一方式必为 `PASSWORD`，如果不修正，当某应用
  只剩 `SMS` 或只剩 `QRCODE` 一种方式时会错误地渲染出口令登录表单。

## Decisions

### Decision 1：后端不再强制补齐 PASSWORD，允许保存为空（代表未配置）

`AppAuthConfigServiceImpl#normalizeLoginMethods` 去掉 `result.add(LoginMethod.PASSWORD)`
这一强制写入，改为：
1. 对提交的每一项做取值范围校验（`PASSWORD`/`SMS`/`QRCODE`），非法值仍直接拒绝（不变）。
2. trim + 去重（不变）。
3. **不再校验"至少一项"**——结果集合允许为空，空列表原样落库，代表"该应用未单独配置认证
   方式"。

查询侧 `parseLoginMethods`（`AppAuthConfigServiceImpl` 与 `SsoLoginContextResolver` 中各
一份同名逻辑）本身已经实现"JSON 为空、或解析出的列表为 `null`/空列表时统一返回
`List.of(PASSWORD)`"，这条回退逻辑**不需要改动**，直接复用即可同时覆盖两种"未配置"场景：
- 从未保存过 `loginMethods`（历史存量数据，列值为空字符串）。
- 显式保存过一次空列表（管理员把所有勾选框都取消后保存，`login_methods` 列值变成 `"[]"`）。

两种情况在查询时都会被 `parseLoginMethods` 归一为 `["PASSWORD"]`，语义上完全等价于"未配置
时默认使用口令登录"，无需分别处理、也无需新增专门的"是否已配置过"标记字段。

新建应用的默认认证配置初始化值不变，仍是 `["PASSWORD"]`（`AppAuthConfigServiceImpl` 里
`.loginMethods(JacksonUtils.toJson(List.of(LoginMethod.PASSWORD)))` 那一行不动）——只是
默认展示值，管理员之后可以显式清空。

**效果**：应用不会再出现"彻底无法登录"的状态——只要管理员没有显式勾选任何非口令方式，
保存空列表和保存 `["PASSWORD"]` 在查询结果上完全等价，都会展示为口令登录可用。只有管理员
明确勾选了 `["SMS"]`/`["QRCODE"]` 等不含 `PASSWORD` 的非空组合时，该应用才会真正只保留
对应方式、不再展示口令登录入口，这是管理员的主动选择，不属于需要拦截的异常输入。

### Decision 2：前端表单项文案与勾选交互调整

- `src/types/app.ts` 的 `SSO_LOGIN_METHOD_OPTIONS` 中 `PASSWORD` 选项去掉
  `disabled: true`，默认勾选状态由 `loginMethods` 初始值 `['PASSWORD']` 保证（不变）。
- `AppConfigView.vue` 表单项 `label` 由"允许的登录认证方式"改为"认证方式配置"；hint 文案
  由"口令登录固定必选，不可取消"改为"认证方式可以不配置，未配置时默认使用口令登录"。
- `saveAuthConfig`（第 622-643 行）里第 634-636 行"口令恒定必选，兜底补齐"的逻辑直接删除，
  `loginMethods` 按勾选框当前值原样提交（可以是空数组），不再做前端侧的"至少一项"拦截。

### Decision 3：SSO 登录页"唯一方式"分支不再假定是口令

`SsoLoginView.vue` 第 400 行 `v-if="loginMethods.length <= 1"`（无 Tab、直接渲染口令表单
的分支）改为 `v-if="loginMethods.length <= 1 && loginMethods.includes('PASSWORD')"`；
不满足这个条件时统一走 `el-tabs` 分支——`el-tabs` 分支里三个 `el-tab-pane` 已经各自按
`loginMethods.includes(...)` 独立控制显隐，唯一方式是 `SMS` 或 `QRCODE` 时会自然渲染成
"只有一个 Tab 页"的样子，不需要再新增专门的"单一非口令方式"无 Tab 样式（保持改动最小，
El-Plus 单 Tab 的展示效果可接受，不引入额外分支）。

第 506 行 hint 文案的展示条件 `loginMethods.length <= 1 || activeTab === 'PASSWORD'`
同步改为 `(loginMethods.length <= 1 && loginMethods.includes('PASSWORD')) ||
activeTab === 'PASSWORD'`，避免唯一方式是短信/扫码时错误展示"用户名为分配的用户编码"这条
仅适用于口令登录的提示。

## Risks / Trade-offs

- **管理员可能为某应用主动只保留短信/扫码、不含口令**：这是本次变更明确要放开的行为，
  不是缺陷；由于"未配置/全部取消"会自动回退为默认口令登录，应用不会出现"彻底无法登录"
  的情况，只有管理员显式勾选了不含 `PASSWORD` 的非空组合（如仅 `["SMS"]`）时，该应用
  才会真正只保留对应方式，这是管理员的主动选择。管理端后台登录页（`LoginView.vue`）固定
  使用口令、完全独立于本配置，不受影响。
- 相比上一版方案（强制"至少一项"校验），本版进一步降低了误操作风险：管理员即便手滑清空
  了所有勾选框，保存后也只是回退为默认口令登录，而不是被拒绝或需要额外提示。

## Migration Plan

不涉及数据库结构变更，`tab_app_auth_config.login_methods` 列定义不变；仅调整业务校验逻辑
与前端交互，无需数据迁移脚本。存量数据行为：
- 未配置过 `loginMethods` 的应用（列值为空）：查询仍回退为 `["PASSWORD"]`，行为不变。
- 已配置过 `loginMethods` 的应用（历次保存时都被强制补过 `PASSWORD`）：现有数据里必然
  包含 `PASSWORD`，行为不变，直到管理员下一次主动编辑并取消勾选（含清空为空列表）。
