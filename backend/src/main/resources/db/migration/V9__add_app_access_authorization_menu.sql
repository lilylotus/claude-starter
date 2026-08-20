-- ----------------------------------------------------------------------------
-- 应用访问授权模块菜单与权限点种子数据（app-access-authorization change）
-- ----------------------------------------------------------------------------
-- V8 只创建了业务表，没有补齐 tab_menu/tab_permission/tab_role_permission 种子数据，
-- 导致默认超级管理员账号看不到"应用访问授权"菜单（超级管理员角色的权限点集合是
-- V1 迁移时一次性 INSERT...SELECT 全量 tab_permission 生成的，之后新增的权限点不会
-- 自动补授权，需要显式补一条 tab_role_permission）。本脚本补齐：
-- 1 个页面级菜单节点（挂在"权限管理"一级分组下） + 8 个按钮级资源，
-- 对应的 9 条 tab_permission 记录，以及授予 SUPER_ADMIN 角色。

SET @admin_user_id_text := '1';

-- 页面级菜单节点，挂在"权限管理"一级分组（code='permission'）下，show_order 小于
-- 管理员管理（5），排在权限管理分组现有子菜单的最后。
SET @permission_group_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'permission');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('应用访问授权', 'AppAccessManagement:appAccess:view', @permission_group_id, 1, 1,
        '应用访问授权页面访问（含最终生效权限查询板块的只读查询）', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

SET @app_access_menu_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'AppAccessManagement:appAccess:view');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('新增策略规则', 'AppAccessManagement:policy:add', @app_access_menu_id, 2, 80, NULL, 2000, @admin_user_id_text,
        NOW(), @admin_user_id_text, NOW()),
       ('编辑策略规则', 'AppAccessManagement:policy:edit', @app_access_menu_id, 2, 70, NULL, 2000, @admin_user_id_text,
        NOW(), @admin_user_id_text, NOW()),
       ('启用策略规则', 'AppAccessManagement:policy:enable', @app_access_menu_id, 2, 60, NULL, 2000, @admin_user_id_text,
        NOW(), @admin_user_id_text, NOW()),
       ('停用策略规则', 'AppAccessManagement:policy:disable', @app_access_menu_id, 2, 50, NULL, 2000, @admin_user_id_text,
        NOW(), @admin_user_id_text, NOW()),
       ('执行策略规则', 'AppAccessManagement:policy:execute', @app_access_menu_id, 2, 40, NULL, 2000, @admin_user_id_text,
        NOW(), @admin_user_id_text, NOW()),
       ('删除策略规则', 'AppAccessManagement:policy:delete', @app_access_menu_id, 2, 30, NULL, 2000, @admin_user_id_text,
        NOW(), @admin_user_id_text, NOW()),
       ('新增/编辑人工例外', 'AppAccessManagement:override:add', @app_access_menu_id, 2, 20, NULL, 2000,
        @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('删除人工例外', 'AppAccessManagement:override:delete', @app_access_menu_id, 2, 10, NULL, 2000,
        @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- 对应的权限点记录（管理端"权限点管理"页面展示、角色授权时选用的是这张表，不是
-- tab_menu；两表用相同的 code 字符串关联，风格与 V1 一致）。
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`,
                               `update_by`, `update_time`)
VALUES ('应用访问授权页面访问', 'AppAccessManagement:appAccess:view', 0, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('新增策略规则', 'AppAccessManagement:policy:add', 0, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('编辑策略规则', 'AppAccessManagement:policy:edit', 0, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('启用策略规则', 'AppAccessManagement:policy:enable', 0, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('停用策略规则', 'AppAccessManagement:policy:disable', 0, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('执行策略规则', 'AppAccessManagement:policy:execute', 0, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('删除策略规则', 'AppAccessManagement:policy:delete', 0, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('新增/编辑人工例外', 'AppAccessManagement:override:add', 0, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('删除人工例外', 'AppAccessManagement:override:delete', 0, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW());

-- 授予超级管理员角色（SUPER_ADMIN，V1 迁移时创建）本次新增的全部权限点。
SET @super_admin_role_id := (SELECT `id` FROM `tab_role` WHERE `code` = 'SUPER_ADMIN');

INSERT INTO `tab_role_permission` (`role_id`, `permission_id`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT @super_admin_role_id, `id`, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
FROM `tab_permission`
WHERE `code` IN (
    'AppAccessManagement:appAccess:view',
    'AppAccessManagement:policy:add',
    'AppAccessManagement:policy:edit',
    'AppAccessManagement:policy:enable',
    'AppAccessManagement:policy:disable',
    'AppAccessManagement:policy:execute',
    'AppAccessManagement:policy:delete',
    'AppAccessManagement:override:add',
    'AppAccessManagement:override:delete'
);
