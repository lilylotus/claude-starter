-- ----------------------------------------------------------------------------
-- RBAC 权限管理系统 - 数据库迁移脚本 V8
-- 新增登录日志能力（add-login-log change）：
--   1. 建表 tab_login_log，记录每一次登录尝试（成功 + 失败），只追加不更新不删除。
--   2. 新增 tab_menu 记录，挂在 system 一级分组下、排在「操作日志」(show_order=5) 下方。
--   3. 新增 tab_permission 记录 LoginLogManagement:loginLog:view。
--   4. 显式给 SUPER_ADMIN 角色追加该权限点关联——V6 超级管理员的全量授权是一次性
--      INSERT ... SELECT 历史快照，不会自动覆盖本次新增的权限点，必须显式补，否则
--      默认管理员账号迁移后看不到这个新菜单。
-- 字段名（login_account/user_id/user_name/login_result/fail_reason/login_ip/
-- login_terminal/login_os/login_browser/login_user_agent）已逐一核对
-- MySQL/PostgreSQL/Oracle/SQL Server 保留字，均无冲突。
-- ----------------------------------------------------------------------------

-- ============================================================================
-- 1. 登录日志模块
-- ============================================================================

CREATE TABLE IF NOT EXISTS `tab_login_log`
(
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `login_account`    VARCHAR(64)  NULL COMMENT '本次登录尝试提交的账号，解密失败时为 NULL',
    `user_id`          BIGINT       NULL COMMENT '关联的 tab_user.id，账号不存在/解密失败时为 NULL',
    `user_name`        VARCHAR(64)  NULL COMMENT '用户姓名快照，账号不存在/解密失败时为 NULL',
    `login_result`     TINYINT      NOT NULL COMMENT '登录结果：1=成功，2=失败',
    `fail_reason`      VARCHAR(64)  NULL COMMENT '失败原因文案，登录成功时为 NULL',
    `login_ip`         VARCHAR(64)  NULL COMMENT '登录发起 IP，取不到时为空',
    `login_terminal`   VARCHAR(32)  NULL COMMENT '登录终端类型，从 User-Agent 解析，解析不出时为空',
    `login_os`         VARCHAR(32)  NULL COMMENT '登录操作系统，从 User-Agent 解析，解析不出时为空',
    `login_browser`    VARCHAR(32)  NULL COMMENT '登录浏览器，从 User-Agent 解析，解析不出时为空',
    `login_user_agent` VARCHAR(512) NULL COMMENT '原始 User-Agent 请求头，取不到时为空',
    `create_by`        VARCHAR(64)  NOT NULL COMMENT '创建人，即本次登录尝试提交的账号，为空时存 unknown',
    `create_time`      DATETIME     NOT NULL COMMENT '创建时间，即本次登录尝试发生时间',
    `update_by`        VARCHAR(64)  NOT NULL COMMENT '更新人，恒等于 create_by（本表只追加不更新）',
    `update_time`      DATETIME     NOT NULL COMMENT '更新时间，恒等于 create_time（本表只追加不更新）',
    PRIMARY KEY (`id`),
    KEY `idx_tab_login_log_login_account` (`login_account`),
    KEY `idx_tab_login_log_user_id` (`user_id`),
    KEY `idx_tab_login_log_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '登录日志表，记录每一次登录尝试（成功+失败），只追加不更新不删除';

-- ============================================================================
-- 2. 菜单 + 权限点
-- ============================================================================

SET @system_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'system');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
VALUES ('登录日志', 'LoginLogManagement:loginLog:view', @system_id, 1, 4, '登录日志管理页面访问', 2000, 'admin', NOW(),
        'admin', NOW());

INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`,
                               `update_by`, `update_time`)
VALUES ('登录日志管理页面访问', 'LoginLogManagement:loginLog:view', 0, NULL, 2000, 'system', NOW(), 'system', NOW());

-- ============================================================================
-- 3. 给超级管理员角色追加本次新增的权限点
-- ============================================================================

SET @super_admin_role_id := (SELECT `id` FROM `tab_role` WHERE `code` = 'SUPER_ADMIN');
SET @login_log_permission_id := (SELECT `id` FROM `tab_permission` WHERE `code` = 'LoginLogManagement:loginLog:view');

INSERT INTO `tab_role_permission` (`role_id`, `permission_id`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES (@super_admin_role_id, @login_log_permission_id, 'system', NOW(), 'system', NOW());
