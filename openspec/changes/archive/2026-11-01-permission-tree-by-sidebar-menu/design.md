## Context

`buildPermissionTree(items, resolveGroupLabel)` 目前按编码第一段分组产出两层树（分组节点 id `group:模块名`，叶子节点 id 为权限点数字 id），被三处使用：

- `PermissionManagementView.vue`：带行内操作的 el-tree，"全部展开"只取第一层 id。
- `RoleManagementView.vue`：`show-checkbox` 勾选树，提交时 `getCheckedKeys()` 过滤出数字 id。
- `RoleDetailView.vue`：直接 `v-for` 两层渲染为"分组名 + 标签"。

左侧侧边栏结构定义在 `src/router/menu.ts` 的 `MENU_GROUPS`（一级 `key/title`，二级 `title/permissionKey`）。对当前 151 个权限编码的统计：132 个能按规则 ①② 归属页面；18 个需要规则 ③（应用访问授权的 policy/override、字典项、导入字段配置、流程实例终止）；1 个（`PluginManagement:plugin:view`）侧边栏没有对应页面。侧边栏有 1 个页面（"应用密钥" `AppManagement:secret:view`）没有任何权限点。

## Goals / Non-Goals

**Goals:**

- 三处展示统一为"一级菜单 → 二级页面 → 权限点"三层结构，顺序与侧边栏一致。
- 结构数据源唯一：直接读取 `MENU_GROUPS`，三处共用同一个构造函数。
- 任何权限点都不会因无法匹配而丢失（兜底"其他"分组）。

**Non-Goals:**

- 不按当前用户权限过滤菜单（权限点管理/角色分配要看到全部权限点，与侧边栏的 `filterMenuGroups` 无关）。
- 不读取后端 `tab_menu` 菜单树（侧边栏实际渲染来源是前端 `MENU_GROUPS`）。
- 不改后端接口、数据库、权限编码。

## Decisions

### Decision 1: 以 `MENU_GROUPS` 作为结构数据源

侧边栏就是由 `MENU_GROUPS` 渲染的，以它为数据源能保证"按左侧菜单展示"字面一致，且新增页面时不需要维护第二份结构。备选方案是调用后端菜单树接口：会额外引入一次请求，并且 `tab_menu` 与前端侧边栏并非逐项一致（例如插件管理有 DB 菜单记录但前端侧边栏未挂载），因此不采用。

### Decision 2: 权限点归属页面的四级匹配规则

按顺序匹配，命中即停止：

1. 编码与页面 `permissionKey` 完全相同（区分同模块同资源下的不同页面，如 `ApprovalManagement:request:view` → 我的申请、`ApprovalManagement:request:approve` → 待我审批）。
2. 编码前两段 `模块:资源` 与页面 `permissionKey` 前两段相同，取侧边栏顺序中第一个（如 `OrgManagement:org:add` → 组织管理；`AppManagement:app:config:editAuth` → 应用管理）。
3. 编码模块段相同，取侧边栏顺序中该模块的第一个页面（如 `DictManagement:dictItem:add` → 字典管理、`WorkflowDesign:instance:terminate` → 流程模型）。
4. 都不匹配 → 末尾"其他"一级节点，二级节点按模块分组、标签用 `resolvePermissionModuleLabel`（保留中文名映射与开发环境漏配告警）。

页面的 `permissionKey` 预先建好"完整编码 / 前两段 / 模块段 → 页面"三张查找表，构树为 O(n)。

### Decision 3: 节点 id 与类型

- 一级节点：`menu:${group.key}`；"其他"一级节点：`menu:__other__`。
- 二级页面节点：`page:${child.path}`（路径唯一）；"其他"下的模块节点：`module:${模块名}`。
- 叶子节点仍为权限点数字 id，`raw` 保留原对象。
- 虚拟节点 id 都是字符串，因此角色弹窗提交时"只保留数字 id"的既有过滤逻辑无需改动；el-tree 父子勾选联动天然支持三层。
- `PermissionTreeNode` 增加 `level?: 'menu' | 'page'` 区分虚拟节点层级，`count` 表示该节点下叶子权限点总数。

### Decision 4: 顺序与空节点

一级、二级节点按 `MENU_GROUPS` 顺序；叶子保持传入顺序（后端已按 showOrder 排好）。没有叶子的页面和一级菜单不输出。

### Decision 5: 函数签名

`buildPermissionTree(items, menuGroups = MENU_GROUPS)`：默认读 `MENU_GROUPS`，参数可注入便于测试/复用；原 `resolveGroupLabel` 参数移除（三处调用方本来都传同一个 `resolvePermissionModuleLabel`，现在由工具内部用于"其他"分组）。`PERMISSION_MODULE_LABELS` 与 `resolvePermissionModuleLabel` 保持导出不变。

### Decision 6: 角色详情页展示

由"分组名 + 标签"改为"一级菜单名"下多行"二级页面名：权限点标签"，保留链式虚线 + 圆点视觉语言（一级节点圆点、二级行缩进）。

## Risks / Trade-offs

- [规则 ③ 把模块内"无独立页面"的权限点归入第一个页面，可能不完全符合语义] → 当前 18 个都与实际所在页面一致（字典项在字典管理页、policy/override 在应用访问授权页、导入字段配置在表单管理页的 tab），`instance:terminate` 归入流程模型可接受。
- [新增侧边栏页面但权限编码前缀与页面不一致] → 会落入规则 ③/④，仍可见不会丢失；"其他"分组非空即提示需要整理。
- [一级菜单与二级页面同名（应用管理 → 应用管理）] → 层级缩进可区分，不做特殊处理。

## Migration Plan

纯前端展示改动，发布即生效；回滚通过 Git 还原四个文件。

## Open Questions

无。
