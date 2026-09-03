## Why

应用配置页"认证管理"标签页里"允许的登录认证方式"这一表单项存在两个问题：
1. 文案偏长，且当前实现里口令登录（`PASSWORD`）固定勾选、禁止取消（`SSO_LOGIN_METHOD_OPTIONS`
   中 `disabled: true`，后端 `AppAuthConfigServiceImpl#normalizeLoginMethods` 也会强制补齐），
   管理员无法为某个应用彻底关闭口令登录入口，只能强制保留。
2. 业务上希望简化文案为"认证方式配置"，并把口令登录从"强制必选"放宽为"默认勾选、可以
   取消"，让管理员按需为个别应用只保留短信/扫码登录。

## What Changes

- 前端 `AppConfigView.vue`：
  - 表单项 label 由"允许的登录认证方式"改为"认证方式配置"。
  - `SSO_LOGIN_METHOD_OPTIONS`（`src/types/app.ts`）中 `PASSWORD` 选项去掉 `disabled: true`，
    默认值仍为勾选状态；hint 文案同步调整为"认证方式可以不配置，未配置时默认使用口令登录"，
    不再声称"口令登录固定必选，不可取消"。
  - 保存时允许提交空数组（即不配置任何认证方式），不做"至少一项"的强制校验——未配置时
    由后端查询侧回退为默认口令登录（见下）。
- 前端 `SsoLoginView.vue`：
  - 当前 `loginMethods.length <= 1` 分支硬编码按口令登录表单渲染，需要改为按实际返回的
    唯一认证方式渲染对应表单（唯一方式可能是 `PASSWORD`/`SMS`/`QRCODE` 其中之一）。
- 后端 `AppAuthConfigServiceImpl`：
  - `normalizeLoginMethods` 不再强制补齐 `PASSWORD`，也不拒绝空列表；改为仅做取值范围
    校验（`PASSWORD`/`SMS`/`QRCODE` 子集）与去重，结果可以是空列表（代表"未配置"）。
  - 新建应用的默认认证配置初始化值不变，仍为 `["PASSWORD"]`。
  - 查询侧回退逻辑（`parseLoginMethods`：JSON 为空、解析结果为 `null`/空列表时统一返回
    `["PASSWORD"]`）本身已经实现"未配置时默认口令登录"的语义，无需新增代码——保存空列表
    后再次查询会自然表现为"默认口令登录"，前端勾选框会显示口令登录被勾选。
- 后端登录方式查询链路（`SsoLoginContextResolver` / `GET /api/authn/sso/login-methods`）：
  - 已解析出具体应用时，直接透传该应用配置的 `loginMethods`，不再假定恒含 `PASSWORD`。
  - `redirect` 缺失、无法解析出应用、或应用查不到认证配置时的保守退化仍固定返回
    `["PASSWORD"]`，不受本次变更影响（这些场景下还没有解析出具体应用，无法参考其配置）。
- 同步更新 `openspec/specs/app-auth-protocol-config/spec.md`、
  `openspec/specs/sso-login-methods/spec.md` 中所有基于"`PASSWORD` 恒定包含"描述的
  Requirement/Scenario。

## Capabilities

### Modified Capabilities
- `app-auth-protocol-config`：认证配置的 `loginMethods` 字段不再强制包含 `PASSWORD`，允许
  提交空列表代表"未配置"；未配置（含从未修改过、以及显式保存为空列表）时查询统一返回
  `["PASSWORD"]`，即默认使用口令登录。移除"提交不含口令时自动补齐"的 Scenario，新增
  "显式保存为空列表时按未配置处理，查询回退为默认口令登录"的 Scenario。
- `sso-login-methods`：登录方式查询接口在已解析出应用时不再恒定包含 `PASSWORD`；SSO 登录页
  按返回列表展示标签页的行为需要覆盖"唯一方式非口令"的场景。

## Impact

- 前端：`views/application/app/AppConfigView.vue`、`views/sso/SsoLoginView.vue`、
  `types/app.ts`（`SSO_LOGIN_METHOD_OPTIONS`）。
- 后端：`app/authconfig/service/impl/AppAuthConfigServiceImpl.java`（校验逻辑）；
  `sso/support/SsoLoginContextResolver.java` 与登录方式查询接口涉及的返回逻辑已确认是
  "直接透传"实现，本次无需改动，仅需更新 spec 描述。
- 风险点：允许取消口令登录后，管理员可以为某个应用只保留短信/扫码；由于"未配置/全部取消"
  会自动回退为默认口令登录，应用不会出现"彻底无法登录"的情况，只有管理员显式勾选了非空的
  `["SMS"]`/`["QRCODE"]` 等不含口令的组合时，该应用才会真正只保留对应方式。管理端后台
  登录页（`LoginView.vue`）固定口令，不受本变更影响。
- 无破坏性变更：未修改过认证方式配置的存量应用行为不变（仍默认仅口令登录）；管理端直接
  登录页不受影响。
