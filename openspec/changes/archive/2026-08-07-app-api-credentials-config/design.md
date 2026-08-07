## Context

见 proposal.md - Why。应用管理（`app/` 模块）当前只有业务字段，没有任何"对外接口凭证"概念。本次需要新增一个与 `tab_app` 一对一的配置表，以及围绕它的生成/查询/重置/修改能力，并在前端新增一个独立的"配置"页面（区别于已有的只读"详情"页面）。范围明确限定为"管理后台配置能力"，不包含真正对外开放、执行签名验签或数据同步的接口——后者是后续独立 change 的范围。

用户在澄清问题里选择的四个关键决策：
1. 本次只做配置能力，不实现对外签名验签/同步接口。
2. SecretKey 仅在生成/重置时明文展示一次，其余时候不可再查看明文。
3. AppId/AccessKey/SecretKey 在新建应用时自动生成。
4. 同步配置的粒度：用户选择"需要更细粒度"但未在后续追问中给出具体说明。**本次按最简单的"每个数据域一个布尔开关"实现**（不做组织子树范围、字段级等更细粒度的限定），这是一个需要在实现完成后向用户重新确认的假设，若后续反馈需要更细粒度（如"仅允许同步某个组织子树"），可在开关字段之外新增范围字段，向后兼容不破坏现有数据结构。

（后续用户反馈补充）用户随后明确了"更细粒度"具体指什么：整个应用一份的同步方式（通知/拉取二选一，不区分四个数据域）；通知模式下需要配置回调接口地址与自定义参数（key-value 列表）。用户在澄清问题里确认的四个关键决策：
5. 同步方式的粒度：整个应用一个同步方式，不按数据域拆分。
6. 通知模式的"参数配置"：自定义 key-value 参数列表（不做超时时间/重试次数等更结构化的字段）。
7. 仍然只做配置存储，不实现真正的 HTTP 通知发送或拉取接口（与决策 1 一致）。
8. 接口地址校验：允许 http 或 https，不强制 https。

## Goals / Non-Goals

**Goals:**
- 每个应用拥有系统生成的 AppId/AccessKey/SecretKey 三元组，创建应用时自动生成。
- SecretKey 落库加密存储，不落明文；仅"重置"操作的响应体单次返回明文。
- 应用的接口签名算法（SHA-256/SM3）、同步范围开关（组织/用户/应用/字典）可查询、可修改。
- 配置相关写操作复用 `org-scope-write-guard` change 已确立的管辖组织范围校验与"伪装成不存在"错误提示风格。
- 应用管理列表新增独立"配置"页面入口（非弹窗），风格与已有"详情"页一致。

**Non-Goals:**
- 不实现任何真正对外开放的接口（不做基于 AppId/AccessKey/SecretKey 的鉴权过滤器、不做签名验签的中间件或过滤器、不做组织/用户/应用/字典的数据同步接口）。这些留给后续独立 change，本次只准备好凭证与配置数据。
- 不做同步范围的更细粒度限定（如按组织子树、按字段）——见上文用户澄清中的说明。
- 不改动应用管理现有的 `application-management` 能力（新增/编辑/启停用/删除/分页/详情），本次是与其协作的独立新能力。
- 不做 SecretKey 的"过期时间"、"多版本共存"等高级凭证生命周期管理。

## Decisions

### 1. 新增独立表 `tab_app_config`（一对一），不在 `tab_app` 上加列
`tab_app` 的业务字段（`name`/`code`/`showOrder`/`remark`/`ext1`~`ext10`）由"表单字段定义"（`bizType=APP`）驱动的动态校验/动态渲染管线管理（`AppServiceImpl.ALLOWED_DYNAMIC_COLUMNS`、`FormFieldDefinitionService`），直接在 `tab_app` 上加凭证/配置列会被这套动态字段管线意外感知或需要额外排除逻辑，且语义上凭证/配置与"应用基本信息"是两个关注点。新增独立表 `tab_app_config`（`app_id` 外键唯一，一对一），通过独立的 `AppConfigEntity`/`AppConfigMapper`/`AppConfigService` 管理，与 `tab_app` 完全解耦。

备选方案：直接在 `tab_app` 加 `open_app_id`/`access_key`/`secret_key`/`sign_algorithm`/`sync_*_enabled` 列。放弃原因：如上，会污染业务字段动态管线，且"配置"在产品语义上就是应用的一个独立子资源（对应独立页面），拆表更贴合。

### 2. AppId 列命名为 `open_app_id`，与内部 FK 列 `app_id` 区分
`tab_app_config` 需要一个"指向 `tab_app.id`"的外键列，按仓库既有 FK 命名惯例（`org_id`/`owner_id`/`admin_id`）应命名为 `app_id`；但用户要求的"应用id（AppId）"是一个系统生成、对外暴露的独立标识，与内部自增主键语义完全不同，不能复用同一个列名。数据库列命名为 `open_app_id`（"开放平台应用标识"），Java 实体字段通过 MyBatis-Plus `@TableField("open_app_id")` 显式映射为 `appId`，对外 DTO/前端字段名保持 `appId`，与用户的术语（AppId）一致，同时不破坏 FK 列命名惯例。

### 3. SecretKey 存储：SM4 对称加密落库，而非明文或不可逆哈希
SecretKey 未来要用于 HMAC 签名验证（`sign_algorithm` 决定用 SHA-256 还是 SM3 系的算法对请求做签名/验签），服务端必须能拿到明文参与签名计算，因此**不能**像密码那样做不可逆哈希存储（哈希后无法参与 HMAC 运算）。为避免直接明文落库，复用仓库已有的 `common/util/Sm4JdkUtils`（对称加密，SM4/CBC/PKCS7Padding）对 SecretKey 加密后再落库，密钥来自新增的 `@ConfigurationProperties(prefix = "rbac.app-secret")` 配置类 `AppSecretProperties`（`sm4Key` 字段，Base64 编码），风格与 `RbacLoginProperties` 里 RSA 密钥对的配置方式一致：仓库里放一个仅用于本地开发的默认值，并在注释里明确提示生产环境必须替换。

备选方案：明文落库。放弃原因：即便本次不实现对外验签接口，SecretKey 本质上是一个高敏感凭证，明文落库是不必要的安全隐患，而 SM4 加解密的接入成本很低（复用现成工具类，一行调用），没有理由不做。

### 4. 凭证生成：`SecureRandom` 十六进制随机串，不用 UUID，均不加前缀
参考仓库里 `PasswordDigestUtils.randomSalt()`（`SecureRandom` + 十六进制编码）的既有模式，新增 `app/support/AppCredentialGenerator`：
- AppId：24 位随机十六进制（12 字节熵）
- AccessKey：32 位随机十六进制（16 字节熵）
- SecretKey：48 位随机十六进制（24 字节熵）

三者均不加前缀（`app_`/`ak_` 这类前缀最初是为了"人工一眼识别用途"，但实现完成后用户明确要求去掉，三者统一为纯随机十六进制字符串，长度本身已足以区分——AppId/AccessKey/SecretKey 在界面上各自有独立的标签展示，不依赖字符串前缀区分）。

不用 `UUID.randomUUID()`（`TokenServiceImpl` 生成登录 token 的既有模式）：UUID 的字节熵固定为 122 bit，选用统一的生成器类而不是复用 `TokenServiceImpl` 的 token 生成逻辑，避免把"应用对外凭证"和"用户登录会话令牌"这两个语义完全不同的概念耦合在一起。

### 5. 生成时机与展示策略：创建时静默生成，仅"重置"接口返回明文
AppId/AccessKey 创建时即生成并且后续查询接口正常返回明文（二者定位类似"用户名"，不是保密材料，AccessKey 单独使用无法完成鉴权，必须配合 SecretKey 签名才有效，因此明文常显是合理的）。SecretKey 创建时同样生成一个初始值（保证 `secret_key` 列非空、后续验签逻辑随时可用），但**不通过任何查询接口暴露明文**——管理员如果需要明文，必须显式调用"重置 SecretKey"，该操作生成一个新值替换当前值并在响应体里返回一次明文。也就是说，"创建应用"这个动作生成的初始 SecretKey 实际上永远不会被任何人看到明文，除非管理员主动执行一次"重置"；这是刻意的设计取舍——保持"唯一的明文暴露入口是重置接口"这一条不变式，不为"首次创建"再单独开一个例外路径，简化实现与心智负担。

### 6. 配置写操作复用管辖组织范围校验，读取 `AppMapper` 而非注入 `AppService`
重置 SecretKey / 修改签名算法 / 修改同步配置这三个写操作，需要先按应用 `orgId` 做管辖组织范围校验（复用 `org-scope-write-guard` change 确立的 `OrgScopeService.isOrgIdAllowed`），越权时报"应用不存在"。`AppConfigServiceImpl` 直接注入 `AppMapper`（只读查询应用实体获取 `orgId`/校验存在性），不注入 `AppService`，避免引入不必要的服务间依赖（`AppConfigServiceImpl` 不需要 `AppService` 的业务方法，只需要按 id 查一行）。

### 7. 三个写操作各自独立的接口，不做一个大而全的"更新配置"接口
`PUT /api/apps/{id}/config/sign-algorithm`、`PUT /api/apps/{id}/config/sync`、`POST /api/apps/{id}/config/secret-key/reset` 三个接口职责单一，与仓库里"启用"/"停用"分离于"更新"的既有惯例一致（而不是把状态变更也塞进通用的 `update`）。三个操作在前端页面上是三个独立的 UI 区块（基础信息/接口配置/同步配置），各自保存，不需要一次性提交多个区块的改动；这三个区块在页面上用 `el-tabs` 切换展示而不是纵向堆叠（见下方"实现完成后调整"），但每个区块各自独立的表单状态、独立的保存按钮/权限点这一结构没有变化。

### 9.（实现完成后补充）不修改已应用的 V3/V4 迁移文件，改用新增 V5 迁移
凭证去前缀（Decision 4）与能力更名为"应用配置"（用户第三点反馈）在实现完成、`V3__app_config.sql`/`V4__app_config_permission_seed.sql` 已经在开发数据库里成功执行（Flyway 记录了其 checksum）之后才提出。Flyway 的核心约定是"已应用的迁移文件不可再修改"——修改会导致其 checksum 与 `flyway_schema_history` 里记录的值不一致，下次启动/测试时 Flyway 校验直接失败，阻断所有开发者和 CI。因此：
- 凭证格式的调整只改 `AppCredentialGenerator` 源码（影响此后新生成的凭证），不回填历史数据，`V3` 建表语句本身不用改（`open_app_id`/`access_key` 列定义与长度不受影响，只是注释文字提到的"格式 app_ + ..."变得不准确）。
- 新增 `V5__app_config_rename_and_no_prefix.sql`：用 `UPDATE` 语句修正 `V4` 已写入 `tab_menu`/`tab_permission` 的展示文案（"应用接口配置" → "应用配置"），用 `ALTER TABLE ... MODIFY COLUMN ... COMMENT` 顺带修正 `V3` 里两个列的注释文字，使其不再提及已废弃的前缀格式。`ALTER ... MODIFY COLUMN` 只改注释，不改列的类型/长度/约束，是无风险的元数据变更。

备选方案：直接编辑 `V3`/`V4` 文件内容。放弃原因：如上，这两个文件在本地开发库里已经被 Flyway 记录并校验通过，编辑会破坏 checksum 一致性；即使在这个特定环境里可以手动清空 `flyway_schema_history` 表来"绕过"，这不是一个能推广到其他开发者/CI 环境的做法，新增迁移是唯一稳妥的路径。

### 8. 操作日志：记录变更但不记录密钥明文
签名算法、同步开关的变更复用 `OperationLogRecorder`，`resourceType` 沿用既有的 `OperationLogResourceType.APP`（目标是应用本身的一项配置变更，不新增资源类型），记录变更前后的字段快照。SecretKey 重置的操作日志**只记录"执行了重置"这一事实**，不记录新旧 SecretKey 的明文或密文——操作日志的"操作历史"页面对同组织的其他管理员可见，把密钥material 写进去等于把秘密广播出去，这是必须避免的信息泄露路径。

### 10.（用户反馈补充）基础同步配置项加在 `tab_app_config` 同一张表，不再拆表
同步方式（`sync_mode`）、通知回调地址（`notify_url`）、通知自定义参数（`notify_params`）与已有的四个 `sync_*_enabled` 开关同属"同步配置"这个概念分组，基数同为"每个应用一份"，因此直接在 `tab_app_config` 上加三列（新增 `V6` 迁移），不新建一张"同步子配置表"。Decision 1 把凭证/配置从 `tab_app` 拆出去，是为了避开 `tab_app` 的动态字段管线；这里 `tab_app_config` 本身就是专门的配置表，不存在同样的顾虑，加列是最直接的做法。

### 11.（用户反馈补充）同步方式整个应用一份，不按数据域拆分
用户明确"同步方式（通知/拉取）"是整个应用一个设置，不需要组织/用户/应用/字典四个数据域各自独立选择同步方式。`sync_mode`/`notify_url`/`notify_params` 三个新字段因此都不带数据域后缀，和四个 `sync_*_enabled` 开关是平级但独立的两组概念：开关决定"同步哪些数据"，同步方式决定"这些数据用什么方式同步"。

### 12.（用户反馈补充）通知模式下地址校验放在服务层，不放 Bean Validation
`notifyUrl` 是否必填、是否需要是合法 URL，取决于同一个请求里另一个字段 `syncMode` 的取值——这是跨字段校验，标准 Bean Validation 注解（`@NotBlank`/`@Pattern` 等）只能校验单个字段，不感知其他字段的值。放弃引入类级自定义约束注解（`@Constraint` + 自定义 `ConstraintValidator`）这类更"声明式"但需要新增文件、理解成本更高的方案，选择在 `AppConfigServiceImpl.updateSyncConfig` 里用一个简单的 `if (SyncMode.NOTIFY.equals(...))  { assertValidNotifyUrl(...) }` 分支完成，和仓库里其他跨字段业务规则（如 `OrgServiceImpl.update` 里"上级组织不能是自身"）的校验方式风格一致。URL 格式校验用 `java.net.URI` 解析 + 校验 `scheme` 是 `http`/`https` 且 `host` 非空，不引入额外的 URL 校验库。前端 `AppConfigView.vue` 额外做一次等价的浏览器端预校验（用 `new URL(...)` + 检查 `protocol`），只是为了避免明显非法输入还要走一次网络往返，不是校验的唯一防线——后端校验才是真正兜底。

### 13.（用户反馈补充）`notifyParams` 落库为 JSON 文本，DTO/VO 层用 `Map<String, String>`
参考仓库里 `tab_operation_log.change_detail`（`TEXT` 列存 JSON 数组）的既有落库模式，`tab_app_config.notify_params` 用 `TEXT` 列存一个 JSON 对象字符串，不使用 MySQL 原生 `JSON` 类型（仓库里目前没有任何表用过原生 `JSON` 列，保持和现有约定一致）。读写转换复用已有的 `common/util/JacksonUtils`（已经内置 `MAP_STRING_TYPE_REFERENCE`，刚好匹配 `Map<String, String>`），不需要新增序列化工具。`AppConfigEntity.notifyParams` 是原始 JSON 字符串，`AppConfigVO.notifyParams` 是 `Map<String, String>`，两者类型不同，MapStruct 生成的映射方法用 `@Mapping(target = "notifyParams", ignore = true)` 显式跳过，由 `AppConfigServiceImpl` 里新增的私有 `toVO(entity)` 方法在调用 `AppConfigConvert.INSTANCE.toVO(entity)` 之后手工解析回填，这个模式复用了 Decision 2 里"MapStruct 类型不匹配就显式忽略 + 调用方兜底"的既有思路（那里是完全没有对应属性所以自动忽略，这里是类型不同需要显式声明）。前端 `notifyParams` 是 `Record<string, string>`，但表单里用 `{key, value}[]` 行数组编辑（模板里动态 key 的双向绑定不便），提交前再收敛回 `Record`，忽略 key 为空的行。

## Risks / Trade-offs

- [风险] SM4 主密钥（`rbac.app-secret.sm4-key`）目前是一个静态配置项，没有密钥轮换/KMS 集成 → 可接受：与仓库现有 RSA 登录密钥对的管理方式一致（同样是静态配置 + 注释提示生产环境替换），本次不引入额外的密钥管理基础设施，属于合理的最小化实现。
- [已解决] 同步配置最初只做四个布尔开关，用户提到过"需要更细粒度"但未给出具体方案 → 用户已在后续反馈里明确为"整个应用一份的同步方式（通知/拉取）+ 通知模式下的接口地址与自定义参数"，见 Decision 10-13，本次已按此实现。
- [权衡] 通知回调地址/参数目前只做配置存储校验，不做"发起一次测试通知"这类连通性校验 → 与本次"只做配置能力"的范围边界一致（design.md Context 决策 1/7），管理员填错地址只有等到未来真正实现通知发送的 change 上线后才会发现；如果需要提前发现配置错误，后续可以加一个独立的"测试通知"按钮，属于增量能力。
- [风险] SecretKey 一旦重置，旧值立即失效——如果外部系统还在用旧值签名，会立刻鉴权失败 → 这是"重置"语义的应有行为（对齐大多数开放平台的 AK/SK 轮换语义），不是本次改动的缺陷；不做"新旧双活过渡期"这类更复杂的轮换机制，保持实现简单。
- [权衡] 本次生成的初始 SecretKey 永远不会被任何人看到明文（见 Decision 5），管理员必须执行一次"重置"才能拿到可用的明文值 → 这是刻意简化，避免为"首次创建展示"单独开发一条例外路径；如果用户希望"新建应用后立即弹窗展示一次初始明文"，是后续可以低成本调整的点。
