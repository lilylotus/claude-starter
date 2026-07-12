-- ----------------------------------------------------------------------------
-- 字典管理模块 - 预置认证类型字典数据（Flyway 迁移版本 V4）
-- 供后续用户管理模块的"认证类型"字段直接消费，避免该模块另行创建种子数据。
-- ----------------------------------------------------------------------------

INSERT INTO `tab_dict_type` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`,
                              `update_by`, `update_time`)
VALUES ('认证类型', 'position_type', 0, '用户管理模块认证类型字段的数据来源', 2000, 'admin', NOW(), 'admin', NOW());

INSERT INTO `tab_dict_item` (`dict_type_id`, `label`, `code`, `show_order`, `remark`, `status`, `create_by`,
                              `create_time`, `update_by`, `update_time`)
SELECT `id`, '主职', 'primary', 3, NULL, 2000, 'admin', NOW(), 'admin', NOW()
FROM `tab_dict_type`
WHERE `code` = 'position_type';

INSERT INTO `tab_dict_item` (`dict_type_id`, `label`, `code`, `show_order`, `remark`, `status`, `create_by`,
                              `create_time`, `update_by`, `update_time`)
SELECT `id`, '兼职', 'part_time', 2, NULL, 2000, 'admin', NOW(), 'admin', NOW()
FROM `tab_dict_type`
WHERE `code` = 'position_type';

INSERT INTO `tab_dict_item` (`dict_type_id`, `label`, `code`, `show_order`, `remark`, `status`, `create_by`,
                              `create_time`, `update_by`, `update_time`)
SELECT `id`, '挂职', 'temporary', 1, NULL, 2000, 'admin', NOW(), 'admin', NOW()
FROM `tab_dict_type`
WHERE `code` = 'position_type';
