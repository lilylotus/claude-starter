-- ----------------------------------------------------------------------------
-- RBAC 权限管理系统 - 数据库迁移脚本 V2
-- 新增用户密码表 tab_user_password：独立存放密码摘要、盐值与首登强制改密标识，
-- 不侵入既有 tab_user 表结构（password-login-auth change design.md Decision 1）。
-- 列名已核对 MySQL/PostgreSQL/Oracle/SQL Server 保留字：password_digest（不用
-- password，避免与历史版本 MySQL 的 PASSWORD() 函数产生歧义）、salt、first_login、
-- user_id 均非保留字。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_user_password` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `user_id`         BIGINT       NOT NULL COMMENT '所属用户 id，关联 tab_user.id，唯一',
    `password_digest` VARCHAR(64)  NOT NULL COMMENT 'SHA-256(明文密码 + 盐值) 摘要，十六进制小写',
    `salt`            VARCHAR(32)  NOT NULL COMMENT '摘要盐值，SecureRandom 随机生成，十六进制编码',
    `first_login`     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否处于待首次登录强制改密状态：1=是，0=否',
    `create_by`       VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_user_password_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '用户密码表，每个用户仅保留一条当前有效密码记录，改密即整行 UPDATE，不保留历史密码';
