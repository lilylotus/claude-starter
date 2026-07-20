
-- ----------------------------------------------------------------------------
-- 菜单管理模块 - 补充操作日志管理页面的菜单资源种子数据（Flyway 迁移版本 V15）
-- 数据来源：仓库根目录 权限资源.txt 新增的 OperationLogManagement 分组。
-- 挂在 V11 已插入的 system 一级分组下，showOrder 低于同分组下已有的
-- MenuManagement:menu:view（20）、DictManagement:dictType:view（10），
-- 使操作日志管理排在菜单管理、字典管理之后，与 router/menu.ts 里 system
-- 分组 children 数组的声明顺序保持一致。操作日志页面只读，没有对应的按钮资源。
-- ----------------------------------------------------------------------------

SET @system_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'system');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('操作日志', 'OperationLogManagement:log:view', @system_id, 1, 5, '操作日志管理页面访问', 2000, 'admin',
        NOW(), 'admin', NOW());
