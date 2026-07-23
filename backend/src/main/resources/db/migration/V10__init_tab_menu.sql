CREATE TABLE `tab_menu` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `name`          VARCHAR(64)  NOT NULL COMMENT '资源名称',
    `code`          VARCHAR(64)  NOT NULL COMMENT '资源编码',
    `parent_id`     BIGINT       NOT NULL DEFAULT 0 COMMENT '上级资源 id，0 表示顶级',
    `resource_type` INT          NOT NULL COMMENT '资源类型：1=菜单，2=按钮，3=API',
    `show_order`    INT          NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前',
    `remark`        VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    `status`        INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）',
    `create_by`     VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_menu_parent_id` (`parent_id`),
    KEY `idx_tab_menu_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '菜单/按钮/API 资源主数据表';
