CREATE TABLE `tab_admin` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `name`        VARCHAR(64)  NOT NULL COMMENT '管理员名称',
    `code`        VARCHAR(64)  NOT NULL COMMENT '管理员编码',
    `user_id`     BIGINT       NOT NULL COMMENT '关联用户 id，关联 tab_user.id，不建物理外键',
    `show_order`  INT          NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前',
    `remark`      VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    `status`      INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）',
    `create_by`   VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_admin_status` (`status`),
    KEY `idx_tab_admin_code` (`code`),
    KEY `idx_tab_admin_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '管理员主数据表';

CREATE TABLE `tab_admin_role` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `admin_id`    BIGINT       NOT NULL COMMENT '管理员 id，关联 tab_admin.id',
    `role_id`     BIGINT       NOT NULL COMMENT '角色 id，关联 tab_role.id',
    `create_by`   VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_admin_role` (`admin_id`, `role_id`),
    KEY `idx_tab_admin_role_admin_id` (`admin_id`),
    KEY `idx_tab_admin_role_role_id` (`role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '管理员角色关联表，无独立 status，随管理员整体同步、物理删除';

CREATE TABLE `tab_admin_org_scope` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `admin_id`         BIGINT       NOT NULL COMMENT '管理员 id，关联 tab_admin.id',
    `org_id`           BIGINT       NOT NULL COMMENT '组织 id，关联 tab_org.id',
    `include_children` TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否包含递归子组织：0=否，1=是',
    `create_by`        VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_admin_org_scope` (`admin_id`, `org_id`),
    KEY `idx_tab_admin_org_scope_admin_id` (`admin_id`),
    KEY `idx_tab_admin_org_scope_org_id` (`org_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '管理员组织管辖范围表，无独立 status，随管理员整体同步、物理删除';
