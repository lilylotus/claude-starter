## MODIFIED Requirements

### Requirement: CAS 票据验证
`GET /api/authn/cas/{appId}/p3/serviceValidate` SHALL 校验 `ticket` 存在、未过期、
未被消费过，且签发时绑定的 `service` 与本次请求的 `service` 一致；校验通过后 SHALL
将该票据标记为已消费（一次性），并返回认证成功响应（含用户标识 `cas:user`，固定取
用户 `code`）；校验不通过时 SHALL 返回认证失败响应。

接口 SHALL 支持 `format` 查询参数（大小写不敏感），取值为 `XML` 时返回 XML 格式响应；
取值缺省或为其它任意值时 SHALL 返回 JSON 格式响应（默认 JSON）。响应中除 `cas:user`
外的用户属性（`cas:attributes` 或 JSON 对应节点）SHALL 按该应用配置的用户信息字段映射
动态生成（未配置任何映射时使用默认的"用户ID + 姓名"两个属性）。

#### Scenario: 合法票据校验成功且不可重复使用
- **WHEN** 调用方使用一个刚签发、`service` 匹配的合法票据发起验证请求
- **THEN** 系统返回认证成功响应；调用方用同一票据再次发起验证请求时，系统返回认证失败响应

#### Scenario: service 不一致时校验失败
- **WHEN** 调用方使用的票据是为另一个 `service` 签发的
- **THEN** 系统返回认证失败响应，不消费该票据的有效性判定结果泄露给非授权调用方

#### Scenario: 未指定 format 时默认返回 JSON
- **WHEN** 调用方发起票据验证请求且未携带 `format` 参数
- **THEN** 系统返回 JSON 格式的认证成功/失败响应，而不是 XML

#### Scenario: format=XML 时返回 XML 格式响应
- **WHEN** 调用方发起票据验证请求并携带 `format=XML`
- **THEN** 系统返回 CAS 3.0 格式的 XML 认证成功/失败响应

#### Scenario: 用户属性按字段映射动态生成
- **WHEN** 某应用配置了一条用户信息字段映射（本地字段"姓名"→应用字段编码
  `displayName`，转换方式"不转换"），且票据校验成功
- **THEN** 响应的属性节点中包含键为 `displayName`、值为该用户姓名的属性，
  而不是固定的 `cas:name`

### Requirement: OAuth2 用户信息查询
`GET /api/authn/oauth/userinfo` SHALL 校验请求头 `Authorization: Bearer <access_token>`
携带的令牌存在且未过期，校验通过后 SHALL 返回该令牌绑定用户的基本身份信息；令牌缺失、
格式不正确或已过期时 SHALL 拒绝并返回 401。

响应体 SHALL 始终包含固定字段 `sub`（取用户 id，不受字段映射配置影响）；除 `sub` 外的
其余字段 SHALL 按该应用配置的用户信息字段映射动态生成（未配置任何映射时使用默认的
"用户ID + 姓名"两个字段）；若某条映射配置的应用侧字段编码恰好为 `sub`，该行配置的值
不生效，最终响应仍以协议规定的固定 `sub` 值为准。

#### Scenario: 合法令牌查询用户信息成功
- **WHEN** 调用方携带一个有效的 access token 请求用户信息接口
- **THEN** 系统返回该令牌签发时绑定用户的基本身份信息，响应体包含固定的 `sub` 字段

#### Scenario: 令牌过期或不存在时拒绝
- **WHEN** 调用方携带的 access token 已过期或不存在
- **THEN** 系统拒绝该请求，返回 401

#### Scenario: 用户信息字段按映射配置动态生成
- **WHEN** 某应用配置了两条用户信息字段映射（本地字段"用户ID"→应用字段编码 `id`，
  本地字段"姓名"→应用字段编码 `displayName`），且请求携带有效令牌
- **THEN** 响应体包含 `sub`、`id`、`displayName` 三个字段，不再包含固定的
  `username`/`name` 字段

#### Scenario: 映射字段编码与 sub 冲突时固定值优先
- **WHEN** 某应用的一条用户信息字段映射的应用侧字段编码被配置为 `sub`
- **THEN** 响应体的 `sub` 字段最终取值仍是协议规定的用户 id，不受该行映射配置的转换
  结果影响
