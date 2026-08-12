-- ----------------------------------------------------------------------------
-- app-sync-field-mapping change：把 tab_app_config 上原有的
-- sync_org_enabled/sync_user_enabled/sync_app_enabled/sync_dict_enabled 四个布尔
-- 开关，升级为独立子表 tab_app_sync_domain_config——每个应用固定 5 行，分别对应
-- 组织/用户/应用/角色/字典五个数据域，每行携带"是否启用"与"每次拉取分页大小"两个
-- 属性（design.md Decision 1）。存量应用按原四个布尔列的值回填组织/用户/应用/字典
-- 四行，角色数据域是本次新增，没有历史值，默认不启用（design.md Decision 3）。
-- V3__app_config.sql/V6__app_sync_notify_config.sql 已在开发库执行过，按 Flyway
-- 约定不可再编辑，本迁移只新增建表 + 回填 + 删列。
-- 列名已核对 MySQL/PostgreSQL/Oracle/SQL Server 保留字：app_ref_id/sync_domain/
-- sync_enabled/page_size 均非保留字（design.md Decision 2：不用更容易和 SQL 标准
-- DOMAIN 关键字混淆的 domain/enabled）。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_app_sync_domain_config` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `app_ref_id`   BIGINT      NOT NULL COMMENT '所属应用 id，关联 tab_app.id',
    `sync_domain`  VARCHAR(16) NOT NULL COMMENT '数据域：ORG/USER/APP/ROLE/DICT',
    `sync_enabled` TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否允许同步该数据域：0=否，1=是',
    `page_size`    INT         NOT NULL DEFAULT 20 COMMENT '每次拉取分页大小',
    `create_by`    VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_app_sync_domain_config_app_domain` (`app_ref_id`, `sync_domain`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '应用同步数据域配置表，每个应用固定 5 行（组织/用户/应用/角色/字典）';

-- 按数据域展开回填：组织/用户/应用/字典四行取自 tab_app_config 对应旧布尔列的值，
-- 角色一行本次新增、默认不启用；五行的 page_size 均取默认值 20。
INSERT INTO `tab_app_sync_domain_config`
    (`app_ref_id`, `sync_domain`, `sync_enabled`, `page_size`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT `app_id`, 'ORG', `sync_org_enabled`, 20, `create_by`, `create_time`, `update_by`, `update_time`
FROM `tab_app_config`;

INSERT INTO `tab_app_sync_domain_config`
    (`app_ref_id`, `sync_domain`, `sync_enabled`, `page_size`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT `app_id`, 'USER', `sync_user_enabled`, 20, `create_by`, `create_time`, `update_by`, `update_time`
FROM `tab_app_config`;

INSERT INTO `tab_app_sync_domain_config`
    (`app_ref_id`, `sync_domain`, `sync_enabled`, `page_size`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT `app_id`, 'APP', `sync_app_enabled`, 20, `create_by`, `create_time`, `update_by`, `update_time`
FROM `tab_app_config`;

INSERT INTO `tab_app_sync_domain_config`
    (`app_ref_id`, `sync_domain`, `sync_enabled`, `page_size`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT `app_id`, 'DICT', `sync_dict_enabled`, 20, `create_by`, `create_time`, `update_by`, `update_time`
FROM `tab_app_config`;

INSERT INTO `tab_app_sync_domain_config`
    (`app_ref_id`, `sync_domain`, `sync_enabled`, `page_size`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT `app_id`, 'ROLE', 0, 20, `create_by`, `create_time`, `update_by`, `update_time`
FROM `tab_app_config`;

-- 四个旧布尔列的数据已经搬到 tab_app_sync_domain_config，删除避免同一份"是否启用"
-- 数据在两张表里各存一份。
ALTER TABLE `tab_app_config`
    DROP COLUMN `sync_org_enabled`,
    DROP COLUMN `sync_user_enabled`,
    DROP COLUMN `sync_app_enabled`,
    DROP COLUMN `sync_dict_enabled`;
