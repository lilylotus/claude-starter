## Context

自 2026-07-24 那次 34→1 的迁移合并以来，`V1__init_schema.sql` 之后又新增了 8 个增量版本：

- `V2__add_user_password_table.sql`：新增 `tab_user_password` 表
- `V3__seed_default_admin_user.sql`：种子化默认管理员 `tab_user`（`code='admin'`）+ 对应
  `tab_user_password` 记录
- `V4__add_user_reset_password_menu.sql`：给用户管理页面补一个"重置密码"按钮资源
  （`UserManagement:user:resetPassword`）
- `V5__add_role_permission_table.sql`：新增 `tab_role_permission` 表
- `V6__seed_permissions_and_super_admin.sql`：种子化 94 条权限点、"超级管理员"角色（关联全部
  94 条权限点）、把默认账号 `admin` 接入管理员身份+超级管理员角色
- `V7__add_missing_import_menu_resources.sql`：给组织/用户/任职/应用四个页面补齐此前漏种子化的
  `importTemplate`/`import` 两个按钮资源（共 8 条 `tab_menu` 记录）
- `V8__add_login_log.sql`：新增 `tab_login_log` 表 + 登录日志菜单/权限点种子数据 + 给超级管理员
  追加登录日志权限关联
- `V9__reorganize_log_menu.sql`：新增"日志管理"顶级菜单，把操作日志、登录日志两个菜单节点的
  `parent_id` 改挂到这个新分组下

这 8 个文件里没有任何 `ALTER TABLE` 语句（与 2026-07-24 那次合并时"大量列级 ALTER"的情况不同），
主要是"新建表"+"按 SELECT 回填 id 再 INSERT/UPDATE 关联数据"的模式，合并复杂度集中在
`tab_menu`（4 层 INSERT + 1 次 UPDATE 调整挂载关系）和 `tab_permission`/`tab_role_permission`
（V6 种子 94 条，V8 又追加 1 条，SUPER_ADMIN 角色最终应关联全部 95 条）上。

`openspec/specs/backend-common-utilities/spec.md` 的"Flyway 迁移目录保持单一基线"需求已明确
允许"当增量迁移文件积累到影响可维护性的程度时，仿照本次操作重新合并出一份新的基线"，本次
即是该需求预见到的重复操作，不需要修改需求文本。

## Goals / Non-Goals

**Goals:**
- 迁移目录重新收敛为一份反映当前最终 schema + 种子数据状态的基线文件 `V1__init_schema.sql`。
- 合并过程不引入任何语义差异：19 张表的最终结构、全部种子数据（含 `tab_menu` 完整菜单树、
  95 条权限点、超级管理员的全量权限关联）都要和旧 `V1~V9` 按顺序执行后的最终状态完全一致。

**Non-Goals:**
- 不处理生产环境或任何已经执行过旧版本迁移的库的升级路径——本次改动假定使用方清空库后
  重新执行新基线（详见 proposal.md「Impact」）。
- 不改变任何业务代码、接口行为，纯粹是迁移文件的组织方式调整。
- 不修改 `openspec/specs/backend-common-utilities/spec.md` 的需求文本（该需求已预见并允许
  本次操作）。

## Decisions

### 1. 继续合并为单个 `V1__init_schema.sql`

延续 2026-07-24 那次的决策：项目所有表之间存在种子数据的相互引用（`tab_menu` 引用自身
`parent_id`、`tab_role_permission` 引用 `tab_role`/`tab_permission`、`tab_admin`/
`tab_admin_role` 引用 `tab_user`/`tab_role`），单文件按"先建表、后插入有依赖关系的种子数据"
的顺序线性组织，依赖关系一目了然，不需要跨文件维护版本号先后顺序。

### 2. 版本号沿用 `V1`（不接着旧编号延续为 `V10`）

理由与 2026-07-24 那次一致：`V1` 代表"从零开始建库的基线"，语义清晰；代价是旧版本号
`V1~V9` 不能再复用，任何还在用旧版本号跑过 migrate 的库必须清库重来（已在 proposal.md
「Impact」中说明）。

### 3. 菜单种子数据直接体现最终挂载关系，不保留 V4/V7/V8/V9 的分步 INSERT/UPDATE

新 `V1` 里 `tab_menu` 的种子数据直接按最终状态一次性 INSERT：5 个一级分组（含新增的"日志
管理"）、各页面节点（含登录日志）、各按钮节点（含"重置密码"、四个页面的导入相关按钮），
操作日志、登录日志两个页面节点直接挂在"日志管理"分组下，不再体现"先挂在系统管理、再
UPDATE 改挂"这个历史过程。这部分历史仍可通过 git 提交记录追溯。

### 4. 权限点种子数据合并为一次性 95 条 INSERT，超级管理员权限关联用 SELECT 全量捕获

`tab_permission` 种子数据合并 `V6`（94 条）与 `V8`（登录日志 1 条）为一次 95 条 INSERT；
`tab_role_permission` 沿用 `V6` 的写法（`INSERT ... SELECT id FROM tab_permission`），
即可在合并后自然覆盖全部 95 条，不需要额外补一条登录日志权限的关联 INSERT（`V8` 里那条
针对 SUPER_ADMIN 追加登录日志权限的语句在合并后被"全量 SELECT"自然吸收，不再单独出现）。

### 5. 不保留任何中间过程 UPDATE 步骤

例如不再"先把操作日志/登录日志菜单挂在系统管理分组下，再用 UPDATE 改挂到日志管理分组"，
而是直接在 INSERT 时就用日志管理分组的 `parent_id`。好处是新环境执行链路更短；代价是丢失
"这个菜单是什么时候、因为什么原因改挂分组"的迁移历史，这部分历史仍可通过 git 提交记录
（本次合并对应的历史 commit，以及此前 `reorganize-log-menu-chinese-code` change 的记录）
追溯。

## Migration Plan

1. 按版本号顺序读取 `V1__init_schema.sql`（旧）到 `V9__reorganize_log_menu.sql`，逐表、逐条
   整理成最终状态，写入新的 `V1__init_schema.sql`。
2. 删除 `V2`~`V9` 共 8 个文件（`git rm`，保留 git 历史可追溯）。
3. 本地/测试库清空（或删除 `flyway_schema_history` 表）后重新执行新 `V1`，通过
   `./gradlew test` 验证 Flyway 迁移无报错、`contextLoads` 通过。
4. 人工核对合并后的关键数据：`tab_menu` 树的节点总数与层级关系（5 个一级分组）、
   `tab_permission` 总数（95 条，含登录日志）、`tab_role_permission` 里 SUPER_ADMIN 关联的
   权限点数量（应等于 `tab_permission` 总数 95）、默认账号 `admin` 的登录链路（`tab_user`+
   `tab_user_password`+`tab_admin`+`tab_admin_role`）完整可用。
5. 无回滚计划——如需回退，`git revert` 本次改动对应的 commit 即可恢复旧的 `V1~V9` 九个文件。

## Open Questions

（无）
