## ADDED Requirements

### Requirement: 表单字段渲染元数据接口豁免操作资源编码校验
`GET /api/form-fields/render-schema` 接口 SHALL 豁免"操作资源编码校验"（运行时权限
点判断，即 `IdentityAuthFilter` 基于 `menu` 请求头调用
`AuthorizationService.hasPermission` 的判断步骤），任何已登录用户携带有效
`identity-token` 与格式合法的 `menu` 请求头即可调用，不要求调用方持有该 `menu` 值
对应的具体权限编码，也不要求持有被查询 `bizType`（ORG/USER/POSITION/APP）对应的
业务管理权限点。该接口仍然要求有效 `identity-token`（未登录 SHALL 被拦截），且仍然
受首次登录强制改密状态拦截豁免同一份自助白名单约束。`/api/form-fields` 下其余接口
（分页查询、详情、新增、编辑、启用、停用、删除）不在本条豁免范围内，继续要求调用方
持有对应的 `FormFieldManagement` 权限点。

#### Scenario: 不持有对应业务管理权限点的用户可以调用渲染元数据接口
- **WHEN** 已登录用户携带有效 `identity-token` 调用 `GET /api/form-fields/render-schema?bizType=ORG`，且该用户当前权限编码集合既不包含 `FormFieldManagement:*` 也不包含 `OrgManagement:org:view`
- **THEN** 系统正常返回该 `bizType` 的渲染元数据，不因缺少上述权限编码而拦截

#### Scenario: 未登录调用被拦截
- **WHEN** 未携带有效 `identity-token` 调用 `GET /api/form-fields/render-schema`
- **THEN** 系统返回未登录业务错误，不返回渲染元数据

#### Scenario: 其余表单字段定义接口仍受权限点约束
- **WHEN** 不持有 `FormFieldManagement` 相关权限点的已登录用户调用
  `GET /api/form-fields`（分页查询）或其新增/编辑/启停用/删除接口
- **THEN** 系统按既有权限点校验拒绝该次调用，不因本次改动而放宽
