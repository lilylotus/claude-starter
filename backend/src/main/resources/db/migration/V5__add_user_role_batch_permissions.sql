-- 新增两个权限点及其菜单/按钮资源目录条目：角色管理"批量添加用户角色"、管理员管理
-- "按角色批量设置管理员"（add-user-role-batch-assignment change tasks.md 第 6 节）。
-- 授权判断只读 tab_permission（AuthorizationServiceImpl 经 tab_role_permission 关联），
-- tab_menu 是角色管理"权限点选择树"渲染用的资源目录，两张表需要同步新增同一个 code，
-- 与 V1 里其余按钮权限点的落库方式保持一致。

SET @role_menu_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'RoleManagement:role:view');
SET @admin_menu_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'AdminManagement:admin:view');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('批量添加用户角色', 'RoleManagement:role:batchAssignUser', @role_menu_id, 2, 5, NULL, 2000, 'system', NOW(), 'system', NOW()),
       ('按角色批量设置管理员', 'AdminManagement:admin:batchPromoteByRole', @admin_menu_id, 2, 5, NULL, 2000, 'system', NOW(), 'system', NOW());

INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('批量添加用户角色', 'RoleManagement:role:batchAssignUser', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
       ('按角色批量设置管理员', 'AdminManagement:admin:batchPromoteByRole', 0, NULL, 2000, 'system', NOW(), 'system', NOW());

-- 存量已初始化过的库里，超级管理员角色早已建好且不再重复执行 V1 那条"关联全部权限点"的
-- INSERT...SELECT；这里对新增的两个权限点单独补授，保持"超级管理员拥有全部权限点"的初始化
-- 语义不因迁移脚本拆分而失效。
INSERT INTO `tab_role_permission` (`role_id`, `permission_id`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT r.`id`, p.`id`, 'system', NOW(), 'system', NOW()
FROM `tab_role` r
         JOIN `tab_permission` p ON p.`code` IN ('RoleManagement:role:batchAssignUser', 'AdminManagement:admin:batchPromoteByRole')
WHERE r.`code` = 'SUPER_ADMIN';
