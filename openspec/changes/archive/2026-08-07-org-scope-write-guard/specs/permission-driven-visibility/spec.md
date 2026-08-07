## MODIFIED Requirements

### Requirement: 查询当前用户已授权权限编码
系统 SHALL 提供一个需要有效 `identity-token` 的接口，返回当前登录用户当前拥有的全部权限编码集合（与 `IdentityAuthFilter`/`AuthorizationService` 运行时鉴权所使用的判断依据一致）；该接口 SHALL 豁免"操作资源编码校验"（即调用方不需要预先拥有某个权限编码才能查询自己拥有哪些权限编码），但仍需携带格式合法的 `menu` 请求头。该接口响应 SHALL 额外携带 `orgScopeRestricted` 布尔字段，值等于当前登录用户的管辖组织范围解析结果是否受限（受限为 `true`，不受限为 `false`），供前端在权限编码之外判断是否需要收紧组织相关选择器的可选范围。

#### Scenario: 已登录用户查询自身权限编码集合
- **WHEN** 已登录用户携带有效 `identity-token` 调用查询当前用户权限编码的接口
- **THEN** 系统返回该用户当前拥有的全部权限编码集合，不因该用户尚未拥有任何具体权限编码而拦截此次查询本身

#### Scenario: 未登录调用被拦截
- **WHEN** 未携带有效 `identity-token` 调用该接口
- **THEN** 系统返回未登录业务错误，不返回权限编码集合

#### Scenario: 管辖组织范围受限时响应标识受限
- **WHEN** 当前登录用户的管辖组织范围解析结果为受限，调用查询当前用户权限编码的接口
- **THEN** 响应的 `orgScopeRestricted` 字段为 `true`

#### Scenario: 管辖组织范围不受限时响应标识不受限
- **WHEN** 当前登录用户的管辖组织范围解析结果不受限（未配置管辖范围，或没有启用状态的管理员身份），调用查询当前用户权限编码的接口
- **THEN** 响应的 `orgScopeRestricted` 字段为 `false`
