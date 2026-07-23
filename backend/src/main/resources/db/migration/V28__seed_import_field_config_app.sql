-- ----------------------------------------------------------------------------
-- Excel 导入字段配置模块 - 预置应用（APP）固定标识列种子数据（Flyway 迁移版本 V28）
-- APP 的表单字段定义清单里没有、也不应该有 ownerId/orgId（它们是选择器，不是
-- 展示字段），但 AppCreateRequest/AppUpdateRequest 上二者均为 @NotNull 必填、
-- Excel 导入必须能通过人可读的编码定位到具体的负责人和组织。这里比照 V27 为
-- POSITION 预置的做法，预置两条不可删除、不可取消必填的固定导入配置行：
-- __ownerCode（表头"负责人编号"，导入时匹配 tab_user.code 得到 ownerId）与
-- __orgCode（表头"组织编码"，导入时匹配 tab_org.code 得到 orgId），
-- form_field_definition_id 为 NULL。保护逻辑不落库，由
-- cn.nihility.rbac.excelimport.constant.LockedImportFieldConfigs 白名单在
-- 更新/删除时计算得出并拒绝相应请求。
-- ----------------------------------------------------------------------------

INSERT INTO `tab_import_field_config`
    (`biz_type`, `form_field_definition_id`, `field_code`, `excel_header_name`, `is_primary_key`, `is_required`,
     `show_order`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('APP', NULL, '__ownerCode', '负责人编号', 1, 1, 200, 2000, 'admin', NOW(), 'admin', NOW()),
       ('APP', NULL, '__orgCode', '组织编码', 1, 1, 190, 2000, 'admin', NOW(), 'admin', NOW());
