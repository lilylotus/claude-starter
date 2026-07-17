-- ----------------------------------------------------------------------------
-- 菜单管理模块 - 补充管理员管理页面的菜单/按钮资源种子数据（Flyway 迁移版本 V13）
-- 数据来源：仓库根目录 权限资源.txt 新增的 AdminManagement 分组。
-- 挂在 V11 已插入的 permission 一级分组下，showOrder 低于同分组下已有的
-- RoleManagement:role:view（20）、PermissionManagement:permission:view（10），
-- 使管理员管理排在角色管理、权限点管理之后，与 router/menu.ts 里 permission
-- 分组 children 数组的声明顺序保持一致。
-- ----------------------------------------------------------------------------

SET @permission_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'permission');

-- ---- 页面菜单（resourceType = 1） ----

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('管理员管理', 'AdminManagement:admin:view', @permission_id, 1, 5, '管理员管理页面访问', 2000, 'admin',
        NOW(), 'admin', NOW());

SET @admin_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'AdminManagement:admin:view');

-- ---- 按钮（resourceType = 2） ----

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('新增管理员', 'AdminManagement:admin:add', @admin_id, 2, 60, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('查看管理员详情', 'AdminManagement:admin:detail', @admin_id, 2, 50, NULL, 2000, 'admin', NOW(), 'admin',
        NOW()),
       ('编辑管理员', 'AdminManagement:admin:edit', @admin_id, 2, 40, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('启用管理员', 'AdminManagement:admin:enable', @admin_id, 2, 30, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('停用管理员', 'AdminManagement:admin:disable', @admin_id, 2, 20, NULL, 2000, 'admin', NOW(), 'admin', NOW()),
       ('删除管理员', 'AdminManagement:admin:delete', @admin_id, 2, 10, NULL, 2000, 'admin', NOW(), 'admin', NOW());
