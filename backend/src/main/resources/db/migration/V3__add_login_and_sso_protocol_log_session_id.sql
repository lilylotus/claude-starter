-- ----------------------------------------------------------------------------
-- add-sso-protocol-access-log change（用户反馈追加）：为登录日志表与 SSO 协议调用
-- 记录表新增 session_id 关联字段，用于把"登录"与"登录之后该会话触发的 CAS/OAuth2
-- 协议调用"串联成同一条主线（design.md Decision 6）。session_id 存的是 SSO 会话
-- 令牌的 SHA-256 摘要，不是原始令牌本身，避免只读日志查询接口暴露可直接冒充登录的
-- bearer 凭据。列名 session_id 已核对 MySQL/PostgreSQL/Oracle/SQL Server 保留字，
-- 均无冲突。
-- ----------------------------------------------------------------------------

ALTER TABLE `tab_login_log`
    ADD COLUMN `session_id` VARCHAR(64) NULL COMMENT '本次登录建立的 SSO 会话标识（会话令牌 SHA-256 摘要），仅 SSO 登录成功时填充，管理端口令登录/登录失败为 NULL'
        AFTER `login_user_agent`,
    ADD KEY `idx_tab_login_log_session_id` (`session_id`);

ALTER TABLE `tab_sso_protocol_log`
    ADD COLUMN `session_id` VARCHAR(64) NULL COMMENT '本次调用所属 SSO 会话标识（会话令牌 SHA-256 摘要），处理链路尚未解析出会话时为 NULL'
        AFTER `user_id`,
    ADD KEY `idx_tab_sso_protocol_log_session_id` (`session_id`);
