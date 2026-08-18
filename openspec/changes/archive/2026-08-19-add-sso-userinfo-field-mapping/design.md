## Context

现状：`CasController#serviceValidate` 固定调用 `CasXmlResponses.success(user.getCode(),
user.getName())`，只返回 XML，属性固定只有 `cas:name`；`OAuthController#userinfo` 固定
拼装 `{sub, username, name}` 三个字段。两者都没有任何可配置空间。

已有可复用的同类模式（app-sync-field-mapping 能力）：`tab_app_sync_field_mapping` 表
（`appRefId` + `metadataFieldId` + `appFieldName`/`appFieldCode` + `transformType`/
`transformValue`），源字段名称/编码不落快照、查询时实时 LEFT JOIN `tab_metadata_field`
读取（`AppSyncFieldMappingMapper.selectByAppRefIdAndDomain`），`TransformType`
（`NO_TRANSFORM`/`FIXED_VALUE`/`SCRIPT`）常量，`FieldMappingTransformer`（GraalVM 沙箱
执行转换脚本，200ms 超时保护），`TransformScriptValidator`（保存时纯语法校验，不执行）。
本次"用户信息字段映射"直接复用这一整套模式（含源字段选择方式），本地字段同样来自
`tab_metadata_field`（`bizType=USER`），与应用同步字段映射的用户域字段共用同一份元数据
字段目录，管理员在"元数据字段管理"里维护的字段名称/编码变更能同时反映到两处。唯一差异：
`tab_metadata_field` 的 USER 目录不含 `id`（它是主键，不属于"可开放配置"的字段目录），
而本次默认字段要求包含"用户ID"，因此保留一个不落在 `tab_metadata_field` 里的固定伪字段
（见 Decision 2），做法对齐 `identity/upstream` 模块处理任职域"所属人员标识"等外键字段
时的 `UpstreamPositionPseudoFieldCode` 先例（不是元数据字段目录能表达的字段，用固定
伪字段编码绕开）。

应用认证配置现状（`AppAuthConfigServiceImpl#findOrCreateByAppRefId`）：查询时若记录不
存在则懒创建一条默认记录再返回，本次字段映射沿用类似"懒处理"思路，但选择不落库、只在
内存兜底（见 Decision 4），避免 GET 请求产生写库副作用。

## Goals / Non-Goals

**Goals:**
- CAS 票据验证支持按 `format` 参数返回 XML 或 JSON，默认 JSON。
- 每个应用一份用户信息字段映射配置（CAS/OAuth2.0 共用），本地字段从已启用的
  `tab_metadata_field`（`bizType=USER`）目录中选择（另加一个固定的"用户ID"伪字段），
  支持本地字段→应用字段的名称/编码映射与三种转换方式，默认（未配置时）等价于
  "用户ID + 姓名"两个字段。
- CAS `<cas:attributes>`/JSON 对应节点与 OAuth2 `userinfo` 响应体的非固定标识部分
  均由该配置动态生成。
- 抽取可复用的脚本转换执行组件，消除两处 GraalVM 沙箱代码重复。

**Non-Goals:**
- 不改变 CAS `cas:user`（固定用户 code）与 OAuth2 `sub`（固定用户 id）两个协议规定的
  固定标识的取值来源，这两者本次不纳入字段映射配置范围。
- 不按协议（CAS/OAuth2.0）分别维护两份独立映射配置（已通过用户确认：一个应用一份，
  两种协议共用）。
- 不新增"本地字段目录"的维护入口，本地字段目录即"元数据字段管理"里已有的 USER 域
  字段（增删改走既有的元数据字段管理功能，不在本次范围内），本次只新增那个固定的
  "用户ID"伪字段，不再额外引入其它伪字段。
- 不改变 CAS 单点登录（`/login`）、单点登出（`/logout`）、OAuth2 授权/令牌签发/刷新
  端点的现有行为。

## Decisions

1. **CAS `format` 参数：大小写不敏感，非法/缺省值一律按 JSON 处理**
   - `@RequestParam(required = false) String format`，标准化为大写后仅当等于
     `"XML"` 时走 XML 分支，其余（包括 `null`、空字符串、`"JSON"`、任何其它取值）一律
     走 JSON 分支——不因未知 `format` 取值报错，保持端点行为简单、对客户端宽容。
   - **BREAKING**：默认值从（现状事实上恒定的）XML 改为 JSON，是本次需求明确要求的
     行为；已知无真实第三方客户端依赖当前默认值。

2. **本地字段接入 `tab_metadata_field`（USER 域），另加一个固定的"用户ID"伪字段**
   - `tab_app_userinfo_field_mapping.metadata_field_id` 设计为**可为空**的 FK（关联
     `tab_metadata_field.id`）：非空时表示一条真实的 USER 元数据字段；为空时约定表示
     固定的"用户ID"伪字段（`tab_user.id`，主键，不在 `tab_metadata_field` 目录里，
     原因见 Context）。查询时用 `LEFT JOIN tab_metadata_field`（对齐
     `AppSyncFieldMappingMapper.selectByAppRefIdAndDomain` 的既有写法），
     `field_name`/`field_code` 用 `COALESCE(mf.field_name, '用户ID')`/
     `COALESCE(mf.field_code, 'id')` 回填，使伪字段和真实元数据字段在返回结构上
     一致，前端/运行时都按同一个 `fieldCode` 字符串处理，不需要区分来源。
   - 保存校验（`assertMetadataFieldValid`，对齐 `AppSyncConfigServiceImpl` 现有逻辑）：
     `metadataFieldId` 为空时视为合法（"用户ID"伪字段，无需校验）；非空时必须存在、
     状态为启用（`MetadataFieldStatus.ENABLED`）、且 `bizType=USER`，否则拒绝保存。
   - 前端本地字段下拉选项 = 调用现成的元数据字段查询接口按 `bizType=USER` 过滤已启用
     字段，再在列表最前面插入一个固定的"用户ID"选项（值用一个约定的哨兵，如
     `metadataFieldId=null`）。

3. **主标识字段固定，映射只驱动附加属性/字段（已与用户确认）**
   - CAS：`cas:user` 继续固定取 `user.getCode()`，与 `cas:attributes` 结构上分离，
     不受映射配置影响，也不会与映射生成的属性发生键冲突。
   - OAuth2：`sub` 继续固定取 `user.getId()`；写入顺序上先 `body.putAll(mappedFields)`
     再 `body.put("sub", ...)`，即使管理员把某一行的 `appFieldCode` 恰好配置成
     `"sub"`，最终仍以协议规定的固定值为准（最后写入生效）。

4. **默认字段不落库，查询接口与运行时解析共用同一段兜底逻辑**
   - 某应用在 `tab_app_userinfo_field_mapping` 中无任何记录时（管理端查询接口与运行时
     解析组件均按此判断），按以下规则现算两行默认映射，不写库：
     - "用户ID"：`metadataFieldId=null`、`fieldCode="id"`、`fieldName="用户ID"`（固定
       字面量）、`appFieldName="用户ID"`、`appFieldCode="id"`、`NO_TRANSFORM`。
     - "姓名"：按 `bizType=USER AND columnName='name'` 查询 `tab_metadata_field`
       （`MetadataFieldMapper`）取其 `id`/`fieldName`/`fieldCode` 回填
       `metadataFieldId`/`fieldName`/`fieldCode`，`appFieldName` 固定用"姓名"字面量
       （应用侧展示名不需要跟随源字段的可编辑名称）、`appFieldCode="name"`、
       `NO_TRANSFORM`；万一该条元数据字段被删除查不到（当前无删除入口，防御性处理），
       则默认列表退化为只有"用户ID"一行。
   - 管理端查询接口（`GET .../userinfo-field-mappings`）：无记录时直接返回上述现算
     默认列表，管理员在此基础上编辑后保存才真正落库（整体替换语义，同
     app-sync-field-mapping）。
   - 运行时解析组件同样：无记录时使用同一段现算逻辑得到默认列表再计算属性值，不依赖
     管理端是否曾经打开过配置页面（避免 GET 产生副作用写库，同时保证运行时行为与
     "从未配置"的语义一致）。

5. **抽取 `ScriptTransformExecutor`，两处转换器共同复用**
   - 新增 `cn.nihility.rbac.common.util.ScriptTransformExecutor`（静态工具类，风格
     对齐 `TransformScriptValidator`：无状态、不注册为 Spring bean），把
     `FieldMappingTransformer` 现有的 `executeScript`/`unwrap` 私有方法迁移过来
     （GraalVM `Context` 沙箱执行、200ms 超时、专用 daemon 线程池、超时/异常时返回
     `null` 并记录 WARN 日志的行为均不变）。
   - `FieldMappingTransformer.transform()` 与新增的
     `SsoUserinfoAttributesResolver.resolve()` 的 `SCRIPT` 分支均改为调用
     `ScriptTransformExecutor.execute(script, sourceValue)`。
   - 动机：两处都是安全相关的沙箱执行代码（超时、权限限制），重复维护有实际的
     一致性风险（一处修了超时时间/权限策略，另一处忘了同步），不是过度设计。

6. **`appFieldCode` 校验与唯一性**
   - Bean Validation `@Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_-]*$")`：必须是合法的
     标识符形态，保证能安全地作为 XML 标签名（CAS 属性）与 JSON key（OAuth2/CAS JSON）
     使用，不需要额外转义。
   - 数据库唯一约束 `uk_tab_app_userinfo_field_mapping(app_ref_id, app_field_code)`，
     避免同一应用内出现重复的输出字段编码；保存时整体替换前置校验同样检查请求内部
     `appFieldCode` 不重复（同 app-sync-field-mapping 现有校验思路）。

7. **接口与权限**
   - `GET /api/apps/{id}/config/auth/userinfo-field-mappings`、
     `PUT /api/apps/{id}/config/auth/userinfo-field-mappings`（整体替换语义），归入
     `AppAuthConfigController`。复用既有权限点 `AppManagement:app:config:editAuth`，
     不新增权限点（同 `AppAuthConfigController`/`AppSyncConfigController` 现状：接口层
     不加校验注解，前端路由/按钮层面控制）。

8. **CAS JSON 响应格式**
   - 新增 `CasJsonResponses`（`sso.cas.support` 包，风格对齐 `CasXmlResponses`），
     按 CAS 3.0 JSON 响应形状构造：
     成功 `{"serviceResponse":{"authenticationSuccess":{"user":"...","attributes":{...}}}}`，
     失败 `{"serviceResponse":{"authenticationFailure":{"code":"...","description":"..."}}}`。
   - `CasXmlResponses.success` 签名从 `(String user, String name)` 改为
     `(String user, Map<String, Object> attributes)`，`<cas:attributes>` 内按
     `attributes` 的每个 entry 生成一个同名子元素（value 经现有 `escape()` 转义），
     `attributes` 为空时省略 `<cas:attributes>` 整个节点（而不是生成空标签）。

## Risks / Trade-offs

- [风险] CAS 默认响应格式从 XML 变为 JSON 是破坏性变更 → 缓解：proposal.md 已标注
  **BREAKING**；系统尚处设计阶段，无真实第三方依赖当前默认值；调用方可显式传
  `format=XML` 保留旧行为。
- [风险] 脚本转换在高频 SSO 登录路径上执行，若大量应用配置了脚本转换字段，可能引入
  登录延迟 → 缓解：复用与 app-sync 场景相同的 200ms 超时 + 专用线程池保护，且 SSO
  场景默认只有 2 个字段，管理员自行配置更多字段时需自行权衡。
- [风险] 抽取 `ScriptTransformExecutor` 属于对现有 `FieldMappingTransformer` 的重构，
  存在改出回归的可能 → 缓解：只做纯粹的方法搬移（不改变超时时间、沙箱权限、异常处理
  行为），现有 `FieldMappingTransformer` 的单元测试覆盖需在重构后继续跑通作为回归保护。
- [风险] OAuth2 `userinfo` 场景下，若管理员把某行 `appFieldCode` 配置成
  `"sub"`，该行配置的值会被固定 `sub` 覆盖、实际不生效，可能造成管理员困惑 → 缓解：
  属于 Non-Goals 明确排除的场景（固定标识不受映射影响），后续如需提示可在保存时加一条
  前端/后端的信息提示（不阻塞保存），本次不做。
- [风险] 本地字段接入 `tab_metadata_field` 后，若管理员在"元数据字段管理"里停用或
  修改了某个已被 SSO 映射引用的字段，会影响 SSO 响应（停用后 `assertMetadataFieldValid`
  只在下次保存时校验，不会立即让已保存的映射行失效；`fieldName` 改名会实时反映到查询
  接口的展示名上）→ 缓解：与 app-sync-field-mapping 现状一致的既有行为，非本次新增
  风险，管理员变更元数据字段配置时应自行确认下游影响。

## Migration Plan

- 新增 Flyway 增量迁移文件 `V2__add_app_userinfo_field_mapping.sql`（`V1__init_schema.sql`
  是当前唯一基线，本次不修改它），只新增 `tab_app_userinfo_field_mapping` 表
  （`metadata_field_id` 允许为空，语义见 Decision 2），不需要回填任何数据（默认值走
  Decision 4 的现算兜底，不落库）。
- 无需修改现有 `tab_app_auth_config`/`tab_app_sync_field_mapping`/`tab_metadata_field`
  表结构（`tab_metadata_field` 的 USER 目录本次不新增"id"字段，见 Context）。

## Open Questions

（无——CAS `cas:user`/OAuth2 `sub` 固定 vs 可配置、配置作用域是否按协议拆分两个关键
决策已通过 AskUserQuestion 与用户确认，见 Decision 3/7 对应选择。）
