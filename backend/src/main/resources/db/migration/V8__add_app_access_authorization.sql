-- ----------------------------------------------------------------------------
-- 应用访问授权模块（app-access-authorization change）
-- ----------------------------------------------------------------------------
-- 五张表：策略规则本身、策略的组织范围条件、策略的用户属性条件（关联
-- tab_metadata_field，biz_type=USER）、策略的目标应用、策略计算结果（POLICY 来源
-- 授权记录，按 policy_id 整体重建），以及独立的人工例外表（GRANT/DENY，与策略结果表
-- 物理隔离，互不影响，见 design.md Decision 2）。全部不建物理外键，字段命名已核对
-- MySQL/PostgreSQL/Oracle/SQL Server 保留字：name/remark/status/last_exec_time/
-- last_exec_by/policy_id/org_id/include_children/metadata_field_id/operator/
-- attr_value/app_id/user_id/override_type 均非保留字。

-- 策略规则主表：名称、备注、启用状态、最近一次执行时间/执行人。
CREATE TABLE IF NOT EXISTS `tab_app_access_policy` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `name`           VARCHAR(128) NOT NULL COMMENT '策略名称',
    `remark`         VARCHAR(255) NULL COMMENT '备注',
    `status`         INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用',
    `last_exec_time` DATETIME     NULL COMMENT '最近一次执行时间，从未执行过为空',
    `last_exec_by`   VARCHAR(64)  NULL COMMENT '最近一次执行人',
    `create_by`      VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_app_access_policy_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '应用访问授权策略规则表';

-- 策略组织范围条件：零条或多条，整体替换语义（先删后插），字段形状对齐
-- tab_admin_org_scope 范式。
CREATE TABLE IF NOT EXISTS `tab_app_access_policy_org_scope` (
    `id`               BIGINT     NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `policy_id`        BIGINT     NOT NULL COMMENT '所属策略 id，关联 tab_app_access_policy.id，不建物理外键',
    `org_id`           BIGINT     NOT NULL COMMENT '组织 id，关联 tab_org.id，不建物理外键',
    `include_children` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否包含递归子组织：0=否，1=是',
    `create_by`        VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`      DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`      DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_app_access_policy_org_scope` (`policy_id`, `org_id`),
    KEY `idx_tab_app_access_policy_org_scope_org_id` (`org_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '应用访问授权策略组织范围条件表';

-- 策略用户属性条件：零条或多条，关联 tab_metadata_field（biz_type=USER）的
-- metadata_field_id，运算符仅 EQ/NE/IN 三种，IN 时 attr_value 为逗号分隔多值
-- （design.md Decision 1）。
CREATE TABLE IF NOT EXISTS `tab_app_access_policy_user_attr` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `policy_id`         BIGINT       NOT NULL COMMENT '所属策略 id，关联 tab_app_access_policy.id，不建物理外键',
    `metadata_field_id` BIGINT       NOT NULL COMMENT '关联的元数据字段 id，关联 tab_metadata_field.id（biz_type=USER），不建物理外键',
    `operator`          VARCHAR(8)   NOT NULL COMMENT '运算符：EQ=等于，NE=不等于，IN=属于多值',
    `attr_value`        VARCHAR(255) NOT NULL COMMENT '比较值，EQ/NE 为单个值，IN 为逗号分隔的多个值',
    `create_by`         VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`         VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_app_access_policy_user_attr` (`policy_id`, `metadata_field_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '应用访问授权策略用户属性条件表';

-- 策略目标应用：一条策略对应一批具体应用 id，多选，至少一个。
CREATE TABLE IF NOT EXISTS `tab_app_access_policy_target_app` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `policy_id`   BIGINT   NOT NULL COMMENT '所属策略 id，关联 tab_app_access_policy.id，不建物理外键',
    `app_id`      BIGINT   NOT NULL COMMENT '目标应用 id，关联 tab_app.id，不建物理外键',
    `create_by`   VARCHAR(64)        DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   VARCHAR(64)        DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_app_access_policy_target_app` (`policy_id`, `app_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '应用访问授权策略目标应用表';

-- 策略计算结果表（POLICY 来源授权记录）：点击"执行"后按 policy_id 整体重建
-- （DELETE ... WHERE policy_id=:id 后批量插入），只存策略计算结果，不存人工例外
-- （design.md Decision 2，与 tab_app_access_manual_override 物理隔离）。同一
-- user_id+app_id 组合可能出现在多条不同 policy_id 下（多个策略都命中同一人同一
-- 应用是正常场景），故唯一约束含 policy_id。
CREATE TABLE IF NOT EXISTS `tab_app_access_policy_grant` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `policy_id`   BIGINT   NOT NULL COMMENT '产生该授权记录的策略 id，关联 tab_app_access_policy.id，不建物理外键',
    `user_id`     BIGINT   NOT NULL COMMENT '用户 id，关联 tab_user.id，不建物理外键',
    `app_id`      BIGINT   NOT NULL COMMENT '应用 id，关联 tab_app.id，不建物理外键',
    `create_by`   VARCHAR(64)        DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   VARCHAR(64)        DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_app_access_policy_grant` (`policy_id`, `user_id`, `app_id`),
    KEY `idx_tab_app_access_policy_grant_user_app` (`user_id`, `app_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '应用访问授权策略计算结果表，按 policy_id 整体重建，SHALL NOT 影响人工例外记录';

-- 人工例外表：对具体"用户+应用"组合手动追加授权（GRANT）或手动收回授权（DENY），
-- 优先级最高，与策略计算结果表物理隔离（design.md Decision 2）。每个用户+应用组合
-- 同一时刻至多一条记录，upsert 语义（重复提交更新已有记录而不是新增）。
CREATE TABLE IF NOT EXISTS `tab_app_access_manual_override` (
    `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `user_id`        BIGINT      NOT NULL COMMENT '用户 id，关联 tab_user.id，不建物理外键',
    `app_id`         BIGINT      NOT NULL COMMENT '应用 id，关联 tab_app.id，不建物理外键',
    `override_type`  VARCHAR(8)  NOT NULL COMMENT '例外类型：GRANT=手动追加授权，DENY=手动收回授权',
    `remark`         VARCHAR(255) NULL COMMENT '备注',
    `create_by`      VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_app_access_manual_override` (`user_id`, `app_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '应用访问授权人工例外表（GRANT/DENY），优先级高于策略计算结果';
