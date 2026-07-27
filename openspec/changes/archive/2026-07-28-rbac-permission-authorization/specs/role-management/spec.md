## MODIFIED Requirements

### Requirement: 角色详情查询
系统 SHALL 提供按 id 查询角色详情的接口，返回结果包含角色名称、角色编码、显示序号、备注、状态、已分配的权限点列表（每项含权限点 id、名称、编码），以及创建人、创建时间、更新人、更新时间。

#### Scenario: 查询存在的角色
- **WHEN** 客户端调用 `GET /api/roles/{id}` 且该角色存在且未被逻辑删除
- **THEN** 系统返回该角色的完整信息，含其已分配的权限点列表

#### Scenario: 查询不存在的角色
- **WHEN** 客户端调用 `GET /api/roles/{id}` 且该 id 不存在或已被逻辑删除
- **THEN** 系统返回业务错误（非零 `code`），不返回 HTTP 500

#### Scenario: 角色未分配任何权限点
- **WHEN** 客户端调用 `GET /api/roles/{id}` 且该角色当前没有分配任何权限点
- **THEN** 系统返回该角色信息，权限点列表为空数组

### Requirement: 新增角色
系统 SHALL 支持创建角色，角色名称（`name`）、角色编码（`code`）为必填项；备注可选；显示序号默认 `0`；可同时提交 0 到多个权限点 id（`permissionIds`），一并建立角色与权限点的关联。新建角色默认状态为启用（`2000`）。角色编码在未被逻辑删除的角色范围内须保持唯一。

#### Scenario: 成功创建角色
- **WHEN** 客户端调用 `POST /api/roles`，携带合法的 `name`、`code`
- **THEN** 系统创建该角色，状态为 `2000`（启用），并返回创建后的角色信息

#### Scenario: 必填字段缺失时拒绝创建
- **WHEN** 客户端调用 `POST /api/roles` 且未携带 `name` 或 `code` 之一
- **THEN** 系统拒绝创建，返回参数校验错误

#### Scenario: 角色编码已存在时拒绝创建
- **WHEN** 客户端调用 `POST /api/roles`，携带的 `code` 与某个未被逻辑删除的角色编码相同
- **THEN** 系统拒绝创建，返回业务错误（非零 `code`）

#### Scenario: 创建角色时同时分配权限点
- **WHEN** 客户端调用 `POST /api/roles`，携带合法的 `name`、`code` 及非空的 `permissionIds` 数组
- **THEN** 系统创建该角色，并建立该角色与 `permissionIds` 中每个权限点的关联

#### Scenario: 创建角色时不携带权限点
- **WHEN** 客户端调用 `POST /api/roles`，未携带 `permissionIds` 或该数组为空
- **THEN** 系统创建该角色，不建立任何权限点关联

### Requirement: 更新角色
系统 SHALL 支持更新角色的名称、编码、显示序号、备注、权限点分配（`permissionIds`，整体覆盖式同步：先清空该角色既有的全部权限点关联，再按提交的 `permissionIds` 重新建立）；不通过本接口修改状态。更新时角色编码同样须在未被逻辑删除的角色范围内保持唯一（排除自身）。

#### Scenario: 成功更新角色
- **WHEN** 客户端调用 `PUT /api/roles/{id}`，携带合法的 `name`、`code` 等字段
- **THEN** 系统更新该角色信息并返回更新后的结果，`status` 保持不变

#### Scenario: 更新时角色编码与自身相同不视为冲突
- **WHEN** 客户端调用 `PUT /api/roles/{id}`，携带的 `code` 与该角色自身当前编码相同
- **THEN** 系统正常更新，不因编码冲突而拒绝

#### Scenario: 更新时角色编码与其他角色冲突
- **WHEN** 客户端调用 `PUT /api/roles/{id}`，携带的 `code` 与另一个未被逻辑删除的角色编码相同
- **THEN** 系统拒绝更新，返回业务错误（非零 `code`）

#### Scenario: 更新角色时整体覆盖权限点分配
- **WHEN** 客户端调用 `PUT /api/roles/{id}`，携带的 `permissionIds` 与该角色当前已分配的权限点不同
- **THEN** 系统将该角色的权限点关联整体替换为 `permissionIds` 中的内容，此前已分配但不在新列表中的权限点关联被移除

#### Scenario: 更新角色时清空权限点分配
- **WHEN** 客户端调用 `PUT /api/roles/{id}`，`permissionIds` 为空数组
- **THEN** 系统移除该角色此前全部的权限点关联，更新后该角色不再拥有任何权限点

## ADDED Requirements

### Requirement: 角色管理前端界面新增权限点分配交互
角色管理页面（`/permission/roles`）的新增/编辑弹窗 SHALL 内嵌权限点勾选控件；由于权限点数量可达上百条，SHALL 按权限点编码冒号分隔的第一段（模块）分组展示为可勾选的树形结构，而非普通下拉多选；打开新增/编辑弹窗时 SHALL 按需请求权限点选项接口（`GET /api/permissions/options`）加载全量可选权限点，页面进入时以及仅浏览/翻页角色列表 SHALL NOT 触发该查询。角色详情页面（`/permission/roles/:id`）SHALL 以同样的分组结构只读展示该角色已分配的权限点，不提供勾选交互。

#### Scenario: 打开新增或编辑弹窗时按需加载权限点选项
- **WHEN** 用户点击"新增"或某一行的"编辑"打开角色弹窗
- **THEN** 系统此时才请求权限点选项接口 `GET /api/permissions/options`，请求完成后再展示弹窗

#### Scenario: 页面进入时不预先请求权限点选项
- **WHEN** 用户打开角色管理页面，且不打开新增或编辑弹窗
- **THEN** 系统不请求权限点选项接口 `GET /api/permissions/options`

#### Scenario: 权限点按模块分组展示
- **WHEN** 用户打开角色新增或编辑弹窗
- **THEN** 权限点勾选控件按编码的模块段分组展示，每组下是该模块内的具体权限点，用户可按模块或逐项勾选

#### Scenario: 编辑角色时回填已分配权限点
- **WHEN** 用户点击某一行的"编辑"打开角色弹窗
- **THEN** 该角色已分配的权限点在权限点勾选控件中呈现为已勾选状态

#### Scenario: 角色详情页只读展示已分配权限点
- **WHEN** 用户查看某个角色的详情页面
- **THEN** 页面按模块分组只读展示该角色已分配的全部权限点，不提供勾选交互
