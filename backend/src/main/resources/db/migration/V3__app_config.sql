-- ----------------------------------------------------------------------------
-- app-api-credentials-config change：新增应用对外接口凭证配置表 tab_app_config，
-- 与 tab_app 一对一（design.md Decision 1）。仅建表，不在此文件混入权限点/菜单
-- 种子数据，种子数据放在 V4__app_config_permission_seed.sql，保持"建表"与
-- "权限点数据初始化"两类关注点的迁移文件相互独立。
-- 列名已核对 MySQL/PostgreSQL/Oracle/SQL Server 保留字：app_id/open_app_id/
-- access_key/secret_key/sign_algorithm/sync_org_enabled/sync_user_enabled/
-- sync_app_enabled/sync_dict_enabled 均非保留字。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_app_config` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `app_id`             BIGINT       NOT NULL COMMENT '所属应用 id，关联 tab_app.id，一对一唯一',
    `open_app_id`        VARCHAR(64)  NOT NULL COMMENT '对外应用标识（AppId），系统生成，全局唯一，格式 app_ + 24 位随机十六进制',
    `access_key`         VARCHAR(64)  NOT NULL COMMENT '对外接口 AccessKey，系统生成，全局唯一，格式 ak_ + 32 位随机十六进制',
    `secret_key`         VARCHAR(255) NOT NULL COMMENT '对外接口 SecretKey，落库前经 SM4 对称加密（Base64），不存明文，仅重置接口单次返回明文',
    `sign_algorithm`     VARCHAR(16)  NOT NULL DEFAULT 'SHA256' COMMENT '接口签名算法：SHA256 或 SM3',
    `sync_org_enabled`   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否允许同步组织数据：0=否，1=是',
    `sync_user_enabled`  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否允许同步用户数据：0=否，1=是',
    `sync_app_enabled`   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否允许同步应用数据：0=否，1=是',
    `sync_dict_enabled`  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否允许同步字典数据：0=否，1=是',
    `create_by`          VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`          VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_app_config_app_id` (`app_id`),
    UNIQUE KEY `uk_tab_app_config_open_app_id` (`open_app_id`),
    UNIQUE KEY `uk_tab_app_config_access_key` (`access_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '应用对外接口凭证配置表，与 tab_app 一对一，无独立 status，随所属应用整体维护';
