-- ----------------------------------------------------------------------------
-- 菜单管理模块 - 插入"系统管理 -> 表单管理"菜单及按钮资源种子数据
-- （Flyway 迁移版本 V23），写法参考 V16__seed_dict_detail_menu_resource_data.sql。
-- 表单字段定义支持完整的新增/编辑/启停用/删除/详情能力，按钮集合与组织/用户等
-- 常规管理页面一致。show_order 低于 V22 插入的"元数据配置"(5)，排在系统管理
-- 分组最末尾。
-- ----------------------------------------------------------------------------

SET @system_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'system');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('表单管理', 'FormFieldManagement:formField:view', @system_id, 1, 1, '表单字段定义管理页面访问', 2000, 'admin',
        NOW(), 'admin', NOW());

SET @form_field_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'FormFieldManagement:formField:view');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('新增表单字段', 'FormFieldManagement:formField:add', @form_field_id, 2, 60, NULL, 2000, 'admin', NOW(),
        'admin', NOW()),
       ('查看表单字段详情', 'FormFieldManagement:formField:detail', @form_field_id, 2, 50, NULL, 2000, 'admin',
        NOW(), 'admin', NOW()),
       ('编辑表单字段', 'FormFieldManagement:formField:edit', @form_field_id, 2, 40, NULL, 2000, 'admin', NOW(),
        'admin', NOW()),
       ('启用表单字段', 'FormFieldManagement:formField:enable', @form_field_id, 2, 30, NULL, 2000, 'admin', NOW(),
        'admin', NOW()),
       ('停用表单字段', 'FormFieldManagement:formField:disable', @form_field_id, 2, 20, NULL, 2000, 'admin', NOW(),
        'admin', NOW()),
       ('删除表单字段', 'FormFieldManagement:formField:delete', @form_field_id, 2, 10, NULL, 2000, 'admin', NOW(),
        'admin', NOW());
