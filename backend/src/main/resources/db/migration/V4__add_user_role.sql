-- 用户角色规则体系（二次设计版本，替换首次设计的独立 tab_user_role 主表）：把角色批量
-- 打标签的能力从"一次性批量操作"改为"持久规则 + 事件驱动自动重算"
-- （add-user-role-batch-assignment change design.md Decision 1）。四张表：规则主表、
-- 规则组织范围条件、规则用户属性条件、规则执行结果表（后者是"用户是否持有某角色"的唯一
-- 数据来源，取代最初设计的 tab_user_role 表）。

-- 规则主表：一个角色可以有多条规则，每条规则独立维护自己的条件与执行状态
CREATE TABLE IF NOT EXISTS `tab_user_role_rule` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `role_id`        BIGINT       NOT NULL COMMENT '目标角色 id，关联 tab_role.id，不建物理外键',
    `name`           VARCHAR(128) NOT NULL COMMENT '规则名称，便于同一角色下管理多条规则',
    `remark`         VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    `last_exec_time` DATETIME     NULL COMMENT '最近一次执行时间，从未执行过为空',
    `last_exec_by`   VARCHAR(64)  NULL COMMENT '最近一次执行人（人工保存触发时为操作人，事件自动触发时为原始事件操作人）',
    `create_by`      VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_user_role_rule_role_id` (`role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户角色规则表：组织范围/用户属性条件持久化，事件驱动自动重算';

-- 规则组织范围条件，字段形状对齐 tab_app_access_policy_org_scope
CREATE TABLE IF NOT EXISTS `tab_user_role_rule_org_scope` (
    `id`               BIGINT     NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `rule_id`          BIGINT     NOT NULL COMMENT '所属规则 id，关联 tab_user_role_rule.id，不建物理外键',
    `org_id`           BIGINT     NOT NULL COMMENT '组织 id，关联 tab_org.id，不建物理外键',
    `include_children` TINYINT    NOT NULL DEFAULT 0 COMMENT '是否包含递归子组织：0=否，1=是',
    `create_by`        VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`      DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`      DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_user_role_rule_org_scope` (`rule_id`, `org_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户角色规则组织范围条件表';

-- 规则用户属性条件，字段形状对齐 tab_app_access_policy_user_attr，metadata_field_id
-- 允许关联 biz_type=USER 或 biz_type=POSITION（比现成的应用访问授权多一个域）
CREATE TABLE IF NOT EXISTS `tab_user_role_rule_user_attr` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `rule_id`           BIGINT       NOT NULL COMMENT '所属规则 id，关联 tab_user_role_rule.id，不建物理外键',
    `metadata_field_id` BIGINT       NOT NULL COMMENT '关联的元数据字段 id，biz_type 为 USER 或 POSITION，不建物理外键',
    `operator`          VARCHAR(8)   NOT NULL COMMENT '运算符：EQ=等于，NE=不等于，IN=属于多值',
    `attr_value`        VARCHAR(255) NOT NULL COMMENT '比较值，EQ/NE 为单个值，IN 为逗号分隔的多个值',
    `create_by`         VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`         VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_user_role_rule_user_attr` (`rule_id`, `metadata_field_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户角色规则用户属性条件表';

-- 规则执行结果表：按 rule_id 整体重建，是"用户是否持有某角色"的唯一数据来源，
-- 直接取代最初设计里独立的 tab_user_role 主表
CREATE TABLE IF NOT EXISTS `tab_user_role_rule_grant` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `rule_id`     BIGINT   NOT NULL COMMENT '产生该关联的规则 id，关联 tab_user_role_rule.id，不建物理外键',
    `user_id`     BIGINT   NOT NULL COMMENT '用户 id，关联 tab_user.id，不建物理外键',
    `role_id`     BIGINT   NOT NULL COMMENT '角色 id，冗余存储自 tab_user_role_rule.role_id，避免查询时反查规则表',
    `create_by`   VARCHAR(64)        DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   VARCHAR(64)        DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_user_role_rule_grant` (`rule_id`, `user_id`),
    KEY `idx_tab_user_role_rule_grant_role_user` (`role_id`, `user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户角色规则计算结果表，按 rule_id 整体重建';
