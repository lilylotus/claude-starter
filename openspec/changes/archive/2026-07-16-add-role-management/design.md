## Context

"权限管理"菜单组在 `frontend/src/router/menu.ts` 里已经存在两个子菜单项（`/permission/roles`、`/permission/points`），但都还没有真实业务组件，路由表通过 `implementedComponents` 缺省 fallback 到 `PlaceholderView.vue`；`stubDescriptions` 里给 `/permission/roles` 预写的占位文案是"定义角色并为角色勾选权限点"，暗示了未来角色管理会包含权限点勾选，但权限点管理（`/permission/points`）本身目前也是占位页，没有任何权限点数据/接口可供勾选。已与用户确认：本次角色管理只落地用户明确列出的字段和 CRUD 能力，不包含权限点勾选、不包含用户-角色绑定，这两块留给权限点管理落地之后再单独规划。

角色实体的字段形状（名称 + 编码 + 显示序号 + 备注 + 状态 + 审计字段）与 `dict-management` 里的字典类型（`DictTypeEntity`）完全一致——都是没有外键关联的扁平实体，唯一的业务规则是编码需在未删除范围内唯一。因此本次直接照抄 `DictTypeService`/`DictTypeServiceImpl`/`DictTypeController` 的结构和实现，而不是照抄更复杂的 `application-management`（它有 `ownerId`/`orgId` 两个外键选择器）。

## Goals / Non-Goals

**Goals:**
- 新增角色主数据表 `tab_role`，具备独立的 `2000`/`3000`/`-1000` 状态语义（与 org/user/dict/app 一致），支持增删改查、启停用、逻辑删除。
- 角色编码在未删除范围内唯一（创建/更新均校验，更新时排除自身），校验方式对齐 `DictTypeServiceImpl.checkCodeUnique`/`OrgServiceImpl.checkCodeUnique`/`AppServiceImpl.checkCodeUnique`。
- 角色列表按 `showOrder` 降序（相同时 `id` 升序）分页展示，不提供搜索栏。

**Non-Goals:**
- 不实现角色-权限点勾选（依赖尚未实现的权限点管理）。
- 不实现用户-角色绑定。
- 不在角色列表页面提供搜索/筛选栏（已与用户确认范围，approved 的界面预览里没有搜索栏）。
- 删除角色不做"是否有关联数据"的阻塞校验（不像 `DictTypeServiceImpl.delete` 会校验是否存在未删除的字典项）——因为本次角色不关联任何其他实体（没有权限点勾选、没有用户绑定），没有可校验的下游数据。

## Decisions

- **新增独立顶层包 `cn.nihility.rbac.role`，不复用 `dict`/`app` 包**：角色是与组织、用户、字典、应用平级的新领域实体，按项目惯例新建独立包（`controller`/`service`/`service.impl`/`dto`/`entity`/`mapper`/`mapstruct`/`constant`）。
- **直接照抄 `DictTypeServiceImpl` 的结构**（而不是 `AppServiceImpl`）：两者字段形状相同（名称+编码+显示序号+备注+状态），且都没有外键需要 join 回填名称，`AppServiceImpl` 里的 `toVOListWithNames`（批量 join 用户/组织表）在角色管理这里用不上。
- **`RoleStatus` 独立建常量类，不复用 `OrgStatus`/`UserStatus`/`DictStatus`/`PositionStatus`/`AppStatus`**：与项目"每个实体独立一份状态常量类"的既有惯例一致。
- **删除角色为简单逻辑删除，不做阻塞校验**：与 `PositionServiceImpl.delete`/`AppServiceImpl.delete` 一致（无下游关联数据可查）；不同于 `OrgServiceImpl.delete`（校验子组织）和 `DictTypeServiceImpl.delete`（校验字典项），因为角色本次不关联任何其他实体。
- **列表接口 `GET /api/roles` 不接受任何筛选参数，只有 `page`/`pageSize`**：已与用户确认角色列表页面不提供搜索栏（approved 的界面预览只列了列表列和弹窗字段，没有搜索栏）；不像 `DictTypeController.page` 那样支持 `keyword` 模糊搜索。

## Risks / Trade-offs

- [角色管理和权限点管理的关联能力（角色勾选权限点、用户绑定角色）本次完全不做，意味着当前的角色是"孤立"的主数据，还不能真正在鉴权链路里起作用] → 与用户明确确认的范围一致；等权限点管理有了真实数据/接口后，再新增一个 change 把关联能力补上，避免在下游能力还不存在时做假设性设计。

## Migration Plan

- 新增 `backend/src/main/resources/db/migration/V8__init_tab_role.sql`：
  ```sql
  CREATE TABLE `tab_role` (
      `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
      `name`        VARCHAR(64)  NOT NULL COMMENT '角色名称',
      `code`        VARCHAR(64)  NOT NULL COMMENT '角色编码',
      `show_order`  INT          NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前',
      `remark`      VARCHAR(255)          DEFAULT NULL COMMENT '备注',
      `status`      INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）',
      `create_by`   VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
      `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
      `update_by`   VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
      `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
      PRIMARY KEY (`id`),
      KEY `idx_tab_role_status` (`status`),
      KEY `idx_tab_role_code` (`code`)
  ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色主数据表';
  ```
- 表名、列名均已按项目既有惯例检查过与 MySQL/PostgreSQL/Oracle/SQL Server 保留字的冲突（`tab_role`/`name`/`code`/`show_order`/`remark`/`status`/`create_by`/`create_time`/`update_by`/`update_time` 均不是保留字，且沿用 `tab_` 前缀和下划线命名，与其他表完全一致）。
- Flyway 迁移只前进不回退，如需撤销需另发新迁移脚本。
