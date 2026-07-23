-- ----------------------------------------------------------------------------
-- 字典管理模块 - 预置性别字典数据（Flyway 迁移版本 V31）
-- 供用户管理模块的"性别"字段直接消费，替代此前的 Java 常量类硬编码。
-- ----------------------------------------------------------------------------

INSERT INTO `tab_dict_type` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('性别', 'gender', 0, '用户管理模块性别字段的数据来源', 2000, 'admin', NOW(), 'admin', NOW());

INSERT INTO `tab_dict_item` (`dict_type_id`, `label`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT `id`, '未知', 'unknown', 1, NULL, 2000, 'admin', NOW(), 'admin', NOW()
FROM `tab_dict_type`
WHERE `code` = 'gender';

INSERT INTO `tab_dict_item` (`dict_type_id`, `label`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT `id`, '男', 'male', 2, NULL, 2000, 'admin', NOW(), 'admin', NOW()
FROM `tab_dict_type`
WHERE `code` = 'gender';

INSERT INTO `tab_dict_item` (`dict_type_id`, `label`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT `id`, '女', 'female', 3, NULL, 2000, 'admin', NOW(), 'admin', NOW()
FROM `tab_dict_type`
WHERE `code` = 'gender';
