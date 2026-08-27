-- ----------------------------------------------------------------------------
-- plugin-jar-upgrade change：新增插件管理页面访问权限点/菜单，供
-- GET /api/v1/plugins 接口的 menu 请求头权限校验使用（IdentityAuthFilter +
-- AuthorizationService，运行时按 tab_permission 中登记的权限编码判断）。
-- 挂在既有"系统管理"一级导航分组（tab_menu.code = 'system'）下，权限编码
-- PluginManagement:plugin:view，只读页面（只支持插件状态查询），没有 add/edit/
-- enable/disable/delete。
-- ----------------------------------------------------------------------------

SET @admin_user_id_text := '1';
SET @system_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'system');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('插件管理', 'PluginManagement:plugin:view', @system_id, 1, 0, '插件（Bean 定义注册阶段）状态查询页面访问', 2000,
        @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`,
                               `update_time`)
VALUES ('插件管理页面访问', 'PluginManagement:plugin:view', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- 超级管理员角色补授本次新增权限点（初始化脚本 V1 里的 SUPER_ADMIN 授权在本脚本执行前
-- 已完成，这里需要单独补一条，否则超级管理员账号也看不到插件管理菜单）。
SET @super_admin_role_id := (SELECT `id` FROM `tab_role` WHERE `code` = 'SUPER_ADMIN');
SET @plugin_permission_id := (SELECT `id` FROM `tab_permission` WHERE `code` = 'PluginManagement:plugin:view');

INSERT INTO `tab_role_permission` (`role_id`, `permission_id`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT @super_admin_role_id, @plugin_permission_id, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
WHERE @super_admin_role_id IS NOT NULL AND @plugin_permission_id IS NOT NULL;
