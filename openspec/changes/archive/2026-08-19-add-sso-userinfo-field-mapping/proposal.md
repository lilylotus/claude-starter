## Why

CAS 票据验证接口当前只返回固定的 XML 格式响应，用户属性硬编码只有 `cas:name`；OAuth2
`userinfo` 接口当前也硬编码只返回 `sub`/`username`/`name` 三个字段。接入的第三方应用
往往需要不同的字段命名/取值约定（如把 `姓名` 映射为应用侧的 `displayName`，或需要一个
固定值/派生值字段），现状无法满足；同时部分客户端更习惯 JSON 而不是 XML。

## What Changes

1. CAS 票据验证接口 `GET /api/authn/cas/{appId}/p3/serviceValidate` 新增 `format` 查询
   参数，支持 `XML`/`JSON`（大小写不敏感），**BREAKING**：默认值由现状事实上的
   "总是 XML" 改为 `JSON`——显式传 `format=XML` 可保留原有行为。
2. 新增"用户信息响应字段映射"配置：每个应用一份（CAS/OAuth2.0 共用，因为一个应用同一
   时间只启用一种协议），管理员在"认证管理"标签页配置一组映射行，本地字段从"元数据字段
   管理"里已启用的用户（USER）字段目录中选择（如姓名、编号、性别、手机号、身份证号、
   扩展字段1~10），另加一个固定的"用户ID"伪字段（不在元数据字段目录里，因为 id 是主键，
   不属于可开放配置字段）；每行还包含应用侧字段名称、应用侧字段编码、转换方式（不转换/
   固定值/脚本），整体替换语义保存；未保存过时默认视为两行——用户ID、姓名（不落库，
   查询与运行时用同一段现算逻辑兜底）。
3. CAS `<cas:attributes>`（含新增的 JSON 格式对应节点）与 OAuth2 `userinfo` 响应体中，
   除各自协议规定必须存在的固定标识外，其余属性/字段均由上述映射配置动态生成，替换掉
   此前硬编码的 `cas:name`/`username`/`name`。协议规定的固定标识本身不受映射配置影响：
   CAS 的 `cas:user` 继续固定取用户 `code`，OAuth2 的 `sub` 继续固定取用户 `id`。
4. 从现有 `FieldMappingTransformer`（app-sync-field-mapping 能力）中抽出 GraalVM 脚本
   沙箱执行逻辑，提炼为可复用的 `ScriptTransformExecutor`，供应用同步字段映射与本次
   新增的用户信息字段映射共同复用，避免两处重复维护脚本沙箱执行细节（超时、权限限制等
   安全相关逻辑）。

## Capabilities

### New Capabilities
（无，均归入下方已有能力的增量）

### Modified Capabilities
- `app-sso-protocol-runtime`: CAS 票据验证新增 `format` 参数（XML/JSON，默认 JSON）；
  CAS 响应属性与 OAuth2 用户信息响应字段改为按应用维度的字段映射配置动态生成。
- `app-auth-protocol-config`: 认证配置新增"用户信息响应字段映射"的查询、整体替换保存
  接口。
- `backend-common-utilities`: 新增可复用的转换脚本执行工具类 `ScriptTransformExecutor`。

## Impact

- 新增数据库表 `tab_app_userinfo_field_mapping`（Flyway 增量迁移文件，不改动 V1 基线）。
- 后端新增：字段映射实体/DTO/Mapper（含 LEFT JOIN `tab_metadata_field` 的 XML 查询）/
  Service/Controller、用户信息属性运行时解析组件、CAS JSON 响应构造工具；修改：
  `CasController`、`CasXmlResponses`、`OAuthController`、`AppProtocolGuard`（新增按
  appId 解析 appRefId 的公共方法）、`FieldMappingTransformer`（改为委托
  `ScriptTransformExecutor`）。不修改 `tab_metadata_field` 表结构/数据（USER 目录本次
  不新增 "id" 字段，用固定伪字段处理，见 design.md Decision 2）。
- 前端"应用配置"页"认证管理"标签页新增一张可编辑的字段映射表格（复用"同步字段映射"
  表格的交互模式：本地字段下拉选择、转换方式下拉 + 条件展示固定值/脚本输入框）。
- `权限资源.txt` 无需新增权限点（复用既有 `AppManagement:app:config:editAuth`）。
- **BREAKING**：CAS 票据验证接口默认响应格式从 XML 变为 JSON；已接入的 CAS 客户端如果
  依赖默认格式为 XML，需要改为显式传 `format=XML`。当前系统尚处于设计阶段，无真实第三方
  接入依赖此默认值。
