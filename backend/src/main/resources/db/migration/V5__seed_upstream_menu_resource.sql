-- ----------------------------------------------------------------------------
-- 补齐"上游数据管理"（identity-upstream-data-sync change）菜单/权限点种子数据。
-- V4 只建了功能本身的 4 张业务表，遗漏了 tab_menu（菜单管理页面树形展示）、
-- tab_permission（权限点管理/角色授权用的扁平清单）、tab_role_permission（把新增
-- 权限点授予超级管理员角色）三处种子数据，导致前端侧边栏不显示该菜单、菜单管理/
-- 权限点管理页面也看不到对应条目。单独开一个新版本号补种子数据，而不是回头改
-- V4——项目虽然还处于开发阶段，但 V4 已经随本次改动的其他部分执行过，直接改写
-- 已执行的迁移脚本会导致 Flyway 校验和不一致。
-- ----------------------------------------------------------------------------

-- ---- tab_menu：与 权限资源.txt 的 UpstreamManagement 清单一一对应 ----

SET @identity_menu_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'identity');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('上游数据管理', 'UpstreamManagement:source:view', @identity_menu_id, 1, 0, '上游数据管理页面访问', 2000,
        'system', NOW(), 'system', NOW());

SET @upstream_menu_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'UpstreamManagement:source:view');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('新增上游数据源', 'UpstreamManagement:source:add', @upstream_menu_id, 2, 80, NULL, 2000, 'system', NOW(),
        'system', NOW()),
       ('编辑上游数据源基础信息', 'UpstreamManagement:source:edit', @upstream_menu_id, 2, 70,
        '编辑名称、同步方式，含独立配置页"基础信息" tab 的保存', 2000, 'system', NOW(), 'system', NOW()),
       ('删除上游数据源', 'UpstreamManagement:source:delete', @upstream_menu_id, 2, 60,
        '级联删除数据域配置、字段映射、同步执行记录', 2000, 'system', NOW(), 'system', NOW()),
       ('启用上游数据源', 'UpstreamManagement:source:enable', @upstream_menu_id, 2, 50, NULL, 2000, 'system', NOW(),
        'system', NOW()),
       ('停用上游数据源', 'UpstreamManagement:source:disable', @upstream_menu_id, 2, 40, NULL, 2000, 'system', NOW(),
        'system', NOW()),
       ('进入数据源配置页', 'UpstreamManagement:source:config', @upstream_menu_id, 2, 30,
        '进入 /identity/upstream/:id/config 独立配置页', 2000, 'system', NOW(), 'system', NOW()),
       ('修改数据源配置', 'UpstreamManagement:source:config:edit', @upstream_menu_id, 2, 20,
        '配置页内连接配置、调度配置、数据范围（数据域启用+取数来源、字段映射）的全部保存动作', 2000, 'system',
        NOW(), 'system', NOW()),
       ('立即同步一次', 'UpstreamManagement:source:manualSync', @upstream_menu_id, 2, 10,
        '手动触发一次同步，不等定时调度到点', 2000, 'system', NOW(), 'system', NOW());

-- ---- tab_permission：角色授权用的扁平权限点清单，与 tab_menu 的 code 一一对应 ----

INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`,
                               `update_by`, `update_time`)
VALUES ('上游数据管理页面访问', 'UpstreamManagement:source:view', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
       ('新增上游数据源', 'UpstreamManagement:source:add', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
       ('编辑上游数据源基础信息', 'UpstreamManagement:source:edit', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
       ('删除上游数据源', 'UpstreamManagement:source:delete', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
       ('启用上游数据源', 'UpstreamManagement:source:enable', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
       ('停用上游数据源', 'UpstreamManagement:source:disable', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
       ('进入数据源配置页', 'UpstreamManagement:source:config', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
       ('修改数据源配置', 'UpstreamManagement:source:config:edit', 0, NULL, 2000, 'system', NOW(), 'system', NOW()),
       ('立即同步一次', 'UpstreamManagement:source:manualSync', 0, NULL, 2000, 'system', NOW(), 'system', NOW());

-- ---- 把新增权限点授予"超级管理员"角色（V1 的授权是一次性 INSERT...SELECT，不会自动
--      覆盖之后新增的权限点，需要在这里显式补一次，与 V1 末尾的授权方式保持一致） ----

SET @super_admin_role_id := (SELECT `id` FROM `tab_role` WHERE `code` = 'SUPER_ADMIN');

INSERT INTO `tab_role_permission` (`role_id`, `permission_id`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT @super_admin_role_id, `id`, 'system', NOW(), 'system', NOW()
FROM `tab_permission`
WHERE `code` LIKE 'UpstreamManagement:%';
