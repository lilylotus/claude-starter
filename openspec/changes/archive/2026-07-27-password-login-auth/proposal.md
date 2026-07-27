## Why

当前系统的登录能力只是前端本地占位实现（`src/api/auth.ts` 里 `setTimeout` 模拟
`admin/admin123`，`src/stores/auth.ts` 只维护单一 token），后端完全没有登录、密码、
身份态相关接口。RBAC 系统的身份管理（组织 → 用户 → 角色 → 权限）目前只有静态数据维护
能力，缺少真实的"谁在操作"这一层身份认证与请求鉴权基础设施，导致所有业务接口目前
无法真正区分调用者身份、也无法按操作资源（menu key）做访问控制。需要补齐一套口令
（账号+密码）登录接口，把前端占位实现替换为对接真实后端的认证体系。

## What Changes

- 新增密码表 `tab_user_password`，密码使用 `SHA-256(password, salt)` 摘要保存，独立盐值
  字段；新增用户时自动创建默认密码记录（默认密码 `Default#123456`），并标记首登
  （`first_login`）为真。
- 新增登录接口：账号、密码由前端使用 RSA 公钥加密后提交，后端用 RSA 私钥解密后校验；
  RSA 公私钥默认配置通过 `@ConfigurationProperties(prefix = "rbac.user.login")` 提供。
- 登录成功后签发 access-key / refresh-key（均为不带横线的 UUID 字符串），保存到
  Redis（`user:identity-token:<用户ID>`），access-key 默认有效期 2 小时；新增
  刷新接口，用 refresh-key 换取新的 access-key。
- 新增统一的请求身份校验层：业务接口要求携带 `identity-token`（access-key）请求头，
  校验 Redis 中的会话是否存在/未过期；未登录或校验失败的请求统一拦截为未登录错误。
- 首登强制改密：登录成功后若该用户 `first_login` 为真，除登录、刷新、修改密码接口
  外的业务接口一律拦截返回业务错误，直到用户完成改密。
- 新增修改密码接口，改密成功后清除 `first_login` 标记。
- 用户管理页面新增"重置密码"操作：管理员可将指定用户密码重置为默认密码
  `Default#123456` 并将其 `first_login` 标记重新置为待改密，该用户此后任意业务
  请求都会被首登拦截，直到完成改密。
- 前端：接入真实登录/刷新/改密接口，账号密码 RSA 加密后提交；`stores/auth.ts` 改为
  维护 access-key/refresh-key 双 token；`api/request.ts` 拦截器改为携带
  `identity-token` 与 `menu`（复用路由 `meta.permissionKey` / 权限资源编码）请求头，
  access-key 失效时用 refresh-key 静默刷新后重试原请求；路由守卫在未登录时重定向登录页，
  在首登标记为真时重定向强制改密页面。
- **BREAKING**：`src/stores/auth.ts` 的 `token` 字段替换为 access-key/refresh-key 双
  字段，依赖旧 `token` 字段的代码需要同步调整。
- 只有 `status = 2000`（启用）的用户可以登录成功，停用（`3000`）、已删除（`-1000`）
  账号一律返回统一的登录失败错误（不区分具体原因）；这一限制在最初的登录实现里就已
  按 spec.md"账号已停用或已删除时登录失败"落地并有测试覆盖，本条只是显式记录确认。
- 新增 Flyway 迁移 `V3__seed_default_admin_user.sql`，初始化默认管理登录账号
  `admin`/`admin`（`status=2000`、`first_login=1`），用于解决新装环境在
  `IdentityAuthFilter` 强制身份校验落地后"没有任何用户就无法通过接口创建第一个
  用户"的引导问题；该账号首次登录后会被强制要求修改密码。

## Capabilities

### New Capabilities
- `password-login-auth`：口令登录认证能力，覆盖账号密码 RSA 加解密、密码摘要存储与
  首登强制改密、access-key/refresh-key 签发与刷新、`identity-token` + `menu` 请求头
  的请求身份/资源标识校验，以及配套前端登录/改密/token 刷新交互。

### Modified Capabilities
- `user-management`：新增"重置密码"管理员操作需求（用户管理页面新增按钮 + 后端
  重置接口），属于新增需求条款，通过该 spec 的 delta 文件以 `ADDED Requirements`
  形式追加；"新增用户"自动创建默认密码属实现细节，不改变该 spec 既有需求条款，
  不在此列。

## Impact

- 后端新增模块 `cn.nihility.rbac.auth`（config/constant/context/controller/dto/entity/
  filter/mapper/service/service.impl/util，按仓库既有分层约定；未新增 `mapstruct` 包，
  因为 `UserPasswordEntity` 从不对外暴露为 VO，没有需要转换的场景），新增 Flyway 迁移
  脚本 `V2__add_user_password_table.sql`（新增 `tab_user_password` 表，含首登标识字段；
  不改动既有 `tab_user` 表结构）、`V3__seed_default_admin_user.sql`（初始化默认管理
  登录账号 `admin`/`admin`）与 `V4__add_user_reset_password_menu.sql`（把
  `UserManagement:user:resetPassword` 补进 `tab_menu` 菜单管理资源树，此前只登记在
  `权限资源.txt` 里，菜单管理页面看不到、角色管理也无法分配这个按钮）。
- 后端新增全局请求拦截层（`identity-token` 校验 + 首登拦截），影响除登录/刷新/改密
  外的所有现有业务接口的调用前置条件（需要携带 `identity-token`）。
- 后端 `user-management` 模块的"新增用户"服务逻辑联动创建默认密码记录，属实现细节。
- 后端 `user-management` 模块新增"重置密码"接口（`UserController` 新增端点，调用
  `auth` 模块 `PasswordService` 的重置能力），前端用户管理页面新增对应按钮。
- 前端 `src/api/auth.ts`、`src/stores/auth.ts`、`src/api/request.ts`、
  `src/router/index.ts` 改造；新增改密页面；用户管理列表页新增"重置密码"按钮；同时
  修复了 `router/index.ts` 中 10 个详情路由（`identity/orgs/:id` 等 `detailRoutes`）
  此前缺失 `meta.permissionKey` 的既有代码问题——本次新增的 `IdentityAuthFilter`
  强制要求所有业务请求携带 `menu` 头后，这一遗漏会导致这些页面自身发起的接口调用被
  拒绝，属于本次改动范围内需要一并处理的前端回归。
- 根目录 `权限资源.txt` 新增用户管理"重置密码"按钮对应的资源编码；上述 10 个详情
  路由用到的 `:detail` 编码本来就已在文件中登记，本次只是把前端路由接上，未新增
  文件条目。
- 依赖：后端需要 RSA 加解密与 UUID 生成能力（JDK 自带，无需新增第三方库）；Redis 已
  在 `build.gradle`/`application.yml` 就绪，无需新增依赖。
