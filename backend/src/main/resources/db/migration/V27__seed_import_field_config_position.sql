-- ----------------------------------------------------------------------------
-- Excel 导入字段配置模块 - 预置任职（POSITION）固定标识列种子数据（Flyway 迁移版本 V27）
-- POSITION 的表单字段定义清单里没有、也不应该有 userId/orgId（它们是选择器，不是
-- 展示字段），但 Excel 导入必须能通过人可读的编码定位到具体的人员和组织。这里预置
-- 两条不可删除、不可取消必填的固定导入配置行：__userCode（表头"人员编号"，导入时
-- 匹配 tab_user.code 得到 userId）与 __orgCode（表头"组织编码"，导入时匹配
-- tab_org.code 得到 orgId），form_field_definition_id 为 NULL。保护逻辑不落库，
-- 由 cn.nihility.rbac.excelimport.constant.LockedImportFieldConfigs 白名单在
-- 更新/删除时计算得出并拒绝相应请求。
-- ----------------------------------------------------------------------------

INSERT INTO `tab_import_field_config`
    (`biz_type`, `form_field_definition_id`, `field_code`, `excel_header_name`, `is_primary_key`, `is_required`,
     `show_order`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('POSITION', NULL, '__userCode', '人员编号', 1, 1, 200, 2000, 'admin', NOW(), 'admin', NOW()),
       ('POSITION', NULL, '__orgCode', '组织编码', 1, 1, 190, 2000, 'admin', NOW(), 'admin', NOW());
