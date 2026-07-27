-- ----------------------------------------------------------------------------
-- RBAC 权限管理系统 - 数据库迁移脚本 V7
-- 核对 V1__init_schema.sql 给 tab_menu 灌种子数据时发现：组织管理
-- （OrgManagement:org:view）、用户管理（UserManagement:user:view）、任职管理
-- （PositionManagement:position:view）、应用管理（AppManagement:app:view）四个
-- 页面节点下，只灌了 add/detail/edit/enable/disable/delete 六个按钮子节点，
-- 唯独漏了 importTemplate（下载导入模板）、import（批量导入）两个——这两个编码
-- 在 权限资源.txt 和 tab_permission（V6 迁移）里都已经登记齐全，前端四个页面的
-- 导入按钮功能也已经实现能跑，纯粹是 tab_menu 这份档案性质的资源目录种子数据
-- 当初漏了。本迁移只做增量 INSERT，补齐这 8 条记录，不改表结构、不影响其他数据。
-- ----------------------------------------------------------------------------

-- 组织管理：挂在 OrgManagement:org:view 页面节点下，排在已有增删改查按钮（show_order
-- 10~60）之后，即 show_order 取更小的值
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
SELECT '下载组织导入模板', 'OrgManagement:org:importTemplate', `id`, 2, 5, NULL, 2000, 'admin', NOW(), 'admin', NOW()
FROM `tab_menu`
WHERE `code` = 'OrgManagement:org:view';

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
SELECT '批量导入组织', 'OrgManagement:org:import', `id`, 2, 0, NULL, 2000, 'admin', NOW(), 'admin', NOW()
FROM `tab_menu`
WHERE `code` = 'OrgManagement:org:view';

-- 用户管理：挂在 UserManagement:user:view 页面节点下
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
SELECT '下载用户导入模板', 'UserManagement:user:importTemplate', `id`, 2, 5, NULL, 2000, 'admin', NOW(), 'admin', NOW()
FROM `tab_menu`
WHERE `code` = 'UserManagement:user:view';

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
SELECT '批量导入用户', 'UserManagement:user:import', `id`, 2, 0, NULL, 2000, 'admin', NOW(), 'admin', NOW()
FROM `tab_menu`
WHERE `code` = 'UserManagement:user:view';

-- 任职管理：挂在 PositionManagement:position:view 页面节点下
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
SELECT '下载任职导入模板', 'PositionManagement:position:importTemplate', `id`, 2, 5, NULL, 2000, 'admin', NOW(),
       'admin', NOW()
FROM `tab_menu`
WHERE `code` = 'PositionManagement:position:view';

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
SELECT '批量导入任职记录', 'PositionManagement:position:import', `id`, 2, 0, NULL, 2000, 'admin', NOW(), 'admin', NOW()
FROM `tab_menu`
WHERE `code` = 'PositionManagement:position:view';

-- 应用管理：挂在 AppManagement:app:view 页面节点下
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
SELECT '下载应用导入模板', 'AppManagement:app:importTemplate', `id`, 2, 5, NULL, 2000, 'admin', NOW(), 'admin', NOW()
FROM `tab_menu`
WHERE `code` = 'AppManagement:app:view';

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
SELECT '批量导入应用', 'AppManagement:app:import', `id`, 2, 0, NULL, 2000, 'admin', NOW(), 'admin', NOW()
FROM `tab_menu`
WHERE `code` = 'AppManagement:app:view';
