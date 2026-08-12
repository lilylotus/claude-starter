-- ----------------------------------------------------------------------------
-- app-sync-field-mapping change：新增应用同步字段映射表，为组织/用户/应用/角色
-- 四个数据域（不含字典）提供字段级同步映射配置——每行关联一个 tab_metadata_field
-- 记录（同步的源字段，字段名称/字段编码实时 JOIN 读取，不落快照，design.md
-- Decision 4），加上应用侧目标字段名称/编码、转换方式与转换取值。保存整个数据域的
-- 字段映射列表采用整体替换语义（先删后插），不做按行的增量 CRUD（design.md
-- Decision 5）。
-- 列名已核对 MySQL/PostgreSQL/Oracle/SQL Server 保留字：app_ref_id/sync_domain/
-- metadata_field_id/app_field_name/app_field_code/transform_type/transform_value
-- 均非保留字。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_app_sync_field_mapping` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `app_ref_id`        BIGINT       NOT NULL COMMENT '所属应用 id，关联 tab_app.id',
    `sync_domain`       VARCHAR(16)  NOT NULL COMMENT '数据域：ORG/USER/APP/ROLE',
    `metadata_field_id` BIGINT       NOT NULL COMMENT '同步的源字段，关联 tab_metadata_field.id',
    `app_field_name`    VARCHAR(128) NOT NULL COMMENT '应用侧目标字段名称，管理员手工填写',
    `app_field_code`    VARCHAR(128) NOT NULL COMMENT '应用侧目标字段编码，管理员手工填写',
    `transform_type`    VARCHAR(16)  NOT NULL DEFAULT 'NO_TRANSFORM'
        COMMENT '转换方式：NO_TRANSFORM=不转换，FIXED_VALUE=固定值，SCRIPT=转换脚本',
    `transform_value`   TEXT         NULL COMMENT '转换取值：固定值的具体值，或脚本源码，NO_TRANSFORM 时为空',
    `create_by`         VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`         VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_app_sync_field_mapping` (`app_ref_id`, `sync_domain`, `metadata_field_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '应用同步字段映射表，组织/用户/应用/角色四个数据域各自的字段级同步映射配置';
