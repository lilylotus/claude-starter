-- ----------------------------------------------------------------------------
-- 组织管理模块 - 建表脚本（Flyway 迁移版本 V1）
-- 数据库需提前手动创建，例如：CREATE DATABASE rbac_demo DEFAULT CHARACTER SET utf8mb4;
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_org`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `name`        VARCHAR(64)  NOT NULL COMMENT '组织名称',
    `code`        VARCHAR(64)  NOT NULL COMMENT '组织编码',
    `parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '上级组织 id，0 表示顶级/根节点',
    `status`      INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）',
    `show_order`  INT          NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前',
    `ext1`        VARCHAR(255) NULL COMMENT '预留扩展字段 1，暂不使用',
    `ext2`        VARCHAR(255) NULL COMMENT '预留扩展字段 2，暂不使用',
    `ext3`        VARCHAR(255) NULL COMMENT '预留扩展字段 3，暂不使用',
    `ext4`        VARCHAR(255) NULL COMMENT '预留扩展字段 4，暂不使用',
    `ext5`        VARCHAR(255) NULL COMMENT '预留扩展字段 5，暂不使用',
    `ext6`        VARCHAR(255) NULL COMMENT '预留扩展字段 6，暂不使用',
    `ext7`        VARCHAR(255) NULL COMMENT '预留扩展字段 7，暂不使用',
    `ext8`        VARCHAR(255) NULL COMMENT '预留扩展字段 8，暂不使用',
    `ext9`        VARCHAR(255) NULL COMMENT '预留扩展字段 9，暂不使用',
    `ext10`       VARCHAR(255) NULL COMMENT '预留扩展字段 10，暂不使用',
    `create_by`   VARCHAR(64)  NULL COMMENT '创建人',
    `create_time` DATETIME     NULL COMMENT '创建时间',
    `update_by`   VARCHAR(64)  NULL COMMENT '更新人',
    `update_time` DATETIME     NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_org_parent_id` (`parent_id`),
    KEY `idx_tab_org_status` (`status`),
    KEY `idx_tab_org_code` (`code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '组织机构表';
