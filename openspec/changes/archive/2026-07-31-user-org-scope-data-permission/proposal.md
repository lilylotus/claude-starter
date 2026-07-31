## Why

`2026-07-30-org-scope-data-permission` 那次改动把"按当前管理员管辖组织范围过滤数据"接入了组织树/列表、任职列表、应用列表三类查询接口，但明确把用户列表（`GET /api/users`）列为故意排除的后续工作——原因是用户与组织是通过 `tab_user_position` 的间接多对多关系（一个用户可能跨组织有多条任职记录），"用户是否在管辖范围内"需要额外设计判断语义，当时风险和复杂度更高，留到后续独立 change。现在需要把这个遗留缺口补上，让"管辖组织范围"对用户列表也生效，消除"组织树、任职、应用都会收紧，唯独用户列表不会"的行为不一致。

## What Changes

- 复用既有的 `OrgScopeService.resolveAllowedOrgIds(userId)` 解析当前登录管理员的管辖组织范围：未配置任何管辖范围时行为完全不变（不受限）。
- `GET /api/users` 分页查询在受限时，于现有过滤条件（未逻辑删除、姓名/手机号/身份证号模糊搜索）基础上，追加一个跨表条件：该用户在 `tab_user_position` 中存在至少一条未被逻辑删除、且所属组织 id 落在管辖范围内的任职记录（"任一任职落在范围内即可见"语义，不要求全部任职都落在范围内）。没有任何未删除任职记录的用户，在受限时视为不在管辖范围内，不出现在列表中。
- 该跨表条件按项目既有约定（多表关联查询写在 MyBatis XML 里，不在 Java 端用 `LambdaQueryWrapper` 拼 `EXISTS` 或先查任职记录再在内存里过滤用户列表）实现，落在 `UserMapper.xml`。
- **不在本次范围**：用户详情查询（`GET /api/users/{id}`）、新增/更新/启停用/逻辑删除/重置密码等写操作接口——延续 `org-scope-data-permission` 那次"只收紧列表/树查询、不触及详情和写操作"的既有先例，不做数据权限层面的写操作拦截。

## Capabilities

### New Capabilities
(无)

### Modified Capabilities
- `user-management`：「用户分页查询」新增"受限时按当前管理员管辖组织范围过滤——仅返回存在至少一条任职落在管辖范围内的用户"的行为。

## Impact

- 后端：`UserServiceImpl.getPage` 注入 `OrgScopeService`（类比 `PositionServiceImpl.getPage` 的既有写法），解析管辖范围后传给数据访问层；`UserMapper` 新增一个按条件分页查询方法（关联/`EXISTS` 查询 `tab_user_position`），SQL 写在 `UserMapper.xml`；`UserMapper` 现有 `countByColumnValue` 保持不变。
- 不改变任何接口的请求/响应 DTO 字段结构，只改变 `GET /api/users` 返回的数据行；不改变权限点（`权限资源.txt`）体系，这是数据权限而非菜单/按钮权限。
- 前端无需改动——渲染的是接口返回的数据，接口返回什么就展示什么。
- 不引入新的第三方依赖。
