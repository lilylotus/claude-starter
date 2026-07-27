## 1. 数据库迁移

- [x] 1.1 新增 `backend/src/main/resources/db/migration/V2__add_user_password_table.sql`：创建 `tab_user_password` 表（`id`、`user_id`、`password_digest`、`salt`、`first_login`、审计字段 `create_by`/`create_time`/`update_by`/`update_time`），`user_id` 唯一索引（文件名与确认阶段最终结论 `V2__add_user_password_table.sql` 保持一致，而非本文件早先草案里的 `V2__add_password_table.sql`）
- [x] 1.2 核对 `password_digest`/`salt`/`first_login` 等列名与 MySQL/PostgreSQL/Oracle/SQL Server 保留字无冲突
- [x] 1.3（后补）新增 `backend/src/main/resources/db/migration/V3__seed_default_admin_user.sql`：初始化默认管理登录用户 `admin`/`admin`（`tab_user.status=2000`，`tab_user_password.first_login=1`），解决新装环境在有 `IdentityAuthFilter` 强制身份校验之后"没有任何用户就无法通过接口创建第一个用户"的引导问题；密码摘要用 MySQL `SHA2()` 现算，算法与 `PasswordDigestUtils#digest` 一致，已用独立 Python 脚本交叉验证摘要值完全相等

## 2. 后端：RSA 配置与密码摘要工具

- [x] 2.1 在 `cn.nihility.rbac.auth.config`（或等价包）新增 `@ConfigurationProperties(prefix = "rbac.user.login")` 配置类，绑定 `publicKey`/`privateKey`（Base64）、`accessTokenExpireSeconds`（默认 7200）、`refreshTokenExpireSeconds`（默认 604800）
- [x] 2.2 用 `RsaJdkUtils.generateKeyPair()` 生成一套本地开发用 RSA-2048 密钥对，写入 `application.yml` 默认值（附注释说明生产环境需替换）；登录接口解密直接复用 `cn.nihility.rbac.common.util.RsaJdkUtils.decrypt(...)`，不新增 RSA 工具类
- [x] 2.3 新增密码摘要工具类（`SecureRandom` 生成盐值，`SHA-256(password + salt)` 摘要，Hex 编码）——`cn.nihility.rbac.auth.util.PasswordDigestUtils`

## 3. 后端：密码表与用户创建联动

- [x] 3.1 新增 `cn.nihility.rbac.auth` 模块基础结构（`entity`/`mapper`/`service`/`service.impl`/`dto`/`constant`/`config`/`context`/`controller`/`filter`/`util`），新增 `UserPasswordEntity`、`UserPasswordMapper`（未新增 `mapstruct` 包：密码记录不对外暴露为 VO，没有需要 MapStruct 转换的 entity↔DTO 场景，强行新增一个空/未使用的转换接口没有意义，详见完成情况说明）
- [x] 3.2 新增 `PasswordService`：`createDefaultPassword(userId)`（写入默认密码 `Default#123456` 摘要 + `first_login=1`）、`resetToDefault(userId)`（复用同一份默认密码摘要生成逻辑，将已存在的密码记录重置为默认密码并重新置 `first_login=1`）、`verifyPassword(userId, plainPassword)`、`updatePassword(userId, newPlainPassword)`（更新摘要/盐值并清除 `first_login`）、`isFirstLogin(userId)`
- [x] 3.3 在用户管理"新增用户"服务流程中调用 `createDefaultPassword`，创建用户与创建默认密码记录需在同一事务内完成（`UserServiceImpl.create` 标注 `@Transactional`）
- [x] 3.4 补充/调整用户新增相关的单元测试或集成测试，覆盖"创建用户后存在对应密码记录且 `first_login=1`"

## 4. 后端：Redis 会话存储

- [x] 4.1 新增 `TokenService`（或等价命名）封装 Redis 会话读写：签发新会话（写 `user:identity-token:<userId>` Hash + `user:access-token:<accessKey>`/`user:refresh-token:<refreshKey>` 反查记录，各自设置 TTL）、校验 access-key（反查 userId 后回读 Hash 校验 `accessKey` 一致且未过期）、按 refresh-key 刷新（校验有效性后生成新 access-key，更新 Hash 与反查记录）
- [x] 4.2 access-key/refresh-key 均使用不含横线的 UUID 字符串（`UUID.randomUUID().toString().replace("-", "")`）

## 5. 后端：登录/刷新/公钥/改密接口

- [x] 5.1 新增 `AuthController`：`GET /api/auth/public-key`、`POST /api/auth/login`、`POST /api/auth/refresh`、`POST /api/auth/password`（改密），加上 springdoc `@Tag`/`@Operation` 注解
- [x] 5.2 新增对应请求/响应 DTO（`jakarta.validation` 注解），登录 DTO 含加密后的账号、密码密文字段
- [x] 5.3 实现登录逻辑：RSA 解密 → 按账号查用户（不存在/停用/已删除均返回统一的登录失败业务错误）→ 校验密码摘要 → 签发 access-key/refresh-key → 返回令牌与首登标识（复核确认：`AuthServiceImpl.login` 用 `Objects.equals(user.getStatus(), UserStatus.ENABLED)` 精确匹配 `2000`，非 `2000` 一律登录失败，`AuthServiceImplTest` 已有停用用户登录失败的用例覆盖）
- [x] 5.4 实现刷新逻辑：校验 refresh-key → 签发新 access-key，旧 access-key 立即失效
- [x] 5.5 实现改密逻辑：校验旧密码 → 更新摘要/盐值 → 清除首登标识

## 5a. 后端：用户管理重置密码接口

- [x] 5a.1 在既有 `UserController` 新增端点 `PUT /api/users/{id}/reset-password`，加上 springdoc `@Operation` 注解；不存在/已删除用户返回业务错误
- [x] 5a.2 `user` 模块 service 层注入并调用 `auth` 模块的 `PasswordService.resetToDefault(userId)`（`auth` 不反向依赖 `user`）
- [x] 5a.3（后补）新增 `backend/src/main/resources/db/migration/V4__add_user_reset_password_menu.sql`：在 `tab_menu` 里补上 `UserManagement:user:resetPassword` 按钮资源（挂在既有"用户管理"菜单节点下，`resource_type=2`，`show_order=15`，排在"停用"与"删除"之间，对应前端实际按钮顺序）。此前这个资源编码只写进了 `权限资源.txt`，没有同步进 `tab_menu` 种子数据，导致"菜单管理"页面看不到这个按钮、也无法在角色管理里把它分配给角色；已用真实本地 MySQL 验证迁移执行后 `tab_menu` 表里正确出现这一行且 `parent_id` 指向用户管理节点

## 6. 后端：请求身份校验过滤器

- [x] 6.1 新增 `IdentityAuthFilter`（`OncePerRequestFilter`）+ `FilterRegistrationBean` 注册，维护白名单路径（登录/刷新/公钥/改密/springdoc 相关路径）
- [x] 6.2 实现 `identity-token` 校验（缺失/过期/不一致 → 统一未登录业务错误码）与 `menu` 请求头格式校验（缺失/格式不合法 → 业务错误）
- [x] 6.3 实现首登强制改密拦截（`first_login=1` 时除白名单外一律拦截，返回专门的业务错误码）
- [x] 6.4 校验通过后将当前 `userId` 放入请求属性，供后续 controller/service 读取（同时通过 `CurrentUserContext` ThreadLocal 暴露，供改密/重置密码等 service 方法读取当前操作人）

## 7. 前端：RSA 加密与 API 封装

- [x] 7.1 新增 RSA-OAEP 加密工具函数（基于原生 `window.crypto.subtle`：`importKey('spki', ..., { name: 'RSA-OAEP', hash: 'SHA-256' }, ...)` + `encrypt`，输入 Base64 公钥与明文，输出 Base64 密文），不新增第三方加密依赖（`src/utils/rsa.ts`）
- [x] 7.2 重写 `src/api/auth.ts`：`getPublicKey()`、`login(form)`（先取公钥，RSA-OAEP 加密账号密码后调用登录接口）、`refresh(refreshKey)`、`changePassword(form)`
- [x] 7.3 更新 `src/types/auth.ts` 新增 `PublicKeyResult`/`LoginResult`/`RefreshResult`/`ChangePasswordForm`，去除与后端登录响应对不上的旧 `UserInfo` 类型

## 8. 前端：登录态存储与请求拦截

- [x] 8.1 重写 `src/stores/auth.ts`：维护 `accessKey`/`accessExpireAt`/`refreshKey`/`refreshExpireAt`/`firstLogin`（外加仅用于界面展示、取自登录表单的 `accountCode`），本地持久化（localStorage），提供 `login`/`refreshAccess`/`setFirstLogin`/`logout`/`isLoggedIn`/`isRefreshValid` 等
- [x] 8.2 更新 `src/api/request.ts` 请求拦截器：携带 `identity-token`（accessKey）与 `menu`（当前路由 `meta.permissionKey`）请求头（公钥/登录/刷新接口白名单豁免）
- [x] 8.3 更新响应拦截器：识别后端以 HTTP 200 + 业务 `code`（401/4010）承载的未登录/首登错误，`code=401` 时若本地 refresh-key 未过期，用单飞（single-flight）机制调用 `refresh` 换取新 accessKey 后重试原始请求，刷新失败/无有效 refresh-key 则清空会话并跳转登录页；`code=4010` 时置位本地 `firstLogin` 并跳转改密页面

## 9. 前端：路由与页面

- [x] 9.1 更新 `src/router/index.ts` 守卫：未登录重定向登录页（保留 `redirect` 参数）；首登待改密状态重定向强制改密页面且拦截其余业务页面导航（含已登录再次访问 `/login` 的情况）
- [x] 9.2 新增强制改密页面组件 `src/views/auth/ChangePasswordView.vue`（路由 `/change-password`，表单：旧密码、新密码、确认新密码，前端校验两次新密码一致），提交成功后清除首登状态并跳转概览页
- [x] 9.3 更新登录页组件对接真实 `login` 接口与错误提示，登录成功后按 `firstLogin` 决定跳改密页还是原目标页
- [x] 9.4 `src/api/user.ts` 新增 `resetPassword(id)` 调用 `PUT /api/users/{id}/reset-password`
- [x] 9.5 用户管理列表页（`UserManagementView.vue`）每行新增"重置密码"按钮，点击后二次确认弹窗，确认后调用重置接口并提示"已重置为默认密码"
- [x] 9.6 补充 10 个详情路由（`identity/orgs/:id` 等，`router/index.ts` 的 `detailRoutes`）缺失的 `meta.permissionKey`：这些页面本身发起的接口请求也是需要携带 `menu` 请求头的业务接口，缺失会被后端 `IdentityAuthFilter` 一律判定为业务错误；沿用各自列表页 `menu.ts` 里已有的 `permissionKey` 命名风格，取值与 `权限资源.txt` 里已经登记的对应 `:detail` 条目一一对应（字典类型/字典项详情用 `system:dict:typeDetail`/`system:dict:itemDetail` 区分）

## 10. 联调与验证

- [x] 10.1 后端：`./gradlew build` 全量通过（编译 + 单元测试 + `check`）
- [x] 10.2 前端：`npm run build`（vue-tsc 类型检查 + vite build）通过
- [x] 10.3 本地联调：启动真实后端（本机 MySQL/Redis 均可达），用 Python 脚本模拟浏览器端 RSA-OAEP 加密，对运行中的接口做了完整回合真实调用（非 mock）：`GET /api/auth/public-key` → 登录成功（`firstLogin=true`）→ 缺 `menu` 头返回 `401` → 缺 `identity-token` 返回 `401` → 首登状态下访问业务接口返回 `4010` → `POST /api/auth/password` 改密成功且清除首登标记 → 业务接口恢复可访问 → `POST /api/auth/refresh` 签发新 access-key 且旧 access-key 立即失效 → `PUT /api/users/{id}/reset-password` 重置为默认密码并重新置位首登标记 → 重置后旧密码登录失败、默认密码登录成功且 `firstLogin=true`。spec.md 里后端相关 Scenario 全部复核通过。**过程中发现并修复了一个真实缺陷**：`application.yml` 里 `rbac.user.login.private-key` 的默认值实际是 PKCS#1 格式 DER，而不是 `RsaJdkUtils.loadPrivateKey` 要求的 PKCS#8 格式，导致真实登录请求 100% 触发 `RSA解密失败` 被吞成"账号或密码不正确"——单元测试全部用内存现生成的密钥对，从未加载过这个默认配置值，所以没被发现。已重新生成一套校验过可用的 PKCS8/X.509 密钥对替换默认值，并新增 `RbacLoginPropertiesKeyPairTest`（`@SpringBootTest` 加载真实配置做加解密往返校验）防止同类问题再次被测试套件漏掉。前端登录页/改密页因缺少可用的本地 MySQL 种子用户数据，未做浏览器端点击验证，但接口契约已通过上述真实后端调用验证一致。
- [x] 10.4 更新根目录 `权限资源.txt`：已新增 `UserManagement:user:resetPassword`；另外发现并修复了一个前端遗漏——10 个详情路由（`identity/orgs/:id` 等）此前没有 `meta.permissionKey`，会导致这些页面发起的业务请求缺 `menu` 头被新加的 `IdentityAuthFilter` 拒绝，属于本次改动引入的功能性回归，已补齐（`权限资源.txt` 里对应的 `:detail` 条目本来就已登记，无需新增文件内容，只是前端路由没有接上）
