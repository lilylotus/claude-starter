-- ----------------------------------------------------------------------------
-- app-sync-field-mapping change：为元数据字段配置目录新增 bizType=ROLE，预置
-- tab_role 的 name/code/show_order/remark 四个字段（design.md Decision 7）。
-- tab_role 无 ext1~ext10 扩展列，本次只覆盖这 4 个既有列；ROLE 不加入
-- FormFieldBizType（角色管理页面不接入动态表单渲染管线），仅服务于
-- tab_app_sync_field_mapping.metadata_field_id 的可选来源。
-- 字段类型与 V1__init_schema.sql 中 tab_role 建表语句一致：name/code 均为
-- VARCHAR(64)，show_order 为 INT，remark 为 VARCHAR(255)。
-- ----------------------------------------------------------------------------

INSERT INTO `tab_metadata_field`
    (`biz_type`, `table_name`, `column_name`, `field_code`, `column_type`, `field_name`, `status`, `create_by`,
     `create_time`, `update_by`, `update_time`)
VALUES ('ROLE', 'tab_role', 'name', 'name', 'VARCHAR(64)', '角色名称', 2000, 'admin', NOW(), 'admin', NOW()),
       ('ROLE', 'tab_role', 'code', 'code', 'VARCHAR(64)', '角色编码', 2000, 'admin', NOW(), 'admin', NOW()),
       ('ROLE', 'tab_role', 'show_order', 'showOrder', 'INT', '显示序号', 2000, 'admin', NOW(), 'admin', NOW()),
       ('ROLE', 'tab_role', 'remark', 'remark', 'VARCHAR(255)', '备注', 2000, 'admin', NOW(), 'admin', NOW());
