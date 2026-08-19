-- ----------------------------------------------------------------------------
-- 单点登出后端回调通知（add-sso-single-logout change）
-- ----------------------------------------------------------------------------

-- tab_app_auth_config 新增登出通知回调地址列：CAS/OAuth2.0 单点登出触发登出主流程时，系统
-- 按此地址以 POST + application/x-www-form-urlencoded 方式回调通知该应用登出事件
-- （design.md Decision 2/3）。未配置（NULL）时登出不通知该应用。列名已核对
-- MySQL/PostgreSQL/Oracle/SQL Server 保留字：logout_notify_url 非保留字。
ALTER TABLE `tab_app_auth_config`
    ADD COLUMN `logout_notify_url` VARCHAR(255) NULL COMMENT '登出通知回调地址，POST 回调该地址通知应用登出事件，未配置时登出不通知该应用'
    AFTER `oauth2_redirect_uri_patterns`;
