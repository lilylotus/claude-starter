-- ----------------------------------------------------------------------------
-- add-sso-protocol-access-log change：新增 SSO 协议调用记录表
-- ----------------------------------------------------------------------------

-- SSO 协议调用记录表：记录 CAS/OAuth2.0 协议全部运行时端点（含全局单点登出接口）的每一次
-- 调用（成功+失败），只追加不更新不删除，与 tab_login_log 物理隔离、互不影响。字段名
-- （app_ref_id/app_id/protocol/event_type/user_id/result/fail_reason/client_ip/
-- user_agent）已逐一核对 MySQL/PostgreSQL/Oracle/SQL Server 保留字，均无冲突。
CREATE TABLE IF NOT EXISTS `tab_sso_protocol_log`
(
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `app_ref_id`   BIGINT       NULL COMMENT '解析出的应用 id，关联 tab_app.id，appId/client_id 解析不到应用时为 NULL',
    `app_id`       VARCHAR(64)  NULL COMMENT '原始 appId/client_id 参数值，即使解析不到 app_ref_id 也保留，便于排查',
    `protocol`     VARCHAR(16)  NOT NULL COMMENT '协议类型：CAS / OAUTH2 / NONE（应用不存在或未开启协议时的兜底值）',
    `event_type`   VARCHAR(20)  NOT NULL COMMENT '事件类型：LOGIN/SERVICE_VALIDATE/LOGOUT/AUTHORIZE/TOKEN/USERINFO',
    `user_id`      BIGINT       NULL COMMENT '关联的 tab_user.id，处理链路尚未解析出用户身份时为 NULL',
    `result`       TINYINT      NOT NULL COMMENT '调用结果：1=成功，2=失败',
    `fail_reason`  VARCHAR(255) NULL COMMENT '失败原因文案，成功时为 NULL',
    `client_ip`    VARCHAR(64)  NULL COMMENT '客户端 IP，取不到时为空',
    `user_agent`   VARCHAR(512) NULL COMMENT '原始 User-Agent 请求头，取不到时为空',
    `create_by`    VARCHAR(64)  NOT NULL COMMENT '创建人，为空时存 unknown',
    `create_time`  DATETIME     NOT NULL COMMENT '创建时间，即本次调用发生时间',
    `update_by`    VARCHAR(64)  NOT NULL COMMENT '更新人，恒等于 create_by（本表只追加不更新）',
    `update_time`  DATETIME     NOT NULL COMMENT '更新时间，恒等于 create_time（本表只追加不更新）',
    PRIMARY KEY (`id`),
    KEY `idx_tab_sso_protocol_log_app_ref_id` (`app_ref_id`, `create_time`),
    KEY `idx_tab_sso_protocol_log_event_type` (`event_type`, `create_time`),
    KEY `idx_tab_sso_protocol_log_user_id` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = 'SSO 协议调用记录表，记录 CAS/OAuth2.0 协议运行时端点的每一次调用（成功+失败），只追加不更新不删除';
