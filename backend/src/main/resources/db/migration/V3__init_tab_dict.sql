-- ----------------------------------------------------------------------------
-- 字典管理模块 - 建表脚本（Flyway 迁移版本 V3）
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_dict_type`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `name`        VARCHAR(64)  NOT NULL COMMENT '字典类型名称',
    `code`        VARCHAR(64)  NOT NULL COMMENT '字典类型编码，未删除范围内全局唯一',
    `show_order`  INT          NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前',
    `remark`      VARCHAR(255) NULL COMMENT '备注',
    `status`      INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）',
    `create_by`   VARCHAR(64)  NULL COMMENT '创建人',
    `create_time` DATETIME     NULL COMMENT '创建时间',
    `update_by`   VARCHAR(64)  NULL COMMENT '更新人',
    `update_time` DATETIME     NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_dict_type_code` (`code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '字典类型表';

CREATE TABLE IF NOT EXISTS `tab_dict_item`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `dict_type_id` BIGINT       NOT NULL COMMENT '所属字典类型 id，关联 tab_dict_type.id',
    `label`        VARCHAR(64)  NOT NULL COMMENT '字典项标签（展示文案）',
    `code`         VARCHAR(64)  NOT NULL COMMENT '字典项编码，同一 dict_type_id 下未删除范围内唯一',
    `show_order`   INT          NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前',
    `remark`       VARCHAR(255) NULL COMMENT '备注',
    `status`       INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）',
    `create_by`    VARCHAR(64)  NULL COMMENT '创建人',
    `create_time`  DATETIME     NULL COMMENT '创建时间',
    `update_by`    VARCHAR(64)  NULL COMMENT '更新人',
    `update_time`  DATETIME     NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_dict_item_dict_type_id` (`dict_type_id`),
    KEY `idx_tab_dict_item_code` (`code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '字典项表';
