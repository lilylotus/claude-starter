-- ----------------------------------------------------------------------------
-- add-sso-login-methods change：SSO 登录页新增短信验证码、扫码两种登录方式
-- （design.md Decision 1/7）：
-- 1. tab_app_auth_config 新增列 login_methods，JSON 字符串数组文本，存储该应用允许的
--    登录认证方式（PASSWORD/SMS/QRCODE 子集，PASSWORD 恒定包含）；新增列带默认值，
--    存量应用自动回填为仅允许口令登录，不影响现有登录行为。
-- 2. tab_login_log 新增列 login_method，区分该条登录日志产生自口令/短信/扫码哪种登录
--    方式；新增列带默认值，存量数据自动视为口令登录产生。
-- 均为标准 ALTER TABLE ADD COLUMN，MySQL 5.7 兼容写法，不涉及窗口函数/CTE 等 8.0+
-- 专属特性，无需额外的数据回填脚本。
-- ----------------------------------------------------------------------------

ALTER TABLE `tab_app_auth_config`
    ADD COLUMN `login_methods` VARCHAR(500) NOT NULL DEFAULT '["PASSWORD"]'
        COMMENT '允许的登录认证方式，JSON 字符串数组（PASSWORD/SMS/QRCODE 子集，PASSWORD 恒定包含）'
        AFTER `service_patterns`;

ALTER TABLE `tab_login_log`
    ADD COLUMN `login_method` VARCHAR(20) NOT NULL DEFAULT 'PASSWORD'
        COMMENT '登录方式：PASSWORD=口令，SMS=短信验证码，QRCODE=扫码'
        AFTER `session_id`;
