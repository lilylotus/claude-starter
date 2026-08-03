## Context

`add-login-log` change 已经把登录日志页面、菜单、权限点落地，且该 change（含其归档记录 `openspec/changes/archive/2026-07-31-add-login-log/`）已随提交 `9d3e587 feat(日志): 日志菜单调整` 一并提交并推送到 `origin/develop`（本设计文档编写时最初假设它还没提交，实现前重新核对 `git log`/`git status` 时发现已推送，见下方"约束"与 Decision 1 的更新）。操作日志模块更早落地，已经随 `V1__init_schema.sql`（顶级菜单结构、操作日志菜单节点）与 `V6__seed_permissions_and_super_admin.sql`（权限点种子数据）一起提交。

本次要在 `add-login-log` 提交之前修正两个问题：
1. `frontend/src/utils/permissionTree.ts` 里的 `PERMISSION_MODULE_LABELS`（权限点编码"模块"前缀 → 中文展示名的映射表）已经登记了 `OperationLogManagement: '操作日志管理'`，但漏掉了 `LoginLogManagement`，导致角色管理弹窗的权限点勾选树、权限点管理页面的列表树在登录日志这个分组上退化成展示原始英文编码前缀，而不是中文名。
2. 操作日志、登录日志两个页面挂在"系统管理"一级菜单下，希望收拢到独立的一级"日志管理"菜单下。

用户已经明确澄清：**权限点编码本身不改**（`OperationLogManagement:log:view`、`LoginLogManagement:loginLog:view` 保持英文三段式），只是要把遗漏的中文展示名映射条目补上；菜单归属调整只涉及 `parent_id`/前端路由 path，不涉及 `code` 变更。

约束：
- `V1`/`V6`/`V8` 均已提交并推送到 `origin/develop`，视为"已发布"的迁移，都不能回头直接改写内容，只能通过后续迁移追加 `UPDATE` 语句。

## Goals / Non-Goals

**Goals:**
- 补上 `PERMISSION_MODULE_LABELS` 里缺失的 `LoginLogManagement: '登录日志管理'` 映射，让角色管理弹窗的权限点勾选树、权限点管理页面的列表树在登录日志分组上正确展示中文名。
- 操作日志、登录日志两个菜单节点统一挂到新的一级"日志管理"菜单下（前端侧边栏 + 后端 `tab_menu` 树一致），前端路由 path 相应调整为 `/log/operation-logs`、`/log/login-logs`。
- 更新《权限资源.txt》，把这两个模块的条目从"系统管理"小节移到新的"日志管理"小节。

**Non-Goals:**
- 不改动 `tab_permission.code`/`tab_menu.code` 里任何一个权限点编码的字符串值——包括操作日志、登录日志这两个本次直接相关的模块，也包括其余模块。
- 不改动后端 `IdentityAuthFilter.MENU_PATTERN` 或任何请求头格式校验逻辑——编码格式没有变化，不需要放宽正则。
- 不建立"新增权限点统一用中文命名"之类的新规范——这是此前一版方案里的误解，已被用户明确否定。
- 不改变操作日志、登录日志页面本身的业务逻辑、字段、查询接口。

## Decisions

### 1. 新增 `V9__reorganize_log_menu.sql`，不改写 `V8`

原方案曾计划直接改写 `V8__add_login_log.sql`（当时判断它还没提交 git，等价于暂存区里的未完成工作）。实现前重新核对 `git log`/`git status` 发现 `V8` 已随 `9d3e587` 提交并推送到 `origin/develop`——与 `V1`/`V6` 一样应视为"已发布"迁移，回头改写会破坏任何已拉取/已跑过该迁移环境的 Flyway checksum。因此改为新增 `V9__reorganize_log_menu.sql`：插入"日志管理"顶级菜单，并用两条 `UPDATE` 语句分别把操作日志（`V1` 插入）、登录日志（`V8` 插入）菜单节点的 `parent_id` 改挂到新节点；`V6` 里操作日志的权限点种子数据本身不需要任何改动（`code` 不变），不涉及。

### 2. 顶级菜单"日志管理"的 `code` 用英文短标识 `log`

其余顶级分组（`identity`/`application`/`permission`/`system`）的 `tab_menu.code` 都是英文短标识而不是三段式权限点编码（顶级分组本身不对应具体权限点，只是导航容器），`show_order` 沿用"数值越大越靠前"的既有约定。"日志管理"排在"系统管理"（`show_order=10`）之后，取 `show_order=5`，与其余顶级分组的数值梯度（40/30/20/10）保持一致，同时避免任何已有编号冲突。

### 3. 补齐缺失的模块中文名映射，而不是改编码本身

`role-management` spec 已有的"角色权限点勾选树的模块标签展示"这条需求（及其"未登记模块的分组节点兜底展示编码前缀"场景）本来就描述了这个映射表驱动展示、缺失时兜底展示原始编码前缀的行为——当前 `LoginLogManagement` 展示为英文正是这个兜底分支被触发的表现，根因是 `add-login-log` 实现时漏登记了这一条，而不是这条兜底规则本身有问题。修复方式是在 `PERMISSION_MODULE_LABELS` 里补上这一条，与已有的 `OperationLogManagement: '操作日志管理'` 保持同样的风格，不需要碰权限点编码、不需要碰后端、不需要新的 spec 需求（行为本身没变，只是让实现符合已有需求）。

**备选方案考虑**：此前一版方案曾打算把权限点编码"模块"段本身换成中文（`LoginLogManagement:loginLog:view` → `登录日志管理:loginLog:view`），但这会牵连后端 `menu` 请求头格式校验正则、数据库迁移里对已提交记录的编码 UPDATE，改动面显著更大且用户已明确表示这不是本意，故放弃，仅保留"补齐展示层映射"这一最小改动。

### 4. 前端路由 path 从 `/system/*` 改为 `/log/*`

其余分组都遵循"路径前缀 = 分组 key"的约定（`/identity/*`、`/application/*`、`/permission/*`、`/system/*`），新分组 key 是 `log`，对应 path 前缀改为 `/log/operation-logs`、`/log/login-logs`，与约定保持一致；同时避免这两个页面继续挂在 `/system/*` 下造成"URL 归属"与"菜单归属"不一致。

## Risks / Trade-offs

- [风险] 前端路由 path 变化后，如果有遗漏的硬编码引用（如收藏的 URL、外部书签）仍指向旧 path，会 404。
  → 缓解：这是一个尚未对外发布的内部管理系统页面（`add-login-log` 本身还没提交），不存在需要兼容的历史外部链接；已对全仓库 grep 确认代码内部没有遗漏引用旧 path 的地方。
- [风险] `V9` 里对 `V1`/`V8` 记录的 `UPDATE` 语句如果条件写错（比如按已经变化的字段匹配导致找不到记录），本地重跑迁移可能"更新 0 行"而不报错，容易被忽略。
  → 缓解：两条 `UPDATE` 语句分别按操作日志、登录日志菜单节点稳定不变的 `code`（`OperationLogManagement:log:view`、`LoginLogManagement:loginLog:view`）精确匹配一次即可；tasks.md 包含"跑一次全新库的迁移，人工核对 `tab_menu` 最终数据"的验证步骤。

## Migration Plan

1. 新增 `V9__reorganize_log_menu.sql`：插入"日志管理"顶级菜单（`code='log'`，`parent_id=0`，`show_order=5`）→ UPDATE 操作日志菜单（`code='OperationLogManagement:log:view'`）的 `parent_id` → UPDATE 登录日志菜单（`code='LoginLogManagement:loginLog:view'`）的 `parent_id`；两条 `UPDATE` 均不改 `code`，`V8` 本身不做任何改动。
2. 本地清空开发库（或删除 `flyway_schema_history`）后跑一次 `./gradlew bootRun`（或对应的 Flyway 命令），确认 `V8`+`V9` 依序迁移无报错，且 `tab_menu` 最终数据符合预期（日志管理顶级节点存在、操作日志与登录日志都挂在其下，两者 `code` 均未变化）。
3. `V9` 是新增迁移，不涉及对已发布迁移的回写，天然不产生 Flyway checksum 冲突；如果发现问题，直接继续修改 `V9` 本身即可（因为 `V9` 自身此时也还未提交/未在共享环境执行）。

## Open Questions

（无）
