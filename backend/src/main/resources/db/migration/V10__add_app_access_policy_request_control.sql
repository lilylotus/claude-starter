-- ----------------------------------------------------------------------------
-- 应用访问授权 - 请求控制条件（app-access-request-control change）
-- ----------------------------------------------------------------------------
-- 两张表：策略的浏览器白名单条件、策略的 IP/网段白名单条件，与既有
-- tab_app_access_policy_org_scope/tab_app_access_policy_target_app 完全同构（零条或
-- 多条，整体替换语义，先删后插，物理删除，无物理外键）。请求控制是运行时校验，不参与
-- 策略"执行"的批量身份计算，`tab_app_access_policy_grant` 表结构不受本次改动影响（见
-- design.md Decision 2）。字段命名已核对 MySQL/PostgreSQL/Oracle/SQL Server 保留字：
-- policy_id/browser_code/ip_cidr 均非保留字。

-- 策略浏览器白名单条件：零条或多条，browser_code 取值枚举 CHROME/FIREFOX/SAFARI/
-- EDGE/OPERA/IE，与 UserAgentParser.parseBrowser 能识别的浏览器一一对应。
CREATE TABLE IF NOT EXISTS `tab_app_access_policy_browser_rule` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `policy_id`    BIGINT      NOT NULL COMMENT '所属策略 id，关联 tab_app_access_policy.id，不建物理外键',
    `browser_code` VARCHAR(16) NOT NULL COMMENT '浏览器编码：CHROME/FIREFOX/SAFARI/EDGE/OPERA/IE',
    `create_by`    VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_app_access_policy_browser_rule` (`policy_id`, `browser_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '应用访问授权策略浏览器白名单条件表';

-- 策略 IP/网段白名单条件：零条或多条，ip_cidr 存原始字符串（单 IP 或 CIDR 网段），保存前
-- 由服务层用 IpCidrMatcher.isValidRule 校验格式合法。
CREATE TABLE IF NOT EXISTS `tab_app_access_policy_ip_rule` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `policy_id`    BIGINT      NOT NULL COMMENT '所属策略 id，关联 tab_app_access_policy.id，不建物理外键',
    `ip_cidr`      VARCHAR(64) NOT NULL COMMENT '单个 IP 地址或 CIDR 网段，如 192.168.1.100 或 192.168.1.0/24',
    `create_by`    VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_app_access_policy_ip_rule` (`policy_id`, `ip_cidr`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '应用访问授权策略 IP/网段白名单条件表';
