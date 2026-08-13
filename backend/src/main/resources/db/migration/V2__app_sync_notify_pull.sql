-- ----------------------------------------------------------------------------
-- 应用数据同步通知/拉取能力（app-sync-notify-pull-api change）
-- 1. tab_app_config 新增 need_sign 列（是否需要签名/验签校验）。
-- 2. 为 tab_app 存量每一行在 tab_app_sync_domain_config 补一行 sync_domain='POSITION'，
--    与 AppSyncConfigServiceImpl.createDefaultDomainConfigs 新的默认值保持一致
--    （sync_enabled=0，page_size=20）。
-- 3. 新建 tab_app_data_change_log：应用数据变更记录表，id 自增列本身即对外序列号
--    （sequence 属于 SQL 保留字/对象类型，不作为列名，见 design.md Decision 6）。
-- 4. 新建 tab_app_notify_record：应用通知发送记录表，仅用于问题排查/展示，不驱动重试。
-- ----------------------------------------------------------------------------

ALTER TABLE `tab_app_config`
    ADD COLUMN `need_sign` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否需要签名/验签校验' AFTER `sign_algorithm`;

INSERT INTO `tab_app_sync_domain_config` (`app_ref_id`, `sync_domain`, `sync_enabled`, `page_size`,
                                           `create_by`, `create_time`, `update_by`, `update_time`)
SELECT `id`, 'POSITION', 0, 20, 'system', NOW(), 'system', NOW()
FROM `tab_app`;

-- 应用数据变更记录表：id 自增列全局单调递增，直接对外充当序列号，不额外维护计数器；
-- 只追加不更新不删除。列名已核对 MySQL/PostgreSQL/Oracle/SQL Server 保留字：
-- data_type/biz_id/operation_type 均非保留字。
CREATE TABLE IF NOT EXISTS `tab_app_data_change_log` (
    `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id，全局单调递增，直接作为对外的序列号（sequence）',
    `data_type`      VARCHAR(20) NOT NULL COMMENT '数据类型：ORG/USER/POSITION/APP/ROLE',
    `biz_id`         BIGINT      NOT NULL COMMENT '变更对象主键 id',
    `operation_type` TINYINT     NOT NULL COMMENT '操作类型：1=新增，2=编辑，3=启用，4=停用，5=删除',
    `create_by`      VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_app_data_change_log_type_biz` (`data_type`, `biz_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '应用数据变更记录表，id 自增列本身即对外序列号，只追加不更新不删除';

-- 应用通知发送记录表：仅用于问题排查/展示，不驱动自动重试。列名已核对
-- MySQL/PostgreSQL/Oracle/SQL Server 保留字：change_log_id/app_ref_id/notify_status/
-- http_status/error_msg 均非保留字。
CREATE TABLE IF NOT EXISTS `tab_app_notify_record` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `change_log_id` BIGINT       NOT NULL COMMENT '关联 tab_app_data_change_log.id',
    `app_ref_id`    BIGINT       NOT NULL COMMENT '关联 tab_app.id',
    `notify_status` TINYINT      NOT NULL COMMENT '通知状态：1=成功，2=失败',
    `http_status`   INT          NULL COMMENT '外部接口返回的 HTTP 状态码，失败且未收到响应时为空',
    `error_msg`     VARCHAR(500) NULL COMMENT '失败原因摘要',
    `create_by`     VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_app_notify_record_change_log_id` (`change_log_id`),
    KEY `idx_tab_app_notify_record_app_ref_id` (`app_ref_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '应用通知发送记录表，仅用于问题排查/展示，不驱动自动重试';
