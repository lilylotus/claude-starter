-- ----------------------------------------------------------------------------
-- 表单字段定义模块 - 写入默认表单字段定义种子数据（Flyway 迁移版本 V21）
-- 为组织/人员/任职/应用四类业务对象各自"可开放配置的原有列"（不含
-- ext1~ext10）预置启用状态的字段定义，绑定 V19 写入的对应元数据字段，使这些
-- 页面无需管理员手动配置即可保持现有展示。ext1~ext10 对应的元数据字段默认
-- 不预置字段定义，保持"零配置不出现，管理员按需绑定"的状态。
-- 承重字段（组织/用户/应用各自的 name、code）的锁定保护不在此处理，由
-- cn.nihility.rbac.formfield.constant.LockedFormFields 白名单在运行时计算。
-- ----------------------------------------------------------------------------

-- ---- 组织（ORG） ----
INSERT INTO `tab_form_field_definition`
    (`biz_type`, `metadata_field_id`, `field_name`, `field_code`, `control_type`, `dict_type_id`, `is_unique`,
     `is_required`, `show_in_list`, `show_in_create`, `show_in_edit`, `editable`, `validate_regex`, `placeholder`,
     `show_order`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('ORG', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_org' AND `column_name` = 'name'),
        '组织名称', 'name', 1, NULL, 0, 1, 1, 1, 1, 1, NULL, NULL, 100, 2000, 'admin', NOW(), 'admin', NOW()),
       ('ORG', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_org' AND `column_name` = 'code'),
        '组织编码', 'code', 1, NULL, 1, 1, 1, 1, 1, 1, NULL, NULL, 90, 2000, 'admin', NOW(), 'admin', NOW()),
       ('ORG',
        (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_org' AND `column_name` = 'show_order'),
        '显示序号', 'showOrder', 2, NULL, 0, 1, 1, 1, 1, 1, NULL, NULL, 20, 2000, 'admin', NOW(), 'admin', NOW()),
       ('ORG', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_org' AND `column_name` = 'remark'),
        '备注', 'remark', 1, NULL, 0, 0, 1, 1, 1, 1, NULL, NULL, 10, 2000, 'admin', NOW(), 'admin', NOW());

-- ---- 人员（USER） ----
INSERT INTO `tab_form_field_definition`
    (`biz_type`, `metadata_field_id`, `field_name`, `field_code`, `control_type`, `dict_type_id`, `is_unique`,
     `is_required`, `show_in_list`, `show_in_create`, `show_in_edit`, `editable`, `validate_regex`, `placeholder`,
     `show_order`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('USER', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_user' AND `column_name` = 'name'),
        '用户姓名', 'name', 1, NULL, 0, 1, 1, 1, 1, 1, NULL, NULL, 100, 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_user' AND `column_name` = 'code'),
        '用户编号', 'code', 1, NULL, 1, 1, 1, 1, 1, 1, NULL, NULL, 90, 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_user' AND `column_name` = 'mobile'),
        '手机号', 'mobile', 1, NULL, 0, 0, 1, 1, 1, 1, NULL, NULL, 70, 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_user' AND `column_name` = 'id_card'),
        '身份证号', 'idCard', 1, NULL, 1, 0, 1, 1, 1, 1, NULL, NULL, 60, 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER',
        (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_user' AND `column_name` = 'show_order'),
        '显示序号', 'showOrder', 2, NULL, 0, 1, 1, 1, 1, 1, NULL, NULL, 20, 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_user' AND `column_name` = 'remark'),
        '备注', 'remark', 1, NULL, 0, 0, 1, 1, 1, 1, NULL, NULL, 10, 2000, 'admin', NOW(), 'admin', NOW());

-- ---- 任职（POSITION） ----
INSERT INTO `tab_form_field_definition`
    (`biz_type`, `metadata_field_id`, `field_name`, `field_code`, `control_type`, `dict_type_id`, `is_unique`,
     `is_required`, `show_in_list`, `show_in_create`, `show_in_edit`, `editable`, `validate_regex`, `placeholder`,
     `show_order`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('POSITION', (SELECT `id`
                     FROM `tab_metadata_field`
                     WHERE `table_name` = 'tab_user_position'
                       AND `column_name` = 'position_address'), '任职地址', 'positionAddress', 1, NULL, 0, 0, 1, 1, 1,
        1, NULL, NULL, 100, 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', (SELECT `id`
                     FROM `tab_metadata_field`
                     WHERE `table_name` = 'tab_user_position'
                       AND `column_name` = 'position_phone'), '任职电话', 'positionPhone', 1, NULL, 0, 0, 1, 1, 1, 1,
        NULL, NULL, 90, 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', (SELECT `id`
                     FROM `tab_metadata_field`
                     WHERE `table_name` = 'tab_user_position'
                       AND `column_name` = 'show_order'), '显示序号', 'showOrder', 2, NULL, 0, 1, 1, 1, 1, 1, NULL,
        NULL, 20, 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', (SELECT `id`
                     FROM `tab_metadata_field`
                     WHERE `table_name` = 'tab_user_position'
                       AND `column_name` = 'remark'), '备注', 'remark', 1, NULL, 0, 0, 1, 1, 1, 1, NULL, NULL, 10,
        2000, 'admin', NOW(), 'admin', NOW());

-- ---- 应用（APP） ----
INSERT INTO `tab_form_field_definition`
    (`biz_type`, `metadata_field_id`, `field_name`, `field_code`, `control_type`, `dict_type_id`, `is_unique`,
     `is_required`, `show_in_list`, `show_in_create`, `show_in_edit`, `editable`, `validate_regex`, `placeholder`,
     `show_order`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('APP', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_app' AND `column_name` = 'name'),
        '应用名称', 'name', 1, NULL, 0, 1, 1, 1, 1, 1, NULL, NULL, 100, 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_app' AND `column_name` = 'code'),
        '应用编码', 'code', 1, NULL, 1, 1, 1, 1, 1, 1, NULL, NULL, 90, 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP',
        (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_app' AND `column_name` = 'show_order'),
        '显示序号', 'showOrder', 2, NULL, 0, 1, 1, 1, 1, 1, NULL, NULL, 20, 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP', (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_app' AND `column_name` = 'remark'),
        '备注', 'remark', 1, NULL, 0, 0, 1, 1, 1, 1, NULL, NULL, 10, 2000, 'admin', NOW(), 'admin', NOW());
