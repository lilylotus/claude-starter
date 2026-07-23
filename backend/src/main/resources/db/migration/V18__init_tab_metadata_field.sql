-- ----------------------------------------------------------------------------
-- 元数据字段配置模块 - 建表脚本（Flyway 迁移版本 V18）
-- 记录组织（ORG）、人员（USER）、任职（POSITION）、应用（APP）四类业务对象
-- "可开放配置"的表字段目录，每条记录对应一个真实存在的数据库列。目录只能通过
-- 迁移预置，不提供新增/删除接口，接口只支持编辑 field_name/status、查询。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_metadata_field`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `biz_type`    VARCHAR(20)  NOT NULL COMMENT '业务对象类型：ORG/USER/POSITION/APP',
    `table_name`  VARCHAR(64)  NOT NULL COMMENT '字段所属表名称，如 tab_org，创建后不可修改',
    `column_name` VARCHAR(64)  NOT NULL COMMENT '字段列名（数据库字段定义），如 code、ext6，创建后不可修改',
    `field_code`  VARCHAR(64) NOT NULL DEFAULT '' COMMENT '字段标识（前端/DTO 使用），创建后不可修改',
    `column_type` VARCHAR(32)  NOT NULL COMMENT '字段类型（数据库字段类型），如 VARCHAR(255)，创建后不可修改',
    `field_name`  VARCHAR(64)  NOT NULL COMMENT '字段名称，如"组织编码"，可编辑',
    `status`      INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除，当前不使用）',
    `create_by`   VARCHAR(64)  NULL COMMENT '创建人',
    `create_time` DATETIME     NULL COMMENT '创建时间',
    `update_by`   VARCHAR(64)  NULL COMMENT '更新人',
    `update_time` DATETIME     NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_metadata_field_table_column` (`table_name`, `column_name`),
    UNIQUE KEY `uk_tab_metadata_field_biz_field_code` (`biz_type`, `field_code`),
    KEY `idx_tab_metadata_field_biz_type` (`biz_type`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '元数据字段配置目录表';
