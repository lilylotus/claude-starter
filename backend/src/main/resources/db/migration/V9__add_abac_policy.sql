-- ----------------------------------------------------------------------------
-- abac-policy-engine change：新增顶级模块 abac（design.md Decision 1~6）——在
-- 现有 RBAC 权限点判定通过之后，叠加一层基于用户属性 + 时间窗口的限制性（DENY-only）
-- 判定。建 4 张新表：tab_abac_policy（策略主表）、tab_abac_policy_resource（目标资源
-- 编码，与 menu 运行时鉴权使用的三段式权限编码口径一致）、tab_abac_policy_user_attr
-- （用户属性条件，关联 tab_metadata_field，biz_type=USER）、
-- tab_abac_policy_time_window（时间窗口条件，可选星期 + 起止时间）。
--
-- 必须在同一条脚本内一次性完成建表 + tab_menu + tab_permission + tab_role_permission
-- （授予 SUPER_ADMIN）：V1 里 SUPER_ADMIN 授权全部权限点是用一条 INSERT...SELECT 覆盖
-- 当时已插入的全部 tab_permission 行，之后新增的独立迁移脚本不会被那条一次性语句覆盖，
-- 必须自己显式补一条 tab_role_permission（V4 曾漏补、靠 V5 单独修复，V6/V8 的注释完整
-- 记录了这个教训，本次不重复）。全部写法参照 V8 的幂等模式（NOT EXISTS 防重复插入），
-- 支持脚本被重复应用到已执行过的环境而不报错/不重复插入。MySQL 5.7 兼容语法：不使用
-- 窗口函数/CTE/JSON_TABLE 等 8.0+ 特性。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_abac_policy`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `name`        VARCHAR(128) NOT NULL COMMENT '策略名称',
    `remark`      VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    `status`      INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用（无逻辑删除语义，删除接口做物理删除）',
    `show_order`  INT          NOT NULL DEFAULT 0 COMMENT '显示序号，仅用于列表展示排序，不参与判定优先级',
    `create_by`   VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_abac_policy_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = 'ABAC 策略规则主数据表';

CREATE TABLE IF NOT EXISTS `tab_abac_policy_resource`
(
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `policy_id`       BIGINT      NOT NULL COMMENT '所属策略 id，关联 tab_abac_policy.id',
    `permission_code` VARCHAR(128) NOT NULL COMMENT '目标资源编码（三段式权限编码，与 menu 运行时鉴权口径一致）',
    `create_by`       VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_abac_policy_resource` (`policy_id`, `permission_code`),
    KEY `idx_tab_abac_policy_resource_code` (`permission_code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = 'ABAC 策略目标资源编码表，随所属策略整体替换（先删后插），物理删除';

CREATE TABLE IF NOT EXISTS `tab_abac_policy_user_attr`
(
    `id`                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `policy_id`         BIGINT      NOT NULL COMMENT '所属策略 id，关联 tab_abac_policy.id',
    `metadata_field_id` BIGINT      NOT NULL COMMENT '关联的元数据字段 id，关联 tab_metadata_field.id（biz_type=USER）',
    `operator`          VARCHAR(8)  NOT NULL COMMENT '运算符：EQ=等于，NE=不等于，IN=属于多值',
    `attr_value`        VARCHAR(512) NOT NULL COMMENT '比较值：EQ/NE 为单个值，IN 为逗号分隔的多个值',
    `create_by`         VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`         VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_abac_policy_user_attr_policy_id` (`policy_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = 'ABAC 策略用户属性条件表，随所属策略整体替换（先删后插），物理删除';

CREATE TABLE IF NOT EXISTS `tab_abac_policy_time_window`
(
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `policy_id`     BIGINT      NOT NULL COMMENT '所属策略 id，关联 tab_abac_policy.id',
    `day_of_week`   TINYINT              DEFAULT NULL COMMENT '星期：1=周一...7=周日，NULL 表示每天均适用',
    `start_time`    TIME        NOT NULL COMMENT '窗口起始时间',
    `end_time`      TIME        NOT NULL COMMENT '窗口结束时间，须晚于起始时间',
    `create_by`     VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_abac_policy_time_window_policy_id` (`policy_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = 'ABAC 策略时间窗口条件表，随所属策略整体替换（先删后插），物理删除';

SET @admin_user_id_text := '1';

-- 1) tab_menu：挂在"权限管理"一级分组下，排在既有子菜单最后（show_order 取 0，比
--    AppAccessManagement 的 1 更小，排在其后）。
SET @permission_group_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'permission');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
SELECT 'ABAC 策略管理', 'AbacManagement:policy:view', @permission_group_id, 1, 0,
       'ABAC 策略管理页面访问（在 RBAC 权限点判定通过之后，叠加一层基于用户属性 + 时间窗口的限制性判定）',
       2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
WHERE @permission_group_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `tab_menu` WHERE `code` = 'AbacManagement:policy:view');

SET @abac_policy_menu_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'AbacManagement:policy:view');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
SELECT '新增策略规则', 'AbacManagement:policy:add', @abac_policy_menu_id, 2, 60, NULL, 2000, @admin_user_id_text,
       NOW(), @admin_user_id_text, NOW()
WHERE @abac_policy_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `tab_menu` WHERE `code` = 'AbacManagement:policy:add');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
SELECT '编辑策略规则', 'AbacManagement:policy:edit', @abac_policy_menu_id, 2, 50, NULL, 2000, @admin_user_id_text,
       NOW(), @admin_user_id_text, NOW()
WHERE @abac_policy_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `tab_menu` WHERE `code` = 'AbacManagement:policy:edit');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
SELECT '启用策略规则', 'AbacManagement:policy:enable', @abac_policy_menu_id, 2, 40, NULL, 2000, @admin_user_id_text,
       NOW(), @admin_user_id_text, NOW()
WHERE @abac_policy_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `tab_menu` WHERE `code` = 'AbacManagement:policy:enable');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
SELECT '停用策略规则', 'AbacManagement:policy:disable', @abac_policy_menu_id, 2, 30, NULL, 2000, @admin_user_id_text,
       NOW(), @admin_user_id_text, NOW()
WHERE @abac_policy_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `tab_menu` WHERE `code` = 'AbacManagement:policy:disable');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
SELECT '删除策略规则', 'AbacManagement:policy:delete', @abac_policy_menu_id, 2, 20, NULL, 2000, @admin_user_id_text,
       NOW(), @admin_user_id_text, NOW()
WHERE @abac_policy_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `tab_menu` WHERE `code` = 'AbacManagement:policy:delete');

-- 2) tab_permission：对应 6 条权限点记录。
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`,
                              `update_by`, `update_time`)
SELECT 'ABAC 策略管理页面访问', 'AbacManagement:policy:view', 0, NULL, 2000, @admin_user_id_text, NOW(),
       @admin_user_id_text, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `tab_permission` WHERE `code` = 'AbacManagement:policy:view');

INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`,
                              `update_by`, `update_time`)
SELECT '新增策略规则', 'AbacManagement:policy:add', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text,
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM `tab_permission` WHERE `code` = 'AbacManagement:policy:add');

INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`,
                              `update_by`, `update_time`)
SELECT '编辑策略规则', 'AbacManagement:policy:edit', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text,
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM `tab_permission` WHERE `code` = 'AbacManagement:policy:edit');

INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`,
                              `update_by`, `update_time`)
SELECT '启用策略规则', 'AbacManagement:policy:enable', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text,
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM `tab_permission` WHERE `code` = 'AbacManagement:policy:enable');

INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`,
                              `update_by`, `update_time`)
SELECT '停用策略规则', 'AbacManagement:policy:disable', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text,
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM `tab_permission` WHERE `code` = 'AbacManagement:policy:disable');

INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`,
                              `update_by`, `update_time`)
SELECT '删除策略规则', 'AbacManagement:policy:delete', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text,
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM `tab_permission` WHERE `code` = 'AbacManagement:policy:delete');

-- 3) tab_role_permission：显式为 SUPER_ADMIN 角色补齐这 6 条权限点的授权。
SET @super_admin_role_id := (SELECT `id` FROM `tab_role` WHERE `code` = 'SUPER_ADMIN');

SET @abac_policy_view_permission_id := (SELECT `id` FROM `tab_permission` WHERE `code` = 'AbacManagement:policy:view');
SET @abac_policy_add_permission_id := (SELECT `id` FROM `tab_permission` WHERE `code` = 'AbacManagement:policy:add');
SET @abac_policy_edit_permission_id := (SELECT `id` FROM `tab_permission` WHERE `code` = 'AbacManagement:policy:edit');
SET @abac_policy_enable_permission_id := (SELECT `id` FROM `tab_permission` WHERE `code` = 'AbacManagement:policy:enable');
SET @abac_policy_disable_permission_id := (SELECT `id` FROM `tab_permission` WHERE `code` = 'AbacManagement:policy:disable');
SET @abac_policy_delete_permission_id := (SELECT `id` FROM `tab_permission` WHERE `code` = 'AbacManagement:policy:delete');

INSERT INTO `tab_role_permission` (`role_id`, `permission_id`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT @super_admin_role_id, @abac_policy_view_permission_id, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
WHERE @super_admin_role_id IS NOT NULL
  AND @abac_policy_view_permission_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `tab_role_permission`
      WHERE `role_id` = @super_admin_role_id AND `permission_id` = @abac_policy_view_permission_id);

INSERT INTO `tab_role_permission` (`role_id`, `permission_id`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT @super_admin_role_id, @abac_policy_add_permission_id, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
WHERE @super_admin_role_id IS NOT NULL
  AND @abac_policy_add_permission_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `tab_role_permission`
      WHERE `role_id` = @super_admin_role_id AND `permission_id` = @abac_policy_add_permission_id);

INSERT INTO `tab_role_permission` (`role_id`, `permission_id`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT @super_admin_role_id, @abac_policy_edit_permission_id, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
WHERE @super_admin_role_id IS NOT NULL
  AND @abac_policy_edit_permission_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `tab_role_permission`
      WHERE `role_id` = @super_admin_role_id AND `permission_id` = @abac_policy_edit_permission_id);

INSERT INTO `tab_role_permission` (`role_id`, `permission_id`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT @super_admin_role_id, @abac_policy_enable_permission_id, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
WHERE @super_admin_role_id IS NOT NULL
  AND @abac_policy_enable_permission_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `tab_role_permission`
      WHERE `role_id` = @super_admin_role_id AND `permission_id` = @abac_policy_enable_permission_id);

INSERT INTO `tab_role_permission` (`role_id`, `permission_id`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT @super_admin_role_id, @abac_policy_disable_permission_id, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
WHERE @super_admin_role_id IS NOT NULL
  AND @abac_policy_disable_permission_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `tab_role_permission`
      WHERE `role_id` = @super_admin_role_id AND `permission_id` = @abac_policy_disable_permission_id);

INSERT INTO `tab_role_permission` (`role_id`, `permission_id`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT @super_admin_role_id, @abac_policy_delete_permission_id, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
WHERE @super_admin_role_id IS NOT NULL
  AND @abac_policy_delete_permission_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `tab_role_permission`
      WHERE `role_id` = @super_admin_role_id AND `permission_id` = @abac_policy_delete_permission_id);
