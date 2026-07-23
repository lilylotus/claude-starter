-- ----------------------------------------------------------------------------
-- Excel 导入字段配置模块 - 建表脚本（Flyway 迁移版本 V26）
-- 按业务对象类型（biz_type：ORG/USER/POSITION/APP）维护一组导入列配置：可关联
-- 已启用的表单字段定义（form_field_definition_id，可空），冗余存储字段标识
-- （field_code），Excel 表头名称（excel_header_name）可独立于表单展示名称自定义，
-- 是否作为匹配已有记录的主键列（is_primary_key）、导入语义下的必填开关
-- （is_required，独立于表单字段定义的必填开关）、显示序号（show_order，决定
-- 模板表头列顺序）。POSITION 的 __userCode/__orgCode 两条固定标识列种子数据见
-- 后续迁移 V27，保护逻辑不落库，由 Java 常量白名单
-- （cn.nihility.rbac.excelimport.constant.LockedImportFieldConfigs）在读取/更新
-- 时计算得出。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_import_field_config`
(
    `id`                        BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `biz_type`                  VARCHAR(20) NOT NULL COMMENT '业务对象类型：ORG/USER/POSITION/APP，创建后不可变',
    `form_field_definition_id`  BIGINT      NULL COMMENT '关联的表单字段定义 id，关联 tab_form_field_definition.id，可空',
    `field_code`                VARCHAR(64) NOT NULL COMMENT '字段标识：关联表单字段定义时取自其 field_code，POSITION 的固定标识列为 __userCode/__orgCode',
    `excel_header_name`         VARCHAR(64) NOT NULL COMMENT 'Excel 表头文字，可与表单展示名称不同',
    `is_primary_key`            TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否作为匹配已有记录的主键列之一',
    `is_required`               TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '导入语义下的必填，独立于表单字段定义的必填开关',
    `show_order`                INT         NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前，决定模板表头列顺序',
    `status`                    INT         NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，-1000=已删除（逻辑删除）',
    `create_by`                 VARCHAR(64) NULL COMMENT '创建人',
    `create_time`               DATETIME    NULL COMMENT '创建时间',
    `update_by`                 VARCHAR(64) NULL COMMENT '更新人',
    `update_time`               DATETIME    NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_import_field_config_biz_type` (`biz_type`),
    KEY `idx_tab_import_field_config_form_field_definition_id` (`form_field_definition_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = 'Excel 导入字段配置表';
