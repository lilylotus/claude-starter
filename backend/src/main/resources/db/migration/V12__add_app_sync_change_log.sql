CREATE TABLE `tab_app_data_change_log` (
    `change_seq` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库生成的全局递增游标',
    `event_id` BIGINT NOT NULL COMMENT '雪花算法生成的全局事件标识',
    `entity_type` VARCHAR(16) NOT NULL COMMENT 'ORG/USER/POSITION/APP/ROLE',
    `entity_id` BIGINT NOT NULL COMMENT '业务实体 id',
    `operation_type` VARCHAR(16) NOT NULL COMMENT 'CREATE/UPDATE/ENABLE/DISABLE/DELETE',
    `entity_version` BIGINT NOT NULL COMMENT '实体结果版本',
    `org_scope_path_before` VARCHAR(255) NULL COMMENT '变更前组织范围路径',
    `org_scope_path_after` VARCHAR(255) NULL COMMENT '变更后组织范围路径',
    `change_time` DATETIME NOT NULL COMMENT '变更发生时间',
    `create_by` VARCHAR(64) NULL COMMENT '创建人',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    `update_by` VARCHAR(64) NULL COMMENT '更新人',
    `update_time` DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`change_seq`),
    UNIQUE KEY `uk_tab_app_data_change_log_event_id` (`event_id`),
    KEY `idx_tab_app_data_change_log_type_seq` (`entity_type`, `change_seq`),
    KEY `idx_tab_app_data_change_log_type_id` (`entity_type`, `entity_id`),
    KEY `idx_tab_app_data_change_log_time` (`change_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用数据全局变更流水';

CREATE TABLE `tab_app_sync_metadata` (
    `metadata_key` VARCHAR(64) NOT NULL COMMENT '元数据键',
    `metadata_value` VARCHAR(255) NOT NULL COMMENT '元数据值',
    `update_time` DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`metadata_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用同步全局元数据';

INSERT INTO `tab_app_sync_metadata` (`metadata_key`, `metadata_value`, `update_time`)
VALUES ('CHANGE_LOG_RETENTION_FLOOR_SEQ', '0', NOW());

CREATE TABLE `tab_app_sync_cursor` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `app_ref_id` BIGINT NOT NULL COMMENT '应用 id',
    `entity_type` VARCHAR(16) NOT NULL COMMENT '同步实体类型',
    `last_delivered_seq` BIGINT NOT NULL COMMENT '最近成功返回的序号',
    `update_time` DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_app_sync_cursor_app_entity` (`app_ref_id`, `entity_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用同步服务端投递水位';

ALTER TABLE `tab_org` ADD COLUMN `version` BIGINT NOT NULL DEFAULT 1 COMMENT '同步实体版本';
ALTER TABLE `tab_user` ADD COLUMN `version` BIGINT NOT NULL DEFAULT 1 COMMENT '同步实体版本';
ALTER TABLE `tab_user_position` ADD COLUMN `version` BIGINT NOT NULL DEFAULT 1 COMMENT '同步实体版本';
ALTER TABLE `tab_app` ADD COLUMN `version` BIGINT NOT NULL DEFAULT 1 COMMENT '同步实体版本';
ALTER TABLE `tab_role` ADD COLUMN `version` BIGINT NOT NULL DEFAULT 1 COMMENT '同步实体版本';
ALTER TABLE `tab_app_config` ADD COLUMN `config_epoch` BIGINT NOT NULL DEFAULT 0 COMMENT '应用同步配置纪元';

ALTER TABLE `tab_app_notify_record`
    ADD COLUMN `event_id` BIGINT NULL COMMENT '雪花事件标识' AFTER `app_ref_id`,
    ADD COLUMN `change_seq` BIGINT NULL COMMENT '变更流水序号' AFTER `event_id`,
    ADD COLUMN `entity_version` BIGINT NULL COMMENT '实体版本' AFTER `change_seq`,
    ADD COLUMN `request_body` TEXT NULL COMMENT '通知请求体快照' AFTER `notify_url`,
    ADD COLUMN `task_status` VARCHAR(16) NOT NULL DEFAULT 'SUCCESS' COMMENT 'PENDING/PROCESSING/RETRY/SUCCESS/DEAD' AFTER `request_body`,
    ADD COLUMN `retry_count` INT NOT NULL DEFAULT 0 COMMENT '已失败尝试次数' AFTER `task_status`,
    ADD COLUMN `next_retry_time` DATETIME NULL COMMENT '下次重试时间' AFTER `retry_count`,
    ADD COLUMN `lease_until` DATETIME NULL COMMENT '处理租约截止时间' AFTER `next_retry_time`,
    ADD UNIQUE KEY `uk_tab_app_notify_record_app_event` (`app_ref_id`, `event_id`),
    ADD KEY `idx_tab_app_notify_record_schedule` (`task_status`, `next_retry_time`, `lease_until`);
