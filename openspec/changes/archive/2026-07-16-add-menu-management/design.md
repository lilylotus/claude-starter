## Context

"系统管理"菜单组在 `frontend/src/router/menu.ts` 里有三个子菜单项：`/system/menus`（本次落地）、`/system/dicts`（已在 `dict-management` 落地）、`/system/logs`（仍是占位页）。`stubDescriptions` 里 `/system/menus` 的文案是"配置侧边栏菜单与其绑定的权限点，控制不同角色登录后能看到哪些入口"。

资源实体本质上是一棵树（菜单可以有子菜单、子按钮；一个菜单下也可能挂多个 API 资源），这与 `org-management` 的组织树形状完全一致（`parentId` 自引用 + 树查询 + 懒加载子节点查询 + 分页子节点查询 + 删除前校验无未删除子节点）。与 `org-management` 唯一的结构性差异是多了一个"资源类型"字段（菜单/按钮/API），其余字段形状（名称+编码+显示序号+备注+状态+审计字段）与 `org-management`/`role-management`/`permission-management` 一致。因此本次直接照抄 `OrgController`/`OrgServiceImpl`/`OrgManagementView.vue`（含最新的"上级组织选择器按需加载"版本，见 `2026-07-15-org-tree-defer-parent-selector-load`）的结构和实现，把"组织"替换成"资源"，并新增资源类型字段的处理。

## Goals / Non-Goals

**Goals:**
- 新增资源主数据表 `tab_menu`，具备独立的 `2000`/`3000`/`-1000` 状态语义（与 org/user/dict/app/role/permission 一致），支持树查询、懒加载子节点查询、分页子节点查询、详情查询、增、改、启停用、逻辑删除。
- 资源编码在未删除范围内唯一（创建/更新均校验，更新时排除自身），校验方式对齐 `OrgServiceImpl.checkCodeUnique`。
- 资源类型（菜单/按钮/API）为必填的固定三选一常量，创建/更新时校验合法性。
- 删除前校验"是否存在未删除的下级资源"，与 `org-management` 的树形删除约束一致。
- 左侧资源树懒加载展开 + 右侧直属下级资源分页表格，交互模式（默认收起、默认展示顶级资源第一页、标题空白直到选中节点、上级资源选择器按需加载并防环）与 `org-management` 一致。

**Non-Goals:**
- 不实现角色-资源（菜单/按钮/API）的勾选关联——与 `role-management`/`permission-management` 当初的范围收敛方式一致，留待两边主数据都齐备后单独规划。
- 不实现"资源真正驱动侧边栏渲染/按钮显隐/接口鉴权"——本次只维护资源树这份主数据，不接入实际的菜单渲染或鉴权拦截流程。
- 不把资源类型做成可由管理员增减的字典项（不复用 `dict-management`）——三种类型是结构性的，每种类型未来可能对应不同的关联字段（如菜单资源关联路由路径、API 资源关联 HTTP method），提前做成自由字典反而掩盖了这个差异；本次固定为常量类，效仿 `OrgStatus` 等状态码的既有模式。
- 不在菜单列表页面提供搜索/筛选栏，与组织管理、角色管理、权限管理一致。
- 不主动把菜单文案从"菜单管理"改名——文案已经就是"菜单管理"，无需改动。

## Decisions

- **新增独立顶层包 `cn.nihility.rbac.menu`，不复用 `org` 包**：资源与组织虽然结构同形（树形 + 状态语义），但属于不同的业务领域（RBAC 资源 vs 组织架构），按项目"每个领域独立一份包"的既有惯例新建独立包（`controller`/`service`/`service.impl`/`dto`/`entity`/`mapper`/`mapstruct`/`constant`）。
- **直接照抄 `OrgController`/`OrgServiceImpl` 的树形接口形状**：`GET /api/menus/tree`（全量嵌套树，供弹窗"上级资源"选择器）、`GET /api/menus/tree/children?parentId=`（懒加载，供左侧导航树）、`GET /api/menus/children?parentId=&page=&pageSize=`（分页，供右侧表格）三个接口的职责划分、排序规则（`showOrder` 降序、`id` 升序）、`parentId` 默认值（`0`）均与 `org-management` 一致，没有必要重新设计。
- **新增 `MenuResourceType` 常量类，使用整型编码而非字符串**：`MENU = 1`、`BUTTON = 2`、`API = 3`，与项目里 `status` 用整型语义编码的既有风格一致（而不是引入字符串枚举这一新形式）。三者互斥，创建/更新时校验取值必须是这三者之一。
- **`MenuStatus` 独立建常量类，不复用 `OrgStatus`/`RoleStatus`/`PermissionStatus`**：与项目"每个实体独立一份状态常量类"的既有惯例一致。
- **删除前校验"是否存在未删除子资源"**：与 `OrgServiceImpl.delete()` 完全一致的前置计数校验（`parent_id = id AND status != DELETED`），原因同样是软删除语义不适合用外键表达，且能给出更明确的业务错误信息。
- **编码唯一性校验范围仅限"未删除"资源**：与 `org-management` 一致，逻辑删除的资源不应继续占用编码命名空间。
- **前端左树右表结构 + `el-tree-select` 防环，照抄 `OrgManagementView.vue`（含按需加载上级组织树的最新版本）**：虚拟顶级根节点（`id: 0`）承载 `parentId = 0` 语义；`pruneSubtree`/`findAncestorPath`/`treeSelectExpandedKeys` 等辅助函数逻辑直接复用；全量资源树（供弹窗用）只在打开新增/编辑弹窗时才按需请求，不在页面加载时预取。
- **资源类型选择器用 `el-radio-group`，而非下拉选择**：只有三个固定选项，单选按钮组比下拉框更直观，且避免了误以为这是一个可搜索/可扩展的列表（与"这是固定类型而非字典"的设计意图保持一致）。
- **前端类型文件命名为 `types/menuResource.ts` 而非 `types/menu.ts`**：实现时发现 `@/types/menu.ts` 已被侧边栏导航用的 `MenuGroup`/`MenuChild` 类型占用（`router/menu.ts` 依赖它），直接复用会覆盖既有文件；`api/menu.ts`、`stores/menu.ts`、`useMenuStore`（Pinia store id `menu`）未与既有文件冲突，予以保留，仅类型文件改名为 `menuResource.ts`（`MenuResourceTreeNode`/`MenuResourceRow`/`MenuResourceFormRequest`），后端包名/表名/接口路径不受影响。
- **列表列展示资源类型而不展示上级资源名称**：右侧表格已经通过"选中的左侧树节点"隐含了上级资源上下文，重复展示 `parentName` 列意义不大（`org-management` 同理，右侧表格不展示 `parentName`，仅在详情弹窗展示）；资源类型是新增的、每行都需要用户区分的关键信息，因此列表里展示资源类型而不展示上级资源名称，详情弹窗里两者都展示。

## Risks / Trade-offs

- **[风险] 资源类型固定为三个常量，后续如需新增类型（如"目录"/"外链"）需要改代码而非配置** → 与"这是结构性类型，不是可扩展字典"的判断一致；如果未来确实需要更多类型，应作为一个独立 change 重新评估，而不是现在就为假设性需求设计可扩展机制。
- **[风险] 本次落地后资源树仍是"孤立"的主数据，不能真正驱动侧边栏渲染或接口鉴权** → 与 `role-management`/`permission-management` 落地时的取舍一致，属于分阶段推进的既定路径：先让资源主数据可维护，再在后续 change 里补上"角色勾选资源"和"资源真正生效"两条关联能力。
- **[权衡] 树在内存中一次性构建（`getTree()`）** → 与 `org-management` 一致的已知技术债，当前判断资源数量不会达到需要分页/缓存的量级。

## Migration Plan

- 新增 `backend/src/main/resources/db/migration/V10__init_tab_menu.sql`：
  ```sql
  CREATE TABLE `tab_menu` (
      `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
      `name`          VARCHAR(64)  NOT NULL COMMENT '资源名称',
      `code`          VARCHAR(64)  NOT NULL COMMENT '资源编码',
      `parent_id`     BIGINT       NOT NULL DEFAULT 0 COMMENT '上级资源 id，0 表示顶级',
      `resource_type` INT          NOT NULL COMMENT '资源类型：1=菜单，2=按钮，3=API',
      `show_order`    INT          NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前',
      `remark`        VARCHAR(255)          DEFAULT NULL COMMENT '备注',
      `status`        INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）',
      `create_by`     VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
      `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
      `update_by`     VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
      `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
      PRIMARY KEY (`id`),
      KEY `idx_tab_menu_parent_id` (`parent_id`),
      KEY `idx_tab_menu_status` (`status`),
      KEY `idx_tab_menu_code` (`code`)
  ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '菜单/按钮/API 资源主数据表';
  ```
- 表名、列名均已按项目既有惯例检查过与 MySQL/PostgreSQL/Oracle/SQL Server 保留字的冲突：`tab_menu`/`parent_id`/`resource_type`/`show_order`/`remark`/`status`/`create_by`/`create_time`/`update_by`/`update_time` 均非保留字；特意用 `resource_type` 而非 `type`——`TYPE` 在部分数据库方言（如 Oracle）中是保留字，与 CLAUDE.md 的字段命名检查要求一致。
- Flyway 迁移只前进不回退，如需撤销需另发新迁移脚本。
