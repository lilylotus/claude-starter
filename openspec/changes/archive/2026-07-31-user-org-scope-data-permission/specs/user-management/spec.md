## MODIFIED Requirements

### Requirement: 用户分页查询
系统 SHALL 提供用户的分页查询接口，支持按姓名（`name`）、手机号（`mobile`）、身份证号（`idCard`）模糊搜索，三者均为可选参数，同时提供多个时为"与"关系；分页参数 `page`（默认 `1`）、`pageSize`（默认 `10`）均为可选；不包含已逻辑删除的用户；结果按 `showOrder` 降序、相同时按 `id` 升序排列。当前登录用户对应的启用状态管理员身份配置了管辖组织范围时，系统 SHALL 在上述过滤条件基础上追加"用户存在至少一条未被逻辑删除、且所属组织落在管辖范围内的任职记录"这一条件（任一任职落在范围内即可见，不要求全部任职都落在范围内）；未配置管辖组织范围（或没有启用状态的管理员身份）时不受此限制，行为与既有实现一致。

#### Scenario: 查询用户分页列表
- **WHEN** 客户端调用 `GET /api/users?page={page}&pageSize={pageSize}`
- **THEN** 系统返回未删除用户的分页结果，包含 `records`、`total`、`page`、`pageSize`

#### Scenario: 按姓名模糊搜索
- **WHEN** 客户端调用 `GET /api/users?name={keyword}`
- **THEN** 系统返回姓名包含 `keyword` 的未删除用户分页结果

#### Scenario: 按手机号模糊搜索
- **WHEN** 客户端调用 `GET /api/users?mobile={keyword}`
- **THEN** 系统返回手机号包含 `keyword` 的未删除用户分页结果

#### Scenario: 按身份证号模糊搜索
- **WHEN** 客户端调用 `GET /api/users?idCard={keyword}`
- **THEN** 系统返回身份证号包含 `keyword` 的未删除用户分页结果

#### Scenario: 组合条件搜索
- **WHEN** 客户端调用 `GET /api/users?name={n}&mobile={m}` 同时携带姓名与手机号关键字
- **THEN** 系统返回姓名包含 `n` 且手机号包含 `m` 的未删除用户分页结果

#### Scenario: 未配置管辖组织范围时不受限制
- **WHEN** 当前登录用户对应的启用状态管理员身份没有配置任何 `tab_admin_org_scope` 记录，调用 `GET /api/users`
- **THEN** 系统返回全部未删除用户的分页结果，行为与改动前一致

#### Scenario: 配置了管辖组织范围时只返回范围内用户
- **WHEN** 当前登录用户对应的启用状态管理员身份配置了管辖组织范围，调用 `GET /api/users`
- **THEN** 系统只返回存在至少一条未删除任职记录、且该任职记录所属组织落在管辖范围内的用户

#### Scenario: 用户跨组织任职时任一任职落在范围内即可见
- **WHEN** 某用户拥有多条任职记录，其中至少一条所属组织落在当前管理员管辖范围内，其余任职所属组织落在范围外
- **THEN** 该用户出现在 `GET /api/users` 的分页结果中

#### Scenario: 没有任职记录的用户在受限时不可见
- **WHEN** 当前登录用户受管辖组织范围限制，某用户没有任何未被逻辑删除的任职记录
- **THEN** 该用户不出现在 `GET /api/users` 的分页结果中
