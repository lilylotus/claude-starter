-- ----------------------------------------------------------------------------
-- 菜单管理模块 - 插入"系统管理 -> 元数据配置"菜单及按钮资源种子数据
-- （Flyway 迁移版本 V22），写法参考 V16__seed_dict_detail_menu_resource_data.sql。
-- 该页面只支持编辑（字段名称/状态）与查看详情，不提供新增/删除元数据字段的入口，
-- 因此不预置 add/delete 按钮资源。show_order 均小于既有的"字典管理"(10)，
-- 使新菜单排在系统管理分组末尾。
-- ----------------------------------------------------------------------------

SET @system_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'system');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('元数据配置', 'MetadataFieldManagement:metadataField:view', @system_id, 1, 5, '元数据字段配置页面访问', 2000,
        'admin', NOW(), 'admin', NOW());

SET @metadata_field_id := (SELECT `id`
                            FROM `tab_menu`
                            WHERE `code` = 'MetadataFieldManagement:metadataField:view');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('查看元数据字段详情', 'MetadataFieldManagement:metadataField:detail', @metadata_field_id, 2, 40, NULL, 2000,
        'admin', NOW(), 'admin', NOW()),
       ('编辑元数据字段', 'MetadataFieldManagement:metadataField:edit', @metadata_field_id, 2, 30, NULL, 2000,
        'admin', NOW(), 'admin', NOW()),
       ('启用元数据字段', 'MetadataFieldManagement:metadataField:enable', @metadata_field_id, 2, 20, NULL, 2000,
        'admin', NOW(), 'admin', NOW()),
       ('停用元数据字段', 'MetadataFieldManagement:metadataField:disable', @metadata_field_id, 2, 10, NULL, 2000,
        'admin', NOW(), 'admin', NOW());
