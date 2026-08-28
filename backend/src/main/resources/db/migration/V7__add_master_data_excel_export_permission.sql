-- ----------------------------------------------------------------------------
-- add-master-data-excel-export change：新增组织/用户/任职/应用四个管理页面的
-- "导出Excel"按钮级权限点，供 GET /api/excel-export/download?bizType= 接口的
-- menu 请求头权限校验使用，登记方式与既有 xxx:import/xxx:importTemplate 权限点
-- 保持一致（tab_menu 按钮资源 + tab_permission 权限点两张表都要登记）。
-- ----------------------------------------------------------------------------

SET @admin_user_id_text := '1';
SET @org_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'OrgManagement:org:view');
SET @user_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'UserManagement:user:view');
SET @position_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'PositionManagement:position:view');
SET @app_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'AppManagement:app:view');

-- ---- 第三层：按钮（resourceType = 2 按钮） ----
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('导出组织', 'OrgManagement:org:export', @org_id, 2, 6, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('导出用户', 'UserManagement:user:export', @user_id, 2, 6, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
       ('导出任职记录', 'PositionManagement:position:export', @position_id, 2, 6, NULL, 2000, @admin_user_id_text, NOW(),
        @admin_user_id_text, NOW()),
       ('导出应用', 'AppManagement:app:export', @app_id, 2, 6, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- ---- 权限点种子数据 ----
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    ('导出组织', 'OrgManagement:org:export', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('导出用户', 'UserManagement:user:export', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('导出任职记录', 'PositionManagement:position:export', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()),
    ('导出应用', 'AppManagement:app:export', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW());

-- 超级管理员角色补授本次新增权限点（初始化脚本 V1 里的 SUPER_ADMIN 授权在本脚本执行前
-- 已完成，这里需要单独补一条，否则超级管理员账号也看不到导出按钮）。
SET @super_admin_role_id := (SELECT `id` FROM `tab_role` WHERE `code` = 'SUPER_ADMIN');

INSERT INTO `tab_role_permission` (`role_id`, `permission_id`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT @super_admin_role_id, `id`, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
FROM `tab_permission`
WHERE `code` IN ('OrgManagement:org:export', 'UserManagement:user:export', 'PositionManagement:position:export',
                 'AppManagement:app:export')
  AND @super_admin_role_id IS NOT NULL;
