-- ----------------------------------------------------------------------------
-- RBAC 权限管理系统 - 数据库基线脚本（Flyway 迁移版本 V1）
-- 本文件由原先的 V1~V34 共 34 个增量迁移文件合并整理而来，代表这些迁移按顺序执行
-- 完毕后的最终数据库状态（建表 + 全部种子数据），不再保留中间过程中的
-- ALTER/UPDATE 步骤。数据库需提前手动创建，例如：
--   CREATE DATABASE rbac_demo DEFAULT CHARACTER SET utf8mb4;
-- 注意：本地开发库如果已经跑过旧的 V1~V34，需要先清空该库（或删除
-- flyway_schema_history 表）后重新执行本文件，否则 Flyway 会因为找不到对应版本号
-- 的历史文件而报错。
-- ----------------------------------------------------------------------------

-- ============================================================================
-- 组织管理模块
-- ============================================================================

CREATE TABLE IF NOT EXISTS `tab_org`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `name`        VARCHAR(64)  NOT NULL COMMENT '组织名称',
    `code`        VARCHAR(64)  NOT NULL COMMENT '组织编码',
    `parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '上级组织 id，0 表示顶级/根节点',
    `status`      INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）',
    `show_order`  INT          NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前',
    `remark`      VARCHAR(255) NULL COMMENT '备注',
    `ext1`        VARCHAR(255) NULL COMMENT '预留扩展字段 1，暂不使用',
    `ext2`        VARCHAR(255) NULL COMMENT '预留扩展字段 2，暂不使用',
    `ext3`        VARCHAR(255) NULL COMMENT '预留扩展字段 3，暂不使用',
    `ext4`        VARCHAR(255) NULL COMMENT '预留扩展字段 4，暂不使用',
    `ext5`        VARCHAR(255) NULL COMMENT '预留扩展字段 5，暂不使用',
    `ext6`        VARCHAR(255) NULL COMMENT '预留扩展字段 6，暂不使用',
    `ext7`        VARCHAR(255) NULL COMMENT '预留扩展字段 7，暂不使用',
    `ext8`        VARCHAR(255) NULL COMMENT '预留扩展字段 8，暂不使用',
    `ext9`        VARCHAR(255) NULL COMMENT '预留扩展字段 9，暂不使用',
    `ext10`       VARCHAR(255) NULL COMMENT '预留扩展字段 10，暂不使用',
    `create_by`   VARCHAR(64)  NULL COMMENT '创建人',
    `create_time` DATETIME     NULL COMMENT '创建时间',
    `update_by`   VARCHAR(64)  NULL COMMENT '更新人',
    `update_time` DATETIME     NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_org_parent_id` (`parent_id`),
    KEY `idx_tab_org_code` (`code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '组织机构表';

-- ============================================================================
-- 字典管理模块
-- ============================================================================

CREATE TABLE IF NOT EXISTS `tab_dict_type`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `name`        VARCHAR(64)  NOT NULL COMMENT '字典类型名称',
    `code`        VARCHAR(64)  NOT NULL COMMENT '字典类型编码，未删除范围内全局唯一',
    `show_order`  INT          NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前',
    `remark`      VARCHAR(255) NULL COMMENT '备注',
    `status`      INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）',
    `create_by`   VARCHAR(64)  NULL COMMENT '创建人',
    `create_time` DATETIME     NULL COMMENT '创建时间',
    `update_by`   VARCHAR(64)  NULL COMMENT '更新人',
    `update_time` DATETIME     NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_dict_type_code` (`code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '字典类型表';

CREATE TABLE IF NOT EXISTS `tab_dict_item`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `dict_type_id` BIGINT       NOT NULL COMMENT '所属字典类型 id，关联 tab_dict_type.id',
    `label`        VARCHAR(64)  NOT NULL COMMENT '字典项标签（展示文案）',
    `code`         VARCHAR(64)  NOT NULL COMMENT '字典项编码，同一 dict_type_id 下未删除范围内唯一',
    `show_order`   INT          NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前',
    `remark`       VARCHAR(255) NULL COMMENT '备注',
    `status`       INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）',
    `create_by`    VARCHAR(64)  NULL COMMENT '创建人',
    `create_time`  DATETIME     NULL COMMENT '创建时间',
    `update_by`    VARCHAR(64)  NULL COMMENT '更新人',
    `update_time`  DATETIME     NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_dict_item_dict_type_id` (`dict_type_id`),
    KEY `idx_tab_dict_item_code` (`code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '字典项表';

-- 预置"任职类型"字典数据，供用户管理模块任职记录的 positionType 字段消费
INSERT INTO `tab_dict_type` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('任职类型', 'position_type', 0, '用户管理模块任职类型字段的数据来源', 2000, 'admin', NOW(), 'admin', NOW());

INSERT INTO `tab_dict_item` (`dict_type_id`, `label`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT `id`, '主职', 'primary', 3, NULL, 2000, 'admin', NOW(), 'admin', NOW()
FROM `tab_dict_type`
WHERE `code` = 'position_type';

INSERT INTO `tab_dict_item` (`dict_type_id`, `label`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT `id`, '兼职', 'part_time', 2, NULL, 2000, 'admin', NOW(), 'admin', NOW()
FROM `tab_dict_type`
WHERE `code` = 'position_type';

INSERT INTO `tab_dict_item` (`dict_type_id`, `label`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT `id`, '挂职', 'temporary', 1, NULL, 2000, 'admin', NOW(), 'admin', NOW()
FROM `tab_dict_type`
WHERE `code` = 'position_type';

-- 预置"性别"字典数据，供用户管理模块的 gender 字段消费
INSERT INTO `tab_dict_type` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('性别', 'gender', 0, '用户管理模块性别字段的数据来源', 2000, 'admin', NOW(), 'admin', NOW());

INSERT INTO `tab_dict_item` (`dict_type_id`, `label`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT `id`, '未知', 'unknown', 1, NULL, 2000, 'admin', NOW(), 'admin', NOW()
FROM `tab_dict_type`
WHERE `code` = 'gender';

INSERT INTO `tab_dict_item` (`dict_type_id`, `label`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT `id`, '男', 'male', 2, NULL, 2000, 'admin', NOW(), 'admin', NOW()
FROM `tab_dict_type`
WHERE `code` = 'gender';

INSERT INTO `tab_dict_item` (`dict_type_id`, `label`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT `id`, '女', 'female', 3, NULL, 2000, 'admin', NOW(), 'admin', NOW()
FROM `tab_dict_type`
WHERE `code` = 'gender';

-- ============================================================================
-- 用户管理 / 任职管理模块
-- ============================================================================

CREATE TABLE IF NOT EXISTS `tab_user`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `name`        VARCHAR(64) NOT NULL COMMENT '用户姓名',
    `code`        VARCHAR(64) NOT NULL COMMENT '用户编号，未删除范围内唯一',
    `gender`      VARCHAR(64) NOT NULL DEFAULT 'unknown' COMMENT '性别，取自字典类型 gender 下的字典项编码',
    `mobile`      VARCHAR(20) NULL COMMENT '手机号，不做唯一性约束',
    `id_card`     VARCHAR(18) NULL COMMENT '身份证号，若提供需在未删除范围内唯一',
    `show_order`  INT         NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前',
    `remark`      VARCHAR(255) NULL COMMENT '备注',
    `status`      INT         NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）',
    `ext1`        VARCHAR(255) NULL COMMENT '预留扩展字段 1，暂不使用',
    `ext2`        VARCHAR(255) NULL COMMENT '预留扩展字段 2，暂不使用',
    `ext3`        VARCHAR(255) NULL COMMENT '预留扩展字段 3，暂不使用',
    `ext4`        VARCHAR(255) NULL COMMENT '预留扩展字段 4，暂不使用',
    `ext5`        VARCHAR(255) NULL COMMENT '预留扩展字段 5，暂不使用',
    `ext6`        VARCHAR(255) NULL COMMENT '预留扩展字段 6，暂不使用',
    `ext7`        VARCHAR(255) NULL COMMENT '预留扩展字段 7，暂不使用',
    `ext8`        VARCHAR(255) NULL COMMENT '预留扩展字段 8，暂不使用',
    `ext9`        VARCHAR(255) NULL COMMENT '预留扩展字段 9，暂不使用',
    `ext10`       VARCHAR(255) NULL COMMENT '预留扩展字段 10，暂不使用',
    `create_by`   VARCHAR(64) NULL COMMENT '创建人',
    `create_time` DATETIME    NULL COMMENT '创建时间',
    `update_by`   VARCHAR(64) NULL COMMENT '更新人',
    `update_time` DATETIME    NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_user_code` (`code`),
    KEY `idx_tab_user_id_card` (`id_card`),
    KEY `idx_tab_user_mobile` (`mobile`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '用户表';

CREATE TABLE IF NOT EXISTS `tab_user_position`
(
    `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `user_id`          BIGINT      NOT NULL COMMENT '所属用户 id，关联 tab_user.id',
    `org_id`           BIGINT      NOT NULL COMMENT '所属组织 id，关联 tab_org.id，不建物理外键',
    `position_type`    VARCHAR(64) NOT NULL COMMENT '任职类型编码，取自字典类型 position_type 下的字典项编码',
    `position_address` VARCHAR(255) NULL COMMENT '任职地址',
    `position_phone`   VARCHAR(20) NULL COMMENT '任职电话',
    `show_order`       INT         NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前',
    `status`           INT NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）',
    `remark`           VARCHAR(255) NULL COMMENT '备注',
    `ext1`             VARCHAR(255) NULL COMMENT '预留扩展字段 1，暂不使用',
    `ext2`             VARCHAR(255) NULL COMMENT '预留扩展字段 2，暂不使用',
    `ext3`             VARCHAR(255) NULL COMMENT '预留扩展字段 3，暂不使用',
    `ext4`             VARCHAR(255) NULL COMMENT '预留扩展字段 4，暂不使用',
    `ext5`             VARCHAR(255) NULL COMMENT '预留扩展字段 5，暂不使用',
    `ext6`             VARCHAR(255) NULL COMMENT '预留扩展字段 6，暂不使用',
    `ext7`             VARCHAR(255) NULL COMMENT '预留扩展字段 7，暂不使用',
    `ext8`             VARCHAR(255) NULL COMMENT '预留扩展字段 8，暂不使用',
    `ext9`             VARCHAR(255) NULL COMMENT '预留扩展字段 9，暂不使用',
    `ext10`            VARCHAR(255) NULL COMMENT '预留扩展字段 10，暂不使用',
    `create_by`        VARCHAR(64) NULL COMMENT '创建人',
    `create_time`      DATETIME    NULL COMMENT '创建时间',
    `update_by`        VARCHAR(64) NULL COMMENT '更新人',
    `update_time`      DATETIME    NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_user_position_user_id` (`user_id`),
    KEY `idx_tab_user_position_org_id` (`org_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '用户任职记录表，无独立 status 列，任职记录做物理删除';

-- ============================================================================
-- 应用管理模块
-- ============================================================================

CREATE TABLE IF NOT EXISTS `tab_app` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `name`        VARCHAR(64)  NOT NULL COMMENT '应用名称',
    `code`        VARCHAR(64)  NOT NULL COMMENT '应用编码',
    `owner_id`    BIGINT       NOT NULL COMMENT '负责人用户 id',
    `org_id`      BIGINT       NOT NULL COMMENT '所属组织 id',
    `show_order`  INT          NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前',
    `remark`      VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    `status`      INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）',
    `ext1`        VARCHAR(255) NULL COMMENT '预留扩展字段 1，暂不使用',
    `ext2`        VARCHAR(255) NULL COMMENT '预留扩展字段 2，暂不使用',
    `ext3`        VARCHAR(255) NULL COMMENT '预留扩展字段 3，暂不使用',
    `ext4`        VARCHAR(255) NULL COMMENT '预留扩展字段 4，暂不使用',
    `ext5`        VARCHAR(255) NULL COMMENT '预留扩展字段 5，暂不使用',
    `ext6`        VARCHAR(255) NULL COMMENT '预留扩展字段 6，暂不使用',
    `ext7`        VARCHAR(255) NULL COMMENT '预留扩展字段 7，暂不使用',
    `ext8`        VARCHAR(255) NULL COMMENT '预留扩展字段 8，暂不使用',
    `ext9`        VARCHAR(255) NULL COMMENT '预留扩展字段 9，暂不使用',
    `ext10`       VARCHAR(255) NULL COMMENT '预留扩展字段 10，暂不使用',
    `create_by`   VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_app_owner_id` (`owner_id`),
    KEY `idx_tab_app_org_id` (`org_id`),
    KEY `idx_tab_app_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '应用主数据表';

-- ============================================================================
-- 角色管理模块
-- ============================================================================

CREATE TABLE IF NOT EXISTS `tab_role` (
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
    KEY `idx_tab_role_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色主数据表';

-- ============================================================================
-- 权限点管理模块
-- ============================================================================

CREATE TABLE IF NOT EXISTS `tab_permission` (
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
    KEY `idx_tab_permission_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '权限点主数据表';

-- ============================================================================
-- 菜单管理模块（菜单/按钮/API 资源）
-- ============================================================================

CREATE TABLE IF NOT EXISTS `tab_menu` (
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
    KEY `idx_tab_menu_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '菜单/按钮/API 资源主数据表';

-- ---- 第一层：侧边栏一级导航分组（resourceType = 1 菜单，parentId = 0） ----

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('身份管理', 'identity', 0, 1, 40, '侧边栏一级导航分组', 2000, 'admin', NOW(), 'admin', NOW()),
       ('应用管理', 'application', 0, 1, 30, '侧边栏一级导航分组', 2000, 'admin', NOW(), 'admin', NOW()),
       ('权限管理', 'permission', 0, 1, 20, '侧边栏一级导航分组', 2000, 'admin', NOW(), 'admin', NOW()),
       ('系统管理', 'system', 0, 1, 10, '侧边栏一级导航分组', 2000, 'admin', NOW(), 'admin', NOW());

SET @identity_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'identity');
SET @application_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'application');
SET @permission_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'permission');
SET @system_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'system');

-- ---- 第二层：8 个已实现管理页面（resourceType = 1 菜单） ----

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('组织管理', 'OrgManagement:org:view', @identity_id, 1, 30, '组织管理页面访问（含左侧组织树浏览）', 2000,
        'admin', NOW(), 'admin', NOW()),
       ('用户管理', 'UserManagement:user:view', @identity_id, 1, 20, '用户管理页面访问', 2000, 'admin', NOW(),
        'admin', NOW()),
       ('任职管理', 'PositionManagement:position:view', @identity_id, 1, 10, '任职管理页面访问', 2000, 'admin',
        NOW(), 'admin', NOW()),
       ('应用管理', 'AppManagement:app:view', @application_id, 1, 10, '应用管理页面访问', 2000, 'admin', NOW(),
        'admin', NOW()),
       ('角色管理', 'RoleManagement:role:view', @permission_id, 1, 20, '角色管理页面访问', 2000, 'admin', NOW(),
        'admin', NOW()),
       ('权限点管理', 'PermissionManagement:permission:view', @permission_id, 1, 10, '权限点管理页面访问', 2000,
        'admin', NOW(), 'admin', NOW()),
       ('菜单管理', 'MenuManagement:menu:view', @system_id, 1, 20, '菜单/资源管理页面访问', 2000, 'admin', NOW(),
        'admin', NOW()),
       ('字典管理', 'DictManagement:dictType:view', @system_id, 1, 10, '字典管理页面访问（含字典类型列表）', 2000,
        'admin', NOW(), 'admin', NOW());

SET @org_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'OrgManagement:org:view');
SET @user_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'UserManagement:user:view');
SET @position_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'PositionManagement:position:view');
SET @app_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'AppManagement:app:view');
SET @role_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'RoleManagement:role:view');
SET @permission_point_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'PermissionManagement:permission:view');
SET @menu_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'MenuManagement:menu:view');
SET @dict_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'DictManagement:dictType:view');

-- ---- 第三层：按钮（resourceType = 2 按钮） ----

-- 组织管理
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('新增组织', 'OrgManagement:org:add', @org_id, 2, 60, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('查看组织详情', 'OrgManagement:org:detail', @org_id, 2, 50, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('编辑组织', 'OrgManagement:org:edit', @org_id, 2, 40, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('启用组织', 'OrgManagement:org:enable', @org_id, 2, 30, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('停用组织', 'OrgManagement:org:disable', @org_id, 2, 20, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('删除组织', 'OrgManagement:org:delete', @org_id, 2, 10, NULL, 2000, 'admin', NOW(), 'admin', NOW());

-- 用户管理
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('新增用户', 'UserManagement:user:add', @user_id, 2, 60, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('查看用户详情', 'UserManagement:user:detail', @user_id, 2, 50, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('编辑用户', 'UserManagement:user:edit', @user_id, 2, 40, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('启用用户', 'UserManagement:user:enable', @user_id, 2, 30, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('停用用户', 'UserManagement:user:disable', @user_id, 2, 20, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('删除用户', 'UserManagement:user:delete', @user_id, 2, 10, NULL, 2000, 'admin', NOW(), 'admin', NOW());

-- 任职管理
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('新增任职记录', 'PositionManagement:position:add', @position_id, 2, 60, NULL, 2000, 'admin', NOW(), 'admin',
        NOW()),
       ('查看任职详情', 'PositionManagement:position:detail', @position_id, 2, 50, NULL, 2000, 'admin', NOW(),
        'admin', NOW()),
       ('编辑任职记录', 'PositionManagement:position:edit', @position_id, 2, 40, NULL, 2000, 'admin', NOW(), 'admin',
        NOW()),
       ('启用任职记录', 'PositionManagement:position:enable', @position_id, 2, 30, NULL, 2000, 'admin', NOW(),
        'admin', NOW()),
       ('停用任职记录', 'PositionManagement:position:disable', @position_id, 2, 20, NULL, 2000, 'admin', NOW(),
        'admin', NOW()),
       ('删除任职记录', 'PositionManagement:position:delete', @position_id, 2, 10, NULL, 2000, 'admin', NOW(),
        'admin', NOW());

-- 应用管理
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('新增应用', 'AppManagement:app:add', @app_id, 2, 60, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('查看应用详情', 'AppManagement:app:detail', @app_id, 2, 50, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('编辑应用', 'AppManagement:app:edit', @app_id, 2, 40, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('启用应用', 'AppManagement:app:enable', @app_id, 2, 30, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('停用应用', 'AppManagement:app:disable', @app_id, 2, 20, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('删除应用', 'AppManagement:app:delete', @app_id, 2, 10, NULL, 2000, 'admin', NOW(), 'admin', NOW());

-- 角色管理
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('新增角色', 'RoleManagement:role:add', @role_id, 2, 60, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('查看角色详情', 'RoleManagement:role:detail', @role_id, 2, 50, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('编辑角色', 'RoleManagement:role:edit', @role_id, 2, 40, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('启用角色', 'RoleManagement:role:enable', @role_id, 2, 30, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('停用角色', 'RoleManagement:role:disable', @role_id, 2, 20, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('删除角色', 'RoleManagement:role:delete', @role_id, 2, 10, NULL, 2000, 'admin', NOW(), 'admin', NOW());

-- 权限点管理
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('新增权限点', 'PermissionManagement:permission:add', @permission_point_id, 2, 60, NULL, 2000, 'admin', NOW(),
        'admin', NOW()),
       ('查看权限点详情', 'PermissionManagement:permission:detail', @permission_point_id, 2, 50, NULL, 2000, 'admin',
        NOW(), 'admin', NOW()),
       ('编辑权限点', 'PermissionManagement:permission:edit', @permission_point_id, 2, 40, NULL, 2000, 'admin',
        NOW(), 'admin', NOW()),
       ('启用权限点', 'PermissionManagement:permission:enable', @permission_point_id, 2, 30, NULL, 2000, 'admin',
        NOW(), 'admin', NOW()),
       ('停用权限点', 'PermissionManagement:permission:disable', @permission_point_id, 2, 20, NULL, 2000, 'admin',
        NOW(), 'admin', NOW()),
       ('删除权限点', 'PermissionManagement:permission:delete', @permission_point_id, 2, 10, NULL, 2000, 'admin',
        NOW(), 'admin', NOW());

-- 菜单管理
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('新增资源', 'MenuManagement:menu:add', @menu_id, 2, 60, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('查看资源详情', 'MenuManagement:menu:detail', @menu_id, 2, 50, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('编辑资源', 'MenuManagement:menu:edit', @menu_id, 2, 40, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('启用资源', 'MenuManagement:menu:enable', @menu_id, 2, 30, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('停用资源', 'MenuManagement:menu:disable', @menu_id, 2, 20, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('删除资源', 'MenuManagement:menu:delete', @menu_id, 2, 10, NULL, 2000, 'admin', NOW(), 'admin', NOW());

-- 字典管理（字典类型 + 字典项两组按钮，同挂在字典管理页面节点下）
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('新增字典类型', 'DictManagement:dictType:add', @dict_id, 2, 100, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('查看字典类型详情', 'DictManagement:dictType:detail', @dict_id, 2, 95, NULL, 2000, 'admin', NOW(), 'admin',
        NOW()),
       ('编辑字典类型', 'DictManagement:dictType:edit', @dict_id, 2, 90, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('启用字典类型', 'DictManagement:dictType:enable', @dict_id, 2, 80, NULL, 2000, 'admin', NOW(), 'admin',
        NOW()),
       ('停用字典类型', 'DictManagement:dictType:disable', @dict_id, 2, 70, NULL, 2000, 'admin', NOW(), 'admin',
        NOW()),
       ('删除字典类型', 'DictManagement:dictType:delete', @dict_id, 2, 60, NULL, 2000, 'admin', NOW(), 'admin',
        NOW()),
       ('新增字典项', 'DictManagement:dictItem:add', @dict_id, 2, 50, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('查看字典项详情', 'DictManagement:dictItem:detail', @dict_id, 2, 45, NULL, 2000, 'admin', NOW(), 'admin',
        NOW()),
       ('编辑字典项', 'DictManagement:dictItem:edit', @dict_id, 2, 40, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('启用字典项', 'DictManagement:dictItem:enable', @dict_id, 2, 30, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('停用字典项', 'DictManagement:dictItem:disable', @dict_id, 2, 20, NULL, 2000, 'admin', NOW(), 'admin',
        NOW()),
       ('删除字典项', 'DictManagement:dictItem:delete', @dict_id, 2, 10, NULL, 2000, 'admin', NOW(), 'admin', NOW());

-- 管理员管理（挂在 permission 一级分组下，排在角色管理、权限点管理之后）
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('管理员管理', 'AdminManagement:admin:view', @permission_id, 1, 5, '管理员管理页面访问', 2000, 'admin',
        NOW(), 'admin', NOW());

SET @admin_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'AdminManagement:admin:view');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('新增管理员', 'AdminManagement:admin:add', @admin_id, 2, 60, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('查看管理员详情', 'AdminManagement:admin:detail', @admin_id, 2, 50, NULL, 2000, 'admin', NOW(), 'admin',
        NOW()),
       ('编辑管理员', 'AdminManagement:admin:edit', @admin_id, 2, 40, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('启用管理员', 'AdminManagement:admin:enable', @admin_id, 2, 30, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('停用管理员', 'AdminManagement:admin:disable', @admin_id, 2, 20, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('删除管理员', 'AdminManagement:admin:delete', @admin_id, 2, 10, NULL, 2000, 'admin', NOW(), 'admin', NOW());

-- 操作日志（挂在 system 一级分组下，只读，没有按钮资源）
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('操作日志', 'OperationLogManagement:log:view', @system_id, 1, 5, '操作日志管理页面访问', 2000, 'admin',
        NOW(), 'admin', NOW());

-- 字典类型、字典项"详情"按钮已直接并入上面字典管理的批量 INSERT 中，此处不再重复

-- 元数据配置（挂在 system 一级分组下，只支持编辑/详情，不提供新增/删除入口）
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('元数据配置', 'MetadataFieldManagement:metadataField:view', @system_id, 1, 5, '元数据字段配置页面访问', 2000,
        'admin', NOW(), 'admin', NOW());

SET @metadata_field_id := (SELECT `id`
                            FROM `tab_menu`
                            WHERE `code` = 'MetadataFieldManagement:metadataField:view');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('查看元数据字段详情', 'MetadataFieldManagement:metadataField:detail', @metadata_field_id, 2, 40, NULL, 2000,
        'admin', NOW(), 'admin', NOW()),
       ('编辑元数据字段', 'MetadataFieldManagement:metadataField:edit', @metadata_field_id, 2, 30, NULL, 2000,
        'admin', NOW(), 'admin', NOW()),
       ('启用元数据字段', 'MetadataFieldManagement:metadataField:enable', @metadata_field_id, 2, 20, NULL, 2000,
        'admin', NOW(), 'admin', NOW()),
       ('停用元数据字段', 'MetadataFieldManagement:metadataField:disable', @metadata_field_id, 2, 10, NULL, 2000,
        'admin', NOW(), 'admin', NOW());

-- 表单管理（挂在 system 一级分组下，排在元数据配置之后，完整支持新增/编辑/启停用/删除/详情）
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('表单管理', 'FormFieldManagement:formField:view', @system_id, 1, 1, '表单字段定义管理页面访问', 2000, 'admin',
        NOW(), 'admin', NOW());

SET @form_field_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'FormFieldManagement:formField:view');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('新增表单字段', 'FormFieldManagement:formField:add', @form_field_id, 2, 60, NULL, 2000, 'admin', NOW(),
        'admin', NOW()),
       ('查看表单字段详情', 'FormFieldManagement:formField:detail', @form_field_id, 2, 50, NULL, 2000, 'admin',
        NOW(), 'admin', NOW()),
       ('编辑表单字段', 'FormFieldManagement:formField:edit', @form_field_id, 2, 40, NULL, 2000, 'admin', NOW(),
        'admin', NOW()),
       ('启用表单字段', 'FormFieldManagement:formField:enable', @form_field_id, 2, 30, NULL, 2000, 'admin', NOW(),
        'admin', NOW()),
       ('停用表单字段', 'FormFieldManagement:formField:disable', @form_field_id, 2, 20, NULL, 2000, 'admin', NOW(),
        'admin', NOW()),
       ('删除表单字段', 'FormFieldManagement:formField:delete', @form_field_id, 2, 10, NULL, 2000, 'admin', NOW(),
        'admin', NOW());

-- ============================================================================
-- 管理员管理模块
-- ============================================================================

CREATE TABLE IF NOT EXISTS `tab_admin` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `name`        VARCHAR(64)  NOT NULL COMMENT '管理员名称',
    `code`        VARCHAR(64)  NOT NULL COMMENT '管理员编码',
    `user_id`     BIGINT       NOT NULL COMMENT '关联用户 id，关联 tab_user.id，不建物理外键',
    `show_order`  INT          NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前',
    `remark`      VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    `status`      INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）',
    `create_by`   VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_admin_code` (`code`),
    KEY `idx_tab_admin_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '管理员主数据表';

CREATE TABLE IF NOT EXISTS `tab_admin_role` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `admin_id`    BIGINT       NOT NULL COMMENT '管理员 id，关联 tab_admin.id',
    `role_id`     BIGINT       NOT NULL COMMENT '角色 id，关联 tab_role.id',
    `create_by`   VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_admin_role` (`admin_id`, `role_id`),
    KEY `idx_tab_admin_role_admin_id` (`admin_id`),
    KEY `idx_tab_admin_role_role_id` (`role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '管理员角色关联表，无独立 status，随管理员整体同步、物理删除';

CREATE TABLE IF NOT EXISTS `tab_admin_org_scope` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `admin_id`         BIGINT       NOT NULL COMMENT '管理员 id，关联 tab_admin.id',
    `org_id`           BIGINT       NOT NULL COMMENT '组织 id，关联 tab_org.id',
    `include_children` TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否包含递归子组织：0=否，1=是',
    `create_by`        VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_admin_org_scope` (`admin_id`, `org_id`),
    KEY `idx_tab_admin_org_scope_admin_id` (`admin_id`),
    KEY `idx_tab_admin_org_scope_org_id` (`org_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '管理员组织管辖范围表，无独立 status，随管理员整体同步、物理删除';

-- ============================================================================
-- 操作日志模块
-- ============================================================================

CREATE TABLE IF NOT EXISTS `tab_operation_log` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `module`             VARCHAR(32)  NOT NULL COMMENT '业务模块中文名，如"组织管理"',
    `resource_type`      VARCHAR(32)  NOT NULL COMMENT '资源类型编码，如 org/user/position/app/role/permission/admin/menu/dictType/dictItem',
    `resource_name`      VARCHAR(32)  NOT NULL COMMENT '资源类型中文名，如"组织"',
    `operation_type`     INT          NOT NULL COMMENT '操作类型：1=新增，2=编辑，3=启用，4=停用，5=删除',
    `operate_source`     INT          NOT NULL DEFAULT 0 COMMENT '操作来源：0=界面操作，1=Excel导入',
    `target_id`          BIGINT       NOT NULL COMMENT '被操作对象主键 id',
    `target_name`        VARCHAR(128) NOT NULL COMMENT '被操作对象名称快照，即使对象后续被改名/删除也保留操作当时的名称',
    `change_detail`      TEXT         NOT NULL COMMENT '字段变更详情，JSON 数组，每项 {field,oldValue,newValue}',
    `operate_ip`         VARCHAR(64)  DEFAULT NULL COMMENT '操作发起 IP，从当前 HTTP 请求自动获取，取不到（如非 HTTP 上下文中调用）时为空',
    `operate_terminal`   VARCHAR(32)  DEFAULT NULL COMMENT '操作终端类型，如 PC/Mobile/Tablet，从 User-Agent 解析，解析不出时为空',
    `operate_os`         VARCHAR(64)  DEFAULT NULL COMMENT '操作系统，如 Windows 10/macOS/Android，从 User-Agent 解析，解析不出时为空',
    `operate_browser`    VARCHAR(64)  DEFAULT NULL COMMENT '操作浏览器，如 Chrome 120，从 User-Agent 解析，解析不出时为空',
    `operate_user_agent` VARCHAR(512) DEFAULT NULL COMMENT '原始 User-Agent 请求头，取不到时为空',
    `create_by`          VARCHAR(64)  NOT NULL COMMENT '操作人，即创建人',
    `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间，即创建时间',
    `update_by`          VARCHAR(64)  NOT NULL COMMENT '更新人，日志不可变更，恒等于 create_by',
    `update_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间，日志不可变更，恒等于 create_time',
    PRIMARY KEY (`id`),
    KEY `idx_tab_operation_log_resource` (`resource_type`, `target_id`),
    KEY `idx_tab_operation_log_module` (`module`),
    KEY `idx_tab_operation_log_create_by` (`create_by`),
    KEY `idx_tab_operation_log_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作日志表，只追加不更新不删除';

-- ============================================================================
-- 元数据字段配置模块
-- ============================================================================

CREATE TABLE IF NOT EXISTS `tab_metadata_field`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `biz_type`    VARCHAR(20)  NOT NULL COMMENT '业务对象类型：ORG/USER/POSITION/APP',
    `table_name`  VARCHAR(64)  NOT NULL COMMENT '字段所属表名称，如 tab_org，创建后不可修改',
    `column_name` VARCHAR(64)  NOT NULL COMMENT '字段列名（数据库字段定义），如 code、ext6，创建后不可修改',
    `field_code`  VARCHAR(64) NOT NULL DEFAULT '' COMMENT '字段标识（前端/DTO 使用），创建后不可修改',
    `column_type` VARCHAR(32)  NOT NULL COMMENT '字段类型（数据库字段类型），如 VARCHAR(255)，创建后不可修改',
    `field_name`  VARCHAR(64)  NOT NULL COMMENT '字段名称，如"组织编码"，可编辑',
    `status`      INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除，当前不使用）',
    `create_by`   VARCHAR(64)  NULL COMMENT '创建人',
    `create_time` DATETIME     NULL COMMENT '创建时间',
    `update_by`   VARCHAR(64)  NULL COMMENT '更新人',
    `update_time` DATETIME     NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_metadata_field_table_column` (`table_name`, `column_name`),
    UNIQUE KEY `uk_tab_metadata_field_biz_field_code` (`biz_type`, `field_code`),
    KEY `idx_tab_metadata_field_biz_type` (`biz_type`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '元数据字段配置目录表';

-- 覆盖组织（ORG）、人员（USER）、任职（POSITION）、应用（APP）四类业务对象各自
-- "可开放配置的原有列"与全部 ext1~ext10 扩展列。已有专用交互控件的字段（parentId、
-- orgId、userId、positionType、ownerId、status 等）不出现在此目录中，继续保持
-- 硬编码渲染；USER 的 gender 已改造为普通字典下拉字段，出现在此目录中。

-- ---- 组织（ORG，对应 tab_org） ----
INSERT INTO `tab_metadata_field`
    (`biz_type`, `table_name`, `column_name`, `field_code`, `column_type`, `field_name`, `status`, `create_by`, `create_time`,
     `update_by`, `update_time`)
VALUES ('ORG', 'tab_org', 'name', 'name', 'VARCHAR(64)', '组织名称', 2000, 'admin', NOW(), 'admin', NOW()),
       ('ORG', 'tab_org', 'code', 'code', 'VARCHAR(64)', '组织编码', 2000, 'admin', NOW(), 'admin', NOW()),
       ('ORG', 'tab_org', 'show_order', 'showOrder', 'INT', '显示序号', 2000, 'admin', NOW(), 'admin', NOW()),
       ('ORG', 'tab_org', 'remark', 'remark', 'VARCHAR(255)', '备注', 2000, 'admin', NOW(), 'admin', NOW()),
       ('ORG', 'tab_org', 'ext1', 'ext1', 'VARCHAR(255)', '扩展字段 1', 2000, 'admin', NOW(), 'admin', NOW()),
       ('ORG', 'tab_org', 'ext2', 'ext2', 'VARCHAR(255)', '扩展字段 2', 2000, 'admin', NOW(), 'admin', NOW()),
       ('ORG', 'tab_org', 'ext3', 'ext3', 'VARCHAR(255)', '扩展字段 3', 2000, 'admin', NOW(), 'admin', NOW()),
       ('ORG', 'tab_org', 'ext4', 'ext4', 'VARCHAR(255)', '扩展字段 4', 2000, 'admin', NOW(), 'admin', NOW()),
       ('ORG', 'tab_org', 'ext5', 'ext5', 'VARCHAR(255)', '扩展字段 5', 2000, 'admin', NOW(), 'admin', NOW()),
       ('ORG', 'tab_org', 'ext6', 'ext6', 'VARCHAR(255)', '扩展字段 6', 2000, 'admin', NOW(), 'admin', NOW()),
       ('ORG', 'tab_org', 'ext7', 'ext7', 'VARCHAR(255)', '扩展字段 7', 2000, 'admin', NOW(), 'admin', NOW()),
       ('ORG', 'tab_org', 'ext8', 'ext8', 'VARCHAR(255)', '扩展字段 8', 2000, 'admin', NOW(), 'admin', NOW()),
       ('ORG', 'tab_org', 'ext9', 'ext9', 'VARCHAR(255)', '扩展字段 9', 2000, 'admin', NOW(), 'admin', NOW()),
       ('ORG', 'tab_org', 'ext10', 'ext10', 'VARCHAR(255)', '扩展字段 10', 2000, 'admin', NOW(), 'admin', NOW());

-- ---- 人员（USER，对应 tab_user） ----
INSERT INTO `tab_metadata_field`
    (`biz_type`, `table_name`, `column_name`, `field_code`, `column_type`, `field_name`, `status`, `create_by`, `create_time`,
     `update_by`, `update_time`)
VALUES ('USER', 'tab_user', 'name', 'name', 'VARCHAR(64)', '用户姓名', 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', 'tab_user', 'code', 'code', 'VARCHAR(64)', '用户编号', 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', 'tab_user', 'mobile', 'mobile', 'VARCHAR(20)', '手机号', 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', 'tab_user', 'id_card', 'idCard', 'VARCHAR(18)', '身份证号', 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', 'tab_user', 'gender', 'gender', 'VARCHAR(64)', '性别', 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', 'tab_user', 'show_order', 'showOrder', 'INT', '显示序号', 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', 'tab_user', 'remark', 'remark', 'VARCHAR(255)', '备注', 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', 'tab_user', 'ext1', 'ext1', 'VARCHAR(255)', '扩展字段 1', 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', 'tab_user', 'ext2', 'ext2', 'VARCHAR(255)', '扩展字段 2', 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', 'tab_user', 'ext3', 'ext3', 'VARCHAR(255)', '扩展字段 3', 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', 'tab_user', 'ext4', 'ext4', 'VARCHAR(255)', '扩展字段 4', 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', 'tab_user', 'ext5', 'ext5', 'VARCHAR(255)', '扩展字段 5', 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', 'tab_user', 'ext6', 'ext6', 'VARCHAR(255)', '扩展字段 6', 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', 'tab_user', 'ext7', 'ext7', 'VARCHAR(255)', '扩展字段 7', 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', 'tab_user', 'ext8', 'ext8', 'VARCHAR(255)', '扩展字段 8', 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', 'tab_user', 'ext9', 'ext9', 'VARCHAR(255)', '扩展字段 9', 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', 'tab_user', 'ext10', 'ext10', 'VARCHAR(255)', '扩展字段 10', 2000, 'admin', NOW(), 'admin', NOW());

-- ---- 任职（POSITION，对应 tab_user_position） ----
INSERT INTO `tab_metadata_field`
    (`biz_type`, `table_name`, `column_name`, `field_code`, `column_type`, `field_name`, `status`, `create_by`, `create_time`,
     `update_by`, `update_time`)
VALUES ('POSITION', 'tab_user_position', 'position_type', 'positionType', 'VARCHAR(64)', '任职类型', 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', 'tab_user_position', 'position_address', 'positionAddress', 'VARCHAR(255)', '任职地址', 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', 'tab_user_position', 'position_phone', 'positionPhone', 'VARCHAR(20)', '任职电话', 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', 'tab_user_position', 'show_order', 'showOrder', 'INT', '显示序号', 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', 'tab_user_position', 'remark', 'remark', 'VARCHAR(255)', '备注', 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', 'tab_user_position', 'ext1', 'ext1', 'VARCHAR(255)', '扩展字段 1', 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', 'tab_user_position', 'ext2', 'ext2', 'VARCHAR(255)', '扩展字段 2', 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', 'tab_user_position', 'ext3', 'ext3', 'VARCHAR(255)', '扩展字段 3', 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', 'tab_user_position', 'ext4', 'ext4', 'VARCHAR(255)', '扩展字段 4', 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', 'tab_user_position', 'ext5', 'ext5', 'VARCHAR(255)', '扩展字段 5', 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', 'tab_user_position', 'ext6', 'ext6', 'VARCHAR(255)', '扩展字段 6', 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', 'tab_user_position', 'ext7', 'ext7', 'VARCHAR(255)', '扩展字段 7', 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', 'tab_user_position', 'ext8', 'ext8', 'VARCHAR(255)', '扩展字段 8', 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', 'tab_user_position', 'ext9', 'ext9', 'VARCHAR(255)', '扩展字段 9', 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', 'tab_user_position', 'ext10', 'ext10', 'VARCHAR(255)', '扩展字段 10', 2000, 'admin', NOW(), 'admin', NOW());

-- ---- 应用（APP，对应 tab_app） ----
INSERT INTO `tab_metadata_field`
    (`biz_type`, `table_name`, `column_name`, `field_code`, `column_type`, `field_name`, `status`, `create_by`, `create_time`,
     `update_by`, `update_time`)
VALUES ('APP', 'tab_app', 'name', 'name', 'VARCHAR(64)', '应用名称', 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP', 'tab_app', 'code', 'code', 'VARCHAR(64)', '应用编码', 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP', 'tab_app', 'show_order', 'showOrder', 'INT', '显示序号', 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP', 'tab_app', 'remark', 'remark', 'VARCHAR(255)', '备注', 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP', 'tab_app', 'ext1', 'ext1', 'VARCHAR(255)', '扩展字段 1', 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP', 'tab_app', 'ext2', 'ext2', 'VARCHAR(255)', '扩展字段 2', 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP', 'tab_app', 'ext3', 'ext3', 'VARCHAR(255)', '扩展字段 3', 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP', 'tab_app', 'ext4', 'ext4', 'VARCHAR(255)', '扩展字段 4', 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP', 'tab_app', 'ext5', 'ext5', 'VARCHAR(255)', '扩展字段 5', 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP', 'tab_app', 'ext6', 'ext6', 'VARCHAR(255)', '扩展字段 6', 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP', 'tab_app', 'ext7', 'ext7', 'VARCHAR(255)', '扩展字段 7', 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP', 'tab_app', 'ext8', 'ext8', 'VARCHAR(255)', '扩展字段 8', 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP', 'tab_app', 'ext9', 'ext9', 'VARCHAR(255)', '扩展字段 9', 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP', 'tab_app', 'ext10', 'ext10', 'VARCHAR(255)', '扩展字段 10', 2000, 'admin', NOW(), 'admin', NOW());

-- ============================================================================
-- 表单字段定义模块
-- ============================================================================

CREATE TABLE IF NOT EXISTS `tab_form_field_definition`
(
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `biz_type`          VARCHAR(20)  NOT NULL COMMENT '业务对象类型，创建时取自所绑定元数据字段，之后不可变',
    `metadata_field_id` BIGINT       NOT NULL COMMENT '绑定的元数据字段 id，关联 tab_metadata_field.id，创建后不可改绑',
    `field_name`        VARCHAR(64)  NOT NULL COMMENT '展示名称，创建时默认取自元数据字段的 field_name，此后可独立编辑',
    `field_code`        VARCHAR(64)  NOT NULL COMMENT '前端/DTO 使用的字段标识，如 idCardNo，同一 biz_type 下唯一',
    `control_type`      INT          NOT NULL COMMENT '控件类型：1=文本框，2=数字框，3=字典下拉，4=日期，5=多选字典下拉',
    `dict_type_code`    VARCHAR(64)  NULL COMMENT '关联的字典类型编码，关联 tab_dict_type.code，仅 control_type=3/5 时必填',
    `is_unique`         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否要求同 biz_type 下有效数据唯一',
    `is_required`       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否必填',
    `show_in_list`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否在列表中展示',
    `show_in_create`    TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否在新增表单中展示',
    `show_in_edit`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否在编辑表单中展示',
    `editable`          TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '表单中展示时是否可编辑，为否则只读展示',
    `validate_regex`    VARCHAR(255) NULL COMMENT '正则校验规则，前后端共用同一个字符串',
    `placeholder`       VARCHAR(128) NULL COMMENT '输入提示文字',
    `show_order`        INT          NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前',
    `status`            INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）',
    `create_by`         VARCHAR(64)  NULL COMMENT '创建人',
    `create_time`       DATETIME     NULL COMMENT '创建时间',
    `update_by`         VARCHAR(64)  NULL COMMENT '更新人',
    `update_time`       DATETIME     NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_form_field_definition_biz_type` (`biz_type`),
    KEY `idx_tab_form_field_definition_metadata_field_id` (`metadata_field_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '表单字段定义表';

-- 为组织/人员/任职/应用四类业务对象各自"可开放配置的原有列"（不含 ext1~ext10）
-- 预置启用状态的字段定义，绑定上面写入的对应元数据字段，使这些页面无需管理员手动
-- 配置即可保持现有展示；USER 的性别字段绑定 gender 字典类型（字典下拉控件）。
-- ext1~ext10 对应的元数据字段默认不预置字段定义，保持"零配置不出现，管理员按需
-- 绑定"的状态。承重字段（组织/用户/应用各自的 name、code）的锁定保护不在此处理，
-- 由 cn.nihility.rbac.formfield.constant.LockedFormFields 白名单在运行时计算。

-- ---- 组织（ORG） ----
INSERT INTO `tab_form_field_definition`
    (`biz_type`, `metadata_field_id`, `field_name`, `field_code`, `control_type`, `dict_type_code`, `is_unique`,
     `is_required`, `show_in_list`, `show_in_create`, `show_in_edit`, `editable`, `validate_regex`, `placeholder`,
     `show_order`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('ORG', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_org' AND `column_name` = 'name'),
        '组织名称', 'name', 1, NULL, 0, 1, 1, 1, 1, 1, NULL, NULL, 1, 2000, 'admin', NOW(), 'admin', NOW()),
       ('ORG', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_org' AND `column_name` = 'code'),
        '组织编码', 'code', 1, NULL, 1, 1, 1, 1, 1, 1, NULL, NULL, 3, 2000, 'admin', NOW(), 'admin', NOW()),
       ('ORG',
        (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_org' AND `column_name` = 'show_order'),
        '显示序号', 'showOrder', 2, NULL, 0, 0, 1, 1, 1, 1, NULL, NULL, 5, 2000, 'admin', NOW(), 'admin', NOW()),
       ('ORG', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_org' AND `column_name` = 'remark'),
        '备注', 'remark', 1, NULL, 0, 0, 1, 1, 1, 1, NULL, NULL, 7, 2000, 'admin', NOW(), 'admin', NOW());

-- ---- 人员（USER） ----
INSERT INTO `tab_form_field_definition`
    (`biz_type`, `metadata_field_id`, `field_name`, `field_code`, `control_type`, `dict_type_code`, `is_unique`,
     `is_required`, `show_in_list`, `show_in_create`, `show_in_edit`, `editable`, `validate_regex`, `placeholder`,
     `show_order`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('USER', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_user' AND `column_name` = 'name'),
        '用户姓名', 'name', 1, NULL, 0, 1, 1, 1, 1, 1, NULL, NULL, 1, 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_user' AND `column_name` = 'code'),
        '用户编号', 'code', 1, NULL, 1, 1, 1, 1, 1, 1, NULL, NULL, 3, 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_user' AND `column_name` = 'mobile'),
        '手机号', 'mobile', 1, NULL, 0, 0, 1, 1, 1, 1, NULL, NULL, 5, 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_user' AND `column_name` = 'id_card'),
        '身份证号', 'idCard', 1, NULL, 1, 0, 1, 1, 1, 1, NULL, NULL, 7, 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_user' AND `column_name` = 'gender'),
        '性别', 'gender', 3, 'gender', 0, 0, 1, 1, 1, 1, NULL, NULL, 7, 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER',
        (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_user' AND `column_name` = 'show_order'),
        '显示序号', 'showOrder', 2, NULL, 0, 0, 1, 1, 1, 1, NULL, NULL, 9, 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_user' AND `column_name` = 'remark'),
        '备注', 'remark', 1, NULL, 0, 0, 1, 1, 1, 1, NULL, NULL, 11, 2000, 'admin', NOW(), 'admin', NOW());

-- ---- 任职（POSITION） ----
INSERT INTO `tab_form_field_definition`
    (`biz_type`, `metadata_field_id`, `field_name`, `field_code`, `control_type`, `dict_type_code`, `is_unique`,
     `is_required`, `show_in_list`, `show_in_create`, `show_in_edit`, `editable`, `validate_regex`, `placeholder`,
     `show_order`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('POSITION', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_user_position' AND `column_name` = 'position_type'),
        '任职类型', 'positionType', 3, 'position_type', 0, 1, 1, 1, 1,
        1, NULL, NULL, 1, 2000, 'admin', NOW(), 'admin', NOW()),
    ('POSITION', (SELECT `id`
                     FROM `tab_metadata_field`
                     WHERE `table_name` = 'tab_user_position'
                       AND `column_name` = 'position_address'), '任职地址', 'positionAddress', 1, NULL, 0, 0, 1, 1, 1,
        1, NULL, NULL, 1, 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', (SELECT `id`
                     FROM `tab_metadata_field`
                     WHERE `table_name` = 'tab_user_position'
                       AND `column_name` = 'position_phone'), '任职电话', 'positionPhone', 1, NULL, 0, 0, 1, 1, 1, 1,
        NULL, NULL, 2, 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', (SELECT `id`
                     FROM `tab_metadata_field`
                     WHERE `table_name` = 'tab_user_position'
                       AND `column_name` = 'show_order'), '显示序号', 'showOrder', 2, NULL, 0, 1, 1, 1, 1, 1, NULL,
        NULL, 3, 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', (SELECT `id`
                     FROM `tab_metadata_field`
                     WHERE `table_name` = 'tab_user_position'
                       AND `column_name` = 'remark'), '备注', 'remark', 1, NULL, 0, 0, 1, 1, 1, 1, NULL, NULL, 4,
        2000, 'admin', NOW(), 'admin', NOW());

-- ---- 应用（APP） ----
INSERT INTO `tab_form_field_definition`
    (`biz_type`, `metadata_field_id`, `field_name`, `field_code`, `control_type`, `dict_type_code`, `is_unique`,
     `is_required`, `show_in_list`, `show_in_create`, `show_in_edit`, `editable`, `validate_regex`, `placeholder`,
     `show_order`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('APP', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_app' AND `column_name` = 'name'),
        '应用名称', 'name', 1, NULL, 0, 1, 1, 1, 1, 1, NULL, NULL, 1, 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_app' AND `column_name` = 'code'),
        '应用编码', 'code', 1, NULL, 1, 1, 1, 1, 1, 1, NULL, NULL, 3, 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP',
        (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_app' AND `column_name` = 'show_order'),
        '显示序号', 'showOrder', 2, NULL, 0, 0, 1, 1, 1, 1, NULL, NULL, 5, 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_app' AND `column_name` = 'remark'),
        '备注', 'remark', 1, NULL, 0, 0, 1, 1, 1, 1, NULL, NULL, 7, 2000, 'admin', NOW(), 'admin', NOW());

-- ============================================================================
-- Excel 导入字段配置模块
-- ============================================================================

CREATE TABLE IF NOT EXISTS `tab_import_field_config`
(
    `id`                        BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `biz_type`                  VARCHAR(20) NOT NULL COMMENT '业务对象类型：ORG/USER/POSITION/APP，创建后不可变',
    `form_field_definition_id`  BIGINT      NULL COMMENT '关联的表单字段定义 id，关联 tab_form_field_definition.id，可空',
    `field_code`                VARCHAR(64) NOT NULL COMMENT '字段标识：关联表单字段定义时取自其 field_code，POSITION/APP/ORG 的固定标识列为 __userCode/__orgCode/__ownerCode/__parentCode',
    `excel_header_name`         VARCHAR(64) NOT NULL COMMENT 'Excel 表头文字，可与表单展示名称不同',
    `is_primary_key`            TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否作为匹配已有记录的主键列之一',
    `is_required`               TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '导入语义下的必填，独立于表单字段定义的必填开关',
    `show_order`                INT         NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前，决定模板表头列顺序',
    `status`                    INT         NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，-1000=已删除（逻辑删除）',
    `create_by`                 VARCHAR(64) NULL COMMENT '创建人',
    `create_time`               DATETIME    NULL COMMENT '创建时间',
    `update_by`                 VARCHAR(64) NULL COMMENT '更新人',
    `update_time`               DATETIME    NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_import_field_config_biz_type` (`biz_type`),
    KEY `idx_tab_import_field_config_form_field_definition_id` (`form_field_definition_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = 'Excel 导入字段配置表';

-- POSITION 的表单字段定义清单里没有、也不应该有 userId/orgId（它们是选择器，不是
-- 展示字段），但 Excel 导入必须能通过人可读的编码定位到具体的人员和组织。这里预置
-- 两条不可删除、不可取消必填的固定导入配置行：__userCode（表头"人员编号"，导入时
-- 匹配 tab_user.code 得到 userId）与 __orgCode（表头"组织编码"，导入时匹配
-- tab_org.code 得到 orgId），form_field_definition_id 为 NULL。保护逻辑不落库，
-- 由 cn.nihility.rbac.excelimport.constant.LockedImportFieldConfigs 白名单在
-- 更新/删除时计算得出并拒绝相应请求。
INSERT INTO `tab_import_field_config`
    (`biz_type`, `form_field_definition_id`, `field_code`, `excel_header_name`, `is_primary_key`, `is_required`,
     `show_order`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('POSITION', NULL, '__userCode', '人员编号', 1, 1, 1, 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', NULL, '__orgCode', '组织编码', 1, 1, 2, 2000, 'admin', NOW(), 'admin', NOW());

-- APP 的 ownerId/orgId 同理是选择器而非展示字段，比照 POSITION 预置两条固定标识列。
INSERT INTO `tab_import_field_config`
    (`biz_type`, `form_field_definition_id`, `field_code`, `excel_header_name`, `is_primary_key`, `is_required`,
     `show_order`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('APP', NULL, '__ownerCode', '负责人编码', 1, 1, 1, 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP', NULL, '__orgCode', '组织编码', 1, 1, 2, 2000, 'admin', NOW(), 'admin', NOW());

-- ORG 的 parentId 同理是选择器（树形选择器）而非展示字段。__parentCode（表头"上级
-- 组织编码"，导入时匹配 tab_org.code 得到 parentId）是必填（is_required=1）：每一
-- 行都必须显式填写上级组织编码，若该组织本身是顶级组织，则填写字面值 "0"，而不是
-- 留空。
INSERT INTO `tab_import_field_config`
    (`biz_type`, `form_field_definition_id`, `field_code`, `excel_header_name`, `is_primary_key`, `is_required`,
     `show_order`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('ORG', NULL, '__parentCode', '上级组织编码', 0, 1, 1, 2000, 'admin', NOW(), 'admin', NOW());
