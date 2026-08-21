-- ----------------------------------------------------------------------------
-- policy-condition-exclusive-priority change：策略规则新增显示序号字段，
-- SSO 协议调用记录新增拒绝来源策略 id 字段（design.md Decision）。
-- 列名 show_order / denied_policy_id 已核对 MySQL/PostgreSQL/Oracle/SQL Server
-- 保留字，均无冲突。
-- ----------------------------------------------------------------------------

-- 显示序号：数值越小优先级越高，运行时最终生效权限判定按该字段升序取排在最前的一条
-- 候选策略计算结果（与 tab_role.show_order"数值越大越靠前"的展示排序语义方向相反，
-- 使用前需注意区分）。
ALTER TABLE `tab_app_access_policy`
    ADD COLUMN `show_order` INT NOT NULL DEFAULT 0 COMMENT '显示序号，数值越小优先级越高，运行时按升序取第一条命中身份的策略计算结果'
        AFTER `status`;

-- 拒绝来源策略 id：仅失败原因为"应用访问授权策略拒绝"（CAS 登录票据签发/OAuth2 授权码
-- 签发因排在最前的候选策略请求控制条件不满足而被拒绝）时非空，其余失败原因均为 NULL。
ALTER TABLE `tab_sso_protocol_log`
    ADD COLUMN `denied_policy_id` BIGINT UNSIGNED NULL COMMENT '被应用访问授权策略拒绝时，拒绝来源的策略 id（tab_app_access_policy.id），仅该失败原因下非空'
        AFTER `fail_reason`;
