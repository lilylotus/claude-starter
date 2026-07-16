## Context

"权限管理"菜单组在 `frontend/src/router/menu.ts` 里有两个子菜单项：`/permission/roles`（已在 `role-management` 落地）和 `/permission/points`（仍是占位页，`stubDescriptions` 里的文案是"维护最细粒度的权限点（如 `identity:user:edit`），供角色勾选和接口鉴权引用"）。本次落地 `/permission/points` 的真实业务能力。

权限点实体的字段形状（名称 + 编码 + 显示序号 + 备注 + 状态 + 审计字段）与 `role-management` 的 `RoleEntity`、`dict-management` 的 `DictTypeEntity` 完全一致——都是没有外键关联的扁平实体，唯一的业务规则是编码需在未删除范围内唯一。因此本次直接照抄刚落地的 `RoleService`/`RoleServiceImpl`/`RoleController` 结构和实现（把"角色"替换成"权限"），这是本项目里第三次出现同一形状的模块（`dict-type` → `role` → `permission`），复用同一套已验证过的模式。

## Goals / Non-Goals

**Goals:**
- 新增权限点主数据表 `tab_permission`，具备独立的 `2000`/`3000`/`-1000` 状态语义（与 org/user/dict/app/role 一致），支持增删改查、启停用、逻辑删除。
- 权限编码在未删除范围内唯一（创建/更新均校验，更新时排除自身），校验方式对齐 `RoleServiceImpl.checkCodeUnique`。
- 权限列表按 `showOrder` 降序（相同时 `id` 升序）分页展示，不提供搜索栏。

**Non-Goals:**
- 不实现角色-权限点勾选（`role-management` 落地时已经把这块排除在外，本次同样不做，等两边都齐备后再单独规划关联能力）。
- 不实现"权限编码在接口鉴权链路里真正生效"（即当前项目里 `permissionKey`/`@PreAuthorize` 之类的实际鉴权拦截）——本次只是维护权限点这份主数据，不接入鉴权流程。
- 不在权限列表页面提供搜索/筛选栏。
- 删除权限点不做"是否有关联数据"的阻塞校验——原因与 `role-management` 一致：本次权限点不关联任何其他实体（没有角色勾选关系），没有可校验的下游数据。
- 不主动把菜单文案从"权限点管理"改成"权限管理"——沿用 `application-management` 落地时的处理方式：先按现有文案实现功能，是否改名交由用户后续单独确认（该项目里应用管理菜单就是先落地、用户确认后再单独改名的）。

## Decisions

- **新增独立顶层包 `cn.nihility.rbac.permission`，不复用 `role`/`dict` 包**：权限点是与角色、字典类型、应用平级的新领域实体，按项目惯例新建独立包（`controller`/`service`/`service.impl`/`dto`/`entity`/`mapper`/`mapstruct`/`constant`）。
- **直接照抄刚落地的 `RoleServiceImpl` 结构**：字段形状、业务规则（编码唯一）、删除语义（无阻塞校验）完全一致，是本项目里连续第二次出现的"扁平实体 + 编码唯一"模式，没有必要重新设计。
- **`PermissionStatus` 独立建常量类，不复用 `OrgStatus`/`UserStatus`/`DictStatus`/`PositionStatus`/`AppStatus`/`RoleStatus`**：与项目"每个实体独立一份状态常量类"的既有惯例一致。
- **列表接口 `GET /api/permissions` 不接受任何筛选参数，只有 `page`/`pageSize`**：与 `role-management`/`application-management` 保持一致的收敛范围（未来如需搜索，另开 change）。
- **表名/实体/接口前缀用 `Permission` 而不是 `PermissionPoint`**：RBAC 语境下"权限点"就是"权限"本身（最细粒度的可授予单元），项目其他地方（`permissionKey` 字段命名、`permission:point:view` 这个权限点编码本身）也用的是 `permission` 这个词根，用更短的 `Permission` 前缀与既有措辞保持一致，避免引入 `PermissionPoint` 这个本项目代码里没出现过的新词。

## Risks / Trade-offs

- [权限点管理本次完全独立于角色管理和实际鉴权链路，落地后仍然是"孤立"的主数据，还不能在登录/接口调用时真正生效] → 与 `role-management` 落地时的取舍一致，属于分阶段推进的既定路径：先让两边的主数据都能独立维护，再在后续 change 里把"角色勾选权限点"和"接口鉴权引用权限点"这两条关联能力补上，避免在下游能力还不明确时做假设性设计。

## Migration Plan

- 新增 `backend/src/main/resources/db/migration/V9__init_tab_permission.sql`：
  ```sql
  CREATE TABLE `tab_permission` (
      `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
      `name`        VARCHAR(64)  NOT NULL COMMENT '权限名称',
      `code`        VARCHAR(64)  NOT NULL COMMENT '权限编码',
      `show_order`  INT          NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前',
      `remark`      VARCHAR(255)          DEFAULT NULL COMMENT '备注',
      `status`      INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）',
      `create_by`   VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
      `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
      `update_by`   VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
      `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
      PRIMARY KEY (`id`),
      KEY `idx_tab_permission_status` (`status`),
      KEY `idx_tab_permission_code` (`code`)
  ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '权限点主数据表';
  ```
- 表名、列名均已按项目既有惯例检查过与 MySQL/PostgreSQL/Oracle/SQL Server 保留字的冲突（`tab_permission`/`name`/`code`/`show_order`/`remark`/`status`/`create_by`/`create_time`/`update_by`/`update_time` 均不是保留字，沿用 `tab_` 前缀和下划线命名，与其他表完全一致）。
- Flyway 迁移只前进不回退，如需撤销需另发新迁移脚本。
