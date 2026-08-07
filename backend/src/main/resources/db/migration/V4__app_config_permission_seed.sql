-- ----------------------------------------------------------------------------
-- app-api-credentials-config change：为新增的"应用接口配置"能力补充 4 个权限点
-- 编码的种子数据，风格与 V1__init_schema.sql 里 AppManagement:app:* 现有记录保持
-- 一致：tab_menu 追加按钮级资源（挂在 AppManagement:app:view 菜单节点下）、
-- tab_permission 追加对应权限点、并把新增权限点授予"超级管理员"角色（沿用 V1
-- 里"超级管理员关联全部种子权限点"的既有约定，逐条追加而非重新执行全量授权）。
-- ----------------------------------------------------------------------------

-- ---- tab_menu：应用管理页面下新增"配置"按钮（resourceType = 2 按钮） ----
SET @app_menu_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'AppManagement:app:view');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('应用接口配置', 'AppManagement:app:config', @app_menu_id, 2, 4, '应用接口配置页面访问', 2000, 'system', NOW(),
        'system', NOW()),
       ('重置 SecretKey', 'AppManagement:app:config:resetSecret', @app_menu_id, 2, 3, NULL, 2000, 'system', NOW(),
        'system', NOW()),
       ('修改签名算法', 'AppManagement:app:config:editSignAlgorithm', @app_menu_id, 2, 2, NULL, 2000, 'system', NOW(),
        'system', NOW()),
       ('修改同步配置', 'AppManagement:app:config:editSync', @app_menu_id, 2, 1, NULL, 2000, 'system', NOW(),
        'system', NOW());

-- ---- tab_permission：应用接口配置相关的 4 个权限点 ----
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    ('应用接口配置页面访问', 'AppManagement:app:config', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('重置 SecretKey', 'AppManagement:app:config:resetSecret', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('修改签名算法', 'AppManagement:app:config:editSignAlgorithm', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
    ('修改同步配置', 'AppManagement:app:config:editSync', 0, NULL, 2000, 'system', NOW(), 'system', NOW());

-- ---- 超级管理员角色追加关联上面新增的 4 个权限点 ----
SET @super_admin_role_id := (SELECT `id` FROM `tab_role` WHERE `code` = 'SUPER_ADMIN');

INSERT INTO `tab_role_permission` (`role_id`, `permission_id`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT @super_admin_role_id, `id`, 'system', NOW(), 'system', NOW()
FROM `tab_permission`
WHERE `code` IN ('AppManagement:app:config', 'AppManagement:app:config:resetSecret',
                  'AppManagement:app:config:editSignAlgorithm', 'AppManagement:app:config:editSync');
