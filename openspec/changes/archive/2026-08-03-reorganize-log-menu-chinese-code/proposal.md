## Why

`add-login-log` change 刚落地后有两个问题：一是登录日志权限点的中文模块名映射（`PERMISSION_MODULE_LABELS`）漏登记了，导致角色管理"新增/编辑角色"弹窗的权限点勾选树、权限点管理页面的列表树在这个分组上退化成展示原始英文编码前缀 `LoginLogManagement`，不熟悉英文缩写的用户看不懂这是什么模块；二是操作日志、登录日志两个页面分别散落在"系统管理"一级菜单下，与组织、用户、角色等真正的业务管理页面混在一起，不利于区分"日志类只读页面"与"业务管理页面"。趁这两个 change（`add-login-log` 与其归档记录）都还没有提交 git，借这次机会一并修正。

## What Changes

- **修正范围（用户已澄清）**：权限点编码本身（`tab_permission.code`/`tab_menu.code`，如 `LoginLogManagement:loginLog:view`）**不改**，继续保持英文三段式，未来新增权限点也不要求用中文命名。真正要改的是"展示层的中文名映射漏了登录日志这一条"：
  - `frontend/src/utils/permissionTree.ts` 的 `PERMISSION_MODULE_LABELS` 里已经有 `OperationLogManagement: '操作日志管理'`，唯独缺了 `LoginLogManagement`，导致角色管理"新增/编辑角色"弹窗里的权限点勾选树、权限点管理页面的列表树，在登录日志这个分组上退化成展示原始英文编码前缀 `LoginLogManagement`（这正是 `role-management` spec 里"未登记模块的分组节点兜底展示编码前缀"这条已有场景所描述的兜底行为——本次是把遗漏的登记补上，不是改这条兜底规则本身）。
  - 补上 `LoginLogManagement: '登录日志管理'` 这一条映射即可让两处树形展示都变成中文，属于 bug 修复，不涉及 spec 行为变更。
- 新增一级菜单"日志管理"（`tab_menu` 顶级节点，`parent_id=0`），把"操作日志""登录日志"两个二级菜单从"系统管理"下移出，改挂到"日志管理"下；前端侧边栏、路由 path（`/system/logs`→`/log/operation-logs`，`/system/login-logs`→`/log/login-logs`）同步调整。挂载调整只改 `parent_id`/前端 path，两个权限点/菜单的 `code` 保持不变（`OperationLogManagement:log:view`、`LoginLogManagement:loginLog:view`）。
- 同步更新仓库根目录《权限资源.txt》，把这两条从"系统管理"小节移到新的"日志管理"小节，编码本身不变。
- 新增 `V9__reorganize_log_menu.sql` 迁移文件（`V8__add_login_log.sql` 已随 `9d3e587 feat(日志): 日志菜单调整` 提交并推送到 `origin/develop`，视为已发布，不再回头改写）：插入"日志管理"顶级菜单，追加两条 `UPDATE tab_menu` 语句把操作日志（`V1` 插入）、登录日志（`V8` 插入）两个菜单节点的 `parent_id` 改挂到日志管理节点；不涉及任何 `UPDATE ... SET code` 语句。

## Capabilities

### New Capabilities

（无——本次不引入新的能力域，只是调整已有能力的菜单归属与编码规范）

### Modified Capabilities

- `menu-management`：菜单资源种子数据新增一个顶级"日志管理"节点，一级分组节点总数由 4 个变为 5 个；顺带修正种子数据需求里一处已过时的举例（操作日志早已实现并已被种子化，不应再作为"尚未实现页面不进入种子数据"的例子）。
- `operation-log-management`：菜单路由 path 从 `/system/logs` 改为 `/log/operation-logs`，挂载分组从"系统管理"改为"日志管理"；页面访问权限点编码 `OperationLogManagement:log:view` 保持不变。

登录日志（`login-log-management`）与侧边导航（`navigation`）两个能力目前的 spec 里并未对具体页面路径/挂载分组做过形式化约束（这些细节只落在 `menu.ts`/`权限资源.txt`/迁移脚本里），本次改动不涉及这两个 spec 文件的需求文本变化。`permission-management`/`role-management` 两个能力的 spec 已经完整覆盖"模块中文名映射缺失时兜底展示编码前缀"的行为，本次只是把遗漏的 `LoginLogManagement` 映射条目补上，属于 bug 修复，不改变任何已发布的需求文本，因此不产出这两个能力的 delta spec。

## Impact

- 前端：`frontend/src/router/menu.ts`（新增日志管理分组、调整 system 分组、path 调整）、`frontend/src/router/index.ts`（path 相关的两个映射表 key）、`frontend/src/utils/permissionTree.ts`（补上 `LoginLogManagement: '登录日志管理'` 这一条缺失的映射）。
- 后端：无代码改动（不涉及权限编码格式、`IdentityAuthFilter` 等）。
- 数据库：新增 `backend/src/main/resources/db/migration/V9__reorganize_log_menu.sql`（新增"日志管理"顶级菜单插入 + 对 `V1` 操作日志、`V8` 登录日志两条菜单记录的 `parent_id` UPDATE，均不涉及 `code` 变更），`V8__add_login_log.sql` 本身不再改动。
- 文档：仓库根目录《权限资源.txt》（系统管理/日志管理两个小节的条目调整，编码本身不变）。
- 前提变更：原方案假设 `V8` 尚未提交 git、可直接改写；实际排查发现 `V8__add_login_log.sql` 已随 `9d3e587` 提交并推送到 `origin/develop`，视为已发布迁移，因此改为新增 `V9`，不回头改写 `V8`。
