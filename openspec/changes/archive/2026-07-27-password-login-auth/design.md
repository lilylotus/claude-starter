## Context

当前后端完全没有认证相关代码，前端登录是本地占位实现。这是一个跨前后端、涉及新数据表、
新中间件（请求身份校验）、Redis 会话存储、RSA 加解密的变更，属于需要在编码前明确技术
决策的场景。约束：

- 后端沿用仓库既有分层约定（`constant/controller/dto/entity/mapper/mapstruct/service/
  service.impl`），全局响应包装 `{ code, message, data }` 与业务异常统一走 `common/`
  下已有的 `Result`/`BusinessException`/`GlobalExceptionHandler`。`auth` 模块实际未
  新增 `mapstruct` 包：`UserPasswordEntity` 从不对外暴露为 VO，没有需要转换的场景，
  按需省略，不是遗漏。
- Redis（`spring-boot-starter-data-redis`）、MySQL/Flyway、MyBatis-Plus 已就绪，不新增
  后端第三方依赖。
- 前端需要一个浏览器端 RSA 加密库（仓库目前没有），这是本次唯一新增的前端依赖。
- 表结构新增字段需要检查 MySQL/PostgreSQL/Oracle/SQL Server 保留字冲突（历史教训，
  见项目约定），且所有新表须有 `create_by/create_time/update_by/update_time` 审计字段。

## Goals / Non-Goals

**Goals:**
- 账号+密码的口令登录，密码全链路（传输 RSA 加密、存储 SHA-256+salt 摘要）不落明文。
- 首次登录（含新建用户产生的默认密码）强制走改密流程后才能访问其余业务接口。
- 基于 Redis 的 access-key/refresh-key 双令牌机制：access-key 短期（默认 2 小时）、
  refresh-key 用于静默换取新 access-key。
- 统一的请求身份校验层：业务接口必须携带有效 `identity-token`（= access-key），并携带
  `menu` 请求头标识本次操作对应的资源编码。
- 前端接入真实登录/刷新/改密流程，替换现有占位实现。

**Non-Goals:**
- **不**在本次实现基于 `menu` 请求头的角色-权限点授权判断（即"该用户的角色是否拥有
  这个 menu key 对应的权限"）。本次只要求：(a) 前端每个业务请求都携带该头，取自路由
  `meta.permissionKey`；(b) 后端身份校验层校验该头非空、格式合法（三段式）。真正按
  角色/权限点做访问控制是一个更大的独立能力（依赖 `permission-management`/
  `role-management` 已有数据 + 尚不存在的"角色-权限点"运行时鉴权引擎），留待后续
  change。**这是一个需要用户确认的范围裁剪，见 Open Questions。**
- 不做验证码、登录失败次数锁定、多端互踢/单点登录限制、密码有效期强制轮换、
  RSA 密钥轮换/多套密钥管理。
- 不做"记住我"/长期免登录。refresh-key 过期后一律要求重新走账号密码登录。

## Decisions

### 1. 密码表设计：`tab_user_password`，首登标识放在密码表而非 `tab_user`
独立表存放认证凭据（摘要、盐值、首登标识），不侵入既有 `tab_user` 表结构（避免
ALTER 现有表、避免与 `user-management` 模块的字段集/Excel 导入模板等产生耦合）。
`user_id` 唯一索引，一个用户对应一条当前有效密码记录（改密即 `UPDATE`，不保留历史）。

字段命名规避关键字：不用 `password` 做列名（MySQL 历史版本中 `PASSWORD()` 为保留
函数名，部分工具/未来 SQL 方言存在歧义风险），改用 `password_digest`；`salt` 在
MySQL/PostgreSQL/Oracle/SQL Server 中均非保留字，可直接使用。

```sql
CREATE TABLE tab_user_password (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    user_id          BIGINT      NOT NULL COMMENT '所属用户 id，关联 tab_user.id，唯一',
    password_digest  VARCHAR(64) NOT NULL COMMENT 'SHA-256(password, salt) 摘要，十六进制小写',
    salt              VARCHAR(32) NOT NULL COMMENT '摘要盐值，随机生成',
    first_login       TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '是否首次登录待强制改密：1=是，0=否',
    create_by / create_time / update_by / update_time  -- 审计字段
    PRIMARY KEY (id),
    UNIQUE KEY uk_tab_user_password_user_id (user_id)
);
```

### 2. 摘要算法：`SHA-256(password + salt)`
按需求明确指定，不引入 BCrypt/Argon2 等自适应哈希（后续如需增强可在独立 change 中
升级，不阻塞本次交付）。`salt` 用 `SecureRandom` 生成 16 字节，Hex 编码存储；摘要
`= SHA-256(明文密码 + salt)` 的十六进制小写字符串。

### 3. 令牌：UUID（去横线）+ Redis 双索引，而非 JWT
需求已明确要求 UUID 风格的 access-key/refresh-key（而非自包含的 JWT），因此服务端
必须靠 Redis 做状态存储和校验。设计三类 Redis 记录，Key 前缀均为 `user:`：

- `user:identity-token:<userId>` — Hash，字段 `accessKey`/`accessExpireAt`/
  `refreshKey`/`refreshExpireAt`（epoch millis）；TTL 设为 refresh 剩余秒数（跟随
  refresh-key 生命周期，到期整条记录连带清理，避免僵尸数据）。同一用户重新登录会
  整体覆盖该 Hash（当前策略允许同一用户多端同时登录/新登录不强制踢旧会话——见
  Non-Goals）。
- `user:access-token:<accessKey>` — String，值为 `userId`，TTL = access 剩余秒数。
  身份校验层用它做 O(1) 反查（token → userId），再回读上面的 Hash 做一致性校验。
- `user:refresh-token:<refreshKey>` — String，值为 `userId`，TTL = refresh 剩余
  秒数。刷新接口用它做反查。

**为什么身份校验还要回读 Hash 校验 `accessKey` 是否一致，而不是只信反查记录**：
刷新接口签发新 access-key 时只会覆盖 Hash 里的 `accessKey`/`accessExpireAt`
和写入新的 `user:access-token:<newKey>` 反查记录，不会主动删除旧 access-key 的
反查记录（旧记录会在自己的 TTL 到期后自然清理）。如果只信反查记录，旧 access-key
在其 TTL 剩余时间内仍会被判定"有效"。所以身份校验层必须在反查得到 `userId` 后，
再回读 Hash 确认 `hash.accessKey == 请求携带的 accessKey`，才能保证刷新后旧
access-key 立即失效，同时省去刷新时主动删除旧反查记录的一次额外 Redis 调用。

Access-key 默认有效期 2 小时、refresh-key 默认有效期 7 天，均做成可配置项（挂在
`rbac.user.login` 前缀下：`access-token-expire-seconds`、`refresh-token-expire-seconds`），
不写死在代码里。

### 3a. 登录账号字段：`tab_user.code`（用户编号）
`AuthServiceImpl` 登录时用解密后的明文账号按 `tab_user.code`（用户编号）精确匹配
`UserEntity`，不是手机号或身份证号；账号不存在/停用/已删除均返回统一的登录失败
业务错误，不暴露具体原因。

### 4. RSA 加解密：复用既有 `RsaJdkUtils`（OAEP），前端用原生 Web Crypto API
仓库 `cn.nihility.rbac.common.util.RsaJdkUtils` 已经实现了 RSA 密钥对生成/加解密/
签名验签，加密算法固定为 `RSA/ECB/OAEPWithSHA-256AndMGF1Padding`（非 PKCS1v1.5）。
按仓库约定（新模块直接复用已有工具，不重复实现），登录解密直接调用
`RsaJdkUtils.decrypt(cipherTextBase64, privateKeyBase64)`，**不新增 RSA 加解密
工具类**，也不引入 PKCS1 相关依赖。

- 后端：`@ConfigurationProperties(prefix = "rbac.user.login")` 绑定
  `publicKey`/`privateKey`（Base64 编码，`RsaJdkUtils.generateKeyPair()` 产出的
  X.509/PKCS8 格式），`application.yml` 中给出一套开发默认值（用
  `RsaJdkUtils.generateKeyPair()` 生成，仅用于本地/演示，生产环境需替换）。

  **实现阶段的经验教训**：最初写入 `application.yml` 的默认 `private-key` 实际是
  PKCS#1 格式 DER，而不是 `RsaJdkUtils.loadPrivateKey` 要求的 PKCS#8 格式，导致对
  运行中的真实后端服务发起的登录请求 100% 解密失败，被业务逻辑吞成"账号或密码不
  正确"。单元测试全部在内存里现生成密钥对，从未加载过这个配置文件默认值本身，因此
  没有覆盖到这个问题，是在人工做端到端真实调用验证时才发现的。已重新生成一套校验过
  可用的 PKCS8/X.509 密钥对替换默认值，并新增
  `RbacLoginPropertiesKeyPairTest`（`@SpringBootTest` 加载真实配置属性，做一次真实的
  公钥加密 + 私钥解密往返校验），防止同类"配置文件默认值本身损坏但被测试忽略"的问题
  再次漏网。这一教训具有普遍性：涉及配置文件默认值（尤其是密钥/证书类）的场景，
  测试覆盖必须包含"加载真实配置文件"这一步，不能只用内存现生成的等价数据代替。
- 新增 `GET /api/auth/public-key` 接口（无需鉴权，登录页可直接调用），返回 Base64
  公钥字符串，前端登录页加载时拉取，避免把公钥硬编码进前端构建产物（默认密钥对
  变更时无需重新构建前端）。
- **前端不新增第三方加密依赖**：`RsaJdkUtils` 用的是标准 RSA-OAEP（SHA-256 +
  MGF1），浏览器原生 `window.crypto.subtle`（Web Crypto API）对 `RSA-OAEP` 算法
  有原生支持，可以直接 `importKey`（`spki` 格式，`hash: 'SHA-256'`）+ `encrypt`
  加密账号、密码，与后端算法严格对应，且 `crypto.subtle` 仅要求安全上下文
  （HTTPS 或 `localhost`，与本项目 `npm run dev` 默认地址一致）。账号、密码分别
  加密后转 Base64 提交，2048 位 key 的 OAEP-SHA256 单次可加密明文上限约 190
  字节，账号密码分开加密不会触及该限制。

### 4a. 管理员重置密码：复用 `PasswordService`，端点挂在 `user-management` 下
"重置密码"是从用户管理页面发起的管理员操作（触发点、权限语义都属于
`user-management` 能力），但实际改写的是 `auth` 模块拥有的 `tab_user_password`
数据，因此按仓库现有的"controller 薄层调用 service"约定处理：

- 端点放在既有 `UserController`：`PUT /api/users/{id}/reset-password`（与
  `enable`/`disable` 同级风格，语义上是"用户"资源的一个子操作），不新开
  `AuthController` 下的端点，避免前端为一个用户管理页面按钮去调两个不同模块的
  Controller。
- `UserController` 直接调用 `auth` 模块暴露的 `PasswordService.resetToDefault(userId)`
  （与"新增用户"流程复用同一个默认密码摘要生成逻辑，避免 `Default#123456` 的摘要
  生成代码重复两份）；`PasswordService` 是 `auth` 模块的 service 接口，`user`
  模块通过依赖注入调用，不做反向依赖（`auth` 不依赖 `user`）。
- 重置后不需要额外主动使该用户当前 access-key/refresh-key 失效：身份校验过滤器
  第 6 步已经是"每次请求都从数据库读取当前 `first_login` 值"（见 Decision 5），
  重置密码把 `first_login` 置回 `1` 后，该用户下一次任意业务请求会自然被首登拦截
  逻辑挡下，不需要在 `TokenService` 里新增"踢会话"的能力。
- 重置密码是一个高敏感操作（任意已登录用户如果能调用到这个接口，就能把任意其他
  用户的密码改成已知的默认值）。本次设计沿用 Non-Goals #1 已经明确的范围裁剪——
  不做基于 `menu` 头的真实角色权限校验，所以这个端点目前只受"通用身份校验通过
  即可调用"这一层保护，和其余业务接口一致，并不比它们更弱；只是这个具体操作的
  误用后果比一般的数据增删改更严重。是否需要给这一个端点单独加一层"仅管理员
  可调用"的校验（先于完整 RBAC 授权引擎落地），列入 Open Questions。
- "重置密码"按钮资源编码 `UserManagement:user:resetPassword` 需要同时出现在两个
  地方：根目录 `权限资源.txt`（人读的全量编码清单）与 `tab_menu` 表（"菜单管理"页面
  和角色管理分配权限时实际读取的数据源）。前者在本 change 早期就已经补上，但
  `tab_menu` 的种子数据漏掉了——这是在响应用户"菜单管理添加用户重置密码操作"的
  后续请求时发现并补上的（`V4__add_user_reset_password_menu.sql`），说明这两处
  登记点是相互独立、需要分别维护的，`权限资源.txt` 顶部的维护约定并不会自动同步到
  `tab_menu`。新增按钮资源时这两处都要检查。

### 4b. 引导问题：Flyway 迁移直接种子化默认管理账号 `admin`/`admin`
`IdentityAuthFilter` 落地后，业务接口（含"新增用户"）一律要求携带有效
`identity-token`；一个全新初始化的数据库没有任何 `tab_user` 记录，也就没有任何账号
能登录，导致无法通过接口创建第一个用户——这是本次改动引入身份校验后必然出现的
引导（bootstrap）问题，在做端到端真实验证时发现。

采用 Flyway 迁移 `V3__seed_default_admin_user.sql` 直接种子化一条 `status=2000`、
`first_login=1` 的默认账号（`admin`/`admin`）解决，而不是运行期"若无任何用户则自动
创建"这类应用代码逻辑：迁移脚本一次性执行、可审计、和其余表结构/字典种子数据
（`tab_dict_type`/`tab_dict_item`，见 `V1__init_schema.sql`）的初始化方式保持一致，
不需要在启动流程里加一段特殊判断逻辑。

密码摘要不在 Java 侧计算后硬编码到 SQL 里，而是用 MySQL 内置 `SHA2(CONCAT(密码,
盐值), 256)` 在插入时现算——好处是摘要值不必跟 `PasswordDigestUtils#digest` 的实现
手工保持同步（少一处"改了 Java 算法却忘记同步更新迁移脚本里写死的摘要"的隐患），
代价是要求两边严格使用同一套"明文+盐值拼接顺序、大小写、编码"的约定，已用独立脚本
交叉验证两侧算出的摘要完全一致。默认密码 `admin`（5 位）明显弱于新用户默认密码
`Default#123456`，但 `first_login=1` 保证首次登录后立即被强制要求改密，缩短弱口令
暴露窗口；`ChangePasswordRequest.newPassword` 的 6 位长度下限只约束新密码，不约束
校验旧密码时的比对，所以用 5 位旧密码换成合规新密码不受阻碍。

### 5. 请求身份校验层：`OncePerRequestFilter`，而非 `HandlerInterceptor`
选 Servlet Filter（`IdentityAuthFilter`，通过 `FilterRegistrationBean` 注册）而不是
Spring MVC `HandlerInterceptor`：Filter 在 `DispatcherServlet` 之前执行，覆盖面更
统一（不依赖 Handler 是否命中 `@RequestMapping`），且方便用 `AntPathMatcher`
维护一份白名单（登录、刷新、改密、公钥获取接口豁免身份校验；改密接口豁免"首登强制"
拦截但仍需要 `identity-token` 校验，因为改密动作本身要认定"是谁在改"）。

校验流程：
1. 白名单路径直接放行。
2. 读取 `identity-token` 头，缺失 → 401 类业务错误（未登录）。
3. 按 Decision 3 描述的反查 + Hash 校验流程，校验失败/过期 → 同样返回未登录错误，
   前端统一按"跳转登录页"处理（区分"access 过期可能可刷新"与"整体未登录"由前端
   根据错误码/是否有 refresh-key 本地缓存自行决策，见前端设计）。
4. 校验通过后，把 `userId` 放入请求属性（`request.setAttribute`），供 controller/
   service 层通过 `HttpServletRequest` 或一个简单的 `CurrentUserContext`
   （ThreadLocal 封装）获取当前操作人，用于审计字段 `create_by`/`update_by` 自动
   填充（本次实现范围内先手动在 service 层读取，不引入 MyBatis-Plus 自动填充
   `MetaObjectHandler` 这类新机制，避免范围膨胀）。
5. 读取 `menu` 头，缺失或不符合三段式格式（`^[A-Za-z]+:[A-Za-z]+:[A-Za-z]+$`）→
   同样拦截为业务错误（前端本应始终携带，此校验主要用于尽早暴露前端遗漏，而非做
   权限判断，见 Non-Goals）。
6. 若该用户当前密码记录 `first_login = 1` 且访问路径不在"改密白名单"内 → 拦截为
   专门的业务错误码（如 `4010` 首登需改密），前端据此重定向强制改密页面。

### 6. 前端 token 刷新策略：响应拦截器里做单飞（single-flight）静默刷新
`request.ts` 的响应拦截器在收到"未登录/`identity-token` 过期"错误码时：若本地有
未过期的 refresh-key，则调用刷新接口换取新 access-key，用新 key 重试原始请求；
并发场景下用一个共享的"正在刷新"Promise 做单飞，避免同一批并发请求触发多次刷新
接口调用。若 refresh-key 也已过期或刷新接口本身返回未登录错误，则清空本地会话并
跳转登录页（保留 `redirect` 查询参数以便登录后回跳）。

## Risks / Trade-offs

- [同一用户允许多端同时登录、新登录不踢旧会话] → 需求未提及"单点登录"，按 Non-Goals
  处理；如后续需要"新登录踢旧会话"，只需在登录成功写入新 Hash 前先删除旧
  `user:access-token:<oldKey>`/`user:refresh-token:<oldKey>` 反查记录即可，改动
  集中在登录 service，不影响本次其余设计。
- [Redis 不可用会导致全站不可登录/不可访问业务接口] → 这是引入 Redis 会话存储的
  固有代价，本次不做降级方案（如本地缓存兜底），因为项目定位是内部管理系统，
  Redis 已是既有基础设施依赖（`data-redis` 早已在 `build.gradle`），可接受。
- [`menu` 头本次只做格式校验、不做权限判断] → 存在"前端未来接入真正权限收敛时
  发现 `menu` 语义被弱校验"的风险；已在 Non-Goals/Open Questions 中显式标注，
  留给用户确认。
- [`tab_user_password` 不保留历史密码] → 无法做"新密码不能与最近 N 次相同"这类策略；
  需求未提出，视为可接受。
- [重置密码端点只受通用身份校验保护，任意已登录用户理论上都能调用] → 后果比一般
  CRUD 更严重（可用来接管其他账号），但这是 Non-Goals #1 已知范围裁剪的直接体现，
  不是本次新引入的独立缺口；是否单独加固见 Open Questions #4。

## Open Questions

1. **`menu` 请求头是否需要在本次一并实现"角色-权限点"授权校验？** 当前设计只做
   格式校验、不做真正的访问控制判断（见 Non-Goals #1）。如果用户期望本次就把
   "该用户角色是否拥有该 menu key 对应权限"的判断也做了，需要额外设计
   （依赖 `role-management`/`permission-management` 现有数据模型，新增角色-权限
   运行时查询与缓存），工作量会显著增加，建议单独开一个 change。
2. **默认 RSA 密钥对的具体取值** 由实现阶段生成一套 2048 位密钥对写入
   `application.yml` 默认配置（仅用于本地/开发环境），不在 design 阶段固定具体
   Base64 值。
3. **refresh-key 默认有效期 7 天** 是设计阶段给出的默认值（需求未明确指定），如
   有不同预期请在确认阶段提出。
4. **重置密码端点是否需要在本次单独加一层"仅管理员可调用"的校验？** 当前设计中
   该端点与其余业务接口一样，只受通用 `identity-token` 身份校验保护，不做角色
   判断（与 Open Questions #1 是同一范围裁剪）。如果用户认为"重置他人密码"的
   风险等级需要现在就单独收紧（例如临时校验调用者是否在 `tab_admin` 中存在有效
   记录，作为完整 RBAC 引擎落地前的过渡方案），需要在确认阶段提出，会小幅增加
   本次工作量。
