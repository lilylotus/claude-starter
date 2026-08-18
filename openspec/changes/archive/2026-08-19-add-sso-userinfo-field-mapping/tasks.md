## 1. 数据库

- [x] 1.1 新增 Flyway 增量迁移文件 `V2__add_app_userinfo_field_mapping.sql`：创建表
      `tab_app_userinfo_field_mapping`（id、app_id、metadata_field_id**可空**（FK
      `tab_metadata_field.id`，为空表示固定的"用户ID"伪字段，见 design.md Decision 2）、
      app_field_name、app_field_code、transform_type、transform_value、
      create_by/create_time/update_by/update_time），唯一约束
      `(app_id, app_field_code)`，不修改 `V1__init_schema.sql`。

## 2. 转换脚本执行组件抽取

- [x] 2.1 新增 `common/util/ScriptTransformExecutor.java`：从
      `FieldMappingTransformer` 迁移 `executeScript`/`unwrap` 逻辑（GraalVM 沙箱、
      200ms 超时、专用 daemon 线程池、超时/异常返回 `null` 并记录 WARN 日志），
      静态工具类风格对齐 `TransformScriptValidator`。
- [x] 2.2 修改 `sync/transform/FieldMappingTransformer.java`：`SCRIPT` 分支改为委托
      `ScriptTransformExecutor.execute(...)`，删除已迁移的私有方法；确认现有相关
      单元测试（如涉及）仍然通过。

## 3. 用户信息字段映射：后端配置层

- [x] 3.1 新增 `app/authconfig/entity/AppUserinfoFieldMappingEntity.java`（对应
      `tab_app_userinfo_field_mapping`，`metadataFieldId` 为 `Long`/可空）、
      `mapper/AppUserinfoFieldMappingMapper.java`（`BaseMapper` + 自定义
      `selectByAppRefId` 查询方法）。
- [x] 3.2 新增 `mybatis/mapper/AppUserinfoFieldMappingMapper.xml`：`LEFT JOIN
      tab_metadata_field`，`field_name`/`field_code` 用
      `COALESCE(mf.field_name, '用户ID')`/`COALESCE(mf.field_code, 'id')` 回填
      （对齐 `AppSyncFieldMappingMapper.xml` 的既有写法），`ORDER BY m.id ASC`；
      新增载体 DTO `AppUserinfoFieldMappingRow`（含 `metadataFieldId`/`fieldName`/
      `fieldCode`/`appFieldName`/`appFieldCode`/`transformType`/`transformValue`）。
- [x] 3.3 新增 DTO：`AppUserinfoFieldMappingVO`（含只读本地字段展示名
      `fieldName`/`fieldCode`）、`AppUserinfoFieldMappingSaveRequest`（Bean
      Validation：应用字段名称/编码非空、`appFieldCode` 标识符正则、转换方式枚举
      校验；`metadataFieldId` 允许为 `null`，不加 `@NotNull`）。
- [x] 3.4 新增 `mapstruct/AppUserinfoFieldMappingConvert.java`（静态单例风格，非
      Spring bean）。
- [x] 3.5 新增（或扩展 `AppAuthConfigService`）查询/整体替换保存两个方法：
      - 查询：该应用在 `tab_app_userinfo_field_mapping` 无记录时，现算默认两行——
        "用户ID"（`metadataFieldId=null`）与按 `bizType=USER AND columnName='name'`
        查 `MetadataFieldMapper` 得到的"姓名"字段（查不到则只返回"用户ID"一行）。
      - 保存：先删后插语义；校验请求内 `appFieldCode` 不重复；`metadataFieldId`
        非空的行需存在、状态 `ENABLED`、`bizType=USER`（对齐
        `AppSyncConfigServiceImpl#assertMetadataFieldValid` 的校验逻辑，`null` 视为
        合法的"用户ID"伪字段跳过校验）；转换脚本语法校验（`TransformScriptValidator`）；
        管辖组织权限校验（复用 `AppScopeGuard`）；操作日志记录（复用
        `OperationLogRecorder`，对齐 `AppAuthConfigServiceImpl` 现有写法）。
      - 实现偏差：无记录时的默认两行现算逻辑抽取为独立的
        `app/authconfig/support/AppUserinfoFieldMappingDefaults`（静态工具类，接收
        调用方已注入的 `MetadataFieldMapper`，风格对齐 `AppScopeGuard`），供
        `AppAuthConfigServiceImpl` 与 `SsoUserinfoAttributesResolver` 共同复用，
        避免两处重复实现同一段兜底逻辑（design.md Decision 4 已要求"共用同一段现算
        逻辑"，design.md 未点名具体类名，此为实现细节的合理选择）。
- [x] 3.6 在 `AppAuthConfigController` 新增两个接口：
      `GET /api/apps/{id}/config/auth/userinfo-field-mappings`、
      `PUT /api/apps/{id}/config/auth/userinfo-field-mappings`，补充 springdoc
      `@Operation` 注解。

## 4. 用户信息字段映射：运行时解析

- [x] 4.1 新增 `sso/support/SsoUserinfoAttributesResolver.java`：按 `appRefId` 查询
      映射记录（无记录时用同 3.5 的现算默认两行逻辑），对每行按 JOIN 得到的
      `fieldCode`（"id"/"name"/"mobile"/... 与 `tab_metadata_field.columnName`/
      伪字段 "id" 对应）从 `UserEntity` 取值，按 `transformType` 应用转换
      （`NO_TRANSFORM` 直接取值、`FIXED_VALUE` 取 `transformValue`、`SCRIPT` 调用
      `ScriptTransformExecutor`），返回 `LinkedHashMap<String, Object>`（key 为
      `appFieldCode`，保持配置顺序）。
- [x] 4.2 在 `AppProtocolGuard` 新增公共方法 `resolveAppRefId(String appId)`：按对外
      应用标识解析内部 `appRefId`，应用不存在时抛 `SsoProtocolException`。

## 5. CAS 协议端点改造

- [x] 5.1 修改 `CasXmlResponses.success` 签名为
      `(String user, Map<String, Object> attributes)`：`attributes` 每个 entry
      生成一个同名子元素（复用现有 `escape()`），`attributes` 为空时不生成
      `<cas:attributes>` 节点；`failure` 方法保持不变。
- [x] 5.2 新增 `sso/cas/support/CasJsonResponses.java`：构造 CAS 3.0 JSON 成功/失败
      响应体（`Map`/`LinkedHashMap`，经 `ProtocolResponseWriter.json` 输出）。
- [x] 5.3 修改 `CasController#serviceValidate`：新增 `format` 参数（大小写不敏感，
      非 `XML` 一律按 JSON 处理，默认 JSON）；成功分支用
      `payload.appId()`→`AppProtocolGuard.resolveAppRefId`→
      `SsoUserinfoAttributesResolver.resolve` 得到属性 Map，按 `format` 选择
      `CasXmlResponses`/`CasJsonResponses` 构造响应；失败分支同样按 `format` 选择
      对应格式的失败响应。

## 6. OAuth2 协议端点改造

- [x] 6.1 修改 `OAuthController#userinfo`：解析 `payload.userId()`/`payload.clientId()`
      后，经 `AppProtocolGuard.resolveAppRefId`→`SsoUserinfoAttributesResolver.resolve`
      得到字段 Map，`body.putAll(mappedFields)` 后再 `body.put("sub", ...)`（固定值
      最后写入、始终生效），删除原有硬编码的 `username`/`name` 赋值。实现偏差：
      `resolveAppRefId` 在令牌签发后应用被删除等边缘场景下会抛
      `SsoProtocolException`，本地 catch 后跳过映射字段（仅保留固定的 `sub`），不
      向上传播（同 `AppProtocolGuard` 类注释"须由 Cas/OAuthController 本地 catch"
      的既有约束）。

## 7. 前端

- [x] 7.1 `src/api/app.ts`/`src/types/app.ts` 新增用户信息字段映射的查询/保存接口
      封装与类型定义。
- [x] 7.2 `AppConfigView.vue` "认证管理"标签页新增"用户信息响应字段映射"可编辑表格：
      本地字段下拉选项 = 复用现有"元数据字段"查询接口按 `bizType=USER` 过滤已启用字段
      （与"同步字段映射"表格现有的 `metadataFieldOptionsCache` 加载逻辑一致，可直接
      复用该缓存），并在选项列表最前面插入一个固定的"用户ID"选项（`metadataFieldId`
      传 `null`）；应用字段名称/编码输入框、转换方式下拉 + 条件展示固定值/脚本输入框
      （复用"同步字段映射"表格现有交互模式与样式类名前缀）、增行/删行、保存按钮（受
      `AppManagement:app:config:editAuth` 权限点控制）。

## 8. 文档

- [x] 8.1 更新 `权限资源.txt`：本次未新增权限点，确认无需改动（复用
      `AppManagement:app:config:editAuth`，已在文件第 98 行确认存在）。

## 9. 验证

- [x] 9.1 `./gradlew build` 编译通过；补充/运行单元测试覆盖：CAS `format` 参数分支
      （默认 JSON/显式 XML）、用户属性动态生成（含默认两行场景）、`appFieldCode`
      唯一性校验、转换脚本语法校验、OAuth `sub` 固定优先于映射配置。
- [ ] 9.2 前端 `npm run build` 类型检查通过；本地 `npm run dev` 手动验证"认证管理"
      标签页字段映射表格的增删行、保存、权限控制交互。
