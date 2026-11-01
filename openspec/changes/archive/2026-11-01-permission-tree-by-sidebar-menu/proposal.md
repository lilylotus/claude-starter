## Why

权限点管理页面与角色管理的权限点勾选树/已分配权限点展示目前按权限编码第一段（模块名）平铺成二十多个分组，与用户在左侧侧边栏看到的"一级菜单 → 二级页面"结构不一致：管理员需要在脑中把"组织管理/用户管理/任职管理"对回"身份管理"，把审批、流程设计等模块对回各自页面，分配角色权限时不直观。需要让权限点的展示结构与左侧菜单保持一致。

## What Changes

- 共享工具 `frontend/src/utils/permissionTree.ts` 的 `buildPermissionTree` 由"按模块分组的两层树"改为"按左侧菜单组织的三层树"：第一层为侧边栏一级菜单（身份管理、应用管理、权限管理……），第二层为该一级菜单下的二级页面（组织管理、用户管理……），第三层为归属该页面的权限点。结构数据源直接复用 `src/router/menu.ts` 的 `MENU_GROUPS`，不新增第二份菜单配置。
- 权限点归属页面的规则（依次匹配）：① 编码与页面 `permissionKey` 完全相同；② 编码前两段（`模块:资源`）与某页面 `permissionKey` 前两段相同；③ 编码模块段与某页面 `permissionKey` 模块段相同时归入侧边栏顺序中该模块的第一个页面（如 `DictManagement:dictItem:*` → 字典管理、`AppAccessManagement:policy:*` → 应用访问授权、`WorkflowDesign:instance:terminate` → 流程模型）；④ 仍无法匹配的（目前只有 `PluginManagement:plugin:view`，侧边栏未挂载插件管理页面）归入末尾"其他"分组，二级节点按模块中文名（`PERMISSION_MODULE_LABELS`）展示。
- 没有任何权限点的页面（如占位页"应用密钥"）和一级菜单不展示。
- 三处调用方同步适配：
  - 权限点管理页面（`PermissionManagementView.vue`）：三层树，一级/二级节点展示数量角标；"全部展开"同时展开一级与二级节点。
  - 角色新增/编辑弹窗（`RoleManagementView.vue`）：三层勾选树，一级/二级节点支持全选/半选联动，提交时仍只提交叶子权限点 id。
  - 角色详情页（`RoleDetailView.vue`）：已分配权限点按"一级菜单 → 二级页面 → 权限点标签"只读展示。
- 不修改后端接口、数据库、权限编码集合与 `权限资源.txt`。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `permission-management`：权限管理前端界面的树形结构由"按模块分组的两层树"改为"按左侧菜单组织的三层树"。
- `role-management`：角色权限点勾选树与角色详情已分配权限点展示改为与左侧菜单一致的三层结构。

## Impact

- 前端：`src/utils/permissionTree.ts`、`src/views/permission/permission/PermissionManagementView.vue`、`src/views/permission/role/RoleManagementView.vue`、`src/views/permission/role/RoleDetailView.vue`。
- 维护约定：新增侧边栏页面时只需在 `router/menu.ts` 登记，权限点树自动跟随；新增模块仍需在 `PERMISSION_MODULE_LABELS` 补中文名（"其他"分组兜底时使用）。
- 无后端、数据库、依赖变更。

## Implementation Result

- `src/utils/permissionTree.ts`：`buildPermissionTree(items, menuGroups = MENU_GROUPS)` 改为按侧边栏菜单构造三层树（一级菜单 `menu:<key>` → 二级页面 `page:<path>` → 权限点数字 id），节点新增 `level`（`menu`/`page`）与叶子数量 `count`；新增 `collectVirtualNodeIds` 供"全部展开"使用；原 `resolveGroupLabel` 参数移除，`PERMISSION_MODULE_LABELS`/`resolvePermissionModuleLabel` 保留并用于"其他"分组。
- `PermissionManagementView.vue`：使用三层树，一级/二级节点展示名称 + 数量角标并以字重区分层级；"全部展开"同时展开两层虚拟节点。
- `RoleManagementView.vue`：勾选树改为三层，增加节点插槽展示一级/二级数量角标；提交逻辑（只保留数字 id）未改动。
- `RoleDetailView.vue`：已分配权限点改为"一级菜单 → 二级页面行（页面名 + 权限点标签）"，二级行用小圆点延续链式视觉语言。
- 验证：`npm run build`（vue-tsc + vite build）通过；通过 Vite `ssrLoadModule` 加载真实 `buildPermissionTree`，对 `权限资源.txt` 的 151 个权限编码构树：151 个叶子全部出现、无丢失，10 个一级节点（9 个侧边栏菜单 + "其他"），"其他"下仅"插件管理"1 个权限点，"应用密钥"占位页因无权限点未展示，`WorkflowDesign:instance:terminate` 归入"流程模型"，`DictManagement:dictItem:*` 归入"字典管理"，均与设计一致。
- 浏览器验证（本地开发环境，admin 登录）：权限点管理页一级节点按侧边栏顺序展示 9 个菜单 + "其他"，默认收起，数量角标正确（身份管理 40、应用管理 15、权限管理 32 等），"全部展开"展开 36 个虚拟节点、共 187 个节点（151 权限点 + 36 虚拟节点），叶子行内操作正常；角色编辑弹窗（超级管理员）默认收起、10 个一级节点全选回显，取消勾选"组织管理"后其 10 个权限点同步取消、"身份管理"变为半选，验证后点击"取消"关闭、未提交；角色详情页（超级管理员）按"一级菜单 → 二级页面 → 权限点标签"分层展示，无权限的角色（部门负责人）显示"暂未分配任何权限点"。
