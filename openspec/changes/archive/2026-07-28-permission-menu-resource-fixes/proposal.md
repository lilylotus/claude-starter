## Why

`rbac-permission-authorization` change 落地后暴露出两处遗留问题（都属于该 change 任务 7.4 "前端浏览器点击验证"当时未完成留下的验证缺口，不是本次新引入的需求）：

1. 权限点管理页面（94 条权限点）是纯分页表格，用户反馈"完整数据列表看不出来层次关系，后面也不好更新调整"——每条权限点的编码本身是 `模块:资源:操作` 三段式，天然带层级信息，但页面没有把这层信息用起来。
2. `tab_menu`（菜单管理资源树，供"菜单管理"页面 CRUD 使用）在组织/用户/任职/应用四个模块下遗漏了"下载导入模板"“批量导入"两个按钮资源节点——这两个操作对应的权限编码（`xxx:importTemplate`/`xxx:import`）已经在 `权限资源.txt` 和 `tab_permission`（`V6` 迁移）里登记齐全，前端四个页面的导入按钮功能也已实现能跑，唯独 `tab_menu` 的种子数据（`V1` 迁移）当初漏灌了这 8 条，导致菜单管理页面看到的资源目录树和权威清单（`权限资源.txt`）对不上。

## What Changes

- 权限点管理页面从"纯分页表格"改为"按编码第一段（模块）分组的两层虚拟树"展示，分组方式复用 `RoleManagementView.vue` 里权限点勾选控件已有的同款分组逻辑（模块 -> 该模块下的权限点叶子节点），不引入第二套分层约定。
- 后端新增一个不分页、返回完整字段（含 `status`/`showOrder`/`remark`）的权限点查询能力，供上述树形管理视图使用；现有的 `GET /api/permissions/options`（精简选项，供角色勾选树等场景复用）不改动、不混用。
- 新增一条 Flyway 迁移，给 `tab_menu` 补全组织/用户/任职/应用四个模块下缺失的 `importTemplate`/`import` 按钮资源节点（各 2 条，共 8 条），使其与 `权限资源.txt`/`tab_permission` 保持一致。
- 不改动 `权限资源.txt`（已经是对的）、不改动 `tab_permission`/`V6` 迁移（已经是对的）、不改动前端四个业务页面的导入按钮功能（已实现且能跑，本次只是给菜单管理的资源目录树补档案数据）。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `permission-management`：权限点管理列表展示从分页表格改为按模块分组的树形展示；新增一个不分页的完整字段查询接口。
- `menu-management`：菜单资源种子数据补全组织/用户/任职/应用四个模块的导入相关按钮资源节点（`ADDED Requirement` 层面是"种子数据完整性"，不是新的接口能力）。
- `role-management`：角色新增/编辑弹窗内的权限点勾选树（按模块分组），分组节点从展示编码前缀改为展示中文模块名，与权限点管理树的展示保持一致（复用同一个可选的标签解析函数，不是新起一套）。

## Impact

- 后端：`permission` 模块（新增 controller 方法 + service 方法 + 对应 VO/单元测试）；新增 Flyway 迁移文件（`tab_menu` 种子数据补录，不改表结构）。
- 前端：`PermissionManagementView.vue`、`stores/permission.ts`（改造为树形状态管理）、`src/api/permission.ts`（新增接口封装）；菜单管理页面本身不用改代码，重新执行迁移后资源树会自动多出 8 个节点。
- 数据库：新增一条种子数据迁移，不涉及表结构变更、不涉及已有数据的修改，纯增量 INSERT。
- 依赖：不新增第三方依赖。
