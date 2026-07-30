# org-scope-data-permission Specification

## Purpose
把 `tab_admin_org_scope`（管理员的"管辖组织范围"，含是否递归子组织）从纯 CRUD/展示数据用起来：组织树/列表、任职列表、应用列表这些查询接口，按当前登录管理员配置的管辖组织范围收窄返回结果；管理员没有配置任何管辖组织范围时，视为不受限制。用户列表（`GET /api/users`）暂不纳入——用户与组织是通过 `tab_user_position` 的间接关系，留待后续独立能力处理。与 `org-management`/`position-management`/`application-management` 协作，是这三个能力查询接口的一层数据可见性收紧。

## Requirements

### Requirement: 管辖组织范围解析
系统 SHALL 提供"解析当前登录用户管辖组织范围"的能力：当前用户不存在启用状态的管理员身份，或存在但未配置任何 `tab_admin_org_scope` 记录时，解析结果 SHALL 为"不受限制"；存在至少一条管辖组织范围配置时，解析结果 SHALL 为一个允许访问的组织 id 集合，其中 `include_children` 为真的配置项 SHALL 展开为该组织自身及其全部子孙组织 id，多条配置项之间取并集。该解析 SHALL 不使用缓存，每次调用实时查询当前数据。

#### Scenario: 未配置管辖组织范围时不受限制
- **WHEN** 当前登录用户对应的启用状态管理员身份没有任何 `tab_admin_org_scope` 记录
- **THEN** 解析结果为不受限制

#### Scenario: 没有启用状态管理员身份时不受限制
- **WHEN** 当前登录用户没有对应的启用状态管理员身份（无管理员记录，或管理员记录已停用）
- **THEN** 解析结果为不受限制

#### Scenario: 配置了递归范围时展开子孙组织
- **WHEN** 当前登录用户的管理员身份配置了一条 `include_children = 1` 的管辖组织范围记录，对应组织存在子孙组织
- **THEN** 解析结果包含该组织自身及其全部未逻辑删除的子孙组织 id

#### Scenario: 配置了非递归范围时只包含自身
- **WHEN** 当前登录用户的管理员身份配置了一条 `include_children = 0` 的管辖组织范围记录
- **THEN** 解析结果只包含该组织自身的 id，不包含其子孙组织

#### Scenario: 多条配置取并集
- **WHEN** 当前登录用户的管理员身份配置了多条管辖组织范围记录
- **THEN** 解析结果为每条记录各自解析结果的并集
