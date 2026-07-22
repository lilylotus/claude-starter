-- ----------------------------------------------------------------------------
-- 元数据字段配置模块 - 写入元数据字段目录种子数据（Flyway 迁移版本 V19）
-- 覆盖组织（ORG）、人员（USER）、任职（POSITION）、应用（APP）四类业务对象
-- 各自"可开放配置的原有列"与全部 ext1~ext10 扩展列。已有专用交互控件的字段
-- （parentId、orgId、userId、positionType、ownerId、status、gender 等）不出现
-- 在此目录中，继续保持硬编码渲染。
-- ----------------------------------------------------------------------------

-- ---- 组织（ORG，对应 tab_org） ----
INSERT INTO `tab_metadata_field`
    (`biz_type`, `table_name`, `column_name`, `column_type`, `field_name`, `status`, `create_by`, `create_time`,
     `update_by`, `update_time`)
VALUES ('ORG', 'tab_org', 'name', 'VARCHAR(64)', '组织名称', 2000, 'admin', NOW(), 'admin', NOW()),
       ('ORG', 'tab_org', 'code', 'VARCHAR(64)', '组织编码', 2000, 'admin', NOW(), 'admin', NOW()),
       ('ORG', 'tab_org', 'show_order', 'INT', '显示序号', 2000, 'admin', NOW(), 'admin', NOW()),
       ('ORG', 'tab_org', 'remark', 'VARCHAR(255)', '备注', 2000, 'admin', NOW(), 'admin', NOW()),
       ('ORG', 'tab_org', 'ext1', 'VARCHAR(255)', '扩展字段 1', 2000, 'admin', NOW(), 'admin', NOW()),
       ('ORG', 'tab_org', 'ext2', 'VARCHAR(255)', '扩展字段 2', 2000, 'admin', NOW(), 'admin', NOW()),
       ('ORG', 'tab_org', 'ext3', 'VARCHAR(255)', '扩展字段 3', 2000, 'admin', NOW(), 'admin', NOW()),
       ('ORG', 'tab_org', 'ext4', 'VARCHAR(255)', '扩展字段 4', 2000, 'admin', NOW(), 'admin', NOW()),
       ('ORG', 'tab_org', 'ext5', 'VARCHAR(255)', '扩展字段 5', 2000, 'admin', NOW(), 'admin', NOW()),
       ('ORG', 'tab_org', 'ext6', 'VARCHAR(255)', '扩展字段 6', 2000, 'admin', NOW(), 'admin', NOW()),
       ('ORG', 'tab_org', 'ext7', 'VARCHAR(255)', '扩展字段 7', 2000, 'admin', NOW(), 'admin', NOW()),
       ('ORG', 'tab_org', 'ext8', 'VARCHAR(255)', '扩展字段 8', 2000, 'admin', NOW(), 'admin', NOW()),
       ('ORG', 'tab_org', 'ext9', 'VARCHAR(255)', '扩展字段 9', 2000, 'admin', NOW(), 'admin', NOW()),
       ('ORG', 'tab_org', 'ext10', 'VARCHAR(255)', '扩展字段 10', 2000, 'admin', NOW(), 'admin', NOW());

-- ---- 人员（USER，对应 tab_user） ----
INSERT INTO `tab_metadata_field`
    (`biz_type`, `table_name`, `column_name`, `column_type`, `field_name`, `status`, `create_by`, `create_time`,
     `update_by`, `update_time`)
VALUES ('USER', 'tab_user', 'name', 'VARCHAR(64)', '用户姓名', 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', 'tab_user', 'code', 'VARCHAR(64)', '用户编号', 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', 'tab_user', 'mobile', 'VARCHAR(20)', '手机号', 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', 'tab_user', 'id_card', 'VARCHAR(18)', '身份证号', 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', 'tab_user', 'show_order', 'INT', '显示序号', 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', 'tab_user', 'remark', 'VARCHAR(255)', '备注', 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', 'tab_user', 'ext1', 'VARCHAR(255)', '扩展字段 1', 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', 'tab_user', 'ext2', 'VARCHAR(255)', '扩展字段 2', 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', 'tab_user', 'ext3', 'VARCHAR(255)', '扩展字段 3', 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', 'tab_user', 'ext4', 'VARCHAR(255)', '扩展字段 4', 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', 'tab_user', 'ext5', 'VARCHAR(255)', '扩展字段 5', 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', 'tab_user', 'ext6', 'VARCHAR(255)', '扩展字段 6', 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', 'tab_user', 'ext7', 'VARCHAR(255)', '扩展字段 7', 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', 'tab_user', 'ext8', 'VARCHAR(255)', '扩展字段 8', 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', 'tab_user', 'ext9', 'VARCHAR(255)', '扩展字段 9', 2000, 'admin', NOW(), 'admin', NOW()),
       ('USER', 'tab_user', 'ext10', 'VARCHAR(255)', '扩展字段 10', 2000, 'admin', NOW(), 'admin', NOW());

-- ---- 任职（POSITION，对应 tab_user_position） ----
INSERT INTO `tab_metadata_field`
    (`biz_type`, `table_name`, `column_name`, `column_type`, `field_name`, `status`, `create_by`, `create_time`,
     `update_by`, `update_time`)
VALUES ('POSITION', 'tab_user_position', 'position_address', 'VARCHAR(255)', '任职地址', 2000, 'admin', NOW(),
        'admin', NOW()),
       ('POSITION', 'tab_user_position', 'position_phone', 'VARCHAR(20)', '任职电话', 2000, 'admin', NOW(), 'admin',
        NOW()),
       ('POSITION', 'tab_user_position', 'show_order', 'INT', '显示序号', 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', 'tab_user_position', 'remark', 'VARCHAR(255)', '备注', 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', 'tab_user_position', 'ext1', 'VARCHAR(255)', '扩展字段 1', 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', 'tab_user_position', 'ext2', 'VARCHAR(255)', '扩展字段 2', 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', 'tab_user_position', 'ext3', 'VARCHAR(255)', '扩展字段 3', 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', 'tab_user_position', 'ext4', 'VARCHAR(255)', '扩展字段 4', 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', 'tab_user_position', 'ext5', 'VARCHAR(255)', '扩展字段 5', 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', 'tab_user_position', 'ext6', 'VARCHAR(255)', '扩展字段 6', 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', 'tab_user_position', 'ext7', 'VARCHAR(255)', '扩展字段 7', 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', 'tab_user_position', 'ext8', 'VARCHAR(255)', '扩展字段 8', 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', 'tab_user_position', 'ext9', 'VARCHAR(255)', '扩展字段 9', 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', 'tab_user_position', 'ext10', 'VARCHAR(255)', '扩展字段 10', 2000, 'admin', NOW(), 'admin',
        NOW());

-- ---- 应用（APP，对应 tab_app） ----
INSERT INTO `tab_metadata_field`
    (`biz_type`, `table_name`, `column_name`, `column_type`, `field_name`, `status`, `create_by`, `create_time`,
     `update_by`, `update_time`)
VALUES ('APP', 'tab_app', 'name', 'VARCHAR(64)', '应用名称', 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP', 'tab_app', 'code', 'VARCHAR(64)', '应用编码', 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP', 'tab_app', 'show_order', 'INT', '显示序号', 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP', 'tab_app', 'remark', 'VARCHAR(255)', '备注', 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP', 'tab_app', 'ext1', 'VARCHAR(255)', '扩展字段 1', 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP', 'tab_app', 'ext2', 'VARCHAR(255)', '扩展字段 2', 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP', 'tab_app', 'ext3', 'VARCHAR(255)', '扩展字段 3', 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP', 'tab_app', 'ext4', 'VARCHAR(255)', '扩展字段 4', 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP', 'tab_app', 'ext5', 'VARCHAR(255)', '扩展字段 5', 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP', 'tab_app', 'ext6', 'VARCHAR(255)', '扩展字段 6', 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP', 'tab_app', 'ext7', 'VARCHAR(255)', '扩展字段 7', 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP', 'tab_app', 'ext8', 'VARCHAR(255)', '扩展字段 8', 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP', 'tab_app', 'ext9', 'VARCHAR(255)', '扩展字段 9', 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP', 'tab_app', 'ext10', 'VARCHAR(255)', '扩展字段 10', 2000, 'admin', NOW(), 'admin', NOW());
