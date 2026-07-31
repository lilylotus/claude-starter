## MODIFIED Requirements

### Requirement: 菜单资源种子数据
系统 SHALL 在数据库初始化阶段（Flyway 迁移）预置覆盖当前已实现管理页面的菜单/按钮资源种子数据，编码与命名与仓库根目录 `权限资源.txt` 中记录的三段式编码（模块:资源:操作）保持一致。种子数据 SHALL 按三层组织：第一层为侧边栏一级导航分组（资源类型=菜单，`parentId = 0`），第二层为已实现的管理页面（资源类型=菜单），第三层为该页面下的按钮（资源类型=按钮）。种子数据 SHALL NOT 包含尚未实现页面（如应用密钥）的资源编码。组织管理、用户管理、任职管理、应用管理四个页面已实现"下载导入模板""批量导入"功能，其第三层按钮种子数据 SHALL 包含对应的 `xxx:importTemplate`、`xxx:import` 两条资源编码（与其余 `add`/`detail`/`edit`/`enable`/`disable`/`delete` 按钮节点同级，挂在同一个页面节点下）。

#### Scenario: 数据库初始化后可查询到完整的菜单资源树
- **WHEN** 数据库完成 Flyway 迁移（含菜单资源种子数据迁移脚本）后，客户端调用 `GET /api/menus/tree`
- **THEN** 返回结果包含 5 个一级分组节点（身份管理、应用管理、权限管理、系统管理、日志管理），每个分组下挂载对应的已实现页面节点，每个页面节点下挂载该页面的按钮节点，节点总数、层级关系与 `权限资源.txt` 记录的编码清单一致

#### Scenario: 种子数据不包含未实现页面的资源
- **WHEN** 查询菜单资源树或按编码查找资源
- **THEN** 应用密钥（`/application/secret`）对应的资源编码不存在于种子数据中

#### Scenario: 组织/用户/任职/应用四个页面的导入相关按钮资源存在于种子数据中
- **WHEN** 数据库完成 Flyway 迁移后，客户端调用 `GET /api/menus/tree`
- **THEN** `OrgManagement:org:view`、`UserManagement:user:view`、`PositionManagement:position:view`、`AppManagement:app:view` 四个页面节点下，均能各自找到 `xxx:importTemplate`（下载导入模板）与 `xxx:import`（批量导入）两个按钮子节点

#### Scenario: 操作日志、登录日志挂载在日志管理分组下
- **WHEN** 数据库完成 Flyway 迁移后，客户端调用 `GET /api/menus/tree`
- **THEN** "日志管理"一级分组下能找到编码为 `OperationLogManagement:log:view`（操作日志）与 `LoginLogManagement:loginLog:view`（登录日志）的两个页面节点，编码与此前挂在"系统管理"下时完全一致，仅 `parentId` 发生变化
