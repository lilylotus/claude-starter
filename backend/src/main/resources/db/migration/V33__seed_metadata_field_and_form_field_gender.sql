-- ----------------------------------------------------------------------------
-- 元数据字段配置模块 / 表单字段定义模块 - 补充人员（USER）性别字段（Flyway 迁移版本 V33）
-- 性别改造为与手机号、身份证号同等地位的普通可配置字段，纳入元数据字段目录，
-- 并预置一条默认启用的表单字段定义（控件类型为字典下拉，绑定 V31 新建的
-- gender 字典类型），使"表单管理 - 导入模板配置"能够选择性别作为导入列。
-- show_order 追加在人员现有字段序列末尾（1~6 已被占用），取 7，不重排既有字段。
-- ----------------------------------------------------------------------------

-- INSERT INTO `tab_metadata_field`
--     (`biz_type`, `table_name`, `column_name`, `field_code`, `column_type`, `field_name`, `status`, `create_by`, `create_time`,
--      `update_by`, `update_time`)
-- VALUES ('USER', 'tab_user', 'gender', 'gender', 'VARCHAR(64)', '性别', 2000, 'admin', NOW(), 'admin', NOW());

INSERT INTO `tab_form_field_definition`
    (`biz_type`, `metadata_field_id`, `field_name`, `field_code`, `control_type`, `dict_type_id`, `is_unique`,
     `is_required`, `show_in_list`, `show_in_create`, `show_in_edit`, `editable`, `validate_regex`, `placeholder`,
     `show_order`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT 'USER',
       (SELECT `id` FROM `tab_metadata_field` WHERE `table_name` = 'tab_user' AND `column_name` = 'gender'),
       '性别', 'gender', 3,
       (SELECT `id` FROM `tab_dict_type` WHERE `code` = 'gender'),
       0, 1, 1, 1, 1, 1, NULL, NULL, 7, 2000, 'admin', NOW(), 'admin', NOW();
