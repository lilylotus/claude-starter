-- ----------------------------------------------------------------------------
-- 菜单管理模块 - 预置全量菜单/按钮资源种子数据（Flyway 迁移版本 V11）
-- 数据来源：仓库根目录 权限资源.txt（三段式编码：模块:资源:操作）。
-- 层级结构：一级分组（对应前端侧边栏 4 个导航分组）-> 页面菜单 -> 按钮，
-- 仅覆盖当前已实现的 8 个管理页面，不包含应用密钥、操作日志这两个尚未实现的占位页面。
-- 父子关系通过会话变量按 code 回填 parentId，不假设自增 id 的起始值。
-- ----------------------------------------------------------------------------

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
       ('编辑字典类型', 'DictManagement:dictType:edit', @dict_id, 2, 90, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('启用字典类型', 'DictManagement:dictType:enable', @dict_id, 2, 80, NULL, 2000, 'admin', NOW(), 'admin',
        NOW()),
       ('停用字典类型', 'DictManagement:dictType:disable', @dict_id, 2, 70, NULL, 2000, 'admin', NOW(), 'admin',
        NOW()),
       ('删除字典类型', 'DictManagement:dictType:delete', @dict_id, 2, 60, NULL, 2000, 'admin', NOW(), 'admin',
        NOW()),
       ('新增字典项', 'DictManagement:dictItem:add', @dict_id, 2, 50, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('编辑字典项', 'DictManagement:dictItem:edit', @dict_id, 2, 40, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('启用字典项', 'DictManagement:dictItem:enable', @dict_id, 2, 30, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('停用字典项', 'DictManagement:dictItem:disable', @dict_id, 2, 20, NULL, 2000, 'admin', NOW(), 'admin',
        NOW()),
       ('删除字典项', 'DictManagement:dictItem:delete', @dict_id, 2, 10, NULL, 2000, 'admin', NOW(), 'admin', NOW());
