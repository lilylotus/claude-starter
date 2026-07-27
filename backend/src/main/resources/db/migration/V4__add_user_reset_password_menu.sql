-- ----------------------------------------------------------------------------
-- RBAC 权限管理系统 - 数据库迁移脚本 V4
-- 菜单管理（tab_menu）补充"重置密码"按钮资源 UserManagement:user:resetPassword，
-- 挂在既有"用户管理"菜单（UserManagement:user:view）下，resource_type = 2（按钮）。
-- 该资源编码此前只登记在根目录 权限资源.txt 里，未同步进 tab_menu 种子数据，
-- 导致菜单管理页面看不到、也无法在角色管理里把这个按钮分配给角色。
-- show_order 取 15，排在"停用"（20）与"删除"（10）之间，对应前端
-- UserManagementView.vue 列表行操作按钮的实际先后顺序（停用/启用 → 重置密码 → 删除）。
-- ----------------------------------------------------------------------------

SET @user_menu_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'UserManagement:user:view');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('重置密码', 'UserManagement:user:resetPassword', @user_menu_id, 2, 15, NULL, 2000, 'admin', NOW(), 'admin',
        NOW());
