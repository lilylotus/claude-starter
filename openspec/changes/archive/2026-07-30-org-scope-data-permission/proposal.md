## Why

`tab_admin_org_scope`（管理员的"管辖组织范围"，含是否递归子组织）目前只是纯 CRUD/展示数据——三次历史 change（`admin-management`、`rbac-permission-authorization` 等）都明确把"按管辖组织范围过滤业务数据"列为故意排除的后续工作。现在需要把这个"地基"用起来：组织树、任职列表、应用列表这些查询接口，应当按当前登录管理员配置的管辖组织范围收窄返回结果；管理员没有配置任何管辖组织范围时，视为不受限制（能看到全部数据），保持现有行为不变——这是纯粹的增量收紧，不引入新的默认拒绝行为。

## What Changes

- 新增一个"管辖组织范围解析"能力：根据当前登录用户对应的启用状态管理员身份，解析出其管辖组织范围——若未配置任何范围，解析结果为"不受限制"；若配置了范围，解析为一个允许访问的组织 id 集合（`include_children = 1` 的条目按组织树展开为该组织及其全部子孙组织 id，多条配置取并集）。组织树递归子组织展开在应用层（Java 内存）完成，不使用 SQL 递归 CTE（目标数据库为 MySQL 5.7，不支持 `WITH RECURSIVE`）。
- 组织树/组织列表相关接口（`GET /api/orgs/tree`、`/api/orgs/tree/children`、`/api/orgs/children`）按解析结果过滤：受限时只返回范围内的组织节点；若某个受限管理员的范围是组织树中间层的一个节点，该节点在响应中表现为"虚拟根节点"——其祖先节点不出现在任何树/列表响应中。
- 任职列表接口（`GET /api/positions?orgId=`）：受限时，若请求的 `orgId` 不在解析出的允许组织 id 集合内，返回空分页结果（不报错，因为按当前用户身份判断这只是"该组织没有任职记录"的自然表现，不暴露"存在但你看不到"的信息）。
- 应用列表接口（`GET /api/apps`）：目前完全没有按组织过滤的语义；受限时追加 `org_id IN (:allowedOrgIds)` 过滤条件；不受限时行为不变。
- **不在本次范围**：用户列表（`GET /api/users`）——用户与组织是通过 `tab_user_position` 的间接关系（一个用户可能有多个跨组织任职），判断"用户是否在管辖范围内"需要额外的 JOIN/EXISTS 语义设计，复杂度和风险明显更高，留到后续独立 change。

## Capabilities

### New Capabilities
- `org-scope-data-permission`：管辖组织范围解析规则（未配置=不受限制、`include_children` 展开、多条配置取并集、解析结果的复用方式），以及组织树/列表在受限时的"虚拟根节点"响应形态。

### Modified Capabilities
- `org-management`：「组织树查询」「组织树懒加载子节点查询」「直属子组织查询」三个既有 Requirement 新增"按当前管理员管辖组织范围过滤"的行为。
- `position-management`：「任职记录按组织分页查询」新增"请求的 `orgId` 超出当前管理员管辖范围时返回空分页"的行为。
- `application-management`：「应用分页查询」新增"按当前管理员管辖组织范围过滤"的行为（该接口此前完全没有组织维度的过滤逻辑，本次是新增而非收紧一个已有过滤条件）。

## Impact

- 后端：新增一个"管辖组织范围解析"服务（大概率落在 `admin` 模块或新建一个跨模块的领域服务，具体位置见 design.md），供 `org`、`user`（本次不涉及）、`application` 三个模块的 Controller/Service 调用；`OrgServiceImpl`、`AppServiceImpl` 的现有查询方法需要接入过滤条件；`PositionController`/`PositionServiceImpl` 需要在现有 `orgId` 必填参数校验基础上叠加范围校验。
- 不改变任何接口的请求/响应 DTO 结构，只改变返回的数据行；不改变权限点（`权限资源.txt`）体系，这是数据权限而非菜单/按钮权限。
- 前端无需改动——它渲染的是接口返回的数据，接口返回什么就展示什么。
- 不引入新的第三方依赖。
