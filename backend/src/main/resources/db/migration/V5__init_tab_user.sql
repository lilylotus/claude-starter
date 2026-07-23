-- ----------------------------------------------------------------------------
-- 用户管理模块 - 建表脚本（Flyway 迁移版本 V5）
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_user`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `name`        VARCHAR(64) NOT NULL COMMENT '用户姓名',
    `code`        VARCHAR(64) NOT NULL COMMENT '用户编号，未删除范围内唯一',
    `gender`      INT         NOT NULL DEFAULT 0 COMMENT '性别：0=未知，1=男，2=女',
    `mobile`      VARCHAR(20) NULL COMMENT '手机号，不做唯一性约束',
    `id_card`     VARCHAR(18) NULL COMMENT '身份证号，若提供需在未删除范围内唯一',
    `show_order`  INT         NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前',
    `remark`      VARCHAR(255) NULL COMMENT '备注',
    `status`      INT         NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）',
    `create_by`   VARCHAR(64) NULL COMMENT '创建人',
    `create_time` DATETIME    NULL COMMENT '创建时间',
    `update_by`   VARCHAR(64) NULL COMMENT '更新人',
    `update_time` DATETIME    NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_user_status` (`status`),
    KEY `idx_tab_user_code` (`code`),
    KEY `idx_tab_user_id_card` (`id_card`),
    KEY `idx_tab_user_mobile` (`mobile`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '用户表';

CREATE TABLE IF NOT EXISTS `tab_user_position`
(
    `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `user_id`          BIGINT      NOT NULL COMMENT '所属用户 id，关联 tab_user.id',
    `org_id`           BIGINT      NOT NULL COMMENT '所属组织 id，关联 tab_org.id，不建物理外键',
    `position_type`    VARCHAR(64) NOT NULL COMMENT '任职类型编码，取自字典类型 position_type 下的字典项编码',
    `position_address` VARCHAR(255) NULL COMMENT '任职地址',
    `position_phone`   VARCHAR(20) NULL COMMENT '任职电话',
    `show_order`       INT         NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前',
    `remark`           VARCHAR(255) NULL COMMENT '备注',
    `create_by`        VARCHAR(64) NULL COMMENT '创建人',
    `create_time`      DATETIME    NULL COMMENT '创建时间',
    `update_by`        VARCHAR(64) NULL COMMENT '更新人',
    `update_time`      DATETIME    NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_user_position_user_id` (`user_id`),
    KEY `idx_tab_user_position_org_id` (`org_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '用户任职记录表，无独立 status 列，任职记录做物理删除';
