-- ----------------------------------------------------------------------------
-- 上游数据同步执行记录明细（upstream-sync-record-improvements change）
-- 记录每次执行记录下每一行处理的原始上游数据与处理结果，成功/失败均记录，
-- 供管理员回溯"这次同步到底处理了什么"（design.md Decision 2）。source_id 冗余自
-- 所属执行记录，供按数据源级联删除时直接 DELETE，不需要联表/子查询。不回改 V1~V6。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_upstream_sync_record_detail`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `sync_record_id` BIGINT       NOT NULL COMMENT '所属同步执行记录 id，关联 tab_upstream_sync_record.id',
    `source_id`      BIGINT       NOT NULL COMMENT '所属上游数据源 id，冗余自所属执行记录，供按数据源级联删除，不需要联表',
    `row_no`         INT          NOT NULL COMMENT '本次执行内该行的序号，从 1 开始',
    `row_data`       TEXT         NOT NULL COMMENT '该行的原始上游数据（取数阶段的原始行，JSON 文本）',
    `status`         VARCHAR(16)  NOT NULL COMMENT '该行处理状态：SUCCESS=成功，FAILED=失败',
    `fail_reason`    VARCHAR(500) NULL COMMENT '失败原因，仅 status=FAILED 时有值',
    `create_by`      VARCHAR(64)  NULL COMMENT '创建人',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      VARCHAR(64)  NULL COMMENT '更新人',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_upstream_sync_record_detail_record` (`sync_record_id`, `id`),
    KEY `idx_tab_upstream_sync_record_detail_source` (`source_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '上游数据同步执行记录明细表，记录每行处理的原始数据与结果，成功/失败均记录';
