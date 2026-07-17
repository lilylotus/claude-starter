## Why

`权限资源.txt`（仓库根目录）已经按三段式格式（模块:资源:操作）统计出全部 8 个已实现
管理页面的菜单/按钮资源编码（60 条），但 `tab_menu` 表目前没有任何种子数据——菜单
管理页面（`/system/menus`）打开后是空的，角色管理页面日后要做"角色分配菜单/按钮权限"
时也没有真实资源可选。这次改动把 `权限资源.txt` 里的清单落地成一份 Flyway 种子数据
迁移脚本，初始化 `tab_menu` 表。

## What Changes

- 新增 Flyway 迁移脚本 `V11__seed_menu_resource_data.sql`，按三层结构写入种子数据：
  - 第一层（`parentId = 0`，资源类型=菜单）：4 个侧边栏一级导航分组（身份管理、应用
    管理、权限管理、系统管理），编码沿用 `router/menu.ts` 里已有的分组 key（`identity`
    /`application`/`permission`/`system`）。
  - 第二层（资源类型=菜单）：8 个已实现管理页面，编码取自 `权限资源.txt` 里各页面的
    `:view` 条目（如 `OrgManagement:org:view`），挂在对应一级分组下。
  - 第三层（资源类型=按钮）：`权限资源.txt` 里除 `:view` 外的全部 52 条按钮编码
    （新增/编辑/详情/启用/停用/删除，字典管理拆分为字典类型/字典项两组按钮），挂在
    对应页面节点下。
- 不涉及应用密钥（`/application/secret`）、操作日志（`/system/logs`）——这两个页面
  尚未实现，`权限资源.txt` 里本来就没有它们的编码。

## Capabilities

### Modified Capabilities
- `menu-management`：`tab_menu` 表新增一份覆盖当前全部已实现页面的种子数据，使资源树
  查询接口（`GET /api/menus/tree` 等）在空库状态下也能返回有意义的树形结构；不涉及
  接口行为变化。

## Impact

- 后端：新增 `backend/src/main/resources/db/migration/V11__seed_menu_resource_data.sql`。
- 前端：无变化。
- 数据库：`tab_menu` 表新增 64 行种子数据（4 个一级分组 + 8 个页面菜单 + 52 个按钮）。
