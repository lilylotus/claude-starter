-- ----------------------------------------------------------------------------
-- Excel 导入字段配置模块 - 预置组织（ORG）固定标识列种子数据（Flyway 迁移版本 V29）
-- ORG 的表单字段定义清单里没有、也不应该有 parentId（组织管理页面里用的是树形
-- 选择器，不是可开放配置的展示字段），但 OrgCreateRequest/OrgUpdateRequest 上
-- parentId 为 @NotNull（默认 0 表示顶级），Excel 导入必须能通过人可读的编码定位
-- 到具体的上级组织。这里比照 V27/V28 为 POSITION/APP 预置的做法，预置一条不可
-- 删除、不可改绑的固定导入配置行：__parentCode（表头"上级组织编码"，导入时匹配
-- tab_org.code 得到 parentId），form_field_definition_id 为 NULL。
-- __parentCode 是必填（is_required=1）：每一行都必须显式填写上级组织编码，若该
-- 组织本身是顶级组织，则填写字面值 "0"，而不是留空（design.md Decision 2 二次
-- 调整）。保护逻辑不落库，由
-- cn.nihility.rbac.excelimport.constant.LockedImportFieldConfigs 白名单在
-- 更新/删除时计算得出并拒绝相应请求。
-- ----------------------------------------------------------------------------

INSERT INTO `tab_import_field_config`
    (`biz_type`, `form_field_definition_id`, `field_code`, `excel_header_name`, `is_primary_key`, `is_required`,
     `show_order`, `status`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES ('ORG', NULL, '__parentCode', '上级组织编码', 0, 1, 1, 2000, 'admin', NOW(), 'admin', NOW());
