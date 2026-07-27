## Context

`tab_permission`（权限点主数据表）没有 `parent_id` 字段，只有一个三段式编码字符串 `code`（`模块:资源:操作`，如 `OrgManagement:org:view`）。当前权限点管理页面（`PermissionManagementView.vue`）是纯分页表格，94 条数据挤在一起看不出模块归属。仓库里已经有两种"把扁平数据展示成树"的先例，需要在它们之间选一个，而不是发明第三种：

1. `MenuManagementView.vue`（菜单管理）：`tab_menu` 表自带真实的 `parent_id`，用懒加载 `el-tree` + 右侧表格实现任意层级的真实树。
2. `RoleManagementView.vue` 里权限点勾选控件：`tab_permission` 没有 `parent_id`，纯前端按 `code.split(':')[0]`（模块名）分组构造两层虚拟树（第一层是分组虚拟节点，`id` 形如 `group:模块名`，不对应任何真实权限点；第二层是叶子节点，即真实权限点），数据源是已有的 `GET /api/permissions/options`（`PermissionOptionVO`：仅 `id`/`name`/`code`，供"只读选项/勾选"场景使用，明确不含审计字段）。

另外，`tab_menu` 种子数据（`V1__init_schema.sql`）在组织/用户/任职/应用四个模块下核对后确认遗漏了 `xxx:importTemplate`/`xxx:import` 两个按钮节点（其余模块——角色、权限点、管理员——核对无遗漏，因为它们本来就没有导入功能）。`权限资源.txt` 和 `tab_permission`（`V6__seed_permissions_and_super_admin.sql`）里这 8 条编码都已经登记齐全，前端四个页面的"下载导入模板"/"批量导入"按钮也已实现能跑；`tab_menu` 单纯是一份档案性质的资源目录（`rbac-permission-authorization` change 的 proposal.md 已明确它"不参与运行时鉴权判断"），这次只是把遗漏的档案数据补齐，不涉及任何鉴权逻辑或表结构变更。

## Goals / Non-Goals

**Goals:**
- 权限点管理页面改为按模块分组的两层树形展示，管理交互（新增/编辑/详情/启停用/删除）保持不变。
- 补全 `tab_menu` 里组织/用户/任职/应用四个模块下缺失的导入相关按钮资源节点，使其与 `权限资源.txt` 一致。

**Non-Goals:**
- 不给 `tab_permission` 加 `parent_id`。理由：`tab_menu.parent_id` 和一个新的 `tab_permission.parent_id` 会是两套物理上独立、需要人工同步的层级数据，历史已经证明这类"两处平行维护的资源清单"容易长期漂移不一致（本次要修的第二个问题正是这类漂移的产物）。权限点编码本身的三段式结构已经是稳定、够用的层级信息来源，没必要再建一张表。
- 不改动运行时鉴权逻辑（`IdentityAuthFilter`/`AuthorizationService`），本次两处修改都是纯展示层 + 档案数据补录，不影响 `hasPermission` 的判断路径。
- 不改动 `权限资源.txt`、不改动 `tab_permission`/`V6` 迁移、不改动四个业务页面的导入按钮功能代码。
- 不追加分页；树形展示天然需要一次性拿到全量数据，不再分页加载。

## Decisions

### 1. 权限点管理树用"虚拟分组两层树"，复用 `RoleManagementView.vue` 现成的分组算法

按 `code.split(':')[0]` 取模块名分组，第一层是不落库的虚拟分组节点，第二层是真实权限点叶子节点。放弃"给 `tab_permission` 加 `parent_id` 做成 `MenuManagementView.vue` 那种真实层级树"的方案，理由见上面 Non-Goals。

两层（而不是按模块+资源两级、三层）的取舍：现有 `RoleManagementView.vue` 的勾选树就是两层，为了同一份权限编码数据在两个不同页面里呈现一致的分组粒度、降低用户认知负担，本次沿用两层，不引入新的三层约定。

### 2. 新增一个不分页、返回完整字段的权限点查询接口，不复用 `PermissionOptionVO`，不改造现有分页接口

现有 `GET /api/permissions/options` 返回 `PermissionOptionVO`（仅 `id`/`name`/`code`），供 `RoleManagementView.vue` 的勾选树复用，其类注释明确写着"不包含审计字段等管理视图才需要的信息"。权限点管理的树形视图每个叶子节点需要展示 `status`（渲染启用/停用标签、决定"启用/停用"按钮显示哪个）和 `showOrder`，往 `PermissionOptionVO` 里加这些字段会污染一个已经被角色模块复用、语义明确的"精简选项"DTO。

也不打算给现有 `GET /api/permissions`（分页接口）加一个"不分页"参数（如 `pageSize=-1`）：分页接口的语义就是分页，混入"传特定值代表不分页"这种隐式约定不如显式加一个新接口清楚，`GET /api/permissions/options` 的先例已经确立了"树形/全量场景另开一个不分页接口"这个约定。

选定方案：新增 `GET /api/permissions/list`，直接复用已有的 `PermissionVO`（`id`/`name`/`code`/`showOrder`/`remark`/`status`/审计字段——字段已经全，不用新建 DTO），Service 层新增 `getAllList()`：查询未逻辑删除的全部权限点（不筛选状态，停用的权限点也要能在管理树里看到并重新启用），按 `showOrder` **升序**、`id` 升序排列——序号越小排在越前面。

这个排序方向和同模块里现有的分页查询（`GET /api/permissions`）、精简选项查询（`GET /api/permissions/options`）的"`showOrder` 降序、值越大越靠前"约定相反，是本次树形展示特意选择的顺序（用户要求"序号小的排在前面"，符合"编号即顺位"的直觉），只影响这一个新接口和权限点管理树的展示，不改变分页表格、角色勾选树两处既有场景的排序行为。



### 3. `tab_menu` 补录用一条新的 Flyway 迁移（`V7`），只做增量 INSERT，不改表结构

新文件 `V7__add_missing_import_menu_resources.sql`，按 `V1__init_schema.sql` 里同一批按钮节点的写法（`SELECT id FROM tab_menu WHERE code = 'xxx:xxx:view'` 取父节点 id，`resource_type=2`，`create_by`/`update_by` 用 `'admin'` 保持和 `V1` 一致），给以下 4 个父节点各补 2 条子节点：
- `OrgManagement:org:view` 下补 `OrgManagement:org:importTemplate`（下载组织导入模板）、`OrgManagement:org:import`（批量导入组织）
- `UserManagement:user:view` 下补 `UserManagement:user:importTemplate`（下载用户导入模板）、`UserManagement:user:import`（批量导入用户）
- `PositionManagement:position:view` 下补 `PositionManagement:position:importTemplate`（下载任职导入模板）、`PositionManagement:position:import`（批量导入任职记录）
- `AppManagement:app:view` 下补 `AppManagement:app:importTemplate`（下载应用导入模板）、`AppManagement:app:import`（批量导入应用）

`show_order` 取比该父节点下现有最小 `show_order`（当前各模块按钮节点是 10~60，删除按钮是 10）更小的值（如 5、0），保证"下载模板"“批量导入"排在已有的增删改查按钮之后，不打乱已有按钮的相对顺序。

## Risks / Trade-offs

- [风险] 树形视图一次性拉全量权限点（当前 94 条，未来可能继续增长）→ 缓解：单条记录字段很小（无关联对象），94 条量级对前端渲染和一次 HTTP 请求毫无压力；即使增长到几百条也远低于需要虚拟滚动的量级，不做提前优化。
- [风险] `GET /api/permissions/list` 和 `GET /api/permissions` 分页接口、`GET /api/permissions/options` 三个接口并存，未来维护者可能搞不清何时用哪个、以及为什么排序方向不一样（新接口升序、另外两个降序）→ 缓解：三个接口分别写清楚各自的 Javadoc/`@Operation` 说明（分页表格场景 / 精简选项勾选场景 / 管理视图全量树场景），并在 `getAllList()` 方法注释里明确写出"升序，序号小的排前面，与本模块其余查询的降序约定不同，这是权限点管理树形展示特意选择的顺序"，避免后续有人"顺手"把排序方向改得和其他接口一致。
- [风险] `V7` 迁移里 `SELECT id FROM tab_menu WHERE code = 'xxx:xxx:view'` 依赖 `V1` 种子数据必须已经跑过且未被手动改动 → 缓解：这和现有 `V3`/`V6` 迁移用 `SET @x := (SELECT ...)` 取关联 id 的既有模式完全一致，风险不是本次新增的。

## Migration Plan

1. 后端：新增 `PermissionController#list` + `PermissionService#getAllList()` + 对应单元测试；新增 `V7` 迁移文件。
2. 前端：`src/api/permission.ts` 新增 `getPermissionList()` 封装；`stores/permission.ts` 从"分页 store"改造为"加载全量列表 + 前端分组构造树"的 store（可参考 `RoleManagementView.vue` 里已有的分组函数抽取/复用思路，避免逻辑重复两份）；`PermissionManagementView.vue` 把 `el-table` + `el-pagination` 换成 `el-tree`（分组节点/叶子节点渲染、行内操作按钮迁移到叶子节点）。
3. 数据库：执行 `./gradlew bootRun`（或任意触发 Flyway 的方式）自动应用 `V7` 迁移；无需手工数据回填。
4. 回滚：`V7` 只做 INSERT，如需回滚，手工 `DELETE FROM tab_menu WHERE code IN (...)` 这 8 条编码即可，不影响其他数据；前端/后端代码回滚走正常的 git revert。

## Open Questions

（无）
