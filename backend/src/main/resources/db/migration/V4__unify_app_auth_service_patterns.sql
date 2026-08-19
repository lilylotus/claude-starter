-- ----------------------------------------------------------------------------
-- 统一应用认证协议回跳地址匹配列表存储（unify-app-auth-service-patterns change）
-- ----------------------------------------------------------------------------

-- tab_app_auth_config 原有 cas_service_patterns（服务 CAS 协议）、
-- oauth2_redirect_uri_patterns（服务 OAuth2.0 协议）两列语义、存储结构、校验逻辑完全一致，
-- 唯一区别只是"当前协议类型用哪一列"，合并为协议无关的单一列 service_patterns，CAS/
-- OAuth2.0 及未来新增协议共用同一份存储（design.md Decision 1/2）。列名已核对
-- MySQL/PostgreSQL/Oracle/SQL Server 保留字：service_patterns 非保留字。

-- 第一步：新增新列，与旧两列的既有约定保持一致（TEXT DEFAULT NULL，存 JSON 字符串数组文本）。
ALTER TABLE `tab_app_auth_config`
    ADD COLUMN `service_patterns` TEXT DEFAULT NULL
        COMMENT '回跳地址 ANT 匹配规则列表（JSON 字符串数组），CAS/OAuth2.0 等协议共用，auth_protocol=NONE 时为空'
        AFTER `oauth2_redirect_uri_patterns`;

-- 第二步：按当前 auth_protocol 取值，把两列中"当前协议对应的那一列"数据迁移进新列；
-- 非 CAS/OAUTH2（含 NONE 与理论上不应存在的脏数据）一律迁移为空数组，符合
-- "service_patterns 只代表当前生效协议"的语义（design.md Risks）。CASE 表达式为标准
-- SQL，MySQL 5.7 原生支持，不涉及窗口函数/CTE。
UPDATE `tab_app_auth_config`
SET `service_patterns` = CASE `auth_protocol`
    WHEN 'CAS' THEN `cas_service_patterns`
    WHEN 'OAUTH2' THEN `oauth2_redirect_uri_patterns`
    ELSE '[]'
END;

-- 第三步：删除旧两列，避免"读旧列还是新列"的歧义长期存在（design.md Decision 2）。
ALTER TABLE `tab_app_auth_config` DROP COLUMN `cas_service_patterns`;
ALTER TABLE `tab_app_auth_config` DROP COLUMN `oauth2_redirect_uri_patterns`;
