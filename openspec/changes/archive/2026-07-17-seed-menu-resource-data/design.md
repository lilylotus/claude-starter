## Context

`tab_menu` 表结构（`V10__init_tab_menu.sql`）已经支持通过 `parentId` 组织任意深度的
树形资源，`resourceType` 区分菜单（1）/按钮（2）/API（3）。`权限资源.txt` 已经列出了
8 个已实现页面、60 条编码（8 条 `:view` 页面编码 + 52 条按钮编码），但这份文件只是
文本清单，还没有对应的种子数据把它写进 `tab_menu`。种子数据写法可参考已有的
`V4__seed_dict_position_type.sql`（`INSERT ... SELECT ... FROM 其他表 WHERE code = ...`
按编码回填外键，而不是硬编码自增 id）。

## Goals / Non-Goals

**Goals:**
- 用一份幂等风格清晰、可读的 Flyway 脚本，把 `权限资源.txt` 里的三段式编码原样写入
  `tab_menu`，父子关系与前端侧边栏（`router/menu.ts` 的 `MENU_GROUPS`）、`权限资源.txt`
  的分组结构保持一致。
- 通过按 `code` 回填 `parentId` 的方式关联父子行，不手写自增 id，避免脚本对
  auto_increment 起始值的隐式假设。

**Non-Goals:**
- 不新增/修改 `tab_menu` 表结构。
- 不涉及应用密钥、操作日志这两个尚未实现页面的资源编码（`权限资源.txt` 本身也没有）。
- 不在这次改动里给角色分配这些资源（`tab_role_menu` 一类的关联表目前也还不存在，
  属于后续角色管理"分配权限"功能的范畴，不在本次 scope 内）。

## Decisions

### 1. 用会话变量而不是子查询自引用 `tab_menu` 来回填 `parentId`
- 每插入完一层，用 `SET @xxx_id := (SELECT id FROM tab_menu WHERE code = '...');` 取得
  刚插入行的 id，下一层 `INSERT ... VALUES (..., @xxx_id, ...)` 直接使用该变量。
- 理由：`INSERT INTO tab_menu (...) SELECT ... FROM tab_menu WHERE ...` 这种在同一条
  语句里对目标表自引用的写法在部分 MySQL 版本/模式下会触发
  `ERROR 1093 (HY000): You can't specify target table 'tab_menu' for update in FROM
  clause`；拆成"先 SELECT 到变量、再用变量 INSERT"两步可以完全避免这个问题，比用
  派生表（`FROM (SELECT ...) AS p`）包一层更直观、也更贴近 DBA 手写种子脚本的习惯。

### 2. 编码沿用 `权限资源.txt` 原文，不做大小写或格式调整
- 页面/按钮层的 `code` 直接照抄 `权限资源.txt`（如 `OrgManagement:org:add`），
  一级分组层的 `code` 沿用 `router/menu.ts` 里 `MENU_GROUPS[].key`（`identity` 等，
  全小写），两种大小写风格并存是刻意的——它们分别是两份已经在仓库里存在的既有约定
  （详见 `权限资源.txt` 顶部说明与 `frontend/src/router/menu.ts`），这次改动不重新
  统一它们，只是如实落地。

### 3. `showOrder` 按页面内从上到下、从左到右的可见顺序降序编号
- 一级分组：`identity=40 > application=30 > permission=20 > system=10`，与侧边栏从上
  到下的展示顺序一致（`showOrder` 降序=靠前，与其他模块的排序规则一致）。
- 页面菜单：同一分组下按 `router/menu.ts` 里子菜单的声明顺序降序编号。
- 按钮：标准 CRUD 页面统一用 `新增=60 > 详情=50 > 编辑=40 > 启用=30 > 停用=20 >
  删除=10`，对应列表页面"新增"按钮在头部、其余在操作列从左到右的顺序；字典管理页面
  两组按钮（字典类型 / 字典项）各自内部按同一模式编号，字典类型整体排在字典项前面
  （`60~100` vs `10~50`），因为字典类型面板在页面左侧、视觉顺序在前。

## Risks / Trade-offs

- [Risk] 若未来 `权限资源.txt` 里的编码发生调整（页面重命名、按钮增删），这份种子
  脚本不会自动同步 → Mitigation：这是种子数据脚本的固有局限（Flyway 迁移一旦执行
  过就不应该再改），后续变化应该通过新的迁移脚本（`V12__...`）追加/更新，而不是
  修改本脚本；`CLAUDE.md` 里已经要求菜单/按钮变化时同步维护 `权限资源.txt`，后续
  变化如需同步进数据库，按同样方式新增迁移脚本即可。

## Open Questions

无。
